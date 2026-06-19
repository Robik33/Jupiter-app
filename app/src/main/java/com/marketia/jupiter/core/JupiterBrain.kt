package com.marketia.jupiter.core

data class JupiterResponse(
    val orderReceived: String,
    val typeDetected: String,
    val nextAction: String,
    val status: String
) {
    fun toSpokenText(): String =
        "Orden recibida. Tipo: $typeDetected. $nextAction Estado: $status."
}

object JupiterBrain {

    fun process(input: String): JupiterResponse {
        val t = input.lowercase().trim()

        return when {
            creates(t, "app", "aplicación", "aplicacion", "aplicativo", "mobile", "android") ->
                respond(input, "APLICACIÓN",
                    "Definir plataforma (Android/iOS/Web), funcionalidades core, stack tecnológico y arquitectura inicial.",
                    "INICIANDO ARQUITECTURA")

            creates(t, "skill", "conocimiento", "especialidad", "dominio") ->
                respond(input, "SKILL",
                    "Definir dominio de conocimiento, subtemas, fuentes y estructura de aprendizaje.",
                    "ESTRUCTURANDO CONOCIMIENTO")

            creates(t, "sistema financiero", "trading system") ||
            (creates(t, "sistema") && contains(t, "financiero", "trading", "capital", "inversión")) ->
                respond(input, "SISTEMA FINANCIERO",
                    "Definir estrategia, activos objetivo, gestión de riesgo y métricas de rendimiento.",
                    "ANALIZANDO MERCADO")

            creates(t, "sistema", "system", "arquitectura", "infraestructura") ->
                respond(input, "SISTEMA",
                    "Definir componentes, flujo de datos, integraciones necesarias y métricas de éxito.",
                    "DISEÑANDO ARQUITECTURA")

            creates(t, "bot", "chatbot") ->
                respond(input, "BOT / AGENTE",
                    "Definir personalidad, capacidades, canal de despliegue y modelo de lenguaje base.",
                    "CONFIGURANDO AGENTE")

            creates(t, "agente", "agent") ->
                respond(input, "AGENTE IA",
                    "Definir modelo base (Claude/GPT/Gemini), herramientas disponibles, contexto y límites de acción. Disponible en V0.4.",
                    "PREPARANDO [V0.4]")

            t.contains("automatizar") || creates(t, "automatización", "automatizacion", "automation") ->
                respond(input, "AUTOMATIZACIÓN",
                    "Mapear proceso manual, identificar triggers y acciones, seleccionar herramientas (Zapier/n8n/Make).",
                    "ANALIZANDO FLUJO")

            t.contains("analizar") && contains(t, "link", "url", "enlace", "podcast", "video", "artículo", "articulo", "canal") ->
                respond(input, "ANÁLISIS DE CONTENIDO",
                    "Ve a MEMORIA → Gestor de Links → pega el enlace con categoría. JÚPITER lo indexará para extracción de conocimiento en V0.4.",
                    "REDIRIGIENDO A MEMORIA")

            t.contains("conectar") && contains(t, "api", "servicio", "webhook") ||
            creates(t, "conector", "integración", "integracion") ->
                respond(input, "INTEGRACIÓN API",
                    "Identificar endpoint, método de autenticación, estructura del payload y casos de uso de la integración.",
                    "PREPARANDO CONECTOR")

            creates(t, "empresa", "negocio", "startup", "compañía", "compania") ->
                respond(input, "EMPRESA / STARTUP",
                    "Definir modelo de negocio, propuesta de valor, mercado objetivo, estructura legal y plan de tracción inicial.",
                    "CONSTRUYENDO ESTRUCTURA")

            creates(t, "curso", "contenido educativo", "training", "programa") ->
                respond(input, "CURSO / CONTENIDO",
                    "Definir audiencia objetivo, módulos temáticos, formato de entrega y metodología de evaluación.",
                    "DISEÑANDO CURRICULUM")

            creates(t, "sitio web", "sitio", "web", "landing", "página", "pagina", "website") ->
                respond(input, "SITIO WEB",
                    "Definir propósito, tecnología base (Next.js/Astro/React), diseño, hosting y dominio.",
                    "ARQUITECTURANDO SITIO")

            creates(t, "marketing", "campaña", "campana", "contenido", "estrategia") ->
                respond(input, "SISTEMA DE MARKETING",
                    "Definir audiencia, canales (Meta/TikTok/LinkedIn), embudo de conversión y métricas de seguimiento.",
                    "DISEÑANDO ESTRATEGIA")

            creates(t, "trading", "estrategia de trading", "señal", "senal") ->
                respond(input, "SISTEMA DE TRADING",
                    "Definir activos, timeframe, lógica de entrada/salida, gestión de riesgo y backtesting.",
                    "CONFIGURANDO ESTRATEGIA")

            t.contains("hola") || t.contains("jupiter") || t.contains("júpiter") || t.contains("inicio") ->
                respond(input, "INICIALIZACIÓN",
                    "JÚPITER activo. Di 'crear app', 'crear sistema', 'crear skill', 'analizar link' o cualquier creación que necesites.",
                    "EN ESPERA")

            t.contains("memoria") || t.contains("skills") || t.contains("links") ->
                respond(input, "CONSULTA DE MEMORIA",
                    "Ve a la pestaña SKILLS para ver conocimientos o MEMORIA para links, proyectos y sistemas.",
                    "REDIRIGIENDO")

            else ->
                respond(input, "ORDEN NO RECONOCIDA",
                    "Especifica qué deseas crear. Ejemplos: 'Crear una app de finanzas', 'Crear un sistema de marketing', 'Crear un skill de IA', 'Analizar este link'.",
                    "REFORMULANDO")
        }
    }

    private fun respond(order: String, type: String, action: String, status: String) =
        JupiterResponse(orderReceived = order, typeDetected = type, nextAction = action, status = status)

    private fun creates(text: String, vararg keywords: String): Boolean {
        val createVerbs = listOf("crear", "create", "construir", "build", "necesito", "quiero",
            "hacer", "desarrollar", "generar", "diseñar", "armar", "implementar")
        val hasVerb = createVerbs.any { text.contains(it) }
        val hasKeyword = keywords.any { text.contains(it) }
        val isDirectCommand = keywords.any { text == it || text.startsWith(it) || text.endsWith(it) }
        return (hasVerb && hasKeyword) || isDirectCommand
    }

    private fun contains(text: String, vararg keywords: String): Boolean =
        keywords.any { text.contains(it) }
}
