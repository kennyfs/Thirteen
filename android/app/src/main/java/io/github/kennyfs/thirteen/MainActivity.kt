package io.github.kennyfs.thirteen

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kennyfs.thirteen.ui.AnalyzeScreen
import io.github.kennyfs.thirteen.ui.PlayScreen
import io.github.kennyfs.thirteen.ui.RecordsScreen
import io.github.kennyfs.thirteen.ui.RulesScreen
import io.github.kennyfs.thirteen.ui.SettingsScreen
import io.github.kennyfs.thirteen.ui.BattleScreen
import io.github.kennyfs.thirteen.battle.BattleViewModel
import androidx.compose.material.icons.filled.Face

enum class Screen(val label: Int, val icon: ImageVector) {
  PLAY(R.string.nav_play, Icons.Filled.PlayArrow),
  BATTLE(R.string.nav_battle, Icons.Filled.Face),
  ANALYZE(R.string.nav_analyze, Icons.Filled.Search),
  RECORDS(R.string.nav_records, Icons.AutoMirrored.Filled.List),
  RULES(R.string.nav_rules, Icons.Filled.Info),
  SETTINGS(R.string.nav_settings, Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val dark = isSystemInDarkTheme()
      // Material You: the system's wallpaper colors on Android 12+.
      val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
        dark -> darkColorScheme()
        else -> lightColorScheme()
      }
      MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize()) {
          val vm: AppViewModel = viewModel()
          val battle: BattleViewModel = viewModel()
          var screen by rememberSaveable { mutableStateOf(Screen.PLAY) }
          // Back: a screen's own pages first, each screen with a BackHandler of its own while it is past its first
          // page (composed after this one, so it comes first); then the screens visited before, newest first; then
          // out of the app.
          var visited by rememberSaveable { mutableStateOf(listOf<Screen>()) }
          fun go(to: Screen) {
            if (to == screen) return
            visited = (visited - to + screen).takeLast(Screen.entries.size)
            screen = to
          }
          BackHandler(visited.isNotEmpty()) {
            screen = visited.last()
            visited = visited.dropLast(1)
          }
          Row(Modifier.safeDrawingPadding()) {
            NavRail(screen, ::go)
            Box(Modifier.fillMaxSize()) {
              when (screen) {
                Screen.PLAY -> PlayScreen(vm)
                Screen.BATTLE -> BattleScreen(battle, vm)
                Screen.ANALYZE -> AnalyzeScreen(vm)
                Screen.RECORDS -> {
                  LaunchedEffect(Unit) { vm.refreshRecords() }
                  RecordsScreen(vm, onAnalyze = { go(Screen.ANALYZE) })
                }
                Screen.RULES -> RulesScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm)
              }
            }
          }
          vm.error?.let { message ->
            AlertDialog(
              onDismissRequest = { vm.error = null },
              confirmButton = { TextButton(onClick = { vm.error = null }) { Text(stringResource(R.string.ok)) } },
              text = { Text(message) },
            )
          }
          battle.error?.let { message ->
            AlertDialog(
              onDismissRequest = { battle.error = null },
              confirmButton = { TextButton(onClick = { battle.error = null }) { Text(stringResource(R.string.ok)) } },
              text = { Text(message) },
            )
          }
        }
      }
    }
  }
}

/**
 * The screens down the left side. Every label is shown when all fit the height; when not (a short screen or a large
 * font), only the current screen's, and the rail scrolls if even that is too tall.
 */
@Composable
private fun NavRail(current: Screen, onPick: (Screen) -> Unit) {
  BoxWithConstraints(Modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer)) {
    // An item: the 28 dp icon, 12 dp of padding and a label line (measured, as fonts scale nonlinearly).
    val labelHeight = with(LocalDensity.current) { rememberTextMeasurer().measure("Ag", MaterialTheme.typography.labelMedium).size.height.toDp() }
    val labels = (40.dp + labelHeight) * Screen.entries.size + 8.dp <= maxHeight
    Column(
      Modifier.fillMaxHeight().widthIn(min = 64.dp).verticalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      for (s in Screen.entries) {
        val selected = s == current
        val colors = MaterialTheme.colorScheme
        Column(
          Modifier.widthIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).clickable { onPick(s) }.testTag("nav_${s.name}")
            .padding(vertical = 6.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(
            Modifier.size(48.dp, 28.dp).background(if (selected) colors.secondaryContainer else Color.Transparent, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(s.icon, contentDescription = stringResource(s.label), tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant)
          }
          if (labels || selected)
            Text(
              stringResource(s.label), style = MaterialTheme.typography.labelMedium, maxLines = 1,
              color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            )
        }
      }
    }
  }
}
