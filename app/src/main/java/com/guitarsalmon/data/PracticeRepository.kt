package com.guitarsalmon.data

import com.guitarsalmon.domain.ChordPerformance
import com.guitarsalmon.domain.DailyProgressPoint
import com.guitarsalmon.domain.ProgressStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/** Everything the progress screen needs, in one snapshot. */
data class ProgressSummary(
    val totalPracticeMs: Long = 0,
    val sessionCount: Int = 0,
    val streakDays: Int = 0,
    val performances: List<ChordPerformance> = emptyList(),
    val chordsToWorkOn: List<ChordPerformance> = emptyList(),
    val dailyProgress: List<DailyProgressPoint> = emptyList(),
) {
    val overallAccuracy: Float get() = ProgressStats.overallAccuracy(performances)
    val hasData: Boolean get() = sessionCount > 0 || performances.isNotEmpty()
}

/**
 * Reads and writes practice history.
 *
 * Aggregation that SQL does well (counts, averages per chord) stays in the DAO;
 * anything calendar-aware is done here with [ProgressStats] so it respects the
 * device's time zone.
 */
class PracticeRepository(
    private val dao: PracticeDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /** Starts a session and returns its id. */
    suspend fun startSession(startTime: Long): Long = dao.insertSession(Session(startTime = startTime))

    suspend fun finishSession(sessionId: Long, durationMs: Long) {
        dao.updateSessionDuration(sessionId, durationMs)
    }

    suspend fun recordAttempt(
        sessionId: Long,
        targetChord: String,
        success: Boolean,
        timeToHitMs: Long?,
        timestamp: Long,
    ) {
        dao.insertAttempt(
            ChordAttempt(
                sessionId = sessionId,
                targetChord = targetChord,
                success = success,
                timeToHitMs = timeToHitMs,
                timestamp = timestamp,
            )
        )
    }

    fun totalPracticeMs(): Flow<Long> = dao.totalPracticeMs()

    fun chordPerformances(): Flow<List<ChordPerformance>> =
        dao.chordStats().map { ProgressStats.toPerformance(it) }

    fun streakDays(today: () -> LocalDate = { LocalDate.now(zone) }): Flow<Int> =
        dao.sessionStartTimes().map { starts ->
            ProgressStats.calculateStreak(ProgressStats.practiceDays(starts, zone), today())
        }

    /** Daily average time-to-hit over the last `days` days, for the chart. */
    fun dailyProgress(days: Long = CHART_WINDOW_DAYS): Flow<List<DailyProgressPoint>> {
        val since = ProgressStats.startOfDayMillisAgo(days, zone)
        return dao.attemptsSince(since).map { ProgressStats.dailyProgress(it, zone) }
    }

    fun sessionCount(): Flow<Int> = dao.sessionCount()

    /** Deletes all sessions and attempts. */
    suspend fun resetAll() {
        // Attempts first: the cascade would handle it, but being explicit keeps the
        // intent obvious and works regardless of foreign key enforcement.
        dao.deleteAllAttempts()
        dao.deleteAllSessions()
    }

    companion object {
        /** How far back the improvement chart looks. */
        const val CHART_WINDOW_DAYS = 30L
    }
}
