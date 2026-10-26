package io.github.jpsutton.inkstash.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.jpsutton.inkstash.R

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** Design tokens from the mockups (Screens.html) that Material's scheme has no slot for. */
data class InkColors(
    val bg: Color, val surface: Color, val surface2: Color, val text: Color, val muted: Color,
    val border: Color, val divider: Color, val dash: Color, val rail: Color, val rail2: Color, val railText: Color,
    val hero: Color, val hero2: Color, val primary: Color, val onPrimary: Color,
    val accent: Color, val onAccent: Color, val danger: Color, val onDanger: Color,
    val scrim: Color, val shadow: Color,
    val okBg: Color, val okFg: Color, val warnBg: Color, val warnFg: Color,
    val badBg: Color, val badFg: Color, val infoBg: Color, val infoFg: Color,
)

private val Light = InkColors(
    bg = Color(0xFFF5F4FB), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF1EFFA), text = Color(0xFF1B1A3A),
    muted = Color(0xFF5A5878), border = Color(0xFFE3E1F0), divider = Color(0xFFF0EEF8), dash = Color(0xFFB9B5D6),
    rail = Color(0xFF1E1B4B), rail2 = Color(0xFF2E2A6B), railText = Color(0xFFC9C6F0),
    hero = Color(0xFF1E1B4B), hero2 = Color(0xFF2E2A6B), primary = Color(0xFF1E1B4B), onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFFF2B33D), onAccent = Color(0xFF1B1A3A), danger = Color(0xFFB3261E), onDanger = Color(0xFFFFFFFF),
    scrim = Color(0x731B1A3A), shadow = Color(0x401E1B4B),
    okBg = Color(0xFFE4F4EA), okFg = Color(0xFF1E6B43), warnBg = Color(0xFFFDF3DC), warnFg = Color(0xFF7A5300),
    badBg = Color(0xFFFBE6E6), badFg = Color(0xFF9B2C2C), infoBg = Color(0xFFE3EEFA), infoFg = Color(0xFF1D4A80),
)

private val Dark = InkColors(
    bg = Color(0xFF121122), surface = Color(0xFF1D1C33), surface2 = Color(0xFF2A2846), text = Color(0xFFF1F0FA),
    muted = Color(0xFFB4B1D0), border = Color(0xFF34325A), divider = Color(0xFF2A2846), dash = Color(0xFF5A5785),
    rail = Color(0xFF0B0A18), rail2 = Color(0xFF1F1D3D), railText = Color(0xFFB4B1D0),
    hero = Color(0xFF2A2760), hero2 = Color(0xFF3A3678), primary = Color(0xFFC9C6F0), onPrimary = Color(0xFF121122),
    accent = Color(0xFFF2B33D), onAccent = Color(0xFF1B1A3A), danger = Color(0xFFFF8A80), onDanger = Color(0xFF2A0A08),
    scrim = Color(0x99000000), shadow = Color(0x80000000),
    okBg = Color(0xFF17352A), okFg = Color(0xFF8FD9B0), warnBg = Color(0xFF3A2E12), warnFg = Color(0xFFF5CF7A),
    badBg = Color(0xFF3D1D20), badFg = Color(0xFFF4A3A3), infoBg = Color(0xFF172B45), infoFg = Color(0xFF9CC4F2),
)

val LocalInk = staticCompositionLocalOf { Light }

object Ink {
    val colors: InkColors @Composable @ReadOnlyComposable get() = LocalInk.current
}

/** The six Lorcana inks, as in the mockups. `on` is the readable text colour on top of `color`. */
enum class InkColor(val label: String, val color: Color, val on: Color) {
    Amber("Amber", Color(0xFFE9A825), Color(0xFF1B1A3A)),
    Amethyst("Amethyst", Color(0xFF7E4FA8), Color.White),
    Emerald("Emerald", Color(0xFF277A4D), Color.White),
    Ruby("Ruby", Color(0xFFC23B3B), Color.White),
    Sapphire("Sapphire", Color(0xFF2F6FB8), Color.White),
    Steel("Steel", Color(0xFF5F6B7A), Color.White);

    companion object {
        fun of(ink: String): InkColor = entries.firstOrNull { it.label.equals(ink, ignoreCase = true) } ?: Steel
    }
}

private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Nunito = FontFamily(listOf(500, 600, 700, 800).map { variable(R.font.nunito, it) })
val Fredoka = FontFamily(listOf(500, 600).map { variable(R.font.fredoka, it) })

private val AppTypography = Typography().let { t ->
    fun TextStyle.nunito() = copy(fontFamily = Nunito)
    Typography(
        displayLarge = t.displayLarge.nunito(), displayMedium = t.displayMedium.nunito(), displaySmall = t.displaySmall.nunito(),
        headlineLarge = t.headlineLarge.copy(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.copy(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.copy(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
        titleMedium = t.titleMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
        titleSmall = t.titleSmall.nunito(),
        bodyLarge = t.bodyLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.SemiBold),
        bodyMedium = t.bodyMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.SemiBold),
        bodySmall = t.bodySmall.copy(fontFamily = Nunito, fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
        labelMedium = t.labelMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
        labelSmall = t.labelSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
    )
}

@Composable
fun InkstashTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val c = if (dark) Dark else Light
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = c.primary, onPrimary = c.onPrimary, secondary = c.accent, onSecondary = c.onAccent,
        background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.surface2, onSurfaceVariant = c.muted, outline = c.border,
        error = c.danger, onError = c.onDanger, scrim = c.scrim,
    )
    CompositionLocalProvider(LocalInk provides c) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
