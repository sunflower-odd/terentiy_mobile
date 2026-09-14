package ru.terentiy.mobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.terentiy.mobile.domain.Agent
import ru.terentiy.mobile.domain.Tri
import ru.terentiy.mobile.domain.WatchState
import ru.terentiy.mobile.ui.home.HomeContent
import ru.terentiy.mobile.ui.home.HomeUiState
import ru.terentiy.mobile.ui.theme.TerentiyTheme

/**
 * Главный экран во всех состояниях наблюдения.
 *
 * Экран проверяется как stateless-composable, поэтому граф зависимостей и сеть
 * в тестах не участвуют.
 */
@RunWith(AndroidJUnit4::class)
class HomeContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun agent(
        camera: Tri = Tri.YES,
        stream: Tri = Tri.YES,
        ml: Tri = Tri.YES,
        online: Boolean = true,
    ) = Agent(
        id = "agent-1",
        name = "Ноутбук",
        cameraId = "camera-room-1",
        softwareVersion = "0.1.0",
        modelVersion = "yolo26m.pt",
        online = online,
        agentOnline = Tri.of(online),
        cameraConnected = camera,
        streamActive = stream,
        mlReady = ml,
        queueSize = 0,
        reportedAt = Instant.parse("2026-09-13T07:00:00Z"),
        lastFrameAt = Instant.parse("2026-09-13T07:00:00Z"),
    )

    private fun show(state: HomeUiState, onRequestImage: (String) -> Unit = {}) {
        compose.setContent {
            TerentiyTheme(dynamicColor = false) {
                HomeContent(state = state, onRefresh = {}, onRequestImage = onRequestImage)
            }
        }
    }

    @Test
    fun приветствие_по_имени() {
        show(HomeUiState(userName = "Ольга", firstLoad = false, watchState = WatchState.WATCHING))
        compose.onNodeWithText("Здравствуйте, Ольга").assertIsDisplayed()
    }

    @Test
    fun приветствие_без_имени_не_показывает_пустоту() {
        show(HomeUiState(userName = "", firstLoad = false))
        compose.onNodeWithText("Здравствуйте").assertIsDisplayed()
    }

    @Test
    fun наблюдение_активно() {
        show(
            HomeUiState(
                firstLoad = false,
                agents = listOf(agent()),
                watchState = WatchState.WATCHING,
            )
        )
        compose.onNodeWithText("Наблюдение активно").assertIsDisplayed()
    }

    @Test
    fun камера_недоступна() {
        show(
            HomeUiState(
                firstLoad = false,
                agents = listOf(agent(camera = Tri.NO)),
                watchState = WatchState.CAMERA_UNAVAILABLE,
            )
        )
        compose.onNodeWithText("Камера недоступна").assertIsDisplayed()
    }

    @Test
    fun агент_недоступен() {
        show(
            HomeUiState(
                firstLoad = false,
                agents = listOf(agent(online = false)),
                watchState = WatchState.AGENT_OFFLINE,
            )
        )
        compose.onNodeWithText("Локальный агент недоступен").assertIsDisplayed()
    }

    @Test
    fun анализ_не_выполняется() {
        show(
            HomeUiState(
                firstLoad = false,
                agents = listOf(agent(ml = Tri.NO)),
                watchState = WatchState.ML_NOT_READY,
            )
        )
        compose.onNodeWithText("Анализ не выполняется").assertIsDisplayed()
    }

    @Test
    fun агент_не_подключён() {
        show(HomeUiState(firstLoad = false, agents = emptyList(), watchState = WatchState.NO_AGENT))
        compose.onNodeWithText("Агент не подключён").assertIsDisplayed()
    }

    @Test
    fun неизвестное_состояние_не_выдаётся_за_исправное() {
        show(
            HomeUiState(
                firstLoad = false,
                agents = listOf(agent(camera = Tri.UNKNOWN)),
                watchState = WatchState.CAMERA_UNAVAILABLE,
            )
        )
        compose.onNodeWithText("неизвестно").assertIsDisplayed()
    }

    @Test
    fun потеря_связи_показывает_баннер_поверх_прежних_данных() {
        show(
            HomeUiState(
                firstLoad = false,
                offline = true,
                agents = listOf(agent()),
                watchState = WatchState.WATCHING,
            )
        )
        compose.onNodeWithText("Нет связи с сервером. Показаны последние известные данные.")
            .assertIsDisplayed()
        compose.onNodeWithText("Ноутбук").assertIsDisplayed()
    }

    @Test
    fun кнопка_получить_вид_запрашивает_кадр_конкретного_агента() {
        var requested: String? = null
        show(
            state = HomeUiState(
                firstLoad = false,
                agents = listOf(agent()),
                watchState = WatchState.WATCHING,
            ),
            onRequestImage = { requested = it },
        )
        compose.onNodeWithText("Получить вид").performClick()
        assertEquals("agent-1", requested)
    }
}
