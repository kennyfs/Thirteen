package io.github.kennyfs.thirteen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kennyfs.thirteen.AppViewModel
import io.github.kennyfs.thirteen.Arranger
import io.github.kennyfs.thirteen.Arrangement as Rows
import io.github.kennyfs.thirteen.Cards
import io.github.kennyfs.thirteen.R
import io.github.kennyfs.thirteen.Score
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// By the number of cards of a rank: one pair, three of a kind, four of a kind.
private val SAME_RANK_CATEGORY = intArrayOf(0, 0, 2, 4, 8)

/** Text colors for good and bad outcomes, readable on the theme's background. */
val GOOD: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)
val BAD: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFE57373) else Color(0xFFC62828)

/** "+3", "−1.5": no decimals for whole numbers. */
fun signed(x: Double): String {
  val a = abs(x)
  return (if (x >= 0) "+" else "−") + if (a == kotlin.math.floor(a)) "%.0f".format(a) else "%.2f".format(a)
}

/** Buttons with less padding, for rows and columns short of room. */
val COMPACT = PaddingValues(horizontal = 10.dp, vertical = 6.dp)

@Composable
fun PlayScreen(vm: AppViewModel) {
  val result = vm.result
  BackHandler(result != null && vm.resultTab == 1) { vm.resultTab = 0 }
  Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
    if (result == null) {
      val (hands, totals) = vm.session
      val you = stringResource(R.string.you)
      Text(
        buildString {
          append(stringResource(R.string.hand_n, hands + 1))
          if (vm.gameValues.isNotEmpty()) append(" · " + stringResource(R.string.rules_label, vm.rulesName(vm.gameValues)))
          for (i in totals.indices) append("   " + (if (i == 0) you else seatName(i)) + " " + signed(totals[i]))
        },
        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
      )
      Arranging(vm.play, vm.gameValues["wheel"]?.toInt() ?: 0, vm.waiting) { vm.confirm() }
    } else Finished(vm, result.first, result.second)
  }
}

@Composable
private fun seatName(i: Int) = stringResource(R.string.seat_pos, i)

private val HAND_GAP = 3.dp
private val SUIT_GAP = 8.dp

/**
 * Three rows to fill and the controls on top, the hand along the bottom. The cards are as large as the room allows:
 * the hand's 13 fill the width (up to a third of the height), the rows' 5 the width left of the controls and the height
 * left above the hand. The hand gives way when the controls (measured) or the rows' labels need more height, as with
 * large fonts on a short screen.
 */
@Composable
fun Arranging(vm: Arranger, wheel: Int, waiting: Boolean, onConfirm: () -> Unit) {
  BoxWithConstraints(Modifier.fillMaxSize().padding(top = 4.dp)) {
    val density = LocalDensity.current
    // A row's label on two lines (its name over its state) or, when three such rows would take over half the height,
    // on one.
    val oneLine = (textRoom(34) + 10.dp) * 3 + 8.dp > maxHeight * 0.5f
    val labelWidth = textRoom(if (oneLine) 120 else 76)
    // Side by side when one above another would take over half the height: a line and three buttons.
    val buttonHeight = maxOf(40.dp, textRoom(20) + 12.dp)
    val columns = if (textRoom(20) + buttonHeight * 3 + 12.dp > maxHeight * 0.5f) 2 else 1
    val controlsWidth = textRoom(120) * columns
    var controlsHeight by remember { mutableStateOf(CONTROLS_HEIGHT) }
    // The rows' labels, their padding, and the gaps between rows.
    val rowsNeeded = (textRoom(if (oneLine) 17 else 34) + 10.dp) * 3 + 8.dp
    val topNeeded = maxOf(controlsHeight, rowsNeeded)
    // The same size sorted either way (sorted by suit, the gaps between suits come out of the others), so the rows
    // above keep their height. handHeight below is about 1.61 cards and 6 dp.
    val handCard = minOf((maxWidth - HAND_GAP * 12) / 13, maxHeight * 0.34f / 1.6f, (maxHeight - topNeeded - 6.dp) / 1.61f, 96.dp)
      .coerceAtLeast(12.dp)
    // Room for a selected card to rise.
    val handHeight = cardHeight(handCard) * 1.15f + 6.dp
    val width = maxWidth
    val rowsHeight = (maxHeight - handHeight).coerceAtLeast(0.dp)
    val slotCard = minOf(
      (maxWidth - controlsWidth - 8.dp - labelWidth - 8.dp - CARD_GAP * 4) / 5,
      ((rowsHeight - 8.dp) / 3 - 8.dp) / 1.4f,
      104.dp,
    ).coerceAtLeast(12.dp)
    Column {
      Row(Modifier.fillMaxWidth().height(rowsHeight), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          for (r in 0 until 3) RowSlot(vm, r, slotCard, labelWidth, oneLine, wheel)
        }
        // Scrolls only if even the smallest hand leaves too little room.
        Box(Modifier.width(controlsWidth).fillMaxHeight().verticalScroll(rememberScrollState())) {
          Controls(vm, columns, waiting, onConfirm, Modifier.fillMaxWidth().onSizeChanged { controlsHeight = with(density) { it.height.toDp() } })
        }
      }
      val hand = vm.unplaced
      // Each card's left and right edges in the hand row, for swipes.
      val edges = remember(hand) { mutableMapOf<Int, Pair<Float, Float>>() }
      // Sorted by suit, a suit's first card follows a wider gap; when the width is short of the usual gaps, all
      // shrink, the suits' staying four times the others.
      val breaks = if (vm.sortBySuit) hand.indices.count { it > 0 && Cards.suit(hand[it - 1]) != Cards.suit(hand[it]) } else 0
      val gaps = (hand.size - 1 - breaks).coerceAtLeast(0)
      val room = width - handCard * hand.size
      val (gap, suitGap) =
        if (HAND_GAP * gaps + SUIT_GAP * breaks <= room) HAND_GAP to SUIT_GAP
        else (room / (gaps + 4 * breaks).coerceAtLeast(1)).coerceAtLeast(0.dp).let { it to it * 4 }
      Row(
        Modifier.fillMaxWidth().height(handHeight).padding(top = 6.dp).swipeSelect(vm, hand, edges),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom,
      ) {
        // Sorted by rank, pairs, trips and quads are tinted (light) as their category; sorted by suit, suits are spaced.
        val count = hand.groupingBy(Cards::rank).eachCount()
        for ((i, c) in hand.withIndex()) {
          if (i > 0) Spacer(Modifier.width(if (vm.sortBySuit && Cards.suit(hand[i - 1]) != Cards.suit(c)) suitGap else gap))
          val n = if (!vm.sortBySuit) count.getValue(Cards.rank(c)) else 1
          CardFace(
            c, handCard, Modifier.onGloballyPositioned { edges[c] = it.positionInParent().x.let { x -> x to x + it.size.width } },
            tint = SAME_RANK_CATEGORY[n], light = true, selected = c in vm.selected, onClick = { vm.toggle(c) },
          )
        }
      }
    }
  }
}

/**
 * A swipe over the hand selects every card it passes when the card it starts on is not selected, and unselects them
 * when it is, so runs of cards (a straight, a flush sorted by suit) take one stroke. A tap still toggles one card.
 */
private fun Modifier.swipeSelect(vm: Arranger, hand: List<Int>, edges: Map<Int, Pair<Float, Float>>): Modifier =
  pointerInput(hand) {
    // The card under x, or the nearest one.
    fun at(x: Float): Int? = hand.indices.filter { edges[hand[it]] != null }.minByOrNull { i ->
      val (l, r) = edges.getValue(hand[i])
      if (x < l) l - x else if (x > r) x - r else 0f
    }
    var on = true
    var last: Int? = null
    detectDragGestures(
      onDragStart = { o ->
        last = at(o.x)?.also { i ->
          on = hand[i] !in vm.selected
          vm.select(listOf(hand[i]), on)
        }
      },
      onDragEnd = { last = null; vm.selectionDone() },
      onDragCancel = { last = null; vm.selectionDone() },
    ) { change, _ ->
      change.consume()
      val from = last ?: return@detectDragGestures
      val i = at(change.position.x) ?: return@detectDragGestures
      if (i != from) {
        vm.select((minOf(from, i)..maxOf(from, i)).map { hand[it] }, on)
        last = i
      }
    }
  }

// The controls' height before they are measured: a line and three buttons at the default font size.
private val CONTROLS_HEIGHT = 156.dp

/** Whether the rows can be played, and the buttons: one above another, or two side by side. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(vm: Arranger, columns: Int, waiting: Boolean, onConfirm: () -> Unit, modifier: Modifier) {
  FlowRow(
    modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
    maxItemsInEachRow = columns,
  ) {
    val eval = vm.rowsEval
    val placed = vm.rows.sumOf { it.size }
    Text(
      when {
        placed < 13 -> stringResource(R.string.cards_to_place, 13 - placed)
        eval?.valid == true -> eval.royalties.sum().let { if (it > 0) stringResource(R.string.ready_royalty, signed(it).drop(1)) else stringResource(R.string.ready) }
        // Rows in order but equal: a foul only under strict rows.
        eval?.valid == false && eval!!.keys[0] <= eval.keys[1] && eval.keys[1] <= eval.keys[2] -> stringResource(R.string.fouled_strict)
        eval?.valid == false -> stringResource(R.string.fouled)
        else -> ""
      },
      color = when (eval?.valid) {
        false -> BAD
        true -> GOOD
        null -> MaterialTheme.colorScheme.onSurface
      },
      modifier = Modifier.fillMaxWidth(),
      style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedButton(onClick = { vm.sortBySuit = !vm.sortBySuit }, Modifier.weight(1f), contentPadding = COMPACT) {
      Text(stringResource(if (vm.sortBySuit) R.string.sort_by_rank else R.string.sort_by_suit))
    }
    OutlinedButton(onClick = vm::clearRows, Modifier.weight(1f), contentPadding = COMPACT) { Text(stringResource(R.string.clear_rows)) }
    Button(onClick = onConfirm, Modifier.weight(1f), enabled = !waiting, contentPadding = COMPACT) {
      if (waiting) CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
      else Text(stringResource(R.string.confirm))
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowSlot(vm: Arranger, r: Int, card: Dp, labelWidth: Dp, oneLine: Boolean, wheel: Int) {
  val cards = vm.rows[r]
  val full = cards.size == Cards.ROW_SIZES[r]
  val eval = vm.rowsEval
  val category = if (full && eval != null) eval.categories[r] else 0
  val armed = vm.armed == r
  val colors = MaterialTheme.colorScheme
  Row(
    Modifier.fillMaxWidth().heightIn(min = cardHeight(card) + 8.dp)
      .background(if (armed) colors.primaryContainer else Color.Transparent, RoundedCornerShape(8.dp))
      .border(if (armed) 2.dp else 1.dp, if (armed) colors.primary else colors.outline, RoundedCornerShape(8.dp))
      .combinedClickable(onLongClick = { vm.clearRow(r) }) { vm.tapRow(r) }.padding(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val name = @Composable {
      Text(
        stringArrayResource(R.array.row_names)[r], style = MaterialTheme.typography.labelMedium, maxLines = 1,
        color = if (armed) colors.onPrimaryContainer else colors.onSurface,
      )
    }
    val state = @Composable { modifier: Modifier ->
      Text(
        when {
          armed -> stringResource(R.string.pick_n, Cards.ROW_SIZES[r] - cards.size - vm.selected.size)
          category > 0 -> Cards.rowLabel(eval!!.keys[r], rowLabels(), wheel)
          else -> "${cards.size}/${Cards.ROW_SIZES[r]}"
        },
        modifier, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = if (armed) colors.primary else colors.onSurfaceVariant,
        fontWeight = if (armed) FontWeight.Medium else FontWeight.Normal,
      )
    }
    if (oneLine) Row(Modifier.width(labelWidth), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      name()
      state(Modifier.weight(1f, fill = false))
    }
    else Column(Modifier.width(labelWidth)) {
      name()
      state(Modifier)
    }
    RowCards(cards, category, card, onCardClick = vm::unplace)
  }
}

/**
 * The finished hand: a line with the buttons, then everyone's rows or the analysis of the player's hand. The buttons
 * share the line with the hand's title, as height is what a landscape screen is short of.
 */
@Composable
private fun Finished(vm: AppViewModel, arrangements: List<Rows>, score: Score) {
  val analysisShown = vm.resultTab == 1
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      stringResource(R.string.hand_n, vm.session.first) + if (vm.gameValues.isNotEmpty()) " · " + vm.rulesName(vm.gameValues) else "",
      Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
    )
    Text(
      stringResource(R.string.this_hand, signed(score.result[0])),
      color = if (score.result[0] >= 0) GOOD else BAD, fontWeight = FontWeight.Medium,
    )
    OutlinedButton(onClick = { if (analysisShown) vm.resultTab = 0 else vm.showGameAnalysis() }, contentPadding = COMPACT) {
      Text(stringResource(if (analysisShown) R.string.tab_result else R.string.analyze_my_hand))
    }
    Button(onClick = { vm.deal() }, contentPadding = COMPACT) { Text(stringResource(R.string.next_hand)) }
  }
  if (!analysisShown) {
    val names = List(4) { if (it == 0) stringResource(R.string.you) else seatName(it) }
    val notes = List(4) { if (it == 0) null else vm.settings.seatName(vm.seats[it - 1]) }
    SeatResults(arrangements, score, vm.gameValues["wheel"]?.toInt() ?: 0, names, notes, vm.session.second)
  }
  else {
    val a = vm.gameAnalysis
    if (a == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
    else CandidateList(a, arrangements[0], expandBest = true, legacyNote(vm, a), Modifier.padding(top = 4.dp))
  }
}

private val ARROWS_WIDTH = 56.dp
private val SEAT_GAP = 2.dp
private val COLUMN_GAP = 8.dp
private val LABEL_STYLE = TextStyle(fontSize = 11.sp, lineHeight = 14.sp)

/**
 * One line per player, the player ("you") first: who, the points, then the three rows side by side, and the scoops as
 * arcs on the right. The cards take the most room the width and height allow; each column is as wide as its cards or
 * its widest label. A seat's name is `names`, with `notes` after it in smaller type; `totals` are the totals so far;
 * with `onSeat`, a line can be tapped.
 */
@Composable
fun SeatResults(
  arrangements: List<Rows>, score: Score, wheel: Int, names: List<String>, notes: List<String?>, totals: List<Double>,
  onSeat: ((Int) -> Unit)? = null,
) {
  val seats = arrangements.size
  val labels = rowLabels()
  val colors = MaterialTheme.colorScheme
  val good = GOOD
  val bad = BAD
  val fouled = stringResource(R.string.fouled_short)
  val mine = score.info[0]
  // Each player's row labels, with the mark against the player's row: ✓ the player wins it, ✗ loses it.
  val rowTexts = List(seats) { s ->
    val info = score.info[s]
    List(3) { r ->
      val d = if (s != 0 && info.valid && mine.valid) mine.keys[r].compareTo(info.keys[r]) else null
      val label = if (info.valid) Cards.rowLabel(info.keys[r], labels, wheel) else fouled
      label to d
    }
  }
  fun mark(d: Int?) = when {
    d == null -> ""
    d > 0 -> " ✓"
    d < 0 -> " ✗"
    else -> " ="
  }
  val measurer = rememberTextMeasurer()
  val density = LocalDensity.current
  // The text left of the cards as last laid out (its estimate below can fall short, as with a large font).
  var textHeight by remember { mutableStateOf(0.dp) }
  BoxWithConstraints(Modifier.fillMaxSize().padding(top = 4.dp)) {
    val labelWidths = List(3) { r ->
      with(density) { (0 until seats).maxOf { s -> measurer.measure(rowTexts[s][r].let { it.first + mark(it.second) }, LABEL_STYLE).size.width }.toDp() }
    }
    val nameWidth = textRoom(84)
    fun columnWidth(r: Int, card: Dp) = maxOf(rowWidth(Cards.ROW_SIZES[r], card) + 2.dp, labelWidths[r])
    fun tableWidth(card: Dp) = 8.dp + nameWidth + COLUMN_GAP * 3 + (0 until 3).fold(0.dp) { sum, r -> sum + columnWidth(r, card) }
    val labelLine = textRoom(14)
    // The text left of the cards: name, points, against the player, and a home run.
    val textBlock = textRoom(48) + if (score.homeRun.any { it }) textRoom(14) else 0.dp
    var card = 72.dp
    while (card > 12.dp && tableWidth(card) + ARROWS_WIDTH > maxWidth) card -= 0.5.dp
    card = minOf(card, ((maxHeight - SEAT_GAP * (seats - 1)) / seats - 6.dp - labelLine - 2.dp) / 1.4f).coerceAtLeast(12.dp)
    val seatHeight = maxOf(textBlock, textHeight, labelLine + cardHeight(card) + 2.dp) + 6.dp
    Row(Modifier.verticalScroll(rememberScrollState())) {
      Column(verticalArrangement = Arrangement.spacedBy(SEAT_GAP)) {
        for (s in 0 until seats) {
          val info = score.info[s]
          Row(
            Modifier.width(tableWidth(card)).height(seatHeight)
              .background(if (s == 0) colors.secondaryContainer else Color.Transparent, RoundedCornerShape(6.dp))
              .let { m -> if (onSeat != null) m.clip(RoundedCornerShape(6.dp)).clickable { onSeat(s) } else m }
              .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
          ) {
            Column(
              Modifier.width(nameWidth).wrapContentHeight(unbounded = true)
                .onSizeChanged { with(density) { it.height.toDp() }.let { h -> if (h > textHeight) textHeight = h } },
            ) {
              Row(verticalAlignment = Alignment.Bottom) {
                Text(names[s], Modifier.weight(1f, fill = false), fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                notes[s]?.let { note ->
                  Text(
                    " " + note, Modifier.weight(1f, fill = false), fontSize = 10.sp, lineHeight = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariant,
                  )
                }
              }
              Row {
                Text(
                  signed(score.result[s]), Modifier.alignByBaseline(), fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp,
                  color = if (score.result[s] > 0) good else if (score.result[s] < 0) bad else colors.onSurface,
                )
                Text(
                  "  " + stringResource(R.string.total_so_far, signed(totals[s])), Modifier.alignByBaseline(), fontSize = 10.sp, lineHeight = 14.sp,
                  maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariant,
                )
              }
              // What the player won from this opponent.
              if (s != 0) {
                val vsYou = score.pairs[0][s]
                Text(
                  stringResource(R.string.vs_you, signed(vsYou)), fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1,
                  color = if (vsYou > 0) good else if (vsYou < 0) bad else colors.onSurfaceVariant,
                )
              }
              if (score.homeRun[s])
                Text(stringResource(R.string.home_run), fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, color = good)
            }
            for (r in 0 until 3) {
              val category = if (info.valid) info.categories[r] else 0
              Column(Modifier.width(columnWidth(r, card))) {
                val (label, d) = rowTexts[s][r]
                Text(
                  buildAnnotatedString {
                    withStyle(SpanStyle(color = if (info.valid) colors.onSurfaceVariant else bad)) { append(label) }
                    withStyle(SpanStyle(color = if (d == null || d == 0) colors.onSurfaceVariant else if (d > 0) good else bad)) { append(mark(d)) }
                  },
                  style = LABEL_STYLE, maxLines = 1, softWrap = false,
                )
                // A row that earns a royalty under the rules.
                val bonus = info.royalties[r] > 0
                Box(
                  if (bonus) Modifier.border(2.dp, colors.tertiary, RoundedCornerShape(4.dp)).padding(1.dp) else Modifier.padding(1.dp),
                ) { RowCards(arrangements[s][r], category, card) }
              }
            }
          }
        }
      }
      ScoopArcs(
        score.scoops, seatHeight, good, bad, colors.onSurfaceVariant,
        Modifier.width(ARROWS_WIDTH).height(seatHeight * seats + SEAT_GAP * (seats - 1)),
      )
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
private fun ScoopArcs(scoops: List<List<Boolean>>, seatHeight: Dp, good: Color, bad: Color, other: Color, modifier: Modifier) {
  Canvas(modifier) {
    val line = seatHeight.toPx() + SEAT_GAP.toPx()
    val x0 = 3.dp.toPx()
    val spacing = minOf(seatHeight.toPx() * 0.2f, 9.dp.toPx())
    val arcs = scoops.indices.flatMap { i -> scoops.indices.filter { j -> i != j && scoops[i][j] }.map { j -> i to j } }
    fun partners(i: Int): List<Int> {
      val all = arcs.mapNotNull { (a, b) -> if (a == i) b else if (b == i) a else null }
      return all.filter { it < i }.sortedDescending() + all.filter { it > i }.sortedDescending()
    }
    fun port(i: Int, j: Int): Float {
      val p = partners(i)
      return line * i + seatHeight.toPx() / 2 + (p.indexOf(j) - (p.size - 1) / 2f) * spacing
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
      val k = (10 + 13 * lane).dp.toPx() / 0.75f
      val v = minOf(0.9f * k, abs(y2 - y1) * 0.35f)
      val c1 = Offset(x0 + k, y1 + dir * v)
      val c2 = Offset(x0 + k, y2 - dir * v)
      val end = Offset(x0, y2)
      val color = if (i == 0) good else if (j == 0) bad else other
      val path = Path().apply {
        moveTo(x0, y1)
        cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
      }
      drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
      // The head points from a point a little before the end, the way the curve is seen to arrive.
      val t = 0.88f
      val u = 1 - t
      val near = Offset(
        u * u * u * x0 + 3 * u * u * t * c1.x + 3 * u * t * t * c2.x + t * t * t * end.x,
        u * u * u * y1 + 3 * u * u * t * c1.y + 3 * u * t * t * c2.y + t * t * t * end.y,
      )
      val dx = end.x - near.x
      val dy = end.y - near.y
      val norm = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
      val head = 6.dp.toPx()
      for (angle in listOf(0.45f, -0.45f)) {
        val bx = -(dx / norm) * cos(angle) + (dy / norm) * sin(angle)
        val by = -(dy / norm) * cos(angle) - (dx / norm) * sin(angle)
        drawLine(color, end, Offset(end.x + head * bx, end.y + head * by), 2.dp.toPx(), StrokeCap.Round)
      }
    }
  }
}
