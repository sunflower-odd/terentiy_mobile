package ru.terentiy.mobile.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.terentiy.mobile.domain.SessionState
import ru.terentiy.mobile.ui.activation.ActivationScreen
import ru.terentiy.mobile.ui.clip.ClipScreen
import ru.terentiy.mobile.ui.eventdetail.EventDetailScreen
import ru.terentiy.mobile.ui.events.EventsScreen
import ru.terentiy.mobile.ui.home.HomeScreen
import ru.terentiy.mobile.ui.pin.PinScreen
import ru.terentiy.mobile.ui.profile.ProfileScreen
import ru.terentiy.mobile.ui.zones.ZonesScreen

/**
 * Навигация приложения.
 *
 * Таб-бар живёт внутри каждой вкладки, а не вокруг всего графа: карточка события,
 * проигрыватель и экраны входа держат собственные отступы под системные панели, а
 * вложенные друг в друга `Scaffold` разбирают эти отступы дважды.
 */
@Composable
fun TerentiyNavHost(viewModel: RootViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()

    /**
     * Потеря сессии обрабатывается здесь, один раз для всего приложения.
     *
     * Сервер отвечает `401`, если токен ему неизвестен; `AuthorizedCall` стирает
     * сессию, и состояние доходит сюда. Экранам не нужно об этом знать — раньше
     * каждый из них держал собственный флаг «сессия истекла».
     */
    LaunchedEffect(sessionState, currentEntry?.destination?.route) {
        val route = currentEntry?.destination?.route ?: return@LaunchedEffect
        if (sessionState == SessionState.NeedsPin && route !in Routes.AUTH_ROUTES) {
            navController.resetTo(Routes.PIN)
        }
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            LaunchedEffect(sessionState) {
                val destination = when (sessionState) {
                    null -> return@LaunchedEffect
                    SessionState.NotActivated -> Routes.ACTIVATION
                    SessionState.NeedsPin -> Routes.PIN
                    is SessionState.Active -> Routes.HOME
                }
                navController.resetTo(destination)
            }
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        composable(Routes.ACTIVATION) {
            // safeDrawingPadding, а не обычные отступы: в safeDrawing входит
            // клавиатура, поэтому поля формы не уезжают под неё.
            Box(Modifier.safeDrawingPadding()) {
                ActivationScreen(
                    onActivated = { navController.resetTo(Routes.HOME) },
                    onGoToPin = { navController.resetTo(Routes.PIN) },
                )
            }
        }

        composable(Routes.PIN) {
            Box(Modifier.safeDrawingPadding()) {
                PinScreen(
                    onLoggedIn = { navController.resetTo(Routes.HOME) },
                    onActivateAgain = { navController.resetTo(Routes.ACTIVATION) },
                )
            }
        }

        composable(Routes.HOME) {
            TerentiyScaffold(currentRoute = Routes.HOME, onSelectTab = navController::openTab) { padding ->
                HomeScreen(
                    contentPadding = padding,
                    onOpenEvent = { eventId -> navController.navigate(Routes.eventDetail(eventId)) },
                    onOpenHistory = { navController.openTab(Routes.EVENTS) },
                )
            }
        }

        composable(Routes.EVENTS) {
            TerentiyScaffold(currentRoute = Routes.EVENTS, onSelectTab = navController::openTab) { padding ->
                EventsScreen(
                    contentPadding = padding,
                    onOpenEvent = { eventId -> navController.navigate(Routes.eventDetail(eventId)) },
                )
            }
        }

        composable(Routes.PROFILE) {
            TerentiyScaffold(currentRoute = Routes.PROFILE, onSelectTab = navController::openTab) { padding ->
                ProfileScreen(
                    contentPadding = padding,
                    onLoggedOut = { navController.resetTo(Routes.PIN) },
                    onOpenZones = { navController.navigate(Routes.ZONES) },
                )
            }
        }

        composable(Routes.EVENT_DETAIL) {
            EventDetailScreen(
                onBack = { navController.popBackStack() },
                onPlayClip = { mediaId -> navController.navigate(Routes.clip(mediaId)) },
            )
        }

        composable(Routes.CLIP) {
            ClipScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ZONES) {
            ZonesScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** Переход между вкладками: одна запись в стеке на вкладку, состояние сохраняется. */
private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Смена сессии: стек очищается целиком.
 *
 * `popUpTo(graph.id)` вместо конкретного маршрута — после выхода или входа в
 * стеке может лежать что угодно, и оставлять там экраны прошлой сессии нельзя.
 */
private fun NavHostController.resetTo(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
