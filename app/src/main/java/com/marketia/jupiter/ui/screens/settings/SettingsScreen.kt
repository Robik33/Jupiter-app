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
import com.marketia.jupiter.core.ai.AIProvider
import com.marketia.jupiter.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    var showApiKey by remember { mutableStateOf(false) }
    var apiKeyDraft by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var modelDraft by remember(settings.model) { mutableStateOf(settings.model) }
    var ollamaUrlDraft by remember(settings.ollamaUrl) { mutableStateOf(settings.ollamaUrl) }

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
                Text("Proveedor IA y parámetros de voz", color = JupiterGray, fontSize = 12.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // ── PROVIDER ────────────────────────────────────────────────────
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

            // ── API KEY ─────────────────────────────────────────────────────
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

            // ── OLLAMA URL ────────────────────────────────────────────────
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

            Divider(color = JupiterSurface, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            // ── VOICE ────────────────────────────────────────────────────────
            SectionLabel("VOZ")

            SliderRow(
                label  = "Velocidad",
                value  = settings.voiceSpeed,
                range  = 0.5f..1.5f,
                onValueChange = { viewModel.setVoiceSpeed(it) }
            )
            Spacer(Modifier.height(4.dp))
            SliderRow(
                label  = "Tono",
                value  = settings.voicePitch,
                range  = 0.5f..1.5f,
                onValueChange = { viewModel.setVoicePitch(it) }
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { viewModel.testVoice() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = JupiterSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("▶  PROBAR VOZ", color = JupiterCyan, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

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
            colors   = RadioButtonDefaults.colors(
                selectedColor   = JupiterCyan,
                unselectedColor = JupiterGray
            )
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(provider.label, color = if (selected) JupiterWhite else JupiterGray,
                fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            if (provider.defaultModel.isNotBlank()) {
                Text(provider.defaultModel, color = JupiterGray.copy(alpha = 0.6f),
                    fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
                      onValueChange: (Float) -> Unit) {
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
                thumbColor        = JupiterCyan,
                activeTrackColor  = JupiterCyan,
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
