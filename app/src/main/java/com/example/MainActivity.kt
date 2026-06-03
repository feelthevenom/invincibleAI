package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainScreen
import com.example.ui.OnboardingScreen
import com.example.ui.WelcomeScreen
import com.example.ui.theme.MyApplicationTheme

import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val app = application as GymApplication
                    val viewModel: GymViewModel = viewModel(
                        factory = GymViewModelFactory(
                            app.repository,
                            app.localFoodRepository,
                            app.offRepository,
                            app.aiManager,
                            app.modelDownloadManager,
                            app.secureStorageManager
                        )
                    )

                    val profile by viewModel.userProfile.collectAsState()
                    val navController = rememberNavController()
                    val startDestination = if (profile.onboardingComplete) "main" else "welcome"

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable("welcome") {
                            WelcomeScreen {
                                navController.navigate("onboarding") {
                                    popUpTo("welcome") { inclusive = true }
                                }
                            }
                        }
                        composable("onboarding") {
                            OnboardingScreen(viewModel) {
                                navController.navigate("main") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            }
                        }
                        composable("main") {
                            MainScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}
