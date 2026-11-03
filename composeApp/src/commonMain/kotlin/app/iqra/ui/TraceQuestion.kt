package app.iqra.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.iqra.engine.Exercise
import app.iqra.engine.LessonSession
import app.iqra.engine.TraceScore
import app.iqra.engine.Tracing

/** Where the guide letter is drawn on a canvas, and which grid cells its ink covers. */
private class Guide(val layout: TextLayoutResult, val topLeft: Offset, val cells: BooleanArray)

/** Draw [layout] into an offscreen bitmap and return its alpha channel. */
private fun render(layout: TextLayoutResult, topLeft: Offset, w: Int, h: Int, density: Density): IntArray {
    val bitmap = ImageBitmap(w, h)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(w.toFloat(), h.toFloat())) {
        drawText(layout, color = Color.Black, topLeft = topLeft)
    }
    val px = IntArray(w * h)
    bitmap.readPixels(px)
    return IntArray(w * h) { (px[it] ushr 24) and 0xFF }
}

private fun buildGuide(layout: TextLayoutResult, w: Int, h: Int, density: Density): Guide {
    // Arabic glyphs sit unevenly in their line box, so centre the guide by its actual ink.
    val lw = layout.size.width
    val lh = layout.size.height
    val alpha = render(layout, Offset.Zero, lw, lh, density)
    var minX = lw; var minY = lh; var maxX = 0; var maxY = 0
    for (y in 0 until lh) for (x in 0 until lw) if (alpha[y * lw + x] > 64) {
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
    }
    val topLeft = if (maxX >= minX) Offset(w / 2f - (minX + maxX) / 2f, h / 2f - (minY + maxY) / 2f) else Offset.Zero
    val full = render(layout, topLeft, w, h, density)
    val g = Tracing.GRID
    val cells = BooleanArray(g * g)
    for (y in 0 until h) for (x in 0 until w) if (full[y * w + x] > 96) {
        cells[(y * g / h).coerceAtMost(g - 1) * g + (x * g / w).coerceAtMost(g - 1)] = true
    }
    return Guide(layout, topLeft, cells)
}

@Composable
fun TraceQuestion(q: Exercise.Trace, session: LessonSession) {
    val measurer = rememberTextMeasurer()
    val localDensity = LocalDensity.current
    val guideColor = palette.separator
    val inkColor = palette.accent
    val style = arabicStyle(190, lineFactor = 1.6)
    val layout = remember(q.letter) { measurer.measure(q.letter.char, style) }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var guide by remember { mutableStateOf<Guide?>(null) }
    var result by remember { mutableStateOf<TraceScore?>(null) }
    val name = rememberSoundPlayer(q.name)
    val done = session.feedback != null

    Text("Trace ${q.letter.name}", style = MaterialTheme.typography.titleLarge, color = palette.text, modifier = Modifier.padding(vertical = 12.dp))
    Text(
        "Follow the grey letter with your finger or mouse. Lift and start again as often as you like.",
        color = palette.secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp),
    )

    Canvas(
        Modifier.fillMaxWidth().aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(palette.card)
            .border(2.dp, if (done) palette.correct else palette.separator, RoundedCornerShape(20.dp))
            .onSizeChanged {
                canvasSize = it.toSize()
                guide = buildGuide(layout, it.width.coerceAtLeast(1), it.height.coerceAtLeast(1), localDensity)
            }
            .pointerInput(done) {
                if (done) return@pointerInput
                detectDragGestures(
                    onDragStart = { current = listOf(it); result = null },
                    onDrag = { change, _ -> current = current + change.position },
                    onDragEnd = { strokes += current; current = emptyList() },
                    onDragCancel = { strokes += current; current = emptyList() },
                )
            }
            .pointerInput(done) {
                if (done) return@pointerInput
                // A tap draws a dot (for the letters' dots).
                detectTapGestures { strokes += listOf(it); result = null }
            },
    ) {
        guide?.let { drawText(it.layout, color = guideColor, topLeft = it.topLeft) }
        val width = 14.dp.toPx()
        for (s in strokes + listOf(current)) {
            if (s.isEmpty()) continue
            if (s.size == 1) {
                drawCircle(inkColor, radius = width / 2, center = s[0])
                continue
            }
            val path = Path().apply {
                moveTo(s[0].x, s[0].y)
                for (p in s.drop(1)) lineTo(p.x, p.y)
            }
            drawPath(path, inkColor, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    Spacer(Modifier.height(8.dp))
    result?.takeIf { !it.passed }?.let {
        Text("Not quite. ${it.advice}", color = palette.wrong, style = MaterialTheme.typography.bodyMedium)
    }
    if (!done) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { strokes.clear(); current = emptyList(); result = null }) { Text("Clear", color = palette.secondary) }
            TextButton(onClick = { session.markSolved(showFeedback = false) }) { Text("Skip", color = palette.secondary) }
        }
        PrimaryButton("Check my tracing", enabled = strokes.isNotEmpty()) {
            val g = guide ?: return@PrimaryButton
            val scale = Tracing.GRID / canvasSize.width
            val score = Tracing.score(g.cells, strokes.map { s -> s.map { it.x * scale to it.y * scale } })
            result = score
            if (score.passed) {
                name?.toggle()
                session.markSolved(showFeedback = true)
            } else {
                strokes.clear()
            }
        }
    }
}
