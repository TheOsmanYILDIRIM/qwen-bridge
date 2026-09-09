package com.qwenbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qwenbridge.data.ConfigManager
import com.qwenbridge.ui.screens.*
import com.qwenbridge.ui.theme.*

enum class AppDestination(val route: String, val title: String, val icon: ImageVector) {
    DASHBOARD("dashboard", "Dashboard", Icons.Default.Dashboard),
    CHAT("chat", "Chat", Icons.Default.Chat),
    STUDIO("studio", "Studio", Icons.Default.AutoFixHigh),
    LOGS("logs", "Logs", Icons.Default.FormatListBulleted),
    TOKEN("token", "Token", Icons.Default.Key),
    SETTINGS("settings", "Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val configManager = ConfigManager.getInstance(this)

        setContent {
            QwenBridgeTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: AppDestination.DASHBOARD.route

                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = SurfaceDark,
                            tonalElevation = 8.dp
                        ) {
                            AppDestination.values().forEach { destination ->
                                val selected = currentRoute == destination.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (currentRoute != destination.route) {
                                            navController.navigate(destination.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = destination.icon,
                                            contentDescription = destination.title,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    label = {
                                        Text(text = destination.title, fontSize = 10.sp)
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PrimaryIndigo,
                                        selectedTextColor = PrimaryIndigo,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = BgDark
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(BgDark)
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = AppDestination.DASHBOARD.route
                        ) {
                            composable(AppDestination.DASHBOARD.route) {
                                DashboardScreen(
                                    configManager = configManager,
                                    onNavigateToSetup = { navController.navigate("setup") },
                                    onNavigateToToken = { navController.navigate(AppDestination.TOKEN.route) }
                                )
                            }
                            composable(AppDestination.CHAT.route) {
                                ChatPlaygroundScreen(configManager = configManager)
                            }
                            composable(AppDestination.STUDIO.route) {
                                MediaStudioScreen(configManager = configManager)
                            }
                            composable(AppDestination.LOGS.route) {
                                LogsScreen(configManager = configManager)
                            }
                            composable(AppDestination.TOKEN.route) {
                                TokenScreen(configManager = configManager)
                            }
                            composable(AppDestination.SETTINGS.route) {
                                SettingsScreen(configManager = configManager)
                            }
                            composable("setup") {
                                SetupWizardScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}
