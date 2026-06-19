package com.marketia.jupiter.core.skills

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSearchSkill @Inject constructor() : JupiterSkill {
    override val id = "web_search"
    override val name = "Búsqueda Web"

    private val http = OkHttpClient()

    override suspend fun execute(params: Map<String, String>): String {
        val query = params["query"] ?: return "Sin query de búsqueda."
        return withContext(Dispatchers.IO) {
            runCatching {
                val encoded = query.replace(" ", "+")
                val req = Request.Builder()
                    .url("https://api.duckduckgo.com/?q=$encoded&format=json&no_redirect=1&no_html=1")
                    .header("User-Agent", "JUPITER-Android/0.4")
                    .build()
                val resp = http.newCall(req).execute()
                val body = resp.body?.string() ?: return@runCatching "Sin respuesta."
                val json = JSONObject(body)
                val abstract = json.optString("Abstract")
                val related = json.optJSONArray("RelatedTopics")
                    ?.let { arr -> (0 until minOf(3, arr.length()))
                        .mapNotNull { arr.optJSONObject(it)?.optString("Text") }
                        .filter { it.isNotBlank() }
                        .joinToString("\n• ", prefix = "\n• ")
                    } ?: ""
                if (abstract.isNotBlank()) "$abstract$related"
                else if (related.isNotBlank()) "Resultados para \"$query\":$related"
                else "No se encontraron resultados directos para \"$query\". Intenta una búsqueda más específica."
            }.getOrElse { "Error de búsqueda: ${it.message}" }
        }
    }
}
