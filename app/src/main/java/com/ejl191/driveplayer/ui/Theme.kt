package com.ejl191.driveplayer.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private val colors = darkColorScheme(
    primary = Retro.LcdGreen,
    onPrimary = Color.Black,
    primaryContainer = Retro.PlSelected,
    onPrimaryContainer = Color.White,
    secondary = Retro.TitleStripe,
    onSecondary = Color.Black,
    background = Retro.Chrome,
    onBackground = Retro.Label,
    surface = Retro.Chrome,
    onSurface = Retro.Label,
    surfaceVariant = Retro.ChromeDark,
    onSurfaceVariant = Retro.Label,
    surfaceContainerLowest = Retro.ChromeDark,
    surfaceContainerLow = Retro.Chrome,
    surfaceContainer = Retro.Chrome,
    surfaceContainerHigh = Retro.Chrome,
    surfaceContainerHighest = Retro.Chrome,
    outline = Retro.BevelLight,
    error = Retro.Error,
)

private fun Typography.monospace(): Typography {
    fun TextStyle.m() = copy(fontFamily = FontFamily.Monospace)
    return Typography(
        displayLarge = displayLarge.m(), displayMedium = displayMedium.m(), displaySmall = displaySmall.m(),
        headlineLarge = headlineLarge.m(), headlineMedium = headlineMedium.m(), headlineSmall = headlineSmall.m(),
        titleLarge = titleLarge.m(), titleMedium = titleMedium.m(), titleSmall = titleSmall.m(),
        bodyLarge = bodyLarge.m(), bodyMedium = bodyMedium.m(), bodySmall = bodySmall.m(),
        labelLarge = labelLarge.m(), labelMedium = labelMedium.m(), labelSmall = labelSmall.m(),
    )
}

private val square = RoundedCornerShape(0.dp)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = Typography().monospace(),
        shapes = Shapes(square, square, square, square, square),
        content = content,
    )
}

fun formatTime(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
