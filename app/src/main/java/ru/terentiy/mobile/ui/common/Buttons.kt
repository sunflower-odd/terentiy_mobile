package ru.terentiy.mobile.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText

/**
 * Кнопки-пилюли из макетов: крупные, полностью скруглённые, во всю ширину.
 *
 * Высота 56dp взята с макетов и заодно с запасом перекрывает минимальный
 * размер области нажатия.
 */
private val ButtonHeight = 56.dp

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = CircleShape,
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        ButtonLabel(text = text, loading = loading, indicatorColor = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = CircleShape,
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
    ) {
        ButtonLabel(text = text, loading = loading, indicatorColor = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ButtonLabel(text: String, loading: Boolean, indicatorColor: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = indicatorColor,
            )
        }
        ButtonText(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Подпись кнопки всегда в одну строку. Не помещается — шрифт уменьшается (до 9 sp),
 * а не переносится: на маленьком телефоне кнопки просто мельче.
 */
@Composable
fun ButtonText(text: String, style: TextStyle = LocalTextStyle.current) {
    val maxSize = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    BasicText(
        text = text,
        style = style.copy(color = LocalContentColor.current),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = maxSize, stepSize = 0.5.sp),
    )
}

/** Отступы текстовой кнопки в ряду из нескольких: меньше стандартных, чтобы подписи не мельчили зря. */
val CompactButtonPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
