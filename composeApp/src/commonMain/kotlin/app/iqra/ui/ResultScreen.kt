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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ResultScreen(result: LessonResult, streak: Int, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        val tint = if (result.passed) palette.accent else palette.heart
        Box(Modifier.size(96.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Icon(if (result.passed) Icons.Rounded.Check else Icons.Rounded.HeartBroken, null, tint = Color.White, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(
            when {
                !result.passed -> "Out of hearts"
                result.practice -> "Practice complete"
                else -> "Lesson complete"
            },
            style = MaterialTheme.typography.displaySmall, color = palette.text,
        )
        Text(
            if (result.passed) result.title
            else "Mistakes are how we learn. Your hearts refill over time — try again soon.",
            color = palette.secondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
        )
        if (result.passed) {
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ResultStat("+${result.xp}", "XP", palette.accent, Modifier.weight(1f))
                ResultStat("${result.accuracy}%", "Accuracy", palette.correct, Modifier.weight(1f))
                if (result.heartGained) ResultStat("+1", "Heart", palette.heart, Modifier.weight(1f))
                else ResultStat("$streak", if (streak == 1) "Day streak" else "Days streak", palette.streak, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Continue", onClick = onContinue)
    }
}

@Composable
private fun ResultStat(value: String, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(palette.card).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = palette.secondary)
    }
}
