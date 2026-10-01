package ru.terentiy.mobile.ui.eventdetail

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import timber.log.Timber

/**
 * Декодирование кадра с понижением разрешения.
 *
 * Сервер ограничивает кадр восьмью мегабайтами, но это про размер файла, а не
 * про память: кадр 4K разворачивается в десятки мегабайт ARGB, и на minSdk 26
 * это реальный риск падения по нехватке памяти. Поэтому два прохода: сначала
 * только размеры, потом декодирование с ближайшей степенью двойки.
 */
internal fun decodeFrame(bytes: ByteArray, targetWidth: Int): ImageBitmap? {
    if (bytes.isEmpty()) return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, targetWidth)
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }.getOrElse { error ->
        Timber.w(error, "Кадр не удалось декодировать")
        null
    }
}

private fun sampleSize(sourceWidth: Int, targetWidth: Int): Int {
    if (sourceWidth <= 0 || targetWidth <= 0) return 1
    var sample = 1
    while (sourceWidth / (sample * 2) >= targetWidth) sample *= 2
    return sample
}
