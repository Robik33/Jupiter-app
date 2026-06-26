package com.marketia.jupiter.core.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.marketia.jupiter.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Public GitHub Gist — always accessible without auth regardless of repo visibility
        const val MANIFEST_URL =
            "https://gist.githubusercontent.com/Robik33/8fdde845e278f54bf639adfccb3be295/raw/latest.json"
        private const val DOWNLOAD_FILENAME = "jupiter-update.apk"
        private const val TIMEOUT_MS = 10 * 60 * 1000L // 10 min
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    suspend fun checkForUpdate() {
        _state.value = UpdateState.Checking
        runCatching {
            val req = Request.Builder().url(MANIFEST_URL).get().build()
            val body = withContext(Dispatchers.IO) {
                http.newCall(req).execute().body?.string()
            } ?: throw Exception("Respuesta vacía del servidor")

            val json = JSONObject(body)
            val manifest = UpdateManifest(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl      = json.getString("apkUrl"),
                releaseUrl  = json.optString("releaseUrl", ""),
                sha256      = json.optString("sha256", ""),
                sizeBytes   = json.optLong("sizeBytes", 0L),
                mandatory   = json.optBoolean("mandatory", false),
                changelog   = json.optJSONArray("changelog")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList()
            )

            if (manifest.versionCode > BuildConfig.VERSION_CODE) {
                _state.value = UpdateState.Available(manifest)
            } else {
                _state.value = UpdateState.UpToDate
            }
        }.onFailure { e ->
            _state.value = UpdateState.Failed("Error al verificar: ${e.message}")
        }
    }

    suspend fun downloadAndInstall(manifest: UpdateManifest) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: run {
                _state.value = UpdateState.Failed("Almacenamiento externo no disponible")
                return
            }
        val destFile = File(destDir, DOWNLOAD_FILENAME)
        if (destFile.exists()) destFile.delete()

        val req = DownloadManager.Request(Uri.parse(manifest.apkUrl))
            .setTitle("JÚPITER v${manifest.versionName}")
            .setDescription("Descargando actualización ${manifest.versionName}...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, DOWNLOAD_FILENAME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadId = dm.enqueue(req)
        _state.value = UpdateState.Downloading(0)

        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        withContext(Dispatchers.IO) {
            while (System.currentTimeMillis() < deadline) {
                val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
                if (!cursor.moveToFirst()) { cursor.close(); delay(400); continue }

                val status     = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total      = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val reason     = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                cursor.close()

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> break
                    DownloadManager.STATUS_FAILED     -> {
                        _state.value = UpdateState.Failed("Error de descarga (código $reason)")
                        return@withContext
                    }
                    else -> {
                        val progress = if (total > 0) (downloaded * 100 / total).toInt() else 0
                        _state.value = UpdateState.Downloading(progress)
                    }
                }
                delay(300)
            }
        }

        if (_state.value is UpdateState.Failed) return

        // Verify SHA256
        _state.value = UpdateState.Verifying
        if (manifest.sha256.isNotBlank()) {
            val actualHash = computeSha256(destFile)
            if (!actualHash.equals(manifest.sha256, ignoreCase = true)) {
                destFile.delete()
                _state.value = UpdateState.Failed(
                    "Verificación fallida.\nHash esperado: ${manifest.sha256.take(16)}...\nObtenido: ${actualHash.take(16)}..."
                )
                return
            }
        }

        _state.value = UpdateState.Downloaded(destFile)
        triggerInstall(destFile)
    }

    fun canInstallPackages(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun triggerInstall(file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            // Redirect to "Install unknown apps" settings for this app — user taps "Allow" once
            openInstallPermissionSettings()
            _state.value = UpdateState.Failed(
                "Activa 'Instalar apps desconocidas' para JUPITER en Ajustes y vuelve a tocar INSTALAR."
            )
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        _state.value = UpdateState.Installing
    }

    fun resetState() { _state.value = UpdateState.Idle }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buf = ByteArray(8192)
            var n: Int
            while (stream.read(buf).also { n = it } != -1) {
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
