package app.iqra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.iqra.engine.Badge
import app.iqra.engine.Badges
import app.iqra.engine.Progress

@Composable
fun BadgesScreen(progress: Progress, onBack: () -> Unit) {
    val earned = Badges.earned(progress)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = palette.accent) }
            Text("Badges", style = MaterialTheme.typography.displaySmall, color = palette.text)
        }
        Text(
            "${earned.size} of ${Badges.all.size} earned",
            color = palette.secondary, modifier = Modifier.padding(start = 12.dp, bottom = 12.dp),
        )
        // Earned first, then the closest to being earned.
        val sorted = Badges.all.sortedWith(
            compareByDescending<Badge> { it.id in earned }
                .thenByDescending { b -> b.progress(progress).let { (d, n) -> d.toFloat() / n } },
        )
        sorted.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                row.forEach { BadgeCard(it, progress, it.id in earned, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BadgeCard(badge: Badge, progress: Progress, earned: Boolean, modifier: Modifier) {
    val (done, needed) = badge.progress(progress)
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(palette.card).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(if (earned) palette.streak else palette.separator),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (earned) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock, null,
                tint = if (earned) palette.onAccent else palette.secondary, modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(badge.title, style = MaterialTheme.typography.titleMedium, color = if (earned) palette.text else palette.secondary, textAlign = TextAlign.Center)
        Text(badge.detail, style = MaterialTheme.typography.bodySmall, color = palette.secondary, textAlign = TextAlign.Center)
        if (!earned && needed > 1) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { done.toFloat() / needed },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = palette.accent, trackColor = palette.separator,
                strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
            )
            Text("$done / $needed", style = MaterialTheme.typography.bodySmall, color = palette.secondary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** "New badge" lines for the results screen. */
@Composable
fun NewBadges(ids: Set<String>) {
    ids.mapNotNull(Badges::byId).forEach { b ->
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(16.dp)).background(palette.card).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(palette.streak), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.EmojiEvents, null, tint = palette.onAccent, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text("New badge: ${b.title}", style = MaterialTheme.typography.titleMedium, color = palette.text)
                Text(b.detail, style = MaterialTheme.typography.bodySmall, color = palette.secondary)
            }
        }
    }
}
