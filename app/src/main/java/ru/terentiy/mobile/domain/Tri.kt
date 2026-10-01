package ru.terentiy.mobile.domain

/**
 * Троичный флаг: да, нет, неизвестно.
 *
 * Контракт отдаёт технические признаки агента как `boolean | null`, где `null`
 * означает «агент ни разу не присылал heartbeat», а не «выключено» — это прямо
 * написано в описании `GET /api/v1/agents`. Если оставить такие поля как
 * `Boolean?`, любое `if (flag == true)` схлопнет «неизвестно» и «нет» в одну
 * ветку, и пользователь увидит «камера недоступна» там, где правда — «мы пока
 * ничего не знаем».
 *
 * Это ровно то различение, которого требует SDD-000 для присутствия человека:
 * «камера не работает» не равно «человека нет». См.
 * docs/adr/0003-tri-vmesto-nullable-boolean.md — решение вернулось в силу после
 * того, как троичность стала частью самого контракта.
 */
enum class Tri {
    YES,
    NO,
    UNKNOWN;

    val isYes: Boolean get() = this == YES
    val isKnown: Boolean get() = this != UNKNOWN

    companion object {
        fun of(value: Boolean?): Tri = when (value) {
            true -> YES
            false -> NO
            null -> UNKNOWN
        }
    }
}
