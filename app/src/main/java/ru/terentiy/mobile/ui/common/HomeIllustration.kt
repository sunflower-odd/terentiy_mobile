package ru.terentiy.mobile.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Иллюстрация с экрана активации: домик, соединённый линией-сердцем с камерой.
 *
 * Рисуется кодом, а не картинкой: фигуры простые, зато иллюстрация сама
 * подхватывает цвета темы и одинаково чётко выглядит на любой плотности экрана.
 */
@Composable
fun HomeIllustration(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.primaryContainer
    val leaf = MaterialTheme.colorScheme.secondaryContainer

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = h * 0.022f)

        // Мягкие кусты на заднем плане
        drawCircle(color = soft, radius = h * 0.26f, center = Offset(w * 0.20f, h * 0.62f))
        drawCircle(color = soft, radius = h * 0.30f, center = Offset(w * 0.80f, h * 0.60f))

        // Домик
        val houseLeft = w * 0.16f
        val houseTop = h * 0.40f
        val houseW = w * 0.34f
        val houseH = h * 0.40f
        drawRect(
            color = accent,
            topLeft = Offset(houseLeft, houseTop),
            size = Size(houseW, houseH),
            style = stroke,
        )
        val roof = Path().apply {
            moveTo(houseLeft - w * 0.03f, houseTop)
            lineTo(houseLeft + houseW / 2f, houseTop - h * 0.20f)
            lineTo(houseLeft + houseW + w * 0.03f, houseTop)
        }
        drawPath(path = roof, color = accent, style = stroke)

        // Окно
        drawRect(
            color = leaf,
            topLeft = Offset(houseLeft + houseW * 0.30f, houseTop + houseH * 0.20f),
            size = Size(houseW * 0.40f, houseH * 0.34f),
        )

        // Линия-сердце от дома к камере
        val heart = Path().apply {
            moveTo(houseLeft + houseW, houseTop + houseH * 0.55f)
            cubicTo(
                w * 0.54f, h * 0.44f,
                w * 0.52f, h * 0.30f,
                w * 0.58f, h * 0.36f,
            )
            cubicTo(
                w * 0.63f, h * 0.42f,
                w * 0.60f, h * 0.52f,
                w * 0.66f, h * 0.56f,
            )
        }
        drawPath(path = heart, color = accent, style = stroke)

        // Камера
        drawCircle(color = accent, radius = h * 0.13f, center = Offset(w * 0.78f, h * 0.48f), style = stroke)
        drawCircle(color = accent, radius = h * 0.05f, center = Offset(w * 0.78f, h * 0.48f))
        drawRect(
            color = accent,
            topLeft = Offset(w * 0.73f, h * 0.70f),
            size = Size(w * 0.10f, h * 0.05f),
        )
    }
}
