package com.marketia.jupiter.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    data object Nucleus : BottomNavItem("nucleus", "NÚCLEO",  Icons.Filled.Hub)
    data object Memory  : BottomNavItem("memory",  "MEMORIA", Icons.Filled.Storage)
    data object Jupiter : BottomNavItem("config",  "JÚPITER", Icons.Filled.AutoAwesome)
}
