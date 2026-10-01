package ru.terentiy.mobile.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import ru.terentiy.mobile.R
import ru.terentiy.mobile.ui.common.IconClock
import ru.terentiy.mobile.ui.common.IconHome
import ru.terentiy.mobile.ui.common.IconPerson

private data class Tab(val route: String, val icon: ImageVector, val labelRes: Int)

private val Tabs = listOf(
    Tab(Routes.HOME, IconHome, R.string.tab_home),
    Tab(Routes.EVENTS, IconClock, R.string.tab_events),
    Tab(Routes.PROFILE, IconPerson, R.string.tab_profile),
)

/**
 * Каркас с нижним таб-баром.
 *
 * Три вкладки, а не четыре, как на части макетов: в сториборде состав панели
 * различается от кадра к кадру, а «События» и «История» там — одно и то же.
 * Оставлено ровно то, что наполняется данными из контракта.
 */
@Composable
fun TerentiyScaffold(
    currentRoute: String?,
    onSelectTab: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val showBar = currentRoute in Routes.TAB_ROUTES

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { onSelectTab(tab.route) },
                            icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) { content(padding) }
    }
}
