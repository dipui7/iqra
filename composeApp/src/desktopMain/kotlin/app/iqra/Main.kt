package app.iqra

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.iqra.resources.Res
import app.iqra.resources.app_icon
import org.jetbrains.compose.resources.painterResource

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Iqra",
        icon = painterResource(Res.drawable.app_icon),
        state = rememberWindowState(width = 440.dp, height = 860.dp),
    ) {
        App()
    }
}
