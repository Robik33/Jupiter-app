package com.marketia.jupiter.core.skills

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class APKBuildSkill @Inject constructor() : JupiterSkill {
    override val id = "apk_build"
    override val name = "APK Builder"

    override suspend fun execute(params: Map<String, String>): String {
        val target  = params["target"]  ?: "release"
        val version = params["version"] ?: "next"
        return "Build $target v$version encolado. Requiere ClaudeCodeBridge activo en PC para compilación real."
    }
}
