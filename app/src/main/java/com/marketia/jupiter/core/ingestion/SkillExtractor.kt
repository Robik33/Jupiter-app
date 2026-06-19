package com.marketia.jupiter.core.ingestion

import com.marketia.jupiter.core.ai.JupiterAIClient
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class ExtractedSkill(
    val name: String,
    val category: String,
    val resumen: String,
    val conocimientos: String,
    val fuente: String
)

@Singleton
class SkillExtractor @Inject constructor(
    private val aiClient: JupiterAIClient
) {
    suspend fun extract(analysis: LinkAnalysis): ExtractedSkill {
        val prompt = """
Analiza este recurso y extrae un skill de aprendizaje.
Responde SOLO con JSON válido en este formato exacto:
{
  "name": "SKILL [TEMA_PRINCIPAL]",
  "category": "[categoria en minusculas: ia, marketing, sistemas, etc]",
  "resumen": "[1-2 oraciones sobre qué enseña este recurso]",
  "conocimientos": "[concepto1],[concepto2],[concepto3],[concepto4],[concepto5]",
  "fuente": "${analysis.url}"
}

Recurso tipo: ${analysis.type.name}
Título: ${analysis.title}
Descripción: ${analysis.metaDescription}
Contenido: ${analysis.rawContent.take(500)}
        """.trimIndent()

        return runCatching {
            val response = aiClient.call(prompt) ?: return@runCatching fallback(analysis)
            val jsonStr = extractJson(response)
            val json = JSONObject(jsonStr)
            ExtractedSkill(
                name        = json.optString("name",         "SKILL ${analysis.title.take(20).uppercase()}"),
                category    = json.optString("category",     detectCategory(analysis)),
                resumen     = json.optString("resumen",      analysis.metaDescription.ifBlank { analysis.title }),
                conocimientos = json.optString("conocimientos", ""),
                fuente      = analysis.url
            )
        }.getOrElse { fallback(analysis) }
    }

    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end   = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else text
    }

    private fun fallback(analysis: LinkAnalysis) = ExtractedSkill(
        name          = "SKILL ${analysis.title.take(30).uppercase()}",
        category      = detectCategory(analysis),
        resumen       = analysis.metaDescription.ifBlank { "Recurso de tipo ${analysis.type.name}" },
        conocimientos = "",
        fuente        = analysis.url
    )

    private fun detectCategory(analysis: LinkAnalysis): String {
        val text = (analysis.title + " " + analysis.rawContent).lowercase()
        return when {
            text.contains("ia") || text.contains("machine learning") || text.contains("llm") -> "ia"
            text.contains("marketing") || text.contains("ventas") || text.contains("ads") -> "marketing"
            text.contains("python") || text.contains("kotlin") || text.contains("código") -> "sistemas"
            text.contains("salud") || text.contains("medicina") || text.contains("bioelectricidad") -> "salud"
            text.contains("finanza") || text.contains("trading") || text.contains("inversión") -> "finanzas"
            text.contains("seguridad") || text.contains("hack") || text.contains("ciberseguridad") -> "ciberseguridad"
            else -> "general"
        }
    }
}
