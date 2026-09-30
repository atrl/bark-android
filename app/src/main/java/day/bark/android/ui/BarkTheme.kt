package day.bark.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object BarkPalette {
    val Ink = Color(0xFF172B2A)
    val Muted = Color(0xFF748580)
    val Teal = Color(0xFF087F70)
    val SoftTeal = Color(0xFFEAF6F1)
    val Background = Color(0xFFF5F7F6)
    val Border = Color(0xFFE5ECE8)
}

@Composable
fun BarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BarkPalette.Teal,
            onPrimary = Color.White,
            primaryContainer = BarkPalette.SoftTeal,
            onPrimaryContainer = BarkPalette.Teal,
            secondary = Color(0xFF526F68),
            onSecondary = Color.White,
            secondaryContainer = BarkPalette.SoftTeal,
            onSecondaryContainer = BarkPalette.Teal,
            background = BarkPalette.Background,
            onBackground = BarkPalette.Ink,
            surface = Color.White,
            onSurface = BarkPalette.Ink,
            surfaceTint = BarkPalette.Teal,
            surfaceBright = Color.White,
            surfaceDim = Color(0xFFE7EEEA),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFFAFCFB),
            surfaceContainer = Color(0xFFF2F6F3),
            surfaceContainerHigh = Color(0xFFEDF4F0),
            surfaceContainerHighest = Color(0xFFE6EFE9),
            surfaceVariant = Color(0xFFEDF2EF),
            onSurfaceVariant = BarkPalette.Muted,
            outline = Color(0xFFC7D6CF),
            outlineVariant = BarkPalette.Border,
        ),
        content = content,
    )
}
