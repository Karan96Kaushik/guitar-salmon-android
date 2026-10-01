#include "dsp/chord_detector.h"

#include <algorithm>
#include <cstring>

namespace gs {

ChordDetector::ChordDetector(float sampleRate, std::size_t fftSize, std::size_t hopSize)
    : fftSize_(fftSize),
      hopSize_(std::min(hopSize == 0 ? 1 : hopSize, fftSize)),
      extractor_(fftSize, [sampleRate] {
          ChromaConfig cfg;
          cfg.sampleRate = sampleRate;
          return cfg;
      }()),
      smoother_(kSmoothingFrames) {
    window_.assign(fftSize_, 0.0f);
}

void ChordDetector::setNoiseGateRms(float rms) {
    noiseGateRms_ = std::max(0.0f, rms);
}

void ChordDetector::setMinConfidence(float confidence) {
    minConfidence_ = std::clamp(confidence, 0.0f, 1.0f);
}

void ChordDetector::reset() {
    std::fill(window_.begin(), window_.end(), 0.0f);
    filled_ = 0;
    smoother_.reset();
    candidateIndex_ = -1;
    candidateStreak_ = 0;
    reportedIndex_ = -1;
    latest_ = DetectionResult();
}

int ChordDetector::pushSamples(const float* samples, std::size_t count) {
    int frames = 0;
    std::size_t offset = 0;

    while (offset < count) {
        const std::size_t room = fftSize_ - filled_;
        const std::size_t take = std::min(room, count - offset);
        std::memcpy(window_.data() + filled_, samples + offset, take * sizeof(float));
        filled_ += take;
        offset += take;

        if (filled_ < fftSize_) break;

        analyseWindow();
        ++frames;

        // Slide the window forward by one hop, keeping the newest
        // (fftSize - hopSize) samples so successive frames overlap.
        const std::size_t keep = fftSize_ - hopSize_;
        if (keep > 0) {
            std::memmove(window_.data(), window_.data() + hopSize_, keep * sizeof(float));
        }
        filled_ = keep;
    }

    return frames;
}

void ChordDetector::analyseWindow() {
    const float rms = computeRms(window_.data(), fftSize_);

    float raw[kNumPitchClasses];
    extractor_.process(window_.data(), raw);
    smoother_.push(raw);

    float smoothed[kNumPitchClasses];
    smoother_.average(smoothed);

    const ChordMatch match = matchChroma(smoothed);

    // A frame only nominates a candidate when it is both loud enough and a
    // convincing match; otherwise it nominates "no chord".
    const bool gated = rms < noiseGateRms_;
    const int candidate =
        (gated || match.confidence < minConfidence_) ? -1 : match.index;

    if (candidate == candidateIndex_) {
        if (candidateStreak_ < kStabilityFrames) ++candidateStreak_;
    } else {
        candidateIndex_ = candidate;
        candidateStreak_ = 1;
    }

    // Hysteresis: the reported chord only follows the candidate once it has won
    // kStabilityFrames frames in a row. This is what stops the display flickering
    // between neighbouring chords during a strum's attack transient.
    if (candidateStreak_ >= kStabilityFrames) {
        reportedIndex_ = candidateIndex_;
    }

    latest_.chordIndex = reportedIndex_;
    latest_.confidence = gated ? 0.0f : match.confidence;
    latest_.rms = rms;
    std::memcpy(latest_.chroma, smoothed, sizeof(latest_.chroma));
}

}  // namespace gs
