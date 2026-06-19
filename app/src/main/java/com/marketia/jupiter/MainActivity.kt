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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.marketia.jupiter.ui.navigation.BottomNavItem
import com.marketia.jupiter.ui.screens.autonomy.AutonomyScreen
import com.marketia.jupiter.ui.screens.evaluation.SelfEvaluationScreen
import com.marketia.jupiter.ui.screens.memory.MemoryScreen
import com.marketia.jupiter.ui.screens.nucleus.NucleusScreen
import com.marketia.jupiter.ui.screens.settings.SettingsScreen
import com.marketia.jupiter.ui.screens.skills.SkillsScreen
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
        BottomNavItem.Skills,
        BottomNavItem.Memory,
        BottomNavItem.Config,
        BottomNavItem.Eval,
        BottomNavItem.Autonomy
    )

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = JupiterSurface) {
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
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = JupiterCyan,
                            selectedTextColor   = JupiterCyan,
                            unselectedIconColor = JupiterGray,
                            unselectedTextColor = JupiterGray,
                            indicatorColor      = JupiterDark
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
            composable(BottomNavItem.Skills.route)  { SkillsScreen() }
            composable(BottomNavItem.Memory.route)  { MemoryScreen() }
            composable(BottomNavItem.Config.route)  { SettingsScreen() }
            composable(BottomNavItem.Eval.route)      { SelfEvaluationScreen() }
            composable(BottomNavItem.Autonomy.route) { AutonomyScreen() }
        }
    }
}
