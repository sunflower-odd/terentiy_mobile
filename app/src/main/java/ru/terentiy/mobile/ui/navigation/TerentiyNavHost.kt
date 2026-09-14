package ru.terentiy.mobile.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.terentiy.mobile.domain.Session
import ru.terentiy.mobile.ui.activation.ActivationScreen
import ru.terentiy.mobile.ui.home.HomeScreen
import ru.terentiy.mobile.ui.pin.PinScreen

@Composable
fun TerentiyNavHost(viewModel: RootViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val session by viewModel.session.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            // Состояние сессии читается из DataStore асинхронно; до первого значения
            // экран остаётся нейтральным, чтобы не мигнуть чужим маршрутом.
            LaunchedEffect(session) {
                val destination = when (session) {
                    null -> return@LaunchedEffect
                    Session.NotActivated -> Routes.ACTIVATION
                    Session.NeedsPin -> Routes.PIN
                    is Session.Active -> Routes.HOME
                }
                navController.navigate(destination) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }

        composable(Routes.ACTIVATION) {
            ActivationScreen(
                onActivated = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ACTIVATION) { inclusive = true }
                    }
                },
                onGoToPin = {
                    navController.navigate(Routes.PIN) {
                        popUpTo(Routes.ACTIVATION) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.PIN) {
            PinScreen(
                onLoggedIn = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.PIN) { inclusive = true }
                    }
                },
                onReactivate = {
                    navController.navigate(Routes.ACTIVATION) {
                        popUpTo(Routes.PIN) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onSessionExpired = {
                    viewModel.dropToken()
                    navController.navigate(Routes.PIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
        }
    }
}
