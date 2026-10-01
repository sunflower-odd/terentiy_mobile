package ru.terentiy.mobile.data.remote

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import ru.terentiy.mobile.core.error.AppError
import ru.terentiy.mobile.core.error.ErrorMapper
import ru.terentiy.mobile.data.local.MediaCache
import ru.terentiy.mobile.domain.MediaPayload
import timber.log.Timber

/**
 * Загрузка кадра или клипа.
 *
 * Маршрут один, тип различается только заголовком `Content-Type`, а размеры
 * расходятся на три порядка: кадр сервер ограничивает 8 МБ, клип — 128 МБ.
 * Поэтому кадр читается в память, а клип **пишется в файл потоком**: `bytes()`
 * на 128 МБ означает гарантированный OOM.
 *
 * Range сервер не поддерживает и `ETag` не отдаёт, так что докачка невозможна, а
 * HTTP-кеш бесполезен. Зато медиа неизменяемо по идентификатору, и этим
 * пользуется [MediaCache].
 */
@Singleton
class MediaDownloader @Inject constructor(
    private val api: TerentiyApi,
    private val cache: MediaCache,
    private val errorMapper: ErrorMapper,
) {

    /**
     * @param onProgress получает скачанные байты и общий размер, если сервер его
     *   сообщил. Вызывается на потоке ввода-вывода.
     */
    suspend fun download(
        mediaId: String,
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> },
    ): MediaPayload = withContext(Dispatchers.IO) {
        cache.video(mediaId)?.let { return@withContext MediaPayload.Video(it) }

        val response = api.media(mediaId)
        if (!response.isSuccessful) {
            val body = runCatching { response.errorBody()?.string() }.getOrNull()
            throw errorMapper.fromResponse(response.code(), body)
        }

        // Тело @Streaming-ответа обязано быть закрыто: незакрытое подвешивает
        // соединение, и следующий опрос встанет в очередь за ним.
        response.body().use { body ->
            if (body == null) throw AppError.Unexpected(null)
            val contentType = body.contentType()?.let { "${it.type}/${it.subtype}" }
            val total = body.contentLength().takeIf { it > 0 }

            when (contentType) {
                TYPE_JPEG -> {
                    if (total != null && total > MAX_IMAGE_BYTES) throw AppError.MediaUnsupported(contentType)
                    MediaPayload.Image(body.bytes())
                }

                TYPE_MP4 -> MediaPayload.Video(writeToCache(mediaId, body, total, onProgress))

                else -> {
                    // Практически это значит, что по введённому адресу отвечает
                    // не наш сервер: контракт допускает только JPEG и MP4.
                    Timber.w("Медиа неожиданного типа | id=%s | type=%s", mediaId, contentType)
                    throw AppError.MediaUnsupported(contentType)
                }
            }
        }
    }

    private fun writeToCache(
        mediaId: String,
        body: okhttp3.ResponseBody,
        total: Long?,
        onProgress: (Long, Long?) -> Unit,
    ): File {
        val target = cache.partial(mediaId)
        var written = 0L
        try {
            target.sink().buffer().use { sink ->
                val source = body.source()
                val chunk = okio.Buffer()
                while (true) {
                    val read = source.read(chunk, CHUNK_BYTES)
                    if (read == -1L) break
                    sink.write(chunk, read)
                    written += read
                    if (written > MAX_CLIP_BYTES) {
                        // Потолок повторяет серверный max_clip_size_mb = 128
                        // (terentiy_ai/server/config/server.example.toml). Без него
                        // испорченный ответ заполнил бы диск устройства.
                        throw AppError.MediaUnsupported(TYPE_MP4)
                    }
                    onProgress(written, total)
                }
            }
        } catch (error: Throwable) {
            cache.discardPartial(mediaId)
            throw if (error is AppError) error else errorMapper.map(error)
        }
        return cache.commit(mediaId)
    }

    private companion object {
        const val TYPE_JPEG = "image/jpeg"
        const val TYPE_MP4 = "video/mp4"
        const val CHUNK_BYTES = 64L * 1024
        const val MAX_IMAGE_BYTES = 16L * 1024 * 1024
        const val MAX_CLIP_BYTES = 128L * 1024 * 1024
    }
}
