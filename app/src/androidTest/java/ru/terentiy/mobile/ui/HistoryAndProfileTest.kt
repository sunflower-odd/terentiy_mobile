package ru.terentiy.mobile.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.terentiy.mobile.R
import ru.terentiy.mobile.domain.EventStatus
import ru.terentiy.mobile.domain.FallEvent
import ru.terentiy.mobile.ui.events.EventRow
import ru.terentiy.mobile.ui.events.EventsContent
import ru.terentiy.mobile.ui.events.EventsUiState
import ru.terentiy.mobile.ui.profile.ProfileContent
import ru.terentiy.mobile.ui.profile.ProfileUiState
import ru.terentiy.mobile.ui.theme.TerentiyTheme

/** История событий и профиль. */
@RunWith(AndroidJUnit4::class)
class HistoryAndProfileTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun event(id: String) = FallEvent(
        id = id,
        agentId = "agent-1",
        frameId = "$id-frame",
        frameMediaId = "$id-media",
        clipMediaId = "$id-clip",
        capturedAt = Instant.parse("2026-09-30T11:58:00Z"),
        createdAt = Instant.parse("2026-09-30T11:58:18Z"),
        status = EventStatus.CONFIRMED,
        analysis = null,
    )

    private fun events(state: EventsUiState, onOpenEvent: (String) -> Unit = {}) {
        compose.setContent {
            TerentiyTheme(darkTheme = false) {
                EventsContent(
                    state = state,
                    contentPadding = PaddingValues(),
                    onRefresh = {},
                    onOpenEvent = onOpenEvent,
                )
            }
        }
    }

    private fun profile(
        state: ProfileUiState,
        onDemoModeChange: (Boolean) -> Unit = {},
        onClearCache: () -> Unit = {},
        onLogout: () -> Unit = {},
    ) {
        compose.setContent {
            TerentiyTheme(darkTheme = false) {
                ProfileContent(
                    state = state,
                    contentPadding = PaddingValues(),
                    onDemoModeChange = onDemoModeChange,
                    onClearCache = onClearCache,
                    onLogout = onLogout,
                )
            }
        }
    }

    @Test
    fun пустая_история_объясняет_почему_она_пуста() {
        events(EventsUiState(firstLoad = false))
        compose.onNodeWithText(text(R.string.events_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.events_empty_hint)).assertIsDisplayed()
    }

    @Test
    fun событие_из_списка_открывается() {
        var opened: String? = null
        events(
            EventsUiState(rows = listOf(EventRow(event("e-1"), seen = false)), firstLoad = false),
            onOpenEvent = { opened = it },
        )
        compose.onNodeWithText(text(R.string.event_fall)).performClick()
        assertEquals("e-1", opened)
    }

    @Test
    fun история_предупреждает_о_задержке_видео() {
        // От падения до появления события проходит 20–40 секунд. Сказать об этом
        // прямо дешевле, чем оставить пользователя думать, что всё сломалось.
        events(EventsUiState(rows = listOf(EventRow(event("e-1"), seen = true)), firstLoad = false))
        compose.onNodeWithText(text(R.string.events_delay_notice)).assertIsDisplayed()
    }

    @Test
    fun кнопки_догрузки_в_истории_нет() {
        // Пагинации в контракте нет: сервер отдаёт всю историю одним ответом.
        events(EventsUiState(rows = listOf(EventRow(event("e-1"), seen = true)), firstLoad = false))
        compose.onNodeWithText("Показать ещё").assertDoesNotExist()
    }

    @Test
    fun профиль_показывает_имя_адрес_и_режим() {
        profile(
            ProfileUiState(
                userName = "Ольга",
                householdId = "house-1",
                serverUrl = "http://10.0.2.2:8080",
                demoMode = true,
            )
        )
        compose.onNodeWithText("Ольга").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.profile_household, "house-1")).assertIsDisplayed()
        compose.onNodeWithText("http://10.0.2.2:8080").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.profile_demo_mode)).assertIsDisplayed()
    }

    @Test
    fun профиль_прямо_говорит_об_отсутствии_уведомлений() {
        profile(ProfileUiState(userName = "Ольга"))
        compose.onNodeWithText(text(R.string.profile_no_push)).assertIsDisplayed()
    }

    @Test
    fun размер_кеша_видно_и_его_можно_очистить() {
        var cleared = false
        profile(ProfileUiState(cacheMegabytes = 84), onClearCache = { cleared = true })

        compose.onNodeWithText(text(R.string.profile_media_cache_size, 84L)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.profile_media_cache_clear)).performClick()
        assertTrue(cleared)
    }

    @Test
    fun пустое_имя_не_оставляет_пустую_строку() {
        profile(ProfileUiState(serverUrl = "http://10.0.2.2:8080"))
        compose.onNodeWithText(text(R.string.profile_no_name)).assertIsDisplayed()
    }

    @Test
    fun включённый_демо_режим_показан_включённым() {
        profile(ProfileUiState(demoMode = true))
        compose.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun выключенный_демо_режим_показан_выключенным() {
        profile(ProfileUiState(demoMode = false))
        compose.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun выход_требует_подтверждения() {
        var loggedOut = false
        profile(ProfileUiState(userName = "Ольга"), onLogout = { loggedOut = true })

        compose.onNodeWithText(text(R.string.profile_logout)).performClick()
        compose.onNodeWithText(text(R.string.profile_logout_confirm)).assertIsDisplayed()
        assertEquals(false, loggedOut)

        compose.onNodeWithText(text(R.string.common_continue)).performClick()
        assertTrue(loggedOut)
    }
}
