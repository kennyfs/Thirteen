package io.github.kennyfs.thirteen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A scale's number: a decimal only for small ones. */
private fun axis(v: Double) = if (abs(v) >= 10 || v == kotlin.math.floor(v)) "%.0f".format(v) else "%.1f".format(v)

/**
 * The running total of the points (0 before the first hand): a line over a dashed zero line, with the highest and
 * lowest totals on the left, the first and last dates under it, and the last total at the end of the line.
 */
@Composable
fun ScoreChart(totals: List<Double>, firstDate: String, lastDate: String, description: String, modifier: Modifier = Modifier, height: Dp = 200.dp) {
  val colors = MaterialTheme.colorScheme
  val line = colors.primary
  val up = GOOD
  val down = BAD
  val measurer = rememberTextMeasurer()
  val label = MaterialTheme.typography.labelMedium.copy(color = colors.onSurfaceVariant, fontSize = 13.sp)
  val points = listOf(0.0) + totals
  Box(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
    Canvas(Modifier.fillMaxWidth().height(height)) {
      val hi = max(points.max(), 0.0)
      val lo = min(points.min(), 0.0)
      val span = (hi - lo).takeIf { it > 0 } ?: 1.0
      val end = points.last()
      val endColor = if (end > 0) up else if (end < 0) down else line
      val e = measurer.measure(signed(end), label.copy(color = endColor, fontSize = 15.sp))
      val a = measurer.measure(firstDate, label)
      val b = measurer.measure(lastDate, label)
      val left = measurer.measure(axis(hi), label).size.width.coerceAtLeast(measurer.measure(axis(lo), label).size.width) + 10.dp.toPx()
      val right = max(44.dp.toPx(), e.size.width + 12.dp.toPx())
      val top = max(10.dp.toPx(), e.size.height / 2f + 2.dp.toPx())
      val bottom = max(26.dp.toPx(), max(a.size.height, b.size.height) + 8.dp.toPx())
      val w = (size.width - left - right).coerceAtLeast(1f)
      val h = (size.height - top - bottom).coerceAtLeast(1f)
      fun x(i: Int) = left + w * i / max(points.size - 1, 1)
      fun y(v: Double) = top + h * (1 - ((v - lo) / span)).toFloat()
      // The zero line and the extremes.
      drawLine(
        colors.outline, Offset(left, y(0.0)), Offset(left + w, y(0.0)), 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
      )
      for (v in listOf(hi, lo, 0.0).distinct()) {
        val t = measurer.measure(axis(v), label)
        drawText(t, topLeft = Offset(left - 6.dp.toPx() - t.size.width, y(v) - t.size.height / 2f))
      }
      val path = Path().apply { points.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
      drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
      drawCircle(endColor, 5.dp.toPx(), Offset(x(points.size - 1), y(end)))
      drawText(e, topLeft = Offset(x(points.size - 1) + 8.dp.toPx(), (y(end) - e.size.height / 2f).coerceIn(0f, size.height - bottom - e.size.height / 2f)))
      drawText(a, topLeft = Offset(left, size.height - a.size.height))
      if (firstDate != lastDate) drawText(b, topLeft = Offset(left + w - b.size.width, size.height - b.size.height))
    }
  }
}
