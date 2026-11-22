package io.github.kennyfs.thirteen

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes

/**
 * The glossary (術語表; the web page's lib/glossary.ts), in each language's res/values-xx/glossary.xml, shown where bool
 * show_glossary is true. The entries are in an order where each definition uses only the terms before it
 * (GlossaryTest). `phrases`: the language's words that link to the entry in running text (glossary_p_<id>; in Chinese
 * the bare 墩 only through its longer forms, as it sits inside other terms).
 */
data class GlossaryEntry(val id: String, @param:StringRes val title: Int, @param:StringRes val desc: Int, @param:ArrayRes val phrases: Int)

data class TermPart(val text: String, val termId: String? = null)

object Glossary {
  val ENTRIES: List<GlossaryEntry> = listOf(
    GlossaryEntry("row", R.string.glossary_t_row, R.string.glossary_d_row, R.array.glossary_p_row),
    GlossaryEntry("rank", R.string.glossary_t_rank, R.string.glossary_d_rank, R.array.glossary_p_rank),
    GlossaryEntry("wheel", R.string.glossary_t_wheel, R.string.glossary_d_wheel, R.array.glossary_p_wheel),
    GlossaryEntry("foul", R.string.glossary_t_foul, R.string.glossary_d_foul, R.array.glossary_p_foul),
    GlossaryEntry("strict", R.string.glossary_t_strict, R.string.glossary_d_strict, R.array.glossary_p_strict),
    GlossaryEntry("scoop", R.string.glossary_t_scoop, R.string.glossary_d_scoop, R.array.glossary_p_scoop),
    GlossaryEntry("home_run", R.string.glossary_t_home_run, R.string.glossary_d_home_run, R.array.glossary_p_home_run),
    GlossaryEntry("front_trips", R.string.glossary_t_front_trips, R.string.glossary_d_front_trips, R.array.glossary_p_front_trips),
    GlossaryEntry("special", R.string.glossary_t_special, R.string.glossary_d_special, R.array.glossary_p_special),
    GlossaryEntry("dragon", R.string.glossary_t_dragon, R.string.glossary_d_dragon, R.array.glossary_p_dragon),
    GlossaryEntry("network", R.string.glossary_t_network, R.string.glossary_d_network, R.array.glossary_p_network),
    GlossaryEntry("q", R.string.glossary_t_q, R.string.glossary_d_q, R.array.glossary_p_q),
    GlossaryEntry("best", R.string.glossary_t_best, R.string.glossary_d_best, R.array.glossary_p_best),
    GlossaryEntry("style", R.string.glossary_t_style, R.string.glossary_d_style, R.array.glossary_p_style),
    GlossaryEntry("rows", R.string.glossary_t_rows, R.string.glossary_d_rows, R.array.glossary_p_rows),
    GlossaryEntry("indep", R.string.glossary_t_indep, R.string.glossary_d_indep, R.array.glossary_p_indep),
    GlossaryEntry("mc", R.string.glossary_t_mc, R.string.glossary_d_mc, R.array.glossary_p_mc),
  )

  val INDEX_BY_ID: Map<String, Int> = ENTRIES.mapIndexed { i, e -> e.id to i }.toMap()

  /**
   * Links one language's phrases (an entry's id to its phrases). Case is ignored; a phrase that begins or ends with a
   * Latin letter or a digit matches only where no other one is next to it ("scoop" not inside "scoopable"), while
   * Chinese and Japanese ones match anywhere, as their words run together.
   */
  class Linker(phrases: Map<String, List<String>>) {
    private val pairs = phrases.flatMap { (id, ps) -> ps.filter { it.isNotBlank() }.map { it to id } }.sortedByDescending { it.first.length }
    private val idOf = pairs.associate { it.first.lowercase() to it.second }
    private val regex = if (pairs.isEmpty()) null else Regex(
      pairs.joinToString("|") { (p, _) ->
        (if (edge(p.first())) "(?<![\\p{IsLatin}\\p{N}])" else "") + Regex.escape(p) + (if (edge(p.last())) "(?![\\p{IsLatin}\\p{N}])" else "")
      },
      setOf(RegexOption.IGNORE_CASE),
    )

    /**
     * `text` in plain runs and links to the terms, the longest phrase first, none overlapping. With `beforeId` (a
     * definition's own), only the terms before that entry.
     */
    fun link(text: String, beforeId: String? = null): List<TermPart> {
      if (text.isEmpty() || regex == null) return listOf(TermPart(text))
      val limit = if (beforeId != null) INDEX_BY_ID[beforeId] ?: -1 else Int.MAX_VALUE
      val out = ArrayList<TermPart>()
      var at = 0
      for (hit in regex.findAll(text)) {
        val id = idOf[hit.value.lowercase()] ?: continue
        if (INDEX_BY_ID.getValue(id) >= limit) continue
        if (hit.range.first > at) out.add(TermPart(text.substring(at, hit.range.first)))
        out.add(TermPart(hit.value, id))
        at = hit.range.last + 1
      }
      if (at < text.length) out.add(TermPart(text.substring(at)))
      return out.ifEmpty { listOf(TermPart(text)) }
    }

    private companion object {
      fun edge(c: Char) = c.isDigit() || Character.UnicodeScript.of(c.code) == Character.UnicodeScript.LATIN
    }
  }
}
