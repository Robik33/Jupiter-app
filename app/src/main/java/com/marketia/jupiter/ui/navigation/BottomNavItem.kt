package com.marketia.jupiter.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    data object Nucleus   : BottomNavItem("nucleus",   "Núcleo",  Icons.Filled.Hub)
    data object Skills    : BottomNavItem("skills",    "Skills",  Icons.Filled.Psychology)
    data object Memory    : BottomNavItem("memory",    "Memoria", Icons.Filled.Storage)
    data object Config    : BottomNavItem("config",    "Config",  Icons.Filled.Settings)
    data object Eval      : BottomNavItem("eval",      "Eval",    Icons.Filled.BugReport)
    data object Autonomy  : BottomNavItem("autonomy",  "Auto",    Icons.Filled.Loop)
}
