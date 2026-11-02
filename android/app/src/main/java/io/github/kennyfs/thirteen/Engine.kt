package io.github.kennyfs.thirteen

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class EngineException(message: String) : Exception(message)

/**
 * The native engine (cpp/command/guiengine.h): one JSON request, one JSON answer. Requests run one at a time on a
 * dedicated thread, in the order they are made.
 */
object Engine {
  init {
    System.loadLibrary("thirteen_jni")
  }

  private val handle = nativeCreate()
  private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "engine") }.asCoroutineDispatcher()

  suspend fun request(req: JSONObject): JSONObject = withContext(dispatcher) {
    val answer = JSONObject(nativeHandle(handle, req.toString()))
    if (answer.has("error")) throw EngineException(answer.getString("error"))
    answer
  }

  suspend fun deal(): List<List<Int>> =
    request(JSONObject().put("cmd", "deal")).getJSONArray("hands").let { hands -> List(4) { intList(hands.getJSONArray(it)) } }

  suspend fun arrange(spec: String, hand: List<Int>, rules: String): Arrangement {
    val req = JSONObject().put("cmd", "arrange").put("player", spec).put("hand", JSONArray(hand)).put("rules", rules)
    return rowsOf(request(req).getJSONArray("arrangement"))
  }

  /**
   * Keys and categories of rows being filled (0 for a row that is not full); valid and each row's royalty once all are
   * full.
   */
  suspend fun rows(rows: Arrangement, rules: String): RowsEval {
    val a = request(JSONObject().put("cmd", "rows").put("rows", toJson(rows)).put("rules", rules))
    return RowsEval(
      intList(a.getJSONArray("keys")), intList(a.getJSONArray("categories")),
      if (a.has("valid")) a.getBoolean("valid") else null,
      a.optJSONArray("royalties")?.let(::doubleList) ?: listOf(0.0, 0.0, 0.0),
    )
  }

  /** The values of a rules spec, after the engine checks it (an EngineException for bad rules). */
  suspend fun rules(spec: String): Map<String, Double> {
    val v = request(JSONObject().put("cmd", "rules").put("rules", spec)).getJSONObject("values")
    return v.keys().asSequence().associateWith { v.getDouble(it) }
  }

  /** The full spec of a rules spec, every value given ("c=0,row=1,..."), as records store it. */
  suspend fun rulesFull(spec: String): String = request(JSONObject().put("cmd", "rules").put("rules", spec)).getString("rules")

  /** Whether a network predates the rules input (it then plays as under the family rules). */
  suspend fun isLegacy(model: String): Boolean = request(JSONObject().put("cmd", "model").put("model", model)).getBoolean("legacy")

  suspend fun score(arrangements: List<Arrangement>, rules: String): Score {
    val req = JSONObject().put("cmd", "score").put("arrangements", JSONArray(arrangements.map(::toJson))).put("rules", rules)
    val a = request(req)
    val scoops = a.getJSONArray("scoops")
    return Score(
      scoops = List(4) { i -> scoops.getJSONArray(i).let { row -> List(4) { row.getBoolean(it) } } },
      homeRun = a.getJSONArray("home_run").let { h -> List(4) { h.getBoolean(it) } },
      points = doubleList(a.getJSONArray("points")),
      dealerCost = doubleList(a.getJSONArray("dealer_cost")),
      result = doubleList(a.getJSONArray("result")),
      pairs = a.getJSONArray("pairs").let { p -> List(4) { doubleList(p.getJSONArray(it)) } },
      info = a.getJSONArray("info").let { i -> List(4) { infoOf(i.getJSONObject(it)) } },
    )
  }

  /** The undominated arrangements plus `extra`, with the model's Q and event probabilities when a model is given. */
  suspend fun analyze(hand: List<Int>, rules: String, model: String?, extra: List<Arrangement>): Analysis {
    val req = JSONObject().put("cmd", "analyze").put("hand", JSONArray(hand)).put("rules", rules)
      .put("extra", JSONArray(extra.map(::toJson)))
    if (model != null) req.put("model", model)
    val a = request(req)
    val cands = a.getJSONArray("cands")
    return Analysis(
      cands = List(cands.length()) { i ->
        val cd = cands.getJSONObject(i)
        Candidate(
          rowsOf(cd.getJSONArray("arrangement")), infoOf(cd.getJSONObject("info")),
          if (cd.has("q")) cd.getDouble("q") else null,
          if (cd.has("probs")) doubleList(cd.getJSONArray("probs")) else null,
          cd.getBoolean("extra"),
        )
      },
      numValid = a.getInt("num_valid"),
      dragon = a.getBoolean("dragon"),
      oppBonus = if (a.has("opp_bonus")) a.getDouble("opp_bonus") else null,
      rules = Rules.values(a.getString("rules")),
      legacyModel = a.optBoolean("legacy_model", false),
    )
  }

  private fun intList(a: JSONArray) = List(a.length()) { a.getInt(it) }
  private fun doubleList(a: JSONArray) = List(a.length()) { a.getDouble(it) }
  private fun rowsOf(a: JSONArray): Arrangement = List(3) { intList(a.getJSONArray(it)) }
  private fun toJson(rows: Arrangement) = JSONArray(rows.map { JSONArray(it) })
  private fun infoOf(o: JSONObject) = Info(
    intList(o.getJSONArray("keys")), intList(o.getJSONArray("categories")), o.getBoolean("valid"), o.getInt("bonus"),
    doubleList(o.getJSONArray("royalties")),
  )

  @JvmStatic private external fun nativeCreate(): Long
  @JvmStatic private external fun nativeHandle(handle: Long, request: String): String
}

/** Front, middle and back, each a list of card ids. */
typealias Arrangement = List<List<Int>>

/** `royalties`: each row's under the rules (0 for none or a foul). */
data class Info(val keys: List<Int>, val categories: List<Int>, val valid: Boolean, val bonus: Int, val royalties: List<Double>)
data class RowsEval(val keys: List<Int>, val categories: List<Int>, val valid: Boolean?, val royalties: List<Double>)
data class Score(
  val scoops: List<List<Boolean>>, val homeRun: List<Boolean>, val points: List<Double>, val dealerCost: List<Double>,
  val result: List<Double>, val info: List<Info>,
  /** pairs[i][j]: what j pays i, home runs included. */
  val pairs: List<List<Double>>,
)
data class Candidate(val arrangement: Arrangement, val info: Info, val q: Double?, val probs: List<Double>?, val extra: Boolean)
/** `rules`: the values the hand was analyzed under; `legacyModel`: the network plays as under family whatever they are. */
data class Analysis(
  val cands: List<Candidate>, val numValid: Int, val dragon: Boolean, val oppBonus: Double?, val rules: Map<String, Double>,
  val legacyModel: Boolean,
)
