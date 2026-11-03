package io.github.kennyfs.thirteen.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// By the number of cards of a rank: one pair, three of a kind, four of a kind.
private val SAME_RANK_CATEGORY = intArrayOf(0, 0, 2, 4, 8)

/** Practice: the hand being set, then its result and the analysis of the player's hand. */
@Composable
fun PlayScreen(vm: AppViewModel, onRules: () -> Unit) {
  val result = vm.result
  BackHandler(result != null && vm.resultTab == 1) { vm.resultTab = 0 }
  when {
    result == null -> Column(Modifier.fillMaxSize()) {
      PracticeHeader(vm, onRules)
      Arranging(vm.play, vm.gameValues["wheel"]?.toInt() ?: 0, vm.waiting, stringResource(R.string.confirm), Modifier.weight(1f)) { vm.confirm() }
    }
    vm.resultTab == 1 -> MyHandAnalysis(vm, result.first[0])
    else -> Finished(vm, result.first, result.second)
  }
}

@Composable
fun seatName(i: Int) = if (i == 0) stringResource(R.string.you) else stringResource(R.string.seat_pos, i)

/** The hand's number, its rules (a chip that opens the rules page) and everyone's running total. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeHeader(vm: AppViewModel, onRules: () -> Unit) {
  val (hands, totals) = vm.session
  val title = @Composable {
    Text(stringResource(R.string.hand_n, hands + 1), style = MaterialTheme.typography.titleLarge)
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
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    Text(
      signed(value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
      color = if (me && value < 0) BAD else if (me && value > 0) GOOD else MaterialTheme.colorScheme.onSurface,
    )
  }
}


/** What to do next, for the line under the rows, and whether it is a problem (a foul). */
@Composable
private fun hint(vm: Arranger): Pair<String, Boolean> {
  val eval = vm.rowsEval
  val placed = vm.rows.sumOf { it.size }
  val armed = vm.armed
  return when {
    vm.selected.isNotEmpty() -> stringResource(R.string.hint_selected, vm.selected.size) to false
    armed != null -> stringResource(R.string.hint_armed, stringArrayResource(R.array.row_names)[armed], Cards.ROW_SIZES[armed] - vm.rows[armed].size) to false
    placed == 0 -> stringResource(R.string.hint_start) to false
    placed < 13 -> stringResource(R.string.hint_left, 13 - placed) to false
    eval?.valid == true -> eval.royalties.sum().let { if (it > 0) stringResource(R.string.ready_royalty, signed(it).drop(1)) else stringResource(R.string.ready) } to false
    // Rows in order but equal: a foul only under strict rows.
    eval?.valid == false && eval.keys[0] <= eval.keys[1] && eval.keys[1] <= eval.keys[2] -> stringResource(R.string.fouled_strict) to true
    eval?.valid == false -> stringResource(R.string.fouled) to true
    else -> "" to false
  }
}

/**
 * Setting a hand into three rows on the table, for practice and battles. Side by side when the room is wide (the rows
 * on the left, the actions on the right, the hand along the bottom), one above another when it is tall (the hand in
 * lines, the actions in a bar under the table). The rows' cards and the hand's are one size, the largest the room allows
 * up to `LocalCardSizes.play`, which is what the window gives the page with the most above the table (practice's), so a
 * battle's cards are the same.
 */
@Composable
fun Arranging(vm: Arranger, wheel: Int, busy: Boolean, confirmLabel: String, modifier: Modifier = Modifier, onConfirm: () -> Unit) {
  BoxWithConstraints(modifier.fillMaxSize()) {
    val wide = maxWidth > maxHeight * 1.15f
    val fit = arrangeFit(maxWidth, maxHeight, wide, textRoom(14), LocalCardSizes.current.play)
    if (wide) ArrangeWide(vm, wheel, busy, confirmLabel, fit.card, onConfirm)
    else ArrangeTall(vm, wheel, busy, confirmLabel, fit, onConfirm)
  }
}

@Composable
private fun Felt(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  val t = LocalTable.current
  Surface(modifier, shape = MaterialTheme.shapes.medium, color = t.felt, contentColor = t.onFelt, content = content)
}

@Composable
private fun ArrangeWide(vm: Arranger, wheel: Int, busy: Boolean, confirmLabel: String, card: Dp, onConfirm: () -> Unit) {
  Felt(Modifier.fillMaxSize().padding(start = 8.dp, end = 12.dp, bottom = 6.dp)) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp)) {
      val controlsWidth = textRoom(120).coerceIn(120.dp, maxWidth * 0.3f)
      val tagWidth = textRoom(46)
      Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(WIDE_GAP)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0 until 3) RowSlotWide(vm, r, card, tagWidth, wheel)
          }
          HandTray(vm, card, 13)
        }
        // The hint at the top, the buttons at the bottom, down the whole height.
        Column(Modifier.width(controlsWidth).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
          HintBox(vm)
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SortToggle(vm)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FeltIconButton(stringResource(R.string.undo), Icons.AutoMirrored.Filled.Undo, vm.canUndo, Modifier.weight(1f)) { vm.undo() }
              FeltIconButton(stringResource(R.string.take_all), Icons.Outlined.ClearAll, vm.rows.any { it.isNotEmpty() }, Modifier.weight(1f)) { vm.clearRows() }
            }
            ConfirmButton(vm, busy, confirmLabel, Modifier.fillMaxWidth(), onConfirm)
          }
        }
      }
    }
  }
}

@Composable
private fun ArrangeTall(vm: Arranger, wheel: Int, busy: Boolean, confirmLabel: String, fit: ArrangeFit, onConfirm: () -> Unit) {
  Column(Modifier.fillMaxSize()) {
    Felt(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp)) {
      BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp)) {
        val density = LocalDensity.current
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
          Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0 until 3) RowSlotTall(vm, r, fit.card, if (fit.above) null else labelWidth(density), headerHeight(density), wheel)
          }
          Column {
            HintLine(vm, Modifier.padding(top = 6.dp, bottom = 2.dp), maxLines = 1, small = true)
            HandTray(vm, fit.card, fit.perLine)
          }
        }
      }
    }
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      val sortLabel = stringResource(if (vm.sortBySuit) R.string.sort_by_suit_on else R.string.sort_by_rank_on)
      OutlinedIconButton(onClick = { vm.sortBySuit = !vm.sortBySuit }, Modifier.size(52.dp)) { Icon(Icons.Filled.SwapVert, sortLabel) }
      OutlinedIconButton(onClick = vm::undo, Modifier.size(52.dp), enabled = vm.canUndo) {
        Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo))
      }
      Button(
        onClick = onConfirm, Modifier.weight(1f).height(BIG_BUTTON), enabled = !busy && vm.rowsEval?.valid == true,
        shape = RoundedCornerShape(18.dp),
      ) {
        if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text(confirmLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }
    }
  }
}


/** What a row says next to its cards: where the picked cards can go, the row's hand, or how full it is. */
@Composable
private fun rowState(vm: Arranger, r: Int, wheel: Int): Triple<String, Boolean, Boolean> {
  val cards = vm.rows[r]
  val free = Cards.ROW_SIZES[r] - cards.size
  val eval = vm.rowsEval
  val picked = vm.selected.size
  return when {
    picked > 0 && free >= picked -> Triple(stringResource(R.string.put_here, picked), true, false)
    picked > 0 && free > 0 -> Triple(stringResource(R.string.room_for, free), false, true)
    vm.armed == r -> Triple(stringResource(R.string.pick_n, free - picked), true, false)
    free == 0 && eval != null && eval.categories[r] > 0 -> Triple(Cards.rowLabel(eval.keys[r], rowLabels(), wheel), false, false)
    else -> Triple("${cards.size}/${Cards.ROW_SIZES[r]}", false, false)
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
private fun RowSlotWide(vm: Arranger, r: Int, card: Dp, tagWidth: Dp, wheel: Int) {
  val t = LocalTable.current
  val cards = vm.rows[r]
  val eval = vm.rowsEval
  val category = if (cards.size == Cards.ROW_SIZES[r] && eval != null) eval.categories[r] else 0
  val (state, target, dim) = rowState(vm, r, wheel)
  Row(
    Modifier.fillMaxWidth().rowGround(vm, r, target, t).padding(horizontal = 6.dp, vertical = WIDE_PAD),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      stringArrayResource(R.array.row_names)[r], Modifier.width(tagWidth), style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center,
    )
    RowWithSlots(cards, category, Cards.ROW_SIZES[r], card, onCardClick = vm::unplace)
    Text(
      state, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
      color = if (target) t.highlight else if (dim) t.onFeltDim else t.onFelt, fontWeight = if (target) FontWeight.Bold else FontWeight.Medium,
    )
    if (cards.isNotEmpty()) TakeBack(vm, r) else Spacer(Modifier.width(48.dp))
  }
}

@Composable
private fun RowSlotTall(vm: Arranger, r: Int, card: Dp, labelWidth: Dp?, headerHeight: Dp, wheel: Int) {
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
    RowWithSlots(cards, category, Cards.ROW_SIZES[r], card, onCardClick = vm::unplace)
  } else Row(ground, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Column(Modifier.width(labelWidth), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      // No room for the take-back button here: a placed card goes back with a tap (the hint says so), a row with
      // a long press.
      name(Modifier)
      stateText(Modifier, 2)
    }
    RowWithSlots(cards, category, Cards.ROW_SIZES[r], card, onCardClick = vm::unplace)
  }
}

/** A row's label beside its cards in a tall layout, and its header above them. */
private fun labelWidth(d: androidx.compose.ui.unit.Density): Dp = maxOf(72.dp, with(d) { (14 * 4.6f).sp.toDp() })
private fun headerHeight(d: androidx.compose.ui.unit.Density): Dp = maxOf(32.dp, with(d) { 26.sp.toDp() })

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

/** The hint in a box of its own, in the column of buttons of the wide page. */
@Composable
private fun HintBox(vm: Arranger) {
  val t = LocalTable.current
  val (text, bad) = hint(vm)
  Text(
    text, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 6.dp)
      .semantics { liveRegion = LiveRegionMode.Polite },
    style = MaterialTheme.typography.bodyMedium, color = if (bad) t.bad else t.onFelt,
  )
}

@Composable
private fun HintLine(vm: Arranger, modifier: Modifier = Modifier, maxLines: Int = 2, small: Boolean = false) {
  val t = LocalTable.current
  val (text, bad) = hint(vm)
  Text(
    text, modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    style = if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
    color = if (bad) t.bad else t.onFelt, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
  )
}

@Composable
private fun SortToggle(vm: Arranger) {
  val t = LocalTable.current
  ToggleGroup(
    listOf(false to stringResource(R.string.sort_rank), true to stringResource(R.string.sort_suit)), vm.sortBySuit, { vm.sortBySuit = it },
    Modifier.fillMaxWidth(), ToggleColors(t.onFelt, t.felt, t.onFelt.copy(alpha = 0.12f), t.onFelt),
  )
}

@Composable
private fun FeltButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
  val t = LocalTable.current
  OutlinedButton(
    onClick = onClick, modifier.heightIn(min = 48.dp), enabled = enabled,
    border = BorderStroke(1.dp, t.onFelt.copy(alpha = if (enabled) 0.5f else 0.2f)),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = t.onFelt, disabledContentColor = t.onFelt.copy(alpha = 0.38f)),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
  ) {
    if (icon != null) Icon(icon, null, Modifier.size(20.dp).padding(end = 2.dp))
    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** A button of an icon only on the table, named for a screen reader. */
@Composable
private fun FeltIconButton(description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
  val t = LocalTable.current
  OutlinedIconButton(
    onClick = onClick, modifier.height(48.dp), enabled = enabled,
    border = BorderStroke(1.dp, t.onFelt.copy(alpha = if (enabled) 0.5f else 0.2f)),
    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = t.onFelt, disabledContentColor = t.onFelt.copy(alpha = 0.38f)),
  ) { Icon(icon, description) }
}

/** The main action on the table: light on the felt, enabled once the rows can be played. */
@Composable
private fun ConfirmButton(vm: Arranger, busy: Boolean, label: String, modifier: Modifier, onConfirm: () -> Unit) {
  val t = LocalTable.current
  Button(
    onClick = onConfirm, modifier.heightIn(min = BIG_BUTTON), enabled = !busy && vm.rowsEval?.valid == true, shape = RoundedCornerShape(18.dp),
    colors = ButtonDefaults.buttonColors(
      containerColor = t.action, contentColor = t.onAction,
      disabledContainerColor = t.onFelt.copy(alpha = 0.14f), disabledContentColor = t.onFelt.copy(alpha = 0.55f),
    ),
  ) {
    if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp, color = t.onAction)
    else Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
  }
}

/**
 * The cards not yet placed, in one line or several of `perLine`. Tap a card to pick it; a swipe picks every card it
 * passes (or unpicks them, when it starts on a picked card).
 */
@Composable
private fun HandTray(vm: Arranger, card: Dp, perLine: Int, modifier: Modifier = Modifier) {
  val hand = vm.unplaced
  // Each card's bounds in the tray, for swipes.
  val bounds = remember(hand) { mutableMapOf<Int, Rect>() }
  var tray by remember { mutableStateOf<LayoutCoordinates?>(null) }
  val count = hand.groupingBy(Cards::rank).eachCount()
  // Sorted by suit, a wider gap between the suits, which the page's sizes leave room for in a single line.
  val single = perLine >= 13
  Column(
    modifier.fillMaxWidth().onGloballyPositioned { tray = it }.swipeSelect(vm, hand, bounds).padding(top = card * 0.22f),
    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    for (line in hand.chunked(perLine.coerceAtLeast(1))) {
      Row(verticalAlignment = Alignment.Bottom) {
        for ((i, c) in line.withIndex()) {
          if (i > 0) Spacer(Modifier.width(if (single && vm.sortBySuit && Cards.suit(line[i - 1]) != Cards.suit(c)) SUIT_GAP else HAND_GAP))
          // Sorted by rank, pairs, trips and quads are tinted (light) as their category.
          val n = if (!vm.sortBySuit) count.getValue(Cards.rank(c)) else 1
          CardFace(
            c, card, Modifier.onGloballyPositioned { coords -> tray?.let { bounds[c] = it.localBoundingBoxOf(coords) } },
            tint = SAME_RANK_CATEGORY[n], light = true, selected = c in vm.selected, onClick = { vm.toggle(c) },
          )
        }
      }
    }
    if (hand.isEmpty()) Spacer(Modifier.height(cardHeight(card)))
  }
}

/**
 * A swipe over the hand selects every card it passes when the card it starts on is not selected, and unselects them
 * when it is, so runs of cards (a straight, a flush sorted by suit) take one stroke. A tap still toggles one card.
 */
private fun Modifier.swipeSelect(vm: Arranger, hand: List<Int>, bounds: Map<Int, Rect>): Modifier =
  pointerInput(hand) {
    // The card under the point, or the nearest one.
    fun at(p: Offset): Int? = hand.indices.filter { bounds[hand[it]] != null }.minByOrNull { i ->
      val b = bounds.getValue(hand[i])
      val dx = if (p.x < b.left) b.left - p.x else if (p.x > b.right) p.x - b.right else 0f
      val dy = if (p.y < b.top) b.top - p.y else if (p.y > b.bottom) p.y - b.bottom else 0f
      dx * dx + dy * dy
    }
    var on = true
    var last: Int? = null
    detectDragGestures(
      onDragStart = { o ->
        last = at(o)?.also { i ->
          on = hand[i] !in vm.selected
          vm.select(listOf(hand[i]), on)
        }
      },
      onDragEnd = { last = null; vm.selectionDone() },
      onDragCancel = { last = null; vm.selectionDone() },
    ) { change, _ ->
      change.consume()
      val from = last ?: return@detectDragGestures
      val i = at(change.position) ?: return@detectDragGestures
      if (i != from) {
        vm.select((minOf(from, i)..maxOf(from, i)).map { hand[it] }, on)
        last = i
      }
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
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(stringResource(R.string.hand_n, vm.session.first), style = MaterialTheme.typography.titleLarge)
        Text(
          stringResource(R.string.this_hand, signed(mine)), style = MaterialTheme.typography.headlineSmall,
          color = if (mine > 0) GOOD else if (mine < 0) BAD else MaterialTheme.colorScheme.onSurface,
        )
        Text(stringResource(R.string.total_so_far, signed(vm.session.second[0])), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        if (wide) {
          HandKeepers(vm, iconsOnly = true)
          ResultActions(vm, compact = true)
        }
      }
      Felt(Modifier.weight(1f).fillMaxWidth().padding(start = 8.dp, end = 12.dp, bottom = if (wide) 6.dp else 0.dp)) {
        SeatResults(arrangements, score, vm.gameValues["wheel"]?.toInt() ?: 0, names, notes, vm.session.second, Modifier.padding(if (wide) 6.dp else 8.dp))
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
  FilledTonalButton(onClick = { vm.showGameAnalysis() }, modifier.heightIn(min = height)) {
    Icon(Icons.Outlined.Analytics, null, Modifier.padding(end = 6.dp))
    Text(stringResource(R.string.analyze_my_hand), maxLines = 1)
  }
  Button(onClick = { vm.deal() }, modifier.heightIn(min = height), shape = RoundedCornerShape(18.dp)) {
    Text(stringResource(R.string.next_hand), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
  }
}

/** The analysis of the player's hand of the finished game, the player's rows pinned. */
@Composable
private fun MyHandAnalysis(vm: AppViewModel, mine: Rows) {
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

/** A row's result for the player against another's: "You win", "You lose", "Tie". */
@Composable
private fun RowMark(d: Int, small: Boolean = false) {
  val t = LocalTable.current
  when {
    d > 0 -> Badge(stringResource(R.string.row_win), Color(0xFFD6EFD8), Color(0xFF1E5E22), small = small)
    d < 0 -> Badge(stringResource(R.string.row_lose), Color(0xFFF8D9D6), Color(0xFF8C1D18), small = small)
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
    // The result's cards are the `other` size, as everywhere else, unless the width has less room.
    val lineCard = minOf(
      (maxWidth - nameWidth - ARROWS_WIDTH - 16.dp - COLUMN_GAP * 3 - SMALL_GAP * 10) / 13,
      LocalCardSizes.current.other,
    )
    if (maxWidth > maxHeight) SeatLines(arrangements, score, wheel, names, notes, totals, lineCard.coerceAtLeast(14.dp), nameWidth, onSeat)
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
  nameWidth: Dp, onSeat: ((Int) -> Unit)?,
) {
  val t = LocalTable.current
  val seats = arrangements.size
  val texts = rowTexts(score, wheel)
  val density = LocalDensity.current
  var seatHeight by remember { mutableStateOf(0.dp) }
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
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
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
 * Narrow screens: a block per player, one under another, the full width: the name and points, then each row's cards
 * with its hand and the result against the player's beside them; the scoops in words.
 */
@Composable
private fun SeatGrid(
  arrangements: List<Rows>, score: Score, wheel: Int, names: List<String>, notes: List<String?>, totals: List<Double>, width: Dp,
  onSeat: ((Int) -> Unit)?,
) {
  val t = LocalTable.current
  val texts = rowTexts(score, wheel)
  val labelWidth = maxOf(72.dp, textRoom(13) * 5.5f)
  val card = ((width - 20.dp - labelWidth - 8.dp - SMALL_GAP * 4) / 5).coerceIn(16.dp, LocalCardSizes.current.play)
  val scoop = stringArrayResource(R.array.prob_names)[0]
  Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    for (s in arrangements.indices) {
      val info = score.info[s]
      val x = score.result[s]
      Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (s == 0) t.onFelt.copy(alpha = 0.14f) else t.slot)
          .let { m -> if (score.homeRun[s]) m.border(2.dp, t.highlight, RoundedCornerShape(12.dp)) else m }
          .let { m -> if (onSeat != null) m.clickable { onSeat(s) } else m }.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(names[s], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
              notes[s]?.let { Text(it, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall, color = t.onFeltDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Text(seatSub(s, score, totals), style = MaterialTheme.typography.bodySmall, color = t.onFeltDim, maxLines = 1)
          }
          if (score.homeRun[s]) Text(stringResource(R.string.home_run), style = MaterialTheme.typography.labelLarge, color = t.highlight)
          Text(signed(x), style = MaterialTheme.typography.headlineSmall, color = if (x > 0) t.good else if (x < 0) t.bad else t.onFelt)
        }
        val scooped = arrangements.indices.filter { score.scoops[s][it] }
        val by = arrangements.indices.filter { score.scoops[it][s] }
        if (scooped.isNotEmpty())
          Text("$scoop → " + scooped.joinToString("、") { names[it] }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (s == 0) t.good else t.onFelt)
        if (by.isNotEmpty())
          Text(stringResource(R.string.scooped_by, by.joinToString("、") { names[it] }), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (s == 0) t.bad else t.onFeltDim)
        for (r in 0 until 3) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.width(labelWidth), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(texts[s][r].first, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = if (info.valid) t.onFeltDim else t.bad)
              texts[s][r].second?.let { RowMark(it) }
            }
            val bonus = info.royalties[r] > 0
            Box(if (bonus) Modifier.border(2.dp, t.highlight, RoundedCornerShape(6.dp)).padding(2.dp) else Modifier) {
              RowCards(arrangements[s][r], if (info.valid) info.categories[r] else 0, card, SMALL_GAP)
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
