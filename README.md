# GuitarSalmon

A real-time guitar chord detector with practice tracking, for people learning the
instrument. Audio capture and all the signal processing happen in native C++; the UI
is Jetpack Compose.

Everything runs on-device. Audio is analysed as it arrives and is never recorded,
stored or transmitted.

## Features

- **Live Detect** — large chord readout, a confidence meter and an animated
  twelve-bar chroma visualiser.
- **Practice** — pick a chord or a sequence (`G - C - D - Em`, a blues in A, a jazz
  `ii - V - I`, …). Each target is marked correct once you hold it for half a second,
  and the time it took is recorded.
- **Progress** — total practice time, day streak, per-chord accuracy and average
  time-to-hit, a line chart of improvement over time, and a "chords to work on" list
  driven by your lowest accuracies.
- **Settings** — noise gate sensitivity with a live input level meter, a toggle for
  the chroma visualiser, and a data reset.

## Requirements

| Tool | Version |
| --- | --- |
| JDK | 17 |
| Android SDK platform | 34 |
| Android NDK | 26.1.10909125 (pinned in `app/build.gradle.kts`) |
| CMake | 3.22.1 |
| Gradle | 8.9 (via the wrapper) |
| Device | Android 8.0 / API 26 or newer, with a microphone |

The NDK and CMake are installed automatically by the Gradle build if they are
missing. To install them yourself:

```bash
sdkmanager "platforms;android-34" "ndk;26.1.10909125" "cmake;3.22.1"
```

## Build

Point the build at your SDK (skip this if `ANDROID_HOME` is already set):

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

Then:

```bash
./gradlew assembleDebug                  # debug APK (auto-bumps patch version)
./gradlew installDebug                   # build and install on a connected device
./gradlew assembleRelease                # release APK (unsigned)
```

The APK lands in `app/build/outputs/apk/`. Three ABIs are built: `arm64-v8a`,
`armeabi-v7a` and `x86_64` (the last one so it runs on an emulator).

### Versioning

`app/version.properties` holds the semantic version (`major.minor.patch`) and the
monotonic Android `versionCode`. Every `assemble` / `install` / `bundle` / `build`
bumps the **patch** and `versionCode` before the APK is stamped, so each rebuild
ships a newer version. IDE sync and other tasks leave the file alone.

```bash
./gradlew assembleDebug                         # 1.0.0 → 1.0.1
./gradlew assembleDebug -PversionBump=minor     # 1.0.1 → 1.1.0
./gradlew assembleDebug -PversionBump=major     # 1.1.0 → 2.0.0
./gradlew assembleDebug -PskipVersionBump=true  # rebuild without bumping
```

## Tests

The platform-independent DSP — chroma folding, chord template matching, the
detector's gating and hysteresis — is tested on the host. No device or emulator
needed, and it takes a couple of seconds:

```bash
./gradlew nativeTest        # or: ./tools/run_native_tests.sh
```

The tests build synthetic chords out of sine waves and push them through the real
pipeline. The broadest one sweeps all 12 roots across all 7 chord qualities and
asserts every one of the 84 chords is identified correctly.

## DSP pipeline

Capture is 48 kHz mono float, opened with Oboe in low-latency mode. The input preset
is `Unprocessed`, falling back to `VoiceRecognition` on devices that do not offer it:
both avoid the automatic gain control and noise suppression applied to normal
recording, which would pump levels and carve holes in the spectrum.

### 1. Capture (realtime thread)

Oboe's audio callback does exactly one thing: copy the incoming samples into a
lock-free single-producer/single-consumer ring buffer. No analysis, no allocation, no
locks — anything that could block would glitch the audio device. If the DSP thread
ever fell behind, the callback drops samples rather than waiting.

### 2. Framing (DSP thread)

A dedicated thread drains the ring buffer into an 8192-sample analysis window that
advances by a 2048-sample hop, so windows overlap by 75% and a new frame is produced
every ~42.7 ms (≈23 frames/second). Each window is multiplied by a Hann window before
transforming.

### 3. Spectrum

A real FFT (KissFFT, vendored in `cpp/third_party/kissfft`) gives 4097 magnitude bins.

### 4. Chroma

Bins between 80 Hz and 2000 Hz are folded into 12 pitch classes. The lower bound sits
just under the guitar's low E (82.4 Hz); above the upper bound harmonics dominate and
muddy the result.

The fold does **not** simply assign every bin to its nearest pitch class, because of a
hard resolution limit: at 48 kHz an 8192-point FFT has 5.9 Hz bins, while a semitone
near the low E spans only about 5 Hz. Bin-by-bin folding would therefore smear a
single low note across three or four adjacent pitch classes.

Instead the spectrum's local maxima are picked out, and each peak's frequency is
refined by parabolic interpolation over the log magnitudes of the three bins around
it. That pins a peak down to a small fraction of a bin — comfortably inside a
semitone — and the leakage bins beside a peak are not local maxima, so they never
contribute at all.

Each peak's magnitude is added to its nearest pitch class. The 12 sums are then
log-compressed with `log1p(γ·x)` and normalised so the largest is 1. Compression
matters because a plucked string's fundamental can be many times the amplitude of a
quietly fretted note; without it, one loud string masks the rest of the chord.

### 5. Smoothing

Chroma vectors are averaged over the last 4 frames. This costs a little latency and
removes a lot of frame-to-frame jitter.

### 6. Chord matching

Templates cover 12 roots × 7 qualities (major, minor, 7, maj7, min7, sus2, sus4) =
84 chords. Each is a 12-element pitch class profile, and the smoothed chroma is
compared against all of them by cosine similarity — which is scale invariant, so how
hard you strum does not affect the result. The best match and its similarity become
the chord and its confidence.

The root is weighted slightly above the other chord tones. Partly because in a guitar
voicing the root is usually the lowest and loudest note and its harmonics reinforce
its own pitch class; and partly because it resolves an otherwise exact tie. A sus2
chord and the sus4 a fifth above it contain the same three pitch classes — `Fsus2` and
`Csus4` are both {F, G, C} — so with purely binary templates 24 of the 84 chords would
be indistinguishable. Chroma discards octave and bass information, so the only thing
that can separate them is which pitch class the audio emphasises.

### 7. Noise gate and stability

Two filters sit on the output:

- **Noise gate** — frames whose RMS falls below a threshold (default 0.01, about
  −40 dBFS, adjustable in Settings) report "no chord" instead of matching whatever
  the room noise happens to resemble.
- **Stability hysteresis** — the reported chord only changes after a new candidate
  wins 3 consecutive frames. This is what stops the display flickering between
  neighbouring chords during a strum's attack transient.

### 8. Handing results to Kotlin

The DSP thread publishes its latest result under a short-lived mutex. Kotlin polls it
at 20 Hz through JNI, which keeps the DSP thread free of any JVM attachment cost, and
exposes it as a `StateFlow<ChordDetection>` carrying the chord name, confidence, RMS
and the 12 chroma values.

## Architecture

```
cpp/                        Native audio + DSP
  dsp/chroma.*              Hann window, peak picking, chroma folding, smoothing
  dsp/chord_matcher.*       Templates and cosine similarity matching
  dsp/chord_detector.*      Framing, noise gate, stability hysteresis
  audio/ring_buffer.h       Lock-free SPSC buffer
  audio/audio_engine.*      Oboe stream + DSP thread
  jni_bridge.cpp            JNI surface
  tests/                    Host-side unit tests
  third_party/kissfft/      Vendored real-FFT subset

app/src/main/java/com/guitarsalmon/
  audio/                    JNI wrapper, audio focus, device changes, detection Flow
  data/                     Room entities, DAO, repositories, settings
  domain/                   Pure logic: progress stats, practice state machine
  ui/                       Compose screens, ViewModels, theme, reusable components
```

The `dsp/` sources are deliberately free of Android, Oboe and JNI dependencies, which
is what lets them be compiled and tested on the host. Chroma folding and chord
matching are pure functions.

### Storage

Room, two tables:

- `sessions` — `id`, `startTime`, `durationMs`
- `chord_attempts` — `id`, `sessionId`, `targetChord`, `success`, `timeToHitMs`,
  `timestamp`

Aggregation that SQL does well (counts, per-chord averages) lives in the DAO.
Anything calendar-aware — the day streak, grouping attempts into days for the chart —
is done in Kotlin with `java.time` so it respects the device's time zone, and is
written as pure functions in `domain/ProgressStats.kt`.

### Lifecycle

- Capture stops in `MainActivity.onStop` and the stream is closed, not merely paused,
  so another app can take the microphone while GuitarSalmon is in the background.
- Audio focus is requested as `GAIN_TRANSIENT_EXCLUSIVE`, since anything else playing
  back would be picked up by the microphone and detected as chords. Losing focus stops
  capture.
- Input device changes (a headset or USB mic appearing or disappearing) surface as an
  Oboe disconnect error; the engine reopens a stream on the new default device while
  the DSP thread keeps running. If no input device is left, capture stops.
- An in-progress practice run ends if capture stops for any of these reasons, rather
  than leaving a target on screen that can never be hit.

## Tuning

The constants worth experimenting with:

| Constant | Where | Default |
| --- | --- | --- |
| Window / hop size | `cpp/dsp/chroma.h` | 8192 / 2048 |
| Analysed frequency range | `ChromaConfig` | 80–2000 Hz |
| Log compression γ | `ChromaConfig` | 100 |
| Smoothing frames | `cpp/dsp/chroma.h` | 4 |
| Stability frames | `cpp/dsp/chord_detector.h` | 3 |
| Noise gate RMS | `cpp/dsp/chord_detector.h` | 0.01 |
| Minimum confidence | `cpp/dsp/chord_detector.h` | 0.55 |
| Required hold time | `domain/PracticeSession.kt` | 500 ms |

## Known limitations

- Chroma discards octave and bass information, so chords that share a pitch class set
  can only be separated by which root the audio emphasises (see §6). Inversions and
  slash chords are not distinguished.
- The template set covers triads and sevenths only: no 6, 9, 11, 13, diminished or
  augmented chords.
- Tuning is assumed to be standard equal temperament at A4 = 440 Hz. A badly out of
  tune guitar will degrade detection.
- Single notes are matched against chord templates rather than reported as notes, so a
  lone string produces a low-confidence guess.

## Third-party code

- [Oboe](https://github.com/google/oboe) — Apache 2.0, consumed as a Maven dependency.
- [KissFFT](https://github.com/mborgerding/kissfft) — BSD-3-Clause, vendored subset in
  `cpp/third_party/kissfft` (licence retained in `COPYING`).
# guitar-salmon-android
