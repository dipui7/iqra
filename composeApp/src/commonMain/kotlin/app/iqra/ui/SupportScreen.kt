package app.iqra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

private enum class Region(val label: String) { Gaza("Gaza"), Sudan("Sudan"), Congo("DR Congo") }

private data class Organisation(val name: String, val about: String, val link: String, val regions: Set<Region>)

/**
 * Well-known humanitarian organisations working in these places. Links go to each organisation's
 * own website; the app never handles donations. Review this list before each release.
 */
private val organisations = listOf(
    Organisation(
        "UNRWA", "UN agency for Palestine refugees: food, shelter, health care and schools in Gaza.",
        "https://www.unrwa.org", setOf(Region.Gaza),
    ),
    Organisation(
        "Medical Aid for Palestinians (MAP)", "Health and medical care for Palestinians, including in Gaza.",
        "https://www.map.org.uk", setOf(Region.Gaza),
    ),
    Organisation(
        "Palestine Children's Relief Fund (PCRF)", "Medical and humanitarian care for children in Gaza.",
        "https://www.pcrf.net", setOf(Region.Gaza),
    ),
    Organisation(
        "Anera", "Food, water, health and shelter for Palestinians in Gaza and the region.",
        "https://www.anera.org", setOf(Region.Gaza),
    ),
    Organisation(
        "Médecins Sans Frontières (Doctors Without Borders)", "Emergency medical care in conflict zones.",
        "https://www.msf.org", setOf(Region.Gaza, Region.Sudan, Region.Congo),
    ),
    Organisation(
        "International Committee of the Red Cross (ICRC)", "Protection and aid for people affected by armed conflict.",
        "https://www.icrc.org", setOf(Region.Gaza, Region.Sudan, Region.Congo),
    ),
    Organisation(
        "UNICEF", "Nutrition, health, water and protection for children.",
        "https://www.unicef.org", setOf(Region.Gaza, Region.Sudan, Region.Congo),
    ),
    Organisation(
        "World Food Programme (WFP)", "Emergency food for families facing hunger.",
        "https://www.wfp.org", setOf(Region.Gaza, Region.Sudan, Region.Congo),
    ),
    Organisation(
        "Save the Children", "Health, food, education and protection for children in crises.",
        "https://www.savethechildren.net", setOf(Region.Gaza, Region.Sudan, Region.Congo),
    ),
    Organisation(
        "UNHCR, the UN Refugee Agency", "Shelter and protection for people forced to flee their homes.",
        "https://www.unhcr.org", setOf(Region.Sudan, Region.Congo),
    ),
    Organisation(
        "International Rescue Committee (IRC)", "Health care, shelter and protection for people hit by conflict.",
        "https://www.rescue.org", setOf(Region.Sudan, Region.Congo),
    ),
    Organisation(
        "Islamic Relief Worldwide", "Emergency relief, food and water for people in crisis.",
        "https://www.islamic-relief.org", setOf(Region.Gaza, Region.Sudan),
    ),
)

@Composable
fun SupportScreen(onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    var filter by remember { mutableStateOf<Region?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = palette.accent) }
            Text("Help others", style = MaterialTheme.typography.displaySmall, color = palette.text)
        }
        Text(
            "Iqra is free, with no subscription. If you'd like to give, these organisations help people in " +
                "Gaza, Sudan and the Democratic Republic of the Congo. Each link opens the organisation's own website.",
            color = palette.secondary, modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("All", filter == null) { filter = null }
            Region.entries.forEach { r -> Chip(r.label, filter == r) { filter = r } }
        }
        organisations.filter { filter == null || filter in it.regions }.forEach { o ->
            Column(
                Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.card)
                    .clickable { uri.openUri(o.link) }.padding(16.dp),
            ) {
                Text(o.name, style = MaterialTheme.typography.titleMedium, color = palette.text)
                Text(o.about, color = palette.secondary, modifier = Modifier.padding(top = 4.dp))
                Text(
                    o.regions.joinToString(" · ") { it.label },
                    style = MaterialTheme.typography.bodySmall, color = palette.streak, modifier = Modifier.padding(top = 6.dp),
                )
                Text(o.link.removePrefix("https://"), color = palette.accent, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Text(
            "Iqra is not connected to these organisations and receives nothing from them. " +
                "Please check an organisation yourself before giving.",
            style = MaterialTheme.typography.bodySmall, color = palette.secondary,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) palette.onAccent else palette.text,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) palette.accent else palette.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
