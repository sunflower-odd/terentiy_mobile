package ru.terentiy.mobile.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Кеш скачанных клипов на диске.
 *
 * Свой, а не OkHttp: сервер не отдаёт ни `ETag`, ни `Cache-Control`, поэтому
 * HTTP-кеш бесполезен. Зато медиа **неизменяемо по идентификатору** — файл,
 * скачанный один раз, остаётся верным навсегда, и кеш по имени файла тривиально
 * корректен.
 *
 * Живёт в `cacheDir`: система вправе его вычистить, и это нормально — файл
 * всегда можно скачать заново.
 */
@Singleton
class MediaCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val directory: File
        get() = File(context.cacheDir, DIRECTORY).apply { mkdirs() }

    /** Готовый файл клипа, если он уже скачан. */
    fun video(mediaId: String): File? = File(directory, mediaId).takeIf { it.isFile && it.length() > 0 }

    /** Файл для записи в процессе загрузки. */
    fun partial(mediaId: String): File = File(directory, "$mediaId$PARTIAL_SUFFIX").apply {
        parentFile?.mkdirs()
        delete()
    }

    /** Переносит дописанный файл под постоянное имя. */
    fun commit(mediaId: String): File {
        val done = File(directory, mediaId)
        val partial = File(directory, "$mediaId$PARTIAL_SUFFIX")
        if (!partial.renameTo(done)) {
            partial.copyTo(done, overwrite = true)
            partial.delete()
        }
        trimToBudget()
        return done
    }

    fun discardPartial(mediaId: String) {
        File(directory, "$mediaId$PARTIAL_SUFFIX").delete()
    }

    /** Сколько места занято — показывается в Профиле. */
    fun sizeBytes(): Long = directory.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    /**
     * Вытеснение по бюджету: самые давние по времени обращения удаляются первыми.
     * Клипы по 30 секунд копятся молча, и без потолка кеш растёт до конца места.
     */
    private fun trimToBudget() {
        val files = directory.listFiles()?.sortedBy { it.lastModified() }?.toMutableList() ?: return
        var total = files.sumOf { it.length() }
        while (total > BUDGET_BYTES && files.isNotEmpty()) {
            val oldest = files.removeAt(0)
            total -= oldest.length()
            if (oldest.delete()) Timber.d("Из кеша вытеснен файл | name=%s", oldest.name)
        }
    }

    private companion object {
        const val DIRECTORY = "media"
        const val PARTIAL_SUFFIX = ".part"
        const val BUDGET_BYTES = 256L * 1024 * 1024
    }
}
