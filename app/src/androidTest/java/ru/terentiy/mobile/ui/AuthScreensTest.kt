package ru.terentiy.mobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.terentiy.mobile.R
import ru.terentiy.mobile.ui.activation.ActivationContent
import ru.terentiy.mobile.ui.activation.ActivationUiState
import ru.terentiy.mobile.ui.pin.PinContent
import ru.terentiy.mobile.ui.pin.PinUiState
import ru.terentiy.mobile.ui.theme.TerentiyTheme

@RunWith(AndroidJUnit4::class)
class AuthScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private fun activation(state: ActivationUiState) {
        compose.setContent {
            TerentiyTheme(dynamicColor = false) {
                ActivationContent(
                    state = state,
                    onServerUrlChange = {},
                    onCodeChange = {},
                    onNameChange = {},
                    onPinChange = {},
                    onPinRepeatChange = {},
                    onSubmit = {},
                    onGoToPin = {},
                )
            }
        }
    }

    private fun pin(state: PinUiState) {
        compose.setContent {
            TerentiyTheme(dynamicColor = false) {
                PinContent(
                    state = state,
                    onPinChange = {},
                    onSubmit = {},
                    onForgetActivation = {},
                )
            }
        }
    }

    @Test
    fun активация_недоступна_пока_форма_не_заполнена() {
        activation(ActivationUiState(serverUrl = "http://10.0.2.2:8080"))
        compose.onNodeWithText("Активировать").assertIsNotEnabled()
    }

    @Test
    fun активация_доступна_при_корректных_данных() {
        activation(
            ActivationUiState(
                serverUrl = "http://10.0.2.2:8080",
                installationCode = "TEST-INSTALLATION-CODE-001",
                userName = "Ольга",
                pin = "123456",
                pinRepeat = "123456",
            )
        )
        compose.onNodeWithText("Активировать").assertIsEnabled()
    }

    @Test
    fun несовпадающий_PIN_блокирует_активацию() {
        activation(
            ActivationUiState(
                serverUrl = "http://10.0.2.2:8080",
                installationCode = "TEST-INSTALLATION-CODE-001",
                userName = "Ольга",
                pin = "123456",
                pinRepeat = "123457",
            )
        )
        compose.onNodeWithText("Активировать").assertIsNotEnabled()
    }

    @Test
    fun ошибка_кода_установки_показывается_пользователю() {
        activation(
            ActivationUiState(
                serverUrl = "http://10.0.2.2:8080",
                errorRes = R.string.error_invalid_installation_code,
            )
        )
        compose.onNodeWithText("Неверный код установки. Проверьте код и адрес сервера.")
            .assertIsDisplayed()
    }

    @Test
    fun вход_недоступен_при_неполном_PIN() {
        pin(PinUiState(pin = "123"))
        compose.onNodeWithText("Войти").assertIsNotEnabled()
    }

    @Test
    fun неверный_PIN_показывается_инлайн() {
        pin(PinUiState(errorRes = R.string.error_invalid_pin))
        compose.onNodeWithText("Неверный PIN.").assertIsDisplayed()
    }

    @Test
    fun блокировка_после_неудачных_попыток_отключает_ввод() {
        pin(PinUiState(pin = "123456", lockSecondsLeft = 15))
        compose.onNodeWithText("Войти").assertIsNotEnabled()
        compose.onNodeWithText("Слишком много попыток. Повторите через 15 с.").assertIsDisplayed()
    }
}
