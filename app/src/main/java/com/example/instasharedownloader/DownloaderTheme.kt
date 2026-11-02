package com.example.instasharedownloader

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1D6F8F), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEFF5), onPrimaryContainer = Color(0xFF084358),
    secondary = Color(0xFF52636A), background = Color(0xFFFAFBFC),
    surface = Color(0xFFFAFBFC), surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF0F3F5), surfaceContainerHigh = Color(0xFFE9EEF1),
    onSurface = Color(0xFF192126), onSurfaceVariant = Color(0xFF59666E),
    outline = Color(0xFF74828A), outlineVariant = Color(0xFFD9E0E4),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF94D2EB), onPrimary = Color(0xFF003649),
    primaryContainer = Color(0xFF124D65), onPrimaryContainer = Color(0xFFCAEAF7),
    secondary = Color(0xFFB5C9D3), background = Color(0xFF101518),
    surface = Color(0xFF101518), surfaceContainerLow = Color(0xFF181E22),
    surfaceContainer = Color(0xFF20282D), surfaceContainerHigh = Color(0xFF2B343A),
    onSurface = Color(0xFFE6ECEF), onSurfaceVariant = Color(0xFFAAB8C1),
    outline = Color(0xFF8A9AA4), outlineVariant = Color(0xFF3C484F),
)

@Composable
internal fun DownloaderTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
