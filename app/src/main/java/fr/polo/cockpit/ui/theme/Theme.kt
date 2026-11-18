package fr.polo.cockpit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Cockpit {
    val Bg = Color(0xFF05080D)
    val Panel = Color(0xFF0D131C)
    val PanelHi = Color(0xFF141C28)
    val Stroke = Color(0x22FFFFFF)
    val Text = Color(0xFFE8EEF6)
    val TextDim = Color(0xFF8A97A8)
    val Warn = Color(0xFFFFA23A)
    val Ok = Color(0xFF3DDC97)
    val Danger = Color(0xFFFF4D4F)
    val Sim = Color(0xFFFFB020)
}

val LocalAccent = staticCompositionLocalOf { Color(0xFF0A7CFF) }
val LocalAnimations = staticCompositionLocalOf { true }

@Composable
fun CockpitTheme(accent: Color, animations: Boolean, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent,
        secondary = accent,
        background = Cockpit.Bg,
        surface = Cockpit.Panel,
        onPrimary = Color.White,
        onBackground = Cockpit.Text,
        onSurface = Cockpit.Text,
    )
    val base = TextStyle(fontFamily = FontFamily.SansSerif, color = Cockpit.Text)
    val typo = MaterialTheme.typography.copy(
        displayLarge = base.copy(fontSize = 84.sp, fontWeight = FontWeight.Thin, letterSpacing = (-2).sp),
        headlineMedium = base.copy(fontSize = 26.sp, fontWeight = FontWeight.Light, letterSpacing = 4.sp),
        titleMedium = base.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
        bodyMedium = base.copy(fontSize = 14.sp),
        labelSmall = base.copy(fontSize = 11.sp, letterSpacing = 1.2.sp, color = Cockpit.TextDim),
    )
    CompositionLocalProvider(LocalAccent provides accent, LocalAnimations provides animations) {
        MaterialTheme(colorScheme = scheme, typography = typo, content = content)
    }
}
