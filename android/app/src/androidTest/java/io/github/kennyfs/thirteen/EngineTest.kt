package io.github.kennyfs.thirteen

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The native engine on a device: every player type under each preset, scoring, rules, and the built-in network. */
@RunWith(AndroidJUnit4::class)
class EngineTest {
  private val settings = Settings(InstrumentationRegistry.getInstrumentation().targetContext)

  @Test
  fun playsAHandUnderEachPreset() = runBlocking {
    for (rules in Rules.PRESETS) {
      val hands = Engine.deal()
      assertEquals(52, hands.flatten().toSet().size)
      val seats = listOf(Seat(Kind.NET, settings.models().first().path), Seat(Kind.MC, ""), Seat(Kind.INDEP, ""))
      val arrangements = listOf(Engine.arrange("greedy", hands[0], rules)) +
        seats.mapIndexed { i, seat -> Engine.arrange(settings.spec(seat), hands[i + 1], rules) }
      for ((i, a) in arrangements.withIndex()) assertEquals(hands[i].toSet(), a.flatten().toSet())
      val score = Engine.score(arrangements, rules)
      assertEquals(0.0, score.result.sum(), 1e-9)
      for (i in 0 until 4) assertEquals(score.points[i], score.pairs[i].sum(), 1e-9)
      val eval = Engine.rows(arrangements[0], rules)
      assertEquals(score.info[0].keys, eval.keys)
      assertTrue(eval.valid == true)
    }
  }

  @Test
  fun rulesChangeThePlay() = runBlocking {
    // Two straights to 6 in the middle and back: equal rows, a foul only under strict rows (pagat).
    val rows = listOf(listOf(51, 47, 43), listOf(0, 5, 10, 15, 16), listOf(1, 6, 11, 12, 17))
    assertTrue(Engine.rows(rows, "family").valid == true)
    assertFalse(Engine.rows(rows, "pagat").valid == true)
    // A-2-3-4-5: the lowest straight, the second highest, or no straight (high card).
    val wheel = listOf(listOf(51, 47, 43), listOf(48, 1, 6, 11, 12), listOf(13, 18, 23, 28, 33))
    assertEquals(5, Cards.categoryOf(Engine.rows(wheel, "family,wheel=1").keys[1]))
    assertEquals(1, Cards.categoryOf(Engine.rows(wheel, "family,wheel=2").keys[1]))
    assertEquals("Straight to 5", Cards.rowLabel(Engine.rows(wheel, "family,wheel=1").keys[1], wheel = 1))
    val taiwan = Engine.rules("taiwan")
    assertEquals(36.0, taiwan["dragon"]!!, 0.0)
    assertEquals(taiwan, Rules.values(Engine.rulesFull("taiwan")))
    assertTrue(runCatching { Engine.rules("family,middle_quads=5,middle_straight_flush=1") }.isFailure)
  }

  @Test
  fun builtInIsCurrent() {
    // An older built-in left by an update is gone, and a saved path to it falls back to the current one.
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    for (old in Settings.RETIRED) java.io.File(context.filesDir, old).writeText("left by an older version")
    val settings = Settings(context)
    for (old in Settings.RETIRED) assertFalse(java.io.File(context.filesDir, old).exists())
    val builtIn = settings.models().first()
    assertTrue(builtIn.builtIn && builtIn.name == "run2_net10")
    assertEquals(builtIn.path, settings.model(java.io.File(context.filesDir, "net_10.onnx").path).path)
  }

  @Test
  fun analyzesWithTheNetwork() = runBlocking {
    val hand = Engine.deal()[0]
    val model = settings.models().first().path
    // The built-in network takes the rules: no family-only warning under any of them.
    assertFalse(Engine.isLegacy(model))
    for (rules in listOf("family", "taiwan", "pagat", "family,strict=1,wheel=2,scoop=2")) {
      val a = Engine.analyze(hand, rules, model, emptyList())
      assertTrue(a.cands.isNotEmpty())
      val q = a.cands.map { it.q!! }
      assertEquals(q.sortedDescending(), q)
      assertEquals(Engine.rules(rules), a.rules)
      assertFalse(a.legacyModel)
    }
  }
}
