package ru.terentiy.mobile.core.network

import kotlin.coroutines.cancellation.CancellationException
import ru.terentiy.mobile.core.error.ErrorMapper

/**
 * Выполняет вызов API и переводит любое исключение в доменную ошибку.
 *
 * Отмена корутины пробрасывается как есть: это управление потоком выполнения,
 * а не ошибка, и превращать её в сообщение пользователю нельзя.
 */
suspend fun <T> apiCall(mapper: ErrorMapper, block: suspend () -> T): T =
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        throw mapper.map(throwable)
    }
