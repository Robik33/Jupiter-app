package com.marketia.jupiter.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.marketia.jupiter.data.entity.StatEntity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HexagonChart(
    stats: List<StatEntity>,
    modifier: Modifier = Modifier
) {
    val statOrder = listOf("fuerza", "velocidad", "agilidad", "resistencia", "elasticidad", "consciencia")
    val labels    = listOf("Fuerza", "Velocidad", "Agilidad", "Resistencia", "Elasticidad", "Consciencia")
    val values    = statOrder.map { name -> (stats.find { it.name == name }?.value ?: 50).toFloat() }

    Canvas(modifier = modifier) {
        val center      = Offset(size.width / 2f, size.height / 2f)
        val maxR        = minOf(size.width, size.height) / 2f
        val chartR      = maxR * 0.60f
        val labelR      = maxR * 0.88f

        val labelPaint = Paint().apply {
            textSize  = 28f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            color     = android.graphics.Color.argb(180, 180, 195, 215)
            typeface  = Typeface.MONOSPACE
        }

        // Grid hexagons at 25 / 50 / 75 / 100 %
        val gridColor = Color(0xFF1E1E30)
        listOf(0.25f, 0.5f, 0.75f, 1.0f).forEach { level ->
            val path = Path()
            for (i in 0 until 6) {
                val a = 2.0 * PI * i / 6 - PI / 2
                val x = center.x + chartR * level * cos(a).toFloat()
                val y = center.y + chartR * level * sin(a).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, gridColor, style = Stroke(1.dp.toPx()))
        }

        // Axis lines
        val axisColor = Color(0xFF2A2A40)
        for (i in 0 until 6) {
            val a = 2.0 * PI * i / 6 - PI / 2
            drawLine(axisColor, center,
                Offset(center.x + chartR * cos(a).toFloat(), center.y + chartR * sin(a).toFloat()),
                1.dp.toPx())
        }

        // Data polygon
        val dataPath = Path()
        values.forEachIndexed { i, value ->
            val a = 2.0 * PI * i / 6 - PI / 2
            val r = chartR * (value / 100f)
            val x = center.x + r * cos(a).toFloat()
            val y = center.y + r * sin(a).toFloat()
            if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
        }
        dataPath.close()
        drawPath(dataPath, Color(0x5500E5FF))
        drawPath(dataPath, Color(0xFF00E5FF), style = Stroke(2.dp.toPx()))

        // Dots
        values.forEachIndexed { i, value ->
            val a = 2.0 * PI * i / 6 - PI / 2
            val r = chartR * (value / 100f)
            drawCircle(Color(0xFF00FF88), 5.dp.toPx(),
                Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat()))
        }

        // Labels via native canvas
        drawIntoCanvas { canvas ->
            labels.forEachIndexed { i, label ->
                val a = 2.0 * PI * i / 6 - PI / 2
                val x = center.x + labelR * cos(a).toFloat()
                val y = center.y + labelR * sin(a).toFloat() + labelPaint.textSize / 3f
                canvas.nativeCanvas.drawText(label, x, y, labelPaint)
            }
        }
    }
}
