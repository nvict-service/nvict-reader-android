package nl.nvict.reader.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import nl.nvict.reader.data.ThemeMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Kleuren van NVict Reader (Windows): accent #10A2DD, logo-paars #8559A5. */
object NVictColors {
    val Accent = Color(0xFF10A2DD)
    val AccentDark = Color(0xFF0B86B8)
    val Purple = Color(0xFF8559A5)
    val Success = Color(0xFF28A745)
    val Error = Color(0xFFD13438)
    val SelectionLight = Color(0x553366CC)
    val SelectionDark = Color(0x660078D7)
    val SearchHit = Color(0x66FFD54F)
    val SearchHitCurrent = Color(0x99FF9800)
}

private val light = lightColorScheme(
    primary = NVictColors.Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F0FA),
    onPrimaryContainer = Color(0xFF063B4F),
    secondary = NVictColors.Purple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEADFF3),
    onSecondaryContainer = Color(0xFF2E1E3E),
    background = Color(0xFFF3F3F3),
    onBackground = Color(0xFF1C1C1C),
    surface = Color(0xFFF3F3F3),
    onSurface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color(0xFF737373),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDEDED),
    surfaceContainerHighest = Color(0xFFE4E4E4),
    outline = Color(0xFFB0B0B0),
    outlineVariant = Color(0xFFDADADA),
    error = NVictColors.Error,
    onError = Color.White,
)

private val dark = darkColorScheme(
    primary = NVictColors.Accent,
    onPrimary = Color.White,
    primaryContainer = NVictColors.AccentDark,
    onPrimaryContainer = Color.White,
    secondary = NVictColors.Purple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3A2D52),
    onSecondaryContainer = Color(0xFFE6D8FA),
    background = Color(0xFF1E1E1E),
    onBackground = Color(0xFFF0F0F0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFF0F0F0),
    surfaceVariant = Color(0xFF3A3A3A),
    onSurfaceVariant = Color(0xFFA0A0A0),
    surfaceContainer = Color(0xFF2D2D2D),
    surfaceContainerHigh = Color(0xFF363636),
    surfaceContainerHighest = Color(0xFF404040),
    outline = Color(0xFF777777),
    outlineVariant = Color(0xFF3F3F3F),
    error = NVictColors.Error,
    onError = Color.White,
)

/** True als de app (niet per se de telefoon) een donker thema toont. */
val LocalAppDark = staticCompositionLocalOf { false }

@Composable
fun NVictTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val isDark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // Statusbalk- en navigatiebalkiconen passend bij het gekozen thema
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }
    CompositionLocalProvider(LocalAppDark provides isDark) {
        MaterialTheme(colorScheme = if (isDark) dark else light, content = content)
    }
}
