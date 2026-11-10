package ru.terentiy.mobile.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
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

    // Экран без прокрутки: всё на одной странице маленького телефона. Настройки
    // собраны в одну карточку строками, выход — внизу.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconBadge(
                    icon = IconPerson,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 44.dp,
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
                    Text(
                        text = stringResource(R.string.profile_server_value, state.serverUrl),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column {
                SettingRow(
                    title = stringResource(R.string.profile_zones),
                    hint = stringResource(R.string.profile_zones_hint),
                    modifier = Modifier.clickable(onClick = onOpenZones),
                ) {
                    Icon(
                        imageVector = IconChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.background)
                SettingRow(
                    title = stringResource(R.string.profile_demo_mode),
                    hint = stringResource(R.string.profile_demo_mode_hint),
                ) {
                    Switch(checked = state.demoMode, onCheckedChange = onDemoModeChange)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.background)
                SettingRow(
                    title = stringResource(R.string.profile_media_cache),
                    hint = stringResource(R.string.profile_media_cache_size, state.cacheMegabytes),
                ) {
                    TextButton(onClick = onClearCache, enabled = state.cacheMegabytes > 0) {
                        Text(stringResource(R.string.profile_media_cache_clear))
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Ограничение, о котором пользователь должен знать прямо, а не узнавать
        // из опыта: push-канала в контракте нет.
        Text(
            text = stringResource(R.string.profile_no_push),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SecondaryButton(
            text = stringResource(R.string.profile_logout),
            onClick = { confirmLogout = true },
            loading = state.loggingOut,
        )
    }
}

/** Строка настроек: название, короткое пояснение в одну строку и элемент справа. */
@Composable
private fun SettingRow(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

