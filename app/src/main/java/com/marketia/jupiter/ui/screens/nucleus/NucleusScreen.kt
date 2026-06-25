package com.marketia.jupiter.ui.screens.nucleus

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.marketia.jupiter.core.JupiterResponse
import com.marketia.jupiter.ui.components.JupiterNucleus
import com.marketia.jupiter.ui.components.OracleStateWidget
import com.marketia.jupiter.ui.theme.*

@Composable
fun NucleusScreen(viewModel: NucleusViewModel = hiltViewModel()) {
    val state        by viewModel.state.collectAsState()
    val inputText    by viewModel.inputText.collectAsState()
    val response     by viewModel.response.collectAsState()
    val partialVoice by viewModel.partialVoice.collectAsState()
    val oracleState  by viewModel.oracleState.collectAsState()
    val context      = LocalContext.current

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startListening() }

    fun onMicTap() {
        when {
            state == NucleusState.Listening -> viewModel.stopListening()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED -> viewModel.startListening()
            else -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JupiterBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.06f))

            OracleStateWidget(
                state    = oracleState,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Identity mark — minimal
            Text(
                text = "J Ú P I T E R",
                color = JupiterCyan.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 8.sp
            )

            Spacer(Modifier.weight(0.04f))

            // Main nucleus sphere
            JupiterNucleus(
                state = state,
                modifier = Modifier
                    .size(220.dp)
                    .clickable { onMicTap() }
            )

            Spacer(Modifier.height(20.dp))

            // State label — natural language, lowercase
            val stateText = when (state) {
                NucleusState.Idle          -> ""
                NucleusState.Listening     -> "escuchando..."
                NucleusState.Processing    -> "pensando..."
                is NucleusState.Responding -> ""
            }

            AnimatedVisibility(visible = stateText.isNotEmpty()) {
                Text(
                    text      = stateText,
                    color     = when (state) {
                        NucleusState.Listening  -> JupiterGreen
                        NucleusState.Processing -> JupiterCyan
                        else                    -> JupiterGray
                    },
                    fontSize  = 13.sp,
                    letterSpacing = 1.sp
                )
            }

            // Partial voice transcription
            AnimatedVisibility(visible = partialVoice.isNotEmpty()) {
                Text(
                    text      = partialVoice,
                    color     = JupiterGray,
                    fontSize  = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.padding(top = 6.dp, start = 16.dp, end = 16.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            // Response card — premium redesign
            AnimatedVisibility(
                visible = response != null,
                enter   = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 },
                exit    = fadeOut(tween(250)) + slideOutVertically(tween(250)) { it / 3 }
            ) {
                response?.let { r ->
                    JarvisResponseCard(
                        response  = r,
                        onDismiss = { viewModel.clearResponse() },
                        onInstall = if (r.action == "OTA_READY") {
                            {
                                viewModel.installOTA(
                                    r.params["apkUrl"]     ?: "",
                                    r.params["sha256"]     ?: "",
                                    r.params["releaseUrl"] ?: ""
                                )
                            }
                        } else null
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Idle hint
            AnimatedVisibility(visible = response == null && state == NucleusState.Idle) {
                Text(
                    text      = "Habla conmigo",
                    color     = JupiterGray.copy(alpha = 0.35f),
                    fontSize  = 12.sp,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            // Input row — refined
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(JupiterDark),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value         = inputText,
                    onValueChange = viewModel::onTextChange,
                    modifier      = Modifier.weight(1f),
                    placeholder   = {
                        Text(
                            "Escribe aquí...",
                            color    = JupiterGray.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor   = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor        = JupiterWhite,
                        unfocusedTextColor      = JupiterWhite,
                        cursorColor             = JupiterCyan,
                        focusedIndicatorColor   = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )

                IconButton(onClick = { onMicTap() }) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (state == NucleusState.Listening) JupiterGreen else JupiterCyan
                    )
                }

                IconButton(
                    onClick  = { viewModel.processInput(inputText) },
                    enabled  = inputText.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = if (inputText.isNotBlank()) JupiterCyan else JupiterGray.copy(alpha = 0.3f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun JarvisResponseCard(
    response: JupiterResponse,
    onDismiss: () -> Unit,
    onInstall: (() -> Unit)? = null
) {
    // Determine accent by intent
    val accent = when {
        response.typeDetected.contains("CODE") || response.typeDetected.contains("BUILD") -> Color(0xFF7C4DFF)
        response.typeDetected.contains("SKILL") || response.typeDetected.contains("CREATE") -> JupiterGreen
        response.typeDetected.contains("INGEST") || response.typeDetected.contains("LINK") -> Color(0xFF00E5FF)
        response.typeDetected.contains("SEARCH") -> Color(0xFFFFD700)
        response.typeDetected.contains("GREETING") -> JupiterCyan
        response.typeDetected.contains("VOICE") -> Color(0xFFFF8800)
        response.typeDetected == "CONFIG" -> Color(0xFFFF5722)
        response.typeDetected == "UNKNOWN" -> JupiterGray
        else -> JupiterCyan
    }

    val statusSymbol = when (response.status) {
        "EJECUTANDO"  -> "◉"
        "COMPLETADO"  -> "◈"
        "ESPERANDO"   -> "○"
        else          -> "◉"
    }

    val statusLabel = when (response.status) {
        "EJECUTANDO"  -> "en proceso"
        "COMPLETADO"  -> "completado"
        "ESPERANDO"   -> "en espera"
        else          -> response.status.lowercase()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = JupiterSurface),
        shape  = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // Top row: intent badge + dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Intent chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text          = response.typeDetected.replace("_", " "),
                        color         = accent,
                        fontSize      = 9.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }

                IconButton(
                    onClick   = onDismiss,
                    modifier  = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint     = JupiterGray.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Accent divider
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(accent.copy(alpha = 0.4f))
            )

            Spacer(Modifier.height(14.dp))

            // Response text — main element
            Text(
                text       = response.nextAction,
                color      = JupiterWhite,
                fontSize   = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(Modifier.height(18.dp))

            // Status — minimal
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text          = statusLabel,
                    color         = accent.copy(alpha = 0.7f),
                    fontSize      = 10.sp,
                    letterSpacing = 0.5.sp
                )
            }

            // OTA install button
            if (onInstall != null) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onInstall,
                    colors  = ButtonDefaults.buttonColors(containerColor = accent),
                    shape   = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text       = "INSTALAR AHORA",
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
