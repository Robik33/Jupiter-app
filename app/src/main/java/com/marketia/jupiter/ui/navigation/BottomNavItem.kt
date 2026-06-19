package com.marketia.jupiter.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : BottomNavItem("dashboard", "Dashboard", Icons.Filled.Home)
    data object Habits    : BottomNavItem("habits",    "Hábitos",   Icons.Filled.List)
    data object Profile   : BottomNavItem("profile",   "Perfil",    Icons.Filled.Person)
}
