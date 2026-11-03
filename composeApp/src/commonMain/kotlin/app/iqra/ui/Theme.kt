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

/** Colours beyond Material's scheme: warm off-white and light green, in light and dark versions. */
@Immutable
data class Palette(
    val background: Color,
    val card: Color,
    val text: Color,
    val secondary: Color,
    val separator: Color,
    val accent: Color,
    val accentSoft: Color,
    /** Text and icons drawn on [accent] (and on the other strong colours). */
    val onAccent: Color,
    val correct: Color,
    val correctSoft: Color,
    val wrong: Color,
    val wrongSoft: Color,
    val streak: Color,
    val heart: Color,
)

// Warm off-white paper with light green; the deeper green keeps buttons and links readable.
private val Light = Palette(
    background = Color(0xFFF7F5EE),
    card = Color(0xFFFFFEFA),
    text = Color(0xFF1F2A24),
    secondary = Color(0xFF6B7570),
    separator = Color(0xFFE7E5DA),
    accent = Color(0xFF2E8B62),
    accentSoft = Color(0xFFE4F2E7),
    onAccent = Color.White,
    correct = Color(0xFF2E8B57),
    correctSoft = Color(0xFFE3F3E6),
    wrong = Color(0xFFC8323C),
    wrongSoft = Color(0xFFFBE9E7),
    streak = Color(0xFFE8891A),
    heart = Color(0xFFE0405E),
)

// Deep green-charcoal, with the same light green as the accent.
private val Dark = Palette(
    background = Color(0xFF111613),
    card = Color(0xFF1B221E),
    text = Color(0xFFEDEFE9),
    secondary = Color(0xFF8E9792),
    separator = Color(0xFF2E3833),
    accent = Color(0xFF6FD3A0),
    accentSoft = Color(0xFF1E3428),
    onAccent = Color(0xFF111613),
    correct = Color(0xFF5FD37E),
    correctSoft = Color(0xFF16301F),
    wrong = Color(0xFFFF6B5E),
    wrongSoft = Color(0xFF3A1A17),
    streak = Color(0xFFFFA53A),
    heart = Color(0xFFFF5C7A),
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
