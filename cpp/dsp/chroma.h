#pragma once

// Chroma (pitch class profile) extraction.
//
// Everything in this header is deliberately free of Android/Oboe dependencies so
// that it can be compiled and unit tested on the host. The free functions are
// pure: given the same input they always produce the same output.

#include <cstddef>
#include <vector>

struct kiss_fftr_state;

namespace gs {

/// Number of pitch classes: C, C#, D, D#, E, F, F#, G, G#, A, A#, B.
constexpr int kNumPitchClasses = 12;

/// Analysis window length in samples (~171 ms at 48 kHz).
constexpr std::size_t kFftSize = 8192;

/// Hop between successive analysis windows (~23 frames/second at 48 kHz).
constexpr std::size_t kHopSize = 2048;

/// Number of frames averaged by ChromaSmoother.
constexpr int kSmoothingFrames = 4;

struct ChromaConfig {
    float sampleRate = 48000.0f;
    /// Lowest analysed frequency. Guitar low E is ~82 Hz.
    float minHz = 80.0f;
    /// Highest analysed frequency; above this harmonics dominate and confuse chroma.
    float maxHz = 2000.0f;
    /// Strength of the logarithmic compression applied to the folded chroma.
    float logGamma = 100.0f;
    /// Peaks quieter than this fraction of the loudest in-band peak are ignored.
    float peakRelativeFloor = 0.01f;
};

/// A local maximum of the magnitude spectrum, refined to sub-bin accuracy.
struct SpectralPeak {
    float hz = 0.0f;
    float magnitude = 0.0f;
};

/// Fills `out[0..n)` with a periodic Hann window.
void makeHannWindow(float* out, std::size_t n);

/// Root mean square of a block of samples. Returns 0 for an empty block.
float computeRms(const float* samples, std::size_t count);

/// Pitch class (0 = C) nearest to `hz`, or -1 if `hz` is not a positive frequency.
/// Uses the equal tempered scale with A4 = 440 Hz.
int pitchClassForFrequency(float hz);

/// Scales `values[0..n)` so the largest element becomes 1. A vector that is
/// entirely zero (or has a non-finite maximum) is left as all zeroes.
void normaliseByMax(float* values, std::size_t n);

/// Cosine similarity of two n-dimensional vectors, clamped to [0, 1].
/// Returns 0 if either vector has zero magnitude.
float cosineSimilarity(const float* a, const float* b, std::size_t n);

/// Finds the local maxima of a magnitude spectrum within [minHz, maxHz].
///
/// `magnitude` holds `numBins` linear magnitudes from a real FFT of length
/// `fftSize` (so numBins == fftSize / 2 + 1). Each peak's frequency is refined by
/// parabolic interpolation over the log magnitudes of the three bins around it,
/// which recovers far better than bin accuracy.
///
/// Peak picking exists because of a hard resolution limit: at 48 kHz an 8192
/// point FFT has 5.9 Hz bins, but a semitone near the guitar's low E spans only
/// ~5 Hz. Folding every bin would therefore smear a single low note across three
/// or four adjacent pitch classes. A peak's interpolated centre, by contrast,
/// pins the frequency down to well under a semitone, and leakage bins beside the
/// peak are not local maxima so they never contribute.
void findSpectralPeaks(const float* magnitude,
                       std::size_t numBins,
                       std::size_t fftSize,
                       const ChromaConfig& config,
                       std::vector<SpectralPeak>& peaksOut);

/// Folds a magnitude spectrum into a 12-element chroma vector.
///
/// Peaks are detected (see findSpectralPeaks) and each peak's linear magnitude is
/// added to its nearest pitch class. The 12 sums are then log-compressed with
/// log1p(gamma * x) and normalised so the maximum is 1.
///
/// Compression happens after folding, and it matters because a plucked string's
/// fundamental can be many times the amplitude of a quietly fretted note:
/// without it, one loud string masks the rest of the chord.
void foldSpectrumToChroma(const float* magnitude,
                          std::size_t numBins,
                          std::size_t fftSize,
                          const ChromaConfig& config,
                          float* chromaOut);

/// Windows a block of samples, takes its real FFT and folds the magnitude
/// spectrum into a chroma vector. Owns the FFT plan, so it is not copyable, but
/// `process` has no frame-to-frame state: the same input always maps to the same
/// chroma.
class ChromaExtractor {
public:
    explicit ChromaExtractor(std::size_t fftSize = kFftSize,
                             const ChromaConfig& config = ChromaConfig());
    ~ChromaExtractor();

    ChromaExtractor(const ChromaExtractor&) = delete;
    ChromaExtractor& operator=(const ChromaExtractor&) = delete;

    /// `samples` must contain exactly fftSize values; `chromaOut` exactly 12.
    void process(const float* samples, float* chromaOut);

    /// Magnitude spectrum of the most recent `process` call (fftSize/2 + 1 bins).
    const std::vector<float>& magnitude() const { return magnitude_; }

    std::size_t fftSize() const { return fftSize_; }
    const ChromaConfig& config() const { return config_; }
    void setConfig(const ChromaConfig& config) { config_ = config; }

private:
    std::size_t fftSize_;
    ChromaConfig config_;
    kiss_fftr_state* fft_ = nullptr;
    std::vector<float> window_;
    std::vector<float> windowed_;
    std::vector<float> spectrum_;  // interleaved complex output
    std::vector<float> magnitude_;
};

/// Moving average over the last N chroma frames. Smoothing trades a little
/// latency for a large drop in frame-to-frame jitter, which keeps the reported
/// chord from flickering while a chord is still ringing.
class ChromaSmoother {
public:
    explicit ChromaSmoother(int numFrames = kSmoothingFrames);

    void push(const float* chroma);

    /// Writes the average of the frames pushed so far into `out` (12 values).
    /// Before any push this is all zeroes.
    void average(float* out) const;

    void reset();

    int framesHeld() const { return framesHeld_; }

private:
    int numFrames_;
    int writeIndex_ = 0;
    int framesHeld_ = 0;
    std::vector<float> history_;  // numFrames_ * 12, frame-major
};

}  // namespace gs
