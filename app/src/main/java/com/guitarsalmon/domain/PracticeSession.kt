package com.guitarsalmon.domain

/** What feeding a detection frame into a [PracticeSession] produced. */
sealed interface PracticeEvent {

    /** Nothing changed; the target is still outstanding. */
    data object None : PracticeEvent

    /** The target was held long enough to count. */
    data class ChordHit(val chord: String, val timeToHitMs: Long) : PracticeEvent

    /** The target was given up on, either skipped or timed out. */
    data class ChordMissed(val chord: String) : PracticeEvent

    /** The last chord in the sequence was hit. */
    data class SequenceComplete(val chord: String, val timeToHitMs: Long) : PracticeEvent
}

/**
 * Drives one run through a chord sequence.
 *
 * A target counts as correct once it has been detected continuously for
 * [holdDurationMs]. Requiring it to be *held* rather than merely seen once is what
 * distinguishes actually fretting the chord from a transient that happened to
 * match while the learner's fingers were moving.
 *
 * Time is passed in explicitly rather than read from the clock, so the whole state
 * machine is deterministic and testable.
 */
class PracticeSession(
    val sequence: List<String>,
    private val holdDurationMs: Long = DEFAULT_HOLD_MS,
    private val minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
) {
    init {
        require(sequence.isNotEmpty()) { "A practice sequence needs at least one chord" }
    }

    /** Position in the sequence; equals sequence.size once the run is finished. */
    var currentIndex: Int = 0
        private set

    /** Chord the learner should be playing, or null when the run is finished. */
    val currentTarget: String?
        get() = sequence.getOrNull(currentIndex)

    val isComplete: Boolean get() = currentIndex >= sequence.size

    private var targetShownAtMs: Long = 0
    private var heldSinceMs: Long? = null

    /** (Re)starts the run from the first chord. */
    fun start(nowMs: Long) {
        currentIndex = 0
        targetShownAtMs = nowMs
        heldSinceMs = null
    }

    /**
     * Feeds one detection frame in.
     *
     * @param detectedChord the chord currently being detected, or null for none.
     */
    fun onDetection(detectedChord: String?, confidence: Float, nowMs: Long): PracticeEvent {
        val target = currentTarget ?: return PracticeEvent.None

        val matches = detectedChord == target && confidence >= minConfidence
        if (!matches) {
            // Any break in the chord restarts the hold timer.
            heldSinceMs = null
            return PracticeEvent.None
        }

        val heldSince = heldSinceMs ?: nowMs.also { heldSinceMs = it }
        if (nowMs - heldSince < holdDurationMs) return PracticeEvent.None

        val timeToHit = (nowMs - targetShownAtMs).coerceAtLeast(0)
        advance(nowMs)
        return if (isComplete) {
            PracticeEvent.SequenceComplete(target, timeToHit)
        } else {
            PracticeEvent.ChordHit(target, timeToHit)
        }
    }

    /** Abandons the current target and moves to the next one. */
    fun skip(nowMs: Long): PracticeEvent {
        val target = currentTarget ?: return PracticeEvent.None
        advance(nowMs)
        return PracticeEvent.ChordMissed(target)
    }

    /**
     * How far through the required hold the learner is, 0..1. Drives the progress
     * ring around the target chord.
     */
    fun holdProgress(nowMs: Long): Float {
        val heldSince = heldSinceMs ?: return 0f
        if (holdDurationMs <= 0) return 1f
        return ((nowMs - heldSince).toFloat() / holdDurationMs).coerceIn(0f, 1f)
    }

    /** Milliseconds the current target has been on screen. */
    fun elapsedOnTargetMs(nowMs: Long): Long = (nowMs - targetShownAtMs).coerceAtLeast(0)

    private fun advance(nowMs: Long) {
        currentIndex++
        targetShownAtMs = nowMs
        heldSinceMs = null
    }

    companion object {
        /** How long a chord must be held to count as correct. */
        const val DEFAULT_HOLD_MS = 500L

        /** Detections below this confidence do not count towards the hold. */
        const val DEFAULT_MIN_CONFIDENCE = 0.6f
    }
}
