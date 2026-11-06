package io.github.kennyfs.thirteen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kennyfs.thirteen.ui.CARD_TAG
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The welcome card of a first start, the tutorial (its pages, the hand to try judged by the AI), the tour (every
 * step, and Back ending it midway), the previous hand's result and analysis, and a swipe over the analysis's cards.
 * Screenshots in /data/local/tmp/tut for a person to look over.
 */
@RunWith(AndroidJUnit4::class)
class TutorialTourTest {
  @get:Rule val compose = createEmptyComposeRule()
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext

  private fun shell(cmd: String) = instrumentation.uiAutomation.executeShellCommand(cmd).close()

  private fun shot(name: String) {
    compose.waitForIdle()
    Thread.sleep(500)
    shell("screencap -p /data/local/tmp/tut/$name.png")
    Thread.sleep(300)
  }

  private fun card(c: Int) = hasTestTag(CARD_TAG) and hasContentDescription(context.resources.getStringArray(R.array.suit_names)[Cards.suit(c)] + " " + Cards.rankText(c))

  @Test
  fun welcomeTutorialTourLastHand() {
    shell("mkdir -p /data/local/tmp/tut")
    context.getSharedPreferences("settings", 0).edit().putBoolean("welcome.seen", false).commit()
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      lateinit var vm: AppViewModel
      scenario.onActivity { vm = ViewModelProvider(it)[AppViewModel::class.java] }
      compose.waitUntil(30_000) { vm.unplaced.size == 13 }

      // The welcome card, then the tutorial.
      compose.onNodeWithTag("welcome_learn").assertExists()
      shot("01-welcome")
      compose.onNodeWithTag("welcome_learn").performClick()
      for (page in 1..7) {
        shot("02-tut$page")
        if (page == 5) {
          // The instinctive rows first: the aces in the back with the queens.
          for (c in Cards.parse("Kd 6s 2h")) compose.onNode(card(c)).performClick()
          compose.onNodeWithTag("row_0").performClick()
          for (c in Cards.parse("Kh Jh 7h 6h 3h")) compose.onNode(card(c)).performClick()
          compose.onNodeWithTag("row_1").performClick()
          compose.waitForIdle()
          shot("02-tut5-set")
          compose.onNodeWithText(context.getString(R.string.tut_try_check)).performClick()
          compose.waitUntil(30_000) { compose.onAllNodesWithTag(CARD_TAG).fetchSemanticsNodes().size == 26 }
          shot("02-tut5-greedy")
          compose.onNodeWithText(context.getString(R.string.tut_try_again)).performScrollTo().performClick()
          compose.waitUntil(10_000) { compose.onAllNodesWithTag(CARD_TAG).fetchSemanticsNodes().size == 13 }
          for (c in Cards.parse("As Ad Kd")) compose.onNode(card(c)).performClick()
          compose.onNodeWithTag("row_0").performClick()
          for (c in Cards.parse("Kh Jh 7h 3h 2h")) compose.onNode(card(c)).performClick()
          compose.onNodeWithTag("row_1").performClick()
          compose.onNodeWithText(context.getString(R.string.tut_try_check)).performClick()
          compose.waitUntil(30_000) { compose.onAllNodesWithText(context.getString(R.string.tut_try_best)).fetchSemanticsNodes().isNotEmpty() }
          shot("02-tut5-best")
        }
        compose.onNodeWithTag("tut_next").performClick()
      }
      assertTrue(!vm.welcome)
      assertEquals(13, vm.unplaced.size)

      // The tour from the settings, Back at its third step: nothing changed.
      val hand = vm.hand
      compose.onNodeWithTag("nav_SETTINGS").performClick()
      compose.onNodeWithText(context.getString(R.string.tour_title)).performScrollTo().performClick()
      for (step in 1..3) {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("tour_card").fetchSemanticsNodes().isNotEmpty() }
        shot("03-tour-back$step")
        if (step < 3) compose.onNodeWithTag("tour_next").performClick()
      }
      Espresso.pressBack()
      compose.waitForIdle()
      assertTrue(compose.onAllNodesWithTag("tour_card").fetchSemanticsNodes().isEmpty())
      assertEquals(hand, vm.hand)
      // The whole tour.
      compose.onNodeWithTag("nav_SETTINGS").performClick()
      compose.onNodeWithText(context.getString(R.string.tour_title)).performScrollTo().performClick()
      var steps = 0
      while (compose.onAllNodesWithTag("tour_card").fetchSemanticsNodes().isNotEmpty() && steps < 12) {
        steps++
        shot("04-tour$steps")
        compose.onNodeWithTag("tour_next").performClick()
      }
      assertEquals(8, steps)

      // The previous hand.
      val rows = runBlocking { Engine.arrange("indep", vm.hand, vm.gameRules) }
      instrumentation.runOnMainSync { vm.play.reset(vm.hand, vm.gameRules, rows); vm.confirm() }
      compose.waitUntil(30_000) { vm.result != null }
      instrumentation.runOnMainSync { vm.deal() }
      compose.waitUntil(30_000) { vm.result == null && vm.unplaced.size == 13 }
      shot("05-last-button")
      compose.onNodeWithTag("last_hand").performClick()
      compose.waitForIdle()
      assertEquals(1, vm.peek)
      shot("05-last-result")
      compose.onNodeWithText(context.getString(R.string.analyze_my_hand)).performClick()
      compose.waitUntil(30_000) { vm.prev?.analysis != null }
      shot("05-last-analysis")
      Espresso.pressBack()
      compose.waitForIdle()
      assertEquals(1, vm.peek)
      Espresso.pressBack()
      compose.waitForIdle()
      assertEquals(0, vm.peek)
      shot("05-back-to-hand")

      // A swipe over the analysis's cards picks five.
      compose.onNodeWithTag("nav_ANALYZE").performClick()
      compose.waitForIdle()
      instrumentation.runOnMainSync { vm.setAnalyzeHand(emptySet()) }
      compose.waitForIdle()
      val centers = compose.onAllNodesWithTag(CARD_TAG).fetchSemanticsNodes().map { it.boundsInRoot.center }
      compose.onRoot().performTouchInput { swipe(Offset(centers[0].x, centers[0].y), Offset(centers[4].x, centers[4].y), 500) }
      compose.waitForIdle()
      shot("06-analyze-swipe")
      assertEquals(5, vm.anaHand.size)
    }
  }
}
