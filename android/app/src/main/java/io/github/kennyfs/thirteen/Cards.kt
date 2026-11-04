package io.github.kennyfs.thirteen

/** Card ids as in cpp/game/cards.h: rank = id / 4 (0 = 2, ..., 12 = A), suit = id % 4 (clubs, diamonds, hearts, spades). */
object Cards {
  const val RANKS = "23456789TJQKA"
  const val SUITS = "cdhs"
  val SUIT_SYMBOLS = listOf("♣", "♦", "♥", "♠")
  val ROW_SIZES = listOf(3, 5, 5)
  /** English row labels by category; the UI passes R.array.row_labels. */
  val ROW_LABELS = listOf("", "High", "Pair", "Two pair", "Trips", "Straight to", "Flush", "Full house", "Quads", "Str. flush to")

  fun rank(c: Int) = c shr 2
  fun suit(c: Int) = c and 3
  fun isRed(c: Int) = suit(c) == 1 || suit(c) == 2
  fun rankText(c: Int) = rankText(RANKS[rank(c)])
  private fun rankText(r: Char) = if (r == 'T') "10" else r.toString()

  /** "Td", as in Cards::toString in C++. */
  fun toString(c: Int) = "${RANKS[rank(c)]}${SUITS[suit(c)]}"
  /** Highest first, separated by spaces: the record format of gui/collect.html. */
  fun toString(cards: Collection<Int>) = cards.sortedDescending().joinToString(" ") { toString(it) }

  /** Cards in the record format ("As Td 2c"). */
  fun parse(s: String): List<Int> =
    s.split(" ").filter { it.isNotBlank() }.map { RANKS.indexOf(it[0].uppercaseChar()) * 4 + SUITS.indexOf(it[1].lowercaseChar()) }

  fun categoryOf(key: Int) = key shr 20

  /**
   * A row's hand in short from its key: the category and the ranks that define it, e.g. "Pair A", "Two pair K 7".
   * `wheel` is the rules' ranking of A-2-3-4-5 (HandEval::withWheel): under 1, a straight to A has 13 and A-2-3-4-5 12.
   */
  fun rowLabel(key: Int, labels: List<String> = ROW_LABELS, wheel: Int = 0): String {
    val cat = categoryOf(key)
    val wheelHigh = wheel == 1 && (cat == 5 || cat == 9)
    fun r(i: Int): String {
      var n = (key shr (16 - 4 * i)) and 15
      if (wheelHigh && i == 0) n = if (n == 13) 12 else if (n == 12) 3 else n
      return rankText(RANKS[n])
    }
    return "${labels[cat]} ${r(0)}" + if (cat == 3) " ${r(1)}" else ""
  }

  /** A card in a laid-out row: `tint` is the row's category for a card that makes the hand (0 for a kicker). */
  data class Placed(val card: Int, val tint: Int, val light: Boolean)

  /**
   * A row in reading order, like rowLayout in gui/play.html: the cards that make the hand first, larger groups first
   * and then higher ranks (2 2 3 3 5 reads 3 3 2 2 5), then the kickers; a straight, flush or straight flush is one
   * group from its top card (A-2-3-4-5 reads 5 4 3 2 A). The second group of a two pair or full house is light. A row
   * whose category is not known (0) is just sorted.
   */
  fun rowLayout(cards: List<Int>, category: Int): List<Placed> {
    val sorted = cards.sortedDescending()
    if (category == 0) return sorted.map { Placed(it, 0, false) }
    if (category == 5 || category == 6 || category == 9) {
      val ranks = sorted.map(::rank).toSet()
      val wheel = category != 6 && 12 in ranks && 0 in ranks
      val order = if (wheel) sorted.filter { rank(it) != 12 } + sorted.filter { rank(it) == 12 } else sorted
      return order.map { Placed(it, category, false) }
    }
    val count = sorted.groupingBy(::rank).eachCount()
    val ranks = count.keys.sortedWith(compareByDescending<Int> { count[it] }.thenByDescending { it })
    var group = 0
    return ranks.flatMap { r ->
      val pair = count.getValue(r) >= 2
      val light = pair && group++ > 0
      sorted.filter { rank(it) == r }.map { Placed(it, if (pair && category > 1) category else 0, light) }
    }
  }

  /** The same cards as sets, whatever their order in each row. */
  fun sameArrangement(a: List<List<Int>>, b: List<List<Int>>) = a.indices.all { a[it].toSet() == b[it].toSet() }

  /** The category (1..9) of a 3- or 5-card row, for tinting when only the cards are known. */
  fun guessCategory(cards: List<Int>): Int {
    if (cards.isEmpty()) return 0
    val counts = cards.groupingBy(::rank).eachCount().values.sortedDescending()
    if (cards.size == 3) return when (counts.first()) { 3 -> 4; 2 -> 2; else -> 1 }
    if (cards.size != 5) return 0
    val flush = cards.map(::suit).distinct().size == 1
    val ranks = cards.map(::rank).distinct().sorted()
    val straight = ranks.size == 5 && (ranks[4] - ranks[0] == 4 || ranks == listOf(0, 1, 2, 3, 12))
    return when {
      flush && straight -> 9
      counts[0] == 4 -> 8
      counts[0] == 3 && counts[1] == 2 -> 7
      flush -> 6
      straight -> 5
      counts[0] == 3 -> 4
      counts[0] == 2 && counts[1] == 2 -> 3
      counts[0] == 2 -> 2
      else -> 1
    }
  }

  /** Sorted for the hand area: by rank, or by suit and then rank. */
  fun sortHand(cards: Collection<Int>, bySuit: Boolean): List<Int> =
    if (bySuit) cards.sortedWith(compareBy<Int> { suit(it) }.thenBy { rank(it) }) else cards.sorted()
}
