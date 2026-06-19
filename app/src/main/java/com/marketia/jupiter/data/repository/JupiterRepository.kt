package com.marketia.jupiter.data.repository

import com.marketia.jupiter.data.db.dao.*
import com.marketia.jupiter.data.entity.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JupiterRepository @Inject constructor(
    private val statDao: StatDao,
    private val habitDao: HabitDao,
    private val missionDao: MissionDao,
    private val progressDao: ProgressDao
) {
    val stats: Flow<List<StatEntity>> = statDao.getAll()
    val habits: Flow<List<HabitEntity>> = habitDao.getAll()
    val missions: Flow<List<MissionEntity>> = missionDao.getAll()
    val progress: Flow<UserProgressEntity?> = progressDao.get()

    suspend fun seedIfEmpty() {
        if (statDao.count() == 0) {
            statDao.insertAll(listOf(
                StatEntity("fuerza",       "Fuerza",       65),
                StatEntity("velocidad",    "Velocidad",    45),
                StatEntity("agilidad",     "Agilidad",     70),
                StatEntity("resistencia",  "Resistencia",  55),
                StatEntity("elasticidad",  "Elasticidad",  40),
                StatEntity("consciencia",  "Consciencia",  80)
            ))
        }
        if (habitDao.count() == 0) {
            habitDao.insertAll(listOf(
                HabitEntity(name = "Meditación 10 min",  icon = "🧘", completed = true,  streak = 7),
                HabitEntity(name = "Ejercicio 30 min",   icon = "💪", completed = false, streak = 3),
                HabitEntity(name = "Leer 20 páginas",    icon = "📖", completed = true,  streak = 12),
                HabitEntity(name = "Hidratación 2L",     icon = "💧", completed = false, streak = 5)
            ))
        }
        if (missionDao.count() == 0) {
            val today = java.time.LocalDate.now().toString()
            missionDao.insertAll(listOf(
                MissionEntity(title = "Completar 3 hábitos",      description = "Termina tus hábitos del día",     expReward = 50, completed = false, date = today),
                MissionEntity(title = "Meditación matutina",      description = "10 minutos de mindfulness",        expReward = 30, completed = true,  date = today),
                MissionEntity(title = "Entrenamiento de fuerza",  description = "Rutina completa de 30 min",        expReward = 40, completed = false, date = today)
            ))
        }
        if (progressDao.count() == 0) {
            progressDao.insert(UserProgressEntity(
                name = "Guerrero",
                level = 7,
                currentExp = 340,
                maxExp = 500
            ))
        }
    }

    suspend fun toggleHabit(habit: HabitEntity) {
        habitDao.update(habit.copy(completed = !habit.completed))
    }

    suspend fun toggleMission(mission: MissionEntity) {
        missionDao.update(mission.copy(completed = !mission.completed))
    }
}
