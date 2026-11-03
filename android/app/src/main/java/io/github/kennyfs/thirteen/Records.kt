package io.github.kennyfs.thirteen

import android.content.ContentResolver
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * The player's arrangements, one JSON object per line in the format of gui/collect.html, so an export feeds
 * `thirteen humanfeatures` directly. `rules` is the full rules spec, as `thirteen record` writes it (records without it
 * are family). The extra field `result` (the player's score of the hand) is ignored there.
 */
class RecordStore(dir: File) {
  private val file = File(dir, "records.jsonl")

  @Synchronized
  fun add(hand: List<Int>, rows: Arrangement, c: Double, rules: String, timeMs: Long, result: Double, label: String = "android"): JSONObject {
    val rec = JSONObject()
      .put("version", 1)
      .put("id", UUID.randomUUID().toString())
      .put("hand", Cards.toString(hand))
      .put("front", Cards.toString(rows[0]))
      .put("middle", Cards.toString(rows[1]))
      .put("back", Cards.toString(rows[2]))
      .put("c", c)
      .put("rules", rules)
      .put("time_ms", timeMs)
      .put("timestamp", Instant.now().toString())
      .put("label", label)
      .put("result", result)
    file.appendText(rec.toString() + "\n")
    return rec
  }

  @Synchronized
  fun all(): List<JSONObject> =
    if (!file.exists()) emptyList() else file.readLines().filter { it.isNotBlank() }.map(::JSONObject)

  @Synchronized
  fun count(): Int = if (!file.exists()) 0 else file.readLines().count { it.isNotBlank() }

  @Synchronized
  fun clear() {
    file.delete()
  }

  /** A copy of the records under `dir` for sharing (a FileProvider path), named by `name`. */
  @Synchronized
  fun copyTo(dir: File, name: String): File {
    dir.mkdirs()
    val out = File(dir, name)
    if (file.exists()) file.copyTo(out, overwrite = true) else out.writeText("")
    return out
  }

  @Synchronized
  fun exportTo(resolver: ContentResolver, uri: Uri) {
    resolver.openOutputStream(uri, "wt")!!.use { out -> if (file.exists()) file.inputStream().use { it.copyTo(out) } }
  }
}

/**
 * The hands the player has saved (a really good deal, a really bad one): a copy of each record in the same format, in
 * its own file, so clearing the records for the developer keeps them. Keyed by the record's id.
 */
class FavoriteStore(dir: File) {
  private val file = File(dir, "favorites.jsonl")

  @Synchronized
  fun all(): List<JSONObject> =
    if (!file.exists()) emptyList() else file.readLines().filter { it.isNotBlank() }.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }

  @Synchronized
  fun ids(): Set<String> = all().map { it.optString("id") }.toSet()

  /** Saves the record, or drops it if it is saved; returns whether it is saved now. */
  @Synchronized
  fun toggle(rec: JSONObject): Boolean {
    val id = rec.optString("id")
    val all = all()
    val saved = all.none { it.optString("id") == id }
    val next = if (saved) all + rec else all.filter { it.optString("id") != id }
    file.writeText(next.joinToString("") { it.toString() + "\n" })
    return saved
  }
}
