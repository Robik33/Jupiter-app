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

    private val GREETINGS = listOf(
        "Aquí. ¿Qué construimos?",
        "Dime.",
        "¿En qué trabajamos?",
        "A tus órdenes.",
        "Presente.",
        "¿Qué necesitas?"
    )

    fun process(input: String): JupiterResponse {
        val text = input.lowercase().trim()
        return when {
            // CODE_TASK before isVoice — "mejorar voz" = tarea de código vía bridge, no TTS local
            isCodeTask(text) -> resp(input, "CODE_TASK",
                "Entendido. Abriendo tarea en el bridge — el daemon se encargará.",
                "DISPATCH_BRIDGE", mapOf("task" to input))

            // TTS local: solo ajustes explícitos de velocidad/tono
            isVoice(text) -> resp(input, "VOICE_CUSTOMIZATION",
                "Ajustado.",
                "APPLY_VOICE", mapOf("speed" to "0.85", "pitch" to "0.93"))

            creates(text, "app", "aplicación", "aplicacion") -> resp(input, "CREATE_APP",
                "App '${extractName(input)}' registrada.",
                "SAVE_PROJECT", mapOf("type" to "app", "name" to extractName(input)))

            creates(text, "skill") -> resp(input, "CREATE_SKILL",
                "Skill '${extractName(input)}' añadido a tu base de conocimiento.",
                "CREATE_SKILL_ENTITY", mapOf("type" to "skill", "name" to extractName(input)))

            creates(text, "sistema") -> resp(input, "CREATE_SYSTEM",
                "Sistema '${extractName(input)}' documentado.",
                "SAVE_SYSTEM", mapOf("type" to "sistema", "name" to extractName(input)))

            creates(text, "bot") -> resp(input, "CREATE_BOT",
                "Bot '${extractName(input)}' registrado como agente.",
                "SAVE_PROJECT", mapOf("type" to "bot", "name" to extractName(input)))

            creates(text, "agente", "agent") -> resp(input, "CREATE_AGENT",
                "Agente '${extractName(input)}' definido y registrado.",
                "SAVE_PROJECT", mapOf("type" to "agente", "name" to extractName(input)))

            creates(text, "automatizacion", "automatización", "flujo") -> resp(input, "CREATE_SYSTEM",
                "Flujo registrado. Puedo desarrollarlo si me das más contexto.",
                "SAVE_SYSTEM", mapOf("type" to "automatizacion", "name" to extractName(input)))

            creates(text, "empresa", "negocio", "startup") -> resp(input, "CREATE_SYSTEM",
                "Modelo de negocio registrado. Dime más para ampliar el sistema.",
                "SAVE_SYSTEM", mapOf("type" to "empresa", "name" to extractName(input)))

            creates(text, "curso", "contenido", "módulo", "modulo") -> resp(input, "CREATE_SKILL",
                "Contenido registrado como skill de conocimiento.",
                "CREATE_SKILL_ENTITY", mapOf("type" to "curso", "name" to extractName(input)))

            creates(text, "sitio", "landing", "página", "pagina") -> resp(input, "CREATE_APP",
                "Sitio '${extractName(input)}' registrado.",
                "SAVE_PROJECT", mapOf("type" to "web", "name" to extractName(input)))

            creates(text, "marketing", "campaña", "campana", "anuncio", "ads") -> resp(input, "CREATE_SYSTEM",
                "Estrategia de marketing registrada en sistema.",
                "SAVE_SYSTEM", mapOf("type" to "marketing", "name" to extractName(input)))

            creates(text, "trading", "inversión", "inversion") -> resp(input, "CREATE_SYSTEM",
                "Sistema de trading registrado.",
                "SAVE_SYSTEM", mapOf("type" to "trading", "name" to extractName(input)))

            isSearch(text) -> resp(input, "WEB_SEARCH",
                "Buscando \"${extractSearchQuery(text)}\"...",
                "SEARCH", mapOf("query" to extractSearchQuery(text)))

            isGitHub(text) -> resp(input, "GITHUB_ACTION",
                "Tarea GitHub abierta. El daemon la procesará.",
                "DISPATCH_BRIDGE", mapOf("action" to "inspect"))

            isBuild(text) -> resp(input, "BUILD_APK",
                "Build encolado. El daemon compilará el APK.",
                "BUILD_APK", mapOf("target" to "release"))

            // INGEST_LINK: URL real → bridge
            text.contains("http") || text.contains("www.") -> resp(input, "INGEST_LINK",
                "Enlace recibido. El daemon lo analizará y extraerá conocimiento.",
                "DISPATCH_BRIDGE", mapOf("url" to (extractUrl(text) ?: input), "content" to input))

            text.contains("guardar") || text.contains("recordar") ||
            text.contains("link") || text.contains("url") -> resp(input, "MEMORY_SAVE",
                "Guardado en memoria.",
                "MEMORY_SAVE", mapOf("content" to input))

            isGreeting(text) -> resp(input, "GREETING", GREETINGS.random())

            else -> resp(input, "UNKNOWN",
                "No lo reconozco en modo local. Di: 'crear skill', 'mejorar algo', o pega un enlace. " +
                "Para lenguaje libre, activa un proveedor IA en JÚPITER.",
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
