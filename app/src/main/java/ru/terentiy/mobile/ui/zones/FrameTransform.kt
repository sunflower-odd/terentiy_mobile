package ru.terentiy.mobile.ui.zones

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import ru.terentiy.mobile.domain.ZonePoint

/**
 * Масштаб и сдвиг кадра в полноэкранном виде.
 *
 * При масштабе 1 кадр вписан в [container]: левый верхний угол [origin], размер
 * [frame], вокруг поля. При увеличении кадр занимает и поля, вплоть до всего
 * экрана. По каждой оси: пока кадр меньше экрана, он стоит по центру; когда
 * больше — сдвигается, но край кадра не заходит внутрь экрана.
 * Точки зоны хранятся в долях кадра, поэтому от масштаба они не зависят.
 */
data class FrameTransform(
    val container: Size,
    val origin: Offset,
    val frame: Size,
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
) {
    val zoomed: Boolean get() = scale > 1.001f

    fun toScreen(point: ZonePoint): Offset = Offset(
        origin.x + offset.x + point.x * frame.width * scale,
        origin.y + offset.y + point.y * frame.height * scale,
    )

    fun toPoint(screen: Offset): ZonePoint = ZonePoint(
        (screen.x - origin.x - offset.x) / (frame.width * scale),
        (screen.y - origin.y - offset.y) / (frame.height * scale),
    )

    /** Касание пришлось на кадр, а не на поля вокруг него. */
    fun contains(screen: Offset): Boolean {
        val topLeft = toScreen(ZonePoint(0f, 0f))
        val bottomRight = toScreen(ZonePoint(1f, 1f))
        return screen.x in topLeft.x..bottomRight.x && screen.y in topLeft.y..bottomRight.y
    }

    /** Увеличение в [factor] раз вокруг точки [centroid] на экране и сдвиг на [pan]. */
    fun zoomedBy(centroid: Offset, factor: Float, pan: Offset = Offset.Zero): FrameTransform {
        val newScale = (scale * factor).coerceIn(1f, MAX_SCALE)
        val local = centroid - origin
        val newOffset = local - (local - offset) * (newScale / scale) + pan
        return copy(scale = newScale, offset = newOffset).clamped()
    }

    /** Двойное касание: увеличить к месту касания или вернуть исходный масштаб. */
    fun toggledAt(screen: Offset): FrameTransform =
        if (zoomed) reset() else zoomedBy(screen, DOUBLE_TAP_SCALE / scale)

    fun reset(): FrameTransform = copy(scale = 1f, offset = Offset.Zero)

    private fun clamped(): FrameTransform = copy(
        offset = Offset(
            clampAxis(origin.x, offset.x, frame.width * scale, container.width),
            clampAxis(origin.y, offset.y, frame.height * scale, container.height),
        ),
    )

    /** Сдвиг по одной оси: кадр меньше экрана — по центру, больше — без пустоты у краёв. */
    private fun clampAxis(origin: Float, offset: Float, scaled: Float, container: Float): Float {
        val start = if (scaled <= container) (container - scaled) / 2f else (origin + offset).coerceIn(container - scaled, 0f)
        return start - origin
    }

    companion object {
        const val MAX_SCALE = 6f
        const val DOUBLE_TAP_SCALE = 3f

        /** Кадр с пропорциями [aspect] (ширина к высоте), вписанный по центру [container]. */
        fun fit(container: Size, aspect: Float): FrameTransform {
            val frame = if (container.width / container.height > aspect) {
                Size(container.height * aspect, container.height)
            } else {
                Size(container.width, container.width / aspect)
            }
            val origin = Offset((container.width - frame.width) / 2f, (container.height - frame.height) / 2f)
            return FrameTransform(container = container, origin = origin, frame = frame)
        }
    }
}
