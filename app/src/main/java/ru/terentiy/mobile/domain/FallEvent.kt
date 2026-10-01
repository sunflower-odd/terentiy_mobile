package ru.terentiy.mobile.domain

import java.time.Instant

/**
 * Состояние события на сервере.
 *
 * В реализованном контракте у перечисления ровно один вариант — `confirmed`, и в
 * базе сервера других значений не появляется: решение «кандидат» или «кулдаун»
 * не создаёт записи события вовсе. [OTHER] существует для прямой совместимости:
 * неизвестное значение не должно ронять разбор всего списка.
 */
enum class EventStatus {
    CONFIRMED,
    OTHER;

    companion object {
        fun parse(raw: String?): EventStatus =
            if (raw?.lowercase() == "confirmed") CONFIRMED else OTHER
    }
}

/**
 * Событие падения.
 *
 * Один тип вместо прежней пары «превью + карточка»: маршрута `GET /events/{id}`
 * не существует, `GET /events` отдаёт полный объект, и делить модель больше не на что.
 *
 * [clipMediaId] не nullable, и это не оптимизм: `list_fall_events` на сервере
 * фильтрует `clip_media_id IS NOT NULL`, то есть событие становится видимым
 * мобильному клиенту только после того, как агент догрузил клип. Состояния
 * «клип готовится» в контракте нет — вместо него событие просто ещё не пришло.
 * Обратная сторона: от падения до появления в истории проходит около 15 секунд
 * пост-ролла плюс запись и загрузка.
 */
data class FallEvent(
    val id: String,
    val agentId: String,
    val frameId: String,
    val frameMediaId: String,
    val clipMediaId: String,
    /** Время кадра, породившего событие. Это то, что видит пользователь. */
    val capturedAt: Instant?,
    /** Время записи события на сервере. Только для диагностики. */
    val createdAt: Instant?,
    val status: EventStatus,
    /** `null`, если форма `analysis` не разобралась — см. [FallAnalysis]. */
    val analysis: FallAnalysis?,
)

/**
 * Разобранная часть поля `analysis`.
 *
 * В контракте `analysis` — свободный объект (`additionalProperties: true`), и это
 * самое вероятное место дрейфа backend: формат меняется вместе с правилами
 * распознавания. Поэтому наружу отдаётся не сырой JSON, а этот класс, а
 * неразобравшееся содержимое даёт `null` вместо исключения — иначе правка правил
 * на сервере уронила бы весь список событий в приложении.
 *
 * Из `analysis` берётся только то, что читаемо без словаря: голосование правил и
 * их имена. `people`, `poses`, `pose_diagnostics` и `ml` намеренно игнорируются —
 * это ML-внутренности, и привязка интерфейса к их форме означала бы правку
 * приложения на каждое изменение модели.
 */
data class FallAnalysis(
    val detected: Boolean,
    val trackId: Int?,
    val candidateRules: List<String>,
    val confirmationRules: List<String>,
    val candidateVotes: Int?,
    val candidateVotesRequired: Int?,
    val confirmationVotes: Int?,
    val confirmationVotesRequired: Int?,
    val processingTimeMs: Double?,
) {
    /** Есть ли что показать в блоке подробностей. */
    val hasDetails: Boolean
        get() = candidateRules.isNotEmpty() ||
            confirmationRules.isNotEmpty() ||
            confirmationVotes != null
}
