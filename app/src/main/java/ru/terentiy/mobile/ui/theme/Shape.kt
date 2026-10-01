package ru.terentiy.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Скругления из макетов: крупные мягкие карточки, внутри них — блоки поменьше.
 * Кнопки скругляются полностью и используют CircleShape напрямую, поэтому
 * отдельного размера для них здесь нет.
 */
val TerentiyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
