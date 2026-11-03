package io.github.kennyfs.thirteen

import android.app.LocaleManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kennyfs.thirteen.ui.CARD_TAG
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every screen under a range of screen sizes, display sizes (density) and font sizes: a screenshot of each in
 * /data/local/tmp/shots for a person to look over (device-tests.sh pulls them), and a check that no card is cut off at the side.
 * A card cut off at the bottom is only logged: a page that scrolls has those. It changes the device's display and font
 * settings and puts them back after; device-tests.sh runs it apart from the other tests. With the argument store=1
 * (STORE=1 device-tests.sh) it takes the Google Play screenshots instead: 16:9, in Chinese and English, with the
 * system's demo status bar.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
  @get:Rule val compose = createEmptyComposeRule()
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val cutOff = mutableListOf<String>()

  private data class Config(
    val name: String, val size: String, val density: Int, val fontScale: Float, val locale: String, val cutout: Boolean,
    val landscape: Boolean = false, val dark: Boolean = false, val style: ColorStyle = ColorStyle.TABLE,
  )

  private fun shell(cmd: String): String =
    ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd)).use { String(it.readBytes()) }

  @After
  fun restore() {
    if (STORE) shell("am broadcast -a com.android.systemui.demo -e command exit")
    shell("wm size reset")
    shell("wm density reset")
    shell("settings put system font_scale 1.0")
    shell("cmd overlay disable $CUTOUT")
    shell("settings put system user_rotation 0")
    shell("cmd uimode night no")
    instrumentation.targetContext.getSharedPreferences("settings", 0).edit().remove("look.colors").remove("look.text").commit()
  }

  @Test
  fun everyScreenFits() {
    shell("mkdir -p $DIR")
    // The rotation is set for each config, not followed from the sensor.
    shell("settings put system accelerometer_rotation 0")
    shell("cmd overlay enable com.android.internal.systemui.navbar.gestural")
    for (config in if (STORE) STORE_CONFIGS else if (COMPARE) COMPARE_CONFIGS else CONFIGS) if (ONLY.isEmpty() || config.name in ONLY) shoot(config)
    assertTrue("Cards cut off at the side:\n" + cutOff.joinToString("\n"), cutOff.isEmpty())
  }

  private fun demoStatusBar() {
    shell("settings put global sysui_demo_allowed 1")
    for (extras in listOf(
      "-e command enter",
      "-e command clock -e hhmm 1200",
      "-e command battery -e level 100 -e plugged false",
      "-e command network -e wifi show -e level 4 -e fully true",
      "-e command notifications -e visible false",
    )) shell("am broadcast -a com.android.systemui.demo $extras")
  }

  private fun shoot(c: Config) {
    shell("wm size ${c.size}")
    shell("wm density ${c.density}")
    shell("settings put system font_scale ${c.fontScale}")
    shell("cmd overlay ${if (c.cutout) "enable" else "disable"} $CUTOUT")
    shell("settings put system user_rotation ${if (c.landscape) 1 else 0}")
    shell("cmd uimode night ${if (c.dark) "yes" else "no"}")
    // After the change of density, which resets the status bar.
    if (STORE) demoStatusBar()
    if (Build.VERSION.SDK_INT >= 33)
      instrumentation.targetContext.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(c.locale)
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      lateinit var vm: AppViewModel
      scenario.onActivity { vm = ViewModelProvider(it)[AppViewModel::class.java] }
      compose.waitUntil(TIMEOUT) { vm.hand.isNotEmpty() && vm.seats.isNotEmpty() }
      instrumentation.runOnMainSync {
        vm.chooseColorStyle(c.style)
        vm.chooseDarkMode(DarkMode.SYSTEM)
        vm.chooseTextSize(TextSize.NORMAL)
        vm.deal(HANDS)
      }
      compose.waitUntil(TIMEOUT) { vm.hand == HANDS[0] }
      shot(c, "0-arrange-start")
      // The front row placed and two cards picked.
      instrumentation.runOnMainSync {
        MINE[0].forEach(vm::toggle)
        vm.placeInto(0)
        MINE[1].take(2).forEach(vm::toggle)
      }
      compose.waitUntil(TIMEOUT) { vm.rowsEval != null }
      shot(c, "1-arrange")
      instrumentation.runOnMainSync {
        MINE[1].drop(2).forEach(vm::toggle)
        vm.placeInto(1)
      }
      compose.waitUntil(TIMEOUT) { vm.rowsEval?.valid == true }
      shot(c, "1b-arrange-full")
      instrumentation.runOnMainSync { vm.confirm() }
      compose.waitUntil(TIMEOUT) { vm.result != null }
      shot(c, "2-result")
      instrumentation.runOnMainSync { vm.showGameAnalysis() }
      compose.waitUntil(TIMEOUT) { vm.gameAnalysis != null }
      shot(c, "3-result-analysis")
      compose.onNodeWithTag("nav_ANALYZE").performClick()
      instrumentation.runOnMainSync { vm.setAnalyzeHand(HANDS[1].take(9).toSet()) }
      shot(c, "4-analyze-pick")
      instrumentation.runOnMainSync {
        vm.analyzeLastHand()
        vm.analyze()
      }
      compose.waitUntil(TIMEOUT) { vm.analysis != null && vm.anaPage == 1 }
      shot(c, "5-candidates")
      compose.onNodeWithTag("nav_BATTLE").performClick()
      shot(c, "6-battle")
      compose.onNodeWithTag("nav_SETTINGS").performClick()
      shot(c, "7-settings")
      compose.onNodeWithText(instrumentation.targetContext.getString(R.string.nav_rules)).performScrollTo().performClick()
      shot(c, "8-rules")
      scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
      compose.onNodeWithTag("nav_RECORDS").performClick()
      shot(c, "9-records")
    }
  }

  private fun shot(c: Config, name: String) {
    compose.waitForIdle()
    // After a change of size or rotation the display can take a moment to turn (the app first).
    // Now and then the emulator misses the rotation: it is asked again.
    var tries = 0
    compose.waitUntil(TIMEOUT) {
      compose.onRoot().fetchSemanticsNode().size.let { (it.width > it.height) == c.landscape }.also {
        if (!it && ++tries % 200 == 0) shell("settings put system user_rotation ${if (c.landscape) 1 else 0}")
      }
    }
    // The system bars settle after a change of size or rotation.
    Thread.sleep(600)
    // The screenshot is taken here and only kept once the display has turned, the app's background shows at both sides
    // (the turn shows a still of the old layout, dark in a light theme) and two in a row agree.
    var screen: Bitmap? = null
    compose.waitUntil(TIMEOUT) {
      val last = screen
      screen = instrumentation.uiAutomation.takeScreenshot()
      screen.let {
        it != null && (it.width > it.height) == c.landscape && light(it.getPixel(1, it.height / 2)) != c.dark &&
          light(it.getPixel(it.width - 2, it.height / 2)) != c.dark &&
          last != null && it.sameAs(last)
      }
    }
    val what = "${c.name} $name"
    val width = compose.onRoot().fetchSemanticsNode().size.width
    for (node in compose.onAllNodesWithTag(CARD_TAG, useUnmergedTree = true).fetchSemanticsNodes()) {
      // boundsInRoot is clipped by the ancestors and the screen; size is the card's own.
      val shown = node.boundsInRoot
      if (shown.height <= 0f) continue
      if (shown.width < node.size.width - 1) cutOff += "$what: a card shows ${shown.width} of ${node.size.width} px (screen $width)"
      else if (shown.height < node.size.height - 1) Log.i(TAG, "$what: a card shows ${shown.height} of ${node.size.height} px in height")
    }
    // The shell, not the app, can write to DIR: the PNG goes through dd.
    val (out, into) = instrumentation.uiAutomation.executeShellCommandRw("dd of=$DIR/${c.name}-$name.png")
    ParcelFileDescriptor.AutoCloseOutputStream(into).use { screen!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
    ParcelFileDescriptor.AutoCloseInputStream(out).use { it.readBytes() }
    Log.i(TAG, "$what: done")
  }

  private fun light(color: Int) = Color.red(color) + Color.green(color) + Color.blue(color) > 3 * 160

  companion object {
    private const val TAG = "ScreenshotTest"
    private const val DIR = "/data/local/tmp/shots"
    private const val CUTOUT = "com.android.internal.display.cutout.emulation.hole"
    private const val TIMEOUT = 90_000L

    // The phone the app is tried on: a Pixel 9 (2424 x 1080 at 420 dpi) with the font a step up (1.15), the size the
    // screenshots are judged at; then smaller and larger fonts, a small phone and a tablet.
    private val CONFIGS = listOf(
      Config("phone", "1080x2424", 420, 1.15f, "zh-TW", cutout = true),
      Config("phone-land", "1080x2424", 420, 1.15f, "zh-TW", cutout = true, landscape = true),
      Config("phone-font1.0", "1080x2424", 420, 1.0f, "zh-TW", cutout = true),
      Config("phone-font1.0-land", "1080x2424", 420, 1.0f, "zh-TW", cutout = true, landscape = true),
      Config("phone-font2.0", "1080x2424", 420, 2.0f, "zh-TW", cutout = true),
      Config("phone-font2.0-land", "1080x2424", 420, 2.0f, "zh-TW", cutout = true, landscape = true),
      Config("phone-en", "1080x2424", 420, 1.15f, "en", cutout = true),
      Config("phone-en-land", "1080x2424", 420, 1.15f, "en", cutout = true, landscape = true),
      Config("phone-dark", "1080x2424", 420, 1.15f, "zh-TW", cutout = true, dark = true),
      Config("small", "720x1600", 320, 1.0f, "zh-TW", cutout = false),
      Config("small-large-land", "720x1600", 360, 1.5f, "en", cutout = false, landscape = true),
      Config("tablet", "1600x2560", 320, 1.0f, "zh-TW", cutout = false),
      Config("tablet-land", "1600x2560", 320, 1.0f, "zh-TW", cutout = false, landscape = true),
      Config("tablet-font", "1600x2560", 320, 1.5f, "en", cutout = false, landscape = true),
    )

    // COMPARE=1 device-tests.sh: the screens to set beside version 1.1.1's. Named device__orientation__colors;
    // 1.1.1, landscape only and with one look, has the device part.
    private val COMPARE = InstrumentationRegistry.getArguments().getString("compare") == "1"
    private val COMPARE_CONFIGS = buildList {
      val devices = listOf("phone" to 1.15f, "phone-font1.0" to 1.0f, "phone-font1.3" to 1.3f, "phone-font2.0" to 2.0f)
      for ((name, font) in devices) for (land in listOf(true, false))
        add(Config("${name}__${if (land) "land" else "port"}__table-light", "1080x2424", 420, font, "zh-TW", cutout = true, landscape = land))
      for (land in listOf(true, false))
        add(Config("tablet__${if (land) "land" else "port"}__table-light", "1600x2560", 320, 1.0f, "zh-TW", cutout = false, landscape = land))
      for ((style, dark, land) in listOf(
        Triple(ColorStyle.MINIMAL, false, true), Triple(ColorStyle.WALLPAPER, false, true), Triple(ColorStyle.TABLE, true, true),
        Triple(ColorStyle.MINIMAL, true, true), Triple(ColorStyle.WALLPAPER, true, true),
        Triple(ColorStyle.MINIMAL, true, false), Triple(ColorStyle.TABLE, true, false), Triple(ColorStyle.WALLPAPER, false, false),
      )) {
        val look = mapOf(ColorStyle.TABLE to "table", ColorStyle.MINIMAL to "minimal", ColorStyle.WALLPAPER to "wallpaper").getValue(style) +
          if (dark) "-dark" else "-light"
        add(Config("phone__${if (land) "land" else "port"}__$look", "1080x2424", 420, 1.15f, "zh-TW", cutout = true, landscape = land, dark = dark, style = style))
      }
    }

    // ONLY=name,name device-tests.sh: only those configs.
    private val ONLY = InstrumentationRegistry.getArguments().getString("only").orEmpty().split(",").filter { it.isNotBlank() }
    private val STORE = InstrumentationRegistry.getArguments().getString("store") == "1"
    // Google Play wants 16:9 with a side of at least 1080 px.
    private val STORE_CONFIGS = listOf(
      Config("store-zh", "1080x1920", 400, 1.0f, "zh-TW", cutout = false, landscape = true),
      Config("store-en", "1080x1920", 400, 1.0f, "en", cutout = false, landscape = true),
    )

    // Seed "11" of the engine's deal, with the greedy player's rows for the player: under family, the networks'
    // rows give three scoops, two of them at seat 1 and two by seat 3.
    private val HANDS = listOf(
      listOf(9, 12, 13, 16, 22, 24, 27, 28, 34, 35, 37, 44, 47),
      listOf(3, 4, 7, 10, 11, 14, 18, 19, 25, 32, 38, 40, 46),
      listOf(0, 1, 8, 15, 17, 26, 29, 30, 39, 43, 48, 49, 50),
      listOf(2, 5, 6, 20, 21, 23, 31, 33, 36, 41, 42, 45, 51),
    )
    private val MINE = listOf(listOf(9, 13, 22), listOf(27, 34, 35, 37, 47), listOf(12, 16, 24, 28, 44))
  }
}
