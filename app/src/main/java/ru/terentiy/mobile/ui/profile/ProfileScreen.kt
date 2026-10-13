package ru.terentiy.mobile.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.terentiy.mobile.R
import ru.terentiy.mobile.ui.common.IconBadge
import ru.terentiy.mobile.ui.common.IconChevronRight
import ru.terentiy.mobile.ui.common.IconPerson
import ru.terentiy.mobile.ui.common.SecondaryButton

@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    onLoggedOut: () -> Unit,
    onOpenZones: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.loggedOut) { if (state.loggedOut) onLoggedOut() }

    ProfileContent(
        state = state,
        contentPadding = contentPadding,
        onDemoModeChange = viewModel::setDemoMode,
        onClearCache = viewModel::clearMediaCache,
        onLogout = viewModel::logout,
        onOpenZones = onOpenZones,
    )
}

@Composable
fun ProfileContent(
    state: ProfileUiState,
    contentPadding: PaddingValues,
    onDemoModeChange: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onLogout: () -> Unit,
    onOpenZones: () -> Unit = {},
) {
    var confirmLogout by remember { mutableStateOf(false) }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.profile_logout)) },
            text = { Text(stringResource(R.string.profile_logout_confirm)) },
            confirmButton = {
                // Подтверждение подписано нейтрально, а не «Выйти из аккаунта»:
                // иначе на экране три одинаковые надписи, и непонятно, какая
                // из них действие.
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) { Text(stringResource(R.string.common_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconBadge(
                    icon = IconPerson,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 52.dp,
                )
                Column {
                    Text(
                        text = state.userName.ifBlank { stringResource(R.string.profile_no_name) },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (state.householdId.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.profile_household, state.householdId),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        InfoCard(
            title = stringResource(R.string.profile_server),
            value = state.serverUrl,
        )

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenZones),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.profile_zones),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.zones_entry_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = IconChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.profile_demo_mode),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.profile_demo_mode_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.demoMode, onCheckedChange = onDemoModeChange)
            }
        }

        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.profile_media_cache),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.profile_media_cache_size, state.cacheMegabytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClearCache, enabled = state.cacheMegabytes > 0) {
                    Text(stringResource(R.string.profile_media_cache_clear))
                }
            }
        }

        // Ограничение, о котором пользователь должен знать прямо, а не узнавать
        // из опыта: push-канала в контракте нет.
        Text(
            text = stringResource(R.string.profile_no_push),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        SecondaryButton(
            text = stringResource(R.string.profile_logout),
            onClick = { confirmLogout = true },
            loading = state.loggingOut,
        )
    }
}

@Composable
private fun InfoCard(title: String, value: String) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
