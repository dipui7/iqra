package app.iqra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.iqra.data.AyahRef
import app.iqra.data.QuranMeta
import app.iqra.data.SurahInfo
import app.iqra.engine.READER_TEXT_DEFAULT
import app.iqra.engine.READER_TEXT_SIZES
import androidx.compose.ui.unit.sp
import app.iqra.data.TajweedGroup
import app.iqra.data.TajweedMark
import app.iqra.data.TajweedMarks
import app.iqra.data.Translation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import app.iqra.data.QuranText
import app.iqra.data.arabicDigits
import app.iqra.engine.RepeatPlan
import app.iqra.nowMillis
import app.iqra.playAudio
import app.iqra.stopAudio

/** The list of surahs to open in the reader (the "Quran" tab on the home screen). */
@Composable
fun SurahList(meta: QuranMeta, onOpen: (Int) -> Unit) {
    // All 114 surahs, under the juz each one starts in.
    meta.surahs.groupBy { meta.juzOf(it.number) }.forEach { (juz, surahs) ->
        Text(
            "JUZ $juz",
            style = MaterialTheme.typography.labelMedium, color = palette.secondary,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
        )
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.card)) {
            surahs.forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = palette.separator)
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(s.number) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AyahNumber(s.number, highlighted = false)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.bodyLarge, color = palette.text)
                        Text(
                            "${s.meaning} · ${s.ayahCount} ayat · ${if (s.meccan) "Meccan" else "Medinan"}",
                            style = MaterialTheme.typography.bodySmall, color = palette.secondary,
                        )
                    }
                    // The surah's Arabic name from Tanzil's metadata (not Quranic text).
                    Text(s.arabicName, style = arabicStyle(22, lineFactor = 1.6).copy(color = palette.accent, textDirection = TextDirection.Rtl))
                }
            }
        }
    }
}

/**
 * Read a surah while it is recited: the ayah being played is highlighted and kept in view.
 * Tap an ayah to play from there; the controls repeat each ayah and loop a range of ayat,
 * which is how most people memorise.
 */
@Composable
fun ReaderScreen(
    quran: QuranText,
    info: SurahInfo,
    onBack: () -> Unit,
    /** Tajweed marks and translation; null while they are still loading. */
    extras: ReaderExtras? = null,
    showTajweed: Boolean = true,
    showTranslation: Boolean = true,
    onShowTajweed: (Boolean) -> Unit = {},
    onShowTranslation: (Boolean) -> Unit = {},
    textSize: Int = READER_TEXT_DEFAULT,
    onTextSize: (Int) -> Unit = {},
) {
    val surah = info.number
    val ayat = remember(surah) { quran.surah(surah) }
    val count = ayat.size
    var from by remember { mutableIntStateOf(1) }
    var to by remember { mutableIntStateOf(count) }
    var perAyah by remember { mutableIntStateOf(1) }
    var loops by remember { mutableIntStateOf(1) }

    var position by remember { mutableStateOf<RepeatPlan.Position?>(null) }
    var playing by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    // Bumped whenever playback is started or stopped by the learner, so a late "done" from an
    // earlier ayah doesn't move things on.
    var token by remember { mutableIntStateOf(0) }

    fun stop() {
        token++
        playing = false
        stopAudio()
    }

    fun play(p: RepeatPlan.Position) {
        val mine = ++token
        position = p
        playing = true
        problem = null
        val started = nowMillis()
        playAudio(AyahRef(surah, p.ayah).audioUrl) {
            if (mine != token || !playing) return@playAudio
            // A "finished" right away means the audio couldn't be fetched (usually no internet):
            // stop rather than race through every ayah.
            if (nowMillis() - started < 400) {
                playing = false
                problem = "Couldn't play the recitation. Check your internet connection — ayat you've heard before play offline."
                return@playAudio
            }
            // Read the settings now, so changing repeats mid-way takes effect.
            val next = RepeatPlan(from, to, perAyah, loops).next(p)
            if (next != null) play(next) else { playing = false; position = null }
        }
    }

    DisposableEffect(surah) { onDispose { stop() } }

    val list = rememberLazyListState()
    val bismillah = quran.bismillah(surah)
    val headerItems = if (bismillah != null) 2 else 1
    LaunchedEffect(position?.ayah) {
        position?.let { list.animateScrollToItem(headerItems + it.ayah - 1) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { stop(); onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = palette.accent) }
            Column(Modifier.weight(1f)) {
                Text(info.name, style = MaterialTheme.typography.titleLarge, color = palette.text)
                Text("Surah $surah · $count ayat", style = MaterialTheme.typography.bodySmall, color = palette.secondary)
            }
            Text(quran.suraName(surah), style = arabicStyle(24, lineFactor = 1.6).copy(color = palette.accent, textDirection = TextDirection.Rtl))
            Spacer(Modifier.width(12.dp))
        }

        LazyColumn(
            Modifier.weight(1f),
            state = list,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Tap an ayah to hear it. Set repeats below to memorise.",
                    style = MaterialTheme.typography.bodySmall, color = palette.secondary,
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Toggle("Tajweed", showTajweed) { onShowTajweed(!showTajweed) }
                    Toggle("Translation", showTranslation) { onShowTranslation(!showTranslation) }
                    Spacer(Modifier.weight(1f))
                    TextSizeControl(textSize, onTextSize)
                }
                if (showTajweed) TajweedKey()
            }
            if (bismillah != null) {
                item {
                    Text(
                        bismillah,
                        style = arabicStyle(24).copy(textDirection = TextDirection.Rtl, color = palette.secondary),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            itemsIndexed(ayat, key = { _, a -> a.first.aya }) { _, (ref, text) ->
                val current = position?.ayah == ref.aya
                val inRange = ref.aya in from..to
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (current) palette.accentSoft else palette.card)
                        .border(2.dp, if (current) palette.accent else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable {
                            // Tapping outside the range plays just that ayah, with the same repeats.
                            if (ref.aya !in from..to) { from = ref.aya; to = ref.aya }
                            play(RepeatPlan(from, to, perAyah, loops).start(ref.aya))
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AyahNumber(ref.aya, highlighted = current, dimmed = !inRange)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        val marks = if (showTajweed) extras?.tajweed?.of(ref).orEmpty() else emptyList()
                        Text(
                            colouredAyah(text, marks),
                            style = arabicStyle(textSize, lineFactor = 2.0)
                                .copy(textDirection = TextDirection.Rtl, color = if (inRange) palette.text else palette.secondary),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (showTranslation) extras?.translation?.of(ref)?.let {
                            // The translation grows a little with the Arabic, from 15 to 19 sp.
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = (15 + 4 * (textSize - READER_TEXT_SIZES.first) / (READER_TEXT_SIZES.last - READER_TEXT_SIZES.first)).sp,
                                    lineHeight = (22 + 6 * (textSize - READER_TEXT_SIZES.first) / (READER_TEXT_SIZES.last - READER_TEXT_SIZES.first)).sp,
                                ),
                                color = palette.secondary, modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        Controls(
            count = count, from = from, to = to, perAyah = perAyah, loops = loops,
            playing = playing, position = position, problem = problem,
            onRange = { f, t -> from = f; to = t; if (playing) stop() },
            onPerAyah = { perAyah = it },
            onLoops = { loops = it },
            onPlayPause = {
                if (playing) stop()
                else play(position?.takeIf { it.ayah in from..to }?.copy(rep = 0) ?: RepeatPlan(from, to, perAyah, loops).start())
            },
        )
    }
}

@Composable
private fun Controls(
    count: Int, from: Int, to: Int, perAyah: Int, loops: Int,
    playing: Boolean, position: RepeatPlan.Position?, problem: String?,
    onRange: (Int, Int) -> Unit, onPerAyah: (Int) -> Unit, onLoops: (Int) -> Unit, onPlayPause: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(palette.card).padding(horizontal = 16.dp, vertical = 10.dp)) {
        problem?.let { Text(it, color = palette.wrong, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ayat", color = palette.secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(92.dp))
            Stepper(from, 1..to) { onRange(it, to) }
            Text("to", color = palette.secondary, modifier = Modifier.padding(horizontal = 8.dp))
            Stepper(to, from..count) { onRange(from, it) }
            Spacer(Modifier.weight(1f))
            if (from != 1 || to != count) Text("All", color = palette.accent, modifier = Modifier.clickable { onRange(1, count) }.padding(6.dp))
        }
        ChipRow("Each ayah", listOf(1, 3, 5, 10), perAyah, { "$it×" }, onPerAyah)
        ChipRow("Whole range", listOf(1, 3, 0), loops, { if (it == 0) "∞" else "$it×" }, onLoops)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(palette.accentFill).clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", tint = palette.onAccentFill, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                when {
                    position != null && playing -> buildString {
                        append("Ayah ${position.ayah}")
                        if (perAyah > 1) append(" · ${position.rep + 1} of $perAyah")
                        if (loops != 1) append(" · round ${position.loop + 1}${if (loops > 1) " of $loops" else ""}")
                    }
                    from == to -> "Play ayah $from"
                    else -> "Play ayat $from–$to"
                },
                color = palette.text, style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun Stepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(palette.background), verticalAlignment = Alignment.CenterVertically) {
        Text("−", color = if (value > range.first) palette.accent else palette.separator, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clickable(enabled = value > range.first) { onChange(value - 1) }.padding(horizontal = 12.dp, vertical = 4.dp))
        Text("$value", color = palette.text, style = MaterialTheme.typography.titleMedium)
        Text("+", color = if (value < range.last) palette.accent else palette.separator, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clickable(enabled = value < range.last) { onChange(value + 1) }.padding(horizontal = 12.dp, vertical = 4.dp))
    }
}

@Composable
private fun ChipRow(label: String, values: List<Int>, selected: Int, text: (Int) -> String, onSelect: (Int) -> Unit) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = palette.secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(92.dp))
        values.forEach { v ->
            val on = v == selected
            Text(
                text(v),
                color = if (on) palette.onAccentFill else palette.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (on) palette.accentFill else palette.background)
                    .clickable { onSelect(v) }.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

/** A round ayah-number badge in Arabic-Indic digits (page furniture, not part of the text). */
@Composable
private fun AyahNumber(n: Int, highlighted: Boolean, dimmed: Boolean = false) {
    Box(
        Modifier.size(36.dp).clip(CircleShape)
            .background(if (highlighted) palette.accentFill else palette.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            arabicDigits(n),
            style = arabicStyle(16, lineFactor = 1.3).copy(
                color = when {
                    highlighted -> palette.onAccentFill
                    dimmed -> palette.secondary
                    else -> palette.accent
                },
            ),
        )
    }
}

/** Data the reader adds to the Quran text: tajweed colours and a translation. */
class ReaderExtras(val tajweed: TajweedMarks, val translation: Translation)

/** The ayah exactly as written, with tajweed letters coloured (colour only; the text is unchanged). */
@Composable
private fun colouredAyah(text: String, marks: List<TajweedMark>): AnnotatedString {
    if (marks.isEmpty()) return AnnotatedString(text)
    val dark = isSystemInDarkTheme()
    return buildAnnotatedString {
        append(text)
        for (m in marks) if (m.end <= text.length) addStyle(SpanStyle(color = tajweedColour(m.group, dark)), m.start, m.end)
    }
}

fun tajweedColour(group: TajweedGroup, dark: Boolean): Color = when (group) {
    // Vivid enough to stand out on thin strokes, and distinct from each other.
    TajweedGroup.Madd -> if (dark) Color(0xFFFF6B6B) else Color(0xFFD62839)
    TajweedGroup.Ghunnah -> if (dark) Color(0xFFFFB347) else Color(0xFFEF7B00)
    TajweedGroup.Ikhfa -> if (dark) Color(0xFFD291FF) else Color(0xFF9C27B0)
    TajweedGroup.Idghaam -> if (dark) Color(0xFF6EE7A0) else Color(0xFF14A04B)
    TajweedGroup.Iqlab -> if (dark) Color(0xFF4DD0E1) else Color(0xFF0097A7)
    TajweedGroup.Qalqalah -> if (dark) Color(0xFF64B5F6) else Color(0xFF1565C0)
    TajweedGroup.Silent -> if (dark) Color(0xFF808782) else Color(0xFFADADA6)
}

/** What each colour means; tap a rule to read what it is. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TajweedKey() {
    val dark = isSystemInDarkTheme()
    var open by remember { mutableStateOf<TajweedGroup?>(null) }
    FlowRow(
        Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TajweedGroup.entries.forEach { g ->
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = if (open == g) null else g }.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(tajweedColour(g, dark)))
                Spacer(Modifier.width(4.dp))
                Text(g.label, style = MaterialTheme.typography.bodySmall, color = palette.text)
            }
        }
    }
    open?.let { Text("${it.label}: ${it.hint}", style = MaterialTheme.typography.bodySmall, color = palette.secondary, modifier = Modifier.padding(top = 2.dp)) }
}

/** A− / A+ buttons for the size of the Arabic. */
@Composable
private fun TextSizeControl(size: Int, onChange: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(palette.card), verticalAlignment = Alignment.CenterVertically) {
        val smaller = size > READER_TEXT_SIZES.first
        val bigger = size < READER_TEXT_SIZES.last
        Text(
            "A", fontSize = 14.sp, color = if (smaller) palette.accent else palette.separator,
            modifier = Modifier.clickable(enabled = smaller) { onChange(size - 2) }.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Box(Modifier.size(1.dp, 18.dp).background(palette.separator))
        Text(
            "A", fontSize = 20.sp, color = if (bigger) palette.accent else palette.separator,
            modifier = Modifier.clickable(enabled = bigger) { onChange(size + 2) }.padding(horizontal = 12.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (on) palette.onAccentFill else palette.text,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) palette.accentFill else palette.card)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
