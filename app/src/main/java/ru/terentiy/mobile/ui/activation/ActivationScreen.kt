package ru.terentiy.mobile.ui.activation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.terentiy.mobile.R
import ru.terentiy.mobile.ui.common.HomeIllustration
import ru.terentiy.mobile.ui.common.IconKey
import ru.terentiy.mobile.ui.common.IconPerson
import ru.terentiy.mobile.ui.common.IconWifi
import ru.terentiy.mobile.ui.common.MessageBanner
import ru.terentiy.mobile.ui.common.PasswordField
import ru.terentiy.mobile.ui.common.PrimaryButton
import ru.terentiy.mobile.ui.common.TerentiyField

@Composable
fun ActivationScreen(
    onActivated: () -> Unit,
    onGoToPin: () -> Unit,
    viewModel: ActivationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.activated) { if (state.activated) onActivated() }

    ActivationContent(
        state = state,
        onServerUrlChange = viewModel::onServerUrlChange,
        onCodeChange = viewModel::onCodeChange,
        onNameChange = viewModel::onNameChange,
        onPinChange = viewModel::onPinChange,
        onPinRepeatChange = viewModel::onPinRepeatChange,
        onCheckServer = viewModel::checkServer,
        onDemoModeChange = viewModel::onDemoModeChange,
        onSubmit = viewModel::submit,
        onGoToPin = onGoToPin,
    )
}

@Composable
fun ActivationContent(
    state: ActivationUiState,
    onServerUrlChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onPinRepeatChange: (String) -> Unit,
    onCheckServer: () -> Unit,
    onDemoModeChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onGoToPin: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HomeIllustration()

            Text(
                text = stringResource(R.string.activation_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.activation_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            state.errorRes?.let { MessageBanner(text = stringResource(it)) }
            state.noticeRes?.let {
                MessageBanner(
                    text = stringResource(it),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            TerentiyField(
                value = state.serverUrl,
                onValueChange = onServerUrlChange,
                label = stringResource(R.string.activation_server_url),
                leadingIcon = IconWifi,
                supportingText = stringResource(R.string.activation_server_url_hint),
                keyboardType = KeyboardType.Uri,
                enabled = !state.loading,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.checkingServer) {
                    CircularProgressIndicator(modifier = Modifier.height(16.dp).padding(end = 8.dp))
                }
                TextButton(onClick = onCheckServer, enabled = !state.checkingServer && !state.loading) {
                    Text(stringResource(R.string.activation_check_server))
                }
            }

            TerentiyField(
                value = state.installationCode,
                onValueChange = onCodeChange,
                label = stringResource(R.string.activation_code),
                leadingIcon = IconKey,
                enabled = !state.loading,
            )

            TerentiyField(
                value = state.userName,
                onValueChange = onNameChange,
                label = stringResource(R.string.activation_name),
                leadingIcon = IconPerson,
                supportingText = stringResource(R.string.activation_name_hint),
                enabled = !state.loading,
            )

            PasswordField(
                value = state.pin,
                onValueChange = onPinChange,
                label = stringResource(R.string.activation_pin),
                leadingIcon = IconKey,
                supportingText = stringResource(R.string.activation_pin_hint),
                keyboardType = KeyboardType.NumberPassword,
                enabled = !state.loading,
            )

            PasswordField(
                value = state.pinRepeat,
                onValueChange = onPinRepeatChange,
                label = stringResource(R.string.activation_pin_repeat),
                leadingIcon = IconKey,
                keyboardType = KeyboardType.NumberPassword,
                enabled = !state.loading,
            )

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.activation_demo_mode),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.activation_demo_mode_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.demoMode,
                        onCheckedChange = onDemoModeChange,
                        enabled = !state.loading,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            PrimaryButton(
                text = stringResource(R.string.activation_submit),
                onClick = onSubmit,
                enabled = state.canSubmit,
                loading = state.loading,
            )

            TextButton(onClick = onGoToPin) {
                Text(stringResource(R.string.activation_have_account))
            }
        }
    }
}
