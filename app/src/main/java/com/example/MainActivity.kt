package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val appViewModel: AppViewModel = viewModel()
                    val currentUser by appViewModel.currentUser.collectAsState()
                    val navController = rememberNavController()

                    // Redirect if user logs out or logs in
                    val startDestination = if (currentUser != null) "dashboard" else "auth"

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable("auth") {
                            AuthScreen(
                                viewModel = appViewModel,
                                onAuthSuccess = {
                                    navController.navigate("dashboard") {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("dashboard") {
                            DashboardScreen(
                                viewModel = appViewModel,
                                onNavigateToChat = {
                                    navController.navigate("chat")
                                },
                                onNavigateToImageAnalysis = {
                                    navController.navigate("image_analysis")
                                },
                                onNavigateToProfile = {
                                    navController.navigate("profile")
                                },
                                onLogout = {
                                    navController.navigate("auth") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("chat") {
                            ConsultantChatScreen(
                                viewModel = appViewModel,
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("image_analysis") {
                            ImageAnalysisScreen(
                                viewModel = appViewModel,
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("profile") {
                            ProfileScreen(
                                viewModel = appViewModel,
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
