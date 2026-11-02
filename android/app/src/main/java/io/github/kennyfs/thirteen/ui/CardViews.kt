package io.github.kennyfs.thirteen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kennyfs.thirteen.Cards
import io.github.kennyfs.thirteen.R

private val RED = Color(0xFFC62828)
private val BLACK = Color(0xFF1B1B1B)
// One hue per category, as in gui/play.html; index = category.
private val HUES = floatArrayOf(0f, 0f, 48f, 26f, 130f, 185f, 218f, 268f, 335f, 0f)

/** The background of a card that makes its row's hand (category > 0), or white for a kicker. */
fun tintColor(category: Int, light: Boolean): Color =
  if (category <= 1) Color.White else Color.hsl(HUES[category], 0.85f, if (light) 0.90f else 0.74f)

/** The card width of lists that scroll; boards size their cards from the room they have. */
val SMALL_CARD = 26.dp
/** The space between the cards of a row. */
val CARD_GAP = 2.dp
const val CARD_TAG = "card"

fun cardHeight(width: Dp): Dp = width * 1.4f

/** The width of `n` cards side by side. */
fun rowWidth(n: Int, width: Dp): Dp = width * n + CARD_GAP * (n - 1).coerceAtLeast(0)

/**
 * Room for text: `sp` as dp, so it grows with the font size setting as the text does. Scaled as body text is: from
 * Android 14 the font size setting enlarges large sizes less, so `sp.toDp()` of a width would fall short of the text.
 */
@Composable
fun textRoom(sp: Int): Dp = with(LocalDensity.current) { 14.sp.toDp() } * (sp / 14f)

/** The row labels of the current language, for Cards.rowLabel. */
@Composable
fun rowLabels(): List<String> = stringArrayResource(R.array.row_labels).toList()

/** A question mark in a circle that shows `text` in a dialog. */
@Composable
fun InfoButton(text: String) {
  var open by remember { mutableStateOf(false) }
  val color = MaterialTheme.colorScheme.primary
  val description = stringResource(R.string.info)
  Box(
    Modifier.size(40.dp).clip(CircleShape).clickable { open = true }.semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
  ) {
    Box(Modifier.size(24.dp).border(2.dp, color, CircleShape), contentAlignment = Alignment.Center) {
      Text("?", color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp)
    }
  }
  if (open)
    AlertDialog(
      onDismissRequest = { open = false },
      confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.ok)) } },
      text = { Text(text) },
    )
}

/**
 * A card `width` wide, card-shaped unless given a `height`. Its rank and suit are part of the picture: they scale with the card, whatever the font size
 * setting. A selected card has a thick border and, with `lift`, sits a little higher.
 */
@Composable
fun CardFace(
  card: Int,
  width: Dp,
  modifier: Modifier = Modifier,
  height: Dp = cardHeight(width),
  tint: Int = 0,
  light: Boolean = false,
  fill: Color = tintColor(tint, light),
  selected: Boolean = false,
  lift: Boolean = selected,
  onClick: (() -> Unit)? = null,
) {
  val shape = RoundedCornerShape(width * 0.14f)
  val base = minOf(width, height / 1.4f)
  val (rankSize, suitSize) = with(LocalDensity.current) { (base * 0.44f).toSp() to (base * 0.40f).toSp() }
  var m = modifier
    .testTag(CARD_TAG)
    .offset(y = if (lift) -(width * 0.2f) else 0.dp)
    .size(width, height)
    .background(fill, shape)
    .border(if (selected) 2.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0xFF9E9E9E), shape)
  if (onClick != null) m = m.clickable(onClick = onClick)
  val color = if (Cards.isRed(card)) RED else BLACK
  Box(m, contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
      Text(Cards.rankText(card), color = color, fontSize = rankSize, fontWeight = FontWeight.Bold, lineHeight = rankSize, maxLines = 1, softWrap = false)
      Text(Cards.SUIT_SYMBOLS[Cards.suit(card)], color = color, fontSize = suitSize, lineHeight = suitSize, maxLines = 1)
    }
  }
}

/** A full or partial row laid out by Cards.rowLayout and tinted by its category (0 = unknown). */
@Composable
fun RowCards(
  cards: List<Int>,
  category: Int,
  width: Dp = SMALL_CARD,
  onCardClick: ((Int) -> Unit)? = null,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(CARD_GAP)) {
    for (p in Cards.rowLayout(cards, category))
      CardFace(p.card, width, tint = p.tint, light = p.light, onClick = onCardClick?.let { f -> { f(p.card) } })
  }
}
