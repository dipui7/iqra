package app.iqra.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.iqra.resources.Res
import app.iqra.resources.amiri_quran
import org.jetbrains.compose.resources.Font

/**
 * Colours beyond Material's scheme: a calm ivory with light green, in light and dark versions.
 * Filled shapes (buttons, chips, checks) use the light [accentFill] with dark [onAccentFill]
 * text; text, icons and lines use the deeper [accent] so they stay readable.
 */
@Immutable
data class Palette(
    val background: Color,
    val card: Color,
    val text: Color,
    val secondary: Color,
    val separator: Color,
    /** Sage green for text, icons, outlines and progress. */
    val accent: Color,
    /** Light green for filled buttons, chips and badges. */
    val accentFill: Color,
    /** Text and icons on [accentFill]. */
    val onAccentFill: Color,
    val accentSoft: Color,
    /** Text and icons on the strong colours (correct, wrong, streak, heart). */
    val onAccent: Color,
    val correct: Color,
    val correctSoft: Color,
    val wrong: Color,
    val wrongSoft: Color,
    val streak: Color,
    val heart: Color,
)

// Warm ivory paper, cream cards, soft charcoal text and light sage green.
private val Light = Palette(
    background = Color(0xFFF8F4EC),
    card = Color(0xFFFFFDF8),
    text = Color(0xFF2E2D28),
    secondary = Color(0xFF7D786E),
    separator = Color(0xFFEBE4D7),
    accent = Color(0xFF4A8F69),
    accentFill = Color(0xFFB3DCC3),
    onAccentFill = Color(0xFF1D4632),
    accentSoft = Color(0xFFE8F3EA),
    onAccent = Color.White,
    correct = Color(0xFF3F9160),
    correctSoft = Color(0xFFE6F3E8),
    wrong = Color(0xFFC4505A),
    wrongSoft = Color(0xFFF9EAE6),
    streak = Color(0xFFE39A3B),
    heart = Color(0xFFDB5C72),
)

// Deep green-charcoal, with the same light green.
private val Dark = Palette(
    background = Color(0xFF121714),
    card = Color(0xFF1C231F),
    text = Color(0xFFECEEE7),
    secondary = Color(0xFF959C96),
    separator = Color(0xFF2F3934),
    accent = Color(0xFF93D3AE),
    accentFill = Color(0xFF93D3AE),
    onAccentFill = Color(0xFF10261A),
    accentSoft = Color(0xFF1F3529),
    onAccent = Color(0xFF121714),
    correct = Color(0xFF6CCB8A),
    correctSoft = Color(0xFF17301F),
    wrong = Color(0xFFFF7B70),
    wrongSoft = Color(0xFF3A1B18),
    streak = Color(0xFFFFB04D),
    heart = Color(0xFFFF6F89),
)

val LocalPalette = staticCompositionLocalOf { Light }

val palette: Palette @Composable get() = LocalPalette.current

/** Amiri Quran (SIL Open Font License), designed for Quranic text with full vowel marks. */
val ArabicFont: FontFamily @Composable get() = FontFamily(Font(Res.font.amiri_quran))

@Composable
fun arabicStyle(size: Int, lineFactor: Double = 1.9) = TextStyle(
    fontFamily = ArabicFont,
    fontSize = size.sp,
    lineHeight = (size * lineFactor).sp,
    color = palette.text,
)

@Composable
fun IqraTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(primary = p.accent, onPrimary = p.onAccent, background = p.background, surface = p.card, onSurface = p.text)
    } else {
        lightColorScheme(primary = p.accent, onPrimary = p.onAccent, background = p.background, surface = p.card, onSurface = p.text)
    }
    val base = Typography()
    val typography = Typography(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = 0.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 15.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
