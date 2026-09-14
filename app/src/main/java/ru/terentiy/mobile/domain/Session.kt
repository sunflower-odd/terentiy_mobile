package ru.terentiy.mobile.domain

/** Состояние локальной сессии, определяющее стартовый экран приложения. */
sealed interface Session {

    /** Устройство ещё не привязано к установке. */
    data object NotActivated : Session

    /** Устройство привязано, но действующего токена нет — нужен PIN. */
    data object NeedsPin : Session

    /** Есть токен; его действительность подтвердит первый же запрос к API. */
    data class Active(val userName: String, val householdId: String) : Session
}
