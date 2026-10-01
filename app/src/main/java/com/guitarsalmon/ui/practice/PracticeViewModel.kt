package com.guitarsalmon.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guitarsalmon.audio.AudioEngineState
import com.guitarsalmon.audio.ChordAudioEngine
import com.guitarsalmon.audio.ChordDetection
import com.guitarsalmon.data.PracticeRepository
import com.guitarsalmon.domain.PracticeEvent
import com.guitarsalmon.domain.PracticeSequence
import com.guitarsalmon.domain.PracticeSequences
import com.guitarsalmon.domain.PracticeSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One finished attempt, kept for the on-screen log. */
data class AttemptOutcome(
    val chord: String,
    val success: Boolean,
    val timeToHitMs: Long?,
)

data class PracticeUiState(
    val sequence: PracticeSequence = PracticeSequences.presets[1],
    val isRunning: Boolean = false,
    val isComplete: Boolean = false,
    val targetChord: String? = null,
    val position: Int = 0,
    /** How far through the required hold the current chord is, 0..1. */
    val holdProgress: Float = 0f,
    val detection: ChordDetection = ChordDetection.NONE,
    val outcomes: List<AttemptOutcome> = emptyList(),
    val engineState: AudioEngineState = AudioEngineState.Stopped,
) {
    val total: Int get() = sequence.chords.size

    /** True while the detected chord matches the target, before the hold completes. */
    val isOnTarget: Boolean get() = holdProgress > 0f

    val hits: Int get() = outcomes.count { it.success }
}

/**
 * Runs a practice session: shows a target, waits for it to be held, records the
 * attempt and advances.
 *
 * The run loop ticks at a fixed interval and reads the latest detection each time,
 * rather than reacting to detection emissions. That keeps the hold timer moving even
 * when the incoming frames are identical, which is exactly what happens during
 * silence.
 */
class PracticeViewModel(
    private val audioEngine: ChordAudioEngine,
    private val repository: PracticeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    val presets: List<PracticeSequence> = PracticeSequences.presets
    val singleChords: List<String> = PracticeSequences.singleChordOptions

    private var runJob: Job? = null
    private var practiceSession: PracticeSession? = null

    /** Row id of the session currently being recorded, if any. */
    private var currentSessionId: Long? = null

    init {
        viewModelScope.launch {
            audioEngine.state.collect { state ->
                _uiState.value = _uiState.value.copy(engineState = state)

                // The engine is stopped when the app goes to the background, and can
                // fail if the mic is taken away. Either way the run cannot continue, so
                // end it rather than leaving a target on screen that can never be hit.
                if (state != AudioEngineState.Running && _uiState.value.isRunning) {
                    stop()
                }
            }
        }
    }

    fun selectSequence(sequence: PracticeSequence) {
        if (_uiState.value.isRunning) return
        _uiState.value = _uiState.value.copy(
            sequence = sequence,
            isComplete = false,
            outcomes = emptyList(),
            position = 0,
            targetChord = null,
        )
    }

    /** Builds a one-chord "sequence" so drilling a single shape reuses the same path. */
    fun selectSingleChord(chord: String) {
        selectSequence(
            PracticeSequence(
                name = chord,
                chords = listOf(chord),
                description = "Single chord drill",
            )
        )
    }

    fun start() {
        if (runJob?.isActive == true) return
        val sequence = _uiState.value.sequence
        val session = PracticeSession(sequence.chords)
        practiceSession = session

        runJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            val sessionId = repository.startSession(startedAt)
            currentSessionId = sessionId

            if (!audioEngine.start()) {
                // Nothing to practise with; close out the empty session row.
                repository.finishSession(sessionId, 0)
                currentSessionId = null
                _uiState.value = _uiState.value.copy(isRunning = false)
                return@launch
            }

            session.start(System.currentTimeMillis())
            _uiState.value = _uiState.value.copy(
                isRunning = true,
                isComplete = false,
                outcomes = emptyList(),
                position = 0,
                targetChord = session.currentTarget,
                holdProgress = 0f,
            )

            try {
                while (isActive && !session.isComplete) {
                    val now = System.currentTimeMillis()
                    val frame = audioEngine.detection.value

                    when (val event = session.onDetection(frame.chordName, frame.confidence, now)) {
                        is PracticeEvent.ChordHit ->
                            recordOutcome(sessionId, event.chord, true, event.timeToHitMs, now)

                        is PracticeEvent.SequenceComplete ->
                            recordOutcome(sessionId, event.chord, true, event.timeToHitMs, now)

                        is PracticeEvent.ChordMissed ->
                            recordOutcome(sessionId, event.chord, false, null, now)

                        PracticeEvent.None -> Unit
                    }

                    _uiState.value = _uiState.value.copy(
                        detection = frame,
                        targetChord = session.currentTarget,
                        position = session.currentIndex,
                        holdProgress = session.holdProgress(now),
                    )

                    delay(TICK_MS)
                }
            } finally {
                // The session row has to be closed out even if the screen is left or
                // the ViewModel is cleared mid-run.
                withContext(NonCancellable) {
                    repository.finishSession(sessionId, System.currentTimeMillis() - startedAt)
                    audioEngine.stop()
                }
                currentSessionId = null
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    isComplete = session.isComplete,
                    holdProgress = 0f,
                )
            }
        }
    }

    fun stop() {
        runJob?.cancel()
        runJob = null
    }

    /** Gives up on the current chord, records it as a miss and moves on. */
    fun skipCurrent() {
        val session = practiceSession ?: return
        val sessionId = currentSessionId ?: return
        if (!_uiState.value.isRunning) return

        val now = System.currentTimeMillis()
        val event = session.skip(now)
        if (event is PracticeEvent.ChordMissed) {
            // skip() has already advanced the target, so the run loop will not see
            // this event again; persist it here.
            viewModelScope.launch {
                recordOutcome(sessionId, event.chord, success = false, timeToHitMs = null, now = now)
            }
        }
    }

    private suspend fun recordOutcome(
        sessionId: Long,
        chord: String,
        success: Boolean,
        timeToHitMs: Long?,
        now: Long,
    ) {
        repository.recordAttempt(sessionId, chord, success, timeToHitMs, now)
        _uiState.value = _uiState.value.copy(
            outcomes = _uiState.value.outcomes + AttemptOutcome(chord, success, timeToHitMs),
        )
    }

    override fun onCleared() {
        super.onCleared()
        runJob?.cancel()
    }

    private companion object {
        /** Run loop interval; matches the audio engine's publish rate. */
        const val TICK_MS = 50L
    }
}
