package app.iqra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.VolunteerActivism
import app.iqra.data.Course
import app.iqra.data.CourseUnit
import app.iqra.data.Exams
import app.iqra.data.Lesson
import app.iqra.data.Track
import app.iqra.engine.MAX_HEARTS
import app.iqra.engine.UNLIMITED_HEARTS_PRICE
import app.iqra.engine.Progress
import app.iqra.nowMillis
import app.iqra.todayEpochDay

@Composable
fun HomeScreen(
    progress: Progress,
    onStart: (String) -> Unit,
    onAbout: () -> Unit,
    onPractice: () -> Unit = {},
    onSettings: () -> Unit = {},
    onSupport: () -> Unit = {},
    onBuyUnlimited: () -> Unit = {},
    /** Content of the "Quran" tab (the surah list), supplied by the app since it needs the text. */
    quranTab: @Composable () -> Unit = {},
    track: Track = Track.Reading,
    onTrackChange: (Track) -> Unit = {},
    onBadges: () -> Unit = {},
) {
    var noHearts by remember { mutableStateOf(false) }
    val today = todayEpochDay()
    val start = { id: String ->
        // Exams use no hearts, so they can always be taken.
        if (progress.hearts > 0 || progress.hasUnlimitedHearts(today) || Exams.isExam(id)) onStart(id) else noHearts = true
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Header(progress, onAbout, onSettings, onSupport, onBadges) }
        item { CoinsCard(progress, today, onBuyUnlimited) }
        // The next exam of the tab being shown (the Quran tab has none).
        if (track != Track.Quran) Course.nextExam(track, progress.passedExams)?.let { (unit, exam) ->
            val open = Course.isUnlocked(exam.id, progress.completed)
            item { NextExamCard(unit, exam, open) { start(exam.id) } }
        }
        if (progress.skills.isNotEmpty()) item { PracticeCard(progress.dueCount(today), progress.hearts < MAX_HEARTS, onPractice) }
        item { TrackSwitch(track, onTrackChange) }
        if (track == Track.Hifz) {
            item {
                Text(
                    "Memorise the short surahs of Juz ʿAmma, one ayah at a time. Every surah is open — start with one you don't know yet.",
                    color = palette.secondary, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (track == Track.Quran) {
            item { quranTab() }
        } else {
            items(if (track == Track.Reading) Course.units else Course.hifzUnits, key = { it.id }) { unit -> UnitCard(unit, progress, start) }
        }
        item {
            Text(
                "Quran text: Tanzil Project (tanzil.net) · Recitation: Mishary Alafasy via EveryAyah",
                style = MaterialTheme.typography.bodySmall,
                color = palette.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }

    if (noHearts) {
        val minutes = ((progress.msUntilNextHeart(nowMillis()) ?: 0) / 60_000) + 1
        AlertDialog(
            onDismissRequest = { noHearts = false },
            confirmButton = {
                if (progress.skills.isNotEmpty()) {
                    TextButton({ noHearts = false; onPractice() }) { Text("Practise") }
                } else {
                    TextButton({ noHearts = false }) { Text("OK") }
                }
            },
            dismissButton = if (progress.skills.isNotEmpty()) ({ TextButton({ noHearts = false }) { Text("Later") } }) else null,
            title = { Text("Out of hearts") },
            text = {
                Text(
                    "Your next heart arrives in about $minutes min." +
                        if (progress.skills.isNotEmpty()) " Or practise what you've learned to earn one now — practice costs no hearts." else "",
                )
            },
        )
    }
}

@Composable
private fun Header(progress: Progress, onAbout: () -> Unit, onSettings: () -> Unit, onSupport: () -> Unit, onBadges: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AlifUp", style = MaterialTheme.typography.displaySmall, color = palette.text)
                Text("Learn to read the Quran, letter by letter", color = palette.secondary)
            }
            IconButton(onClick = onBadges) { Icon(Icons.Rounded.EmojiEvents, "Badges", tint = palette.streak) }
            IconButton(onClick = onSupport) { Icon(Icons.Rounded.VolunteerActivism, "Help people in need", tint = palette.secondary) }
            IconButton(onClick = onAbout) { Icon(Icons.Rounded.Info, "Sources and licences", tint = palette.secondary) }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings", tint = palette.secondary) }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(Icons.Rounded.LocalFireDepartment, palette.streak, "${progress.currentStreak(todayEpochDay())}", "day streak", Modifier.weight(1f))
            Stat(Icons.Rounded.Bolt, palette.accent, "${progress.xp}", "XP", Modifier.weight(1f))
            if (progress.hasUnlimitedHearts(todayEpochDay())) {
                Stat(Icons.Rounded.Favorite, palette.heart, "∞", "hearts", Modifier.weight(1f))
            } else {
                Stat(Icons.Rounded.Favorite, palette.heart, "${progress.hearts}/$MAX_HEARTS", heartsLabel(progress), Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        DailyGoal(progress.xpOn(todayEpochDay()), progress.dailyGoal)
    }
}

@Composable
private fun TrackSwitch(track: Track, onChange: (Track) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.separator).padding(3.dp)) {
        listOf(Track.Reading to "Learn to read", Track.Hifz to "Memorise", Track.Quran to "Quran").forEach { (t, label) ->
            val selected = t == track
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (selected) palette.card else Color.Transparent)
                    .clickable { onChange(t) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = if (selected) palette.text else palette.secondary)
            }
        }
    }
}

@Composable
private fun DailyGoal(xp: Int, goal: Int) {
    val done = xp >= goal
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.card).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Daily goal", style = MaterialTheme.typography.bodyMedium, color = palette.text, modifier = Modifier.weight(1f))
            Text(
                if (done) "Done · $xp XP" else "$xp / $goal XP",
                style = MaterialTheme.typography.bodyMedium,
                color = if (done) palette.correct else palette.secondary,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (xp.toFloat() / goal).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (done) palette.correct else palette.streak,
            trackColor = palette.separator,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun PracticeCard(due: Int, canEarnHeart: Boolean, onPractice: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.card).clickable(onClick = onPractice)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(palette.accentSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Refresh, null, tint = palette.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Practice", style = MaterialTheme.typography.titleMedium, color = palette.text)
            Text(
                buildString {
                    append(if (due > 0) "$due to review today" else "Strengthen your weakest letters")
                    if (canEarnHeart) append(" · earns a heart")
                },
                style = MaterialTheme.typography.bodyMedium, color = palette.secondary,
            )
        }
        Icon(Icons.Rounded.PlayArrow, "Start practice", tint = palette.accent)
    }
}

private fun earnsCoins(examId: String): String {
    val n = Exams.coinsFor(examId)
    return "earns $n ${if (n == 1) "coin" else "coins"}"
}

@Composable
private fun NextExamCard(unit: CourseUnit, exam: Lesson, open: Boolean, onStart: () -> Unit) {
    val where = if (unit.track == Track.Reading) "Unit ${unit.id.drop(1)}" else unit.title
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.card).clickable(enabled = open, onClick = onStart)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (open) palette.streak else palette.separator), contentAlignment = Alignment.Center) {
            Icon(if (open) Icons.Rounded.Stars else Icons.Rounded.Lock, null, tint = if (open) palette.onAccent else palette.secondary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (Exams.isUnitCheck(exam.id)) "Unit check: ${unit.title}" else exam.title,
                style = MaterialTheme.typography.titleMedium, color = palette.text,
            )
            Text(
                when {
                    !open -> "Finish $where's lessons to unlock · " + earnsCoins(exam.id)
                    Exams.isUnitCheck(exam.id) -> "Next · 5 questions · " + earnsCoins(exam.id)
                    else -> "Next exam · after $where · " + earnsCoins(exam.id)
                },
                style = MaterialTheme.typography.bodyMedium, color = palette.secondary,
            )
        }
        if (open) Icon(Icons.Rounded.PlayArrow, "Start exam", tint = palette.streak)
    }
}

/** Coins from exams, and unlocking unlimited hearts with them. */
@Composable
private fun CoinsCard(progress: Progress, today: Long, onBuy: () -> Unit) {
    val owned = progress.ownsUnlimitedHearts(today)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.card).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(palette.streak), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Stars, null, tint = palette.onAccent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${progress.coins} ${if (progress.coins == 1) "coin" else "coins"}",
                style = MaterialTheme.typography.titleMedium, color = palette.text,
            )
            Text(
                when {
                    owned -> "Unlimited hearts are yours while your streak lasts"
                    progress.canBuyUnlimitedHearts(today) -> "Enough for unlimited hearts"
                    else -> "Pass exams to earn coins · $UNLIMITED_HEARTS_PRICE unlock unlimited hearts"
                },
                style = MaterialTheme.typography.bodySmall, color = palette.secondary,
            )
        }
        if (progress.canBuyUnlimitedHearts(today)) {
            TextButton(onClick = onBuy) { Text("Unlock") }
        }
    }
}

private fun heartsLabel(progress: Progress): String {
    val ms = progress.msUntilNextHeart(nowMillis()) ?: return "hearts"
    return "+1 in ${ms / 60_000 + 1} min"
}

@Composable
private fun Stat(icon: ImageVector, tint: Color, value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(palette.card).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = palette.text)
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = palette.secondary, maxLines = 1)
    }
}

@Composable
private fun UnitCard(unit: CourseUnit, progress: Progress, onStart: (String) -> Unit) {
    val done = unit.lessons.count { it.id in progress.completed }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.card)) {
        Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (unit.surah != null) "SURAH ${unit.surah}" else "UNIT ${unit.id.drop(1)}",
                    style = MaterialTheme.typography.labelSmall, color = palette.secondary,
                )
                Text(unit.title, style = MaterialTheme.typography.titleLarge, color = palette.text)
                Text(unit.subtitle, style = MaterialTheme.typography.bodyMedium, color = palette.secondary)
            }
            if (unit.badge.isNotEmpty()) {
                Text(
                    unit.badge,
                    style = arabicStyle(30).copy(textDirection = TextDirection.Rtl, color = palette.accent),
                )
            }
        }
        LinearProgressIndicator(
            progress = { done.toFloat() / unit.lessons.size },
            modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(4.dp),
            color = palette.accent,
            trackColor = palette.separator,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(8.dp))
        unit.lessons.forEachIndexed { i, lesson ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = palette.separator)
            val completed = lesson.id in progress.completed
            val unlocked = Course.isUnlocked(lesson.id, progress.completed)
            LessonRow(lesson.title, completed, unlocked, examId = lesson.id.takeIf(Exams::isExam)) { onStart(lesson.id) }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun LessonRow(title: String, completed: Boolean, unlocked: Boolean, examId: String? = null, onClick: () -> Unit) {
    val exam = examId != null
    val (icon, bg, fg) = when {
        // Exams stand out: a star in the coin colour.
        exam && unlocked -> Triple(if (completed) Icons.Rounded.Check else Icons.Rounded.Stars, palette.streak, palette.onAccent)
        completed -> Triple(Icons.Rounded.Check, palette.accentFill, palette.onAccentFill)
        unlocked -> Triple(Icons.Rounded.PlayArrow, palette.accentSoft, palette.accent)
        else -> Triple(Icons.Rounded.Lock, palette.separator, palette.secondary)
    }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = unlocked, onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (unlocked) palette.text else palette.secondary)
            if (exam) {
                Text(
                    if (completed) "Passed" else "Pass to ${earnsCoins(examId!!).replace("earns", "earn")}",
                    style = MaterialTheme.typography.bodySmall, color = palette.streak,
                )
            }
        }
        if (completed) Text(if (exam) "Retake" else "Review", style = MaterialTheme.typography.bodyMedium, color = palette.secondary)
    }
}
