#include "audio/audio_engine.h"

#include <android/log.h>

#include <chrono>
#include <cstring>

#define LOG_TAG "GuitarSalmonNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace gs {

AudioEngine::AudioEngine() = default;

AudioEngine::~AudioEngine() {
    stop();
}

oboe::Result AudioEngine::openStream(oboe::InputPreset preset) {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Mono)
        // Pin the rate to 48 kHz and let Oboe resample if the device differs, so
        // the DSP stage always sees the rate its analysis constants assume.
        ->setSampleRate(kTargetSampleRate)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setInputPreset(preset)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    return builder.openStream(stream_);
}

bool AudioEngine::start() {
    if (running_.load(std::memory_order_acquire)) return true;

    {
        std::lock_guard<std::mutex> lock(streamMutex_);

        // Unprocessed gives us the rawest signal, which is what pitch analysis
        // wants: no AGC pumping the level and no noise suppression carving holes
        // in the spectrum. Not every device implements it, so fall back to
        // VoiceRecognition, which at least disables most processing.
        oboe::Result result = openStream(oboe::InputPreset::Unprocessed);
        if (result != oboe::Result::OK) {
            LOGW("Unprocessed input unavailable (%s); falling back to VoiceRecognition",
                 oboe::convertToText(result));
            result = openStream(oboe::InputPreset::VoiceRecognition);
        }
        if (result != oboe::Result::OK) {
            LOGW("Failed to open input stream: %s", oboe::convertToText(result));
            stream_.reset();
            return false;
        }

        sampleRate_.store(stream_->getSampleRate(), std::memory_order_relaxed);
        LOGI("Input stream open: %d Hz, %d ch, api=%s",
             stream_->getSampleRate(),
             stream_->getChannelCount(),
             oboe::convertToText(stream_->getAudioApi()));
    }

    {
        std::lock_guard<std::mutex> lock(resultMutex_);
        latestResult_ = DetectionResult();
    }

    stopRequested_.store(false, std::memory_order_release);
    running_.store(true, std::memory_order_release);
    dspThread_ = std::thread(&AudioEngine::dspLoop, this);

    oboe::Result startResult;
    {
        std::lock_guard<std::mutex> lock(streamMutex_);
        startResult = stream_->requestStart();
    }
    if (startResult != oboe::Result::OK) {
        LOGW("Failed to start input stream: %s", oboe::convertToText(startResult));
        shutdown();
        return false;
    }

    return true;
}

void AudioEngine::stop() {
    if (!running_.load(std::memory_order_acquire) && !dspThread_.joinable()) return;
    shutdown();
}

void AudioEngine::shutdown() {
    // Clear `running_` first so a concurrent disconnect callback does not reopen
    // the stream behind our back.
    running_.store(false, std::memory_order_release);
    stopRequested_.store(true, std::memory_order_release);

    {
        std::lock_guard<std::mutex> lock(streamMutex_);
        if (stream_) {
            stream_->requestStop();
            stream_->close();
            stream_.reset();
        }
    }

    if (dspThread_.joinable()) dspThread_.join();

    sampleRate_.store(0, std::memory_order_relaxed);
    {
        std::lock_guard<std::mutex> lock(resultMutex_);
        latestResult_ = DetectionResult();
    }
}

oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream* /*stream*/,
                                                  void* audioData,
                                                  int32_t numFrames) {
    // Realtime thread. The only work here is a copy into the ring buffer; all
    // analysis happens on the DSP thread. Overflow is dropped rather than waited
    // on, because blocking here would glitch the audio device.
    if (numFrames > 0) {
        ring_.write(static_cast<const float*>(audioData), static_cast<std::size_t>(numFrames));
    }
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream* /*stream*/, oboe::Result error) {
    if (!running_.load(std::memory_order_acquire)) return;
    if (error != oboe::Result::ErrorDisconnected) {
        LOGW("Stream error after close: %s", oboe::convertToText(error));
        return;
    }

    // The input device changed -- a headset or USB mic was plugged in or pulled
    // out. Oboe has already closed the old stream, so open a fresh one on the new
    // default device. The DSP thread keeps running; it simply sees a short gap,
    // which the noise gate reports as "no chord".
    if (restarting_.exchange(true)) return;

    LOGI("Input device disconnected; reopening stream");
    {
        std::lock_guard<std::mutex> lock(streamMutex_);
        stream_.reset();

        oboe::Result result = openStream(oboe::InputPreset::Unprocessed);
        if (result != oboe::Result::OK) {
            result = openStream(oboe::InputPreset::VoiceRecognition);
        }
        if (result == oboe::Result::OK && stream_) {
            sampleRate_.store(stream_->getSampleRate(), std::memory_order_relaxed);
            result = stream_->requestStart();
        }
        if (result != oboe::Result::OK) {
            LOGW("Could not reopen input after disconnect: %s", oboe::convertToText(result));
            stream_.reset();
            running_.store(false, std::memory_order_release);
        }
    }
    restarting_.store(false, std::memory_order_release);
}

DetectionResult AudioEngine::latestResult() const {
    std::lock_guard<std::mutex> lock(resultMutex_);
    return latestResult_;
}

void AudioEngine::setNoiseGateRms(float rms) {
    pendingNoiseGate_.store(rms, std::memory_order_relaxed);
}

void AudioEngine::dspLoop() {
    ChordDetector detector(static_cast<float>(kTargetSampleRate));

    float appliedGate = pendingNoiseGate_.load(std::memory_order_relaxed);
    detector.setNoiseGateRms(appliedGate);

    // Read in hop-sized chunks so each read yields exactly one analysis frame.
    std::vector<float> chunk(kHopSize);

    while (!stopRequested_.load(std::memory_order_acquire)) {
        const float gate = pendingNoiseGate_.load(std::memory_order_relaxed);
        if (gate != appliedGate) {
            detector.setNoiseGateRms(gate);
            appliedGate = gate;
        }

        // Drain everything the callback has produced since the last wake-up.
        while (ring_.available() >= chunk.size()) {
            ring_.read(chunk.data(), chunk.size());
            if (detector.pushSamples(chunk.data(), chunk.size()) > 0) {
                std::lock_guard<std::mutex> lock(resultMutex_);
                latestResult_ = detector.latest();
            }
        }

        std::this_thread::sleep_for(std::chrono::milliseconds(kPollIntervalMs));
    }
}

}  // namespace gs
