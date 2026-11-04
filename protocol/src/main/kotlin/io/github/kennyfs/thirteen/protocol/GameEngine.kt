package io.github.kennyfs.thirteen.protocol

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class EngineError(message: String) : Exception(message)

/** What a room asks of the engine. Rules are full specs as rules() returns them. */
interface GameEngine {
  /** The full spec of a checked spec; EngineError for rules the engine rejects. */
  suspend fun rules(spec: String): String
  suspend fun valid(rows: Rows, rules: String): Boolean
  /** A network's arrangement, for a seat nobody plays. */
  suspend fun bot(hand: List<Int>, rules: String): Rows
  /** The strongest back, then middle, then front: what a player who runs out of time gets. */
  suspend fun greedy(hand: List<Int>, rules: String): Rows
  suspend fun score(plays: List<Rows>, rules: String): TableScore
  /** The network's best arrangement of a hand and what each play loses against it (best Q minus its Q, at least 0). */
  suspend fun losses(hand: List<Int>, rules: String, plays: List<Rows>): Pair<Rows, List<Double>>
}

/**
 * GameEngine over the engine's JSON requests (cpp/command/guiengine.h), one request line to one answer line through
 * `call`: a `thirteen gui` process on the server, the JNI engine on a phone. `model` is the network's path. Every
 * request is built here from checked values; nothing a client sends reaches the engine as text.
 */
class EngineClient(private val model: String, private val call: suspend (String) -> String) : GameEngine {
  private suspend fun ask(req: JsonObject): JsonObject {
    val answer = BattleJson.parseToJsonElement(call(req.toString())).jsonObject
    answer["error"]?.let { throw EngineError(it.jsonPrimitive.content) }
    return answer
  }

  private fun cards(cards: List<Int>) = JsonArray(cards.map { JsonPrimitive(it) })
  private fun rows(rows: Rows) = JsonArray(rows.map(::cards))
  private fun rowsOf(e: JsonElement): Rows = e.jsonArray.map { r -> r.jsonArray.map { it.jsonPrimitive.int } }
  private fun doubles(e: JsonElement) = e.jsonArray.map { it.jsonPrimitive.double }

  override suspend fun rules(spec: String): String =
    ask(buildJsonObject { put("cmd", "rules"); put("rules", spec) })["rules"]!!.jsonPrimitive.content

  override suspend fun valid(rows: Rows, rules: String): Boolean =
    ask(buildJsonObject { put("cmd", "rows"); put("rows", rows(rows)); put("rules", rules) })["valid"]?.jsonPrimitive?.boolean == true

  private suspend fun arrange(player: String, hand: List<Int>, rules: String): Rows =
    rowsOf(ask(buildJsonObject { put("cmd", "arrange"); put("player", player); put("hand", cards(hand)); put("rules", rules) })["arrangement"]!!)

  override suspend fun bot(hand: List<Int>, rules: String) = arrange("net:model=$model", hand, rules)

  override suspend fun greedy(hand: List<Int>, rules: String) = arrange("greedy", hand, rules)

  override suspend fun score(plays: List<Rows>, rules: String): TableScore {
    val a = ask(buildJsonObject { put("cmd", "score"); put("arrangements", JsonArray(plays.map(::rows))); put("rules", rules) })
    return TableScore(
      result = doubles(a["result"]!!),
      points = doubles(a["points"]!!),
      scoops = a["scoops"]!!.jsonArray.map { r -> r.jsonArray.map { it.jsonPrimitive.boolean } },
      homeRun = a["home_run"]!!.jsonArray.map { it.jsonPrimitive.boolean },
      pairs = a["pairs"]!!.jsonArray.map(::doubles),
    )
  }

  override suspend fun losses(hand: List<Int>, rules: String, plays: List<Rows>): Pair<Rows, List<Double>> {
    val distinct = plays.distinctBy(::canonical)
    val a = ask(buildJsonObject {
      put("cmd", "analyze"); put("hand", cards(hand.sorted())); put("rules", rules); put("model", model)
      put("extra", JsonArray(distinct.map(::rows)))
    })
    val cands = a["cands"]!!.jsonArray.map { it.jsonObject }
    val q = cands.map { it["q"]!!.jsonPrimitive.double }
    val keys = cands.map { c -> c["info"]!!.jsonObject["keys"]!!.jsonArray.map { it.jsonPrimitive.int } }
    val arrangements = cands.map { rowsOf(it["arrangement"]!!) }
    val byCards = arrangements.indices.associateBy { canonical(arrangements[it]) }
    val losses = plays.map { play ->
      // A play with the same row hands as a candidate but other kickers or suits took that candidate's place.
      val i = byCards[canonical(play)] ?: keys.indexOf(rowKeys(play, rules))
      if (i < 0) throw EngineError("A play is not among the candidates")
      (q[0] - q[i]).coerceAtLeast(0.0)
    }
    return arrangements[0] to losses
  }

  private suspend fun rowKeys(rows: Rows, rules: String): List<Int> =
    ask(buildJsonObject { put("cmd", "rows"); put("rows", rows(rows)); put("rules", rules) })["keys"]!!.jsonArray.map { it.jsonPrimitive.int }

  companion object {
    fun canonical(rows: Rows): List<List<Int>> = rows.map { it.sorted() }
  }
}
