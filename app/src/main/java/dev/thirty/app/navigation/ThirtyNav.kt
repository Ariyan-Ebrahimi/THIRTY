package dev.thirty.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.thirty.app.data.repository.ThirtyRepository
import dev.thirty.app.ui.screens.completion.CompletionScreen
import dev.thirty.app.ui.screens.history.HistoryDetailScreen
import dev.thirty.app.ui.screens.history.HistoryScreen
import dev.thirty.app.ui.screens.journey.JourneyScreen
import dev.thirty.app.ui.screens.journey.JourneyViewModel
import dev.thirty.app.ui.screens.journey.JourneyViewModelFactory
import dev.thirty.app.ui.screens.onboarding.OnboardingFlow
import dev.thirty.app.ui.screens.onboarding.OnboardingViewModel
import dev.thirty.app.ui.screens.onboarding.OnboardingViewModelFactory
import dev.thirty.app.ui.screens.settings.SettingsScreen
import dev.thirty.app.ui.screens.splash.SplashScreen
import dev.thirty.app.ui.screens.today.TodayScreen
import dev.thirty.app.ui.screens.today.TodayViewModel
import dev.thirty.app.ui.screens.today.TodayViewModelFactory
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val TODAY = "today"
    const val JOURNEY = "journey"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history_detail/{challengeId}"
    const val COMPLETION = "completion/{challengeId}"
    fun historyDetail(id: Long) = "history_detail/$id"
    fun completion(id: Long) = "completion/$id"
}

@Composable
fun ThirtyNav(repository: ThirtyRepository) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    var startDest by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            repository.refreshDayStates()
        } catch (_: Exception) { }
        // Decide synchronously via one-shot read
        val hasActive = try {
            repository.hasAnyActive()
        } catch (_: Exception) { false }
        startDest = if (hasActive) Routes.TODAY else Routes.SPLASH
    }

    if (startDest == null) {
        SplashScreen(onDone = {})
        return
    }

    val showBottomBar = route in listOf(Routes.TODAY, Routes.JOURNEY, Routes.SETTINGS)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color.Transparent) {
                    val items = listOf(
                        Triple(Routes.TODAY, "Today", Icons.Filled.Home),
                        Triple(Routes.JOURNEY, "Journey", Icons.Filled.DateRange),
                        Triple(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
                    )
                    items.forEach { (r, label, icon) ->
                        NavigationBarItem(
                            selected = route == r,
                            onClick = {
                                nav.navigate(r) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = startDest!!,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(onDone = {
                    nav.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                })
            }
            composable(Routes.ONBOARDING) {
                val factory = remember { OnboardingViewModelFactory(repository) }
                val vm: OnboardingViewModel = viewModel(factory = factory)
                val activePlans by repository.activeListFlow.collectAsState(initial = emptyList())
                OnboardingFlow(
                    vm = vm,
                    canGoBack = activePlans.isNotEmpty(),
                    onBack = { nav.popBackStack() },
                    onFinished = {
                        nav.navigate(Routes.TODAY) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.TODAY) {
                val factory = remember { TodayViewModelFactory(repository) }
                val vm: TodayViewModel = viewModel(factory = factory)
                TodayScreen(
                    vm = vm,
                    onGoJourney = { nav.navigate(Routes.JOURNEY) },
                    onGoOnboarding = {
                        // Keep Today on the back stack so the flow's back button returns here.
                        nav.navigate(Routes.ONBOARDING)
                    },
                    onCompleted30 = { id -> nav.navigate(Routes.completion(id)) }
                )
            }
            composable(Routes.JOURNEY) {
                val factory = remember { JourneyViewModelFactory(repository) }
                val vm: JourneyViewModel = viewModel(factory = factory)
                JourneyScreen(
                    vm = vm,
                    onStartNew = { nav.navigate(Routes.ONBOARDING) },
                    onOpenHistory = { nav.navigate(Routes.HISTORY) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    repository = repository,
                    onOpenHistory = { nav.navigate(Routes.HISTORY) }
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    repository = repository,
                    onBack = { nav.popBackStack() },
                    onOpen = { id -> nav.navigate(Routes.historyDetail(id)) }
                )
            }
            composable(
                Routes.HISTORY_DETAIL,
                arguments = listOf(navArgument("challengeId") { type = NavType.LongType })
            ) { entry ->
                HistoryDetailScreen(
                    repository = repository,
                    challengeId = entry.arguments?.getLong("challengeId") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onRerun = {
                        // Land on Today with the fresh run already selected.
                        nav.navigate(Routes.TODAY) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(
                Routes.COMPLETION,
                arguments = listOf(navArgument("challengeId") { type = NavType.LongType })
            ) { entry ->
                CompletionScreen(
                    repository = repository,
                    challengeId = entry.arguments?.getLong("challengeId") ?: 0L,
                    onKeepHabit = {
                        nav.navigate(Routes.TODAY) {
                            popUpTo(Routes.COMPLETION) { inclusive = true }
                        }
                    },
                    onStartAnother = {
                        nav.navigate(Routes.ONBOARDING) {
                            popUpTo(Routes.COMPLETION) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
