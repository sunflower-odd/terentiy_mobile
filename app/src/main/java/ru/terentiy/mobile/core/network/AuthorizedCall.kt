package ru.terentiy.mobile.core.network

import javax.inject.Inject
import javax.inject.Singleton
import ru.terentiy.mobile.core.error.AppError
import ru.terentiy.mobile.core.error.ErrorMapper
import ru.terentiy.mobile.data.local.SessionHolder
import timber.log.Timber

/**
 * Вызов защищённого маршрута.
 *
 * Единственное место, где приложение реагирует на `401`: локальная сессия
 * стирается, ошибка пробрасывается дальше. Стирание делает состояние сессии
 * наблюдаемым, и навигация уводит на экран PIN сама.
 *
 * ## Почему здесь нет автоматического повторного входа
 *
 * Соблазнительно завести `Authenticator`, который при `401` сам выполнит
 * `mobile/login` и повторит запрос. Для этого пришлось бы хранить PIN на
 * устройстве — то есть превратить «украли токен» в «украли PIN» и обнулить смысл
 * того, что PIN вообще спрашивают. Токен бессрочный, прозрачное переполучение не
 * даёт ничего, кроме этого риска.
 *
 * Прежний `TokenAuthenticator` с мьютексом решал проблему ротации refresh-токена.
 * В реализованном контракте ни refresh, ни ротации нет — вместе с ними ушла и
 * проблема. См. docs/adr/0005-serializatsiya-obnovleniya-sessii.md.
 */
@Singleton
class AuthorizedCall @Inject constructor(
    private val errorMapper: ErrorMapper,
    private val sessions: SessionHolder,
) {

    suspend operator fun <T> invoke(block: suspend () -> T): T =
        try {
            block()
        } catch (cancellation: kotlin.coroutines.cancellation.CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            val error = errorMapper.map(throwable)
            if (error is AppError.Unauthorized) {
                Timber.i("Сервер не принял токен, требуется повторный вход")
                sessions.clear()
            }
            throw error
        }
}
