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

/** The wide layout for `u`, and whether it is tight: the row labels taller than the cards would be. */
private fun wideFit(areaW: Dp, areaH: Dp, u: Dp, maxCard: Dp): Pair<ArrangeFit, Boolean> {
  fun room(sp: Int) = u * (sp / 14f)
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
  val byHeight = (h - fixed) / (1.4f * 4 + 0.22f)
  // A label taller than the card makes its row taller. Then each row is as tall as its label already: its cards may
  // be as tall too, and only the hand takes the height that is left (the web's sizing.ts alike).
  val label = room(40) + 2.dp
  if (byHeight * 1.4f < label) {
    val rowCard = minOf(byRowsWidth, label / 1.4f, maxCard).down().coerceAtLeast(18.dp)
    val handCard = minOf(byHandWidth, (h - fixed - label * 3) / (1.4f + 0.22f), maxCard).down().coerceAtLeast(18.dp)
    return ArrangeFit(rowCard, false, 13, handCard, minOf(rowCard, handCard)) to true
  }
  val s = minOf(byRowsWidth, byHandWidth, byHeight, maxCard).down().coerceAtLeast(18.dp)
  // A larger hand, as wide as the felt at most, with the rows as large as the height then leaves them.
  val avail = h - fixed
  // Not capped by maxCard (the size the pages that arrange share): the hand takes the width this page has.
  val hand = minOf(byHandWidth, MAX_PLAY, avail / (1.4f * 3 * ROWS_TO_HAND + 1.4f + 0.22f)).down()
  val rows = minOf((avail - hand * (1.4f + 0.22f)) / (1.4f * 3), byRowsWidth, maxCard).down()
  if (hand > s && rows >= 18.dp && rows >= hand * ROWS_TO_HAND - 0.5.dp && rows * 1.4f >= label) return ArrangeFit(rows, false, 13, hand, s) to false
  return ArrangeFit(s, false, 13) to false
}

/**
 * The least height of a wide arranging table (arrangeFit's wide branch) whose cards are still finger-sized, 32 dp wide
 * all alike: the felt's padding, three rows and the hand at that size. A row label at the normal size (a compact fit's)
 * is no taller than such a row. A page that holds the table among other things (the tutorial's practice) scrolls below
 * this rather than crush the cards.
 */
fun wideArrangeMinHeight(): Dp = 6.dp + 12.dp + ROW_PAD * 6 + ROW_STACK_GAP * 2 + 6.dp + 32.dp * (1.4f * 4 + 0.22f)

/** How the arranging page is laid out: the card's width (the rows' and the hand's), and how it is arranged. */
/**
 * `card`: the rows' cards; `hand`: the hand's (larger on a wide page when that fills the width, see ROWS_TO_HAND);
 * `even`: the size both would have alike, which the other pages take as the play size (CardSizes.play);
 * `compact`: a wide page at a large font whose row labels would crush the cards, so the sizes are for labels at the
 * normal size and the labels are drawn so (the web's sizing.ts has the same).
 */
class ArrangeFit(val card: Dp, val above: Boolean, val perLine: Int, val hand: Dp = card, val even: Dp = card, val compact: Boolean = false)

/**
 * On a wide page the hand may be larger than the rows' cards, up to filling the felt's width, so long as the rows' keep
 * at least this much of its size (the rows give up the height the hand takes). The web page's sizing.ts has the same.
 */
const val ROWS_TO_HAND = 0.75f

/** `u` is textRoom(14): what a 14sp text takes, which grows with the font size. */
fun arrangeFit(areaW: Dp, areaH: Dp, wide: Boolean, u: Dp, maxCard: Dp = MAX_PLAY): ArrangeFit {
  fun room(sp: Int) = u * (sp / 14f)
  if (wide) {
    val (fit, tight) = wideFit(areaW, areaH, u, maxCard)
    if (!tight || u <= 14.dp) return fit
    val f = wideFit(areaW, areaH, 14.dp, maxCard).first
    return ArrangeFit(f.card, f.above, f.perLine, f.hand, f.even, compact = true)
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
  val play = arrangeFit(w, h - header, wide, u).even
  // A battle's header: the hand, the time left and how many have set it, in a line of titleLarge (28 high) padded 4
  // above and under, or two when they do not fit side by side.
  val battleLines = if (room(16) * 15 + 60.dp > w - 28.dp) 2 else 1
  val battle = arrangeFit(w, h - (room(28) + 2.dp) * battleLines - 8.dp, wide, u).even
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
