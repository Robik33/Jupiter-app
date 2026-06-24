package com.marketia.jupiter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.marketia.jupiter.ui.navigation.BottomNavItem
import com.marketia.jupiter.ui.screens.memory.MemoryScreen
import com.marketia.jupiter.ui.screens.nucleus.NucleusScreen
import com.marketia.jupiter.ui.screens.settings.SettingsScreen
import com.marketia.jupiter.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { JupiterTheme { JupiterScaffold() } }
    }
}

@Composable
private fun JupiterScaffold() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val items = listOf(
        BottomNavItem.Nucleus,
        BottomNavItem.Memory,
        BottomNavItem.Jupiter
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = JupiterDark,
                tonalElevation = androidx.compose.ui.unit.Dp.Unspecified
            ) {
                items.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick  = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        },
                        icon  = { Icon(item.icon, contentDescription = item.label) },
                        label = {
                            Text(
                                item.label,
                                fontSize = 9.sp,
                                fontWeight = if (currentRoute == item.route) FontWeight.Bold else FontWeight.Normal,
                                letterSpacing = 1.5.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = JupiterCyan,
                            selectedTextColor   = JupiterCyan,
                            unselectedIconColor = JupiterGray,
                            unselectedTextColor = JupiterGray,
                            indicatorColor      = JupiterSurface
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = BottomNavItem.Nucleus.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Nucleus.route) { NucleusScreen() }
            composable(BottomNavItem.Memory.route)  { MemoryScreen() }
            composable(BottomNavItem.Jupiter.route) { SettingsScreen() }
        }
    }
}
