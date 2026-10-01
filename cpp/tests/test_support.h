#pragma once

// Minimal assertion/registration harness so the DSP tests need no third party
// test framework.

#include <cmath>
#include <cstdio>
#include <functional>
#include <string>
#include <vector>

namespace gstest {

struct TestCase {
    std::string name;
    std::function<void()> body;
};

/// Registry of every test declared with GS_TEST.
std::vector<TestCase>& registry();

/// Thrown by the check macros; caught by the runner.
struct Failure {
    std::string message;
};

void fail(const char* file, int line, const std::string& what);

/// Registers a test at static initialisation time.
struct Registrar {
    Registrar(const char* name, std::function<void()> body) {
        registry().push_back({name, std::move(body)});
    }
};

int runAll();

// --- Synthetic signal helpers -------------------------------------------------

/// Frequency in Hz of a MIDI note number (A4 = 69 = 440 Hz).
double midiToHz(double midi);

/// Adds a sine of the given frequency and amplitude to `out`.
void addSine(std::vector<float>& out, double hz, double amplitude, double sampleRate);

/// Amplitude of the root relative to the other chord tones in renderChord.
///
/// On a guitar the root is normally the lowest, loudest note of the voicing, and
/// the matcher's templates encode that same assumption via kRootWeight. Rendering
/// an emphasised root keeps the synthetic signals faithful to that, and it is
/// what distinguishes a sus2 chord from the sus4 a fifth above it, which share
/// all three pitch classes.
constexpr double kRootAmplitudeBoost = 2.0;

/// Renders a chord as a sum of sines, one per chord tone, with an emphasised root.
///
/// `root` is a pitch class (0 = C) placed in the octave starting at `baseMidi`,
/// and `intervals` are semitones above that root. Amplitudes are scaled so their
/// sum is 0.8, keeping the signal inside [-1, 1].
std::vector<float> renderChord(int root,
                               const std::vector<int>& intervals,
                               std::size_t numSamples,
                               double sampleRate = 48000.0,
                               int baseMidi = 48 /* C3 */);

}  // namespace gstest

#define GS_TEST(name)                                                       \
    static void name();                                                     \
    static ::gstest::Registrar gs_registrar_##name(#name, name);            \
    static void name()

#define GS_CHECK(cond)                                                      \
    do {                                                                    \
        if (!(cond)) ::gstest::fail(__FILE__, __LINE__, "expected: " #cond); \
    } while (false)

#define GS_CHECK_EQ(a, b)                                                   \
    do {                                                                    \
        const auto gs_a = (a);                                              \
        const auto gs_b = (b);                                              \
        if (!(gs_a == gs_b)) {                                              \
            ::gstest::fail(__FILE__, __LINE__,                              \
                           std::string(#a " == " #b " (left=") +            \
                               std::to_string(gs_a) + ", right=" +          \
                               std::to_string(gs_b) + ")");                 \
        }                                                                   \
    } while (false)

#define GS_CHECK_NEAR(a, b, tol)                                            \
    do {                                                                    \
        const double gs_a = static_cast<double>(a);                         \
        const double gs_b = static_cast<double>(b);                         \
        if (!(std::fabs(gs_a - gs_b) <= (tol))) {                           \
            ::gstest::fail(__FILE__, __LINE__,                              \
                           std::string(#a " ~= " #b " (left=") +            \
                               std::to_string(gs_a) + ", right=" +          \
                               std::to_string(gs_b) + ")");                 \
        }                                                                   \
    } while (false)

#define GS_CHECK_GT(a, b)                                                   \
    do {                                                                    \
        const double gs_a = static_cast<double>(a);                         \
        const double gs_b = static_cast<double>(b);                         \
        if (!(gs_a > gs_b)) {                                               \
            ::gstest::fail(__FILE__, __LINE__,                              \
                           std::string(#a " > " #b " (left=") +             \
                               std::to_string(gs_a) + ", right=" +          \
                               std::to_string(gs_b) + ")");                 \
        }                                                                   \
    } while (false)

#define GS_CHECK_STREQ(a, b)                                                \
    do {                                                                    \
        const std::string gs_a = (a);                                       \
        const std::string gs_b = (b);                                       \
        if (gs_a != gs_b) {                                                 \
            ::gstest::fail(__FILE__, __LINE__,                              \
                           "\"" + gs_a + "\" == \"" + gs_b + "\"");         \
        }                                                                   \
    } while (false)
