package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.GymLoadingIndicator
import com.example.ui.MainScreen
import com.example.ui.OnboardingScreen
import com.example.ui.WelcomeScreen
import com.example.ui.WorkoutSetupScreen
import com.example.ui.theme.MyApplicationTheme

import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as GymApplication
            val viewModel: GymViewModel = viewModel(
                factory = GymViewModelFactory(
                    app.repository,
                    app.localFoodRepository,
                    app.localExerciseRepository,
                    app.offRepository,
                    app.aiManager,
                    app.modelDownloadManager,
                    app.secureStorageManager,
                    app.exerciseGuideRepository,
                    app.coachHistoryRepository,
                    app.appUpdateManager
                )
            )

            val profile by viewModel.userProfile.collectAsState()
            val profileLoaded by viewModel.initialProfileLoaded.collectAsState()

            MyApplicationTheme(themeMode = profile.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                    DisposableEffect(viewModel, lifecycleOwner) {
                        val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
                            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                                viewModel.archiveCoachSessionOnBackground()
                            }
                        }
                        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
                        onDispose {
                            androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
                        }
                    }

                    if (!profileLoaded) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            GymLoadingIndicator(message = "Loading your profile…")
                        }
                        return@Surface
                    }

                    val navController = rememberNavController()
                    val targetRoute = when {
                        !profile.onboardingComplete -> "welcome"
                        !profile.workoutSetupComplete -> "workout_setup"
                        else -> "main"
                    }

                    LaunchedEffect(targetRoute) {
                        val current = navController.currentBackStackEntry?.destination?.route
                        if (current != targetRoute) {
                            navController.navigate(targetRoute) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = targetRoute
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
                                navController.navigate("workout_setup") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            }
                        }
                        composable("workout_setup") {
                            WorkoutSetupScreen(
                                viewModel = viewModel,
                                profile = profile,
                                onComplete = {
                                    navController.navigate("main") {
                                        popUpTo("workout_setup") { inclusive = true }
                                    }
                                }
                            )
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
