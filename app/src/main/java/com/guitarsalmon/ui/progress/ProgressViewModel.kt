package com.guitarsalmon.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guitarsalmon.data.PracticeRepository
import com.guitarsalmon.data.ProgressSummary
import com.guitarsalmon.domain.ProgressStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Combines every progress query into a single [ProgressSummary] for the screen. */
class ProgressViewModel(
    private val repository: PracticeRepository,
) : ViewModel() {

    val summary: StateFlow<ProgressSummary> = combine(
        repository.totalPracticeMs(),
        repository.sessionCount(),
        repository.streakDays(),
        repository.chordPerformances(),
        repository.dailyProgress(),
    ) { totalMs, sessions, streak, performances, daily ->
        ProgressSummary(
            totalPracticeMs = totalMs,
            sessionCount = sessions,
            streakDays = streak,
            performances = performances.sortedByDescending { it.attempts },
            chordsToWorkOn = ProgressStats.chordsToWorkOn(performances),
            dailyProgress = daily,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ProgressSummary(),
    )

    private companion object {
        /** Keeps the queries alive briefly across configuration changes. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
