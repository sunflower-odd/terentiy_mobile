package ru.terentiy.mobile.ui.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.terentiy.mobile.R
import ru.terentiy.mobile.ui.common.HomeIllustration
import ru.terentiy.mobile.ui.common.IconKey
import ru.terentiy.mobile.ui.common.MessageBanner
import ru.terentiy.mobile.ui.common.PasswordField
import ru.terentiy.mobile.ui.common.PrimaryButton

@Composable
fun PinScreen(
    onLoggedIn: () -> Unit,
    onActivateAgain: () -> Unit,
    viewModel: PinViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.loggedIn) { if (state.loggedIn) onLoggedIn() }
    LaunchedEffect(state.forgotten) { if (state.forgotten) onActivateAgain() }

    PinContent(
        state = state,
        onPinChange = viewModel::onPinChange,
        onSubmit = viewModel::submit,
        onForgetActivation = viewModel::forgetActivation,
    )
}

/**
 * Содержимое экрана без зависимости от ViewModel: проверяется Compose-тестами
 * без поднятия графа зависимостей и работает в превью.
 */
@Composable
fun PinContent(
    state: PinUiState,
    onPinChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onForgetActivation: () -> Unit,
) {
    var confirmReset by remember { mutableStateOf(false) }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.pin_forgot)) },
            text = { Text(stringResource(R.string.pin_forgot_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onForgetActivation()
                }) { Text(stringResource(R.string.common_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HomeIllustration()

            Text(
                text = stringResource(R.string.pin_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.pin_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            state.errorRes?.let { MessageBanner(text = stringResource(it)) }

            if (state.locked) {
                MessageBanner(
                    text = stringResource(R.string.pin_locked, state.lockSecondsLeft),
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PasswordField(
                value = state.pin,
                onValueChange = onPinChange,
                label = stringResource(R.string.pin_field),
                leadingIcon = IconKey,
                enabled = !state.locked,
                keyboardType = KeyboardType.NumberPassword,
            )

            Spacer(Modifier.height(4.dp))

            PrimaryButton(
                text = stringResource(R.string.pin_submit),
                onClick = onSubmit,
                enabled = state.canSubmit,
                loading = state.loading,
            )

            TextButton(onClick = { confirmReset = true }) {
                Text(stringResource(R.string.pin_forgot))
            }
        }
    }
}
