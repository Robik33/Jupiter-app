package com.marketia.jupiter.ui.screens.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marketia.jupiter.data.entity.HabitEntity
import com.marketia.jupiter.ui.theme.*

@Composable
fun HabitsScreen(viewModel: HabitsViewModel = hiltViewModel()) {
    val habits by viewModel.habits.collectAsState()
    val completed = habits.count { it.completed }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JupiterBlack)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JupiterSurface)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "HÁBITOS",
                    color = JupiterCyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                )
                Text("$completed / ${habits.size} completados hoy", color = JupiterGray, fontSize = 12.sp)
            }
            Text(
                text = "$completed/${habits.size}",
                color = JupiterGreen,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(habits) { habit ->
                HabitRow(habit = habit, onToggle = { viewModel.toggleHabit(habit) })
            }
        }
    }
}

@Composable
private fun HabitRow(habit: HabitEntity, onToggle: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (habit.completed) Color(0xFF0D1A14) else JupiterSurface
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(habit.icon, fontSize = 26.sp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    color = if (habit.completed) JupiterGray else JupiterWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", fontSize = 12.sp)
                    Text(
                        " ${habit.streak} días de racha",
                        color = Color(0xFFFF7043),
                        fontSize = 11.sp
                    )
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
