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
            isVoice(text) -> resp(input, "VOICE_CUSTOMIZATION",
                "Ajustando mi voz para sonar más natural. Velocidad y tono optimizados.",
                "APPLY_VOICE", mapOf("speed" to "0.82", "pitch" to "0.90"))

            creates(text, "app", "aplicación", "aplicacion") -> resp(input, "CREATE_APP",
                "Iniciando proyecto de app. Registrando arquitectura en memoria.",
                "SAVE_PROJECT", mapOf("type" to "app", "name" to extractName(input)))

            creates(text, "skill") -> resp(input, "CREATE_SKILL",
                "Creando nuevo skill. Define las capacidades que necesitas.",
                "SAVE_PROJECT", mapOf("type" to "skill", "name" to extractName(input)))

            creates(text, "sistema") -> resp(input, "CREATE_SYSTEM",
                "Diseñando arquitectura del sistema. Documentando en memoria.",
                "SAVE_SYSTEM", mapOf("type" to "sistema", "name" to extractName(input)))

            creates(text, "bot") -> resp(input, "CREATE_BOT",
                "Iniciando arquitectura del bot. Registrando en agentes.",
                "SAVE_PROJECT", mapOf("type" to "bot", "name" to extractName(input)))

            creates(text, "agente", "agent") -> resp(input, "CREATE_AGENT",
                "Definiendo capacidades del agente IA. Registrando en sistema.",
                "SAVE_PROJECT", mapOf("type" to "agente", "name" to extractName(input)))

            creates(text, "automatizacion", "automatización", "flujo") -> resp(input, "CREATE_SYSTEM",
                "Diseñando flujo de automatización. Documentando componentes.",
                "SAVE_SYSTEM", mapOf("type" to "automatizacion", "name" to extractName(input)))

            creates(text, "empresa", "negocio", "startup") -> resp(input, "CREATE_SYSTEM",
                "Estructurando modelo de negocio. Documentando en sistema.",
                "SAVE_SYSTEM", mapOf("type" to "empresa", "name" to extractName(input)))

            creates(text, "curso", "contenido", "módulo", "modulo") -> resp(input, "CREATE_SKILL",
                "Estructurando contenido del curso. Registrando módulos.",
                "SAVE_PROJECT", mapOf("type" to "curso", "name" to extractName(input)))

            creates(text, "sitio", "landing", "página", "pagina") -> resp(input, "CREATE_APP",
                "Iniciando diseño del sitio web. Definiendo arquitectura frontend.",
                "SAVE_PROJECT", mapOf("type" to "web", "name" to extractName(input)))

            creates(text, "marketing", "campaña", "campana", "anuncio", "ads") -> resp(input, "CREATE_SYSTEM",
                "Diseñando estrategia de marketing. Documentando en sistema.",
                "SAVE_SYSTEM", mapOf("type" to "marketing", "name" to extractName(input)))

            creates(text, "trading", "inversión", "inversion") -> resp(input, "CREATE_SYSTEM",
                "Estructurando sistema de trading. Registrando en finanzas.",
                "SAVE_SYSTEM", mapOf("type" to "trading", "name" to extractName(input)))

            isSearch(text) -> resp(input, "WEB_SEARCH",
                "Buscando información...",
                "SEARCH", mapOf("query" to extractSearchQuery(text)))

            isGitHub(text) -> resp(input, "GITHUB_ACTION",
                "Acción GitHub registrada. Conecta ClaudeCodeBridge para ejecución real.",
                "GITHUB", mapOf("action" to "inspect"))

            isBuild(text) -> resp(input, "BUILD_APK",
                "Build encolado. Requiere ClaudeCodeBridge activo en PC.",
                "BUILD_APK", mapOf("target" to "release"))

            text.contains("guardar") || text.contains("recordar") -> resp(input, "MEMORY_SAVE",
                "Guardando en memoria...",
                "MEMORY_SAVE", mapOf("content" to input))

            text.contains("http") || text.contains("link") || text.contains("url") -> resp(input, "MEMORY_SAVE",
                "Guardando enlace en memoria.",
                "MEMORY_SAVE", mapOf("content" to input, "type" to "link"))

            isGreeting(text) -> resp(input, "GREETING",
                "Listo para crear. ¿Qué necesitas construir hoy?")

            else -> resp(input, "UNKNOWN",
                "¿Quieres que lo convierta en un skill, ajuste de voz, proyecto o búsqueda web?",
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

    private fun isVoice(text: String): Boolean {
        val voiceWords = listOf("voz","tts","hablas","suenas","sonar","speech")
        val changeWords = listOf("humana","natural","ajust","cambi","crea","mejor","configur","otra","distinta")
        return voiceWords.any { text.contains(it) } && changeWords.any { text.contains(it) }
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
