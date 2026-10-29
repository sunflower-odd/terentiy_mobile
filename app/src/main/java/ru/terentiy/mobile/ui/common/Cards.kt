package ru.terentiy.mobile.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Круглая подложка под иконку — повторяющийся мотив всех макетов. */
@Composable
fun IconBadge(
    icon: ImageVector,
    container: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
) {
    Surface(shape = CircleShape, color = container, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

/**
 * Карточка тревоги о возможном падении.
 *
 * Формулировка приходит с сервера и намеренно нейтральна: система сообщает, что
 * эпизод требует проверки, а не утверждает, что человек упал. Подтверждать
 * или опровергать это — дело человека, открывшего карточку.
 */
@Composable
fun AlertCard(
    title: String,
    statement: String,
    time: String,
    onOpen: () -> Unit,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconBadge(
                    icon = IconWarning,
                    container = MaterialTheme.colorScheme.onError.copy(alpha = 0.18f),
                    tint = MaterialTheme.colorScheme.onError,
                    size = 68.dp,
                )
                Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    text = statement,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Text(text = time, style = MaterialTheme.typography.labelMedium)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onError,
                    contentColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.TextButton(
                        onClick = onOpen,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(ru.terentiy.mobile.R.string.alert_open_event),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                ) {
                    Icon(
                        imageVector = IconClose,
                        contentDescription = androidx.compose.ui.res.stringResource(ru.terentiy.mobile.R.string.common_close),
                        tint = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        }
    }
}

/** Полоса с сообщением: ошибка, предупреждение или нейтральная подсказка. */
@Composable
fun MessageBanner(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.errorContainer,
    content: Color = MaterialTheme.colorScheme.onErrorContainer,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
