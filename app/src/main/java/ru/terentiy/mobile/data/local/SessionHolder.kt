package ru.terentiy.mobile.data.local

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.terentiy.mobile.domain.Session

/**
 * Текущая сессия как наблюдаемое значение.
 *
 * ## Зачем он нужен
 *
 * Токен живёт в зашифрованном хранилище, которое читается синхронно и потоком не
 * является. Пока обновление сессии было автоматическим, это скрывалось: экраны
 * сами выставляли флаг «сессия истекла». Теперь `401` — единственный путь к
 * повторному входу, и состояние обязано быть наблюдаемым: стёрли токен —
 * навигация должна увести на экран PIN сама, без участия экрана, на котором
 * запрос упал.
 *
 * ## Побочная выгода
 *
 * [AuthInterceptor][ru.terentiy.mobile.core.network.AuthInterceptor] читает
 * значение из памяти вместо расшифровки keystore на каждом запросе.
 */
@Singleton
class SessionHolder @Inject constructor(
    private val storage: SecureStorage,
) {

    private val _session = MutableStateFlow(storage.session())

    val session: StateFlow<Session?> = _session.asStateFlow()

    val current: Session? get() = _session.value

    fun save(value: Session) {
        storage.saveSession(value)
        _session.value = value
    }

    fun clear() {
        storage.clearSession()
        _session.value = null
    }
}
