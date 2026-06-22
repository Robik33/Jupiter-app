package com.marketia.jupiter.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marketia.jupiter.BuildConfig
import com.marketia.jupiter.core.ai.AIProvider
import com.marketia.jupiter.core.update.UpdateManager
import com.marketia.jupiter.core.update.UpdateState
import com.marketia.jupiter.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings     by viewModel.settings.collectAsState()
    val updateState  by viewModel.updateState.collectAsState()
    var showApiKey     by remember { mutableStateOf(false) }
    var showGithubPat  by remember { mutableStateOf(false) }
    var apiKeyDraft    by remember(settings.apiKey)     { mutableStateOf(settings.apiKey) }
    var modelDraft     by remember(settings.model)      { mutableStateOf(settings.model) }
    var ollamaUrlDraft by remember(settings.ollamaUrl)  { mutableStateOf(settings.ollamaUrl) }
    var githubPatDraft by remember(settings.githubPat)  { mutableStateOf(settings.githubPat) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JupiterBlack)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(JupiterSurface)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Text("CONFIGURACIÓN", color = JupiterCyan, fontSize = 20.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 5.sp)
                Text("Proveedor IA · voz · actualizaciones", color = JupiterGray, fontSize = 12.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // ── PROVIDER ───────────────────────────────────────────────────────
            SectionLabel("PROVEEDOR IA")
            AIProvider.entries.forEach { provider ->
                ProviderRow(
                    provider = provider,
                    selected = settings.provider == provider,
                    onClick  = { viewModel.setProvider(provider) }
                )
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(16.dp))

            // ── API KEY ────────────────────────────────────────────────────────
            if (settings.provider != AIProvider.LOCAL) {
                SectionLabel("API KEY")
                OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = { apiKeyDraft = it; viewModel.setApiKey(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("sk-...", color = JupiterGray, fontSize = 13.sp) },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle",
                                tint = JupiterGray
                            )
                        }
                    },
                    colors = jupiterFieldColors(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))

                SectionLabel("MODELO")
                OutlinedTextField(
                    value = modelDraft,
                    onValueChange = { modelDraft = it; viewModel.setModel(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(settings.provider.defaultModel.ifBlank { "modelo" },
                        color = JupiterGray, fontSize = 13.sp) },
                    colors = jupiterFieldColors(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
            }

            // ── OLLAMA URL ─────────────────────────────────────────────────────
            if (settings.provider == AIProvider.OLLAMA || settings.provider == AIProvider.HERMES) {
                SectionLabel("SERVIDOR OLLAMA")
                OutlinedTextField(
                    value = ollamaUrlDraft,
                    onValueChange = { ollamaUrlDraft = it; viewModel.setOllamaUrl(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("http://192.168.x.x:11434", color = JupiterGray, fontSize = 13.sp) },
                    colors = jupiterFieldColors(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
            }

            // ── GITHUB PAT ────────────────────────────────────────────────────
            HorizontalDivider(color = JupiterSurface, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
            SectionLabel("GITHUB PAT")
            OutlinedTextField(
                value = githubPatDraft,
                onValueChange = { githubPatDraft = it; viewModel.setGithubPat(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("ghp_...", color = JupiterGray, fontSize = 13.sp) },
                visualTransformation = if (showGithubPat) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showGithubPat = !showGithubPat }) {
                        Icon(
                            if (showGithubPat) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle",
                            tint = JupiterGray
                        )
                    }
                },
                colors = jupiterFieldColors(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Requerido para ClaudeCodeBridge (crear GitHub Issues). Scope: repo.",
                color = JupiterGray.copy(alpha = 0.6f),
                fontSize = 10.sp,
                lineHeight = 13.sp
            )
            Spacer(Modifier.height(12.dp))

            HorizontalDivider(color = JupiterSurface, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            // ── VOICE ──────────────────────────────────────────────────────────
            SectionLabel("VOZ")
            SliderRow(
                label = "Velocidad",
                value = settings.voiceSpeed,
                range = 0.5f..1.5f,
                onValueChange = { viewModel.setVoiceSpeed(it) }
            )
            Spacer(Modifier.height(4.dp))
            SliderRow(
                label = "Tono",
                value = settings.voicePitch,
                range = 0.5f..1.5f,
                onValueChange = { viewModel.setVoicePitch(it) }
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { viewModel.testVoice() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = JupiterSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("▶  PROBAR VOZ", color = JupiterCyan, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }

            HorizontalDivider(color = JupiterSurface, thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))

            // ── ACTUALIZACIÓN REMOTA ──────────────────────────────────────────
            SectionLabel("ACTUALIZACIÓN REMOTA")
            UpdateSection(updateState = updateState, viewModel = viewModel)

            Spacer(Modifier.height(48.dp))
        }
    }
}

// ── UPDATE SECTION ────────────────────────────────────────────────────────────

@Composable
private fun UpdateSection(updateState: UpdateState, viewModel: SettingsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(JupiterSurface)
            .padding(16.dp)
    ) {
        // Current version
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("VERSIÓN ACTUAL", color = JupiterGray, fontSize = 9.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    color = JupiterWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            val (badgeColor, badgeLabel) = when (updateState) {
                is UpdateState.UpToDate    -> JupiterGreen   to "AL DÍA"
                is UpdateState.Available   -> Color(0xFFFFAA00) to "DISPONIBLE"
                is UpdateState.Downloading -> JupiterCyan    to "DESCARGANDO"
                is UpdateState.Verifying   -> JupiterCyan    to "VERIFICANDO"
                is UpdateState.Downloaded  -> JupiterGreen   to "LISTO"
                is UpdateState.Installing  -> JupiterPurple  to "INSTALANDO"
                is UpdateState.Failed      -> Color(0xFFFF4444) to "ERROR"
                is UpdateState.Checking    -> JupiterGray    to "BUSCANDO..."
                else                       -> JupiterGray    to "MANUAL"
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(badgeLabel, color = badgeColor, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }

        Spacer(Modifier.height(14.dp))

        // State-specific content
        when (val s = updateState) {
            is UpdateState.Available -> {
                UpdateAvailableCard(s)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.downloadUpdate(s.manifest) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFAA00))
                ) {
                    Text("DESCARGAR v${s.manifest.versionName}", color = JupiterBlack,
                        fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
            is UpdateState.Downloading -> {
                Text("Descargando... ${s.progress}%", color = JupiterCyan, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { s.progress / 100f },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = JupiterCyan,
                    trackColor = JupiterBlack
                )
            }
            is UpdateState.Verifying -> {
                Text("Verificando SHA256...", color = JupiterCyan, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = JupiterCyan,
                    trackColor = JupiterBlack
                )
            }
            is UpdateState.Downloaded -> {
                Text("APK verificado. Listo para instalar.", color = JupiterGreen, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.installDownloaded(s.file) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = JupiterGreen)
                ) {
                    Text("INSTALAR AHORA", color = JupiterBlack, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
            is UpdateState.Installing -> {
                Text("Instalador del sistema activo. Toca INSTALAR.", color = JupiterPurple, fontSize = 13.sp)
            }
            is UpdateState.Failed -> {
                Text(s.reason, color = Color(0xFFFF4444), fontSize = 12.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.resetUpdateState() }) {
                    Text("REINTENTAR", color = JupiterCyan, fontSize = 11.sp)
                }
            }
            is UpdateState.UpToDate -> {
                Text("Tienes la versión más reciente.", color = JupiterGreen, fontSize = 13.sp)
            }
            is UpdateState.Checking -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = JupiterCyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Buscando actualizaciones...", color = JupiterGray, fontSize = 13.sp)
                }
            }
            else -> {
                Text("Toca el botón para buscar una nueva versión.",
                    color = JupiterGray, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Check button (always visible except during active operations)
        val busy = updateState is UpdateState.Checking ||
                   updateState is UpdateState.Downloading ||
                   updateState is UpdateState.Verifying ||
                   updateState is UpdateState.Installing
        if (!busy) {
            OutlinedButton(
                onClick = { viewModel.checkForUpdate() },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(JupiterCyan)
                ),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = JupiterCyan)
            ) {
                Text("BUSCAR ACTUALIZACIÓN", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Manifest: ${UpdateManager.MANIFEST_URL.removePrefix("https://gist.githubusercontent.com/")}",
            color = JupiterGray.copy(alpha = 0.5f), fontSize = 8.sp, lineHeight = 11.sp
        )
    }
}

@Composable
private fun UpdateAvailableCard(state: UpdateState.Available) {
    val m = state.manifest
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFAA00).copy(alpha = 0.08f))
            .border(1.dp, Color(0xFFFFAA00).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text("Nueva versión disponible", color = Color(0xFFFFAA00), fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text("v${m.versionName} (build ${m.versionCode})", color = JupiterWhite,
            fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (m.mandatory) {
            Spacer(Modifier.height(4.dp))
            Text("⚠ Actualización obligatoria", color = Color(0xFFFF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (m.changelog.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("CAMBIOS", color = JupiterGray, fontSize = 9.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            m.changelog.take(5).forEach { item ->
                Text("• $item", color = JupiterGray.copy(alpha = 0.9f), fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        if (m.sizeBytes > 0) {
            Spacer(Modifier.height(6.dp))
            Text("Tamaño: ${"%.1f".format(m.sizeBytes / 1_048_576.0)} MB",
                color = JupiterGray.copy(alpha = 0.7f), fontSize = 10.sp)
        }
    }
}

// ── SHARED COMPOSABLES ────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = JupiterGray, fontSize = 10.sp, letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun ProviderRow(provider: AIProvider, selected: Boolean, onClick: () -> Unit) {
    val accent = if (selected) JupiterCyan else JupiterGray.copy(alpha = 0.3f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) JupiterCyan.copy(alpha = 0.08f) else JupiterSurface)
            .border(1.dp, accent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick  = onClick,
            colors   = RadioButtonDefaults.colors(selectedColor = JupiterCyan, unselectedColor = JupiterGray)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(provider.label, color = if (selected) JupiterWhite else JupiterGray,
                fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            if (provider.defaultModel.isNotBlank()) {
                Text(provider.defaultModel, color = JupiterGray.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String, value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = JupiterWhite, fontSize = 13.sp)
            Text("%.2f".format(value), color = JupiterCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = { onValueChange((it * 100).roundToInt() / 100f) },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor         = JupiterCyan,
                activeTrackColor   = JupiterCyan,
                inactiveTrackColor = JupiterSurface
            )
        )
    }
}

@Composable
private fun jupiterFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor     = JupiterWhite,
    unfocusedTextColor   = JupiterWhite,
    focusedBorderColor   = JupiterCyan,
    unfocusedBorderColor = JupiterGray.copy(alpha = 0.4f),
    cursorColor          = JupiterCyan,
    focusedLabelColor    = JupiterCyan,
    unfocusedLabelColor  = JupiterGray
)
