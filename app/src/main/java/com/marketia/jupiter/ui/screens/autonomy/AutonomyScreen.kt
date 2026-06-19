package com.marketia.jupiter.ui.screens.autonomy

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marketia.jupiter.core.autonomy.EngineState
import com.marketia.jupiter.core.autonomy.OllamaStatus
import com.marketia.jupiter.data.entity.TaskEntity
import com.marketia.jupiter.ui.theme.*

@Composable
fun AutonomyScreen(viewModel: AutonomyViewModel = hiltViewModel()) {
    val engineState  by viewModel.engineState.collectAsState()
    val tasks        by viewModel.tasks.collectAsState()
    val ollamaStatus by viewModel.ollamaStatus.collectAsState()
    val tokensSaved  by viewModel.tokensSaved.collectAsState()
    val settings     by viewModel.settings.collectAsState()
    val currentStep  by viewModel.currentStep.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(JupiterBlack)) {
        // ── Header ──────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(JupiterSurface)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AUTONOMÍA", color = JupiterCyan, fontSize = 20.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 5.sp)
                    Spacer(Modifier.width(10.dp))
                    EngineStateBadge(engineState)
                }
                val stepText = (engineState as? EngineState.Processing)?.step ?: ""
                Text(
                    if (stepText.isNotBlank()) "Ciclo: $stepText" else "Loop: PLAN → EXECUTE → VERIFY → FIX → REPORT",
                    color = JupiterGray, fontSize = 11.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Engine controls ──────────────────────────────────────────────
            item {
                EngineControlRow(
                    engineState = engineState,
                    onStart     = { viewModel.startEngine() },
                    onStop      = { viewModel.stopEngine() },
                    onAddTask   = { showAddDialog = true }
                )
            }

            // ── Stats bar ────────────────────────────────────────────────────
            item { StatsRow(viewModel, tokensSaved) }

            // ── Provider status ──────────────────────────────────────────────
            item { ProviderStatusSection(ollamaStatus, settings.claudeKey, settings.openrouterKey, onRefresh = { viewModel.refreshOllama() }) }

            // ── Task queue ───────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("COLA DE TAREAS", color = JupiterGray, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    if (tasks.any { it.status == "DONE" }) {
                        TextButton(onClick = { viewModel.clearDone() }) {
                            Text("Limpiar completadas", color = JupiterGray, fontSize = 11.sp)
                        }
                    }
                }
            }

            if (tasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("◈", fontSize = 32.sp, color = JupiterGray.copy(alpha = 0.4f))
                            Spacer(Modifier.height(8.dp))
                            Text("Sin tareas pendientes", color = JupiterGray, fontSize = 13.sp)
                            Text("Toca + para agregar una tarea", color = JupiterGray.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }
                }
            } else {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(task = task, onDelete = { viewModel.deleteTask(task) })
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // FAB
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.padding(16.dp),
            containerColor = JupiterCyan,
            contentColor = JupiterBlack,
            shape = RoundedCornerShape(14.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Agregar tarea") }
    }

    if (showAddDialog) {
        AddTaskDialog(
            onDismiss = { showAddDialog = false },
            onAdd     = { title, desc, priority ->
                viewModel.submitTask(title, desc, priority)
                showAddDialog = false
            }
        )
    }
}

// ── Engine badge ──────────────────────────────────────────────────────────────

@Composable
private fun EngineStateBadge(state: EngineState) {
    val (label, color) = when (state) {
        is EngineState.Running    -> "EN EJECUCIÓN" to JupiterGreen
        is EngineState.Processing -> state.step.take(12) to JupiterCyan
        is EngineState.Stopped   -> "DETENIDO" to JupiterGray
        else                     -> "INACTIVO" to JupiterGray
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

// ── Controls row ──────────────────────────────────────────────────────────────

@Composable
private fun EngineControlRow(
    engineState: EngineState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAddTask: () -> Unit
) {
    val isRunning = engineState is EngineState.Running || engineState is EngineState.Processing
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = if (isRunning) onStop else onStart,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) Color(0xFFFF4444) else JupiterGreen
            )
        ) {
            Text(
                if (isRunning) "⬛  DETENER" else "▶  INICIAR LOOP",
                color = JupiterBlack, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp
            )
        }
        OutlinedButton(
            onClick = onAddTask,
            modifier = Modifier.height(48.dp),
            shape = RoundedCornerShape(12.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(JupiterCyan)
            ),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = JupiterCyan)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("TAREA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Stats bar ─────────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(viewModel: AutonomyViewModel, tokensSaved: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniStat("PENDIENTES", viewModel.pendingCount().toString(), JupiterCyan, Modifier.weight(1f))
        MiniStat("EN CURSO", viewModel.runningCount().toString(), Color(0xFFFFAA00), Modifier.weight(1f))
        MiniStat("LISTAS", viewModel.doneCount().toString(), JupiterGreen, Modifier.weight(1f))
        MiniStat("TOKENS\nAHORRADOS", tokensSaved.toString(), JupiterPurple, Modifier.weight(1f))
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(JupiterSurface)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(label, color = JupiterGray, fontSize = 8.sp, letterSpacing = 0.3.sp, lineHeight = 10.sp)
    }
}

// ── Provider status ───────────────────────────────────────────────────────────

@Composable
private fun ProviderStatusSection(
    ollama: OllamaStatus?,
    claudeKey: String,
    openrouterKey: String,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(JupiterSurface)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PROVEEDORES", color = JupiterGray, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh",
                    tint = JupiterGray, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderChip(
                name   = "Ollama",
                active = ollama?.available == true,
                detail = if (ollama?.available == true) ollama.activeModel.take(16) else ollama?.error?.take(20) ?: "Sin datos",
                color  = JupiterGreen,
                modifier = Modifier.weight(1f)
            )
            ProviderChip(
                name   = "OpenRouter",
                active = openrouterKey.isNotBlank(),
                detail = if (openrouterKey.isNotBlank()) "API key OK" else "Sin key",
                color  = Color(0xFFFFAA00),
                modifier = Modifier.weight(1f)
            )
            ProviderChip(
                name   = "Claude",
                active = claudeKey.isNotBlank(),
                detail = if (claudeKey.isNotBlank()) "API key OK" else "Sin key",
                color  = JupiterPurple,
                modifier = Modifier.weight(1f)
            )
        }
        if (ollama?.available == true && ollama.models.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Modelos Ollama: ${ollama.models.take(4).joinToString(", ")}",
                color = JupiterGray.copy(alpha = 0.7f), fontSize = 10.sp, lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun ProviderChip(name: String, active: Boolean, detail: String, color: Color, modifier: Modifier) {
    val chipColor = if (active) color else JupiterGray.copy(alpha = 0.4f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(chipColor.copy(alpha = 0.1f))
            .border(1.dp, chipColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(chipColor)
        )
        Spacer(Modifier.height(4.dp))
        Text(name, color = if (active) JupiterWhite else JupiterGray,
            fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = chipColor, fontSize = 8.sp, lineHeight = 10.sp)
    }
}

// ── Task card ─────────────────────────────────────────────────────────────────

@Composable
private fun TaskCard(task: TaskEntity, onDelete: () -> Unit) {
    val (statusColor, statusLabel) = when (task.status) {
        "PENDING"  -> JupiterCyan    to "PENDIENTE"
        "RUNNING"  -> Color(0xFFFFAA00) to "EJECUTANDO"
        "FIXING"   -> Color(0xFFFF8800) to "CORRIGIENDO"
        "DONE"     -> JupiterGreen   to "LISTO"
        "FAILED"   -> Color(0xFFFF4444) to "FALLIDO"
        "BLOCKED"  -> Color(0xFFFF2222) to "BLOQUEADO"
        else       -> JupiterGray    to task.status
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(JupiterSurface)
            .border(1.dp, statusColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(statusColor)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.title, color = JupiterWhite, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StatusTag(statusLabel, statusColor)
            }
            if (task.description.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(task.description.take(80), color = JupiterGray, fontSize = 11.sp, lineHeight = 15.sp)
            }
            if (task.provider.isNotBlank() && task.status == "DONE") {
                Spacer(Modifier.height(3.dp))
                Text("via ${task.provider}", color = JupiterGreen.copy(alpha = 0.7f), fontSize = 10.sp)
            }
            if (task.lastError.isNotBlank() && task.status in listOf("FAILED", "BLOCKED")) {
                Spacer(Modifier.height(3.dp))
                Text(task.lastError.take(60), color = Color(0xFFFF4444).copy(alpha = 0.8f), fontSize = 10.sp)
            }
            if (task.result.isNotBlank() && task.status == "DONE") {
                Spacer(Modifier.height(4.dp))
                Text(task.result.take(120), color = JupiterWhite.copy(alpha = 0.8f), fontSize = 11.sp, lineHeight = 15.sp)
            }
            if (task.attempts > 0) {
                Spacer(Modifier.height(3.dp))
                Text("Intentos: ${task.attempts} · Prioridad: ${task.priority}",
                    color = JupiterGray.copy(alpha = 0.6f), fontSize = 9.sp)
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Eliminar",
                tint = JupiterGray.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun StatusTag(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

// ── Add task dialog ───────────────────────────────────────────────────────────

@Composable
private fun AddTaskDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit
) {
    var title    by remember { mutableStateOf("") }
    var desc     by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("MEDIUM") }
    val priorities = listOf("LOW", "MEDIUM", "HIGH", "CRITICAL")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JupiterSurface,
        title = { Text("Nueva Tarea Autónoma", color = JupiterCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                TaskField("Título de la tarea", title) { title = it }
                Spacer(Modifier.height(8.dp))
                TaskField("Descripción / objetivo", desc, maxLines = 3) { desc = it }
                Spacer(Modifier.height(10.dp))
                Text("PRIORIDAD", color = JupiterGray, fontSize = 9.sp, letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    priorities.forEach { p ->
                        val selected = p == priority
                        val pColor = when (p) {
                            "CRITICAL" -> Color(0xFFFF2222)
                            "HIGH"     -> Color(0xFFFF8800)
                            "MEDIUM"   -> JupiterCyan
                            else       -> JupiterGray
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) pColor.copy(alpha = 0.2f) else JupiterBlack)
                                .border(1.dp, if (selected) pColor else JupiterGray.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .then(Modifier.weight(1f, fill = false)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(p, color = if (selected) pColor else JupiterGray,
                                fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.clickableNoRipple { priority = p })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (title.isNotBlank()) onAdd(title, desc, priority) },
                enabled = title.isNotBlank()) {
                Text("ENVIAR AL LOOP", color = if (title.isNotBlank()) JupiterCyan else JupiterGray)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCELAR", color = JupiterGray) }
        }
    )
}

@Composable
private fun TaskField(label: String, value: String, maxLines: Int = 1, onValueChange: (String) -> Unit) {
    TextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        modifier = Modifier.fillMaxWidth(),
        maxLines = maxLines,
        colors = TextFieldDefaults.colors(
            focusedContainerColor   = JupiterBlack, unfocusedContainerColor = JupiterBlack,
            focusedTextColor        = JupiterWhite, unfocusedTextColor      = JupiterWhite,
            focusedLabelColor       = JupiterCyan,  unfocusedLabelColor     = JupiterGray,
            cursorColor             = JupiterCyan,
            focusedIndicatorColor   = JupiterCyan,  unfocusedIndicatorColor = JupiterGray
        )
    )
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
