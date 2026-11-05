package app.iqra

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    document.getElementById("loading")?.remove()
    ComposeViewport(document.body!!) {
        // On a wide screen, keep the app phone-shaped in the middle rather than stretched.
        Box(Modifier.fillMaxSize().background(Color(0xFFE9E6DC)), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight()) { App() }
        }
    }
}
