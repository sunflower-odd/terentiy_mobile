package ru.terentiy.mobile.ui.zones

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.terentiy.mobile.domain.ZonePoint

/** Пересчёт касаний в доли кадра при увеличении. */
class FrameTransformTest {

    private val eps = 0.001f

    // Экран 1000×1000, кадр 4:3 вписан по ширине: 1000×750, сверху и снизу поля по 125.
    private val base = FrameTransform.fit(Size(1000f, 1000f), 4f / 3f)

    @Test
    fun `кадр вписывается по центру`() {
        assertEquals(Offset(0f, 125f), base.origin)
        assertEquals(Size(1000f, 750f), base.frame)
        assertFalse(base.contains(Offset(500f, 50f)))
        assertTrue(base.contains(Offset(500f, 500f)))
    }

    @Test
    fun `точка переводится туда и обратно при любом масштабе`() {
        val zoomed = base.zoomedBy(Offset(300f, 400f), 3f)
        val point = ZonePoint(0.3f, 0.4f)
        val back = zoomed.toPoint(zoomed.toScreen(point))
        assertEquals(point.x, back.x, eps)
        assertEquals(point.y, back.y, eps)
    }

    @Test
    fun `увеличение не сдвигает точку под пальцами`() {
        val centroid = Offset(300f, 400f)
        val before = base.toPoint(centroid)
        val after = base.zoomedBy(centroid, 2f).toPoint(centroid)
        assertEquals(before.x, after.x, eps)
        assertEquals(before.y, after.y, eps)
    }

    @Test
    fun `масштаб и сдвиг ограничены`() {
        assertEquals(1f, base.zoomedBy(Offset(500f, 500f), 0.5f).scale, eps)
        assertEquals(FrameTransform.MAX_SCALE, base.zoomedBy(Offset(500f, 500f), 100f).scale, eps)
        // Сдвиг за край: край кадра не заходит внутрь экрана.
        val panned = base.zoomedBy(Offset(500f, 500f), 2f, pan = Offset(5000f, -5000f))
        assertEquals(0f, panned.toScreen(ZonePoint(0f, 0f)).x, eps)
        assertEquals(1000f, panned.toScreen(ZonePoint(1f, 1f)).y, eps)
    }

    @Test
    fun `увеличенный кадр занимает поля вокруг него`() {
        // При масштабе 2 кадр 2000×1500 больше экрана 1000×1000 по обеим осям и закрывает его целиком.
        val zoomed = base.zoomedBy(Offset(500f, 500f), 2f)
        val topLeft = zoomed.toScreen(ZonePoint(0f, 0f))
        val bottomRight = zoomed.toScreen(ZonePoint(1f, 1f))
        assertTrue(topLeft.x <= 0f && topLeft.y <= 0f)
        assertTrue(bottomRight.x >= 1000f && bottomRight.y >= 1000f)
        assertTrue(zoomed.contains(Offset(500f, 50f)))
    }

    @Test
    fun `кадр меньше экрана стоит по центру`() {
        // При масштабе 1.2 высота 900 меньше 1000: кадр по центру по вертикали, сколько ни сдвигай.
        val zoomed = base.zoomedBy(Offset(500f, 500f), 1.2f, pan = Offset(0f, 300f))
        assertEquals(50f, zoomed.toScreen(ZonePoint(0f, 0f)).y, eps)
        assertEquals(950f, zoomed.toScreen(ZonePoint(0f, 1f)).y, eps)
    }

    @Test
    fun `двойное касание увеличивает и возвращает`() {
        val zoomed = base.toggledAt(Offset(200f, 300f))
        assertEquals(FrameTransform.DOUBLE_TAP_SCALE, zoomed.scale, eps)
        assertEquals(1f, zoomed.toggledAt(Offset(200f, 300f)).scale, eps)
        assertEquals(Offset.Zero, zoomed.reset().offset)
    }
}
