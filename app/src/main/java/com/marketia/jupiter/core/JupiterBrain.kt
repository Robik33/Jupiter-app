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
        "Aqui. Que construimos?",
        "Dime.",
        "En que trabajamos?",
        "A tus ordenes.",
        "Presente.",
        "Que necesitas?"
    )

    fun process(input: String): JupiterResponse {
        val text = normalizeText(input.lowercase().trim())
        return when {
            // Voice improvement -> CODE_TASK via bridge (checked before TTS local)
            isVoiceImprovement(text) -> resp(input, "CODE_TASK",
                "Entendido. Voy a modificar la voz - el daemon se encargara.",
                "DISPATCH_BRIDGE", mapOf("task" to input, "category" to "voice"))

            // Debug/malfunction detection -> CODE_TASK via bridge
            isDebugTask(text) -> resp(input, "CODE_TASK",
                "Detectado problema. Abriendo tarea de correccion en el bridge.",
                "DISPATCH_BRIDGE", mapOf("task" to input, "category" to "debug"))

            // General code tasks: modifica, cambia, mejora, arregla, etc.
            isCodeTask(text) -> resp(input, "CODE_TASK",
                "Entendido. Abriendo tarea en el bridge - el daemon se encargara.",
                "DISPATCH_BRIDGE", mapOf("task" to input))

            // TTS local: only explicit speed/pitch adjustments (not "mejora", "hazla humana")
            isVoiceLocalAdjust(text) -> resp(input, "VOICE_CUSTOMIZATION",
                "Ajustado.",
                "APPLY_VOICE", mapOf("speed" to "0.85", "pitch" to "0.93"))

            creates(text, "app", "aplicacion") -> resp(input, "CREATE_APP",
                "App '${extractName(input)}' registrada.",
                "SAVE_PROJECT", mapOf("type" to "app", "name" to extractName(input)))

            creates(text, "skill") -> resp(input, "CREATE_SKILL",
                "Skill '${extractName(input)}' anadido a tu base de conocimiento.",
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

            creates(text, "automatizacion", "flujo") -> resp(input, "CREATE_SYSTEM",
                "Flujo registrado. Puedo desarrollarlo si me das mas contexto.",
                "SAVE_SYSTEM", mapOf("type" to "automatizacion", "name" to extractName(input)))

            creates(text, "empresa", "negocio", "startup") -> resp(input, "CREATE_SYSTEM",
                "Modelo de negocio registrado. Dime mas para ampliar el sistema.",
                "SAVE_SYSTEM", mapOf("type" to "empresa", "name" to extractName(input)))

            creates(text, "curso", "contenido", "modulo") -> resp(input, "CREATE_SKILL",
                "Contenido registrado como skill de conocimiento.",
                "CREATE_SKILL_ENTITY", mapOf("type" to "curso", "name" to extractName(input)))

            creates(text, "sitio", "landing", "pagina") -> resp(input, "CREATE_APP",
                "Sitio '${extractName(input)}' registrado.",
                "SAVE_PROJECT", mapOf("type" to "web", "name" to extractName(input)))

            creates(text, "marketing", "campana", "anuncio", "ads") -> resp(input, "CREATE_SYSTEM",
                "Estrategia de marketing registrada en sistema.",
                "SAVE_SYSTEM", mapOf("type" to "marketing", "name" to extractName(input)))

            creates(text, "trading", "inversion") -> resp(input, "CREATE_SYSTEM",
                "Sistema de trading registrado.",
                "SAVE_SYSTEM", mapOf("type" to "trading", "name" to extractName(input)))

            isSearch(text) -> resp(input, "WEB_SEARCH",
                "Buscando \"${extractSearchQuery(text)}\"...",
                "SEARCH", mapOf("query" to extractSearchQuery(text)))

            isGitHub(text) -> resp(input, "GITHUB_ACTION",
                "Tarea GitHub abierta. El daemon la procesara.",
                "DISPATCH_BRIDGE", mapOf("action" to "inspect"))

            isBuild(text) -> resp(input, "BUILD_APK",
                "Build encolado. El daemon compilara el APK.",
                "BUILD_APK", mapOf("target" to "release"))

            text.contains("http") || text.contains("www.") -> resp(input, "INGEST_LINK",
                "Enlace recibido. El daemon lo analizara y extraera conocimiento.",
                "DISPATCH_BRIDGE", mapOf("url" to (extractUrl(text) ?: input), "content" to input))

            text.contains("guardar") || text.contains("recordar") ||
            text.contains("link") || text.contains("url") -> resp(input, "MEMORY_SAVE",
                "Guardado en memoria.",
                "MEMORY_SAVE", mapOf("content" to input))

            isGreeting(text) -> resp(input, "GREETING", GREETINGS.random())

            // Never UNKNOWN - always classify by approximation
            else -> intelligentFallback(input, text)
        }
    }

    private fun resp(
        input: String, intent: String, response: String,
        action: String? = null, params: Map<String, String> = emptyMap()
    ) = JupiterResponse(
        orderReceived = input,
        typeDetected  = intent,
        nextAction    = response,
        status        = if (action != null) "EJECUTANDO" else "COMPLETADO",
        action        = action,
        params        = params
    )

    private fun normalizeText(s: String): String = s
        .replace('a', 'a').let { t ->
            t.map { c ->
                when (c) {
                    'á', 'Á' -> 'a'
                    'é', 'É' -> 'e'
                    'í', 'Í' -> 'i'
                    'ó', 'Ó' -> 'o'
                    'ú', 'Ú' -> 'u'
                    'ü', 'Ü' -> 'u'
                    'ñ', 'Ñ' -> 'n'
                    '¿', '¡' -> ' '
                    else -> c
                }
            }.joinToString("")
        }

    private fun isVoiceImprovement(text: String): Boolean {
        val voiceWords = listOf("voz", "tts", "hablas", "suenas", "sonar", "speech", "hablar")
        val qualityWords = listOf(
            "humana", "human", "natural", "expresiva", "fluida",
            "robot", "monotona", "monotono", "robotica",
            "gusta", "agrada", "mejor"
        )
        return voiceWords.any { text.contains(it) } && qualityWords.any { text.contains(it) }
    }

    private fun isDebugTask(text: String): Boolean {
        val debugPhrases = listOf(
            "no funciona", "no sirve", "no va", "no me gusta",
            "no responde", "no abre", "no carga", "esto no",
            "falla", "fallo", "error", "bug", "roto", "broken",
            "problema", "se rompe", "crashea", "crash", "se cierra"
        )
        return debugPhrases.any { text.contains(it) }
    }

    private fun isCodeTask(text: String): Boolean {
        val codeVerbs = listOf(
            "mejorar", "mejora", "fix", "corregir", "corrige",
            "optimizar", "optimiza", "refactorizar", "arreglar", "arregla",
            "actualizar", "actualiza", "modifica", "modificar",
            "cambia", "cambiar", "implementa", "implementar",
            "integra", "integrar", "conecta", "conectar",
            "analiza", "analizar", "refactoriza"
        )
        val isTtsSetting = listOf("velocidad", "pitch", "speed", "ajust", "configur").any { text.contains(it) }
        return codeVerbs.any { text.contains(it) } && !isTtsSetting
    }

    private fun isVoiceLocalAdjust(text: String): Boolean {
        val voiceWords = listOf("voz", "tts", "hablas", "speech")
        val settingWords = listOf("ajust", "cambi", "configur", "velocidad", "tono", "pitch", "speed", "otra", "distinta")
        return voiceWords.any { text.contains(it) } && settingWords.any { text.contains(it) }
    }

    private fun intelligentFallback(input: String, text: String): JupiterResponse {
        val actionVerbs = listOf(
            "mejora", "modifica", "corrige", "arregla", "actualiza", "optimiza",
            "haz", "cambia", "integra", "analiza", "conecta", "implementa"
        )
        val linkWords = listOf("link", "url", "articulo", "pagina", "video", "recurso")
        val skillWords = listOf("skill", "aprende", "ensena", "conocimiento", "aprend", "ensename")
        return when {
            actionVerbs.any { text.contains(it) } ->
                resp(input, "CODE_TASK",
                    "Accion detectada. Creando tarea en el bridge.",
                    "DISPATCH_BRIDGE", mapOf("task" to input))
            linkWords.any { text.contains(it) } ->
                resp(input, "INGEST_LINK",
                    "Recurso detectado. Enviando al bridge para analisis.",
                    "DISPATCH_BRIDGE", mapOf("url" to input, "content" to input))
            skillWords.any { text.contains(it) } ->
                resp(input, "CREATE_SKILL",
                    "Creando skill: ${extractName(input)}.",
                    "CREATE_SKILL_ENTITY", mapOf("name" to extractName(input), "type" to "general"))
            else ->
                resp(input, "AI_CHAT",
                    "Sin conexion para responder eso. Prueba: crear skill, mejorar algo, o pega un enlace.",
                    null, mapOf("query" to input))
        }
    }

    private fun extractUrl(text: String): String? =
        Regex("https?://[^\\s]+|www\\.[^\\s]+").find(text)?.value

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
        text == "jupiter"

    private fun creates(text: String, vararg keywords: String): Boolean {
        val verbs = listOf("crear","create","construir","build","necesito","quiero","hacer",
            "desarrollar","generar","disenar","armar","implementar","iniciar","lanzar","quiero un")
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
