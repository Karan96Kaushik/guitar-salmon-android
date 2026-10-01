#include "tests/test_support.h"

#include <cmath>

namespace gstest {

std::vector<TestCase>& registry() {
    static std::vector<TestCase> cases;
    return cases;
}

void fail(const char* file, int line, const std::string& what) {
    throw Failure{std::string(file) + ":" + std::to_string(line) + ": " + what};
}

double midiToHz(double midi) {
    return 440.0 * std::pow(2.0, (midi - 69.0) / 12.0);
}

void addSine(std::vector<float>& out, double hz, double amplitude, double sampleRate) {
    const double twoPiFOverSr = 2.0 * M_PI * hz / sampleRate;
    for (std::size_t i = 0; i < out.size(); ++i) {
        out[i] += static_cast<float>(amplitude * std::sin(twoPiFOverSr * static_cast<double>(i)));
    }
}

std::vector<float> renderChord(int root,
                               const std::vector<int>& intervals,
                               std::size_t numSamples,
                               double sampleRate,
                               int baseMidi) {
    std::vector<float> signal(numSamples, 0.0f);

    // Budget the 0.8 total amplitude across the tones, giving the root a larger share.
    const double shares =
        kRootAmplitudeBoost + static_cast<double>(intervals.size()) - 1.0;
    const double unitAmplitude = 0.8 / shares;

    for (int interval : intervals) {
        const double midi = static_cast<double>(baseMidi + root + interval);
        const double amplitude =
            unitAmplitude * (interval == 0 ? kRootAmplitudeBoost : 1.0);
        addSine(signal, midiToHz(midi), amplitude, sampleRate);
    }
    return signal;
}

int runAll() {
    int failures = 0;
    for (const TestCase& test : registry()) {
        try {
            test.body();
            std::printf("[  PASS  ] %s\n", test.name.c_str());
        } catch (const Failure& f) {
            std::printf("[  FAIL  ] %s\n           %s\n", test.name.c_str(), f.message.c_str());
            ++failures;
        }
    }
    std::printf("\n%zu test(s), %d failure(s)\n", registry().size(), failures);
    return failures == 0 ? 0 : 1;
}

}  // namespace gstest

int main() {
    return gstest::runAll();
}
