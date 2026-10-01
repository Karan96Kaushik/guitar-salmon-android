#include "dsp/chroma.h"

#include <algorithm>
#include <cmath>
#include <cstring>

#include "kiss_fftr.h"

namespace gs {
namespace {

/// MIDI note number of A4, the reference pitch.
constexpr float kA4Midi = 69.0f;
constexpr float kA4Hz = 440.0f;

}  // namespace

void makeHannWindow(float* out, std::size_t n) {
    if (n == 0) return;
    if (n == 1) {
        out[0] = 1.0f;
        return;
    }
    const float twoPi = 6.283185307179586f;
    for (std::size_t i = 0; i < n; ++i) {
        out[i] = 0.5f * (1.0f - std::cos(twoPi * static_cast<float>(i) / static_cast<float>(n)));
    }
}

float computeRms(const float* samples, std::size_t count) {
    if (count == 0) return 0.0f;
    double sum = 0.0;
    for (std::size_t i = 0; i < count; ++i) {
        const double v = samples[i];
        sum += v * v;
    }
    return static_cast<float>(std::sqrt(sum / static_cast<double>(count)));
}

int pitchClassForFrequency(float hz) {
    if (!(hz > 0.0f)) return -1;
    // MIDI note number, which is linear in pitch class.
    const float midi = kA4Midi + 12.0f * std::log2(hz / kA4Hz);
    // MIDI 0 is C, so taking the note number modulo 12 yields 0 = C.
    int pc = static_cast<int>(std::lround(midi)) % kNumPitchClasses;
    if (pc < 0) pc += kNumPitchClasses;
    return pc;
}

void normaliseByMax(float* values, std::size_t n) {
    float maxValue = 0.0f;
    for (std::size_t i = 0; i < n; ++i) {
        if (std::isfinite(values[i]) && values[i] > maxValue) maxValue = values[i];
    }
    if (maxValue <= 0.0f) {
        std::fill(values, values + n, 0.0f);
        return;
    }
    const float inv = 1.0f / maxValue;
    for (std::size_t i = 0; i < n; ++i) {
        values[i] = std::isfinite(values[i]) ? values[i] * inv : 0.0f;
    }
}

float cosineSimilarity(const float* a, const float* b, std::size_t n) {
    double dot = 0.0, na = 0.0, nb = 0.0;
    for (std::size_t i = 0; i < n; ++i) {
        dot += static_cast<double>(a[i]) * b[i];
        na += static_cast<double>(a[i]) * a[i];
        nb += static_cast<double>(b[i]) * b[i];
    }
    if (na <= 0.0 || nb <= 0.0) return 0.0f;
    const double sim = dot / (std::sqrt(na) * std::sqrt(nb));
    return static_cast<float>(std::clamp(sim, 0.0, 1.0));
}

void findSpectralPeaks(const float* magnitude,
                       std::size_t numBins,
                       std::size_t fftSize,
                       const ChromaConfig& config,
                       std::vector<SpectralPeak>& peaksOut) {
    peaksOut.clear();
    if (numBins < 3 || fftSize == 0 || config.sampleRate <= 0.0f) return;

    const float binHz = config.sampleRate / static_cast<float>(fftSize);
    if (binHz <= 0.0f) return;

    // Restrict the search to [minHz, maxHz], leaving room for the k-1 / k+1
    // neighbours that interpolation needs.
    std::size_t firstBin = static_cast<std::size_t>(std::ceil(config.minHz / binHz));
    if (firstBin < 1) firstBin = 1;
    std::size_t lastBin = static_cast<std::size_t>(std::floor(config.maxHz / binHz));
    if (lastBin > numBins - 2) lastBin = numBins - 2;
    if (firstBin > lastBin) return;

    float loudest = 0.0f;
    for (std::size_t bin = firstBin; bin <= lastBin; ++bin) {
        // Strictly greater on the left and >= on the right so a flat top is
        // counted exactly once.
        const float m = magnitude[bin];
        if (!(m > magnitude[bin - 1]) || !(m >= magnitude[bin + 1])) continue;
        if (!(m > 0.0f)) continue;

        // Parabolic interpolation over log magnitudes. For a Hann-windowed tone
        // this estimates the true peak position to a small fraction of a bin.
        const float a = std::log(magnitude[bin - 1] + 1e-20f);
        const float b = std::log(m);
        const float c = std::log(magnitude[bin + 1] + 1e-20f);
        const float denom = a - 2.0f * b + c;

        float offset = 0.0f;
        float logPeak = b;
        if (std::fabs(denom) > 1e-12f) {
            offset = 0.5f * (a - c) / denom;
            // Guard against a degenerate fit pushing the peak into a neighbour.
            if (!(offset > -1.0f && offset < 1.0f)) offset = 0.0f;
            logPeak = b - 0.25f * (a - c) * offset;
        }

        const float hz = (static_cast<float>(bin) + offset) * binHz;
        if (hz < config.minHz || hz > config.maxHz) continue;

        const float peakMag = std::exp(logPeak);
        if (!std::isfinite(peakMag)) continue;

        peaksOut.push_back(SpectralPeak{hz, peakMag});
        if (peakMag > loudest) loudest = peakMag;
    }

    // Drop peaks that are almost certainly noise rather than played notes.
    if (loudest > 0.0f && config.peakRelativeFloor > 0.0f) {
        const float floorMag = loudest * config.peakRelativeFloor;
        peaksOut.erase(std::remove_if(peaksOut.begin(),
                                      peaksOut.end(),
                                      [floorMag](const SpectralPeak& p) {
                                          return p.magnitude < floorMag;
                                      }),
                       peaksOut.end());
    }
}

void foldSpectrumToChroma(const float* magnitude,
                          std::size_t numBins,
                          std::size_t fftSize,
                          const ChromaConfig& config,
                          float* chromaOut) {
    std::fill(chromaOut, chromaOut + kNumPitchClasses, 0.0f);

    // Reused across calls so steady-state analysis does not allocate. The DSP
    // thread owns its own copy; `thread_local` keeps that true for any caller.
    static thread_local std::vector<SpectralPeak> peaks;
    findSpectralPeaks(magnitude, numBins, fftSize, config, peaks);

    for (const SpectralPeak& peak : peaks) {
        const int pc = pitchClassForFrequency(peak.hz);
        if (pc < 0) continue;
        chromaOut[pc] += peak.magnitude;
    }

    for (int pc = 0; pc < kNumPitchClasses; ++pc) {
        chromaOut[pc] = std::log1p(config.logGamma * chromaOut[pc]);
    }

    normaliseByMax(chromaOut, kNumPitchClasses);
}

ChromaExtractor::ChromaExtractor(std::size_t fftSize, const ChromaConfig& config)
    : fftSize_(fftSize), config_(config) {
    fft_ = kiss_fftr_alloc(static_cast<int>(fftSize_), /*inverse=*/0, nullptr, nullptr);
    window_.resize(fftSize_);
    makeHannWindow(window_.data(), fftSize_);
    windowed_.resize(fftSize_);
    // kiss_fftr writes fftSize/2 + 1 complex values.
    spectrum_.resize(2 * (fftSize_ / 2 + 1));
    magnitude_.resize(fftSize_ / 2 + 1);
}

ChromaExtractor::~ChromaExtractor() {
    if (fft_ != nullptr) kiss_fftr_free(fft_);
}

void ChromaExtractor::process(const float* samples, float* chromaOut) {
    if (fft_ == nullptr) {
        std::fill(chromaOut, chromaOut + kNumPitchClasses, 0.0f);
        return;
    }

    for (std::size_t i = 0; i < fftSize_; ++i) {
        windowed_[i] = samples[i] * window_[i];
    }

    kiss_fftr(fft_,
              windowed_.data(),
              reinterpret_cast<kiss_fft_cpx*>(spectrum_.data()));

    // Normalise by the window length so magnitudes are independent of FFT size.
    const float scale = 2.0f / static_cast<float>(fftSize_);
    for (std::size_t bin = 0; bin < magnitude_.size(); ++bin) {
        const float re = spectrum_[2 * bin];
        const float im = spectrum_[2 * bin + 1];
        magnitude_[bin] = std::sqrt(re * re + im * im) * scale;
    }

    foldSpectrumToChroma(magnitude_.data(), magnitude_.size(), fftSize_, config_, chromaOut);
}

ChromaSmoother::ChromaSmoother(int numFrames)
    : numFrames_(numFrames > 0 ? numFrames : 1) {
    history_.assign(static_cast<std::size_t>(numFrames_) * kNumPitchClasses, 0.0f);
}

void ChromaSmoother::push(const float* chroma) {
    float* slot = history_.data() + static_cast<std::size_t>(writeIndex_) * kNumPitchClasses;
    std::memcpy(slot, chroma, kNumPitchClasses * sizeof(float));
    writeIndex_ = (writeIndex_ + 1) % numFrames_;
    if (framesHeld_ < numFrames_) ++framesHeld_;
}

void ChromaSmoother::average(float* out) const {
    std::fill(out, out + kNumPitchClasses, 0.0f);
    if (framesHeld_ == 0) return;
    for (int frame = 0; frame < framesHeld_; ++frame) {
        const float* slot = history_.data() + static_cast<std::size_t>(frame) * kNumPitchClasses;
        for (int pc = 0; pc < kNumPitchClasses; ++pc) out[pc] += slot[pc];
    }
    const float inv = 1.0f / static_cast<float>(framesHeld_);
    for (int pc = 0; pc < kNumPitchClasses; ++pc) out[pc] *= inv;
}

void ChromaSmoother::reset() {
    std::fill(history_.begin(), history_.end(), 0.0f);
    writeIndex_ = 0;
    framesHeld_ = 0;
}

}  // namespace gs
