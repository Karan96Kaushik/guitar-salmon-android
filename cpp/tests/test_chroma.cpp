#include "tests/test_support.h"

#include <algorithm>
#include <numeric>

#include "dsp/chroma.h"

using namespace gs;

namespace {

/// Index of the largest element of a 12-element chroma vector.
int argMax(const float* chroma) {
    return static_cast<int>(std::max_element(chroma, chroma + kNumPitchClasses) - chroma);
}

}  // namespace

GS_TEST(HannWindowHasExpectedShape) {
    std::vector<float> w(8);
    makeHannWindow(w.data(), w.size());

    GS_CHECK_NEAR(w[0], 0.0f, 1e-6);
    GS_CHECK_NEAR(w[4], 1.0f, 1e-6);
    // Periodic Hann is symmetric about the midpoint, excluding sample 0.
    GS_CHECK_NEAR(w[1], w[7], 1e-6);
    GS_CHECK_NEAR(w[2], w[6], 1e-6);
}

GS_TEST(RmsOfSineIsAmplitudeOverRootTwo) {
    std::vector<float> signal(48000, 0.0f);
    gstest::addSine(signal, 440.0, 0.5, 48000.0);
    GS_CHECK_NEAR(computeRms(signal.data(), signal.size()), 0.5 / std::sqrt(2.0), 1e-3);
}

GS_TEST(RmsOfSilenceIsZero) {
    std::vector<float> silence(1024, 0.0f);
    GS_CHECK_NEAR(computeRms(silence.data(), silence.size()), 0.0f, 1e-9);
}

GS_TEST(PitchClassMapsReferenceFrequencies) {
    GS_CHECK_EQ(pitchClassForFrequency(440.0f), 9);    // A4
    GS_CHECK_EQ(pitchClassForFrequency(261.626f), 0);  // C4
    GS_CHECK_EQ(pitchClassForFrequency(82.407f), 4);   // E2, guitar low string
    GS_CHECK_EQ(pitchClassForFrequency(196.0f), 7);    // G3
    GS_CHECK_EQ(pitchClassForFrequency(880.0f), 9);    // A5, octave invariance
    GS_CHECK_EQ(pitchClassForFrequency(0.0f), -1);
    GS_CHECK_EQ(pitchClassForFrequency(-100.0f), -1);
}

GS_TEST(PitchClassToleratesSlightDetuning) {
    // +-20 cents should still land on A.
    GS_CHECK_EQ(pitchClassForFrequency(440.0f * std::pow(2.0f, 20.0f / 1200.0f)), 9);
    GS_CHECK_EQ(pitchClassForFrequency(440.0f * std::pow(2.0f, -20.0f / 1200.0f)), 9);
}

GS_TEST(NormaliseByMaxScalesPeakToOne) {
    float values[4] = {1.0f, 2.0f, 4.0f, 0.0f};
    normaliseByMax(values, 4);
    GS_CHECK_NEAR(values[0], 0.25f, 1e-6);
    GS_CHECK_NEAR(values[2], 1.0f, 1e-6);
    GS_CHECK_NEAR(values[3], 0.0f, 1e-6);
}

GS_TEST(NormaliseByMaxLeavesZeroVectorAlone) {
    float values[3] = {0.0f, 0.0f, 0.0f};
    normaliseByMax(values, 3);
    for (float v : values) GS_CHECK_NEAR(v, 0.0f, 1e-9);
}

GS_TEST(CosineSimilarityBasics) {
    const float a[3] = {1.0f, 0.0f, 0.0f};
    const float b[3] = {2.0f, 0.0f, 0.0f};
    const float c[3] = {0.0f, 1.0f, 0.0f};
    const float zero[3] = {0.0f, 0.0f, 0.0f};

    // Scale invariant.
    GS_CHECK_NEAR(cosineSimilarity(a, b, 3), 1.0f, 1e-6);
    GS_CHECK_NEAR(cosineSimilarity(a, c, 3), 0.0f, 1e-6);
    GS_CHECK_NEAR(cosineSimilarity(a, zero, 3), 0.0f, 1e-9);
}

GS_TEST(SinglePeakFoldsToItsPitchClass) {
    // Synthesise a lone A4 and check the chroma peaks at A (index 9).
    std::vector<float> signal(kFftSize, 0.0f);
    gstest::addSine(signal, 440.0, 0.5, 48000.0);

    ChromaExtractor extractor;
    float chroma[kNumPitchClasses];
    extractor.process(signal.data(), chroma);

    GS_CHECK_EQ(argMax(chroma), 9);
    GS_CHECK_NEAR(chroma[9], 1.0f, 1e-6);
    // Every other pitch class should be essentially empty.
    for (int pc = 0; pc < kNumPitchClasses; ++pc) {
        if (pc == 9) continue;
        GS_CHECK(chroma[pc] < 0.2f);
    }
}

GS_TEST(PeakPickingResolvesLowSemitones) {
    // A semitone near the guitar's low E is narrower than an FFT bin, so this is
    // the case bin-by-bin folding gets wrong.
    std::vector<float> signal(kFftSize, 0.0f);
    const double lowE = gstest::midiToHz(40);  // E2, ~82.4 Hz
    gstest::addSine(signal, lowE, 0.5, 48000.0);

    ChromaExtractor extractor;
    float chroma[kNumPitchClasses];
    extractor.process(signal.data(), chroma);

    GS_CHECK_EQ(argMax(chroma), 4);  // E
    for (int pc = 0; pc < kNumPitchClasses; ++pc) {
        if (pc == 4) continue;
        GS_CHECK(chroma[pc] < 0.2f);
    }
}

GS_TEST(FoldingIsOctaveInvariant) {
    // The same pitch class two octaves apart must fold to the same bin.
    ChromaExtractor extractor;
    float low[kNumPitchClasses];
    float high[kNumPitchClasses];

    std::vector<float> a3(kFftSize, 0.0f);
    gstest::addSine(a3, 220.0, 0.5, 48000.0);
    extractor.process(a3.data(), low);

    std::vector<float> a5(kFftSize, 0.0f);
    gstest::addSine(a5, 880.0, 0.5, 48000.0);
    extractor.process(a5.data(), high);

    GS_CHECK_EQ(argMax(low), 9);
    GS_CHECK_EQ(argMax(high), 9);
}

GS_TEST(TriadFoldsToItsThreeChordTones) {
    // C major triad as pure sines: chroma should be hot at C, E, G only.
    const std::vector<float> signal =
        gstest::renderChord(/*root=*/0, {0, 4, 7}, kFftSize);

    ChromaExtractor extractor;
    float chroma[kNumPitchClasses];
    extractor.process(signal.data(), chroma);

    for (int pc : {0, 4, 7}) GS_CHECK_GT(chroma[pc], 0.6f);
    for (int pc = 0; pc < kNumPitchClasses; ++pc) {
        if (pc == 0 || pc == 4 || pc == 7) continue;
        GS_CHECK(chroma[pc] < 0.2f);
    }
}

GS_TEST(SilenceProducesEmptyChroma) {
    const std::vector<float> silence(kFftSize, 0.0f);

    ChromaExtractor extractor;
    float chroma[kNumPitchClasses];
    extractor.process(silence.data(), chroma);

    for (int pc = 0; pc < kNumPitchClasses; ++pc) GS_CHECK_NEAR(chroma[pc], 0.0f, 1e-6);
}

GS_TEST(LogCompressionLiftsQuietChordTones) {
    // One bin 10x louder than another: after compression the quiet tone should be
    // a much larger fraction of the peak than the raw 0.1 ratio.
    std::vector<float> magnitude(kFftSize / 2 + 1, 0.0f);
    ChromaConfig cfg;
    const float binHz = cfg.sampleRate / static_cast<float>(kFftSize);

    const int loudBin = static_cast<int>(std::lround(440.0f / binHz));   // A
    const int quietBin = static_cast<int>(std::lround(329.63f / binHz));  // E
    magnitude[static_cast<std::size_t>(loudBin)] = 0.5f;
    magnitude[static_cast<std::size_t>(quietBin)] = 0.05f;

    float chroma[kNumPitchClasses];
    foldSpectrumToChroma(magnitude.data(), magnitude.size(), kFftSize, cfg, chroma);

    GS_CHECK_NEAR(chroma[9], 1.0f, 1e-6);
    GS_CHECK_GT(chroma[4], 0.4f);
}

GS_TEST(SmootherAveragesRecentFrames) {
    ChromaSmoother smoother(4);
    float out[kNumPitchClasses];

    // No frames yet: all zeroes.
    smoother.average(out);
    for (float v : out) GS_CHECK_NEAR(v, 0.0f, 1e-9);

    float frame[kNumPitchClasses] = {};
    frame[0] = 1.0f;
    smoother.push(frame);
    frame[0] = 3.0f;
    smoother.push(frame);

    smoother.average(out);
    GS_CHECK_EQ(smoother.framesHeld(), 2);
    GS_CHECK_NEAR(out[0], 2.0f, 1e-6);
}

GS_TEST(SmootherForgetsFramesBeyondItsWindow) {
    ChromaSmoother smoother(4);
    float frame[kNumPitchClasses] = {};

    // Push 1 then four 0s: the 1 should have fallen out of the window.
    frame[0] = 1.0f;
    smoother.push(frame);
    frame[0] = 0.0f;
    for (int i = 0; i < 4; ++i) smoother.push(frame);

    float out[kNumPitchClasses];
    smoother.average(out);
    GS_CHECK_NEAR(out[0], 0.0f, 1e-6);
    GS_CHECK_EQ(smoother.framesHeld(), 4);
}

GS_TEST(SmootherResetClearsHistory) {
    ChromaSmoother smoother(4);
    float frame[kNumPitchClasses] = {};
    frame[5] = 1.0f;
    smoother.push(frame);
    smoother.reset();

    GS_CHECK_EQ(smoother.framesHeld(), 0);
    float out[kNumPitchClasses];
    smoother.average(out);
    for (float v : out) GS_CHECK_NEAR(v, 0.0f, 1e-9);
}
