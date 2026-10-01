#pragma once

// Chord template matching.
//
// A chord template is a 12-element binary pitch class profile: 1 for every pitch
// class the chord contains, 0 elsewhere. Matching is cosine similarity between a
// measured chroma vector and every template, which is scale invariant -- so the
// overall loudness of the input does not affect the result.

#include <cstddef>
#include <string>
#include <vector>

#include "dsp/chroma.h"

namespace gs {

/// Chord qualities recognised by the matcher, in template-generation order.
enum class ChordType {
    Major,
    Minor,
    Dominant7,
    Major7,
    Minor7,
    Sus2,
    Sus4,
};

/// Number of chord qualities.
constexpr int kNumChordTypes = 7;

/// 12 roots x 7 qualities.
constexpr int kNumChordTemplates = kNumPitchClasses * kNumChordTypes;

/// Template weight of the chord's root.
///
/// The root is weighted above the other chord tones for two reasons. In a guitar
/// voicing the root is usually the lowest and loudest note, and its harmonics
/// reinforce its own pitch class, so a heavier root genuinely fits the measured
/// chroma better. It also breaks an otherwise exact tie: a sus2 chord and the
/// sus4 chord a fifth above it contain the same three pitch classes (Fsus2 and
/// Csus4 are both F, G, C), so with purely binary templates those 24 chords would
/// be indistinguishable. Emphasising the root resolves the pair in favour of
/// whichever root the audio actually emphasises.
constexpr float kRootWeight = 1.0f;

/// Template weight of every non-root chord tone.
constexpr float kChordToneWeight = 0.8f;

struct ChordTemplate {
    std::string name;   ///< Display name, e.g. "Am7", "F#maj7", "C".
    int root = 0;       ///< Root pitch class, 0 = C.
    ChordType type = ChordType::Major;
    float weights[kNumPitchClasses] = {};
};

/// Name of pitch class `pc` (0 = C) using sharps, or "?" when out of range.
const char* pitchClassName(int pc);

/// Semitone intervals above the root for a chord quality.
const std::vector<int>& chordTypeIntervals(ChordType type);

/// Suffix appended to the root name for a chord quality ("" for major).
const char* chordTypeSuffix(ChordType type);

/// All 84 templates, generated once on first use. Ordered root-major: index
/// `root * kNumChordTypes + type`.
const std::vector<ChordTemplate>& chordTemplates();

/// Total number of templates (== kNumChordTemplates).
int chordCount();

/// Display name of template `index`, or "" when out of range.
const char* chordName(int index);

/// Index of the template with the given root and quality.
int chordIndex(int root, ChordType type);

struct ChordMatch {
    int index = -1;         ///< Template index, or -1 when no match was possible.
    float confidence = 0.0f;  ///< Cosine similarity of the winning template, 0..1.
};

/// Returns the template most similar to `chroma` (12 values). If every
/// similarity is zero -- for example when `chroma` is all zeroes -- the returned
/// index is -1.
///
/// Pure: no state is kept between calls.
ChordMatch matchChroma(const float* chroma);

}  // namespace gs
