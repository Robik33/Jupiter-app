package com.marketia.jupiter.core.engine

import javax.inject.Inject
import javax.inject.Singleton

enum class ToolType {
    RESPOND, SEARCH, SAVE_MEMORY, DISPATCH_BRIDGE,
    APPLY_VOICE, SELF_EVAL, OTA, CREATE_SKILL
}

data class SelectedTool(val type: ToolType, val reason: String)

@Singleton
class ToolSelector @Inject constructor() {

    fun select(intent: String, action: String?, params: Map<String, String>): SelectedTool = when {
        intent == "SELF_EVAL" || action == "RUN_SELF_EVAL" ->
            SelectedTool(ToolType.SELF_EVAL, "Autodiagnostico solicitado")
        intent == "VOICE_CUSTOMIZATION" && action == "APPLY_VOICE" ->
            SelectedTool(ToolType.APPLY_VOICE, "Ajuste de voz local")
        intent == "WEB_SEARCH" && action == "SEARCH" ->
            SelectedTool(ToolType.SEARCH, "Busqueda web")
        action == "DISPATCH_BRIDGE" ->
            SelectedTool(ToolType.DISPATCH_BRIDGE, "Tarea requiere Claude Code")
        intent == "CREATE_SKILL" || action == "CREATE_SKILL_ENTITY" ->
            SelectedTool(ToolType.CREATE_SKILL, "Crear skill en DB")
        action in listOf("SAVE_PROJECT", "SAVE_SYSTEM", "MEMORY_SAVE") ->
            SelectedTool(ToolType.SAVE_MEMORY, "Guardar en memoria")
        action == "OTA_READY" ->
            SelectedTool(ToolType.OTA, "Actualización disponible")
        else ->
            SelectedTool(ToolType.RESPOND, "Respuesta directa")
    }

    fun describeChain(): String = """
        Herramientas disponibles:
        · RESPOND — respuesta directa de IA
        · SEARCH — búsqueda web DuckDuckGo
        · SAVE_MEMORY — guardar en base de conocimiento
        · DISPATCH_BRIDGE — Claude Code daemon (código, builds, releases)
        · APPLY_VOICE — ajuste local de TTS
        · SELF_EVAL — autodiagnóstico del sistema
        · CREATE_SKILL — crear skill en Room DB
        · OTA — actualizar APK
    """.trimIndent()
}
