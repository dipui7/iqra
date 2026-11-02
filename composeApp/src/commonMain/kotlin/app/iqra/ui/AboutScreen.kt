package app.iqra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

private data class Source(val title: String, val body: String, val link: String)

private val sources = listOf(
    Source(
        "Quran text — Tanzil Project",
        "Uthmani text, version 1.1. Copyright © 2007-2026 Tanzil Project, licensed under Creative Commons " +
            "Attribution 3.0. Used verbatim: the text is never changed, only shown whole or split at its own spaces.",
        "https://tanzil.net",
    ),
    Source(
        "Recitation — Mishary Rashid Alafasy",
        "Per-ayah recordings streamed from EveryAyah.com when you tap play. The audio is not bundled with the app " +
            "and an internet connection is needed to hear it.",
        "https://everyayah.com",
    ),
    Source(
        "Font — Amiri Quran",
        "Copyright © 2010-2022 The Amiri Quran Project Authors. SIL Open Font License 1.1.",
        "https://github.com/aliftype/amiri",
    ),
    Source(
        "Lessons",
        "Letter names, sounds and hints are written for this app and are not Quranic text. " +
            "No Quranic text in this app is generated or altered by software or AI.",
        "https://tanzil.net/updates/",
    ),
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = palette.accent) }
            Text("Sources", style = MaterialTheme.typography.displaySmall, color = palette.text)
        }
        sources.forEach { s ->
            Column(
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.card)
                    .clickable { uri.openUri(s.link) }.padding(16.dp),
            ) {
                Text(s.title, style = MaterialTheme.typography.titleMedium, color = palette.text)
                Text(s.body, color = palette.secondary, modifier = Modifier.padding(top = 4.dp))
                Text(s.link, color = palette.accent, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
