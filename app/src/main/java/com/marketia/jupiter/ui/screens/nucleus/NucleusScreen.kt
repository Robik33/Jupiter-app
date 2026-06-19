package com.marketia.jupiter.ui.screens.nucleus

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.marketia.jupiter.ui.theme.*

@Composable
fun NucleusScreen(viewModel: NucleusViewModel = hiltViewModel()) {
    val state        by viewModel.state.collectAsState()
    val inputText    by viewModel.inputText.collectAsState()
    val response     by viewModel.response.collectAsState()
    val partialVoice by viewModel.partialVoice.collectAsState()
    val context = LocalContext.current

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
            Spacer(Modifier.weight(0.08f))

            // NÚCLEO title
            Text(
                text = "JÚPITER",
                color = JupiterCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp
            )
            Text(
                text = "NÚCLEO DE CREACIÓN",
                color = JupiterGray,
                fontSize = 9.sp,
                letterSpacing = 3.sp
            )

            Spacer(Modifier.weight(0.06f))

            // Animated nucleus sphere — clickable to toggle listening
            JupiterNucleus(
                state = state,
                modifier = Modifier
                    .size(230.dp)
                    .clickable { onMicTap() }
            )

            Spacer(Modifier.height(16.dp))

            // State indicator
            Text(
                text = when (state) {
                    NucleusState.Idle       -> "Toca el núcleo para hablar"
                    NucleusState.Listening  -> "● ESCUCHANDO"
                    NucleusState.Processing -> "◈ PROCESANDO"
                    is NucleusState.Responding -> "◉ RESPONDIENDO"
                },
                color = when (state) {
                    NucleusState.Listening  -> JupiterGreen
                    NucleusState.Processing -> JupiterPurple
                    is NucleusState.Responding -> JupiterCyan
                    else -> JupiterGray
                },
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                fontWeight = if (state != NucleusState.Idle) FontWeight.Bold else FontWeight.Normal
            )

            // Partial voice text
            AnimatedVisibility(visible = partialVoice.isNotEmpty()) {
                Text(
                    text = "\"$partialVoice\"",
                    color = JupiterGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            // Response card
            AnimatedVisibility(
                visible = response != null,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 },
                exit  = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 }
            ) {
                response?.let { ResponseCard(it) { viewModel.clearResponse() } }
            }

            Spacer(Modifier.height(12.dp))

            // Prompt hint
            AnimatedVisibility(visible = response == null && state == NucleusState.Idle) {
                Text(
                    text = "¿Qué necesitas crear?",
                    color = JupiterGray.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(12.dp))

            // Text input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(JupiterSurface),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = viewModel::onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("Orden escrita...", color = JupiterGray, fontSize = 13.sp)
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

                // Mic button
                IconButton(onClick = { onMicTap() }) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voz",
                        tint = if (state == NucleusState.Listening) JupiterGreen else JupiterCyan
                    )
                }

                // Send button
                IconButton(
                    onClick = { viewModel.processInput(inputText) },
                    enabled = inputText.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Enviar",
                        tint = if (inputText.isNotBlank()) JupiterCyan else JupiterGray
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ResponseCard(response: JupiterResponse, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JupiterSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    ResponseRow(label = "ORDEN",    value = "\"${response.orderReceived}\"", JupiterGray)
                    Spacer(Modifier.height(8.dp))
                    ResponseRow(label = "TIPO",     value = response.typeDetected, JupiterCyan)
                    Spacer(Modifier.height(8.dp))
                    ResponseRow(label = "ACCIÓN",   value = response.nextAction, JupiterWhite)
                    Spacer(Modifier.height(10.dp))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Clear, contentDescription = "Cerrar", tint = JupiterGray)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(JupiterBlack)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(JupiterGreen)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = response.status,
                        color = JupiterGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ResponseRow(label: String, value: String, valueColor: Color) {
    Text(label, color = JupiterGray, fontSize = 9.sp, letterSpacing = 2.sp)
    Spacer(Modifier.height(2.dp))
    Text(value, color = valueColor, fontSize = 13.sp, lineHeight = 18.sp)
}
