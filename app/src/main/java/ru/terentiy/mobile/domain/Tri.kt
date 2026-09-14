package ru.terentiy.mobile.domain

/**
 * Троичный флаг: да, нет, неизвестно.
 *
 * Backend отдаёт технические признаки агента как `boolean | null`, где `null` означает
 * «агент ни разу не присылал heartbeat», а не «выключено». Если оставить эти поля
 * как `Boolean?`, то любое `if (flag == true)` схлопнет «неизвестно» и «нет» в одну
 * ветку, и пользователь увидит «камера недоступна» там, где правда — «мы пока
 * ничего не знаем».
 *
 * Это ровно то различение, которого требует SDD-000 §6 и §11 для присутствия
 * человека: «камера не работает» не равно «человека нет». Перенесено на технические
 * флаги без изменений. См. docs/adr/0003-tri-vmesto-nullable-boolean.md.
 */
enum class Tri {
    YES,
    NO,
    UNKNOWN;

    val isYes: Boolean get() = this == YES

    companion object {
        fun of(value: Boolean?): Tri = when (value) {
            true -> YES
            false -> NO
            null -> UNKNOWN
        }
    }
}
