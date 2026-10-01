#pragma once

// Stateful chord detection pipeline: sample framing -> chroma -> smoothing ->
// template matching -> noise gate -> stability hysteresis.
//
// Still platform independent, so it can be exercised on the host with synthetic
// audio. Not thread safe: a single DSP thread owns one instance.

#include <cstddef>
#include <vector>

#include "dsp/chord_matcher.h"
#include "dsp/chroma.h"

namespace gs {

/// Consecutive frames a candidate must win before the reported chord changes.
constexpr int kStabilityFrames = 3;

/// Default noise gate, roughly -40 dBFS.
constexpr float kDefaultNoiseGateRms = 0.01f;

/// Similarity below which a frame is treated as "not a chord".
constexpr float kDefaultMinConfidence = 0.55f;

struct DetectionResult {
    /// Template index of the reported chord, or -1 for "no chord".
    int chordIndex = -1;
    /// Cosine similarity of the best matching template for the latest frame, 0..1.
    float confidence = 0.0f;
    /// RMS level of the latest analysis window, 0..1.
    float rms = 0.0f;
    /// Smoothed chroma vector of the latest frame, normalised so its max is 1.
    float chroma[kNumPitchClasses] = {};
};

class ChordDetector {
public:
    explicit ChordDetector(float sampleRate = 48000.0f,
                           std::size_t fftSize = kFftSize,
                           std::size_t hopSize = kHopSize);

    ChordDetector(const ChordDetector&) = delete;
    ChordDetector& operator=(const ChordDetector&) = delete;

    /// Appends samples, running one analysis frame per `hopSize` samples once the
    /// first full window has accumulated. Returns the number of frames analysed.
    int pushSamples(const float* samples, std::size_t count);

    /// Result of the most recent analysis frame.
    const DetectionResult& latest() const { return latest_; }

    /// Clears the window, smoothing history and stability counters.
    void reset();

    /// RMS below which frames are reported as "no chord". Clamped to >= 0.
    void setNoiseGateRms(float rms);
    float noiseGateRms() const { return noiseGateRms_; }

    void setMinConfidence(float confidence);
    float minConfidence() const { return minConfidence_; }

    std::size_t fftSize() const { return fftSize_; }
    std::size_t hopSize() const { return hopSize_; }

private:
    /// Analyses the current window contents and updates `latest_`.
    void analyseWindow();

    std::size_t fftSize_;
    std::size_t hopSize_;
    float noiseGateRms_ = kDefaultNoiseGateRms;
    float minConfidence_ = kDefaultMinConfidence;

    ChromaExtractor extractor_;
    ChromaSmoother smoother_;

    std::vector<float> window_;
    std::size_t filled_ = 0;

    // Stability hysteresis state.
    int candidateIndex_ = -1;
    int candidateStreak_ = 0;
    int reportedIndex_ = -1;

    DetectionResult latest_;
};

}  // namespace gs
