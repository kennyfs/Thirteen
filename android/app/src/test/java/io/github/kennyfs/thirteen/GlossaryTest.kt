package io.github.kennyfs.thirteen

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The glossary's order and linking (Glossary.kt; its words in res/values(-xx)/glossary.xml), in every language that shows it. */
class GlossaryTest {
  private class Lang(val dir: String) {
    private fun read(name: String) = File("src/main/res/$dir/$name").takeIf { it.exists() }?.readText()?.replace(Regex("<!--[\\s\\S]*?-->"), "") ?: ""
    private val glossary = read("glossary.xml")
    val strings: Map<String, String> =
      Regex("""<string name="([^"]+)">([^<]*)</string>""").findAll(glossary).associate { it.groupValues[1] to it.groupValues[2].replace("\\'", "'") }
    val phrases: Map<String, List<String>> = Regex("""<string-array name="glossary_p_([^"]+)">([\s\S]*?)</string-array>""").findAll(glossary)
      .associate { m -> m.groupValues[1] to Regex("<item>([^<]*)</item>").findAll(m.groupValues[2]).map { it.groupValues[1] }.toList() }
    /** All the language's words: its strings and its glossary. */
    val text: String = read("strings.xml") + glossary
    val shown: Boolean? = Regex("""<bool name="show_glossary">(\w+)</bool>""").find(read("bools.xml"))?.groupValues?.get(1)?.toBoolean()
    val linker by lazy { Glossary.Linker(phrases) }
  }

  private val dirs = listOf("values", "values-zh-rTW", "values-zh-rHK", "values-zh-rCN", "values-ja", "values-vi", "values-in")
  private val langs = dirs.associateWith { Lang(it) }
  /** The languages that show the glossary (a language without its own bool takes the default's). */
  private val shown = dirs.filter { (langs.getValue(it).shown ?: langs.getValue("values").shown) == true }

  @Test fun englishAndTaiwanChineseShowIt() {
    assertTrue("values" in shown && "values-zh-rTW" in shown)
  }

  @Test fun longestPhraseFirst() {
    val parts = langs.getValue("values-zh-rTW").linker.link("三墩都贏叫打槍，被打槍的機率；頭墩衝三是特殊墩。")
    assertEquals(
      listOf("三墩" to "row", "打槍" to "scoop", "打槍" to "scoop", "頭墩" to "row", "衝三" to "front_trips", "特殊墩" to "special"),
      parts.filter { it.termId != null }.map { it.text to it.termId },
    )
    assertEquals("三墩都贏叫打槍，被打槍的機率；頭墩衝三是特殊墩。", parts.joinToString("") { it.text })
  }

  /** Latin words: any case, whole words only. */
  @Test fun wholeWordsAnyCase() {
    val parts = langs.getValue("values").linker.link("Scooped twice, a scoopable front row; the best one attacks.")
    assertEquals(
      listOf("Scooped" to "scoop", "front row" to "row", "best one" to "best", "attacks" to "style"),
      parts.filter { it.termId != null }.map { it.text to it.termId },
    )
  }

  /** Each shown language: every entry has its words and phrases, and each definition uses only the terms before it. */
  @Test fun eachDefinitionUsesOnlyEarlierTerms() {
    for (dir in shown) {
      val l = langs.getValue(dir)
      for ((i, e) in Glossary.ENTRIES.withIndex()) {
        val desc = l.strings["glossary_d_${e.id}"]
        assertTrue("$dir: no words for ${e.id}", desc != null && l.strings["glossary_t_${e.id}"] != null)
        assertTrue("$dir: no phrases for ${e.id}", l.phrases[e.id].orEmpty().isNotEmpty())
        for (p in l.linker.link(desc!!)) {
          val at = Glossary.INDEX_BY_ID[p.termId ?: continue]!!
          assertTrue("$dir: ${e.id}'s definition uses ${p.termId} (\"${p.text}\"), defined later", at <= i)
        }
      }
      assertEquals("$dir: glossary strings", Glossary.ENTRIES.size * 2 + 2, l.strings.size)
    }
  }

  /** Each shown language: some phrase of every entry is one the language's own words use (not a guess at a word). */
  @Test fun phrasesAreUsed() {
    for (dir in shown) {
      val l = langs.getValue(dir)
      val used = l.linker.link(l.text).mapNotNull { it.termId }.toSet()
      for (e in Glossary.ENTRIES) assertTrue("$dir: no phrase of ${e.id} occurs in its words", e.id in used)
    }
  }
}
