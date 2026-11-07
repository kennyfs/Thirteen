package io.github.kennyfs.thirteen.ui

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kennyfs.thirteen.Cards
import io.github.kennyfs.thirteen.R
import io.github.kennyfs.thirteen.ui.theme.LocalDark
import io.github.kennyfs.thirteen.ui.theme.LocalOutcome
import io.github.kennyfs.thirteen.ui.theme.LocalTable
import kotlin.math.abs

private val RED = Color(0xFFC62828)
private val BLACK = Color(0xFF1B1B1B)
private val EDGE = Color(0xFF9E9E9E)
// The same cards in a dark theme: a dark face, light ink.
private val DARK_FACE = Color(0xFF2B2F2D)
private val DARK_RED = Color(0xFFFF8A80)
private val DARK_BLACK = Color(0xFFF0F0F0)
private val DARK_EDGE = Color(0xFF626865)
// One hue per category, as in gui/play.html; index = category.
private val HUES = floatArrayOf(0f, 0f, 48f, 26f, 130f, 185f, 218f, 268f, 335f, 0f)

/** The background of a card that makes its row's hand (category > 0), or white (dark in a dark theme) for a kicker. */
fun tintColor(category: Int, light: Boolean, dark: Boolean = false): Color = when {
  category <= 1 -> if (dark) DARK_FACE else Color.White
  dark -> Color.hsl(HUES[category], 0.45f, if (light) 0.28f else 0.38f)
  else -> Color.hsl(HUES[category], 0.85f, if (light) 0.90f else 0.74f)
}

/** A plain card's face: white, or dark in a dark theme. */
val CARD_FACE: Color @Composable get() = if (LocalDark.current) DARK_FACE else Color.White

/** Text colors for good and bad outcomes on the theme's surfaces. */
val GOOD: Color @Composable get() = LocalOutcome.current.good
val BAD: Color @Composable get() = LocalOutcome.current.bad

/** "+3", "−1.5": no decimals for whole numbers. */
fun signed(x: Double): String {
  val a = abs(x)
  return (if (x >= 0) "+" else "−") + if (a == kotlin.math.floor(a)) "%.0f".format(a) else "%.2f".format(a)
}

/** The card width of lists that scroll; boards size their cards from the room they have. */
val SMALL_CARD = 26.dp
/** The space between the cards of a row. */
val CARD_GAP = 4.dp
const val CARD_TAG = "card"

fun cardHeight(width: Dp): Dp = width * 1.4f

/** The width of `n` cards side by side. */
fun rowWidth(n: Int, width: Dp, gap: Dp = CARD_GAP): Dp = width * n + gap * (n - 1).coerceAtLeast(0)

/**
 * Room for text: `sp` as dp, so it grows with the font size setting as the text does. Scaled as body text is: from
 * Android 14 the font size setting enlarges large sizes less, so `sp.toDp()` of a width would fall short of the text.
 */
@Composable
fun textRoom(sp: Int): Dp = with(LocalDensity.current) { 14.sp.toDp() } * (sp / 14f)

/**
 * A name in a tight place (a tile, a row's label) on one line: it shrinks, to [min] of the style's size at most, before
 * it ends in "…". Some languages run far longer than Chinese, for which the places were measured.
 */
fun shrinking(style: TextStyle, min: Float = SHRINK_MIN) = TextAutoSize.StepBased(style.fontSize * min, style.fontSize, 0.25.sp)

const val SHRINK_MIN = 0.8f

/** The row labels of the current language, for Cards.rowLabel. */
@Composable
fun rowLabels(): List<String> = stringArrayResource(R.array.row_labels).toList()

/** "Spade K", for screen readers. */
@Composable
fun cardName(card: Int): String = stringArrayResource(R.array.suit_names)[Cards.suit(card)] + " " + Cards.rankText(card)

/**
 * A card `width` wide, card-shaped unless given a `height`. Its rank and suit are part of the picture: they scale with
 * the card, whatever the font size setting. A selected card has the table's highlight around it and, with `lift`,
 * sits a little higher.
 */
@Composable
fun CardFace(
  card: Int,
  width: Dp,
  modifier: Modifier = Modifier,
  height: Dp = cardHeight(width),
  tint: Int = 0,
  light: Boolean = false,
  fill: Color = tintColor(tint, light, LocalDark.current),
  selected: Boolean = false,
  lift: Boolean = selected,
  onClick: (() -> Unit)? = null,
) {
  val shape = RoundedCornerShape(width * 0.14f)
  val base = minOf(width, height / 1.4f)
  val rankSize = with(LocalDensity.current) { (base * 0.46f).toSp() }
  val highlight = LocalTable.current.highlight
  val dark = LocalDark.current
  val name = cardName(card)
  var m = modifier
    .testTag(CARD_TAG)
    .offset(y = if (lift) -(width * 0.2f) else 0.dp)
    .size(width, height)
    .let { if (selected) it.shadow(4.dp, shape) else it }
    .background(fill, shape)
    .let { if (selected) it.border(3.dp, highlight, shape) else it.border(1.dp, if (dark) DARK_EDGE else EDGE, shape) }
  m = if (onClick != null) m.clickable(role = Role.Checkbox, onClickLabel = null, onClick = onClick) else m
  m = m.semantics(mergeDescendants = true) {
    contentDescription = name
    if (onClick != null) this.selected = selected
  }
  val color = if (Cards.isRed(card)) (if (dark) DARK_RED else RED) else (if (dark) DARK_BLACK else BLACK)
  Box(m, contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
      Text(
        Cards.rankText(card), color = color, fontSize = rankSize, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
        lineHeight = rankSize, maxLines = 1, softWrap = false, letterSpacing = (-0.04).sp,
      )
      Spacer(Modifier.height(base * SUIT_SPACE))
      SuitMark(Cards.suit(card), color, Modifier.size(base * SUIT_SIZE))
    }
  }
}

/** The suit's size and its space below the rank, in card widths. */
private const val SUIT_SIZE = 0.40f
private const val SUIT_SPACE = 0.03f

/**
 * A suit drawn, not set as text: the font's symbols come as emoji on some phones, which ignore the color (black spades
 * on a dark card), and the text forms differ in size and place from font to font. Index = Cards.suit.
 */
@Composable
fun SuitMark(suit: Int, color: Color, modifier: Modifier = Modifier) {
  Canvas(modifier) {
    val w = size.width
    val h = size.height
    fun p(x: Float, y: Float) = Offset(x * w, y * h)
    val path = Path()
    fun Path.cubic(a: Offset, b: Offset, c: Offset) = cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)
    when (suit) {
      0 -> { // club: three leaves and a stem
        val r = 0.235f * w
        for (c in listOf(p(0.5f, 0.27f), p(0.255f, 0.6f), p(0.745f, 0.6f))) drawCircle(color, r, c)
        drawCircle(color, 0.16f * w, p(0.5f, 0.55f))
        path.moveTo(0.5f * w, 0.55f * h)
        path.lineTo(0.64f * w, h)
        path.lineTo(0.36f * w, h)
        path.close()
      }
      1 -> { // diamond
        path.moveTo(0.5f * w, 0f)
        path.lineTo(0.88f * w, 0.5f * h)
        path.lineTo(0.5f * w, h)
        path.lineTo(0.12f * w, 0.5f * h)
        path.close()
      }
      2 -> { // heart
        path.moveTo(0.5f * w, 0.95f * h)
        path.cubic(p(0.12f, 0.66f), p(0f, 0.5f), p(0f, 0.3f))
        path.cubic(p(0f, 0.12f), p(0.13f, 0.03f), p(0.27f, 0.03f))
        path.cubic(p(0.39f, 0.03f), p(0.47f, 0.1f), p(0.5f, 0.2f))
        path.cubic(p(0.53f, 0.1f), p(0.61f, 0.03f), p(0.73f, 0.03f))
        path.cubic(p(0.87f, 0.03f), p(1f, 0.12f), p(1f, 0.3f))
        path.cubic(p(1f, 0.5f), p(0.88f, 0.66f), p(0.5f, 0.95f))
        path.close()
      }
      else -> { // spade: a heart upside down and a stem
        path.moveTo(0.5f * w, 0f)
        path.cubic(p(0.88f, 0.28f), p(1f, 0.42f), p(1f, 0.6f))
        path.cubic(p(1f, 0.76f), p(0.88f, 0.86f), p(0.74f, 0.86f))
        path.cubic(p(0.63f, 0.86f), p(0.56f, 0.8f), p(0.53f, 0.72f))
        path.lineTo(0.64f * w, h)
        path.lineTo(0.36f * w, h)
        path.lineTo(0.47f * w, 0.72f * h)
        path.cubic(p(0.44f, 0.8f), p(0.37f, 0.86f), p(0.26f, 0.86f))
        path.cubic(p(0.12f, 0.86f), p(0f, 0.76f), p(0f, 0.6f))
        path.cubic(p(0f, 0.42f), p(0.12f, 0.28f), p(0.5f, 0f))
        path.close()
      }
    }
    drawPath(path, color)
  }
}

/** An empty place for a card in a row: a dashed outline in the table's text color. */
@Composable
fun CardSlot(width: Dp, modifier: Modifier = Modifier) {
  val color = LocalTable.current.onFelt.copy(alpha = 0.45f)
  Box(
    modifier.size(width, cardHeight(width)).drawBehind {
      val r = (width * 0.14f).toPx()
      drawRoundRect(
        color, cornerRadius = CornerRadius(r, r),
        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
      )
    },
  )
}

/** A full or partial row laid out by Cards.rowLayout and tinted by its category (0 = unknown). */
@Composable
fun RowCards(
  cards: List<Int>,
  category: Int,
  width: Dp = SMALL_CARD,
  gap: Dp = CARD_GAP,
  onCardClick: ((Int) -> Unit)? = null,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
    for (p in Cards.rowLayout(cards, category))
      CardFace(p.card, width, tint = p.tint, light = p.light, onClick = onCardClick?.let { f -> { f(p.card) } })
  }
}

/** A row's cards and then empty places up to `size`. */
@Composable
fun RowWithSlots(
  cards: List<Int>, category: Int, size: Int, width: Dp, gap: Dp = CARD_GAP, onCardClick: ((Int) -> Unit)? = null,
  cardModifier: (Int) -> Modifier = { Modifier },
) {
  Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
    for (p in Cards.rowLayout(cards, category))
      CardFace(p.card, width, cardModifier(p.card), tint = p.tint, light = p.light, onClick = onCardClick?.let { f -> { f(p.card) } })
    repeat(size - cards.size) { CardSlot(width) }
  }
}
