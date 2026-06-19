package com.marketia.jupiter.core.evaluation

import android.content.Context
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import com.marketia.jupiter.BuildConfig
import com.marketia.jupiter.core.ai.AIProvider
import com.marketia.jupiter.data.repository.JupiterRepository
import com.marketia.jupiter.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class EvaluationReport(
    val version: String,
    val versionCode: Int,
    val activeModules: List<String>,
    val detectedIssues: List<EvaluationIssue>,
    val availableSkills: List<String>,
    val missingCapabilities: List<String>,
    val recommendations: List<String>,
    val apiStatus: ApiStatus,
    val memoryStats: MemoryStats,
    val voiceStatus: VoiceStatus
)

data class EvaluationIssue(
    val severity: IssueSeverity,
    val module: String,
    val description: String,
    val suggestedFix: String
)

enum class IssueSeverity { HIGH, MEDIUM, LOW, INFO }

data class ApiStatus(
    val provider: String,
    val hasApiKey: Boolean,
    val connected: Boolean
)

data class MemoryStats(
    val totalLinks: Int,
    val totalProjects: Int,
    val totalSystems: Int,
    val totalAgents: Int,
    val totalSkills: Int
)

data class VoiceStatus(
    val sttAvailable: Boolean,
    val ttsReady: Boolean,
    val currentSpeed: Float,
    val currentPitch: Float
)

@Singleton
class SelfEvaluationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: JupiterRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend fun evaluate(): EvaluationReport {
        val settings = settingsRepository.getCurrentSettings()
        val skills = repository.skills.first()
        val links = repository.links.first()
        val projects = repository.projects.first()
        val systems = repository.systems.first()
        val agents = repository.agents.first()

        val sttAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        val issues = mutableListOf<EvaluationIssue>()
        val missing = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        // Check AI provider
        if (settings.provider == AIProvider.LOCAL) {
            issues.add(EvaluationIssue(
                IssueSeverity.MEDIUM, "AI Router",
                "Usando fallback local (JupiterBrain). Sin LLM real conectado.",
                "Configurar OpenRouter o Claude en la pantalla Config."
            ))
            recommendations.add("Conectar proveedor IA para lenguaje natural real.")
        } else if (settings.apiKey.isBlank() && settings.provider != AIProvider.OLLAMA) {
            issues.add(EvaluationIssue(
                IssueSeverity.HIGH, "API Key",
                "Proveedor ${settings.provider.label} seleccionado sin API key.",
                "Ingresar API key en Config > API KEY."
            ))
        }

        if (!sttAvailable) {
            issues.add(EvaluationIssue(
                IssueSeverity.HIGH, "Voz STT",
                "SpeechRecognizer no disponible en este dispositivo.",
                "Instalar Google app o habilitar reconocimiento de voz."
            ))
        }

        if (settings.voiceSpeed == 1.0f && settings.voicePitch == 1.0f) {
            recommendations.add("Di 'quiero una voz mas humana' para optimizar TTS.")
        }

        if (projects.isEmpty()) {
            recommendations.add("No tienes proyectos. Dile a JUPITER 'crear app de...' para empezar.")
        }

        if (agents.isEmpty()) {
            missing.add("Agentes IA (sin bots creados aun)")
        }

        // Active modules
        val activeModules = listOf(
            "NUCLEO (Canvas animado)",
            "SKILLS (${skills.size} categorias)",
            "MEMORIA (Room DB v2)",
            "VOZ (STT + TTS)",
            "AI ROUTER (${settings.provider.label})",
            "REGISTROS (Tool, Skill, Agent)",
            "CONFIGURACION (DataStore)"
        )

        val availableSkills = listOf("WebSearch", "Memory", "VoiceSettings", "GitHub", "APKBuild") +
                              skills.map { it.name }

        // V0.5 capabilities not yet implemented
        missing.addAll(listOf(
            "ClaudeCode Bridge (programacion remota)",
            "ScreenshotAnalysis (Vision IA)",
            "PlaywrightBridge (navegacion web)",
            "AutoInstall APK (con aprobacion)"
        ))

        recommendations.apply {
            if (links.size > 10) add("Tienes ${links.size} links guardados. Considera organizarlos por categoria.")
            if (skills.size < 7) add("Faltan skills en la base de datos. Ejecutar seed.")
            add("V0.5 agrega autoevaluacion y bridge con Claude Code en PC.")
        }

        return EvaluationReport(
            version     = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            activeModules = activeModules,
            detectedIssues = issues,
            availableSkills = availableSkills,
            missingCapabilities = missing,
            recommendations = recommendations,
            apiStatus = ApiStatus(settings.provider.label, settings.apiKey.isNotBlank(), false),
            memoryStats = MemoryStats(links.size, projects.size, systems.size, agents.size, skills.size),
            voiceStatus = VoiceStatus(sttAvailable, true, settings.voiceSpeed, settings.voicePitch)
        )
    }
}
