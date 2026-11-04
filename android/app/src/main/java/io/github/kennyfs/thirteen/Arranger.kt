package io.github.kennyfs.thirteen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A hand being set into rows, for the game against the computer and for battles: the selection, the row picked
 * first, and the engine's view of the rows (keys, categories, validity) under `rules` (a full spec).
 * `onError` shows a message; `rowRoom` words "only n more fit in that row".
 */
class Arranger(
  private val scope: CoroutineScope,
  private val onError: (String) -> Unit,
  private val rowRoom: (row: Int, free: Int) -> String,
) {
  var hand by mutableStateOf<List<Int>>(emptyList()); private set
  var rows by mutableStateOf<Arrangement>(EMPTY); private set
  var selected by mutableStateOf<Set<Int>>(emptySet()); private set
  /** The row picked first: cards tapped then go to it once they fill it. */
  var armed by mutableStateOf<Int?>(null); private set
  var rowsEval by mutableStateOf<RowsEval?>(null); private set
  var sortBySuit by mutableStateOf(false)
  var rules = "family"; private set
  /** Earlier rows of this hand, for undo; the latest last. */
  private var history by mutableStateOf<List<Arrangement>>(emptyList())
  val canUndo: Boolean get() = history.isNotEmpty()

  val unplaced: List<Int> get() = Cards.sortHand(hand.filter { c -> rows.none { c in it } }, sortBySuit)

  /** A new hand to set, with nothing placed (or `placed` rows, as when resuming a hand already set). */
  fun reset(hand: List<Int>, rules: String, placed: Arrangement = EMPTY) {
    this.hand = hand
    this.rules = rules
    selected = emptySet()
    armed = null
    history = emptyList()
    updateRows(placed)
  }

  fun toggle(card: Int) {
    selected = if (card in selected) selected - card else selected + card
    selectionDone()
  }

  /** Selects or unselects cards during a swipe over the hand; selectionDone() when it ends. */
  fun select(cards: Collection<Int>, on: Boolean) {
    selected = if (on) selected + cards else selected - cards
  }

  /** With a row picked first, places the selected cards once they fill it. */
  fun selectionDone() {
    val row = armed ?: return
    if (selected.size == Cards.ROW_SIZES[row] - rows[row].size) placeInto(row)
  }

  /** A tap on a row: places the selected cards, or with none selected, picks the row for the next cards. */
  fun tapRow(row: Int) {
    if (selected.isNotEmpty()) placeInto(row)
    else armed = if (armed == row || rows[row].size == Cards.ROW_SIZES[row]) null else row
  }

  /** Puts the selected cards into a row, then fills the last open row if the cards left fit it exactly. */
  fun placeInto(row: Int) {
    val free = Cards.ROW_SIZES[row] - rows[row].size
    if (selected.isEmpty()) return
    if (selected.size > free) return onError(rowRoom(row, free))
    var next = rows.mapIndexed { r, cards -> if (r == row) cards + selected.sortedDescending() else cards }
    selected = emptySet()
    armed = null
    saveForUndo()
    val left = hand.filter { c -> next.none { c in it } }
    val open = (0 until 3).filter { next[it].size < Cards.ROW_SIZES[it] }
    if (open.size == 1 && left.size == Cards.ROW_SIZES[open[0]] - next[open[0]].size)
      next = next.mapIndexed { r, cards -> if (r == open[0]) cards + left else cards }
    updateRows(next)
  }

  /** Returns a card to the hand; a swipe over several saves one undo step, at its first card (`saveUndo`). */
  fun unplace(card: Int, saveUndo: Boolean = true) {
    if (rows.none { card in it }) return
    if (saveUndo) saveForUndo()
    updateRows(rows.map { it - card })
  }

  /** Returns a row's cards to the hand. */
  fun clearRow(row: Int) {
    if (rows[row].isEmpty()) return
    saveForUndo()
    updateRows(rows.mapIndexed { r, cards -> if (r == row) emptyList() else cards })
  }

  /** Whether "take all back" has anything to do: cards in the rows, or picked ones (it unpicks them). */
  val canClear get() = selected.isNotEmpty() || rows.any { it.isNotEmpty() }

  fun clearRows() {
    selected = emptySet()
    armed = null
    if (rows.any { it.isNotEmpty() }) saveForUndo()
    updateRows(EMPTY)
  }

  /** Puts the rows back as they were before the last change. */
  fun undo() {
    val last = history.lastOrNull() ?: return
    history = history.dropLast(1)
    selected = emptySet()
    armed = null
    updateRows(last)
  }

  private fun saveForUndo() {
    history = (history + listOf(rows)).takeLast(50)
  }

  private fun updateRows(next: Arrangement) {
    rows = next
    rowsEval = null
    if (hand.isEmpty()) return
    val rules = rules
    scope.launch {
      try {
        val eval = Engine.rows(next, rules)
        if (rows == next) rowsEval = eval
      } catch (e: Exception) {
        onError(e.message ?: e.toString())
      }
    }
  }

  /** The rows as the engine sees them now. */
  suspend fun eval(): RowsEval = rowsEval ?: Engine.rows(rows, rules)

  companion object {
    val EMPTY: Arrangement = listOf(emptyList(), emptyList(), emptyList())
  }
}
