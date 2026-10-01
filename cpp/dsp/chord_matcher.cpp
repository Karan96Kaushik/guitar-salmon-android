#include "dsp/chord_matcher.h"

namespace gs {
namespace {

const char* const kPitchClassNames[kNumPitchClasses] = {
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
};

constexpr ChordType kAllChordTypes[kNumChordTypes] = {
    ChordType::Major,
    ChordType::Minor,
    ChordType::Dominant7,
    ChordType::Major7,
    ChordType::Minor7,
    ChordType::Sus2,
    ChordType::Sus4,
};

std::vector<ChordTemplate> buildChordTemplates() {
    std::vector<ChordTemplate> templates;
    templates.reserve(kNumChordTemplates);

    for (int root = 0; root < kNumPitchClasses; ++root) {
        for (int t = 0; t < kNumChordTypes; ++t) {
            const ChordType type = kAllChordTypes[t];
            ChordTemplate tmpl;
            tmpl.root = root;
            tmpl.type = type;
            tmpl.name = std::string(kPitchClassNames[root]) + chordTypeSuffix(type);
            for (int interval : chordTypeIntervals(type)) {
                const int pc = (root + interval) % kNumPitchClasses;
                tmpl.weights[pc] = (interval == 0) ? kRootWeight : kChordToneWeight;
            }
            templates.push_back(std::move(tmpl));
        }
    }
    return templates;
}

}  // namespace

const char* pitchClassName(int pc) {
    if (pc < 0 || pc >= kNumPitchClasses) return "?";
    return kPitchClassNames[pc];
}

const std::vector<int>& chordTypeIntervals(ChordType type) {
    static const std::vector<int> kMajor{0, 4, 7};
    static const std::vector<int> kMinor{0, 3, 7};
    static const std::vector<int> kDominant7{0, 4, 7, 10};
    static const std::vector<int> kMajor7{0, 4, 7, 11};
    static const std::vector<int> kMinor7{0, 3, 7, 10};
    static const std::vector<int> kSus2{0, 2, 7};
    static const std::vector<int> kSus4{0, 5, 7};

    switch (type) {
        case ChordType::Major: return kMajor;
        case ChordType::Minor: return kMinor;
        case ChordType::Dominant7: return kDominant7;
        case ChordType::Major7: return kMajor7;
        case ChordType::Minor7: return kMinor7;
        case ChordType::Sus2: return kSus2;
        case ChordType::Sus4: return kSus4;
    }
    return kMajor;
}

const char* chordTypeSuffix(ChordType type) {
    switch (type) {
        case ChordType::Major: return "";
        case ChordType::Minor: return "m";
        case ChordType::Dominant7: return "7";
        case ChordType::Major7: return "maj7";
        case ChordType::Minor7: return "m7";
        case ChordType::Sus2: return "sus2";
        case ChordType::Sus4: return "sus4";
    }
    return "";
}

const std::vector<ChordTemplate>& chordTemplates() {
    static const std::vector<ChordTemplate> kTemplates = buildChordTemplates();
    return kTemplates;
}

int chordCount() {
    return static_cast<int>(chordTemplates().size());
}

const char* chordName(int index) {
    const auto& templates = chordTemplates();
    if (index < 0 || index >= static_cast<int>(templates.size())) return "";
    return templates[static_cast<std::size_t>(index)].name.c_str();
}

int chordIndex(int root, ChordType type) {
    return root * kNumChordTypes + static_cast<int>(type);
}

ChordMatch matchChroma(const float* chroma) {
    const auto& templates = chordTemplates();
    ChordMatch best;
    for (std::size_t i = 0; i < templates.size(); ++i) {
        const float sim = cosineSimilarity(chroma, templates[i].weights, kNumPitchClasses);
        if (sim > best.confidence) {
            best.confidence = sim;
            best.index = static_cast<int>(i);
        }
    }
    return best;
}

}  // namespace gs
