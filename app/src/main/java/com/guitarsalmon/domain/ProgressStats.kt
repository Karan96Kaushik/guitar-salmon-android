package com.guitarsalmon.domain

import com.guitarsalmon.data.AttemptPoint
import com.guitarsalmon.data.ChordStatsRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Accuracy and speed for one chord, derived from its raw attempt counts. */
data class ChordPerformance(
    val chord: String,
    val attempts: Int,
    val successes: Int,
    val averageTimeToHitMs: Long?,
) {
    /** Fraction of attempts that succeeded, 0..1. */
    val accuracy: Float
        get() = if (attempts == 0) 0f else successes.toFloat() / attempts
}

/** One point on the improvement chart: the mean time-to-hit for a single day. */
data class DailyProgressPoint(
    val date: LocalDate,
    val averageTimeToHitMs: Long,
    val attempts: Int,
)

/**
 * Pure functions behind the progress screen.
 *
 * Kept free of Android and Room types so the arithmetic can be reasoned about and
 * tested directly.
 */
object ProgressStats {

    /** Minimum attempts before a chord is worth calling out as needing work. */
    const val MIN_ATTEMPTS_FOR_ADVICE = 3

    fun toPerformance(rows: List<ChordStatsRow>): List<ChordPerformance> =
        rows.map { row ->
            ChordPerformance(
                chord = row.targetChord,
                attempts = row.attempts,
                successes = row.successes,
                averageTimeToHitMs = row.averageTimeToHitMs?.let { Math.round(it) },
            )
        }

    /**
     * Length of the current run of consecutive days with at least one session.
     *
     * A streak counts today if the user has practised today, and otherwise still
     * counts a streak ending yesterday: mid-day you have not necessarily lost a
     * streak just because you have not picked up the guitar yet. Two or more days
     * of silence breaks it.
     */
    fun calculateStreak(practiceDays: Set<LocalDate>, today: LocalDate): Int {
        if (practiceDays.isEmpty()) return 0

        val start = when {
            practiceDays.contains(today) -> today
            practiceDays.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> return 0
        }

        var streak = 0
        var day = start
        while (practiceDays.contains(day)) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** Distinct local dates on which a session was started. */
    fun practiceDays(sessionStartTimes: List<Long>, zone: ZoneId): Set<LocalDate> =
        sessionStartTimes
            .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .toSet()

    /**
     * Groups successful attempts by local date and averages their time-to-hit.
     *
     * Failed attempts are excluded: they have no time-to-hit, and including them
     * would make the chart measure two different things at once.
     */
    fun dailyProgress(points: List<AttemptPoint>, zone: ZoneId): List<DailyProgressPoint> =
        points
            .filter { it.success && it.timeToHitMs != null }
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .map { (date, dayPoints) ->
                DailyProgressPoint(
                    date = date,
                    averageTimeToHitMs = dayPoints.sumOf { it.timeToHitMs!! } / dayPoints.size,
                    attempts = dayPoints.size,
                )
            }
            .sortedBy { it.date }

    /**
     * Chords the learner should focus on: lowest accuracy first, with ties broken
     * by the slower average time-to-hit.
     *
     * Chords with very few attempts are ignored, because one unlucky miss should
     * not brand a chord as a weakness.
     */
    fun chordsToWorkOn(
        performances: List<ChordPerformance>,
        limit: Int = 3,
        minAttempts: Int = MIN_ATTEMPTS_FOR_ADVICE,
    ): List<ChordPerformance> =
        performances
            .filter { it.attempts >= minAttempts && it.accuracy < 1f }
            .sortedWith(
                compareBy<ChordPerformance> { it.accuracy }
                    .thenByDescending { it.averageTimeToHitMs ?: Long.MAX_VALUE },
            )
            .take(limit)

    /** Overall accuracy across every chord, 0..1. */
    fun overallAccuracy(performances: List<ChordPerformance>): Float {
        val attempts = performances.sumOf { it.attempts }
        if (attempts == 0) return 0f
        return performances.sumOf { it.successes }.toFloat() / attempts
    }

    /** Epoch milliseconds at the start of the day `days` ago, in `zone`. */
    fun startOfDayMillisAgo(days: Long, zone: ZoneId, now: Instant = Instant.now()): Long =
        now.atZone(zone)
            .truncatedTo(ChronoUnit.DAYS)
            .minusDays(days)
            .toInstant()
            .toEpochMilli()
}
