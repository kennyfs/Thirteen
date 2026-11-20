package io.github.kennyfs.thirteen

import androidx.annotation.StringRes

/**
 * The glossary (術語表; the web page's lib/glossary.ts), in Taiwan Chinese only (res/values/glossary.xml, shown where
 * bool show_glossary is true). The entries are in an order where each definition uses only the terms before it
 * (GlossaryTest). `zh`: the phrases that link to the entry in running text (the bare 墩 only through its longer forms,
 * as it sits inside other terms).
 */
data class GlossaryEntry(val id: String, @param:StringRes val title: Int, @param:StringRes val desc: Int, val zh: List<String>)

data class TermPart(val text: String, val termId: String? = null)

object Glossary {
  val ENTRIES: List<GlossaryEntry> = listOf(
    GlossaryEntry("row", R.string.glossary_t_row, R.string.glossary_d_row, listOf("頭墩", "中墩", "尾墩", "三墩")),
    GlossaryEntry("rank", R.string.glossary_t_rank, R.string.glossary_d_rank, listOf("牌型", "同花順", "鐵支", "葫蘆", "順子", "三條", "兩對", "散牌")),
    GlossaryEntry("wheel", R.string.glossary_t_wheel, R.string.glossary_d_wheel, listOf("A-2-3-4-5")),
    GlossaryEntry("foul", R.string.glossary_t_foul, R.string.glossary_d_foul, listOf("倒水")),
    GlossaryEntry("strict", R.string.glossary_t_strict, R.string.glossary_d_strict, listOf("嚴格墩")),
    GlossaryEntry("scoop", R.string.glossary_t_scoop, R.string.glossary_d_scoop, listOf("打槍")),
    GlossaryEntry("home_run", R.string.glossary_t_home_run, R.string.glossary_d_home_run, listOf("全壘打")),
    GlossaryEntry("front_trips", R.string.glossary_t_front_trips, R.string.glossary_d_front_trips, listOf("衝三")),
    GlossaryEntry("special", R.string.glossary_t_special, R.string.glossary_d_special, listOf("特殊墩")),
    GlossaryEntry("dragon", R.string.glossary_t_dragon, R.string.glossary_d_dragon, listOf("一條龍")),
    GlossaryEntry("network", R.string.glossary_t_network, R.string.glossary_d_network, listOf("神經網路")),
    GlossaryEntry("q", R.string.glossary_t_q, R.string.glossary_d_q, listOf("期望得分", "期望值")),
    GlossaryEntry("best", R.string.glossary_t_best, R.string.glossary_d_best, listOf("首選", "最佳擺法")),
    GlossaryEntry("style", R.string.glossary_t_style, R.string.glossary_d_style, listOf("攻擊型", "防守型")),
    GlossaryEntry("rows", R.string.glossary_t_rows, R.string.glossary_d_rows, listOf("搶墩")),
    GlossaryEntry("indep", R.string.glossary_t_indep, R.string.glossary_d_indep, listOf("獨立墩")),
    GlossaryEntry("mc", R.string.glossary_t_mc, R.string.glossary_d_mc, listOf("蒙地卡羅")),
  )

  val INDEX_BY_ID: Map<String, Int> = ENTRIES.mapIndexed { i, e -> e.id to i }.toMap()

  private val PAIRS = ENTRIES.flatMap { e -> e.zh.map { it to e.id } }.sortedByDescending { it.first.length }
  private val MAP = PAIRS.toMap()
  private val REGEX = Regex(PAIRS.joinToString("|") { Regex.escape(it.first) })

  /**
   * `text` in plain runs and links to the terms, the longest phrase first, none overlapping. With `beforeId` (a
   * definition's own), only the terms before that entry.
   */
  fun link(text: String, beforeId: String? = null): List<TermPart> {
    if (text.isEmpty()) return listOf(TermPart(text))
    val limit = if (beforeId != null) INDEX_BY_ID[beforeId] ?: -1 else Int.MAX_VALUE
    val out = ArrayList<TermPart>()
    var at = 0
    for (hit in REGEX.findAll(text)) {
      val id = MAP[hit.value] ?: continue
      if (INDEX_BY_ID.getValue(id) >= limit) continue
      if (hit.range.first > at) out.add(TermPart(text.substring(at, hit.range.first)))
      out.add(TermPart(hit.value, id))
      at = hit.range.last + 1
    }
    if (at < text.length) out.add(TermPart(text.substring(at)))
    return out.ifEmpty { listOf(TermPart(text)) }
  }
}
