package io.github.kennyfs.thirteen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kennyfs.thirteen.ui.CARD_TAG
import org.junit.Assert.assertEquals
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A swipe over the hand selects the cards it passes, or unselects them when it starts on a selected card. */
@RunWith(AndroidJUnit4::class)
class SwipeSelectTest {
  @get:Rule val compose = createEmptyComposeRule()

  @Test
  fun swipeSelectsThenUnselects() {
    // The welcome card of a first start would cover the screens.
    InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("settings", 0).edit().putBoolean("welcome.seen", true).commit()
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      lateinit var vm: AppViewModel
      scenario.onActivity { vm = ViewModelProvider(it)[AppViewModel::class.java] }
      compose.waitUntil(20_000) { vm.unplaced.size == 13 }
      compose.waitForIdle()
      // With the rows empty, the 13 cards on screen are the hand, line by line (one or two), left to right.
      val centers = compose.onAllNodesWithTag(CARD_TAG).fetchSemanticsNodes().map { it.boundsInRoot.center }
        .sortedWith(compareBy({ Math.round(it.y / 20f) }, { it.x }))
      assertEquals(13, centers.size)
      val hand = vm.unplaced
      fun drag(from: Int, to: Int) {
        compose.onRoot().performTouchInput { swipe(Offset(centers[from].x, centers[from].y), Offset(centers[to].x, centers[to].y), 400) }
        compose.waitForIdle()
      }
      drag(0, 3)
      assertEquals(hand.take(4).toSet(), vm.selected)
      drag(3, 1)
      assertEquals(setOf(hand[0]), vm.selected)
    }
  }
}
