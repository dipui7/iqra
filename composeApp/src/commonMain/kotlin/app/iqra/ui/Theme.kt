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

/** Colours beyond Material's scheme, modelled on Apple's system palette. */
@Immutable
data class Palette(
    val background: Color,
    val card: Color,
    val text: Color,
    val secondary: Color,
    val separator: Color,
    val accent: Color,
    val accentSoft: Color,
    val correct: Color,
    val correctSoft: Color,
    val wrong: Color,
    val wrongSoft: Color,
    val streak: Color,
    val heart: Color,
)

private val Light = Palette(
    background = Color(0xFFF2F2F7),
    card = Color.White,
    text = Color(0xFF1C1C1E),
    secondary = Color(0xFF8E8E93),
    separator = Color(0xFFE5E5EA),
    accent = Color(0xFF0E8A66),
    accentSoft = Color(0xFFE3F4EE),
    correct = Color(0xFF248A3D),
    correctSoft = Color(0xFFE6F6EA),
    wrong = Color(0xFFD70015),
    wrongSoft = Color(0xFFFDE8EA),
    streak = Color(0xFFFF9500),
    heart = Color(0xFFFF2D55),
)

private val Dark = Palette(
    background = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    text = Color.White,
    secondary = Color(0xFF8E8E93),
    separator = Color(0xFF38383A),
    accent = Color(0xFF30D1A0),
    accentSoft = Color(0xFF12352B),
    correct = Color(0xFF30D158),
    correctSoft = Color(0xFF12301A),
    wrong = Color(0xFFFF453A),
    wrongSoft = Color(0xFF3A1513),
    streak = Color(0xFFFF9F0A),
    heart = Color(0xFFFF375F),
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
        darkColorScheme(primary = p.accent, background = p.background, surface = p.card, onSurface = p.text)
    } else {
        lightColorScheme(primary = p.accent, background = p.background, surface = p.card, onSurface = p.text)
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
