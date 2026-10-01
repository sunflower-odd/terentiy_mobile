package ru.terentiy.mobile.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.terentiy.mobile.R
import ru.terentiy.mobile.domain.Agent
import ru.terentiy.mobile.domain.EventStatus
import ru.terentiy.mobile.domain.FallEvent
import ru.terentiy.mobile.domain.Tri
import ru.terentiy.mobile.domain.WatchState
import ru.terentiy.mobile.ui.home.HomeContent
import ru.terentiy.mobile.ui.home.HomeUiState
import ru.terentiy.mobile.ui.theme.TerentiyTheme

/**
 * Главный экран в основных состояниях.
 *
 * Отдельно проверяется, что кнопки управления наблюдением **нет**: в
 * реализованном контракте команд не существует, и кнопка, которой нечего вызвать,
 * хуже её отсутствия.
 */
@RunWith(AndroidJUnit4::class)
class HomeContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private val now: Instant = Instant.parse("2026-09-30T12:00:00Z")

    private fun agent(
        online: Boolean = true,
        camera: Tri = Tri.YES,
        stream: Tri = Tri.YES,
        queueSize: Int? = 0,
    ) = Agent(
        id = "agent-1",
        householdId = "house-1",
        name = "Гостиная",
        cameraId = "camera-1",
        softwareVersion = "1.0.0",
        createdAt = now,
        online = online,
        agentOnline = Tri.of(online),
        cameraConnected = camera,
        streamActive = stream,
        queueSize = queueSize,
        reportedAt = now,
        lastFrameAt = now.minusSeconds(2),
    )

    private fun event(id: String = "event-1") = FallEvent(
        id = id,
        agentId = "agent-1",
        frameId = "frame-1",
        frameMediaId = "media-frame",
        clipMediaId = "media-clip",
        capturedAt = now.minusSeconds(120),
        createdAt = now.minusSeconds(100),
        status = EventStatus.CONFIRMED,
        analysis = null,
    )

    private fun show(
        state: HomeUiState,
        onOpenEvent: (String) -> Unit = {},
        onOpenHistory: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        compose.setContent {
            TerentiyTheme(darkTheme = false) {
                HomeContent(
                    state = state,
                    contentPadding = PaddingValues(),
                    onRefresh = {},
                    onOpenEvent = onOpenEvent,
                    onOpenHistory = onOpenHistory,
                    onDismissAlert = onDismiss,
                )
            }
        }
    }

    @Test
    fun исправный_контур_показывает_что_наблюдение_работает() {
        show(
            HomeUiState(
                userName = "Ольга",
                agents = listOf(agent()),
                watchState = WatchState.WATCHING,
                firstLoad = false,
            )
        )
        compose.onNodeWithText(text(R.string.home_greeting, "Ольга")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.watch_watching)).assertIsDisplayed()
        compose.onNodeWithText("Гостиная").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_camera_connected)).assertIsDisplayed()
    }

    @Test
    fun кнопки_управления_наблюдением_на_экране_нет() {
        show(
            HomeUiState(
                agents = listOf(agent()),
                watchState = WatchState.WATCHING,
                firstLoad = false,
            )
        )
        // Строки из прошлой версии контракта: их не должно быть даже в ресурсах,
        // но проверка дешёвая и ловит случайный возврат.
        compose.onNodeWithText("Включить наблюдение").assertDoesNotExist()
        compose.onNodeWithText("Выключить наблюдение").assertDoesNotExist()
    }

    @Test
    fun молчащий_агент_показывает_неизвестное_состояние() {
        show(
            HomeUiState(
                agents = listOf(agent(online = false, camera = Tri.UNKNOWN, stream = Tri.UNKNOWN, queueSize = null)),
                watchState = WatchState.UNKNOWN,
                firstLoad = false,
            )
        )
        compose.onNodeWithText(text(R.string.watch_unknown)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_camera_unknown)).assertIsDisplayed()
    }

    @Test
    fun недоступная_камера_видна_отдельной_строкой() {
        show(
            HomeUiState(
                agents = listOf(agent(camera = Tri.NO)),
                watchState = WatchState.CAMERA_UNAVAILABLE,
                firstLoad = false,
            )
        )
        compose.onNodeWithText(text(R.string.watch_camera_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_camera_unavailable)).assertIsDisplayed()
    }

    @Test
    fun очередь_кадров_показывается_только_когда_она_не_пуста() {
        show(
            HomeUiState(
                agents = listOf(agent(queueSize = 24)),
                watchState = WatchState.FRAMES_DELAYED,
                firstLoad = false,
            )
        )
        compose.onNodeWithText(text(R.string.home_queue, 24)).assertIsDisplayed()
    }

    @Test
    fun пустая_очередь_строки_не_добавляет() {
        show(
            HomeUiState(
                agents = listOf(agent(queueSize = 0)),
                watchState = WatchState.WATCHING,
                firstLoad = false,
            )
        )
        compose.onNodeWithText(text(R.string.home_queue, 0)).assertDoesNotExist()
    }

    @Test
    fun без_агента_показывается_объяснение() {
        show(HomeUiState(watchState = WatchState.NO_AGENT, firstLoad = false))
        compose.onNodeWithText(text(R.string.home_no_agent)).assertIsDisplayed()
    }

    @Test
    fun потеря_связи_оставляет_последнее_состояние_на_экране() {
        show(
            HomeUiState(
                agents = listOf(agent()),
                watchState = WatchState.WATCHING,
                firstLoad = false,
                offline = true,
            )
        )
        compose.onNodeWithText(text(R.string.home_offline_banner)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.watch_watching)).assertIsDisplayed()
    }

    @Test
    fun тревога_открывает_событие() {
        var opened: String? = null
        show(
            HomeUiState(
                agents = listOf(agent()),
                watchState = WatchState.WATCHING,
                alert = event(),
                unseenCount = 1,
                firstLoad = false,
            ),
            onOpenEvent = { opened = it },
        )

        compose.onNodeWithText(text(R.string.event_fall)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.event_basis)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.alert_open_event)).performClick()

        assertEquals("event-1", opened)
    }

    @Test
    fun счётчик_новых_ведёт_в_историю() {
        var opened = false
        show(
            HomeUiState(
                agents = listOf(agent()),
                watchState = WatchState.WATCHING,
                unseenCount = 3,
                firstLoad = false,
            ),
            onOpenHistory = { opened = true },
        )

        compose.onNodeWithText(text(R.string.home_history_with_unseen, 3)).performClick()
        assertEquals(true, opened)
    }
}
