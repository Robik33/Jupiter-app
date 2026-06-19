package com.marketia.jupiter.ui.screens.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketia.jupiter.data.entity.SkillEntity
import com.marketia.jupiter.data.repository.JupiterRepository
import com.marketia.jupiter.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SkillsViewModel @Inject constructor(
    private val repository: JupiterRepository
) : ViewModel() {
    val skills = repository.skills.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private data class SkillDisplay(
    val category: String,
    val icon: String,
    val accentColor: Color
)

private val categoryMeta = mapOf(
    "salud"         to SkillDisplay("salud",         "🩺", Color(0xFF00FF88)),
    "supervivencia" to SkillDisplay("supervivencia", "🛡", Color(0xFFFF7043)),
    "ia"            to SkillDisplay("ia",            "🤖", Color(0xFF00E5FF)),
    "ciberseguridad" to SkillDisplay("ciberseguridad","🔒", Color(0xFFFF4081)),
    "marketing"     to SkillDisplay("marketing",     "📡", Color(0xFF7C4DFF)),
    "sistemas"      to SkillDisplay("sistemas",      "⚙️", Color(0xFF40C4FF)),
    "finanzas"      to SkillDisplay("finanzas",      "📈", Color(0xFFFFD700))
)

@Composable
fun SkillsScreen(viewModel: SkillsViewModel = hiltViewModel()) {
    val skills by viewModel.skills.collectAsState()
    var selectedSkill by remember { mutableStateOf<SkillEntity?>(null) }

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
                Text(
                    text = "SKILLS",
                    color = JupiterCyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 5.sp
                )
                Text(
                    text = "${skills.size} módulos de conocimiento activos",
                    color = JupiterGray,
                    fontSize = 12.sp
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(skills) { skill ->
                SkillCard(skill) { selectedSkill = skill }
            }
        }
    }

    selectedSkill?.let { skill ->
        SkillDetailSheet(skill) { selectedSkill = null }
    }
}

@Composable
private fun SkillCard(skill: SkillEntity, onClick: () -> Unit) {
    val meta = categoryMeta[skill.category]
    val accent = meta?.accentColor ?: JupiterCyan
    val icon = meta?.icon ?: "◈"

    Card(
        colors = CardDefaults.cardColors(containerColor = JupiterSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clickable(onClick = onClick)
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(icon, fontSize = 28.sp)

            Column {
                Text(
                    text = skill.name.removePrefix("SKILL "),
                    color = JupiterWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = skill.tags.split(",").take(3).joinToString(" · "),
                    color = JupiterGray,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (skill.isBuiltIn) "ACTIVO" else "CUSTOM",
                        color = accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillDetailSheet(skill: SkillEntity, onDismiss: () -> Unit) {
    val meta = categoryMeta[skill.category]
    val accent = meta?.accentColor ?: JupiterCyan

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = JupiterSurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text(meta?.icon ?: "◈", fontSize = 36.sp)
            Spacer(Modifier.height(8.dp))
            Text(skill.name, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.height(4.dp))
            Text(skill.description, color = JupiterGray, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(16.dp))
            Text("MÓDULOS", color = JupiterGray, fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            skill.tags.split(",").forEach { tag ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(accent))
                    Spacer(Modifier.width(10.dp))
                    Text(tag.trim(), color = JupiterWhite, fontSize = 14.sp)
                }
            }
        }
    }
}
