package io.github.kennyfs.thirteen.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A list's place while it loads (an analysis on its way), as the web page's PanelSkeleton: a heading bar and `rows` rows
 * in the shape of a candidate's (a mark, its words and its figure over a line of cards), as blocks in
 * surfaceContainerHighest on surfaceContainerHigh, pulsing over 1.6 s. `label` is what TalkBack says.
 */
@Composable
fun PanelSkeleton(label: String, modifier: Modifier = Modifier, rows: Int = 5) {
  val pulse = skeletonPulse()
  val block = MaterialTheme.colorScheme.surfaceContainerHighest
  val ground = MaterialTheme.colorScheme.surfaceContainerHigh
  Column(
    modifier.fillMaxSize().padding(12.dp).clearAndSetSemantics { contentDescription = label }.graphicsLayer { alpha = pulse.value },
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Bar(block, 20.dp, Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp).fillMaxWidth(0.6f).widthIn(max = 320.dp))
    repeat(rows) {
      Column(
        Modifier.fillMaxWidth().background(ground, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Bar(block, 18.dp, Modifier.width(52.dp))
          Box(Modifier.weight(1f)) { Bar(block, 18.dp, Modifier.fillMaxWidth().widthIn(max = 240.dp)) }
          Bar(block, 18.dp, Modifier.width(44.dp))
        }
        Box(Modifier.fillMaxWidth().widthIn(max = 420.dp).height(44.dp).background(block, RoundedCornerShape(8.dp)))
      }
    }
  }
}

@Composable
private fun Bar(color: Color, height: Dp, modifier: Modifier) {
  Box(modifier.height(height).background(color, RoundedCornerShape(height / 2)))
}

/** The skeletons' pulse, 1 to 0.45 and back in 1.6 s; still with the system's animations off. */
@Composable
private fun skeletonPulse(): State<Float> {
  val context = LocalContext.current
  val still = remember {
    android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
  }
  if (still) return remember { mutableFloatStateOf(1f) }
  return rememberInfiniteTransition(label = "skeleton").animateFloat(
    1f, 0.45f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
  )
}
