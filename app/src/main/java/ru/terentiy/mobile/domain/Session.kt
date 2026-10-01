package ru.terentiy.mobile.domain

/**
 * Сессия мобильного устройства.
 *
 * Токен opaque, **без срока жизни и без отзыва**: в таблице `access_tokens` на
 * сервере нет ни `expires_at`, ни `revoked_at`, а маршрута выхода не существует.
 * Поэтому здесь нет ни refresh-токена, ни сроков — обновлять нечего.
 *
 * Следствия, которые нужно помнить:
 * - `401` означает не «истёк», а «сервер этот токен не знает» — реакция одна:
 *   повторный вход по `device_id` и PIN;
 * - каждый вход выдаёт новый токен, а прежние остаются действительными навсегда;
 * - выход из аккаунта стирает токен только локально. Это всё, что может клиент.
 *
 * См. docs/backlog-mobile.md §3.
 */
data class Session(
    val accessToken: String,
    val householdId: String,
)

/** Состояние локальной сессии, определяющее стартовый экран. */
sealed interface SessionState {
    /** Устройство ещё не привязано к установке. */
    data object NotActivated : SessionState

    /** Устройство привязано, но токена нет — нужен вход по PIN. */
    data object NeedsPin : SessionState

    /** Токен есть; его действительность подтвердит первый же запрос. */
    data class Active(val userName: String, val householdId: String) : SessionState
}
