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

/** Как выглядит правая отметка в строке состояния. */
enum class StatusMark { OK, BAD, UNKNOWN }

/**
 * Строка состояния из макетов: иконка в цветном кружке, подпись, отметка справа.
 *
 * Три варианта отметки, а не два: «неизвестно» намеренно отличается и от нормы,
 * и от отказа — агент, который ни разу не выходил на связь, не то же самое, что
 * агент с выключенной камерой.
 */
@Composable
fun StatusRow(
    icon: ImageVector,
    label: String,
    mark: StatusMark,
    modifier: Modifier = Modifier,
) {
    val markColor = when (mark) {
        StatusMark.OK -> MaterialTheme.colorScheme.secondary
        StatusMark.BAD -> MaterialTheme.colorScheme.error
        StatusMark.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconBadge(icon = icon, container = MaterialTheme.colorScheme.primaryContainer, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Surface(shape = CircleShape, color = markColor, modifier = Modifier.size(26.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (mark) {
                            StatusMark.OK -> IconCheck
                            StatusMark.BAD -> IconClose
                            StatusMark.UNKNOWN -> IconWarning
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

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
