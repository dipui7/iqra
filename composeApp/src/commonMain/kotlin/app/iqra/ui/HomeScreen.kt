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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
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
import app.iqra.data.Course
import app.iqra.data.CourseUnit
import app.iqra.engine.MAX_HEARTS
import app.iqra.engine.Progress
import app.iqra.nowMillis
import app.iqra.todayEpochDay

@Composable
fun HomeScreen(progress: Progress, onStart: (String) -> Unit, onAbout: () -> Unit) {
    var noHearts by remember { mutableStateOf(false) }
    val start = { id: String -> if (progress.hearts > 0) onStart(id) else noHearts = true }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Header(progress, onAbout) }
        items(Course.units, key = { it.id }) { unit -> UnitCard(unit, progress, start) }
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
            confirmButton = { TextButton({ noHearts = false }) { Text("OK") } },
            title = { Text("Out of hearts") },
            text = { Text("Take a short break. Your next heart arrives in about $minutes min.") },
        )
    }
}

@Composable
private fun Header(progress: Progress, onAbout: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Iqra", style = MaterialTheme.typography.displaySmall, color = palette.text)
                Text("Learn to read the Quran, letter by letter", color = palette.secondary)
            }
            IconButton(onClick = onAbout) { Icon(Icons.Rounded.Info, "Sources and licences", tint = palette.secondary) }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(Icons.Rounded.LocalFireDepartment, palette.streak, "${progress.currentStreak(todayEpochDay())}", "day streak", Modifier.weight(1f))
            Stat(Icons.Rounded.Bolt, palette.accent, "${progress.xp}", "XP", Modifier.weight(1f))
            Stat(Icons.Rounded.Favorite, palette.heart, "${progress.hearts}/$MAX_HEARTS", heartsLabel(progress), Modifier.weight(1f))
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
                Text("UNIT ${unit.id.drop(1)}", style = MaterialTheme.typography.labelSmall, color = palette.secondary)
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
            LessonRow(lesson.title, completed, unlocked) { onStart(lesson.id) }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun LessonRow(title: String, completed: Boolean, unlocked: Boolean, onClick: () -> Unit) {
    val (icon, bg, fg) = when {
        completed -> Triple(Icons.Rounded.Check, palette.accent, Color.White)
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
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (unlocked) palette.text else palette.secondary, modifier = Modifier.weight(1f))
        if (completed) Text("Review", style = MaterialTheme.typography.bodyMedium, color = palette.secondary)
    }
}
