package com.mohit.rehabit.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohit.rehabit.container
import com.mohit.rehabit.ui.builder.CombinePlansScreen
import com.mohit.rehabit.ui.builder.ExerciseEditorScreen
import com.mohit.rehabit.ui.builder.PlanEditorScreen
import com.mohit.rehabit.ui.follow.FollowPlanScreen
import com.mohit.rehabit.ui.setup.SetupScreen
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mohit.rehabit.ui.library.ExerciseDetailScreen
import com.mohit.rehabit.ui.library.LibraryScreen
import com.mohit.rehabit.ui.library.PlanDetailScreen
import com.mohit.rehabit.ui.player.PlayerRequest
import com.mohit.rehabit.ui.player.PlayerScreen
import com.mohit.rehabit.ui.progress.ProgressScreen
import com.mohit.rehabit.ui.settings.SettingsScreen
import com.mohit.rehabit.ui.today.TodayScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Filled.Home),
    LIBRARY("library", "Library", Icons.AutoMirrored.Filled.List),
    PROGRESS("progress", "Progress", Icons.Filled.DateRange),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

private fun NavHostController.openTab(tab: Tab) = navigate(tab.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun RehabitAppUi(pendingRoute: String? = null, onRouteHandled: () -> Unit = {}) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val context = LocalContext.current
    val prefsFlow = remember { context.container.settings.flow }
    val prefs by prefsFlow.collectAsStateWithLifecycle(initialValue = null)

    // Opened from a notification (e.g. its Start button).
    val navReady = backStack != null
    LaunchedEffect(pendingRoute, navReady) {
        val target = pendingRoute ?: return@LaunchedEffect
        if (!navReady) return@LaunchedEffect
        if (target == Tab.TODAY.route) nav.openTab(Tab.TODAY) else nav.navigate(target)
        onRouteHandled()
    }
    val follow: (String) -> Unit = { id ->
        if (prefs?.setupDone == true) nav.navigate("follow/$id") else nav.navigate("setup?next=$id")
    }

    val openPlan: (String) -> Unit = { id -> nav.navigate("plan/$id") }
    val openExercise: (String) -> Unit = { id -> nav.navigate("exercise/$id") }
    val startPhase: (String, String) -> Unit = { planId, phaseId -> nav.navigate("player/plan/$planId/$phaseId") }

    Scaffold(
        bottomBar = {
            if (Tab.entries.any { it.route == route }) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.openTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.TODAY.route,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Tab.TODAY.route) {
                TodayScreen(
                    onStartSlot = { day, slotId -> nav.navigate("player/slot/$day/$slotId") },
                    onTryExercise = { id -> nav.navigate("player/exercise/$id") },
                    onOpenPlan = openPlan,
                    onStartRoutine = startPhase,
                    onBrowseLibrary = { nav.openTab(Tab.LIBRARY) },
                    onOpenSettings = { nav.openTab(Tab.SETTINGS) },
                    onOpenProgress = { nav.openTab(Tab.PROGRESS) },
                )
            }
            composable(Tab.LIBRARY.route) {
                LibraryScreen(
                    onOpenExercise = openExercise,
                    onOpenPlan = openPlan,
                    onShowProblems = { nav.openTab(Tab.SETTINGS) },
                    onNewExercise = { nav.navigate("exercise-edit") },
                    onNewPlan = { nav.navigate("plan-edit") },
                    onCombine = { nav.navigate("combine") },
                )
            }
            composable(Tab.PROGRESS.route) { ProgressScreen() }
            composable(Tab.SETTINGS.route) { SettingsScreen(onOpenDay = { nav.navigate("setup") }) }
            composable(
                route = "setup?next={next}",
                arguments = listOf(navArgument("next") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                val next = entry.arguments?.getString("next")
                SetupScreen(
                    firstRun = next != null,
                    onBack = { nav.popBackStack() },
                    onDone = {
                        if (next != null) {
                            nav.navigate("follow/$next") { popUpTo("setup?next={next}") { inclusive = true } }
                        } else {
                            nav.popBackStack()
                        }
                    },
                )
            }
            composable(
                route = "follow/{planId}",
                arguments = listOf(navArgument("planId") { type = NavType.StringType }),
            ) { entry ->
                FollowPlanScreen(
                    planId = entry.arguments?.getString("planId").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onFollowed = { nav.openTab(Tab.TODAY) },
                )
            }
            composable(
                route = "player/slot/{day}/{slotId}",
                arguments = listOf(
                    navArgument("day") { type = NavType.LongType },
                    navArgument("slotId") { type = NavType.StringType },
                ),
            ) { entry ->
                val request = PlayerRequest.Slot(
                    day = entry.arguments?.getLong("day") ?: 0L,
                    slotId = entry.arguments?.getString("slotId").orEmpty(),
                )
                PlayerScreen(request, onClose = { nav.popBackStack() })
            }
            composable(
                route = "exercise/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                ExerciseDetailScreen(
                    id = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenExercise = openExercise,
                    onTry = { id -> nav.navigate("player/exercise/$id") },
                    onEdit = { id -> nav.navigate("exercise-edit?id=$id") },
                    onDeleted = { nav.popBackStack() },
                )
            }
            composable(
                route = "plan/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                PlanDetailScreen(
                    id = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenExercise = openExercise,
                    onStart = startPhase,
                    onFollow = follow,
                    onEdit = { id -> nav.navigate("plan-edit?id=$id") },
                    onDeleted = { nav.popBackStack() },
                )
            }
            composable(
                route = "exercise-edit?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                val id = entry.arguments?.getString("id")
                ExerciseEditorScreen(
                    id = id,
                    onBack = { nav.popBackStack() },
                    onSaved = { savedId ->
                        nav.popBackStack()
                        if (id == null) openExercise(savedId)
                    },
                )
            }
            composable(
                route = "plan-edit?id={id}&combine={combine}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("combine") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("id")
                val combine = entry.arguments?.getString("combine")?.split(',')?.filter { it.isNotBlank() }.orEmpty()
                PlanEditorScreen(
                    id = id,
                    combine = combine,
                    onBack = { nav.popBackStack() },
                    onSaved = { savedId ->
                        nav.popBackStack()
                        if (id == null) openPlan(savedId)
                    },
                )
            }
            composable("combine") {
                CombinePlansScreen(
                    onBack = { nav.popBackStack() },
                    onCombine = { ids ->
                        nav.navigate("plan-edit?combine=${ids.joinToString(",")}") { popUpTo("combine") { inclusive = true } }
                    },
                )
            }
            composable(
                route = "player/plan/{planId}/{phaseId}",
                arguments = listOf(
                    navArgument("planId") { type = NavType.StringType },
                    navArgument("phaseId") { type = NavType.StringType },
                ),
            ) { entry ->
                val request = PlayerRequest.PlanPhase(
                    planId = entry.arguments?.getString("planId").orEmpty(),
                    phaseId = entry.arguments?.getString("phaseId").orEmpty(),
                )
                PlayerScreen(request, onClose = { nav.popBackStack() })
            }
            composable(
                route = "player/exercise/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val request = PlayerRequest.SingleExercise(entry.arguments?.getString("id").orEmpty())
                PlayerScreen(request, onClose = { nav.popBackStack() })
            }
        }
    }
}
