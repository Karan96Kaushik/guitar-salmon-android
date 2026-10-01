package com.guitarsalmon.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeDao {

    @Insert
    suspend fun insertSession(session: Session): Long

    @Query("UPDATE sessions SET durationMs = :durationMs WHERE id = :sessionId")
    suspend fun updateSessionDuration(sessionId: Long, durationMs: Long)

    @Insert
    suspend fun insertAttempt(attempt: ChordAttempt): Long

    /** Total practice time across every session, in milliseconds. */
    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM sessions")
    fun totalPracticeMs(): Flow<Long>

    @Query("SELECT COUNT(*) FROM sessions")
    fun sessionCount(): Flow<Int>

    /**
     * Start times of every session, oldest first.
     *
     * Day bucketing for the practice streak happens in Kotlin rather than SQL so
     * it uses the device's time zone and calendar rules correctly.
     */
    @Query("SELECT startTime FROM sessions ORDER BY startTime ASC")
    fun sessionStartTimes(): Flow<List<Long>>

    /** Per-chord accuracy and average time-to-hit, over all time. */
    @Query(
        """
        SELECT targetChord,
               COUNT(*) AS attempts,
               SUM(CASE WHEN success THEN 1 ELSE 0 END) AS successes,
               AVG(CASE WHEN success THEN timeToHitMs ELSE NULL END) AS averageTimeToHitMs
        FROM chord_attempts
        GROUP BY targetChord
        ORDER BY targetChord ASC
        """
    )
    fun chordStats(): Flow<List<ChordStatsRow>>

    /** Attempts recorded at or after `since`, oldest first. */
    @Query(
        """
        SELECT timestamp, success, timeToHitMs
        FROM chord_attempts
        WHERE timestamp >= :since
        ORDER BY timestamp ASC
        """
    )
    fun attemptsSince(since: Long): Flow<List<AttemptPoint>>

    @Query("DELETE FROM chord_attempts")
    suspend fun deleteAllAttempts()

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()
}
