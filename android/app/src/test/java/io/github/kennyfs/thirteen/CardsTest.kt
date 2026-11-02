package io.github.kennyfs.thirteen

import org.junit.Assert.assertEquals
import org.junit.Test

class CardsTest {
  private fun card(s: String) = Cards.RANKS.indexOf(s[0]) * 4 + Cards.SUITS.indexOf(s[1])
  private fun cards(s: String) = s.split(" ").map(::card)
  private fun layout(s: String, category: Int) = Cards.rowLayout(cards(s), category).joinToString(" ") { Cards.toString(it.card) }

  @Test
  fun recordFormat() {
    assertEquals("As Td 2c", Cards.toString(cards("2c As Td")))
  }

  @Test
  fun layoutGroupsFirst() {
    // 2 2 3 3 5 reads 3 3 2 2 5; the lower pair is light, the kicker untinted.
    val row = Cards.rowLayout(cards("2c 2d 3c 3d 5s"), 3)
    assertEquals("3d 3c 2d 2c 5s", row.joinToString(" ") { Cards.toString(it.card) })
    assertEquals(listOf(false, false, true, true, false), row.map { it.light })
    assertEquals(listOf(3, 3, 3, 3, 0), row.map { it.tint })
    assertEquals("Ks Kd Kc 9s 9h", layout("9h Kc 9s Kd Ks", 7))
    // The wheel reads 5 4 3 2 A.
    assertEquals("5c 4d 3h 2s Ac", layout("Ac 2s 3h 4d 5c", 5))
  }

  @Test
  fun rowLabels() {
    // Category in bits 20-23, then the tie-break ranks from bit 16 down.
    assertEquals("Pair A", Cards.rowLabel((2 shl 20) or (12 shl 16)))
    assertEquals("Two pair K 7", Cards.rowLabel((3 shl 20) or (11 shl 16) or (5 shl 12)))
    assertEquals("Straight to 10", Cards.rowLabel((5 shl 20) or (8 shl 16)))
  }

  @Test
  fun wheelLabels() {
    // Under wheel 1, a straight to A has 13 and A-2-3-4-5 has 12.
    assertEquals("Straight to A", Cards.rowLabel((5 shl 20) or (13 shl 16), wheel = 1))
    assertEquals("Straight to 5", Cards.rowLabel((5 shl 20) or (12 shl 16), wheel = 1))
    assertEquals("Str. flush to A", Cards.rowLabel((9 shl 20) or (12 shl 16)))
  }

  @Test
  fun ruleSpecs() {
    val spec = RuleSpec("taiwan", mapOf("scoop" to 2.0, "on_win" to 1.0))
    assertEquals("taiwan,scoop=2,on_win=1", spec.spec())
    assertEquals(spec, RuleSpec.parse(spec.spec()))
    assertEquals(RuleSpec(), RuleSpec.parse("nonsense,row=1"))
    val values = Rules.values("c=0,row=1,scoop=3,wheel=2")
    assertEquals(2.0, values["wheel"]!!, 0.0)
    assertEquals(true, Rules.same(values, values + ("c" to 0.5)))
    assertEquals("taiwan", Rules.presetOf(values, mapOf("family" to emptyMap(), "taiwan" to values)))
    assertEquals(0.1, Rules.notableQGap(mapOf("scoop" to 1.0, "row" to 0.0)), 1e-9)
    assertEquals(0.9, Rules.notableQGap(values), 1e-9)
  }
}
