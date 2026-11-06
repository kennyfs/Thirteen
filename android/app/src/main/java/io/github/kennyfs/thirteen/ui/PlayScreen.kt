package io.github.kennyfs.thirteen.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kennyfs.thirteen.AppViewModel
import io.github.kennyfs.thirteen.Arranger
import io.github.kennyfs.thirteen.Arrangement as Rows
import io.github.kennyfs.thirteen.Cards
import io.github.kennyfs.thirteen.R
import io.github.kennyfs.thirteen.Score
import io.github.kennyfs.thirteen.ui.theme.LocalTable
import io.github.kennyfs.thirteen.ui.theme.TableColors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// By the number of cards of a rank: one pair, three of a kind, four of a kind.
private val SAME_RANK_CATEGORY = intArrayOf(0, 0, 2, 4, 8)

/** Practice: the hand being set, then its result and the analysis of the player's hand. */
@Composable
fun PlayScreen(vm: AppViewModel, onRules: () -> Unit) {
  val result = vm.result
  if (result == null) Column(Modifier.fillMaxSize()) {
    PracticeHeader(vm, onRules)
    Arranging(vm.play, vm.gameValues["wheel"]?.toInt() ?: 0, vm.waiting, stringResource(R.string.confirm), Modifier.weight(1f)) { vm.confirm() }
  } else Finished(vm, result.first, result.second)
}

@Composable
fun seatName(i: Int) = if (i == 0) stringResource(R.string.you) else stringResource(R.string.seat_pos, i)

/** The hand's number, its rules (a chip that opens the rules page) and everyone's running total. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeHeader(vm: AppViewModel, onRules: () -> Unit) {
  val (hands, totals) = vm.session
  val title = @Composable {
    // A hand played again is not one of the session's: no number.
    Text(if (vm.replaying) stringResource(R.string.replay_title) else stringResource(R.string.hand_n, hands + 1), style = MaterialTheme.typography.titleLarge)
    if (vm.gameValues.isNotEmpty()) Chip(vm.rulesName(vm.gameValues), onRules, trailingMenu = true)
  }
  val scores = @Composable { for (i in totals.indices) ScoreChip(seatName(i), totals[i], i == 0) }
  BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 2.dp, bottom = 4.dp)) {
    if (maxWidth >= 600.dp) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      title()
      Spacer(Modifier.weight(1f))
      scores()
    } else Column {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { title() }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) { scores() }
    }
  }
}

/** A name and a running total; the player's on a tinted ground. */
@Composable
fun ScoreChip(name: String, value: Double, me: Boolean) {
  Row(
    Modifier.clip(RoundedCornerShape(10.dp)).background(if (me) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
      .padding(horizontal = if (me) 8.dp else 2.dp, vertical = 3.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    // The name and the figure on one baseline (their sizes differ).
    Text(name, Modifier.alignByBaseline(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    Text(
      signed(value), Modifier.alignByBaseline(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
      color = if (me && value < 0) BAD else if (me && value > 0) GOOD else MaterialTheme.colorScheme.onSurface,
    )
  }
}


/** Once the hand is all in the rows: whether they can be played (and the royalties), or why not (a foul). */
@Composable
private fun readiness(vm: Arranger): Pair<String, Boolean> {
  val eval = vm.rowsEval
  return when {
    vm.unplaced.isNotEmpty() -> "" to false
    eval?.valid == true -> eval.royalties.sum().let { if (it > 0) stringResource(R.string.ready_royalty, signed(it).drop(1)) else stringResource(R.string.ready) } to false
    // Rows in order but equal: a foul only under strict rows.
    eval?.valid == false && eval.keys[0] <= eval.keys[1] && eval.keys[1] <= eval.keys[2] -> stringResource(R.string.fouled_strict) to true
    eval?.valid == false -> stringResource(R.string.fouled) to true
    else -> "" to false
  }
}

/**
 * Setting a hand into three rows on the table, for practice and battles. On a wide window the buttons in a bar on top,
 * the three rows side by side under it and the hand in a line along the bottom, both as wide as the table; on a tall
 * one the rows one above another, the hand in a line or two under them, and the buttons in a bar under the table. The
 * rows' cards and the hand's are one size, the largest the room allows up to `LocalCardSizes.play`, which is what the
 * window gives the page with the most above the table (practice's), so a battle's cards are the same.
 */
@Composable
fun Arranging(
  vm: Arranger, wheel: Int, busy: Boolean, confirmLabel: String, modifier: Modifier = Modifier, confirmShort: String = confirmLabel,
  onConfirm: () -> Unit,
) {
  BoxWithConstraints(modifier.fillMaxSize()) {
    val wide = maxWidth > maxHeight * 1.15f
    val fit = arrangeFit(maxWidth, maxHeight, wide, textRoom(14), LocalCardSizes.current.play)
    if (wide) ArrangeWide(vm, wheel, busy, confirmLabel, confirmShort, fit.card, onConfirm)
    else ArrangeTall(vm, wheel, busy, confirmLabel, confirmShort, fit, onConfirm)
  }
}

@Composable
private fun Felt(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  val t = LocalTable.current
  Surface(modifier, shape = MaterialTheme.shapes.medium, color = t.felt, contentColor = t.onFelt, content = content)
}

/**
 * The wide page: the three rows one above another at the left, the buttons at their right (the confirm button under
 * the others), and the hand in a line as wide as the table under them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArrangeWide(vm: Arranger, wheel: Int, busy: Boolean, confirmLabel: String, confirmShort: String, card: Dp, onConfirm: () -> Unit) {
  Felt(Modifier.fillMaxSize().padding(start = 8.dp, end = 12.dp, bottom = 6.dp)) {
    Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
      val density = LocalDensity.current
      // The buttons fill the room right of the rows, in three lines as tall as the rows' (sorting, the tools, the confirm).
      var rowsHeight by remember { mutableStateOf(0.dp) }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ROWS_GAP)) {
        RowsStack(vm, card, labelWidth(density), wheel, Modifier.width(IntrinsicSize.Max).onSizeChanged { rowsHeight = with(density) { it.height.toDp() } })
        Column(Modifier.weight(1f).height(rowsHeight), verticalArrangement = Arrangement.spacedBy(ROW_STACK_GAP)) {
          SortToggle(vm, Modifier.fillMaxWidth().weight(1f), MaterialTheme.typography.titleMedium)
          // The three tools lay out alike: their names all beside the icons, all under them, or none (icons only).
          BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val labels = listOf(stringResource(R.string.undo), stringResource(R.string.take_all), stringResource(R.string.help))
            val style = MaterialTheme.typography.titleMedium
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val sizes = labels.map { with(density) { measurer.measure(it, style).size.let { s -> s.width.toDp() to s.height.toDp() } } }
            // A button's inside: its third of the line less the gaps, its padding and its border.
            val innerW = (maxWidth - 16.dp) / 3 - 12.dp - 2.dp
            val innerH = maxHeight - 8.dp - 2.dp
            val mode = when {
              sizes.all { 24.dp + 6.dp + it.first <= innerW } -> LabelMode.BESIDE
              sizes.all { it.first <= innerW && 24.dp + 2.dp + it.second <= innerH } -> LabelMode.UNDER
              else -> LabelMode.NONE
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              val tool = Modifier.weight(1f).fillMaxHeight()
              FeltToolButton(labels[0], Icons.AutoMirrored.Filled.Undo, vm.canUndo, tool, mode) { vm.undo() }
              FeltToolButton(labels[1], Icons.Outlined.ClearAll, vm.canClear, tool, mode) { vm.clearRows() }
              ArrangeHelp(modifier = tool, mode = mode)
            }
          }
          ConfirmButton(vm, busy, confirmLabel, confirmShort, Modifier.fillMaxWidth().weight(1f), MaterialTheme.typography.titleLarge, onConfirm)
        }
      }
      HandTray(vm, card, 13)
    }
  }
}

/**
 * The three rows one above another, each with its name and state at its left. A swipe over their cards takes each card
 * it touches back to the hand (one undo step for the swipe); a tap still takes back one.
 */
@Composable
private fun RowsStack(vm: Arranger, card: Dp, labelWidth: Dp?, wheel: Int, modifier: Modifier = Modifier, scroll: Boolean = false, labelFill: Boolean = false) {
  val density = LocalDensity.current
  val bounds = remember { mutableMapOf<Int, Rect>() }
  var box by remember { mutableStateOf<LayoutCoordinates?>(null) }
  val placed = vm.rows.flatten()
  Column(
    modifier.let { if (scroll) it.verticalScroll(rememberScrollState()) else it }
      .onGloballyPositioned { box = it }.swipeUnplace(vm, placed, bounds),
    verticalArrangement = Arrangement.spacedBy(if (labelWidth == null) 8.dp else ROW_STACK_GAP),
  ) {
    for (r in 0 until 3)
      RowSlotTall(vm, r, card, labelWidth, headerHeight(density), wheel, labelFill) { c ->
        Modifier.onGloballyPositioned { coords -> box?.let { if (coords.isAttached) bounds[c] = it.localBoundingBoxOf(coords) } }
      }
  }
}

/** A swipe over the rows' cards returns each card it touches to the hand. */
private fun Modifier.swipeUnplace(vm: Arranger, placed: List<Int>, bounds: Map<Int, Rect>): Modifier =
  pointerInput(placed) {
    fun at(p: Offset): Int? = placed.firstOrNull { c -> bounds[c]?.contains(p) == true }
    val touched = mutableSetOf<Int>()
    var last = Offset.Zero
    fun touch(p: Offset) {
      val c = at(p) ?: return
      if (touched.add(c)) vm.unplace(c, saveUndo = touched.size == 1)
    }
    detectDragGestures(onDragStart = { o -> touched.clear(); last = o; touch(o) }) { change, _ ->
      change.consume()
      val p = change.position
      val steps = ((p - last).getDistance() / 4.dp.toPx()).toInt().coerceIn(1, 200)
      for (k in 1..steps) touch(last + (p - last) * (k / steps.toFloat()))
      last = p
    }
  }

@Composable
private fun ArrangeTall(vm: Arranger, wheel: Int, busy: Boolean, confirmLabel: String, confirmShort: String, fit: ArrangeFit, onConfirm: () -> Unit) {
  Column(Modifier.fillMaxSize()) {
    Felt(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
      BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
        val density = LocalDensity.current
        // What the rows take, for the hand's room under them.
        val rowH = if (fit.above) headerHeight(density) + cardHeight(fit.card) else maxOf(cardHeight(fit.card), labelHeight(density))
        val rowsH = (rowH + ROW_PAD * 2) * 3 + 8.dp * 2
        val handRoom = maxHeight - rowsH - 8.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
          RowsStack(vm, fit.card, if (fit.above) null else labelWidth(density), wheel, Modifier.weight(1f, fill = false), scroll = true, labelFill = true)
          HandTray(vm, fit.card, fit.perLine, Modifier.padding(top = 8.dp), room = handRoom)
        }
      }
    }
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      val sortLabel = stringResource(if (vm.sortBySuit) R.string.sort_by_suit_on else R.string.sort_by_rank_on)
      val sortShort = stringResource(if (vm.sortBySuit) R.string.sort_suit else R.string.sort_rank)
      val sortTiny = stringResource(if (vm.sortBySuit) R.string.sort_suit_short else R.string.sort_rank_short)
      OutlinedButton(
        onClick = { vm.sortBySuit = !vm.sortBySuit },
        Modifier.height(46.dp).semantics { contentDescription = sortLabel },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
      ) {
        Icon(Icons.Filled.SwapVert, null, Modifier.size(20.dp))
        FitText(sortShort, sortTiny, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge)
      }
      OutlinedIconButton(onClick = vm::undo, Modifier.size(46.dp), enabled = vm.canUndo) {
        Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo))
      }
      OutlinedIconButton(onClick = vm::clearRows, Modifier.size(46.dp), enabled = vm.canClear) {
        Icon(Icons.Outlined.ClearAll, stringResource(R.string.take_all))
      }
      ArrangeHelp(onFelt = false)
      Button(
        onClick = onConfirm, Modifier.weight(1f).height(BIG_BUTTON), enabled = !busy && vm.rowsEval?.valid == true,
        shape = RoundedCornerShape(18.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
      ) {
        if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onPrimary)
        else FitText(confirmLabel, confirmShort, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }
    }
  }
}

/** A (?) button: how to set a hand, in a dialog. */
@Composable
private fun ArrangeHelp(onFelt: Boolean = true, modifier: Modifier = Modifier.width(52.dp), mode: LabelMode = LabelMode.NONE) {
  var open by remember { mutableStateOf(false) }
  val t = LocalTable.current
  val label = stringResource(R.string.help)
  if (onFelt) FeltToolButton(label, Icons.AutoMirrored.Outlined.HelpOutline, true, modifier, mode) { open = true }
  else OutlinedIconButton(onClick = { open = true }, Modifier.size(46.dp)) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, label) }
  if (open)
    AlertDialog(
      onDismissRequest = { open = false },
      confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.ok)) } },
      title = { Text(stringResource(R.string.arrange_help_title)) },
      text = { Text(stringResource(R.string.arrange_help), Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyLarge) },
    )
}

/** What a row says next to its cards: where the picked cards can go, the row's hand, or how full it is. */
@Composable
private fun rowState(vm: Arranger, r: Int, wheel: Int): Triple<String, Boolean, Boolean> {
  val cards = vm.rows[r]
  val free = Cards.ROW_SIZES[r] - cards.size
  val eval = vm.rowsEval
  val picked = vm.selected.size
  // The lit ground says where the picked cards can go; no words for it.
  val target = (picked > 0 && free >= picked) || vm.armed == r
  val dim = picked > free
  return when {
    free == 0 && eval != null && eval.categories[r] > 0 -> Triple(Cards.rowLabel(eval.keys[r], rowLabels(), wheel), target, dim)
    else -> Triple("${cards.size}/${Cards.ROW_SIZES[r]}", target, dim)
  }
}

/** A row's ground: lit, with a dashed edge, where the picked cards can go. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.rowGround(vm: Arranger, r: Int, target: Boolean, t: TableColors): Modifier {
  val shape = RoundedCornerShape(10.dp)
  return this.clip(shape)
    .background(if (target) t.target else t.slot)
    .drawBehind {
      if (target) {
        val r2 = 10.dp.toPx()
        drawRoundRect(
          t.highlight, cornerRadius = CornerRadius(r2, r2),
          style = Stroke(2.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))),
        )
      }
    }
    .combinedClickable(onLongClick = { vm.clearRow(r) }) { vm.tapRow(r) }
    .testTag("row_$r")
}

@Composable
private fun RowSlotTall(
  vm: Arranger, r: Int, card: Dp, labelWidth: Dp?, headerHeight: Dp, wheel: Int, labelFill: Boolean = false,
  cardModifier: (Int) -> Modifier = { Modifier },
) {
  val t = LocalTable.current
  val cards = vm.rows[r]
  val eval = vm.rowsEval
  val category = if (cards.size == Cards.ROW_SIZES[r] && eval != null) eval.categories[r] else 0
  val (state, target, dim) = rowState(vm, r, wheel)
  val name = @Composable { m: Modifier ->
    Text(
      stringArrayResource(R.array.row_names)[r], m, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
      color = t.onFelt, maxLines = 1,
    )
  }
  val stateText = @Composable { m: Modifier, lines: Int ->
    Text(
      state, m, style = MaterialTheme.typography.bodySmall, maxLines = lines, overflow = TextOverflow.Ellipsis,
      color = if (target) t.highlight else if (dim) t.onFeltDim else t.onFelt, fontWeight = if (target) FontWeight.Bold else FontWeight.Medium,
    )
  }
  val ground = Modifier.fillMaxWidth().rowGround(vm, r, target, t).padding(ROW_PAD)
  if (labelWidth == null) Column(ground) {
    Row(Modifier.fillMaxWidth().height(headerHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      name(Modifier.padding(start = 4.dp))
      stateText(Modifier.weight(1f), 1)
      if (cards.isNotEmpty()) TakeBack(vm, r, Modifier.size(32.dp))
    }
    RowWithSlots(cards, category, Cards.ROW_SIZES[r], card, onCardClick = { vm.unplace(it) }, cardModifier = cardModifier)
  } else Row(ground, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    // labelFill: the label takes what the cards leave, the cards at the right, the front row's under the others' first three.
    Column(if (labelFill) Modifier.weight(1f) else Modifier.width(labelWidth), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      // No room for the take-back button here: a placed card goes back with a tap, a row with a long press.
      name(Modifier)
      stateText(Modifier, 2)
    }
    Box(if (labelFill) Modifier.width(card * 5 + CARD_GAP * 4) else Modifier) {
      RowWithSlots(cards, category, Cards.ROW_SIZES[r], card, onCardClick = { vm.unplace(it) }, cardModifier = cardModifier)
    }
  }
}

/** A row's label beside its cards in a tall layout, and its header above them. */
// Sizes from the text's: a small size scaled (a large sp would be scaled less, as large text is under a large font).
private fun labelWidth(d: androidx.compose.ui.unit.Density): Dp = maxOf(72.dp, with(d) { 14.sp.toDp() } * 4.6f)
private fun headerHeight(d: androidx.compose.ui.unit.Density): Dp = maxOf(32.dp, with(d) { 13.sp.toDp() } * 2)
/** The height of a label beside the cards: the name and a line of state. */
private fun labelHeight(d: androidx.compose.ui.unit.Density): Dp = with(d) { 24.sp.toDp() + 16.sp.toDp() } + 2.dp

/** Returns a row's cards to the hand (as a long press on the row does). */
@Composable
private fun TakeBack(vm: Arranger, r: Int, modifier: Modifier = Modifier) {
  val t = LocalTable.current
  IconButton(
    onClick = { vm.clearRow(r) }, modifier,
    colors = IconButtonDefaults.iconButtonColors(containerColor = t.onFelt.copy(alpha = 0.12f), contentColor = t.onFelt),
  ) {
    Icon(Icons.Outlined.FileDownload, stringResource(R.string.take_back_row, stringArrayResource(R.array.row_names)[r]))
  }
}

@Composable
private fun SortToggle(vm: Arranger, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.labelLarge) {
  val t = LocalTable.current
  ToggleGroup(
    listOf(false to stringResource(R.string.sort_rank), true to stringResource(R.string.sort_suit)), vm.sortBySuit, { vm.sortBySuit = it },
    modifier, ToggleColors(t.onFelt, t.felt, t.onFelt.copy(alpha = 0.12f), t.onFelt), style,
  )
}

/** Where a tool's name goes: beside its icon, under it, or nowhere (the icon only). */
private enum class LabelMode { BESIDE, UNDER, NONE }

/** A tool on the table: its icon, and its name as `mode` says (the icon only where that does not fit after all). */
@Composable
private fun FeltToolButton(label: String, icon: ImageVector, enabled: Boolean, modifier: Modifier, mode: LabelMode, onClick: () -> Unit) {
  val t = LocalTable.current
  OutlinedButton(
    onClick = onClick, modifier.height(BAR_HEIGHT), enabled = enabled, shape = RoundedCornerShape(18.dp),
    border = BorderStroke(1.dp, t.onFelt.copy(alpha = if (enabled) 0.5f else 0.2f)),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = t.onFelt, disabledContentColor = t.onFelt.copy(alpha = 0.38f)),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp),
  ) { IconLabel(icon, label, MaterialTheme.typography.titleMedium, mode) }
}

/** An icon with its label beside or under it as `mode` says, never cut off: the icon alone when that does not fit. */
@Composable
private fun IconLabel(icon: ImageVector, label: String, style: TextStyle, mode: LabelMode) {
  Layout(
    { Icon(icon, null); Text(label, style = style, maxLines = 1, softWrap = false) },
    Modifier.clearAndSetSemantics { contentDescription = label },
  ) { ms, c ->
    val i = ms[0].measure(Constraints())
    val l = ms[1].measure(Constraints())
    val gap = 6.dp.roundToPx()
    val besideW = i.width + gap + l.width
    val underH = i.height + 2.dp.roundToPx() + l.height
    when {
      mode == LabelMode.BESIDE && besideW <= c.maxWidth -> {
        val h = maxOf(i.height, l.height)
        layout(besideW, h) { i.place(0, (h - i.height) / 2); l.place(i.width + gap, (h - l.height) / 2) }
      }
      mode == LabelMode.UNDER && l.width <= c.maxWidth && underH <= c.maxHeight -> {
        val w = maxOf(i.width, l.width)
        layout(w, underH) { i.place((w - i.width) / 2, 0); l.place((w - l.width) / 2, underH - l.height) }
      }
      else -> layout(i.width, i.height) { i.place(0, 0) }
    }
  }
}

/** The main action on the table: light on the felt, enabled once the rows can be played. */
@Composable
private fun ConfirmButton(
  vm: Arranger, busy: Boolean, label: String, short: String, modifier: Modifier, style: TextStyle = MaterialTheme.typography.titleMedium,
  onConfirm: () -> Unit,
) {
  val t = LocalTable.current
  Button(
    onClick = onConfirm, modifier, enabled = !busy && vm.rowsEval?.valid == true, shape = RoundedCornerShape(18.dp),
    colors = ButtonDefaults.buttonColors(
      containerColor = t.action, contentColor = t.onAction,
      disabledContainerColor = t.onFelt.copy(alpha = 0.14f), disabledContentColor = t.onFelt.copy(alpha = 0.55f),
    ),
  ) {
    if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp, color = t.onAction)
    else FitText(label, short, style = style, fontWeight = FontWeight.Bold)
  }
}

/**
 * The cards not yet placed, in one line or several of `perLine`. Tap a card to pick it; a swipe picks the cards it
 * touches (or unpicks them, when it starts on a picked card). Once all are placed, whether the rows can be played.
 */
@Composable
private fun HandTray(vm: Arranger, card: Dp, perLine: Int, modifier: Modifier = Modifier, room: Dp? = null) {
  val hand = vm.unplaced
  // Each card's bounds in the tray, for swipes.
  val bounds = remember(hand) { mutableMapOf<Int, Rect>() }
  var tray by remember { mutableStateOf<LayoutCoordinates?>(null) }
  val count = hand.groupingBy(Cards::rank).eachCount()
  val lines = ceil(13f / perLine).toInt()
  // Sorted by suit with the room for it: a line a suit, from the left, the lines closer together if that makes them fit
  // (the cards keep their size, and a picked card its room to rise).
  var lineGap = HAND_LINE_GAP
  val bySuit = if (vm.sortBySuit && room != null) {
    val suitLines = hand.groupBy(Cards::suit).values.flatMap { it.chunked(perLine.coerceAtLeast(1)) }
    fun fits(gap: Dp) = cardHeight(card) * suitLines.size + gap * (suitLines.size - 1) + card * 0.22f <= room
    when {
      fits(HAND_LINE_GAP) -> suitLines
      fits(HAND_LINE_GAP_TIGHT) -> suitLines.also { lineGap = HAND_LINE_GAP_TIGHT }
      else -> null
    }
  } else null
  // Else, sorted by suit, a wider gap between the suits, which the page's sizes leave room for in a single line.
  val single = perLine >= 13
  Column(
    modifier.fillMaxWidth().onGloballyPositioned { tray = it }.swipeSelect(vm, hand, bounds).padding(top = card * 0.22f),
    horizontalAlignment = if (bySuit != null) Alignment.Start else Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(lineGap),
  ) {
    for (line in bySuit ?: hand.chunked(perLine.coerceAtLeast(1))) {
      Row(verticalAlignment = Alignment.Bottom) {
        for ((i, c) in line.withIndex()) {
          if (i > 0) Spacer(Modifier.width(if (single && bySuit == null && vm.sortBySuit && Cards.suit(line[i - 1]) != Cards.suit(c)) SUIT_GAP else HAND_GAP))
          // Pairs, trips and quads are tinted (light) as their category, whichever way the hand is sorted.
          val n = count.getValue(Cards.rank(c))
          CardFace(
            c, card, Modifier.onGloballyPositioned { coords -> tray?.let { bounds[c] = it.localBoundingBoxOf(coords) } },
            tint = SAME_RANK_CATEGORY[n], light = true, selected = c in vm.selected, onClick = { vm.toggle(c) },
          )
        }
      }
    }
    if (hand.isEmpty()) {
      val t = LocalTable.current
      val (text, bad) = readiness(vm)
      Box(Modifier.fillMaxWidth().height(cardHeight(card) * lines + HAND_LINE_GAP * (lines - 1)), contentAlignment = Alignment.Center) {
        Text(
          text, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold, color = if (bad) t.bad else t.onFelt, textAlign = TextAlign.Center,
        )
      }
    }
  }
}

/**
 * A swipe over the hand picks each card it touches when the card it starts on is not picked, and unpicks them when it
 * is, so a run of cards (a straight, a flush sorted by suit) takes one stroke, even over two lines. A tap still
 * toggles one card. The path between two reported points is followed in small steps, so a quick swipe misses none.
 */
private fun Modifier.swipeSelect(vm: Arranger, hand: List<Int>, bounds: Map<Int, Rect>): Modifier =
  pointerInput(hand) {
    fun at(p: Offset): Int? = hand.firstOrNull { c -> bounds[c]?.contains(p) == true }
    var on = true
    val touched = mutableSetOf<Int>()
    var last = Offset.Zero
    fun touch(p: Offset) {
      val c = at(p) ?: return
      // The first card touched decides whether the swipe picks or unpicks.
      if (touched.isEmpty()) on = c !in vm.selected
      if (touched.add(c)) vm.select(listOf(c), on)
    }
    detectDragGestures(
      onDragStart = { o ->
        touched.clear()
        last = o
        touch(o)
      },
      onDragEnd = { vm.selectionDone() },
      onDragCancel = { vm.selectionDone() },
    ) { change, _ ->
      change.consume()
      val p = change.position
      val steps = ((p - last).getDistance() / 4.dp.toPx()).toInt().coerceIn(1, 200)
      for (k in 1..steps) touch(last + (p - last) * (k / steps.toFloat()))
      last = p
    }
  }

/** The finished hand: what it gave the player, everyone's rows, and the way on. */
@Composable
private fun Finished(vm: AppViewModel, arrangements: List<Rows>, score: Score) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val wide = maxWidth > maxHeight * 1.15f
    val names = List(4) { seatName(it) }
    val notes = List(4) { if (it == 0) null else vm.settings.seatName(vm.seats[it - 1]) }
    val mine = score.result[0]
    Column(Modifier.fillMaxSize()) {
      Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = if (wide) 0.dp else 4.dp, bottom = if (wide) 0.dp else 4.dp)
          .heightIn(min = if (wide) RESULT_HEADER else 56.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(if (vm.replaying) stringResource(R.string.replay_title) else stringResource(R.string.hand_n, vm.session.first), style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
        Text(
          stringResource(R.string.this_hand, signed(mine)), style = MaterialTheme.typography.headlineSmall,
          color = if (mine > 0) GOOD else if (mine < 0) BAD else MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false,
        )
        Text(
          stringResource(R.string.total_so_far, signed(vm.session.second[0])), Modifier.weight(1f),
          style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (wide) {
          HandKeepers(vm, iconsOnly = true)
          ResultActions(vm, compact = true)
        }
      }
      // Narrow: the felt and the players close to the edges, for larger cards.
      Felt(Modifier.weight(1f).fillMaxWidth().padding(start = if (wide) 8.dp else 4.dp, end = if (wide) 12.dp else 4.dp, bottom = if (wide) 6.dp else 0.dp)) {
        SeatResults(
          arrangements, score, vm.gameValues["wheel"]?.toInt() ?: 0, names, notes, vm.session.second,
          Modifier.padding(if (wide) PaddingValues(6.dp) else PaddingValues(horizontal = 2.dp, vertical = 4.dp)),
        )
      }
      if (!wide) Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { HandKeepers(vm, iconsOnly = false) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { ResultActions(vm, Modifier.weight(1f)) }
      }
    }
  }
}

/** Save the finished hand to the favorites, or play it again to arrange it another way. */
@Composable
private fun RowScope.HandKeepers(vm: AppViewModel, iconsOnly: Boolean) {
  val rec = vm.lastRecord
  val saved = rec != null && rec.optString("id") in vm.favoriteIds
  val favorite = stringResource(if (saved) R.string.favorite_remove else R.string.favorite_add)
  val replay = stringResource(R.string.replay_hand)
  val starIcon = if (saved) Icons.Filled.Star else Icons.Outlined.StarBorder
  if (iconsOnly) {
    IconButton(onClick = { rec?.let(vm::toggleFavorite) }, Modifier.testTag("favorite"), enabled = rec != null) {
      Icon(starIcon, favorite, tint = if (saved) LocalTable.current.highlight else LocalContentColor.current)
    }
    IconButton(onClick = { vm.replay() }, Modifier.testTag("replay")) { Icon(Icons.Outlined.Replay, replay) }
  } else {
    OutlinedButton(onClick = { rec?.let(vm::toggleFavorite) }, Modifier.weight(1f).heightIn(min = 48.dp).testTag("favorite"), enabled = rec != null) {
      Icon(starIcon, null, Modifier.padding(end = 6.dp), tint = if (saved) LocalTable.current.highlight else LocalContentColor.current)
      Text(stringResource(if (saved) R.string.favorite_saved else R.string.favorite_short), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    OutlinedButton(onClick = { vm.replay() }, Modifier.weight(1f).heightIn(min = 48.dp).testTag("replay")) {
      Icon(Icons.Outlined.Replay, null, Modifier.padding(end = 6.dp))
      Text(stringResource(R.string.replay_short), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun ResultActions(vm: AppViewModel, modifier: Modifier = Modifier, compact: Boolean = false) {
  val height = if (compact) 44.dp else BIG_BUTTON
  FilledTonalButton(
    onClick = { vm.showGameAnalysis() }, modifier.heightIn(min = height),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
  ) {
    // The icon only where the button has the room: the full words matter more.
    if (compact) Icon(Icons.Outlined.Analytics, null, Modifier.padding(end = 6.dp))
    FitText(stringResource(R.string.analyze_my_hand), stringResource(R.string.battle_analyze))
  }
  Button(onClick = { vm.deal() }, modifier.heightIn(min = height), shape = RoundedCornerShape(18.dp)) {
    FitText(stringResource(R.string.next_hand), stringResource(R.string.next_hand_short), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
  }
}

/** The analysis of the player's hand of the finished game, the player's rows pinned. */
@Composable
fun MyHandAnalysis(vm: AppViewModel) {
  val mine = vm.result?.first?.get(0) ?: return
  Column(Modifier.fillMaxSize()) {
    TitleBar(stringResource(R.string.analyze_my_hand), onBack = { vm.resultTab = 0 }) {
      Button(onClick = { vm.deal() }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.next_hand)) }
    }
    val a = vm.gameAnalysis
    if (a == null) Loading(stringResource(R.string.analyzing))
    else CandidateList(a, mine, legacyNote(vm, a), Modifier.weight(1f))
  }
}

/** A centred spinner with what is being waited for. */
@Composable
fun Loading(text: String, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
    CircularProgressIndicator(Modifier.size(48.dp), strokeWidth = 5.dp)
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

private val ARROWS_WIDTH = 56.dp
private val SEAT_GAP = 4.dp
private val COLUMN_GAP = 10.dp
private val SMALL_GAP = 3.dp
/** The narrow result's blocks: their padding, and the gaps between their rows and between the cards side by side. */
private val GRID_PAD = 6.dp
private val GRID_COLUMN_GAP = 6.dp
private val GRID_CARD_GAP = 1.dp

/**
 * A row's hand name in the narrow result: plain for the player's own rows; against another's, on the color of the
 * player's result there (as [RowMark]'s), which the screen reader says.
 */
@Composable
private fun RowName(name: String, d: Int?, valid: Boolean) {
  val t = LocalTable.current
  val style = MaterialTheme.typography.labelSmall
  if (d == null || !valid) {
    Text(name, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (valid) t.onFeltDim else t.bad)
    return
  }
  val (container, content) = when {
    d > 0 -> WIN_CONTAINER to WIN_CONTENT
    d < 0 -> LOSE_CONTAINER to LOSE_CONTENT
    else -> t.onFelt.copy(alpha = 0.18f) to t.onFelt
  }
  val said = stringResource(if (d > 0) R.string.row_win else if (d < 0) R.string.row_lose else R.string.row_tie)
  Text(
    name, Modifier.clip(RoundedCornerShape(5.dp)).background(container).padding(horizontal = 4.dp).semantics { contentDescription = "$name, $said" },
    style = style, color = content, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
  )
}

private val WIN_CONTAINER = Color(0xFFD6EFD8)
private val WIN_CONTENT = Color(0xFF1E5E22)
private val LOSE_CONTAINER = Color(0xFFF8D9D6)
private val LOSE_CONTENT = Color(0xFF8C1D18)

/** A row's result for the player against another's: "You win", "You lose", "Tie". */
@Composable
private fun RowMark(d: Int, small: Boolean = false) {
  val t = LocalTable.current
  when {
    d > 0 -> Badge(stringResource(R.string.row_win), WIN_CONTAINER, WIN_CONTENT, small = small)
    d < 0 -> Badge(stringResource(R.string.row_lose), LOSE_CONTAINER, LOSE_CONTENT, small = small)
    else -> Badge(stringResource(R.string.row_tie), t.onFelt.copy(alpha = 0.18f), t.onFelt, small = small)
  }
}

/**
 * Everyone's rows on the table, the player ("you") first: one line per player with the scoops as arcs on the right
 * when the width allows cards of a readable size, else a card per player, two side by side, with the scoops in words.
 * `names` are the players, `notes` (smaller) what kind of player; `totals` the totals so far; with `onSeat`, a
 * player can be tapped.
 */
@Composable
fun SeatResults(
  arrangements: List<Rows>, score: Score, wheel: Int, names: List<String>, notes: List<String?>, totals: List<Double>,
  modifier: Modifier = Modifier, onSeat: ((Int) -> Unit)? = null,
) {
  BoxWithConstraints(modifier.fillMaxSize()) {
    val seats = arrangements.size
    val nameWidth = textRoom(92)
    // On a wide window the players' lines, as many as there are players, fill the height: their cards as large as the
    // height and the width allow. Each line: its padding (2 above and under), its labels' line (as measured: with a
    // badge, a CJK line is taller than the style's line height; until then a guess), 2 under it, and each row's border
    // room of 2 above and under.
    var labelLine by remember { mutableStateOf<Dp?>(null) }
    val byHeight = ((maxHeight - SEAT_GAP * (seats - 1)) / seats - (labelLine ?: textRoom(22)) - 10.dp) / 1.4f
    val lineCard = minOf((maxWidth - nameWidth - ARROWS_WIDTH - 16.dp - COLUMN_GAP * 3 - SMALL_GAP * 10 - 12.dp) / 13, byHeight, MAX_PLAY)
    if (maxWidth > maxHeight)
      SeatLines(arrangements, score, wheel, names, notes, totals, lineCard.coerceAtLeast(14.dp), nameWidth, onSeat) { h -> if (h > (labelLine ?: 0.dp)) labelLine = h }
    else SeatGrid(arrangements, score, wheel, names, notes, totals, maxWidth, onSeat)
  }
}

/** Each player's row labels, with the result of the player's row against it (null for the player's own). */
@Composable
private fun rowTexts(score: Score, wheel: Int): List<List<Pair<String, Int?>>> {
  val labels = rowLabels()
  val fouled = stringResource(R.string.fouled_short)
  val mine = score.info[0]
  return List(score.info.size) { s ->
    val info = score.info[s]
    List(3) { r ->
      val d = if (s != 0 && info.valid && mine.valid) mine.keys[r].compareTo(info.keys[r]) else null
      (if (info.valid) Cards.rowLabel(info.keys[r], labels, wheel) else fouled) to d
    }
  }
}

/** Under a player's points: what the player won or lost against this one, or the player's own total. */
@Composable
private fun seatSub(s: Int, score: Score, totals: List<Double>): String {
  if (s == 0) return stringResource(R.string.total_so_far, signed(totals[0]))
  val v = score.pairs[0][s]
  return when {
    v > 0 -> stringResource(R.string.vs_you_win, signed(v).drop(1))
    v < 0 -> stringResource(R.string.vs_you_lose, signed(v).drop(1))
    else -> stringResource(R.string.vs_you_even)
  }
}

@Composable
private fun SeatLines(
  arrangements: List<Rows>, score: Score, wheel: Int, names: List<String>, notes: List<String?>, totals: List<Double>, card: Dp,
  nameWidth: Dp, onSeat: ((Int) -> Unit)?, onLabelLine: (Dp) -> Unit = {},
) {
  val t = LocalTable.current
  val seats = arrangements.size
  val texts = rowTexts(score, wheel)
  val density = LocalDensity.current
  // The tallest line, which all take; anew when the cards change size.
  var seatHeight by remember(card) { mutableStateOf(0.dp) }
  Row(Modifier.verticalScroll(rememberScrollState())) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SEAT_GAP)) {
      for (s in 0 until seats) {
        val info = score.info[s]
        Row(
          Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (s == 0) t.onFelt.copy(alpha = 0.12f) else Color.Transparent)
            .let { m -> if (onSeat != null) m.clickable { onSeat(s) } else m }
            .let { m -> if (score.homeRun[s]) m.border(2.dp, t.highlight, RoundedCornerShape(12.dp)) else m }
            .onGloballyPositioned { with(density) { it.size.height.toDp() }.let { h -> if (h > seatHeight) seatHeight = h } }
            .let { if (seatHeight > 0.dp) it.heightIn(min = seatHeight) else it }
            .padding(horizontal = 8.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
        ) {
          SeatName(s, names, notes, score, totals, Modifier.width(nameWidth), compact = true)
          for (r in 0 until 3) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Row(
                Modifier.onGloballyPositioned { with(density) { onLabelLine(it.size.height.toDp()) } },
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  texts[s][r].first, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false,
                  color = if (info.valid) t.onFeltDim else t.bad,
                )
                texts[s][r].second?.let { RowMark(it, small = true) }
              }
              // A row that earns a royalty under the rules.
              val bonus = info.royalties[r] > 0
              Box(if (bonus) Modifier.border(2.dp, t.highlight, RoundedCornerShape(6.dp)).padding(2.dp) else Modifier.padding(2.dp)) {
                RowCards(arrangements[s][r], if (info.valid) info.categories[r] else 0, card, SMALL_GAP)
              }
            }
          }
        }
      }
    }
    val good = t.good
    val bad = t.bad
    val other = t.onFeltDim
    if (score.scoops.any { row -> row.any { it } })
      Column(Modifier.width(ARROWS_WIDTH), horizontalAlignment = Alignment.End) {
        Text(stringArrayResource(R.array.prob_names)[0], style = MaterialTheme.typography.labelMedium, color = t.onFeltDim)
        ScoopArcs(
          score.scoops, seatHeight, good, bad, other,
          Modifier.width(ARROWS_WIDTH).height(seatHeight * seats + SEAT_GAP * (seats - 1)).padding(top = 0.dp),
          topOffset = with(density) { -(MaterialTheme.typography.labelMedium.lineHeight.toDp()) },
        )
      }
    else Spacer(Modifier.width(ARROWS_WIDTH))
  }
}

@Composable
private fun SeatName(s: Int, names: List<String>, notes: List<String?>, score: Score, totals: List<Double>, modifier: Modifier, compact: Boolean = false) {
  val t = LocalTable.current
  val x = score.result[s]
  val color = if (x > 0) t.good else if (x < 0) t.bad else t.onFelt
  if (compact) {
    // Short: the name and the points on one line, the total or the result against the player under them.
    Column(modifier) {
      Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(names[s], Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(signed(x), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
      }
      Text(seatSub(s, score, totals), style = MaterialTheme.typography.bodySmall, color = t.onFeltDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    return
  }
  Column(modifier) {
    Text(names[s], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    notes[s]?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = t.onFeltDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    Text(signed(x), style = MaterialTheme.typography.headlineSmall, color = color)
    Text(seatSub(s, score, totals), style = MaterialTheme.typography.bodySmall, color = t.onFeltDim, maxLines = 2)
    if (score.homeRun[s]) Text(stringResource(R.string.home_run), style = MaterialTheme.typography.labelLarge, color = t.highlight)
  }
}

/**
 * Narrow screens: a block per player, one under another, the full width: the name, points and scoops in a header, then
 * the three rows side by side when the width allows, else each row on its own line.
 */
@Composable
private fun SeatGrid(
  arrangements: List<Rows>, score: Score, wheel: Int, names: List<String>, notes: List<String?>, totals: List<Double>, width: Dp,
  onSeat: ((Int) -> Unit)?,
) {
  val t = LocalTable.current
  val texts = rowTexts(score, wheel)
  // Side by side, the cards fill the width: the block's padding, the gaps between the rows and between the cards, and
  // each row's 2 dp of border room; larger than `other`, as there is nothing beside them here.
  val sideCard = ((width - GRID_PAD * 2 - GRID_COLUMN_GAP * 2 - GRID_CARD_GAP * 10 - 2.dp * 3) / 13).coerceAtMost(MAX_OTHER)
  // The three rows side by side when the cards are not too small and the front's three are as wide as its name ("散牌 Q").
  val sideBySide = sideCard >= 20.dp && rowWidth(3, sideCard, GRID_CARD_GAP) >= textRoom(48)
  val labelWidth = maxOf(72.dp, textRoom(13) * 5.5f)
  val stackCard = ((width - GRID_PAD * 2 - labelWidth - 8.dp - SMALL_GAP * 4) / 5).coerceIn(16.dp, LocalCardSizes.current.play)
  val scoop = stringArrayResource(R.array.prob_names)[0]
  var expandedSeat by rememberSaveable { mutableStateOf<Int?>(null) }
  Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    for (s in arrangements.indices) {
      val info = score.info[s]
      val x = score.result[s]
      val scooped = arrangements.indices.filter { score.scoops[s][it] }
      val by = arrangements.indices.filter { score.scoops[it][s] }
      val expanded = sideBySide && expandedSeat == s
      Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (s == 0) t.onFelt.copy(alpha = 0.14f) else t.slot)
          .let { m -> if (score.homeRun[s]) m.border(2.dp, t.highlight, RoundedCornerShape(12.dp)) else m }
          .clickable {
            // With `onSeat` a tap is for it (an analysis); else it shows the rows one above another.
            if (onSeat != null) onSeat(s) else if (sideBySide) expandedSeat = if (expandedSeat == s) null else s
          }.padding(horizontal = GRID_PAD, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(names[s], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
              notes[s]?.let { Text(it, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall, color = t.onFeltDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
              Text(seatSub(s, score, totals), style = MaterialTheme.typography.bodySmall, color = t.onFeltDim, maxLines = 1)
              if (scooped.isNotEmpty())
                Text("$scoop → " + scooped.joinToString("、") { names[it] }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (s == 0) t.good else t.onFelt, maxLines = 1, overflow = TextOverflow.Ellipsis)
              if (by.isNotEmpty())
                Text(stringResource(R.string.scooped_by, by.joinToString("、") { names[it] }), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (s == 0) t.bad else t.onFeltDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
          if (score.homeRun[s]) Text(stringResource(R.string.home_run), style = MaterialTheme.typography.labelLarge, color = t.highlight)
          Text(signed(x), style = MaterialTheme.typography.headlineSmall, color = if (x > 0) t.good else if (x < 0) t.bad else t.onFelt)
        }
        if (sideBySide && !expanded) {
          // Each row as wide as its cards, its hand's name over it; against another player, the name on the color of
          // the player's result there (won, lost, tied), so it stays one line even over the front's three cards.
          Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(GRID_COLUMN_GAP)) {
            for (r in 0 until 3) {
              Column(Modifier.width(rowWidth(Cards.ROW_SIZES[r], sideCard, GRID_CARD_GAP) + 2.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RowName(texts[s][r].first, texts[s][r].second, info.valid)
                Spacer(Modifier.weight(1f))
                val bonus = info.royalties[r] > 0
                Box(if (bonus) Modifier.border(2.dp, t.highlight, RoundedCornerShape(5.dp)).padding(1.dp) else Modifier.padding(1.dp)) {
                  RowCards(arrangements[s][r], if (info.valid) info.categories[r] else 0, sideCard, GRID_CARD_GAP)
                }
              }
            }
          }
        } else {
          for (r in 0 until 3) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Column(Modifier.width(labelWidth), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(texts[s][r].first, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = if (info.valid) t.onFeltDim else t.bad)
                texts[s][r].second?.let { RowMark(it) }
              }
              val bonus = info.royalties[r] > 0
              Box(if (bonus) Modifier.border(2.dp, t.highlight, RoundedCornerShape(6.dp)).padding(2.dp) else Modifier) {
                RowCards(arrangements[s][r], if (info.valid) info.categories[r] else 0, stackCard, SMALL_GAP)
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Who scooped whom: an arc from the scooper's line to the scooped one's, bulging right, with the head at the scooped
 * player along the curve. Arcs between neighbours share the innermost lane (their spans never overlap); longer spans
 * get lanes further out. Each player's ends are spread over its line, the ones to players above on top, nearer
 * players further out, so that nested arcs do not cross and heads at one player stay apart.
 */
@Composable
private fun ScoopArcs(scoops: List<List<Boolean>>, seatHeight: Dp, good: Color, bad: Color, other: Color, modifier: Modifier, topOffset: Dp = 0.dp) {
  Canvas(modifier) {
    val line = seatHeight.toPx() + SEAT_GAP.toPx()
    val y0 = topOffset.toPx()
    val x0 = 3.dp.toPx()
    val spacing = minOf(seatHeight.toPx() * 0.2f, 12.dp.toPx())
    val arcs = scoops.indices.flatMap { i -> scoops.indices.filter { j -> i != j && scoops[i][j] }.map { j -> i to j } }
    fun partners(i: Int): List<Int> {
      val all = arcs.mapNotNull { (a, b) -> if (a == i) b else if (b == i) a else null }
      return all.filter { it < i }.sortedDescending() + all.filter { it > i }.sortedDescending()
    }
    fun port(i: Int, j: Int): Float {
      val p = partners(i)
      return y0 + line * i + seatHeight.toPx() / 2 + (p.indexOf(j) - (p.size - 1) / 2f) * spacing
    }
    // Arcs between other players first: the player's own are drawn on top.
    for ((i, j) in arcs.sortedBy { (i, j) -> if (i == 0 || j == 0) 1 else 0 }) {
      val lo = minOf(i, j)
      val hi = maxOf(i, j)
      val lane = when (hi - lo) {
        1 -> 0
        2 -> if (lo == 0) 1 else 2
        else -> 3
      }
      val y1 = port(i, j)
      val y2 = port(j, i)
      val dir = if (y2 > y1) 1f else -1f
      // The curve reaches x0 + 0.75 k; the controls also lean toward the other end, so the arc leaves and arrives
      // at a slant and the head follows it.
      val k = (12 + 14 * lane).dp.toPx() / 0.75f
      val v = minOf(0.9f * k, abs(y2 - y1) * 0.35f)
      val c1 = Offset(x0 + k, y1 + dir * v)
      val c2 = Offset(x0 + k, y2 - dir * v)
      val end = Offset(x0, y2)
      val color = if (i == 0) good else if (j == 0) bad else other
      val path = Path().apply {
        moveTo(x0, y1)
        cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
      }
      drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
      // The head points from a point a little before the end, the way the curve is seen to arrive.
      val tt = 0.88f
      val u = 1 - tt
      val near = Offset(
        u * u * u * x0 + 3 * u * u * tt * c1.x + 3 * u * tt * tt * c2.x + tt * tt * tt * end.x,
        u * u * u * y1 + 3 * u * u * tt * c1.y + 3 * u * tt * tt * c2.y + tt * tt * tt * end.y,
      )
      val dx = end.x - near.x
      val dy = end.y - near.y
      val norm = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
      val head = 8.dp.toPx()
      for (angle in listOf(0.45f, -0.45f)) {
        val bx = -(dx / norm) * cos(angle) + (dy / norm) * sin(angle)
        val by = -(dy / norm) * cos(angle) - (dx / norm) * sin(angle)
        drawLine(color, end, Offset(end.x + head * bx, end.y + head * by), 3.dp.toPx(), StrokeCap.Round)
      }
    }
  }
}
