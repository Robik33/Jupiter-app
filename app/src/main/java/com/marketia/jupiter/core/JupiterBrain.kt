package com.marketia.jupiter.core

data class JupiterResponse(
    val orderReceived: String,
    val typeDetected: String,
    val nextAction: String,
    val status: String,
    val action: String? = null,
    val params: Map<String, String> = emptyMap()
) {
    fun toSpokenText(): String = nextAction
}

object JupiterBrain {

    fun process(input: String): JupiterResponse {
        val text = input.lowercase().trim()
        return when {
            // CODE_TASK before isVoice — "mejorar voz" = tarea de código vía bridge, no TTS local
            isCodeTask(text) -> resp(input, "CODE_TASK",
                "Tarea de mejora registrada. Enviando al bridge para implementación automática.",
                "DISPATCH_BRIDGE", mapOf("task" to input))

            // TTS local: solo ajustes explícitos de velocidad/tono
            isVoice(text) -> resp(input, "VOICE_CUSTOMIZATION",
                "Ajustando configuración TTS. Velocidad y tono actualizados.",
                "APPLY_VOICE", mapOf("speed" to "0.82", "pitch" to "0.90"))

            creates(text, "app", "aplicación", "aplicacion") -> resp(input, "CREATE_APP",
                "App registrada. Creando estructura en memoria.",
                "SAVE_PROJECT", mapOf("type" to "app", "name" to extractName(input)))

            creates(text, "skill") -> resp(input, "CREATE_SKILL",
                "Creando skill: ${extractName(input)}. Guardando en base de datos.",
                "CREATE_SKILL_ENTITY", mapOf("type" to "skill", "name" to extractName(input)))

            creates(text, "sistema") -> resp(input, "CREATE_SYSTEM",
                "Sistema registrado. Documentando arquitectura.",
                "SAVE_SYSTEM", mapOf("type" to "sistema", "name" to extractName(input)))

            creates(text, "bot") -> resp(input, "CREATE_BOT",
                "Bot registrado. Arquitectura guardada en agentes.",
                "SAVE_PROJECT", mapOf("type" to "bot", "name" to extractName(input)))

            creates(text, "agente", "agent") -> resp(input, "CREATE_AGENT",
                "Agente IA definido. Registrando capacidades.",
                "SAVE_PROJECT", mapOf("type" to "agente", "name" to extractName(input)))

            creates(text, "automatizacion", "automatización", "flujo") -> resp(input, "CREATE_SYSTEM",
                "Flujo de automatización registrado.",
                "SAVE_SYSTEM", mapOf("type" to "automatizacion", "name" to extractName(input)))

            creates(text, "empresa", "negocio", "startup") -> resp(input, "CREATE_SYSTEM",
                "Modelo de negocio registrado en sistema.",
                "SAVE_SYSTEM", mapOf("type" to "empresa", "name" to extractName(input)))

            creates(text, "curso", "contenido", "módulo", "modulo") -> resp(input, "CREATE_SKILL",
                "Contenido del curso registrado como skill.",
                "CREATE_SKILL_ENTITY", mapOf("type" to "curso", "name" to extractName(input)))

            creates(text, "sitio", "landing", "página", "pagina") -> resp(input, "CREATE_APP",
                "Sitio web registrado. Definiendo arquitectura frontend.",
                "SAVE_PROJECT", mapOf("type" to "web", "name" to extractName(input)))

            creates(text, "marketing", "campaña", "campana", "anuncio", "ads") -> resp(input, "CREATE_SYSTEM",
                "Estrategia de marketing registrada.",
                "SAVE_SYSTEM", mapOf("type" to "marketing", "name" to extractName(input)))

            creates(text, "trading", "inversión", "inversion") -> resp(input, "CREATE_SYSTEM",
                "Sistema de trading registrado.",
                "SAVE_SYSTEM", mapOf("type" to "trading", "name" to extractName(input)))

            isSearch(text) -> resp(input, "WEB_SEARCH",
                "Buscando: ${extractSearchQuery(text)}",
                "SEARCH", mapOf("query" to extractSearchQuery(text)))

            isGitHub(text) -> resp(input, "GITHUB_ACTION",
                "Acción GitHub encolada. ClaudeCodeBridge procesará la tarea.",
                "DISPATCH_BRIDGE", mapOf("action" to "inspect"))

            isBuild(text) -> resp(input, "BUILD_APK",
                "Build encolado. Daemon PC compilará el APK.",
                "BUILD_APK", mapOf("target" to "release"))

            // INGEST_LINK: URL real detectada → bridge para análisis
            text.contains("http") || text.contains("www.") -> resp(input, "INGEST_LINK",
                "Enlace detectado. Analizando contenido vía bridge.",
                "DISPATCH_BRIDGE", mapOf("url" to (extractUrl(text) ?: input), "content" to input))

            text.contains("guardar") || text.contains("recordar") ||
            text.contains("link") || text.contains("url") -> resp(input, "MEMORY_SAVE",
                "Guardando en memoria.",
                "MEMORY_SAVE", mapOf("content" to input))

            isGreeting(text) -> resp(input, "GREETING",
                "JÚPITER activo. Órdenes disponibles: crear skill/app/sistema, mejorar [algo], analizar enlace, buscar [tema].")

            else -> resp(input, "UNKNOWN",
                "Sin coincidencia local. Di: 'crear skill [nombre]', 'mejorar [algo]', o pega un enlace. Activa IA en Ajustes para interpretación libre.",
                "CLARIFY")
        }
    }

    private fun resp(
        input: String, intent: String, response: String,
        action: String? = null, params: Map<String, String> = emptyMap()
    ) = JupiterResponse(
        orderReceived = input,
        typeDetected  = intent,
        nextAction    = response,
        status        = when { intent == "UNKNOWN" -> "ESPERANDO"; action != null -> "EJECUTANDO"; else -> "COMPLETADO" },
        action        = action,
        params        = params
    )

    private fun isCodeTask(text: String): Boolean {
        val codeVerbs = listOf("mejorar","fix","corregir","optimizar","refactorizar","arreglar","actualizar")
        // Exclude pure TTS commands (those are handled by isVoice)
        val isTtsSetting = listOf("ajust","configur","velocidad","pitch","speed").any { text.contains(it) }
        return codeVerbs.any { text.contains(it) } && !isTtsSetting
    }

    private fun isVoice(text: String): Boolean {
        val voiceWords = listOf("voz","tts","hablas","suenas","sonar","speech")
        // Only explicit TTS setting adjustments, not "mejorar" (that becomes CODE_TASK)
        val settingWords = listOf("ajust","cambi","configur","velocidad","tono","pitch","speed","otra","distinta")
        return voiceWords.any { text.contains(it) } && settingWords.any { text.contains(it) }
    }

    private fun extractUrl(text: String): String? {
        return Regex("https?://[^\\s]+|www\\.[^\\s]+").find(text)?.value
    }

    private fun isSearch(text: String) =
        text.startsWith("busca") || text.startsWith("buscar") ||
        text.contains("busca ") || text.contains("encuentra ")

    private fun isGitHub(text: String) =
        text.contains("github") || text.contains("repositorio") ||
        text.contains("commit") || text.contains("push repo")

    private fun isBuild(text: String) =
        (text.contains("build") || text.contains("compilar")) && text.contains("apk")

    private fun isGreeting(text: String) =
        text.startsWith("hola") || text.startsWith("hey") || text.startsWith("buenas") ||
        text == "jupiter" || text == "júpiter"

    private fun creates(text: String, vararg keywords: String): Boolean {
        val verbs = listOf("crear","create","construir","build","necesito","quiero","hacer",
            "desarrollar","generar","diseñar","armar","implementar","iniciar","lanzar","quiero un")
        val hasVerb = verbs.any { text.contains(it) }
        val hasKw   = keywords.any { text.contains(it) }
        val direct  = keywords.any { text == it || text.startsWith("$it ") }
        return (hasVerb && hasKw) || direct
    }

    private fun extractName(input: String): String {
        val lower = input.lowercase()
        val verbs = listOf("crear","create","construir","necesito","quiero","hacer","desarrollar","generar")
        var result = input
        for (v in verbs) {
            if (lower.contains(v)) {
                result = input.substringAfter(v, input).trim().replaceFirstChar { it.uppercase() }
                break
            }
        }
        return result.take(60)
    }

    private fun extractSearchQuery(text: String): String {
        val prefixes = listOf("busca sobre ","buscar sobre ","busca ","buscar ","encuentra ")
        for (p in prefixes) { if (text.contains(p)) return text.substringAfter(p).trim() }
        return text
    }
}
