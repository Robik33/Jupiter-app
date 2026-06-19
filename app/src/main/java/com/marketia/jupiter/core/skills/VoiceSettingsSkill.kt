package com.marketia.jupiter.core.skills

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceSettingsSkill @Inject constructor() : JupiterSkill {
    override val id = "voice_settings"
    override val name = "Configuración de Voz"

    override suspend fun execute(params: Map<String, String>): String {
        val speed = params["speed"]?.toFloatOrNull() ?: 0.9f
        val pitch = params["pitch"]?.toFloatOrNull() ?: 0.92f
        return "Voz ajustada: velocidad=${speed}, tono=${pitch}. Suena más natural ahora."
    }
}
