package com.marketia.jupiter.core.creator

import com.marketia.jupiter.core.ai.JupiterAIClient
import com.marketia.jupiter.data.entity.ProjectEntity
import com.marketia.jupiter.data.repository.JupiterRepository
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class ProjectBlueprint(
    val nombre: String,
    val tipo: String,
    val objetivo: String,
    val estado: String = "DISEÑO",
    val fases: List<String>,
    val recursos: List<String>
)

@Singleton
class ProjectCreatorEngine @Inject constructor(
    private val aiClient: JupiterAIClient,
    private val repository: JupiterRepository
) {
    suspend fun create(userRequest: String): ProjectBlueprint {
        val prompt = """
Eres JUPITER. El usuario quiere crear un proyecto. Genera un blueprint estructurado.
Responde SOLO con JSON válido en este formato exacto:
{
  "nombre": "[nombre corto del proyecto]",
  "tipo": "[app|bot|sistema|marketing|negocio|curso|web|trading]",
  "objetivo": "[objetivo principal en 1 oración]",
  "fases": ["Fase 1: ...", "Fase 2: ...", "Fase 3: ...", "Fase 4: ..."],
  "recursos": ["recurso1", "recurso2", "recurso3"]
}

Solicitud del usuario: $userRequest
        """.trimIndent()

        val blueprint = runCatching {
            val response = aiClient.call(prompt) ?: return@runCatching null
            val jsonStr = extractJson(response)
            val json = JSONObject(jsonStr)
            val fasesArr = json.optJSONArray("fases")
            val recursosArr = json.optJSONArray("recursos")
            ProjectBlueprint(
                nombre   = json.optString("nombre",   extractName(userRequest)),
                tipo     = json.optString("tipo",     detectType(userRequest)),
                objetivo = json.optString("objetivo", "Construir $userRequest"),
                fases    = (0 until (fasesArr?.length() ?: 0)).map { fasesArr!!.getString(it) }
                               .ifEmpty { defaultFases() },
                recursos = (0 until (recursosArr?.length() ?: 0)).map { recursosArr!!.getString(it) }
                               .ifEmpty { listOf("Definir durante Fase 1") }
            )
        }.getOrNull() ?: localBlueprint(userRequest)

        // Persist to Room
        repository.addProject(
            name        = blueprint.nombre,
            type        = blueprint.tipo,
            description = blueprint.objetivo
        )

        return blueprint
    }

    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end   = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else "{}"
    }

    private fun localBlueprint(request: String) = ProjectBlueprint(
        nombre   = extractName(request),
        tipo     = detectType(request),
        objetivo = "Construir: $request",
        fases    = defaultFases(),
        recursos = listOf("Por definir en planificación")
    )

    private fun defaultFases() = listOf(
        "Fase 1: Investigación y definición de requisitos",
        "Fase 2: Diseño de arquitectura y wireframes",
        "Fase 3: Desarrollo e implementación",
        "Fase 4: Pruebas, lanzamiento y ajustes"
    )

    private fun extractName(request: String): String {
        val verbs = listOf("crear","create","construir","necesito","quiero","hacer","desarrollar")
        var result = request
        for (v in verbs) {
            if (request.lowercase().contains(v)) {
                result = request.substringAfter(v, request).trim().replaceFirstChar { it.uppercase() }
                break
            }
        }
        return result.take(50)
    }

    private fun detectType(request: String): String {
        val lower = request.lowercase()
        return when {
            lower.contains("app") || lower.contains("aplicación") -> "app"
            lower.contains("bot") -> "bot"
            lower.contains("marketing") || lower.contains("campaña") -> "marketing"
            lower.contains("trading") || lower.contains("inversión") -> "trading"
            lower.contains("empresa") || lower.contains("negocio") -> "negocio"
            lower.contains("curso") || lower.contains("contenido") -> "curso"
            lower.contains("sitio") || lower.contains("web") -> "web"
            else -> "sistema"
        }
    }
}
