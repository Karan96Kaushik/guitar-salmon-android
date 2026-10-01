package com.guitarsalmon.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One practice session, created when the user starts practising and updated with
 * its duration when they stop.
 */
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch milliseconds at which the session started. */
    val startTime: Long,
    /** How long the session lasted, in milliseconds. */
    val durationMs: Long = 0,
)

/**
 * One attempt at a target chord during practice.
 *
 * @param success whether the chord was detected stably before the user moved on.
 * @param timeToHitMs milliseconds from the chord being shown to it being detected,
 *   or `null` when the attempt was skipped or abandoned.
 */
@Entity(
    tableName = "chord_attempts",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            // Attempts are meaningless without their session, so let the database
            // clean them up rather than relying on callers to do it.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("targetChord"), Index("timestamp")],
)
data class ChordAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val targetChord: String,
    val success: Boolean,
    @ColumnInfo(name = "timeToHitMs") val timeToHitMs: Long?,
    /** Epoch milliseconds at which the attempt finished. */
    val timestamp: Long,
)

/** Aggregated accuracy and speed for a single target chord. */
data class ChordStatsRow(
    val targetChord: String,
    val attempts: Int,
    val successes: Int,
    /** Mean time-to-hit over successful attempts only; null when there are none. */
    val averageTimeToHitMs: Double?,
)

/** Minimal projection of an attempt, used to build the improvement chart. */
data class AttemptPoint(
    val timestamp: Long,
    val success: Boolean,
    val timeToHitMs: Long?,
)
