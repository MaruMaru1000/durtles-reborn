package com.example.durtlesreborn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.durtlesreborn.data.morae
import com.example.durtlesreborn.data.pitchPattern

/**
 * A pitch-accent diagram in the style of Kanshudo: each mora is a dot sitting at its pitch height
 * (high or low), the dots are joined by horizontal lines with a vertical step where the pitch
 * falls, and a hollow dot after the word stands for the following particle. A leading vertical
 * line shows where the first mora starts from.
 */
@Composable
fun PitchDiagram(reading: String, accent: Int, modifier: Modifier = Modifier) {
    val mora = morae(reading)
    if (mora.isEmpty()) return
    val pattern = pitchPattern(mora.size, accent)

    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dotColor = MaterialTheme.colorScheme.primary
    val labelStyle = MaterialTheme.typography.labelMedium
    val textMeasurer = rememberTextMeasurer()

    val step = 30.dp
    val height = 76.dp
    Canvas(modifier.size(width = step * (mora.size + 1), height = height)) {
        val stepPx = step.toPx()
        val highY = 44.dp.toPx()
        val lowY = 64.dp.toPx()
        val stroke = 2.dp.toPx()
        val dotRadius = 3.dp.toPx()
        fun y(level: Char) = if (level == 'H') highY else lowY

        val dotX = FloatArray(mora.size) { stepPx * (it + 0.5f) }
        val dotY = FloatArray(mora.size) { y(pattern[it]) }

        // Leading vertical: from the low baseline up to the first mora's pitch.
        drawLine(lineColor, Offset(dotX[0], lowY), Offset(dotX[0], dotY[0]), stroke)

        // The morae, with a vertical step wherever the pitch changes.
        for (i in 1 until mora.size) {
            val px = dotX[i - 1]; val py = dotY[i - 1]
            val nx = dotX[i]; val ny = dotY[i]
            if (py == ny) {
                drawLine(lineColor, Offset(px, py), Offset(nx, ny), stroke)
            } else {
                val mid = (px + nx) / 2f
                drawLine(lineColor, Offset(px, py), Offset(mid, py), stroke)
                drawLine(lineColor, Offset(mid, py), Offset(mid, ny), stroke)
                drawLine(lineColor, Offset(mid, ny), Offset(nx, ny), stroke)
            }
        }

        // The following particle: high after a heiban word, low after an accented one.
        val particleX = stepPx * (mora.size + 0.5f)
        val particleY = y(if (accent == 0) 'H' else 'L')
        val lx = dotX.last(); val ly = dotY.last()
        if (ly == particleY) {
            drawLine(lineColor, Offset(lx, ly), Offset(particleX, particleY), stroke)
        } else {
            val mid = (lx + particleX) / 2f
            drawLine(lineColor, Offset(lx, ly), Offset(mid, ly), stroke)
            drawLine(lineColor, Offset(mid, ly), Offset(mid, particleY), stroke)
            drawLine(lineColor, Offset(mid, particleY), Offset(particleX, particleY), stroke)
        }

        // Kana above each dot, and the dots themselves.
        for (i in mora.indices) {
            val layout = textMeasurer.measure(AnnotatedString(mora[i]), labelStyle)
            drawText(layout, topLeft = Offset(dotX[i] - layout.size.width / 2f, 4.dp.toPx()))
            drawCircle(dotColor, dotRadius, Offset(dotX[i], dotY[i]))
        }
        // Hollow particle dot.
        drawCircle(lineColor, dotRadius, Offset(particleX, particleY), style = Stroke(stroke))
    }
}
