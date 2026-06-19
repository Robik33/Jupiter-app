package com.marketia.jupiter.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketia.jupiter.data.entity.HabitEntity
import com.marketia.jupiter.data.entity.MissionEntity
import com.marketia.jupiter.data.repository.JupiterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: JupiterRepository
) : ViewModel() {

    val stats = repository.stats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val habits = repository.habits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val missions = repository.missions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val progress = repository.progress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { repository.seedIfEmpty() }
    }

    fun toggleHabit(habit: HabitEntity) = viewModelScope.launch { repository.toggleHabit(habit) }
    fun toggleMission(mission: MissionEntity) = viewModelScope.launch { repository.toggleMission(mission) }
}
