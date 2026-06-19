package com.marketia.jupiter.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.marketia.jupiter.data.entity.HabitEntity
import com.marketia.jupiter.data.entity.MissionEntity
import com.marketia.jupiter.data.entity.StatEntity
import com.marketia.jupiter.data.entity.UserProgressEntity
import com.marketia.jupiter.ui.components.HexagonChart
import com.marketia.jupiter.ui.theme.*

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val stats    by viewModel.stats.collectAsState()
    val habits   by viewModel.habits.collectAsState()
    val missions by viewModel.missions.collectAsState()
    val progress by viewModel.progress.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(JupiterBlack),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HeaderSection(progress) }
        item { HexagonStatsSection(stats) }
        item { SectionLabel("MISIÓN DIARIA") }
        items(missions) { mission -> MissionCard(mission) { viewModel.toggleMission(mission) } }
        item { SectionLabel("HÁBITOS") }
        items(habits.take(3)) { habit -> HabitCard(habit) { viewModel.toggleHabit(habit) } }
        item { ProgressSection(progress, habits, missions) }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun HeaderSection(progress: UserProgressEntity?) {
    val level      = progress?.level ?: 1
    val currentExp = progress?.currentExp ?: 0
    val maxExp     = progress?.maxExp ?: 100
    val fraction   = if (maxExp > 0) currentExp.toFloat() / maxExp.toFloat() else 0f

    JCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "JÚPITER",
                    color = JupiterCyan,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp
                )
                Text(
                    text = "Guerrero  ·  Nv.$level",
                    color = JupiterGray,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$currentExp EXP",
                    color = Color(0xFFFFD700),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "/ $maxExp",
                    color = JupiterGray,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = JupiterCyan,
            trackColor = JupiterDark
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "$currentExp / $maxExp EXP para nivel ${level + 1}",
            color = JupiterGray,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun HexagonStatsSection(stats: List<StatEntity>) {
    JCard {
        SectionLabel("ESTADÍSTICAS")
        HexagonChart(
            stats = stats,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        )
        Spacer(Modifier.height(8.dp))
        val displayStats = listOf("fuerza", "velocidad", "agilidad", "resistencia", "elasticidad", "consciencia")
            .map { name -> stats.find { it.name == name } ?: StatEntity(name, name.replaceFirstChar { it.uppercase() }, 50) }
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                displayStats.take(3).forEach { stat -> StatRow(stat) }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                displayStats.drop(3).forEach { stat -> StatRow(stat) }
            }
        }
    }
}

@Composable
private fun StatRow(stat: StatEntity) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stat.label, color = JupiterWhite, fontSize = 12.sp)
        Text("${stat.value}", color = JupiterCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MissionCard(mission: MissionEntity, onToggle: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JupiterSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = mission.completed,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = JupiterCyan,
                    uncheckedColor = JupiterGray,
                    checkmarkColor = JupiterBlack
                )
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = mission.title,
                    color = if (mission.completed) JupiterGray else JupiterWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(mission.description, color = JupiterGray, fontSize = 11.sp)
            }
            Text(
                text = "+${mission.expReward}",
                color = Color(0xFFFFD700),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(" EXP", color = JupiterGray, fontSize = 10.sp)
        }
    }
}

@Composable
private fun HabitCard(habit: HabitEntity, onToggle: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JupiterSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(habit.icon, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    color = if (habit.completed) JupiterGray else JupiterWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", fontSize = 11.sp)
                    Text(" ${habit.streak} días", color = Color(0xFFFF7043), fontSize = 11.sp)
                }
            }
            Checkbox(
                checked = habit.completed,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = JupiterGreen,
                    uncheckedColor = JupiterGray,
                    checkmarkColor = JupiterBlack
                )
            )
        }
    }
}

@Composable
private fun ProgressSection(
    progress: UserProgressEntity?,
    habits: List<HabitEntity>,
    missions: List<MissionEntity>
) {
    val habitsCompleted  = habits.count { it.completed }
    val missionsCompleted = missions.count { it.completed }

    SectionLabel("PROGRESO")
    JCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ProgressStat("Hábitos\nHoy",    "$habitsCompleted/${habits.size}",    JupiterGreen)
            ProgressDivider()
            ProgressStat("Misiones\nActivas", "$missionsCompleted/${missions.size}", JupiterCyan)
            ProgressDivider()
            ProgressStat("Racha\nMáxima",   "${habits.maxOfOrNull { it.streak } ?: 0} días", Color(0xFFFFD700))
        }
    }
}

@Composable
private fun ProgressStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = JupiterGray, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun ProgressDivider() {
    Box(modifier = Modifier.width(1.dp).height(40.dp).background(JupiterDark))
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = JupiterGray,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp
    )
}

@Composable
private fun JCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JupiterSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}
