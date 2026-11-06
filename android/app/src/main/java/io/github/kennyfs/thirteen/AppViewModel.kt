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
import io.github.kennyfs.thirteen.protocol.BattleRecord
import org.json.JSONObject
import kotlin.random.Random

/** The game against three computer players (always c = 0) under the chosen rules, and the analysis of single hands. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
  val settings = Settings(app)
  val records = RecordStore(app.filesDir)
  val favorites = FavoriteStore(app.filesDir)
  /** Battles (the hands of each are in the records too), and the saved ones: copies, kept when the records are cleared. */
  val battles = BattleStore(app.filesDir)
  val favoriteBattles = BattleStore(app.filesDir, "favorite_battles")

  private fun str(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)

  var error by mutableStateOf<String?>(null)

  // ---- Look ----

  var colorStyle by mutableStateOf(settings.colorStyle); private set
  var darkMode by mutableStateOf(settings.darkMode); private set
  var textSize by mutableStateOf(settings.textSize); private set

  fun chooseColorStyle(v: ColorStyle) {
    colorStyle = v
    settings.colorStyle = v
  }

  fun chooseDarkMode(v: DarkMode) {
    darkMode = v
    settings.darkMode = v
  }

  fun chooseTextSize(v: TextSize) {
    textSize = v
    settings.textSize = v
  }

  private fun launchCatching(block: suspend () -> Unit) = viewModelScope.launch {
    try {
      block()
    } catch (e: Exception) {
      error = e.message ?: e.toString()
    }
  }

  // ---- Rules ----

  /** The rules page's choice; the practice game takes it when the page is left (rulesClosed). */
  var ruleSpec by mutableStateOf(settings.rules); private set
  /** Its values, once the engine has checked them. */
  var ruleValues by mutableStateOf<Map<String, Double>>(emptyMap()); private set
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
      } catch (e: EngineException) {
        // The steppers keep to the ranges the engine takes, so this is a bug: say so in a dialog, not in the page.
        error = e.message
      }
    }
  }

  /**
   * The rules page was left: under changed rules the practice game deals a new hand at once (the hand in play is
   * dropped, so no record is set under rules it was not dealt for); deal starts the totals over when the rules differ
   * from theirs, so changing and changing back keeps them.
   */
  fun rulesClosed() = launchCatching {
    if (gameRules.isNotEmpty() && Engine.rulesFull(ruleSpec.spec()) != gameRules) deal().join()
  }

  /** The name of the rules with these values: a preset's, or custom. */
  fun rulesName(values: Map<String, Double>): String =
    Rules.presetOf(values, presetValues)?.let { getApplication<Application>().resources.getStringArray(R.array.preset_names)[Rules.PRESETS.indexOf(it)] }
      ?: str(R.string.custom)

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

  /** The hand being set. */
  val play = Arranger(viewModelScope, { error = it }) { row, free ->
    str(R.string.row_room, getApplication<Application>().resources.getStringArray(R.array.row_names)[row], free)
  }
  val hand get() = play.hand
  val rows get() = play.rows
  val selected get() = play.selected
  val rowsEval get() = play.rowsEval
  val unplaced get() = play.unplaced
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
  /** Ids of the saved hands, and the finished hand's record (what the star on the result saves). */
  var favoriteIds by mutableStateOf(favorites.ids()); private set
  var favoriteBattleIds by mutableStateOf(favoriteBattles.ids()); private set
  /** The battle opened from the records. */
  var openedBattle by mutableStateOf<BattleRecord?>(null)
  var lastRecord by mutableStateOf<JSONObject?>(null); private set
  /** A screen asks for another: the number of requests so far and the screen's name ("PLAY" to rearrange a hand...). */
  var tabRequest by mutableStateOf(0 to ""); private set
  /** The hand is one played again: neither in the totals nor in the records. */
  var replaying by mutableStateOf(false); private set

  private var others: List<List<Int>> = emptyList()
  private var opponents: Deferred<List<Arrangement>>? = null
  private var dealtAt = 0L

  init {
    launchCatching {
      loadRules()
      deal().join()
      loadLegacy()
    }
  }

  /** Deals a new hand; tests pass `fixed` hands (the player's first). */
  fun deal(fixed: List<List<Int>>? = null, again: Boolean = false) = launchCatching {
    replaying = again
    lastRecord = null
    result = null
    resultTab = 0
    gameAnalysis = null
    val hands = fixed ?: Engine.deal()
    others = hands.drop(1)
    if (ruleValues.isEmpty()) loadRules()
    gameRules = Engine.rulesFull(ruleSpec.spec())
    gameValues = Rules.values(gameRules)
    play.reset(hands[0], gameRules)
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

  fun toggle(card: Int) = play.toggle(card)

  fun placeInto(row: Int) = play.placeInto(row)

  fun confirm() = launchCatching {
    val eval = play.eval()
    if (eval.valid != true) {
      error = str(if (eval.valid == null) R.string.fill_rows_first else R.string.fouled)
      return@launchCatching
    }
    waiting = true
    try {
      val all = listOf(rows) + opponents!!.await()
      val score = Engine.score(all, gameRules)
      // A hand played again is practice: it is neither in the records nor in the totals.
      if (!replaying) {
        lastRecord = records.add(hand, rows, 0.0, gameRules, System.currentTimeMillis() - dealtAt, score.result[0])
        recordCount = records.count()
        settings.addToSession(score.result)
        session = settings.session()
      }
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

  /** The totals and the hand count start over, and so does practice: a new hand, the first. */
  fun resetSession() {
    settings.resetSession()
    session = settings.session()
    deal()
  }

  /** Analyzes a record's hand on the Analyze screen. */
  fun analyzeRecordOnScreen(rec: JSONObject) {
    analyzeRecord(rec)
    tabRequest = tabRequest.first + 1 to "ANALYZE"
  }

  /** The name of a record's rules (the rules field is a full spec; none or a preset's name for older records). */
  fun rulesLabel(full: String): String {
    val values = if ("=" in full) Rules.values(full) else presetValues[full.ifEmpty { "family" }] ?: emptyMap()
    return rulesName(values)
  }

  fun toggleFavorite(rec: JSONObject) {
    favorites.toggle(rec)
    favoriteIds = favorites.ids()
  }

  /** Saves a battle, or drops it if it is saved. */
  fun toggleFavoriteBattle(b: BattleRecord) {
    if (b.id in favoriteBattles.ids()) favoriteBattles.remove(b.id) else favoriteBattles.save(b)
    favoriteBattleIds = favoriteBattles.ids()
  }

  /** The player's record of a battle's hand. */
  fun battleHandRecord(battle: String, hand: Int): JSONObject? =
    records.all().lastOrNull { it.optString("battle") == battle && it.optInt("battle_hand", -1) == hand }
      ?: favorites.all().lastOrNull { it.optString("battle") == battle && it.optInt("battle_hand", -1) == hand }

  /**
   * The same 13 cards again, to arrange another way: the finished hand with the same opponents' cards, or (a record's
   * or a saved hand) with the other 39 cards dealt anew. Not counted in the totals.
   */
  fun replay(cards: List<Int>? = null) {
    val mine = cards ?: hand
    val rest = if (cards == null) others else (0 until 52).filter { it !in mine }.shuffled().chunked(13)
    deal(listOf(mine) + rest, again = true)
    tabRequest = tabRequest.first + 1 to "PLAY"
  }

  /** Re-reads the count: battles add records too. */
  fun refreshRecords() {
    recordCount = records.count()
  }

  /** Deletes the records and the battles; the totals and practice start over too ([resetSession]). */
  fun clearRecords() {
    records.clear()
    battles.clear()
    recordCount = 0
    resetSession()
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
    seatChoices = List(3) { settings.seat(it) }
    anaModel = settings.analyzeModel
  }

  /** The opponents chosen in the settings; a game takes them when it is dealt. */
  var seatChoices by mutableStateOf(List(3) { settings.seat(it) }); private set
  var mcSamples by mutableStateOf(settings.mcSamples); private set

  fun setSeat(i: Int, seat: Seat) {
    settings.setSeat(i, seat)
    seatChoices = List(3) { settings.seat(it) }
  }

  fun chooseMcSamples(n: Int) {
    settings.mcSamples = n
    mcSamples = n
  }
}
