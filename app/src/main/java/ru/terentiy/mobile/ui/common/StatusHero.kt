package ru.terentiy.mobile.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Что показывает круглая зона состояния на главном экране. */
enum class HeroMood { SLEEPING, WATCHING, ALERT }

/**
 * Круглая зона состояния из макетов.
 *
 * Для включённого наблюдения расходятся мягкие кольца — это единственная
 * анимация в приложении, и она несёт смысл: показывает, что система работает
 * прямо сейчас, а не замерла. В остальных состояниях движения нет намеренно.
 */
@Composable
fun StatusHero(
    mood: HeroMood,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 200.dp,
) {
    val accent = when (mood) {
        HeroMood.SLEEPING -> MaterialTheme.colorScheme.onPrimaryContainer
        HeroMood.WATCHING -> MaterialTheme.colorScheme.primary
        HeroMood.ALERT -> MaterialTheme.colorScheme.error
    }
    val halo = when (mood) {
        HeroMood.SLEEPING -> MaterialTheme.colorScheme.primaryContainer
        HeroMood.WATCHING -> MaterialTheme.colorScheme.secondaryContainer
        HeroMood.ALERT -> MaterialTheme.colorScheme.errorContainer
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (mood == HeroMood.WATCHING) {
            PulsingRings(color = halo, modifier = Modifier.size(size))
        } else {
            Surface(
                modifier = Modifier.size(size * 0.72f),
                shape = CircleShape,
                color = halo,
                content = {},
            )
        }

        Surface(
            modifier = Modifier.size(size * 0.42f),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (mood) {
                    HeroMood.SLEEPING -> Icon(
                        imageVector = IconMoon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(size * 0.2f),
                    )

                    HeroMood.WATCHING -> Icon(
                        imageVector = IconVideoCam,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(size * 0.2f),
                    )

                    HeroMood.ALERT -> Icon(
                        imageVector = IconWarning,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(size * 0.2f),
                    )
                }
            }
        }
    }
}

/**
 * Три кольца, расходящиеся с задержкой друг относительно друга.
 *
 * Рисуются на Canvas, а не набором вложенных Surface: так они не участвуют
 * в компоновке и не вызывают перерасчёт layout на каждом кадре.
 */
@Composable
private fun PulsingRings(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rings")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringProgress",
    )

    Canvas(modifier = modifier) {
        val maxRadius = this.size.minDimension / 2f
        repeat(3) { index ->
            val phase = (progress + index / 3f) % 1f
            val radius = maxRadius * (0.35f + 0.65f * phase)
            drawCircle(
                color = color,
                radius = radius,
                alpha = (1f - phase).coerceIn(0f, 1f) * 0.55f,
            )
        }
    }
}
