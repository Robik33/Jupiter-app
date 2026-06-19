package com.marketia.jupiter.ui.screens.evaluation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.marketia.jupiter.core.bridge.BridgeResult
import com.marketia.jupiter.core.bridge.ClaudeCodeTask
import com.marketia.jupiter.core.evaluation.EvaluationIssue
import com.marketia.jupiter.core.evaluation.EvaluationReport
import com.marketia.jupiter.core.evaluation.IssueSeverity
import com.marketia.jupiter.ui.theme.*

@Composable
fun SelfEvaluationScreen(viewModel: SelfEvaluationViewModel = hiltViewModel()) {
    val evalState   by viewModel.evalState.collectAsState()
    val bridgeState by viewModel.bridgeState.collectAsState()
    val pendingTask by viewModel.pendingTask.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JupiterBlack)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(JupiterSurface)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Text(
                    "AUTOEVALUACIÓN",
                    color = JupiterCyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 5.sp
                )
                Text(
                    "Diagnóstico interno del sistema JÚPITER",
                    color = JupiterGray,
                    fontSize = 12.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Eval button ─────────────────────────────────────────────────
            Button(
                onClick = { viewModel.evaluate() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = JupiterCyan),
                shape = RoundedCornerShape(14.dp),
                enabled = evalState !is EvalState.Loading
            ) {
                Text(
                    if (evalState is EvalState.Loading) "EVALUANDO..." else "◈  AUTOEVALUARME",
                    color = JupiterBlack,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Loading ──────────────────────────────────────────────────────
            if (evalState is EvalState.Loading) {
                CircularProgressIndicator(color = JupiterCyan, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text("Analizando sistema...", color = JupiterGray, fontSize = 12.sp)
            }

            // ── Error ────────────────────────────────────────────────────────
            if (evalState is EvalState.Error) {
                ErrorCard((evalState as EvalState.Error).msg)
            }

            // ── Report ───────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = evalState is EvalState.Done,
                enter = fadeIn(),
                exit  = fadeOut()
            ) {
                val report = (evalState as? EvalState.Done)?.report ?: return@AnimatedVisibility
                ReportContent(
                    report = report,
                    onSendToClaudeCode = { viewModel.prepareTask(report) }
                )
            }

            // ── Approval gate ────────────────────────────────────────────────
            AnimatedVisibility(visible = pendingTask != null) {
                pendingTask?.let { task ->
                    ApprovalCard(
                        task      = task,
                        sending   = bridgeState is BridgeState.Sending,
                        onApprove = { viewModel.approveAndSend() },
                        onReject  = { viewModel.rejectTask() }
                    )
                }
            }

            // ── Sent result ──────────────────────────────────────────────────
            AnimatedVisibility(visible = bridgeState is BridgeState.Sent) {
                val result = (bridgeState as? BridgeState.Sent)?.result
                result?.let { SentResultCard(it) }
            }
        }
    }
}

// ── Report content ────────────────────────────────────────────────────────────

@Composable
private fun ReportContent(report: EvaluationReport, onSendToClaudeCode: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {

        // Version badge
        VersionBadge(report)
        Spacer(Modifier.height(16.dp))

        // Active modules
        SectionHeader("MÓDULOS ACTIVOS", JupiterGreen)
        report.activeModules.forEach { module ->
            BulletRow(module, JupiterGreen)
        }
        Spacer(Modifier.height(14.dp))

        // Issues
        if (report.detectedIssues.isNotEmpty()) {
            SectionHeader("PROBLEMAS DETECTADOS", Color(0xFFFF4444))
            report.detectedIssues.forEach { issue ->
                IssueCard(issue)
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(14.dp))
        }

        // Memory stats
        SectionHeader("MEMORIA", JupiterCyan)
        StatsRow(report.memoryStats.totalSkills, report.memoryStats.totalProjects,
                 report.memoryStats.totalLinks, report.memoryStats.totalAgents)
        Spacer(Modifier.height(14.dp))

        // Voice
        SectionHeader("VOZ", JupiterPurple)
        VoiceStatusRow(report.voiceStatus)
        Spacer(Modifier.height(14.dp))

        // Skills available
        SectionHeader("SKILLS DISPONIBLES", JupiterCyan)
        report.availableSkills.take(8).forEach { skill ->
            BulletRow(skill, JupiterCyan)
        }
        Spacer(Modifier.height(14.dp))

        // Missing capabilities
        if (report.missingCapabilities.isNotEmpty()) {
            SectionHeader("CAPACIDADES FALTANTES", JupiterGray)
            report.missingCapabilities.forEach { cap ->
                BulletRow(cap, JupiterGray)
            }
            Spacer(Modifier.height(14.dp))
        }

        // Recommendations
        if (report.recommendations.isNotEmpty()) {
            SectionHeader("RECOMENDACIONES", Color(0xFFFFD700))
            report.recommendations.forEach { rec ->
                BulletRow(rec, Color(0xFFFFD700))
            }
            Spacer(Modifier.height(20.dp))
        }

        // Send to Claude Code button
        OutlinedButton(
            onClick = onSendToClaudeCode,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(JupiterCyan)
            ),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = JupiterCyan)
        ) {
            Text(
                "▲  ENVIAR TAREA A CLAUDE CODE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── Sub-components ────────────────────────────────────────────────────────────

@Composable
private fun VersionBadge(report: EvaluationReport) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(JupiterSurface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("v${report.version}", color = JupiterCyan, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("build ${report.versionCode}", color = JupiterGray, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            val issueCount = report.detectedIssues.size
            val highCount = report.detectedIssues.count { it.severity == IssueSeverity.HIGH }
            StatusChip(
                text = if (issueCount == 0) "SISTEMA OK" else "$highCount CRÍTICOS · $issueCount TOTAL",
                color = when {
                    highCount > 0 -> Color(0xFFFF4444)
                    issueCount > 0 -> Color(0xFFFFAA00)
                    else -> JupiterGreen
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(label: String, color: Color) {
    Text(
        label,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun BulletRow(text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.7f))
        )
        Spacer(Modifier.width(10.dp))
        Text(text, color = JupiterWhite, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun IssueCard(issue: EvaluationIssue) {
    val (accentColor, severityLabel) = when (issue.severity) {
        IssueSeverity.HIGH   -> Color(0xFFFF4444) to "CRÍTICO"
        IssueSeverity.MEDIUM -> Color(0xFFFFAA00) to "MEDIO"
        IssueSeverity.LOW    -> JupiterGray       to "BAJO"
        IssueSeverity.INFO   -> JupiterCyan       to "INFO"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(JupiterSurface)
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(severityLabel, accentColor)
                Spacer(Modifier.width(8.dp))
                Text(issue.module, color = JupiterWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(issue.description, color = JupiterGray, fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text("→ ${issue.suggestedFix}", color = accentColor.copy(alpha = 0.8f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun StatsRow(skills: Int, projects: Int, links: Int, agents: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            skills    to "SKILLS",
            projects  to "PROYECTOS",
            links     to "LINKS",
            agents    to "AGENTES"
        ).forEach { (count, label) ->
            StatChip(count.toString(), label, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatChip(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(JupiterSurface)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = JupiterCyan, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(label, color = JupiterGray, fontSize = 9.sp, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun VoiceStatusRow(voice: com.marketia.jupiter.core.evaluation.VoiceStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(JupiterSurface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusChip(if (voice.sttAvailable) "STT ✓" else "STT ✗",
            if (voice.sttAvailable) JupiterGreen else Color(0xFFFF4444))
        StatusChip("TTS ✓", JupiterGreen)
        Text("vel ${"%.2f".format(voice.currentSpeed)}", color = JupiterCyan, fontSize = 11.sp)
        Text("tono ${"%.2f".format(voice.currentPitch)}", color = JupiterPurple, fontSize = 11.sp)
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

// ── Approval card ─────────────────────────────────────────────────────────────

@Composable
private fun ApprovalCard(
    task: ClaudeCodeTask,
    sending: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(JupiterSurface)
            .border(1.dp, JupiterCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Text("PENDIENTE DE APROBACIÓN", color = JupiterCyan, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        TaskRow("Objetivo", task.goal.take(80))
        TaskRow("Problema", task.problem.take(100))
        TaskRow("Canal", task.channel.name)
        TaskRow("Prioridad", task.priority.name)
        TaskRow("Cambio solicitado", task.requestedChange.take(120))
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                enabled = !sending,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = JupiterGray)
            ) { Text("RECHAZAR", fontSize = 11.sp, letterSpacing = 1.sp) }
            Button(
                onClick = onApprove,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                enabled = !sending,
                colors = ButtonDefaults.buttonColors(containerColor = JupiterCyan)
            ) {
                if (sending) CircularProgressIndicator(modifier = Modifier.size(16.dp),
                    color = JupiterBlack, strokeWidth = 2.dp)
                else Text("APROBAR Y ENVIAR", color = JupiterBlack, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun TaskRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label.uppercase(), color = JupiterGray, fontSize = 9.sp, letterSpacing = 1.sp)
        Text(value, color = JupiterWhite, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

// ── Sent result card ──────────────────────────────────────────────────────────

@Composable
private fun SentResultCard(result: BridgeResult) {
    val isOk = result.success
    val color = if (isOk) JupiterGreen else Color(0xFFFF4444)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(JupiterSurface)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            if (isOk) "✓ ENVIADO" else "✗ ERROR",
            color = color, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(result.message, color = JupiterWhite, fontSize = 13.sp, lineHeight = 18.sp)
        if (result.issueUrl?.isNotBlank() == true) {
            Spacer(Modifier.height(6.dp))
            Text(result.issueUrl, color = JupiterCyan, fontSize = 11.sp)
        }
    }
    Spacer(Modifier.height(16.dp))
}

// ── Error card ────────────────────────────────────────────────────────────────

@Composable
private fun ErrorCard(msg: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFFF4444).copy(alpha = 0.12f))
            .padding(14.dp)
    ) {
        Text("Error: $msg", color = Color(0xFFFF4444), fontSize = 13.sp)
    }
    Spacer(Modifier.height(12.dp))
}
