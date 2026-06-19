package com.marketia.jupiter.core.ingestion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class LinkAnalysis(
    val url: String,
    val title: String,
    val type: LinkType,
    val rawContent: String,
    val metaDescription: String = ""
)

enum class LinkType { YOUTUBE, GITHUB, PDF, WEB, PODCAST, UNKNOWN }

@Singleton
class LinkAnalyzer @Inject constructor() {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun analyze(url: String): LinkAnalysis = withContext(Dispatchers.IO) {
        val type = detectType(url)
        when (type) {
            LinkType.YOUTUBE -> analyzeYouTube(url)
            LinkType.GITHUB  -> analyzeGitHub(url)
            else             -> analyzeWeb(url, type)
        }
    }

    private fun detectType(url: String): LinkType = when {
        url.contains("youtube.com") || url.contains("youtu.be") -> LinkType.YOUTUBE
        url.contains("github.com")                               -> LinkType.GITHUB
        url.endsWith(".pdf", ignoreCase = true)                  -> LinkType.PDF
        url.contains("podcast") || url.contains("spotify.com")  -> LinkType.PODCAST
        else                                                     -> LinkType.WEB
    }

    private fun analyzeYouTube(url: String): LinkAnalysis {
        val videoId = extractYouTubeId(url)
        val oembedUrl = "https://www.youtube.com/oembed?url=${url}&format=json"
        val title = runCatching {
            val req = Request.Builder().url(oembedUrl).build()
            val resp = http.newCall(req).execute()
            JSONObject(resp.body?.string() ?: "").optString("title", "Video de YouTube")
        }.getOrDefault("Video de YouTube")
        return LinkAnalysis(
            url = url, title = title, type = LinkType.YOUTUBE,
            rawContent = "Video de YouTube: $title. ID: $videoId",
            metaDescription = "Contenido audiovisual en YouTube"
        )
    }

    private fun analyzeGitHub(url: String): LinkAnalysis {
        val parts = url.removePrefix("https://github.com/").split("/")
        val repo = if (parts.size >= 2) "${parts[0]}/${parts[1]}" else url
        val title = runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$repo")
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            val resp = http.newCall(req).execute()
            val json = JSONObject(resp.body?.string() ?: "")
            val desc = json.optString("description", "")
            val name = json.optString("full_name", repo)
            "$name${if (desc.isNotBlank()) ": $desc" else ""}"
        }.getOrDefault(repo)
        return LinkAnalysis(
            url = url, title = title, type = LinkType.GITHUB,
            rawContent = "Repositorio GitHub: $title",
            metaDescription = "Código fuente en GitHub"
        )
    }

    private fun analyzeWeb(url: String, type: LinkType): LinkAnalysis {
        return runCatching {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 JUPITER/0.6 LinkAnalyzer")
                .build()
            val resp = http.newCall(req).execute()
            val html = resp.body?.string()?.take(8000) ?: ""
            val title = extractHtmlTitle(html).ifBlank { url.substringAfterLast("/").take(60) }
            val meta = extractMetaDescription(html)
            val text = stripHtml(html).take(2000)
            LinkAnalysis(url = url, title = title, type = type, rawContent = text, metaDescription = meta)
        }.getOrElse {
            LinkAnalysis(url = url, title = url.substringAfterLast("/").take(60),
                type = type, rawContent = "No se pudo obtener contenido de: $url")
        }
    }

    private fun extractYouTubeId(url: String): String =
        Regex("[?&]v=([^&]+)").find(url)?.groupValues?.get(1)
            ?: url.substringAfterLast("/").substringBefore("?")

    private fun extractHtmlTitle(html: String): String =
        Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.trim() ?: ""

    private fun extractMetaDescription(html: String): String =
        Regex("""<meta[^>]+name=[\"']description[\"'][^>]+content=[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.trim() ?: ""

    private fun stripHtml(html: String): String =
        html.replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ").trim()
}
