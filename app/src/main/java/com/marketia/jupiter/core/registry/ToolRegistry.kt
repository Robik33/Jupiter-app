package com.marketia.jupiter.core.registry

import com.marketia.jupiter.core.skills.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRegistry @Inject constructor(
    val webSearch:     WebSearchSkill,
    val memory:        MemorySkill,
    val voiceSettings: VoiceSettingsSkill,
    val gitHub:        GitHubSkill,
    val apkBuild:      APKBuildSkill
) {
    fun getByAction(action: String): JupiterSkill? = when (action) {
        "SEARCH"                         -> webSearch
        "MEMORY_SAVE", "SAVE_PROJECT"    -> memory
        "APPLY_VOICE"                    -> voiceSettings
        "GITHUB"                         -> gitHub
        "BUILD_APK"                      -> apkBuild
        else                             -> null
    }
}
