package com.marketia.jupiter.core.skills

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubSkill @Inject constructor() : JupiterSkill {
    override val id = "github"
    override val name = "GitHub"

    override suspend fun execute(params: Map<String, String>): String {
        val action = params["action"] ?: "inspect"
        val repo   = params["repo"]   ?: "Robik33/Jupiter-app"
        return when (action) {
            "inspect"  -> "Inspeccionando repo $repo. Conecta via ClaudeCodeBridge para acceso completo."
            "issue"    -> "Creando issue en $repo: ${params["title"] ?: "Sin título"}."
            "release"  -> "Release en $repo v${params["version"] ?: "0.0.0"} preparado."
            else       -> "GitHub action '$action' en $repo registrado."
        }
    }
}
