package com.marketia.jupiter.ui.screens.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marketia.jupiter.data.entity.*
import com.marketia.jupiter.ui.theme.*

@Composable
fun MemoryScreen(viewModel: MemoryViewModel = hiltViewModel()) {
    val tabs = listOf("LINKS", "PROYECTOS", "SISTEMAS", "AGENTES", "NODOS")
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

    val links    by viewModel.links.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val systems  by viewModel.systems.collectAsState()
    val agents   by viewModel.agents.collectAsState()
    val nodes    by viewModel.nodes.collectAsState()

    Scaffold(
        containerColor = JupiterBlack,
        floatingActionButton = {
            if (selectedTab < 4) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = JupiterCyan,
                    contentColor = JupiterBlack,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                        text = "MEMORIA",
                        color = JupiterCyan,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 5.sp
                    )
                    val total = links.size + projects.size + systems.size + agents.size + nodes.size
                    Text(
                        text = "$total registros · ${nodes.size} nodos semánticos",
                        color = JupiterGray,
                        fontSize = 12.sp
                    )
                }
            }

            // Tab row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = JupiterSurface,
                contentColor = JupiterCyan,
                edgePadding = 0.dp
            ) {
                tabs.forEachIndexed { i, tab ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = {
                            Text(
                                tab,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == i) JupiterCyan else JupiterGray
                            )
                        }
                    )
                }
            }

            // Tab content
            when (selectedTab) {
                0 -> LinksTab(links)
                1 -> ProjectsTab(projects)
                2 -> SystemsTab(systems)
                3 -> AgentsTab(agents)
                4 -> NodesTab(nodes, onDelete = { viewModel.deleteNode(it) })
            }
        }
    }

    if (showAddDialog && selectedTab < 4) {
        when (selectedTab) {
            0 -> AddLinkDialog(onDismiss = { showAddDialog = false }, onAdd = { url, title, cat ->
                viewModel.addLink(url, title, cat); showAddDialog = false
            })
            1 -> AddProjectDialog(onDismiss = { showAddDialog = false }, onAdd = { name, type, desc ->
                viewModel.addProject(name, type, desc); showAddDialog = false
            })
            2 -> AddSystemDialog(onDismiss = { showAddDialog = false }, onAdd = { name, type, arch ->
                viewModel.addSystem(name, type, arch); showAddDialog = false
            })
            3 -> AddAgentDialog(onDismiss = { showAddDialog = false }, onAdd = { name, model, cap ->
                viewModel.addAgent(name, model, cap); showAddDialog = false
            })
        }
    }
}

// ── LINKS ─────────────────────────────────────────────────────────────────────

@Composable
private fun LinksTab(links: List<LinkEntity>) {
    if (links.isEmpty()) {
        EmptyState("Sin links guardados", "Guarda URLs en el tab Auto con INGESTAR LINK")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(links) { link ->
            MemoryCard(
                accent = Color(0xFF00E5FF),
                title  = link.title,
                sub    = link.url,
                meta   = link.category,
                badge  = if (link.processed) "PROCESADO" else "PENDIENTE"
            )
        }
    }
}

@Composable
private fun ProjectsTab(projects: List<ProjectEntity>) {
    if (projects.isEmpty()) {
        EmptyState("Sin proyectos", "Registra proyectos en construcción")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(projects) { p ->
            MemoryCard(accent = Color(0xFF7C4DFF), title = p.name, sub = p.description,
                meta = p.type, badge = p.status)
        }
    }
}

@Composable
private fun SystemsTab(systems: List<SystemEntity>) {
    if (systems.isEmpty()) {
        EmptyState("Sin sistemas", "Registra arquitecturas y sistemas diseñados")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(systems) { s ->
            MemoryCard(accent = Color(0xFF40C4FF), title = s.name, sub = s.architecture,
                meta = s.type, badge = s.status)
        }
    }
}

@Composable
private fun AgentsTab(agents: List<AgentEntity>) {
    if (agents.isEmpty()) {
        EmptyState("Sin agentes", "Registra bots y agentes IA creados")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(agents) { a ->
            MemoryCard(accent = Color(0xFFFF4081), title = a.name, sub = a.capability,
                meta = a.model, badge = a.status)
        }
    }
}

// ── NODOS (Semantic Memory) ───────────────────────────────────────────────────

@Composable
private fun NodesTab(nodes: List<MemoryNodeEntity>, onDelete: (MemoryNodeEntity) -> Unit) {
    if (nodes.isEmpty()) {
        EmptyState("Sin nodos semánticos", "Los nodos se crean al ingestar links o crear skills")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(nodes, key = { it.id }) { node ->
            NodeCard(node = node, onDelete = { onDelete(node) })
        }
    }
}

@Composable
private fun NodeCard(node: MemoryNodeEntity, onDelete: () -> Unit) {
    val (accent, typeLabel) = when (node.type) {
        "SKILL"   -> Color(0xFF00FF88) to "SKILL"
        "LINK"    -> Color(0xFF00E5FF) to "LINK"
        "PROJECT" -> Color(0xFF7C4DFF) to "PROYECTO"
        "SYSTEM"  -> Color(0xFF40C4FF) to "SISTEMA"
        "AGENT"   -> Color(0xFFFF4081) to "AGENTE"
        "PROMPT"  -> Color(0xFFFFD700) to "PROMPT"
        "API"     -> Color(0xFFFF8800) to "API"
        "TASK"    -> Color(0xFFFF6B6B) to "TAREA"
        else      -> JupiterGray       to node.type
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(JupiterSurface)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent.copy(alpha = 0.15f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(typeLabel, color = accent, fontSize = 8.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
                Spacer(Modifier.width(6.dp))
                Text(node.label, color = JupiterWhite, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (node.summary.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(node.summary.take(80), color = JupiterGray, fontSize = 11.sp, lineHeight = 15.sp)
            }
            if (node.tags.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(node.tags, color = accent.copy(alpha = 0.6f), fontSize = 9.sp, letterSpacing = 0.3.sp)
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Eliminar",
                tint = JupiterGray.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
        }
    }
}

// ── SHARED CARD ───────────────────────────────────────────────────────────────

@Composable
private fun MemoryCard(accent: Color, title: String, sub: String, meta: String, badge: String? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JupiterSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.width(3.dp).height(48.dp)
                    .clip(RoundedCornerShape(2.dp)).background(accent)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = JupiterWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    if (badge != null) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                .background(accent.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(badge.uppercase(), color = accent, fontSize = 9.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(sub, color = JupiterGray, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(meta.uppercase(), color = accent.copy(alpha = 0.7f), fontSize = 9.sp, letterSpacing = 1.sp)
            }
        }
    }
}

// ── EMPTY STATE ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("◈", fontSize = 36.sp, color = JupiterGray.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))
            Text(title, color = JupiterGray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = JupiterGray.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

// ── ADD DIALOGS ───────────────────────────────────────────────────────────────

@Composable
private fun AddLinkDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("general") }
    JupiterDialog("Guardar Link", onDismiss, onConfirm = { onAdd(url, title, cat) },
        confirmEnabled = url.isNotBlank() && title.isNotBlank()) {
        JupiterField("URL", url) { url = it }
        Spacer(Modifier.height(8.dp))
        JupiterField("Título", title) { title = it }
        Spacer(Modifier.height(8.dp))
        JupiterField("Categoría", cat) { cat = it }
    }
}

@Composable
private fun AddProjectDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("app") }
    var desc by remember { mutableStateOf("") }
    JupiterDialog("Nuevo Proyecto", onDismiss, onConfirm = { onAdd(name, type, desc) },
        confirmEnabled = name.isNotBlank()) {
        JupiterField("Nombre", name) { name = it }
        Spacer(Modifier.height(8.dp))
        JupiterField("Tipo (app, bot, sistema...)", type) { type = it }
        Spacer(Modifier.height(8.dp))
        JupiterField("Descripción", desc) { desc = it }
    }
}

@Composable
private fun AddSystemDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("automatización") }
    var arch by remember { mutableStateOf("") }
    JupiterDialog("Nuevo Sistema", onDismiss, onConfirm = { onAdd(name, type, arch) },
        confirmEnabled = name.isNotBlank()) {
        JupiterField("Nombre", name) { name = it }
        Spacer(Modifier.height(8.dp))
        JupiterField("Tipo", type) { type = it }
        Spacer(Modifier.height(8.dp))
        JupiterField("Arquitectura", arch) { arch = it }
    }
}

@Composable
private fun AddAgentDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("Claude Sonnet") }
    var cap by remember { mutableStateOf("") }
    JupiterDialog("Nuevo Agente", onDismiss, onConfirm = { onAdd(name, model, cap) },
        confirmEnabled = name.isNotBlank()) {
        JupiterField("Nombre", name) { name = it }
        Spacer(Modifier.height(8.dp))
        JupiterField("Modelo base", model) { model = it }
        Spacer(Modifier.height(8.dp))
        JupiterField("Capacidad", cap) { cap = it }
    }
}

// ── DIALOG SHELL ─────────────────────────────────────────────────────────────

@Composable
private fun JupiterDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JupiterSurface,
        title = { Text(title, color = JupiterCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = { Column(content = content) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text("GUARDAR", color = if (confirmEnabled) JupiterCyan else JupiterGray)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCELAR", color = JupiterGray) }
        }
    )
}

@Composable
private fun JupiterField(label: String, value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor   = JupiterBlack, unfocusedContainerColor = JupiterBlack,
            focusedTextColor        = JupiterWhite, unfocusedTextColor      = JupiterWhite,
            focusedLabelColor       = JupiterCyan,  unfocusedLabelColor     = JupiterGray,
            cursorColor             = JupiterCyan,
            focusedIndicatorColor   = JupiterCyan,  unfocusedIndicatorColor = JupiterGray
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = {})
    )
}
