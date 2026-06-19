package com.marketia.jupiter.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.marketia.jupiter.ui.components.HexagonChart
import com.marketia.jupiter.ui.screens.dashboard.DashboardViewModel
import com.marketia.jupiter.ui.theme.*

@Composable
fun ProfileScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val progress by viewModel.progress.collectAsState()
    val stats    by viewModel.stats.collectAsState()
    val habits   by viewModel.habits.collectAsState()
    val missions by viewModel.missions.collectAsState()

    val level      = progress?.level ?: 1
    val currentExp = progress?.currentExp ?: 0
    val maxExp     = progress?.maxExp ?: 100
    val fraction   = if (maxExp > 0) currentExp.toFloat() / maxExp else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JupiterBlack)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar + Name
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(JupiterSurface)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(JupiterDark),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚡", fontSize = 36.sp)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = progress?.name ?: "Guerrero",
                    color = JupiterWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "NIVEL  $level",
                    color = JupiterCyan,
                    fontSize = 14.sp,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.width(200.dp).height(8.dp).clip(CircleShape),
                    color = Color(0xFFFFD700),
                    trackColor = JupiterDark
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$currentExp / $maxExp EXP",
                    color = JupiterGray,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Stats grid
        Card(
            colors = CardDefaults.cardColors(containerColor = JupiterSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ESTADÍSTICAS",
                    color = JupiterGray,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                HexagonChart(
                    stats = stats,
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Summary stats
        Card(
            colors = CardDefaults.cardColors(containerColor = JupiterSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("RESUMEN", color = JupiterGray, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                ProfileRow("Hábitos completados hoy", "${habits.count { it.completed }} / ${habits.size}", JupiterGreen)
                ProfileRow("Misiones completadas",     "${missions.count { it.completed }} / ${missions.size}", JupiterCyan)
                ProfileRow("Racha máxima",              "${habits.maxOfOrNull { it.streak } ?: 0} días", Color(0xFFFFD700))
                ProfileRow("Total hábitos",             "${habits.size}", JupiterPurple)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProfileRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = JupiterGray, fontSize = 13.sp)
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
