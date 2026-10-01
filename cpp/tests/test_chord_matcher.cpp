#include "tests/test_support.h"

#include <string>

#include "dsp/chord_detector.h"
#include "dsp/chord_matcher.h"
#include "dsp/chroma.h"

using namespace gs;

namespace {

/// Runs the full chroma + matching pipeline over a synthetic chord and returns
/// the detected chord's display name.
std::string detectChordName(int root, ChordType type, int baseMidi = 48) {
    const std::vector<float> signal =
        gstest::renderChord(root, chordTypeIntervals(type), kFftSize, 48000.0, baseMidi);

    ChromaExtractor extractor;
    float chroma[kNumPitchClasses];
    extractor.process(signal.data(), chroma);

    const ChordMatch match = matchChroma(chroma);
    return chordName(match.index);
}

}  // namespace

GS_TEST(TemplateSetCoversTwelveRootsAndSevenTypes) {
    GS_CHECK_EQ(chordCount(), 84);
    GS_CHECK_EQ(chordCount(), kNumChordTemplates);
}

GS_TEST(ChordNamesFollowConvention) {
    GS_CHECK_STREQ(chordName(chordIndex(0, ChordType::Major)), "C");
    GS_CHECK_STREQ(chordName(chordIndex(9, ChordType::Minor)), "Am");
    GS_CHECK_STREQ(chordName(chordIndex(7, ChordType::Dominant7)), "G7");
    GS_CHECK_STREQ(chordName(chordIndex(5, ChordType::Major7)), "Fmaj7");
    GS_CHECK_STREQ(chordName(chordIndex(4, ChordType::Minor7)), "Em7");
    GS_CHECK_STREQ(chordName(chordIndex(2, ChordType::Sus2)), "Dsus2");
    GS_CHECK_STREQ(chordName(chordIndex(2, ChordType::Sus4)), "Dsus4");
    GS_CHECK_STREQ(chordName(chordIndex(6, ChordType::Major)), "F#");
    // Out of range indices are safe.
    GS_CHECK_STREQ(chordName(-1), "");
    GS_CHECK_STREQ(chordName(9999), "");
}

GS_TEST(TemplatesContainExpectedPitchClasses) {
    const auto& templates = chordTemplates();

    // C major: C (root), E, G.
    const ChordTemplate& cMajor = templates[chordIndex(0, ChordType::Major)];
    GS_CHECK_NEAR(cMajor.weights[0], kRootWeight, 1e-6);
    GS_CHECK_NEAR(cMajor.weights[4], kChordToneWeight, 1e-6);
    GS_CHECK_NEAR(cMajor.weights[7], kChordToneWeight, 1e-6);
    GS_CHECK_NEAR(cMajor.weights[3], 0.0f, 1e-6);

    // A minor seventh: A (root), C, E, G.
    const ChordTemplate& aMin7 = templates[chordIndex(9, ChordType::Minor7)];
    GS_CHECK_NEAR(aMin7.weights[9], kRootWeight, 1e-6);
    GS_CHECK_NEAR(aMin7.weights[0], kChordToneWeight, 1e-6);
    GS_CHECK_NEAR(aMin7.weights[4], kChordToneWeight, 1e-6);
    GS_CHECK_NEAR(aMin7.weights[7], kChordToneWeight, 1e-6);
}

GS_TEST(RootEmphasisSeparatesSusTwoFromSusFour) {
    // Fsus2 and Csus4 are both {F, G, C}. The only thing telling them apart is
    // which pitch class the audio emphasises, so the templates must differ and
    // the louder root must win.
    const auto& templates = chordTemplates();
    const ChordTemplate& fSus2 = templates[chordIndex(5, ChordType::Sus2)];
    const ChordTemplate& cSus4 = templates[chordIndex(0, ChordType::Sus4)];

    for (int pc : {0, 5, 7}) {
        GS_CHECK_GT(fSus2.weights[pc], 0.0f);
        GS_CHECK_GT(cSus4.weights[pc], 0.0f);
    }
    GS_CHECK_GT(fSus2.weights[5], cSus4.weights[5]);  // F is Fsus2's root
    GS_CHECK_GT(cSus4.weights[0], fSus2.weights[0]);  // C is Csus4's root

    // A chroma with F loudest prefers Fsus2; with C loudest, Csus4.
    float fRooted[kNumPitchClasses] = {};
    fRooted[5] = 1.0f;
    fRooted[7] = fRooted[0] = 0.8f;
    GS_CHECK_STREQ(chordName(matchChroma(fRooted).index), "Fsus2");

    float cRooted[kNumPitchClasses] = {};
    cRooted[0] = 1.0f;
    cRooted[5] = cRooted[7] = 0.8f;
    GS_CHECK_STREQ(chordName(matchChroma(cRooted).index), "Csus4");
}

GS_TEST(ExactTemplateMatchesItselfPerfectly) {
    const auto& templates = chordTemplates();
    for (std::size_t i = 0; i < templates.size(); ++i) {
        const ChordMatch match = matchChroma(templates[i].weights);
        GS_CHECK_NEAR(match.confidence, 1.0f, 1e-5);
        // Templates are unique, so a template must match itself.
        GS_CHECK_EQ(match.index, static_cast<int>(i));
    }
}

GS_TEST(EmptyChromaMatchesNothing) {
    const float zero[kNumPitchClasses] = {};
    const ChordMatch match = matchChroma(zero);
    GS_CHECK_EQ(match.index, -1);
    GS_CHECK_NEAR(match.confidence, 0.0f, 1e-9);
}

GS_TEST(TriadBeatsOtherQualitiesOnTheSameRoot) {
    // A clean C major triad should prefer "C" over C7 / Cmaj7, which add a note
    // the signal does not contain.
    float chroma[kNumPitchClasses] = {};
    chroma[0] = chroma[4] = chroma[7] = 1.0f;

    const auto& templates = chordTemplates();
    const float major =
        cosineSimilarity(chroma, templates[chordIndex(0, ChordType::Major)].weights,
                         kNumPitchClasses);
    const float dom7 =
        cosineSimilarity(chroma, templates[chordIndex(0, ChordType::Dominant7)].weights,
                         kNumPitchClasses);
    const float maj7 =
        cosineSimilarity(chroma, templates[chordIndex(0, ChordType::Major7)].weights,
                         kNumPitchClasses);
    const float relativeMinor =
        cosineSimilarity(chroma, templates[chordIndex(4, ChordType::Minor)].weights,
                         kNumPitchClasses);

    GS_CHECK_GT(major, dom7);
    GS_CHECK_GT(major, maj7);
    GS_CHECK_GT(major, relativeMinor);
}

GS_TEST(SyntheticOpenChordsAreIdentified) {
    // The chords a beginner learns first, rendered as sine triads.
    GS_CHECK_STREQ(detectChordName(7, ChordType::Major), "G");
    GS_CHECK_STREQ(detectChordName(0, ChordType::Major), "C");
    GS_CHECK_STREQ(detectChordName(2, ChordType::Major), "D");
    GS_CHECK_STREQ(detectChordName(4, ChordType::Minor), "Em");
    GS_CHECK_STREQ(detectChordName(9, ChordType::Minor), "Am");
    GS_CHECK_STREQ(detectChordName(5, ChordType::Major), "F");
    GS_CHECK_STREQ(detectChordName(4, ChordType::Major), "E");
    GS_CHECK_STREQ(detectChordName(9, ChordType::Major), "A");
}

GS_TEST(EverySyntheticChordInTheTemplateSetIsIdentified) {
    // Exhaustive sweep: all 12 roots x 7 qualities must round-trip through
    // synthesis, chroma extraction and matching.
    const ChordType types[kNumChordTypes] = {
        ChordType::Major,  ChordType::Minor, ChordType::Dominant7, ChordType::Major7,
        ChordType::Minor7, ChordType::Sus2,  ChordType::Sus4,
    };

    for (int root = 0; root < kNumPitchClasses; ++root) {
        for (ChordType type : types) {
            const std::string expected = chordName(chordIndex(root, type));
            const std::string actual = detectChordName(root, type);
            if (actual != expected) {
                ::gstest::fail(__FILE__, __LINE__,
                               "chord " + expected + " was detected as " + actual);
            }
        }
    }
}

GS_TEST(DetectorGatesOutSilence) {
    ChordDetector detector;
    const std::vector<float> silence(kFftSize * 2, 0.0f);
    detector.pushSamples(silence.data(), silence.size());

    GS_CHECK_EQ(detector.latest().chordIndex, -1);
    GS_CHECK_NEAR(detector.latest().confidence, 0.0f, 1e-6);
    GS_CHECK(detector.latest().rms < kDefaultNoiseGateRms);
}

GS_TEST(DetectorGatesOutVeryQuietAudio) {
    ChordDetector detector;
    // A real C major chord, but 60 dB down: below the gate, so "no chord".
    std::vector<float> signal = gstest::renderChord(0, {0, 4, 7}, kFftSize * 3);
    for (float& s : signal) s *= 0.001f;

    detector.pushSamples(signal.data(), signal.size());
    GS_CHECK_EQ(detector.latest().chordIndex, -1);
}

GS_TEST(DetectorProducesOneFramePerHop) {
    ChordDetector detector;
    const std::vector<float> signal = gstest::renderChord(0, {0, 4, 7}, kFftSize);

    // The first full window yields one frame.
    GS_CHECK_EQ(detector.pushSamples(signal.data(), kFftSize), 1);
    // After that, every hop's worth of samples yields one more.
    GS_CHECK_EQ(detector.pushSamples(signal.data(), kHopSize), 1);
    GS_CHECK_EQ(detector.pushSamples(signal.data(), kHopSize * 3), 3);
    // A partial hop yields nothing yet.
    GS_CHECK_EQ(detector.pushSamples(signal.data(), kHopSize / 2), 0);
}

GS_TEST(DetectorWaitsForStabilityBeforeReporting) {
    ChordDetector detector;
    // One continuous G major chord, fed hop by hop exactly as the DSP thread does.
    const std::vector<float> signal =
        gstest::renderChord(7, {0, 4, 7}, kFftSize + kHopSize * 2);
    std::size_t cursor = 0;

    // Frame 1: a candidate exists, but has not won kStabilityFrames in a row yet.
    detector.pushSamples(signal.data() + cursor, kFftSize);
    cursor += kFftSize;
    GS_CHECK_EQ(detector.latest().chordIndex, -1);

    // Frame 2: still one short of the threshold.
    detector.pushSamples(signal.data() + cursor, kHopSize);
    cursor += kHopSize;
    GS_CHECK_EQ(detector.latest().chordIndex, -1);

    // Frame 3 confirms the candidate, so it is finally reported.
    detector.pushSamples(signal.data() + cursor, kHopSize);
    GS_CHECK_STREQ(chordName(detector.latest().chordIndex), "G");
}

GS_TEST(DetectorHoldsChordWhileSignalContinues) {
    ChordDetector detector;
    // 1 second of a steady D major chord.
    const std::vector<float> signal = gstest::renderChord(2, {0, 4, 7}, 48000);
    detector.pushSamples(signal.data(), signal.size());

    GS_CHECK_STREQ(chordName(detector.latest().chordIndex), "D");
    GS_CHECK_GT(detector.latest().confidence, 0.9f);
    GS_CHECK_GT(detector.latest().rms, kDefaultNoiseGateRms);
}

GS_TEST(DetectorResetClearsState) {
    ChordDetector detector;
    const std::vector<float> signal = gstest::renderChord(0, {0, 4, 7}, 48000);
    detector.pushSamples(signal.data(), signal.size());
    GS_CHECK(detector.latest().chordIndex >= 0);

    detector.reset();
    GS_CHECK_EQ(detector.latest().chordIndex, -1);
    GS_CHECK_NEAR(detector.latest().rms, 0.0f, 1e-9);
}

GS_TEST(DetectorNoiseGateIsConfigurable) {
    ChordDetector detector;
    std::vector<float> signal = gstest::renderChord(0, {0, 4, 7}, 48000);
    for (float& s : signal) s *= 0.02f;  // RMS is now a little under 0.01

    // With a permissive gate the chord is reported.
    detector.setNoiseGateRms(0.0f);
    detector.pushSamples(signal.data(), signal.size());
    GS_CHECK_STREQ(chordName(detector.latest().chordIndex), "C");

    // With a strict gate the same audio is rejected.
    detector.reset();
    detector.setNoiseGateRms(0.5f);
    detector.pushSamples(signal.data(), signal.size());
    GS_CHECK_EQ(detector.latest().chordIndex, -1);
}
