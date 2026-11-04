package io.github.kennyfs.thirteen.battle

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.kennyfs.thirteen.Analysis
import io.github.kennyfs.thirteen.Engine
import io.github.kennyfs.thirteen.Score
import io.github.kennyfs.thirteen.protocol.HandOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A battle's closed hands as the result pages show them, for a battle being played and one from the records alike:
 * the hands, what the pages need worked out from them (each play's row categories, a table's score from the player's
 * seat), and the hand and player opened for a closer look with their analysis. `model` is the network for the
 * analyses, which run on this phone; `onError` shows a failed one.
 */
class BattleReview(private val scope: CoroutineScope, private val model: () -> String, private val onError: (String) -> Unit) {
  /** The player's id in the battle; -1 for none. */
  var me by mutableStateOf(-1)
  /** The rules as the engine spells them out. */
  var rules = ""
  /** Player ids to names, for the analysis's marks of who chose what. */
  var names: Map<Int, String> = emptyMap()

  var outcomes by mutableStateOf<List<HandOutcome>>(emptyList()); private set
  /** Each row's category of the plays shown (by their rows, sorted within each row), for laying them out. */
  var categories by mutableStateOf<Map<List<List<Int>>, List<Int>>>(emptyMap()); private set
  /**
   * A table game's hands as the practice table shows them, by hand index: the plays' indices with the player's own
   * first, then the others in seat order, and the full score in that order (the room's, with each row's hand).
   */
  var tables by mutableStateOf<Map<Int, Pair<List<Int>, Score>>>(emptyMap()); private set

  /** The hand opened for a closer look, and the player whose hand of it is analyzed. */
  var detail by mutableStateOf<Int?>(null); private set
  var analyzed by mutableStateOf<Int?>(null); private set
  var detailAnalysis by mutableStateOf<Analysis?>(null); private set
  /** Candidates by index to the names of the other players who chose them (a puzzle). */
  var detailTags by mutableStateOf<Map<Int, String>>(emptyMap()); private set
  /**
   * Opened from a hand's result while the room waits for the next: back goes straight back to the result, and the
   * next hand's deal closes it.
   */
  var fromReview by mutableStateOf(false); private set

  /** Adds or replaces hands (by index); returns the ones new to it. */
  fun add(list: List<HandOutcome>): List<HandOutcome> {
    val known = outcomes.associateBy { it.index }.toMutableMap()
    val fresh = list.filter { it.index !in known }
    for (o in list) known[o.index] = o
    outcomes = known.values.sortedBy { it.index }
    val rules = rules
    if (rules.isEmpty()) return fresh
    scope.launch {
      for (o in list) {
        if (o.score == null || o.index in tables) continue
        val mine = o.plays.indexOfFirst { it.player == me }
        val order = if (mine < 0) o.plays.indices.toList() else listOf(mine) + o.plays.indices.filter { it != mine }
        runCatching { Engine.score(order.map { o.plays[it].rows }, rules) }.onSuccess { tables = tables + (o.index to (order to it)) }
      }
      for (o in list) for (rows in o.plays.map { it.rows } + listOfNotNull(o.best)) {
        val key = rows.map { it.sorted() }
        if (key in categories) continue
        runCatching { Engine.rows(rows, rules).categories }.onSuccess { categories = categories + (key to it) }
      }
    }
    return fresh
  }

  fun categoriesOf(rows: List<List<Int>>): List<Int>? = categories[rows.map { it.sorted() }]

  fun clear() {
    me = -1
    rules = ""
    names = emptyMap()
    outcomes = emptyList()
    categories = emptyMap()
    tables = emptyMap()
    closeDetail()
  }

  /** A closed hand opened from the results: its table first. */
  fun openDetail(index: Int) {
    if (outcomes.none { it.index == index }) return
    detail = index
    fromReview = false
    closeAnalysis()
  }

  /** Analyzes a player's hand of `index` from its result, while the room waits for the next hand. */
  fun analyzeFromResult(index: Int, player: Int) {
    openDetail(index)
    if (detail != index) return
    fromReview = true
    openAnalysis(player)
  }

  /**
   * Analyzes one player's hand of the opened hand with the built-in network, their play pinned and, in a puzzle,
   * the others' plays of the same cards marked.
   */
  fun openAnalysis(player: Int) {
    val index = detail ?: return
    val o = outcomes.firstOrNull { it.index == index } ?: return
    val play = o.plays.firstOrNull { it.player == player } ?: return
    val rules = rules
    val names = names
    analyzed = player
    detailAnalysis = null
    detailTags = emptyMap()
    scope.launch {
      try {
        val hand = play.rows.flatten().sorted()
        // In a puzzle everyone set these cards; at a table the others had other hands.
        val same = o.plays.filter { it.rows.flatten().toSet() == hand.toSet() }
        val a = Engine.analyze(hand, rules, model(), same.map { it.rows })
        val tags = HashMap<Int, MutableList<String>>()
        for (p in same) {
          if (p.player == player) continue
          val keys = Engine.rows(p.rows, rules).keys
          val i = a.cands.indexOfFirst { it.info.keys == keys }
          if (i >= 0) tags.getOrPut(i) { mutableListOf() } += names[p.player] ?: "?"
        }
        if (detail == index && analyzed == player) {
          detailAnalysis = a
          detailTags = tags.mapValues { it.value.joinToString(", ") }
        }
      } catch (e: Exception) {
        onError(e.message ?: e.toString())
      }
    }
  }

  fun closeAnalysis() {
    analyzed = null
    detailAnalysis = null
    detailTags = emptyMap()
  }

  fun closeDetail() {
    detail = null
    fromReview = false
    closeAnalysis()
  }
}
