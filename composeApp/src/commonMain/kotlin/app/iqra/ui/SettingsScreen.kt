package app.iqra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import app.iqra.engine.DAILY_GOALS
import app.iqra.data.Letters
import app.iqra.engine.ProgressStore
import app.iqra.remindersSupported
import app.iqra.engine.Skills

private fun goalName(xp: Int) = when (xp) {
    10 -> "Casual"
    20 -> "Regular"
    30 -> "Serious"
    else -> "Intense"
}

@Composable
fun SettingsScreen(store: ProgressStore, onBack: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    val progress = store.progress
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = palette.accent) }
            Text("Settings", style = MaterialTheme.typography.displaySmall, color = palette.text)
        }

        SectionLabel("DAILY GOAL")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.card)) {
            DAILY_GOALS.forEachIndexed { i, xp ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = palette.separator)
                Row(
                    Modifier.fillMaxWidth().clickable { store.setDailyGoal(xp) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(goalName(xp), style = MaterialTheme.typography.bodyLarge, color = palette.text, modifier = Modifier.weight(1f))
                    Text("$xp XP a day", color = palette.secondary)
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Icons.Rounded.Check, if (progress.dailyGoal == xp) "Selected" else null,
                        tint = if (progress.dailyGoal == xp) palette.accent else palette.card,
                    )
                }
            }
        }
        Text(
            "A lesson gives 10 XP, or 15 with no mistakes.",
            style = MaterialTheme.typography.bodySmall, color = palette.secondary, modifier = Modifier.padding(start = 16.dp, top = 6.dp),
        )

        if (remindersSupported) {
            SectionLabel("DAILY REMINDER")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.card).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf<Int?>(null, 8, 13, 18, 21).forEach { hour ->
                        val on = progress.reminderHour == hour
                        Text(
                            if (hour == null) "Off" else "$hour:00",
                            color = if (on) palette.onAccentFill else palette.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .background(if (on) palette.accentFill else palette.background)
                                .clickable { store.setReminder(hour) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                Text(
                    "Only if you haven't practised yet that day.",
                    style = MaterialTheme.typography.bodySmall, color = palette.secondary, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        SectionLabel("YOUR PROGRESS")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.card).padding(16.dp)) {
            InfoRow("Lessons completed", "${progress.completed.size}")
            InfoRow("Total XP", "${progress.xp}")
            InfoRow("Best streak", if (progress.bestStreak == 1) "1 day" else "${progress.bestStreak} days")
            InfoRow("Letters and rules practised", "${progress.skills.size}")
        }

        SectionLabel("LETTERS")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.card).padding(12.dp)) {
            // Right to left, as the alphabet is read.
            Letters.all.chunked(7).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.reversed().forEach { letter ->
                        val state = progress.skills[Skills.letter(letter)]
                        val bg = when {
                            state == null -> palette.background
                            state.box == 0 -> palette.wrongSoft
                            state.box < 3 -> palette.accentSoft
                            else -> palette.correctSoft
                        }
                        Box(Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
                            // Full line height keeps dots below the letter (ب ي ج) inside the tile.
                            Text(letter.char, style = arabicStyle(24))
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend(palette.wrongSoft, "Needs practice")
                Legend(palette.accentSoft, "Learning")
                Legend(palette.correctSoft, "Strong")
            }
        }

        Spacer(Modifier.padding(top = 12.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.card)
                .clickable { confirmReset = true }.padding(16.dp),
        ) {
            Text("Reset progress", style = MaterialTheme.typography.bodyLarge, color = palette.wrong)
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset progress?") },
            text = { Text("All completed lessons, XP, streaks and review history will be erased. This can't be undone.") },
            confirmButton = { TextButton({ store.reset(); confirmReset = false }) { Text("Reset", color = palette.wrong) } },
            dismissButton = { TextButton({ confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = palette.secondary)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = palette.secondary, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = palette.text, modifier = Modifier.weight(1f))
        Text(value, color = palette.secondary)
    }
}
