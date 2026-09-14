package ru.terentiy.mobile.ui.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.terentiy.mobile.R
import ru.terentiy.mobile.ui.common.ButtonContent
import ru.terentiy.mobile.ui.common.MessageBanner

@Composable
fun PinScreen(
    onLoggedIn: () -> Unit,
    onReactivate: () -> Unit,
    viewModel: PinViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.loggedIn) { if (state.loggedIn) onLoggedIn() }
    LaunchedEffect(state.forgotten) { if (state.forgotten) onReactivate() }

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
    var confirmReactivate by remember { mutableStateOf(false) }

    if (confirmReactivate) {
        AlertDialog(
            onDismissRequest = { confirmReactivate = false },
            title = { Text(stringResource(R.string.pin_reactivate)) },
            text = { Text(stringResource(R.string.pin_reactivate_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReactivate = false
                    onForgetActivation()
                }) { Text(stringResource(R.string.common_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReactivate = false }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = stringResource(R.string.pin_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.pin_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.errorRes?.let { MessageBanner(text = stringResource(it)) }

            if (state.locked) {
                MessageBanner(
                    text = stringResource(R.string.pin_locked, state.lockSecondsLeft),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            OutlinedTextField(
                value = state.pin,
                onValueChange = onPinChange,
                label = { Text(stringResource(R.string.pin_field)) },
                singleLine = true,
                enabled = !state.locked,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = onSubmit,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ButtonContent(loading = state.loading, label = stringResource(R.string.pin_submit))
            }

            TextButton(
                onClick = { confirmReactivate = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.pin_reactivate))
            }
        }
    }
}
