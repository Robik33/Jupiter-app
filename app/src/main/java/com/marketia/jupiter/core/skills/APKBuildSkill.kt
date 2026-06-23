package com.marketia.jupiter.core.skills

import com.marketia.jupiter.core.bridge.BridgeChannel
import com.marketia.jupiter.core.bridge.ClaudeCodeBridge
import com.marketia.jupiter.core.bridge.ClaudeCodeTask
import com.marketia.jupiter.core.bridge.TaskPriority
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class APKBuildSkill @Inject constructor(
    private val bridge: ClaudeCodeBridge
) : JupiterSkill {
    override val id   = "apk_build"
    override val name = "APK Builder"

    override suspend fun execute(params: Map<String, String>): String {
        val target  = params["target"]  ?: "release"
        val version = params["version"] ?: "next"
        val notes   = params["notes"]   ?: ""

        val gradleTask = "assemble${target.replaceFirstChar { it.uppercase() }}"
        val task = ClaudeCodeTask(
            version         = version,
            goal            = "Build APK $target v$version",
            objective       = "Compilar y publicar APK de JÚPITER $target v$version",
            problem         = "Se requiere nueva build: $target v$version${if (notes.isNotBlank()) "\n$notes" else ""}",
            evidence        = "Solicitado desde JÚPITER vía APKBuildSkill",
            requestedChange = "Ejecutar ./gradlew $gradleTask, verificar APK, publicar resultado con APK_URL y SHA256",
            filesToChange   = listOf("app/build.gradle.kts", "releases/latest.json"),
            steps           = listOf(
                "Verificar versionCode y versionName en app/build.gradle.kts",
                "Ejecutar: ./gradlew $gradleTask --no-daemon",
                "Verificar APK en app/build/outputs/apk/$target/",
                "Calcular SHA256 del APK",
                "Subir APK a Catbox.moe",
                "Actualizar releases/latest.json con nueva URL y SHA256",
                "Publicar APK_URL y RELEASE_URL en este issue"
            ),
            validation      = "APK existe, SHA256 verificado, Gist actualizado",
            expectedResult  = "APK $target v$version disponible en Catbox + GitHub Release",
            priority        = TaskPriority.HIGH,
            channel         = BridgeChannel.GITHUB_ISSUE
        )

        val result = bridge.send(task)
        return if (result.success) {
            "Build request enviado a Claude Code.\nIssue: ${result.issueUrl}\nJÚPITER recibirá APK_URL cuando esté listo."
        } else {
            "Error enviando build: ${result.message}\nConfigura GitHub PAT en Config > GitHub PAT."
        }
    }
}
