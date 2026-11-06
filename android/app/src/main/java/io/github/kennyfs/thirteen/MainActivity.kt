package io.github.kennyfs.thirteen

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import android.app.Activity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.kennyfs.thirteen.ui.textRoom
import io.github.kennyfs.thirteen.ui.cardSizes
import io.github.kennyfs.thirteen.ui.LocalCardSizes
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import io.github.kennyfs.thirteen.battle.BattleViewModel
import io.github.kennyfs.thirteen.ui.BattleRecordPage
import io.github.kennyfs.thirteen.ui.AnalyzeScreen
import io.github.kennyfs.thirteen.ui.CandidatesPage
import io.github.kennyfs.thirteen.ui.MyHandAnalysis
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.History
import io.github.kennyfs.thirteen.ui.BattleScreen
import io.github.kennyfs.thirteen.ui.NetworksPage
import io.github.kennyfs.thirteen.ui.OpponentsPage
import io.github.kennyfs.thirteen.ui.PlayScreen
import io.github.kennyfs.thirteen.ui.RecordsPage
import io.github.kennyfs.thirteen.ui.RulesPage
import io.github.kennyfs.thirteen.ui.SettingsPage
import io.github.kennyfs.thirteen.ui.SettingsScreen
import io.github.kennyfs.thirteen.ui.theme.ThirteenTheme
import io.github.kennyfs.thirteen.ui.theme.isDark

/** The top-level screens, in the navigation bar or rail. */
enum class Screen(val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
  PLAY(R.string.nav_play, Icons.Outlined.Style, Icons.Filled.Style),
  BATTLE(R.string.nav_battle, Icons.Outlined.Groups, Icons.Filled.Groups),
  ANALYZE(R.string.nav_analyze, Icons.Outlined.Insights, Icons.Filled.Insights),
  RECORDS(R.string.nav_records, Icons.Outlined.History, Icons.Filled.History),
  SETTINGS(R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}

/** A destination: a top-level screen or a page opened from one. */
enum class Dest { PLAY, BATTLE, ANALYZE, RECORDS, SETTINGS, RULES, OPPONENTS, NETWORKS, MY_ANALYSIS, CANDIDATES, BATTLE_RECORD }

private fun root(s: Screen) = Dest.valueOf(s.name)

private const val SLIDE_MS = 160

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val vm: AppViewModel = viewModel()
      val battle: BattleViewModel = viewModel()
      val dark = isDark(vm.darkMode, isSystemInDarkTheme())
      // System bar icons follow the app's own light or dark, which can differ from the system's.
      DisposableEffect(dark) {
        val style = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
      }
      ThirteenTheme(vm.colorStyle, vm.darkMode, vm.textSize) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
          App(vm, battle)
          vm.error?.let { message -> ErrorDialog(message) { vm.error = null } }
          battle.error?.let { message -> ErrorDialog(message) { battle.error = null } }
        }
      }
    }
  }
}

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    text = { Text(message, style = MaterialTheme.typography.bodyLarge) },
  )
}

/**
 * The screens with the navigation bar (a tall window) or rail (a wide one). Each screen keeps its own stack of pages,
 * so switching screens and back returns to the page left. Back: the screen's own pages first, then to Practice, then
 * out of the app.
 */
@Composable
private fun App(vm: AppViewModel, battle: BattleViewModel) {
  var tab by rememberSaveable { mutableStateOf(Screen.PLAY) }
  // Each screen's pages, saved as names.
  var stacks by rememberSaveable { mutableStateOf(Screen.entries.associate { it.name to listOf(root(it).name) }) }
  val pushed = stacks.getValue(tab.name).map { Dest.valueOf(it) }
  // The pages the view models open: the analysis of the finished hand, the candidates of the analyzed one. They sit
  // in the stack too, so that back (and its preview) leaves them as any other page.
  val opened = when (tab) {
    Screen.PLAY -> listOfNotNull(Dest.MY_ANALYSIS.takeIf { vm.result != null && vm.resultTab == 1 })
    Screen.ANALYZE -> listOfNotNull(Dest.CANDIDATES.takeIf { vm.analysis != null && vm.anaPage == 1 })
    Screen.RECORDS -> listOfNotNull(Dest.BATTLE_RECORD.takeIf { vm.openedBattle != null })
    else -> emptyList()
  }
  val stack = pushed.take(1) + opened + pushed.drop(1)
  fun push(d: Dest) {
    stacks = stacks + (tab.name to (stacks.getValue(tab.name) + d.name))
  }
  fun pop() {
    when {
      pushed.size > 1 -> stacks = stacks + (tab.name to stacks.getValue(tab.name).dropLast(1))
      Dest.MY_ANALYSIS in opened -> vm.resultTab = 0
      Dest.CANDIDATES in opened -> vm.anaPage = 0
      Dest.BATTLE_RECORD in opened -> vm.openedBattle = null
    }
  }
  fun open(page: SettingsPage) = push(Dest.valueOf(page.name))
  // A hand to rearrange or to analyze (from a record or the result): to that screen's first page.
  var handled by rememberSaveable { mutableStateOf(vm.tabRequest.first) }
  LaunchedEffect(vm.tabRequest) {
    val (n, name) = vm.tabRequest
    if (n != handled) {
      handled = n
      val target = Screen.valueOf(name)
      tab = target
      stacks = stacks + (target.name to listOf(root(target).name))
    }
  }

  // A rail at the side whenever the window is wider than tall, a landscape phone too: there the height is what the
  // cards need.
  val window = LocalWindowInfo.current.containerSize
  val type = if (window.width > window.height) NavigationSuiteType.NavigationRail
  else NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
  // Landscape is full screen: the status and navigation bars come back with a swipe from the edge.
  val view = LocalView.current
  val landscape = window.width > window.height
  SideEffect {
    val activityWindow = (view.context as? Activity)?.window ?: return@SideEffect
    val controller = WindowCompat.getInsetsController(activityWindow, view)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (landscape) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
  }
  // In a battle the other screens are out of reach (no bar or rail), until the player leaves it.
  LaunchedEffect(battle.inRoom) { if (battle.inRoom) tab = Screen.BATTLE }
  BackHandler(tab != Screen.PLAY && stack.size == 1 && !battle.inRoom) { tab = Screen.PLAY }
  NavigationSuiteScaffold(
    navigationSuiteItems = {
      for (s in Screen.entries) {
        val selected = s == tab
        item(
          selected = selected,
          onClick = {
            // Again on the current screen: back to its first page.
            if (selected) stacks = stacks + (s.name to listOf(root(s).name)) else tab = s
          },
          // Practice's cards are held at the bottom, fanned out from there.
          icon = { Icon(if (selected) s.selectedIcon else s.icon, null, if (s == Screen.PLAY) Modifier.graphicsLayer(scaleY = -1f) else Modifier) },
          label = { Text(stringResource(s.label), maxLines = 1) },
          modifier = Modifier.testTag("nav_${s.name}"),
          alwaysShowLabel = true,
        )
      }
    },
    layoutType = if (battle.immersive) NavigationSuiteType.None else type,
  ) {
    // The bar or rail looks after its own side; the content keeps clear of the rest.
    val sides = if (battle.immersive) WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
    else if (type == NavigationSuiteType.NavigationBar) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
    else WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(sides))) {
      // The two sizes of card, for the room the pages have.
      val sizes = cardSizes(maxWidth, maxHeight, textRoom(14))
      LaunchedEffect(sizes.play, sizes.other, sizes.battle) { Log.i("CardSizes", "$maxWidth x $maxHeight: play ${sizes.play}, battle ${sizes.battle}, other ${sizes.other}") }
      CompositionLocalProvider(LocalCardSizes provides sizes) {
      // A new screen from the bar or rail shows at once; a page opened in a screen slides in, briefly.
      key(tab) {
        NavDisplay(
          backStack = stack,
          onBack = { pop() },
          transitionSpec = { slideInHorizontally(tween(SLIDE_MS)) { it } togetherWith slideOutHorizontally(tween(SLIDE_MS)) { -it / 4 } },
          popTransitionSpec = { slideInHorizontally(tween(SLIDE_MS)) { -it / 4 } togetherWith slideOutHorizontally(tween(SLIDE_MS)) { it } },
          predictivePopTransitionSpec = { slideInHorizontally(tween(SLIDE_MS)) { -it / 4 } togetherWith slideOutHorizontally(tween(SLIDE_MS)) { it } },
          entryProvider = { d ->
            NavEntry(d, contentKey = "${tab.name}/${d.name}") {
              // Each page on the background: the one under it shows while back is dragged.
              Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                when (d) {
                  Dest.PLAY -> PlayScreen(vm) { push(Dest.RULES) }
                  Dest.BATTLE -> BattleScreen(battle, vm)
                  Dest.ANALYZE -> AnalyzeScreen(vm) { push(Dest.RULES) }
                  Dest.RECORDS -> RecordsPage(vm)
                  Dest.SETTINGS -> SettingsScreen(vm, ::open)
                  Dest.RULES -> RulesPage(vm, ::pop)
                  Dest.OPPONENTS -> OpponentsPage(vm, ::pop)
                  Dest.NETWORKS -> NetworksPage(vm, ::pop)
                  Dest.MY_ANALYSIS -> MyHandAnalysis(vm)
                  Dest.CANDIDATES -> CandidatesPage(vm)
                  Dest.BATTLE_RECORD -> vm.openedBattle?.let { b -> BattleRecordPage(vm, b) { vm.openedBattle = null } }
                }
              }
            }
          },
        )
      }
      }
    }
  }
}
