package ru.terentiy.mobile.ui

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
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
import ru.terentiy.mobile.domain.FallAnalysis
import ru.terentiy.mobile.domain.FallEvent
import ru.terentiy.mobile.ui.clip.ClipContent
import ru.terentiy.mobile.ui.clip.ClipUiState
import ru.terentiy.mobile.ui.eventdetail.EventDetailContent
import ru.terentiy.mobile.ui.eventdetail.EventDetailUiState
import ru.terentiy.mobile.ui.theme.TerentiyTheme

/**
 * Карточка события и экран записи.
 *
 * Главное здесь не вид, а формулировки: заголовок утвердительный, как в модели
 * сервера, но рядом всегда стоит основание — решение приняли правила по позе
 * человека, а не человек.
 */
@RunWith(AndroidJUnit4::class)
class EventDetailContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private val analysis = FallAnalysis(
        detected = true,
        trackId = 3,
        candidateRules = listOf("aspect_ratio=1.400"),
        confirmationRules = listOf("angular_speed=118deg/s"),
        candidateVotes = 1,
        candidateVotesRequired = 1,
        confirmationVotes = 2,
        confirmationVotesRequired = 1,
        processingTimeMs = 84.219,
    )

    private fun event(analysis: FallAnalysis? = null) = FallEvent(
        id = "event-1",
        agentId = "agent-1",
        frameId = "frame-1",
        frameMediaId = "media-frame-1",
        clipMediaId = "media-clip-1",
        capturedAt = Instant.parse("2026-09-30T11:58:00Z"),
        createdAt = Instant.parse("2026-09-30T11:58:18Z"),
        status = EventStatus.CONFIRMED,
        analysis = analysis,
    )

    private fun detail(
        state: EventDetailUiState,
        onBack: () -> Unit = {},
        onPlayClip: (String) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        compose.setContent {
            TerentiyTheme(darkTheme = false) {
                EventDetailContent(
                    state = state,
                    onBack = onBack,
                    onPlayClip = onPlayClip,
                    onRetry = onRetry,
                    onRetryFrame = {},
                )
            }
        }
    }

    @Test
    fun заголовок_утвердительный_но_с_основанием() {
        detail(EventDetailUiState(loading = false, event = event()))
        compose.onNodeWithText(text(R.string.event_fall)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.event_basis)).assertIsDisplayed()
    }

    @Test
    fun кнопка_видео_есть_всегда() {
        // Сервер отдаёт только события с загруженным клипом, состояния
        // «готовится» не существует.
        var played: String? = null
        detail(EventDetailUiState(loading = false, event = event()), onPlayClip = { played = it })

        compose.onNodeWithText(text(R.string.event_play_clip)).performClick()
        assertEquals("media-clip-1", played)
    }

    @Test
    fun подробности_свёрнуты_по_умолчанию_и_раскрываются() {
        detail(EventDetailUiState(loading = false, event = event(analysis)))

        compose.onNodeWithText(text(R.string.event_details)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.event_details_confirmation, 2, 1)).assertDoesNotExist()

        compose.onNodeWithText(text(R.string.common_expand)).performClick()
        compose.onNodeWithText(text(R.string.event_details_confirmation, 2, 1)).assertIsDisplayed()
        compose.onNodeWithText("aspect_ratio=1.400, angular_speed=118deg/s").assertIsDisplayed()
    }

    @Test
    fun без_разбора_блока_подробностей_нет() {
        detail(EventDetailUiState(loading = false, event = event(analysis = null)))
        compose.onNodeWithText(text(R.string.event_details)).assertDoesNotExist()
    }

    @Test
    fun недоступный_кадр_объясняется_и_даёт_повторить() {
        detail(
            EventDetailUiState(
                loading = false,
                event = event(),
                frameErrorRes = R.string.error_media_not_found,
            )
        )
        compose.onNodeWithText(text(R.string.error_media_not_found)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_retry)).assertIsDisplayed()
    }

    @Test
    fun событие_не_найдено_даёт_повторить() {
        var retried = false
        detail(
            EventDetailUiState(loading = false, errorRes = R.string.error_event_not_found),
            onRetry = { retried = true },
        )
        compose.onNodeWithText(text(R.string.error_event_not_found)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_retry)).performClick()
        assertTrue(retried)
    }

    @Test
    fun загрузка_записи_показывает_прогресс() {
        compose.setContent {
            TerentiyTheme(darkTheme = false) {
                ClipContent(
                    state = ClipUiState(downloading = true, downloadedBytes = 3L * 1024 * 1024, totalBytes = 12L * 1024 * 1024),
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        compose.onNodeWithText(text(R.string.clip_downloading, 3L)).assertIsDisplayed()
    }

    @Test
    fun ошибка_записи_даёт_повторить() {
        var retried = false
        compose.setContent {
            TerentiyTheme(darkTheme = false) {
                ClipContent(
                    state = ClipUiState(downloading = false, errorRes = R.string.error_media_not_found),
                    onBack = {},
                    onRetry = { retried = true },
                )
            }
        }
        compose.onNodeWithText(text(R.string.error_media_not_found)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_retry)).performClick()
        assertTrue(retried)
    }
}
