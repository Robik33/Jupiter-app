package com.marketia.jupiter.core.registry

import com.marketia.jupiter.data.entity.SkillEntity
import com.marketia.jupiter.data.repository.JupiterRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillRegistry @Inject constructor(
    private val repository: JupiterRepository
) {
    val skills: Flow<List<SkillEntity>> = repository.skills

    fun relevantCategory(intent: String): String = when (intent) {
        "CREATE_APP", "CREATE_SYSTEM", "BUILD_APK", "GITHUB_ACTION" -> "sistemas"
        "CREATE_BOT", "CREATE_AGENT", "VOICE_CUSTOMIZATION"         -> "ia"
        "WEB_SEARCH"                                                 -> "ia"
        "CREATE_SKILL"                                               -> "ia"
        else                                                         -> "general"
    }
}
