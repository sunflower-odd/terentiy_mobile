package ru.terentiy.mobile.ui

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.terentiy.mobile.R
import ru.terentiy.mobile.domain.Zone
import ru.terentiy.mobile.domain.ZoneBindingFailure
import ru.terentiy.mobile.domain.ZoneBindingState
import ru.terentiy.mobile.domain.ZoneObservation
import ru.terentiy.mobile.domain.ZonePoint
import ru.terentiy.mobile.domain.ZoneState
import ru.terentiy.mobile.ui.theme.TerentiyTheme
import ru.terentiy.mobile.ui.zones.ZoneCheck
import ru.terentiy.mobile.ui.zones.ZonesActions
import ru.terentiy.mobile.ui.zones.ZonesContent
import ru.terentiy.mobile.ui.zones.ZonesUiState

/**
 * Проверка зоны в основных состояниях. Сервер здесь не нужен: состояния
 * подаются готовыми, как их выдаёт ViewModel по ответам сервера.
 */
@RunWith(AndroidJUnit4::class)
class ZoneCheckContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private val square = listOf(ZonePoint(0.2f, 0.5f), ZonePoint(0.6f, 0.5f), ZonePoint(0.6f, 0.9f), ZonePoint(0.2f, 0.9f))

    private fun zone(id: String, state: ZoneState) = Zone(
        id = id, agentId = "agent-1", cameraId = "camera-1", name = "Дверь $id", polygon = square,
        referenceFrameId = "frame-1", referenceMediaId = "media-1", state = state, version = 1,
    )

    private fun check(
        state: ZoneBindingState?,
        observation: ZoneObservation? = null,
        failure: ZoneBindingFailure? = null,
    ) = ZoneCheck(zoneId = "z1", zoneName = "Дверь z1", polygon = square, sessionId = "s1", state = state, observation = observation, failure = failure)

    private fun show(state: ZonesUiState, actions: ZonesActions = ZonesActions()) {
        compose.setContent {
            TerentiyTheme(darkTheme = false) {
                ZonesContent(state = state, onBack = {}, actions = actions)
            }
        }
    }

    @Test
    fun проверить_можно_только_непроверенную_зону() {
        var started: String? = null
        show(
            ZonesUiState(loading = false, zones = listOf(zone("z1", ZoneState.DRAFT), zone("z2", ZoneState.BOUND))),
            ZonesActions(startCheck = { started = it }),
        )

        val buttons = compose.onAllNodesWithText(text(R.string.zone_check))
        assertEquals(1, buttons.fetchSemanticsNodes().size)
        buttons[0].performScrollTo().performClick()
        assertEquals("z1", started)
    }

    @Test
    fun во_время_проверки_виден_статус_а_вне_зоны_просьба_шагнуть_в_неё() {
        var cancelled = false
        show(
            ZonesUiState(
                loading = false,
                check = check(ZoneBindingState.COLLECTING, ZoneObservation("m1", ZonePoint(0.8f, 0.3f), inZone = false)),
            ),
            ZonesActions(cancelCheck = { cancelled = true }),
        )

        compose.onNodeWithText(text(R.string.zone_check_title, "Дверь z1")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.zone_check_collecting)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.zone_check_step_inside)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.zone_check_cancel)).performScrollTo().performClick()
        assertEquals(true, cancelled)
    }

    @Test
    fun проверенная_зона() {
        var closed = false
        show(ZonesUiState(loading = false, check = check(ZoneBindingState.BOUND)), ZonesActions(closeCheck = { closed = true }))

        compose.onNodeWithText(text(R.string.zone_check_bound)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.zone_done)).performScrollTo().performClick()
        assertEquals(true, closed)
    }

    @Test
    fun неудача_показывает_причину_без_технических_подробностей_и_даёт_повторить() {
        var retried = false
        show(
            ZonesUiState(loading = false, check = check(ZoneBindingState.EXPIRED, failure = ZoneBindingFailure.NOT_INSIDE)),
            ZonesActions(retryCheck = { retried = true }),
        )

        compose.onNodeWithText(text(R.string.zone_check_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.zone_check_failure_not_inside)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.zone_check_retry)).performScrollTo().performClick()
        assertEquals(true, retried)
    }

    @Test
    fun в_показе_без_сервера_проверка_объясняет_что_нужен_сервер() {
        show(ZonesUiState(loading = false, check = check(state = null).copy(sessionId = null, errorRes = R.string.error_zone_check_needs_server)))

        compose.onNodeWithText(text(R.string.zone_check_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.error_zone_check_needs_server)).assertIsDisplayed()
    }
}
