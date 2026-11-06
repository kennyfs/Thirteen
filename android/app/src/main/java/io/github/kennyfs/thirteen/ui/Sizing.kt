package io.github.kennyfs.thirteen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The sizes of card, the same on the pages that follow one another, so the cards do not change size from page to page:
 * `play` for arranging a practice hand, which the fingers pick, so as large as the room allows; `battle` for arranging
 * a battle's hand (and the rows waiting for the others), which has a header of its own;
 * `other` for what is only looked at, 13 cards in three rows side by side: results, the analysis and what pops up; and
 * `record` for the records' lists, which have nothing beside their cards. Worked out once for the window by [cardSizes].
 */
class CardSizes(val play: Dp, val other: Dp, val record: Dp = other, val battle: Dp = play)

val LocalCardSizes = compositionLocalOf { CardSizes(44.dp, 26.dp) }

/** The gap inside a row of the three side by side, and between the rows. */
val LINE_GAP = 2.dp
val LINE_GROUP_GAP = 8.dp
/** What a page keeps clear at each side, for the lists of cards. */
val PAGE_PAD = 16.dp
/** The height of the result page's header on a wide window. */
val RESULT_HEADER = 48.dp
val MAX_OTHER = 56.dp
private val MAX_RECORD = 64.dp
val MAX_PLAY = 100.dp

private fun Dp.down(): Dp = (floor(value * 2) / 2).dp

/** The width at which 13 cards in three rows, side by side, fill `width` (rounded down: never wider than `width`). */
fun lineCard(width: Dp): Dp = ((width - LINE_GAP * 10 - LINE_GROUP_GAP * 2) / 13).down().coerceAtLeast(8.dp)

/** The three rows of a play, side by side, each card as large as `other` allows in the room there is. */
@Composable
fun ThreeRows(rows: List<List<Int>>, categories: List<Int>?, modifier: Modifier = Modifier, valid: Boolean = true) {
  BoxWithConstraints(modifier) {
    val card = minOf(LocalCardSizes.current.other, lineCard(maxWidth))
    Row(horizontalArrangement = Arrangement.spacedBy(LINE_GROUP_GAP)) {
      for (r in 0 until 3) RowCards(rows[r], if (valid) categories?.getOrNull(r) ?: 0 else 0, card, LINE_GAP)
    }
  }
}

// ---- Arranging ----

val HAND_GAP = 4.dp
val SUIT_GAP = 12.dp
/** Between the hand's lines; the closer gap when the suits' lines fit only so. */
val HAND_LINE_GAP = 6.dp
val HAND_LINE_GAP_TIGHT = 2.dp
val ROW_PAD = 6.dp
/** Between the rows and the buttons of the wide page. */
val ROWS_GAP = 10.dp
/** Between the rows one above another of the wide page. */
val ROW_STACK_GAP = 6.dp

/** The height of the wide page's buttons. */
val BAR_HEIGHT = 44.dp

/** How the arranging page is laid out: the card's width (the rows' and the hand's), and how it is arranged. */
class ArrangeFit(val card: Dp, val above: Boolean, val perLine: Int)

/** `u` is textRoom(14): what a 14sp text takes, which grows with the font size. */
fun arrangeFit(areaW: Dp, areaH: Dp, wide: Boolean, u: Dp, maxCard: Dp = MAX_PLAY): ArrangeFit {
  fun room(sp: Int) = u * (sp / 14f)
  if (wide) {
    // Felt padded 8 and 12 outside (6 under) and 6 inside. The three rows one above another, each with its name at its
    // left, the buttons at their right, then the hand in a line of 13 as wide as the felt.
    val w = areaW - 20.dp - 12.dp
    val h = areaH - 6.dp - 12.dp
    val labels = maxOf(72.dp, room(64)) + 8.dp
    val buttons = maxOf(200.dp, room(190))
    val byRowsWidth = (w - buttons - ROWS_GAP - labels - ROW_PAD * 2 - CARD_GAP * 4) / 5
    val byHandWidth = (w - HAND_GAP * 12 - (SUIT_GAP - HAND_GAP) * 3) / 13
    // The rows' padding and gaps, the gap above the hand; three rows' cards, then the hand's and a picked card's lift.
    val fixed = ROW_PAD * 6 + ROW_STACK_GAP * 2 + 6.dp
    var byHeight = (h - fixed) / (1.4f * 4 + 0.22f)
    // A label taller than the card makes its row taller.
    val label = room(40) + 2.dp
    if (byHeight * 1.4f < label) byHeight = (h - fixed - label * 3) / (1.4f + 0.22f)
    val s = minOf(byRowsWidth, byHandWidth, byHeight, maxCard).down().coerceAtLeast(18.dp)
    return ArrangeFit(s, false, 13)
  }
  // Felt padded 8 outside and 8 inside; the bar of buttons under it. The hand in one line or, at most, two.
  val w = areaW - 32.dp
  val h = areaH - (BIG_BUTTON + 16.dp) - 16.dp
  val header = maxOf(32.dp, room(26))
  var best: ArrangeFit? = null
  for (perLine in listOf(13, 7)) {
    val lines = ceil(13f / perLine).toInt()
    for (above in listOf(false, true)) {
      val labels = if (above) 0.dp else maxOf(72.dp, room(64)) + 8.dp
      val byRowsWidth = (w - labels - ROW_PAD * 2 - CARD_GAP * 4) / 5
      val byHandWidth = (w - HAND_GAP * (perLine - 1) - if (perLine >= 13) (SUIT_GAP - HAND_GAP) * 3 else 0.dp) / perLine
      val fixed = ROW_PAD * 2 * 3 + 8.dp * 2 + 6.dp * (lines - 1) + if (above) header * 3 else 0.dp
      val byHeight = (h - fixed) / (1.4f * (3 + lines) + 0.22f)
      val s = minOf(byRowsWidth, byHandWidth, byHeight, maxCard).down().coerceAtLeast(20.dp)
      // The first of the equally large ones wins: the labels beside.
      if (best == null || s > best.card + 0.5.dp) best = ArrangeFit(s, above, perLine)
    }
  }
  return best!!
}

/** The sizes for a window area `w` x `h` (what the screens share, below the system bars and beside the rail). */
fun cardSizes(w: Dp, h: Dp, u: Dp): CardSizes {
  val wide = w > h * 1.15f
  fun room(sp: Int) = u * (sp / 14f)
  // What the practice page's header takes of the height, the largest of the pages that arrange.
  // Tall: the title row and the scores, which take a second line when they do not fit side by side.
  val scoreLines = if ((room(16) * 4.6f + 8.dp) * 4f > w - 28.dp) 2 else 1
  val header = if (wide) 54.dp else 6.dp + maxOf(48.dp, room(26)) + (room(20) + 6.dp) * scoreLines
  val play = arrangeFit(w, h - header, wide, u).card
  // A battle's header: the hand, the time left and how many have set it, in a line of titleLarge (28 high) padded 4
  // above and under, or two when they do not fit side by side.
  val battleLines = if (room(16) * 15 + 60.dp > w - 28.dp) 2 else 1
  val battle = arrangeFit(w, h - (room(28) + 2.dp) * battleLines - 8.dp, wide, u).card
  val other = if (wide) {
    // The result page's four players in the height left by its header (48), the felt's padding and the gaps: each a line
    // of hand names and the cards.
    val perSeat = (h - maxOf(RESULT_HEADER, room(32)) - 6.dp - 12.dp - 4.dp * 3) / 4 - room(18) - 12.dp
    // The analysis page's list takes two thirds of the width.
    minOf(lineCard(w * 0.66f - PAGE_PAD * 2), (perSeat / 1.4f).down(), MAX_OTHER)
  } else minOf(lineCard(w - PAGE_PAD * 2), MAX_OTHER)
  // The records: a line each on a wide window, two a line with the rows one under another on a narrow one.
  val record = if (w >= 560.dp + PAGE_PAD * 2) lineCard(w - PAGE_PAD * 2) else (((w - PAGE_PAD * 2 - 12.dp) / 2 - 8.dp - LINE_GAP * 4) / 5).down()
  return CardSizes(play, other.coerceAtLeast(14.dp), minOf(record, MAX_RECORD).coerceAtLeast(14.dp), battle)
}
