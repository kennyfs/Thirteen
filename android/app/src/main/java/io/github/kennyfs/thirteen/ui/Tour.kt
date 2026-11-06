package io.github.kennyfs.thirteen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.kennyfs.thirteen.R
import io.github.kennyfs.thirteen.Screen
import io.github.kennyfs.thirteen.ui.theme.LocalTable
import kotlin.math.roundToInt

/** A step of the tour: the screen it shows, the element in the light (a [tourAnchor] key, or none), its words. */
data class TourStep(val screen: Screen, val target: String?, val title: Int, val text: Int)

val TOUR_STEPS = listOf(
  TourStep(Screen.PLAY, "hand", R.string.tour_play_title, R.string.tour_play),
  TourStep(Screen.PLAY, "confirm", R.string.tour_confirm_title, R.string.tour_confirm),
  TourStep(Screen.PLAY, "rules", R.string.tour_rules_title, R.string.tour_rules),
  TourStep(Screen.ANALYZE, "nav_ANALYZE", R.string.tour_analyze_title, R.string.tour_analyze),
  TourStep(Screen.BATTLE, "nav_BATTLE", R.string.tour_battle_title, R.string.tour_battle),
  TourStep(Screen.RECORDS, "nav_RECORDS", R.string.tour_records_title, R.string.tour_records),
  TourStep(Screen.SETTINGS, "nav_SETTINGS", R.string.tour_settings_title, R.string.tour_settings),
  TourStep(Screen.PLAY, null, R.string.tour_end_title, R.string.tour_end),
)

/**
 * The tour of the app: from screen to screen (the app shows each step's screen), a light on one element of it. It
 * deals, plays and records nothing, so leaving it at any step (Skip, or Back) leaves everything as it was.
 */
class TourState {
  /** The step shown, -1 when the tour is not on. */
  var step by mutableIntStateOf(-1); private set
  val on get() = step >= 0
  /** Where each anchored element is, in the root's coordinates. */
  val anchors = mutableStateMapOf<String, Rect>()

  fun start() { step = 0 }
  fun next() { if (step >= TOUR_STEPS.size - 1) end() else step++ }
  fun prev() { if (step > 0) step-- }
  fun end() { step = -1 }
}

val LocalTour = staticCompositionLocalOf { TourState() }
/** Opens the tutorial page from anywhere (the table's help, the tour's end). */
val LocalOpenTutorial = staticCompositionLocalOf<() -> Unit> { {} }

/** Marks an element the tour can light: its bounds are kept under `key` while it is shown. */
fun Modifier.tourAnchor(key: String): Modifier = composed {
  val tour = LocalTour.current
  DisposableEffect(key) { onDispose { tour.anchors.remove(key) } }
  onGloballyPositioned { tour.anchors[key] = it.boundsInRoot() }
}

/**
 * The tour over the whole app: dimmed but for the step's element, and a card beside it with the step's words, Back,
 * Next and, always, Skip. Back (the gesture or the button) ends it too; touches on the dimmed app do nothing.
 */
@Composable
fun TourOverlay(tour: TourState, onTutorial: () -> Unit) {
  val step = TOUR_STEPS.getOrNull(tour.step) ?: return
  val last = tour.step == TOUR_STEPS.size - 1
  BackHandler { tour.end() }
  val d = LocalDensity.current
  val hole = step.target?.let { tour.anchors[it] }?.let { r -> with(d) { r.inflate(6.dp.toPx()) } }
  val highlight = LocalTable.current.highlight
  BoxWithConstraints(
    Modifier.fillMaxSize().testTag("tour")
      // Every touch on the dimmed app is taken here: nothing under the tour is touched by mistake.
      .pointerInput(Unit) { awaitEachGesture { while (true) { val e = awaitPointerEvent(); e.changes.forEach { it.consume() }; if (e.changes.all { !it.pressed }) break } } },
  ) {
    val w = constraints.maxWidth.toFloat()
    val h = constraints.maxHeight.toFloat()
    Canvas(Modifier.fillMaxSize()) {
      val path = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(Offset.Zero, Size(w, h)))
        if (hole != null) addRoundRect(RoundRect(hole, CornerRadius(14.dp.toPx())))
      }
      drawPath(path, Color.Black.copy(alpha = 0.55f))
      if (hole != null) drawRoundRect(highlight, hole.topLeft, hole.size, CornerRadius(14.dp.toPx()), style = Stroke(3.dp.toPx()))
    }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val m = with(d) { 12.dp.toPx() }
    val cw = minOf(with(d) { 380.dp.toPx() }, w - 2 * m)
    val ch = cardSize.height.toFloat()
    // Below the light if it fits, else above, else over the bottom.
    val pos = when {
      hole == null -> Offset((w - cw) / 2, ((h - ch) / 2).coerceAtLeast(m))
      hole.bottom + m + ch <= h - m -> Offset((hole.center.x - cw / 2).coerceIn(m, w - cw - m), hole.bottom + m)
      hole.top - m - ch >= m -> Offset((hole.center.x - cw / 2).coerceIn(m, w - cw - m), hole.top - m - ch)
      hole.right + m + cw <= w - m -> Offset(hole.right + m, (hole.center.y - ch / 2).coerceIn(m, (h - ch - m).coerceAtLeast(m)))
      hole.left - m - cw >= m -> Offset(hole.left - m - cw, (hole.center.y - ch / 2).coerceIn(m, (h - ch - m).coerceAtLeast(m)))
      else -> Offset((w - cw) / 2, h - ch - m)
    }
    Surface(
      Modifier.offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }.width(with(d) { cw.toDp() })
        .onSizeChanged { cardSize = it }.semantics { paneTitle = "tour" }.testTag("tour_card"),
      shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp,
    ) {
      Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("${tour.step + 1} / ${TOUR_STEPS.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(Modifier.weight(1f))
          if (!last) TextButton(onClick = { tour.end() }, Modifier.testTag("tour_skip")) { Text(stringResource(R.string.tour_skip)) }
        }
        Text(stringResource(step.title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(step.text), style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (tour.step > 0) TextButton(onClick = { tour.prev() }) { Text(stringResource(R.string.tour_prev)) }
          Spacer(Modifier.weight(1f))
          if (last) {
            FilledTonalButton(onClick = { tour.end(); onTutorial() }) { Text(stringResource(R.string.tut_title)) }
            Button(onClick = { tour.end() }, Modifier.testTag("tour_next")) { Text(stringResource(R.string.tour_done)) }
          } else Button(onClick = { tour.next() }, Modifier.testTag("tour_next")) { Text(stringResource(R.string.tour_next)) }
        }
      }
    }
  }
}

/** The welcome card of the first start: the tutorial, the tour, or play. Any way out counts as seen. */
@Composable
fun WelcomeDialog(onLearn: () -> Unit, onTour: () -> Unit, onClose: () -> Unit) {
  androidx.compose.material3.AlertDialog(
    onDismissRequest = onClose,
    confirmButton = {},
    title = { Text(stringResource(R.string.welcome_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 6.dp))
        Button(onClick = onLearn, Modifier.fillMaxWidth().testTag("welcome_learn")) { Text(stringResource(R.string.welcome_learn)) }
        FilledTonalButton(onClick = onTour, Modifier.fillMaxWidth().testTag("welcome_tour")) { Text(stringResource(R.string.welcome_tour)) }
        TextButton(onClick = onClose, Modifier.fillMaxWidth().testTag("welcome_skip")) { Text(stringResource(R.string.welcome_skip)) }
      }
    },
  )
}
