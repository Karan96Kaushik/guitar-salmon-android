#pragma once

// Oboe microphone capture feeding the chord detector.
//
// Threading model:
//   * Oboe's realtime callback does one thing: copy samples into the ring buffer.
//     No DSP, no locks, no allocation.
//   * A dedicated DSP thread drains the ring buffer and runs the analysis.
//   * Callers (the JNI bridge) poll the most recent result, which is published
//     under a short-lived mutex.

#include <atomic>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>

#include <oboe/Oboe.h>

#include "audio/ring_buffer.h"
#include "dsp/chord_detector.h"

namespace gs {

class AudioEngine : public oboe::AudioStreamDataCallback,
                    public oboe::AudioStreamErrorCallback {
public:
    AudioEngine();
    ~AudioEngine() override;

    AudioEngine(const AudioEngine&) = delete;
    AudioEngine& operator=(const AudioEngine&) = delete;

    /// Opens the input stream and starts the DSP thread. Returns false if the
    /// stream could not be opened (for example when the mic permission is
    /// missing). Calling start() while already running is a no-op that returns true.
    bool start();

    /// Stops the DSP thread and closes the stream. Safe to call when not running.
    void stop();

    bool isRunning() const { return running_.load(std::memory_order_acquire); }

    /// Most recent detection result. Safe to call from any thread.
    DetectionResult latestResult() const;

    /// Noise gate threshold, applied by the DSP thread on its next frame.
    void setNoiseGateRms(float rms);

    /// Sample rate the stream actually opened with, or 0 when not running.
    int32_t sampleRate() const { return sampleRate_.load(std::memory_order_relaxed); }

    // --- oboe::AudioStreamDataCallback ---
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream,
                                          void* audioData,
                                          int32_t numFrames) override;

    // --- oboe::AudioStreamErrorCallback ---
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    /// Opens a stream with the given input preset.
    oboe::Result openStream(oboe::InputPreset preset);

    /// DSP thread body: drain the ring buffer, analyse, publish.
    void dspLoop();

    /// Tears down the stream and thread without touching `restarting_`.
    void shutdown();

    static constexpr int32_t kTargetSampleRate = 48000;
    /// Ring buffer headroom: ~0.68 s at 48 kHz, far more than the DSP thread's
    /// wake-up interval needs, so a scheduling hiccup cannot drop samples.
    static constexpr std::size_t kRingCapacity = 32768;
    /// How long the DSP thread sleeps between polls of the ring buffer. Well under
    /// the ~42.7 ms hop interval, so frames are never late by much.
    static constexpr int kPollIntervalMs = 8;

    std::shared_ptr<oboe::AudioStream> stream_;
    std::mutex streamMutex_;

    RingBuffer ring_{kRingCapacity};
    std::thread dspThread_;
    std::atomic<bool> running_{false};
    std::atomic<bool> stopRequested_{false};
    std::atomic<bool> restarting_{false};
    std::atomic<int32_t> sampleRate_{0};
    std::atomic<float> pendingNoiseGate_{kDefaultNoiseGateRms};

    mutable std::mutex resultMutex_;
    DetectionResult latestResult_;
};

}  // namespace gs
