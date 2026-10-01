package ru.terentiy.mobile.domain

import java.io.File

/**
 * Содержимое `GET /api/v1/media/{media_id}`.
 *
 * Кадр и клип приходят одним маршрутом и различаются **только заголовком
 * `Content-Type`** — ни расширения в пути, ни отдельного эндпоинта нет.
 * Разница в размере на три порядка: кадр сервер ограничивает 8 МБ, клип — 128 МБ,
 * поэтому единый `ByteArray` здесь не годится: 128 МБ в памяти означают
 * гарантированный OOM.
 *
 * Кадр читается в память, клип пишется в файл потоком.
 */
sealed interface MediaPayload {
    data class Image(val bytes: ByteArray) : MediaPayload {
        // Массив байтов сравнивается по ссылке, из-за чего Compose считал бы
        // состояние изменившимся на каждом чтении.
        override fun equals(other: Any?): Boolean =
            this === other || (other is Image && bytes.contentEquals(other.bytes))

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    data class Video(val file: File) : MediaPayload
}
