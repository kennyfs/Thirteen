package io.github.kennyfs.thirteen

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The glossary's order and linking (Glossary.kt; its Taiwan Chinese words in res/values-zh-rTW/glossary.xml). */
class GlossaryTest {
  private val strings: Map<String, String> by lazy {
    val xml = File("src/main/res/values-zh-rTW/glossary.xml").readText()
    Regex("""<string name="([^"]+)">([^<]*)</string>""").findAll(xml).associate { it.groupValues[1] to it.groupValues[2] }
  }

  @Test fun longestPhraseFirst() {
    val parts = Glossary.link("三墩都贏叫打槍，被打槍的機率；頭墩衝三是特殊墩。")
    assertEquals(
      listOf("三墩" to "row", "打槍" to "scoop", "打槍" to "scoop", "頭墩" to "row", "衝三" to "front_trips", "特殊墩" to "special"),
      parts.filter { it.termId != null }.map { it.text to it.termId },
    )
    assertEquals("三墩都贏叫打槍，被打槍的機率；頭墩衝三是特殊墩。", parts.joinToString("") { it.text })
  }

  /** Every entry has its words, and each definition uses only the terms before it (or its own). */
  @Test fun eachDefinitionUsesOnlyEarlierTerms() {
    for ((i, e) in Glossary.ENTRIES.withIndex()) {
      val desc = strings["glossary_d_${e.id}"]
      assertTrue("no words for ${e.id}", desc != null && strings["glossary_t_${e.id}"] != null)
      for (p in Glossary.link(desc!!)) {
        val at = Glossary.INDEX_BY_ID[p.termId ?: continue]!!
        assertTrue("${e.id}'s definition uses ${p.termId} (\"${p.text}\"), defined later", at <= i)
      }
      assertTrue(Glossary.link(desc, e.id).none { it.termId == e.id })
    }
    assertEquals(Glossary.ENTRIES.size * 2 + 2, strings.size)
  }
}
