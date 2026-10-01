package ru.terentiy.mobile.ui

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.terentiy.mobile.R
import ru.terentiy.mobile.ui.activation.ActivationContent
import ru.terentiy.mobile.ui.activation.ActivationUiState
import ru.terentiy.mobile.ui.pin.PinContent
import ru.terentiy.mobile.ui.pin.PinUiState
import ru.terentiy.mobile.ui.theme.TerentiyTheme

/**
 * Экраны подключения и входа.
 *
 * Тексты берутся из ресурсов, а не переписываются в тест: иначе правка
 * формулировки в strings.xml валит тесты, ничего не сломав в приложении.
 */
@RunWith(AndroidJUnit4::class)
class AuthScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private val validForm = ActivationUiState(
        serverUrl = "http://10.0.2.2:8080",
        installationCode = "HOME-SETUP-CODE",
        userName = "Ольга",
        pin = "123456",
        pinRepeat = "123456",
    )

    private fun activation(
        state: ActivationUiState,
        onCheckServer: () -> Unit = {},
        onGoToPin: () -> Unit = {},
    ) {
        compose.setContent {
            TerentiyTheme(darkTheme = false) {
                ActivationContent(
                    state = state,
                    onServerUrlChange = {},
                    onCodeChange = {},
                    onNameChange = {},
                    onPinChange = {},
                    onPinRepeatChange = {},
                    onCheckServer = onCheckServer,
                    onDemoModeChange = {},
                    onSubmit = {},
                    onGoToPin = onGoToPin,
                )
            }
        }
    }

    private fun pin(state: PinUiState, onForget: () -> Unit = {}) {
        compose.setContent {
            TerentiyTheme(darkTheme = false) {
                PinContent(
                    state = state,
                    onPinChange = {},
                    onSubmit = {},
                    onForgetActivation = onForget,
                )
            }
        }
    }

    @Test
    fun подключение_недоступно_пока_форма_не_заполнена() {
        activation(ActivationUiState(serverUrl = "http://10.0.2.2:8080"))
        compose.onNodeWithText(text(R.string.activation_submit)).assertIsNotEnabled()
    }

    @Test
    fun подключение_доступно_при_корректных_данных() {
        activation(validForm)
        compose.onNodeWithText(text(R.string.activation_submit)).assertIsEnabled()
    }

    @Test
    fun несовпадающий_PIN_блокирует_подключение() {
        activation(validForm.copy(pinRepeat = "123457"))
        compose.onNodeWithText(text(R.string.activation_submit)).assertIsNotEnabled()
    }

    @Test
    fun короткий_PIN_блокирует_подключение() {
        activation(validForm.copy(pin = "123", pinRepeat = "123"))
        compose.onNodeWithText(text(R.string.activation_submit)).assertIsNotEnabled()
    }

    @Test
    fun кнопка_проверки_адреса_доходит_до_обработчика() {
        var checked = false
        activation(validForm, onCheckServer = { checked = true })
        compose.onNodeWithText(text(R.string.activation_check_server)).performClick()
        assertTrue(checked)
    }

    @Test
    fun ответ_сервера_на_проверку_показывается() {
        activation(validForm.copy(noticeRes = R.string.activation_server_ok))
        compose.onNodeWithText(text(R.string.activation_server_ok)).assertIsDisplayed()
    }

    @Test
    fun ошибка_кода_установки_показывается_пользователю() {
        activation(validForm.copy(errorRes = R.string.error_invalid_installation_code))
        compose.onNodeWithText(text(R.string.error_invalid_installation_code)).assertIsDisplayed()
    }

    @Test
    fun вход_недоступен_при_неполном_PIN() {
        pin(PinUiState(pin = "123"))
        compose.onNodeWithText(text(R.string.pin_submit)).assertIsNotEnabled()
    }

    @Test
    fun вход_доступен_при_полном_PIN() {
        pin(PinUiState(pin = "123456"))
        compose.onNodeWithText(text(R.string.pin_submit)).assertIsEnabled()
    }

    @Test
    fun неверный_PIN_показывается_инлайн() {
        pin(PinUiState(errorRes = R.string.error_invalid_pin))
        compose.onNodeWithText(text(R.string.error_invalid_pin)).assertIsDisplayed()
    }

    @Test
    fun блокировка_после_неудачных_попыток_отключает_вход() {
        pin(PinUiState(pin = "123456", lockSecondsLeft = 15))
        compose.onNodeWithText(text(R.string.pin_submit)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.pin_locked, 15)).assertIsDisplayed()
    }

    @Test
    fun сброс_привязки_предупреждает_до_действия() {
        // Ротация device_id необратима и оставляет сироту на сервере:
        // предупреждение обязано появиться раньше, чем что-либо произойдёт.
        var forgotten = false
        pin(PinUiState(), onForget = { forgotten = true })

        compose.onNodeWithText(text(R.string.pin_forgot)).performClick()
        compose.onNodeWithText(text(R.string.pin_forgot_warning)).assertIsDisplayed()
        assertEquals(false, forgotten)

        compose.onNodeWithText(text(R.string.common_continue)).performClick()
        assertTrue(forgotten)
    }
}
