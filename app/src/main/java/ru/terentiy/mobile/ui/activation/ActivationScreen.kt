package ru.terentiy.mobile.ui.activation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

/**
 * Первое подключение приложения к установке.
 *
 * Адрес сервера редактируется здесь, а не зашит в сборку: backend Stage 01 живёт
 * на ноутбуке, и его адрес меняется от сети к сети.
 */
@Composable
fun ActivationScreen(
    onActivated: () -> Unit,
    onGoToPin: () -> Unit,
    viewModel: ActivationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.activated) {
        if (state.activated) onActivated()
    }

    ActivationContent(
        state = state,
        onServerUrlChange = viewModel::onServerUrlChange,
        onCodeChange = viewModel::onCodeChange,
        onNameChange = viewModel::onNameChange,
        onPinChange = viewModel::onPinChange,
        onPinRepeatChange = viewModel::onPinRepeatChange,
        onSubmit = viewModel::submit,
        onGoToPin = onGoToPin,
    )
}

/**
 * Содержимое экрана без зависимости от ViewModel.
 *
 * Разделение на обёртку и содержимое даёт две вещи: экран проверяется Compose-тестами
 * без поднятия графа зависимостей, и работают превью в Android Studio.
 */
@Composable
fun ActivationContent(
    state: ActivationUiState,
    onServerUrlChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onPinRepeatChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoToPin: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.activation_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.activation_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.errorRes?.let { MessageBanner(text = stringResource(it)) }

            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = onServerUrlChange,
                label = { Text(stringResource(R.string.activation_server_url)) },
                supportingText = { Text(stringResource(R.string.activation_server_url_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.installationCode,
                onValueChange = onCodeChange,
                label = { Text(stringResource(R.string.activation_code)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.userName,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.activation_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.pin,
                onValueChange = onPinChange,
                label = { Text(stringResource(R.string.activation_pin)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.pinRepeat,
                onValueChange = onPinRepeatChange,
                label = { Text(stringResource(R.string.activation_pin_repeat)) },
                singleLine = true,
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
                ButtonContent(
                    loading = state.loading,
                    label = stringResource(R.string.activation_submit),
                )
            }

            TextButton(onClick = onGoToPin, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.activation_have_account))
            }
        }
    }
}
