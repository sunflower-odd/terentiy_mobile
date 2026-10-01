package ru.terentiy.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = GreenDeep,
    onPrimary = SurfaceWhite,
    primaryContainer = GreenMint,
    onPrimaryContainer = GreenDeepDark,
    secondary = GreenGrass,
    onSecondary = SurfaceWhite,
    secondaryContainer = GreenGrassSoft,
    onSecondaryContainer = GreenDeepDark,
    tertiary = GreenDeepDark,
    onTertiary = SurfaceWhite,
    tertiaryContainer = GreenMintStrong,
    onTertiaryContainer = GreenDeepDark,
    background = CreamBackground,
    onBackground = InkNavy,
    surface = SurfaceWhite,
    onSurface = InkNavy,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = InkMuted,
    error = CoralAlert,
    onError = SurfaceWhite,
    errorContainer = CoralAlertSoft,
    onErrorContainer = InkNavy,
    outline = GreenMintStrong,
    outlineVariant = SurfaceMuted,
)

private val DarkColors = darkColorScheme(
    primary = GreenDeepOnDark,
    onPrimary = BackgroundDark,
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = InkOnDark,
    secondary = GreenGrass,
    onSecondary = BackgroundDark,
    secondaryContainer = GreenContainerDark,
    onSecondaryContainer = InkOnDark,
    tertiary = GreenDeepOnDark,
    onTertiary = BackgroundDark,
    tertiaryContainer = GreenContainerDark,
    onTertiaryContainer = InkOnDark,
    background = BackgroundDark,
    onBackground = InkOnDark,
    surface = SurfaceDark,
    onSurface = InkOnDark,
    surfaceVariant = SurfaceMutedDark,
    onSurfaceVariant = InkMutedOnDark,
    error = CoralOnDark,
    onError = BackgroundDark,
    errorContainer = CoralContainerDark,
    onErrorContainer = InkOnDark,
    outline = GreenContainerDark,
    outlineVariant = SurfaceMutedDark,
)

/**
 * Тема приложения.
 *
 * Material You (динамические цвета по обоям устройства) намеренно **не**
 * используется: он подменил бы фирменный зелёный произвольной палитрой
 * пользователя, и от визуального языка из макетов ничего бы не осталось.
 * Это осознанный отказ от системной подстройки в пользу узнаваемости.
 */
@Composable
fun TerentiyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = TerentiyTypography,
        shapes = TerentiyShapes,
        content = content,
    )
}
