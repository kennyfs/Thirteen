package io.github.kennyfs.thirteen

import io.github.kennyfs.thirteen.Commentary.MistakeKind
import io.github.kennyfs.thirteen.Commentary.Shape
import io.github.kennyfs.thirteen.Commentary.StyleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentaryTest {
  private fun rows(s: String): Arrangement = s.split("/").map { Cards.parse(it.trim()) }
  private val family = mapOf("row" to 0.0, "scoop" to 1.0)

  /** A candidate with front key 1 shl 20 * category (enough for the comparisons here). */
  private fun cand(s: String, cats: List<Int>, q: Double, scoop: Double, scooped: Double, win: List<Double> = listOf(0.5, 0.5, 0.5)) =
    Candidate(
      rows(s), Info(cats.map { it shl 20 }, cats, true, 0, listOf(0.0, 0.0, 0.0)), q,
      listOf(scoop, scooped, 0.0, 0.0, 0.0) + win + win.map { 1 - it } + List(6) { 0.0 }, false,
    )

  private fun analysis(vararg c: Candidate, rules: Map<String, Double> = family) = Analysis(c.toList(), 100, false, 0.0, rules, false)

  @Test
  fun shapes() {
    assertEquals(Shape.FIVE_PAIRS, Commentary.shapeOf(rows("As Ah 7c / Qd Qc Jh Jc 2h / Ks Kd Th Tc 2s"), listOf(2, 3, 3)))
    assertEquals(Shape.FOUR_PAIRS, Commentary.shapeOf(rows("Qs Qd Th / Ah Ac 9s 4s 2c / 8h 8d 6d 6c 5c"), listOf(2, 2, 3)))
    assertEquals(Shape.TWO_FLUSHES, Commentary.shapeOf(rows("Ts 7s 6d / Kc 8c 6c 4c 3c / Ah Kh Qh 9h 4h"), listOf(1, 6, 6)))
    assertEquals(Shape.THREE_FLUSHES, Commentary.shapeOf(rows("Ts 7s 6s / Kc 8c 6c 4c 3c / Ah Kh Qh 9h 4h"), listOf(1, 6, 6)))
    assertEquals(Shape.THREE_STRAIGHTS, Commentary.shapeOf(rows("Ac 2d 3h / 8h 7h 6s 5d 4d / Ts 9d 8d 7c 6c"), listOf(1, 5, 5)))
    // A-2-3 is no straight when A-2-3-4-5 is not one.
    assertEquals(Shape.TWO_STRAIGHTS, Commentary.shapeOf(rows("Ac 2d 3h / 8h 7h 6s 5d 4d / Ts 9d 8d 7c 6c"), listOf(1, 5, 5), wheel = 2))
    assertNull(Commentary.shapeOf(rows("Ah Qh 6c / Kh Kc 4h 4c 3c / Ts 9h 8c 7s 6d"), listOf(1, 3, 5)))
  }

  @Test
  fun missedTwoStraights() {
    val best = cand("Qh 7d 3h / 8h 7h 6s 5d 4d / Ts 9d 8d 7c 6c", listOf(1, 5, 5), -0.514, 0.01, 0.12)
    val mine = cand("Qh 7h 7d / Ts 9d 8h 8d 6s / 7c 6c 5d 4d 3h", listOf(2, 2, 5), -1.035, 0.01, 0.27)
    val r = Commentary.remarks(analysis(best, mine), mine.arrangement)
    val m = r[0] as Commentary.Mistake
    assertEquals(MistakeKind.MISSED, m.kind)
    assertEquals(listOf(5), m.categories)
    assertEquals(StyleKind.BOTH, m.vsMine)
  }

  @Test
  fun straightInsteadOfFlush() {
    val best = cand("Ts Th Jd / Kc Ks 8c 5d 4d / 9c 8s 7h 6c 5c", listOf(2, 2, 5), -0.567, 0.04, 0.16)
    val mine = cand("7h 5d 4d / Ts Th Ks Jd 8s / Kc 9c 8c 6c 5c", listOf(1, 2, 6), -1.585, 0.01, 0.45)
    val m = Commentary.remarks(analysis(best, mine), mine.arrangement)[0] as Commentary.Mistake
    assertEquals(MistakeKind.SWAPPED, m.kind)
    assertEquals(listOf(5), m.categories)
    assertEquals(listOf(6), m.replaced)
  }

  @Test
  fun smallGapIsTheSame() {
    val best = cand("Kh Tc 6c / Jh Jc 8d 3c 2d / As Qs 8s 4s 2s", listOf(1, 2, 6), -0.836, 0.02, 0.23)
    val mine = cand("Kh 8d 6c / Jh Jc Tc 3c 2d / As Qs 8s 4s 2s", listOf(1, 2, 6), -0.860, 0.02, 0.23)
    val r = Commentary.remarks(analysis(best, mine), mine.arrangement)
    assertTrue(r[0] is Commentary.Same)
  }

  @Test
  fun attackAndDefense() {
    val best = cand("4c 4d 8d / 5s Ts Kc Ad As / 3c 3s 9c 9d 9s", listOf(2, 2, 7), 0.0, 0.13, 0.05, listOf(0.8, 0.5, 0.6))
    val alt = cand("Kc Ad As / 3c 3s 4c 4d Ts / 5s 8d 9c 9d 9s", listOf(2, 3, 4), -0.015, 0.08, 0.01, listOf(0.5, 0.7, 0.5))
    val far = { b: Candidate -> b.copy(q = b.q!! - 0.08) }
    var s = Commentary.remarks(analysis(best, far(alt)), null).filterIsInstance<Commentary.Style>().single()
    assertEquals(StyleKind.ATTACK, s.kind)
    assertEquals(0, s.row)
    s = Commentary.remarks(analysis(far(alt).copy(q = 0.0), best.copy(q = -0.08)), null).filterIsInstance<Commentary.Style>().single()
    assertEquals(StyleKind.DEFENSE, s.kind)
    assertEquals(1, s.row)
    // Within 0.03: just as good.
    assertTrue(Commentary.remarks(analysis(best, alt), null)[0] is Commentary.Close)
  }
}
