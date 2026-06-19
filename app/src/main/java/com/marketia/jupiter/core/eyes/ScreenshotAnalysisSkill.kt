package com.marketia.jupiter.core.eyes

import com.marketia.jupiter.core.ai.JupiterAIClient
import com.marketia.jupiter.core.skills.JupiterSkill
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenshotAnalysisSkill @Inject constructor(
    private val aiClient: JupiterAIClient
) : JupiterSkill {
    override val id   = "screenshot_analysis"
    override val name = "Analisis de Pantalla"

    override suspend fun execute(params: Map<String, String>): String {
        val description = params["description"] ?: return "Sin descripcion de pantalla."
        // V0.5: text-based analysis via AI. Vision (base64 image) in V0.6.
        val prompt = "Analiza esta descripcion de pantalla y sugiere mejoras para JUPITER: $description"
        return runCatching {
            aiClient.call(prompt) ?: "Analisis local: revisa la pantalla manualmente."
        }.getOrDefault("Error al analizar. Revisa la conexion AI.")
    }
}
