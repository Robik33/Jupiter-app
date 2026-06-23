package com.marketia.jupiter.ui.screens.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SkillsViewModel @Inject constructor(
    private val repository: JupiterRepository
) : ViewModel() {

    val skills = repository.skills.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    fun createSkill(name: String, category: String, description: String, tags: String) {
        viewModelScope.launch {
            repository.saveSkill(
                SkillEntity(
                    name = name.uppercase().let { if (!it.startsWith("SKILL ")) "SKILL $it" else it },
                    category = category.lowercase().trim(),
                    description = description.trim(),
                    tags = tags.trim(),
                    isBuiltIn = false
                )
            )
        }
    }

    fun updateSkill(skill: SkillEntity) {
        viewModelScope.launch { repository.updateSkill(skill) }
    }

    fun deleteSkill(skill: SkillEntity) {
        viewModelScope.launch { repository.deleteSkill(skill) }
    }

    fun duplicateSkill(skill: SkillEntity) {
        viewModelScope.launch {
            repository.saveSkill(
                skill.copy(
                    id = 0,
                    name = "${skill.name} (copia)",
                    isBuiltIn = false,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }
}

private data class SkillDisplay(val icon: String, val accentColor: Color)

private val categoryMeta = mapOf(
    "salud"          to SkillDisplay("🩺", Color(0xFF00FF88)),
    "supervivencia"  to SkillDisplay("🛡", Color(0xFFFF7043)),
    "ia"             to SkillDisplay("🤖", Color(0xFF00E5FF)),
    "ciberseguridad" to SkillDisplay("🔒", Color(0xFFFF4081)),
    "marketing"      to SkillDisplay("📡", Color(0xFF7C4DFF)),
    "sistemas"       to SkillDisplay("⚙️", Color(0xFF40C4FF)),
    "finanzas"       to SkillDisplay("📈", Color(0xFFFFD700))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(viewModel: SkillsViewModel = hiltViewModel()) {
    val skills by viewModel.skills.collectAsState()
    var selectedSkill    by remember { mutableStateOf<SkillEntity?>(null) }
    var editingSkill     by remember { mutableStateOf<SkillEntity?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var skillToDelete    by remember { mutableStateOf<SkillEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(JupiterBlack)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JupiterSurface)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column {
                    Text("SKILLS", color = JupiterCyan, fontSize = 20.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 5.sp)
                    Text("${skills.size} módulos · ${skills.count { !it.isBuiltIn }} personalizados",
                        color = JupiterGray, fontSize = 12.sp)
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
                    SkillCard(
                        skill = skill,
                        onClick = { selectedSkill = skill },
                        onEdit = { editingSkill = skill },
                        onDuplicate = { viewModel.duplicateSkill(skill) },
                        onDelete = { skillToDelete = skill }
                    )
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = JupiterCyan,
            contentColor = JupiterBlack
        ) {
            Icon(Icons.Default.Add, contentDescription = "Nueva skill")
        }
    }

    // Detail sheet
    selectedSkill?.let { skill ->
        SkillDetailSheet(skill) { selectedSkill = null }
    }

    // Create dialog
    if (showCreateDialog) {
        SkillEditDialog(
            skill = null,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, category, description, tags ->
                viewModel.createSkill(name, category, description, tags)
                showCreateDialog = false
            }
        )
    }

    // Edit dialog
    editingSkill?.let { skill ->
        SkillEditDialog(
            skill = skill,
            onDismiss = { editingSkill = null },
            onConfirm = { name, category, description, tags ->
                viewModel.updateSkill(skill.copy(
                    name = name,
                    category = category.lowercase().trim(),
                    description = description,
                    tags = tags
                ))
                editingSkill = null
            }
        )
    }

    // Delete confirmation
    skillToDelete?.let { skill ->
        AlertDialog(
            onDismissRequest = { skillToDelete = null },
            containerColor = JupiterSurface,
            title = { Text("Eliminar skill", color = Color(0xFFFF4444), fontWeight = FontWeight.Bold) },
            text = { Text("¿Eliminar \"${skill.name}\"? Esta acción no se puede deshacer.", color = JupiterGray) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSkill(skill); skillToDelete = null }) {
                    Text("ELIMINAR", color = Color(0xFFFF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { skillToDelete = null }) { Text("CANCELAR", color = JupiterGray) }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SkillCard(
    skill: SkillEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val meta   = categoryMeta[skill.category]
    val accent = meta?.accentColor ?: JupiterCyan
    val icon   = meta?.icon ?: "◈"
    var showMenu by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = JupiterSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(icon, fontSize = 28.sp)
                Column {
                    Text(
                        text = skill.name.removePrefix("SKILL "),
                        color = JupiterWhite, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = skill.tags.split(",").take(3).joinToString(" · "),
                        color = JupiterGray, fontSize = 10.sp, lineHeight = 14.sp
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
                            color = accent, fontSize = 9.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Context menu on long press
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(JupiterSurface)
            ) {
                DropdownMenuItem(
                    text = { Text("Editar", color = JupiterWhite) },
                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = JupiterCyan) },
                    onClick = { showMenu = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("Duplicar", color = JupiterWhite) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = JupiterCyan) },
                    onClick = { showMenu = false; onDuplicate() }
                )
                DropdownMenuItem(
                    text = { Text("Eliminar", color = Color(0xFFFF4444)) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF4444)) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillEditDialog(
    skill: SkillEntity?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: String, description: String, tags: String) -> Unit
) {
    var name        by remember { mutableStateOf(skill?.name?.removePrefix("SKILL ")?.trim() ?: "") }
    var category    by remember { mutableStateOf(skill?.category ?: "ia") }
    var description by remember { mutableStateOf(skill?.description ?: "") }
    var tags        by remember { mutableStateOf(skill?.tags ?: "") }
    val isEdit = skill != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JupiterSurface,
        title = {
            Text(
                if (isEdit) "Editar skill" else "Nueva skill",
                color = JupiterCyan, fontWeight = FontWeight.Black, letterSpacing = 2.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                JupiterField("Nombre", name, "ej: TRADING FOREX") { name = it }
                JupiterField("Categoría", category, "ia, finanzas, sistemas…") { category = it }
                JupiterField("Descripción", description, "Qué hace esta skill", singleLine = false) { description = it }
                JupiterField("Tags", tags, "tag1,tag2,tag3") { tags = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) onConfirm(name.trim(), category.trim(), description.trim(), tags.trim())
                },
                enabled = name.isNotBlank()
            ) {
                Text(if (isEdit) "GUARDAR" else "CREAR", color = JupiterCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCELAR", color = JupiterGray) }
        }
    )
}

@Composable
private fun JupiterField(
    label: String,
    value: String,
    placeholder: String,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = JupiterGray, fontSize = 12.sp) },
        placeholder = { Text(placeholder, color = JupiterGray.copy(alpha = 0.5f), fontSize = 11.sp) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = JupiterWhite,
            unfocusedTextColor = JupiterWhite,
            focusedBorderColor = JupiterCyan,
            unfocusedBorderColor = JupiterGray.copy(alpha = 0.4f),
            cursorColor = JupiterCyan,
            focusedLabelColor = JupiterCyan
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillDetailSheet(skill: SkillEntity, onDismiss: () -> Unit) {
    val meta   = categoryMeta[skill.category]
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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
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
