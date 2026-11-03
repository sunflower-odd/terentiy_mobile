package ru.terentiy.mobile.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Иконки, которых нет в базовом наборе Material.
 *
 * Здесь **все** иконки приложения, а не только экзотические. Причина в двух
 * решениях, наложившихся друг на друга: расширенный набор
 * `material-icons-extended` заморожен и тянет тысячи неиспользуемых векторов,
 * а базовый `material-icons-core` в Compose BOM 2026.02 больше не приходит
 * транзитивно вместе с Material 3 — сборка падала на `Unresolved reference
 * 'icons'`. Вместо подключения отделённого и объявленного устаревшим артефакта
 * нужные формы нарисованы здесь: их полтора десятка, они простые, и
 * зависимостей у них нет.
 *
 * Все построены в стандартном для Material поле 24×24, поэтому масштабируются
 * и окрашиваются через обычный `Icon(imageVector = ..., tint = ...)`.
 */
private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

private val Black = SolidColor(Color.Black)

/** Четыре угла наружу: развернуть кадр на весь экран. */
val IconExpand: ImageVector by lazy {
    icon("Expand") {
        path(
            stroke = Black,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f)
            moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
            moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
            moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f)
        }
    }
}

/** Корзина: удалить. */
val IconDelete: ImageVector by lazy {
    icon("Delete") {
        path(
            stroke = Black,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4f, 7f); lineTo(20f, 7f)
            moveTo(9f, 7f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 7f)
            moveTo(6f, 7f); lineTo(7f, 20f); lineTo(17f, 20f); lineTo(18f, 7f)
            moveTo(10f, 11f); lineTo(10f, 16f)
            moveTo(14f, 11f); lineTo(14f, 16f)
        }
    }
}

/** Открытый глаз: показать пароль. */
val IconEye: ImageVector by lazy {
    icon("Eye") {
        path(fill = Black, pathFillType = PathFillType.EvenOdd) {
            moveTo(12f, 5f)
            curveTo(6f, 5f, 2.5f, 10.5f, 2f, 12f)
            curveTo(2.5f, 13.5f, 6f, 19f, 12f, 19f)
            curveTo(18f, 19f, 21.5f, 13.5f, 22f, 12f)
            curveTo(21.5f, 10.5f, 18f, 5f, 12f, 5f)
            close()
            moveTo(12f, 15.5f)
            curveTo(10.1f, 15.5f, 8.5f, 13.9f, 8.5f, 12f)
            curveTo(8.5f, 10.1f, 10.1f, 8.5f, 12f, 8.5f)
            curveTo(13.9f, 8.5f, 15.5f, 10.1f, 15.5f, 12f)
            curveTo(15.5f, 13.9f, 13.9f, 15.5f, 12f, 15.5f)
            close()
        }
    }
}

/** Перечёркнутый глаз: скрыть пароль. */
val IconEyeOff: ImageVector by lazy {
    icon("EyeOff") {
        path(fill = Black, pathFillType = PathFillType.EvenOdd) {
            moveTo(12f, 5f)
            curveTo(6f, 5f, 2.5f, 10.5f, 2f, 12f)
            curveTo(2.5f, 13.5f, 6f, 19f, 12f, 19f)
            curveTo(18f, 19f, 21.5f, 13.5f, 22f, 12f)
            curveTo(21.5f, 10.5f, 18f, 5f, 12f, 5f)
            close()
            moveTo(12f, 15.5f)
            curveTo(10.1f, 15.5f, 8.5f, 13.9f, 8.5f, 12f)
            curveTo(8.5f, 10.1f, 10.1f, 8.5f, 12f, 8.5f)
            curveTo(13.9f, 8.5f, 15.5f, 10.1f, 15.5f, 12f)
            curveTo(15.5f, 13.9f, 13.9f, 15.5f, 12f, 15.5f)
            close()
        }
        path(stroke = Black, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(4f, 4f)
            lineTo(20f, 20f)
        }
    }
}

/** Полумесяц: наблюдение отключено. */
val IconMoon: ImageVector by lazy {
    icon("Moon") {
        path(fill = Black, pathFillType = PathFillType.EvenOdd) {
            moveTo(20f, 15.5f)
            curveTo(18.9f, 15.9f, 17.7f, 16.1f, 16.5f, 16.1f)
            curveTo(11.3f, 16.1f, 7.1f, 11.9f, 7.1f, 6.7f)
            curveTo(7.1f, 5.5f, 7.3f, 4.3f, 7.7f, 3.2f)
            curveTo(4.9f, 4.7f, 3f, 7.7f, 3f, 11.1f)
            curveTo(3f, 16.1f, 7.1f, 20.2f, 12.1f, 20.2f)
            curveTo(15.5f, 20.2f, 18.5f, 18.3f, 20f, 15.5f)
            close()
        }
    }
}

/** Видеокамера: состояние камеры и активное наблюдение. */
val IconVideoCam: ImageVector by lazy {
    icon("VideoCam") {
        path(fill = Black) {
            moveTo(4f, 6f)
            lineTo(13f, 6f)
            curveTo(14.1f, 6f, 15f, 6.9f, 15f, 8f)
            lineTo(15f, 16f)
            curveTo(15f, 17.1f, 14.1f, 18f, 13f, 18f)
            lineTo(4f, 18f)
            curveTo(2.9f, 18f, 2f, 17.1f, 2f, 16f)
            lineTo(2f, 8f)
            curveTo(2f, 6.9f, 2.9f, 6f, 4f, 6f)
            close()
            moveTo(22f, 7.5f)
            lineTo(22f, 16.5f)
            lineTo(17f, 13.5f)
            lineTo(17f, 10.5f)
            close()
        }
    }
}

/** Значок связи: состояние агента. */
val IconWifi: ImageVector by lazy {
    icon("Wifi") {
        path(stroke = Black, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(4.5f, 10f)
            curveTo(8.6f, 6.4f, 15.4f, 6.4f, 19.5f, 10f)
        }
        path(stroke = Black, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(7.5f, 13.4f)
            curveTo(10.1f, 11.2f, 13.9f, 11.2f, 16.5f, 13.4f)
        }
        path(fill = Black) {
            moveTo(12f, 19f)
            curveTo(10.9f, 19f, 10f, 18.1f, 10f, 17f)
            curveTo(10f, 15.9f, 10.9f, 15f, 12f, 15f)
            curveTo(13.1f, 15f, 14f, 15.9f, 14f, 17f)
            curveTo(14f, 18.1f, 13.1f, 19f, 12f, 19f)
            close()
        }
    }
}

/** Ключ: поле кода активации. */
val IconKey: ImageVector by lazy {
    icon("Key") {
        path(fill = Black, pathFillType = PathFillType.EvenOdd) {
            moveTo(15.5f, 3f)
            curveTo(12.5f, 3f, 10f, 5.5f, 10f, 8.5f)
            curveTo(10f, 9.2f, 10.1f, 9.8f, 10.3f, 10.4f)
            lineTo(3f, 17.7f)
            lineTo(3f, 21f)
            lineTo(6.3f, 21f)
            lineTo(7.8f, 19.5f)
            lineTo(7.8f, 17.5f)
            lineTo(9.8f, 17.5f)
            lineTo(11.3f, 16f)
            lineTo(13.6f, 13.7f)
            curveTo(14.2f, 13.9f, 14.8f, 14f, 15.5f, 14f)
            curveTo(18.5f, 14f, 21f, 11.5f, 21f, 8.5f)
            curveTo(21f, 5.5f, 18.5f, 3f, 15.5f, 3f)
            close()
            moveTo(16.5f, 9f)
            curveTo(15.7f, 9f, 15f, 8.3f, 15f, 7.5f)
            curveTo(15f, 6.7f, 15.7f, 6f, 16.5f, 6f)
            curveTo(17.3f, 6f, 18f, 6.7f, 18f, 7.5f)
            curveTo(18f, 8.3f, 17.3f, 9f, 16.5f, 9f)
            close()
        }
    }
}

/** Часы: вкладка истории. */
val IconClock: ImageVector by lazy {
    icon("Clock") {
        path(fill = Black, pathFillType = PathFillType.EvenOdd) {
            moveTo(12f, 2f)
            curveTo(6.5f, 2f, 2f, 6.5f, 2f, 12f)
            curveTo(2f, 17.5f, 6.5f, 22f, 12f, 22f)
            curveTo(17.5f, 22f, 22f, 17.5f, 22f, 12f)
            curveTo(22f, 6.5f, 17.5f, 2f, 12f, 2f)
            close()
            moveTo(12f, 20f)
            curveTo(7.6f, 20f, 4f, 16.4f, 4f, 12f)
            curveTo(4f, 7.6f, 7.6f, 4f, 12f, 4f)
            curveTo(16.4f, 4f, 20f, 7.6f, 20f, 12f)
            curveTo(20f, 16.4f, 16.4f, 20f, 12f, 20f)
            close()
        }
        path(stroke = Black, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(12f, 7f)
            lineTo(12f, 12f)
            lineTo(15.5f, 14f)
        }
    }
}

// --- Формы из базового набора Material, нарисованные заново ---
//
// В Compose BOM 2026.02 артефакт material-icons-core перестал приходить вместе
// с Material 3, а подключать его отдельно незачем: он объявлен устаревшим.

/** Силуэт человека: профиль, логин. */
val IconPerson: ImageVector by lazy {
    icon("Person") {
        path(fill = Black, pathFillType = PathFillType.EvenOdd) {
            moveTo(12f, 4f)
            curveTo(14.2f, 4f, 16f, 5.8f, 16f, 8f)
            curveTo(16f, 10.2f, 14.2f, 12f, 12f, 12f)
            curveTo(9.8f, 12f, 8f, 10.2f, 8f, 8f)
            curveTo(8f, 5.8f, 9.8f, 4f, 12f, 4f)
            close()
            moveTo(12f, 14f)
            curveTo(16.4f, 14f, 20f, 16.2f, 20f, 19f)
            lineTo(20f, 20f)
            lineTo(4f, 20f)
            lineTo(4f, 19f)
            curveTo(4f, 16.2f, 7.6f, 14f, 12f, 14f)
            close()
        }
    }
}

/** Замок: пароль. */
val IconLock: ImageVector by lazy {
    icon("Lock") {
        path(stroke = Black, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(8f, 10f)
            lineTo(8f, 8f)
            curveTo(8f, 5.8f, 9.8f, 4f, 12f, 4f)
            curveTo(14.2f, 4f, 16f, 5.8f, 16f, 8f)
            lineTo(16f, 10f)
        }
        path(fill = Black, pathFillType = PathFillType.EvenOdd) {
            moveTo(6f, 10f)
            lineTo(18f, 10f)
            lineTo(18f, 20f)
            lineTo(6f, 20f)
            close()
            moveTo(11f, 13f)
            lineTo(13f, 13f)
            lineTo(13f, 17f)
            lineTo(11f, 17f)
            close()
        }
    }
}

/** Домик: вкладка «Главная». */
val IconHome: ImageVector by lazy {
    icon("Home") {
        path(fill = Black, pathFillType = PathFillType.NonZero) {
            moveTo(12f, 3f)
            lineTo(21.5f, 11f)
            lineTo(19f, 11f)
            lineTo(19f, 20f)
            lineTo(14.5f, 20f)
            lineTo(14.5f, 14f)
            lineTo(9.5f, 14f)
            lineTo(9.5f, 20f)
            lineTo(5f, 20f)
            lineTo(5f, 11f)
            lineTo(2.5f, 11f)
            close()
        }
    }
}

/** Треугольник с восклицательным знаком: тревога и неизвестное состояние. */
val IconWarning: ImageVector by lazy {
    icon("Warning") {
        path(fill = Black, pathFillType = PathFillType.EvenOdd) {
            moveTo(12f, 3f)
            lineTo(22f, 20f)
            lineTo(2f, 20f)
            close()
            // Знак вырезается из треугольника правилом EvenOdd, поэтому иконка
            // остаётся одноцветной и корректно окрашивается через tint.
            moveTo(11f, 9f)
            lineTo(13f, 9f)
            lineTo(13f, 14.5f)
            lineTo(11f, 14.5f)
            close()
            moveTo(11f, 16f)
            lineTo(13f, 16f)
            lineTo(13f, 18f)
            lineTo(11f, 18f)
            close()
        }
    }
}

/** Крестик: закрытие баннера. */
val IconClose: ImageVector by lazy {
    icon("Close") {
        path(stroke = Black, strokeLineWidth = 2.5f, strokeLineCap = StrokeCap.Round) {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }
}

/** Шеврон вправо: строка списка открывается. */
val IconChevronRight: ImageVector by lazy {
    icon("ChevronRight") {
        path(
            stroke = Black,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(9.5f, 6f)
            lineTo(15.5f, 12f)
            lineTo(9.5f, 18f)
        }
    }
}

/** Стрелка назад. */
val IconArrowBack: ImageVector by lazy {
    icon("ArrowBack") {
        path(
            stroke = Black,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(20f, 12f)
            lineTo(4.5f, 12f)
            moveTo(11f, 5.5f)
            lineTo(4.5f, 12f)
            lineTo(11f, 18.5f)
        }
    }
}
