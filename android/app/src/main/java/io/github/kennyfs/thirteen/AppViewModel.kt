package io.github.kennyfs.thirteen

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.random.Random

/** The game against three computer players (always c = 0) under the chosen rules, and the analysis of single hands. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
  val settings = Settings(app)
  val records = RecordStore(app.filesDir)

  private fun str(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)

  var error by mutableStateOf<String?>(null)

  private fun launchCatching(block: suspend () -> Unit) = viewModelScope.launch {
    try {
      block()
    } catch (e: Exception) {
      error = e.message ?: e.toString()
    }
  }

  // ---- Rules ----

  /** The rules page's choice; a game takes it when dealt. */
  var ruleSpec by mutableStateOf(settings.rules); private set
  /** Its values, once the engine has checked them. */
  var ruleValues by mutableStateOf<Map<String, Double>>(emptyMap()); private set
  var rulesError by mutableStateOf<String?>(null); private set
  /** Each preset's values, to name the rules of a game or record. */
  var presetValues by mutableStateOf<Map<String, Map<String, Double>>>(emptyMap()); private set
  /** Networks that predate the rules input, by path. */
  var legacyModels by mutableStateOf<Set<String>>(emptySet()); private set

  /** Picks a preset, dropping any changes. */
  fun choosePreset(preset: String) = applyRules(RuleSpec(preset))

  /** Sets one value; the same as the preset's drops the override. */
  fun setRule(name: String, value: Double) {
    val preset = presetValues[ruleSpec.preset]
    val overrides = if (preset?.get(name) == value) ruleSpec.overrides - name else ruleSpec.overrides + (name to value)
    applyRules(ruleSpec.copy(overrides = overrides))
  }

  private fun applyRules(spec: RuleSpec) {
    viewModelScope.launch {
      try {
        ruleValues = Engine.rules(spec.spec())
        ruleSpec = spec
        settings.rules = spec
        rulesError = null
      } catch (e: EngineException) {
        rulesError = e.message
      }
    }
  }

  /** The name of the rules with these values: a preset's, or custom. */
  fun rulesName(values: Map<String, Double>): String = Rules.presetOf(values, presetValues) ?: str(R.string.custom)

  /** Whether a legacy network would play these rules as if they were family. */
  fun legacyMismatch(values: Map<String, Double>): Boolean {
    val family = presetValues["family"] ?: return false
    return values.isNotEmpty() && !Rules.same(values, family)
  }

  private suspend fun loadRules() {
    presetValues = Rules.PRESETS.associateWith { Engine.rules(it) }
    ruleValues = try {
      Engine.rules(ruleSpec.spec())
    } catch (e: EngineException) {
      // Stored rules the engine no longer takes: back to the preset.
      ruleSpec = RuleSpec(ruleSpec.preset)
      settings.rules = ruleSpec
      Engine.rules(ruleSpec.spec())
    }
  }

  private suspend fun loadLegacy() {
    legacyModels = settings.models().filter { runCatching { Engine.isLegacy(it.path) }.getOrDefault(false) }.map { it.path }.toSet()
  }

  // ---- Play ----

  var hand by mutableStateOf<List<Int>>(emptyList()); private set
  var rows by mutableStateOf<Arrangement>(listOf(emptyList(), emptyList(), emptyList())); private set
  var selected by mutableStateOf<Set<Int>>(emptySet()); private set
  /** The row picked first: cards tapped then go to it once they fill it. */
  var armed by mutableStateOf<Int?>(null); private set
  var rowsEval by mutableStateOf<RowsEval?>(null); private set
  var sortBySuit by mutableStateOf(false)
  var seats by mutableStateOf<List<Seat>>(emptyList()); private set
  var waiting by mutableStateOf(false); private set
  /** The finished hand: everyone's arrangement (0 is the player) and the score. */
  var result by mutableStateOf<Pair<List<Arrangement>, Score>?>(null); private set
  /** The finished hand's pages: 0 the result, 1 the analysis of the player's hand. */
  var resultTab by mutableStateOf(0)
  var gameAnalysis by mutableStateOf<Analysis?>(null); private set
  var session by mutableStateOf(settings.session()); private set
  /** The current game's rules: the full spec (the engine's toString) and its values. */
  var gameRules by mutableStateOf(""); private set
  var gameValues by mutableStateOf<Map<String, Double>>(emptyMap()); private set
  var recordCount by mutableStateOf(records.count()); private set

  private var others: List<List<Int>> = emptyList()
  private var opponents: Deferred<List<Arrangement>>? = null
  private var dealtAt = 0L

  val unplaced: List<Int> get() = Cards.sortHand(hand.filter { c -> rows.none { c in it } }, sortBySuit)

  init {
    launchCatching {
      loadRules()
      deal().join()
      loadLegacy()
    }
  }

  /** Deals a new hand; tests pass `fixed` hands (the player's first). */
  fun deal(fixed: List<List<Int>>? = null) = launchCatching {
    result = null
    resultTab = 0
    gameAnalysis = null
    armed = null
    val hands = fixed ?: Engine.deal()
    hand = hands[0]
    others = hands.drop(1)
    rows = listOf(emptyList(), emptyList(), emptyList())
    selected = emptySet()
    rowsEval = null
    if (ruleValues.isEmpty()) loadRules()
    gameRules = Engine.rulesFull(ruleSpec.spec())
    gameValues = Rules.values(gameRules)
    // Totals under other rules are on another scale: they start over.
    if (settings.sessionRules != gameRules) {
      if (settings.sessionRules.isNotEmpty()) settings.resetSession()
      settings.sessionRules = gameRules
      session = settings.session()
    }
    seats = List(3) { settings.seat(it) }
    val specs = seats.map(settings::spec)
    val rules = gameRules
    opponents = viewModelScope.async { List(3) { Engine.arrange(specs[it], others[it], rules) } }
    dealtAt = System.currentTimeMillis()
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
    if (selected.size > free) {
      error = str(R.string.row_room, getApplication<Application>().resources.getStringArray(R.array.row_names)[row], free)
      return
    }
    var next = rows.mapIndexed { r, cards -> if (r == row) cards + selected.sortedDescending() else cards }
    selected = emptySet()
    armed = null
    val left = hand.filter { c -> next.none { c in it } }
    val open = (0 until 3).filter { next[it].size < Cards.ROW_SIZES[it] }
    if (open.size == 1 && left.size == Cards.ROW_SIZES[open[0]] - next[open[0]].size)
      next = next.mapIndexed { r, cards -> if (r == open[0]) cards + left else cards }
    updateRows(next)
  }

  fun unplace(card: Int) = updateRows(rows.map { it - card })

  /** Returns a row's cards to the hand. */
  fun clearRow(row: Int) = updateRows(rows.mapIndexed { r, cards -> if (r == row) emptyList() else cards })

  fun clearRows() {
    selected = emptySet()
    armed = null
    updateRows(listOf(emptyList(), emptyList(), emptyList()))
  }

  private fun updateRows(next: Arrangement) {
    rows = next
    rowsEval = null
    launchCatching {
      val eval = Engine.rows(next, gameRules)
      if (rows == next) rowsEval = eval
    }
  }

  fun confirm() = launchCatching {
    val eval = rowsEval ?: Engine.rows(rows, gameRules)
    if (eval.valid != true) {
      error = str(if (eval.valid == null) R.string.fill_rows_first else R.string.fouled)
      return@launchCatching
    }
    waiting = true
    try {
      val all = listOf(rows) + opponents!!.await()
      val score = Engine.score(all, gameRules)
      records.add(hand, rows, 0.0, gameRules, System.currentTimeMillis() - dealtAt, score.result[0])
      recordCount = records.count()
      settings.addToSession(score.result)
      session = settings.session()
      result = all to score
    } finally {
      waiting = false
    }
  }

  /** Shows the analysis of the player's hand, computing it the first time. */
  fun showGameAnalysis() = launchCatching {
    resultTab = 1
    val r = result ?: return@launchCatching
    if (gameAnalysis != null) return@launchCatching
    analyzing = true
    try {
      val a = Engine.analyze(hand.sorted(), gameRules, settings.model(anaModel).path, listOf(r.first[0]))
      if (result === r) gameAnalysis = a
    } finally {
      analyzing = false
    }
  }

  fun resetSession() {
    settings.resetSession()
    session = settings.session()
  }

  fun clearRecords() {
    records.clear()
    recordCount = 0
  }

  fun exportRecords(resolver: ContentResolver, uri: Uri) = launchCatching { records.exportTo(resolver, uri) }

  // ---- Analyze ----

  var anaHand by mutableStateOf<Set<Int>>(emptySet())
  /** The analysis page's sub-pages: 0 the hand, 1 the candidates. */
  var anaPage by mutableStateOf(0)
  var anaModel by mutableStateOf(settings.analyzeModel)
  /** The player's arrangement of the hand being analyzed, when it comes from a game. */
  var anaMine by mutableStateOf<Arrangement?>(null); private set
  /** The rules of the hand being analyzed when it comes from a game or record (full spec); else the rules page's. */
  var anaRules by mutableStateOf<String?>(null); private set
  val anaRuleValues: Map<String, Double> get() = anaRules?.let(Rules::values) ?: ruleValues
  var analysis by mutableStateOf<Analysis?>(null); private set
  var analyzing by mutableStateOf(false); private set

  fun setAnalyzeHand(cards: Set<Int>, mine: Arrangement? = null, rules: String? = null) {
    anaHand = cards
    anaMine = mine
    anaRules = rules
    analysis = null
    anaPage = 0
  }

  fun toggleAnalyzeCard(card: Int) {
    if (card in anaHand) setAnalyzeHand(anaHand - card, rules = anaRules)
    else if (anaHand.size < 13) setAnalyzeHand(anaHand + card, rules = anaRules)
  }

  /** Analyzes under the rules page's rules instead of the game's. */
  fun useCurrentRules() {
    anaRules = null
    analysis = null
  }

  fun randomAnalyzeHand() = setAnalyzeHand((0 until 52).shuffled(Random).take(13).toSet())

  /** The last hand played, with the player's arrangement among the candidates. */
  fun analyzeLastHand() {
    val r = result ?: return
    setAnalyzeHand(r.first[0].flatten().toSet(), r.first[0], gameRules)
  }

  /** A recorded hand, with the player's arrangement among the candidates. */
  fun analyzeRecord(rec: JSONObject) {
    setAnalyzeHand(
      Cards.parse(rec.getString("hand")).toSet(), listOf("front", "middle", "back").map { Cards.parse(rec.getString(it)) },
      // Records from before the rules field were played under family.
      rec.optString("rules").ifEmpty { "family" },
    )
  }

  fun chooseAnalyzeModel(path: String) {
    anaModel = path
    settings.analyzeModel = path
  }

  fun analyze() = launchCatching {
    if (anaHand.size != 13) {
      error = str(R.string.pick_13)
      return@launchCatching
    }
    analyzing = true
    try {
      analysis = Engine.analyze(anaHand.sorted(), anaRules ?: ruleSpec.spec(), settings.model(anaModel).path, listOfNotNull(anaMine))
      anaPage = 1
    } finally {
      analyzing = false
    }
  }

  // ---- Models ----

  var models by mutableStateOf(settings.models()); private set

  fun importModel(resolver: ContentResolver, uri: Uri) = launchCatching {
    val file = settings.importModel(resolver, uri)
    try {
      // Loads it: the analysis of any hand fails on a file that is not a compatible network.
      Engine.analyze((0 until 13).map { it * 4 }, "family", file.path, emptyList())
    } catch (e: Exception) {
      file.delete()
      throw EngineException(str(R.string.not_usable, e.message ?: ""))
    }
    models = settings.models()
    loadLegacy()
  }

  fun deleteModel(model: Model) {
    settings.deleteModel(model)
    models = settings.models()
  }

  fun setSeat(i: Int, seat: Seat) {
    settings.setSeat(i, seat)
  }
}
