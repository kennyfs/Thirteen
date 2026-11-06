package io.github.kennyfs.thirteen.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kennyfs.thirteen.AppViewModel
import io.github.kennyfs.thirteen.ColorStyle
import io.github.kennyfs.thirteen.DarkMode
import io.github.kennyfs.thirteen.Kind
import io.github.kennyfs.thirteen.R
import io.github.kennyfs.thirteen.TextSize

/** The settings pages that open from the list. */
enum class SettingsPage { RULES, OPPONENTS, NETWORKS }

/** A page of the settings: a title bar with Back and a column that scrolls, kept to a readable width. */
@Composable
fun SettingsSubpage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
  Column(Modifier.fillMaxSize()) {
    TitleBar(title, onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
      Column(Modifier.readable()) { content() }
    }
  }
}

@Composable
fun SettingsScreen(vm: AppViewModel, open: (SettingsPage) -> Unit) {
  val context = LocalContext.current
  Column(Modifier.fillMaxSize()) {
    TitleBar(stringResource(R.string.nav_settings))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
      Column(Modifier.readable()) {
        SectionHeader(stringResource(R.string.settings_look))
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(stringResource(R.string.text_size), style = MaterialTheme.typography.bodyLarge)
          val sizes = stringArrayResource(R.array.text_sizes)
          ToggleGroup(TextSize.entries.map { it to sizes[it.ordinal] }, vm.textSize, vm::chooseTextSize, Modifier.fillMaxWidth())
        }
        val styles = stringArrayResource(R.array.color_styles)
        val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        SelectRow(
          stringResource(R.string.color_style), ColorStyle.entries.filter { dynamic || it == ColorStyle.TABLE || it == ColorStyle.MINIMAL }.map { it to styles[it.ordinal] },
          vm.colorStyle, vm::chooseColorStyle,
          icons = mapOf(
            ColorStyle.TABLE to Icons.Outlined.Palette,
            ColorStyle.MINIMAL to Icons.Outlined.Contrast,
            ColorStyle.WALLPAPER to Icons.Outlined.Wallpaper,
          ),
        )
        val darks = stringArrayResource(R.array.dark_modes)
        SelectRow(
          stringResource(R.string.dark_mode), DarkMode.entries.map { it to darks[it.ordinal] },
          vm.darkMode, vm::chooseDarkMode,
          icons = mapOf(
            DarkMode.SYSTEM to Icons.Outlined.BrightnessAuto,
            DarkMode.LIGHT to Icons.Outlined.LightMode,
            DarkMode.DARK to Icons.Outlined.DarkMode,
          ),
        )

        SectionHeader(stringResource(R.string.settings_game))
        SettingRow(stringResource(R.string.nav_rules), vm.rulesName(vm.ruleValues).takeIf { vm.ruleValues.isNotEmpty() }) { open(SettingsPage.RULES) }
        val kinds = stringArrayResource(R.array.kinds)
        SettingRow(stringResource(R.string.opponents_title), opponentsSummary(vm, kinds)) { open(SettingsPage.OPPONENTS) }
        SettingRow(stringResource(R.string.networks), stringResource(R.string.networks_summary, vm.models.size)) { open(SettingsPage.NETWORKS) }

        SectionHeader(stringResource(R.string.help))
        val tour = LocalTour.current
        val openTutorial = LocalOpenTutorial.current
        SettingRow(stringResource(R.string.tut_title), stringResource(R.string.tut_note)) { openTutorial() }
        SettingRow(stringResource(R.string.tour_title), stringResource(R.string.tour_note)) { tour.start() }

        SectionHeader(stringResource(R.string.settings_about))
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
        SettingRow(stringResource(R.string.app_name) + " " + version, stringResource(R.string.about_source)) {
          context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kennyfs/Thirteen")))
        }
        SettingRow(stringResource(R.string.about_web), stringResource(R.string.about_web_note, WEB_HOST)) {
          context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://$WEB_HOST/")))
        }
        SettingRow(stringResource(R.string.privacy_policy)) {
          context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kennyfs/Thirteen/blob/main/docs/privacy.md")))
        }
      }
    }
  }
}

@Composable
private fun opponentsSummary(vm: AppViewModel, kinds: Array<String>): String {
  val seats = vm.seatChoices
  val first = seats.first()
  return if (seats.all { it == first }) stringResource(R.string.opponents_all, if (first.kind == Kind.NET) vm.settings.model(first.model).name else kinds[first.kind.ordinal])
  else seats.joinToString("、") { if (it.kind == Kind.NET) vm.settings.model(it.model).name else kinds[it.kind.ordinal] }
}

/** The three computer players: each a network, Monte Carlo or indep. They take effect from the next hand. */
@Composable
fun OpponentsPage(vm: AppViewModel, onBack: () -> Unit) {
  SettingsSubpage(stringResource(R.string.opponents_title), onBack) {
    Text(
      stringResource(R.string.next_hand_note), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val kinds = stringArrayResource(R.array.kinds_short)
    for (i in 0 until 3) {
      val seat = vm.seatChoices[i]
      Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(stringResource(R.string.seat_pos, i + 1), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
        ToggleGroup(Kind.entries.map { it to kinds[it.ordinal] }, seat.kind, { vm.setSeat(i, seat.copy(kind = it)) }, Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        // Off, not gone, for a seat of another kind: the page keeps its height.
        SelectRow(
          stringResource(R.string.network), vm.models.map { it.path to modelLabel(it) }, vm.settings.model(seat.model).path,
          { vm.setSeat(i, seat.copy(model = it)) }, enabled = seat.kind == Kind.NET,
        )
      }
    }
    SelectRow(
      stringResource(R.string.mc_samples), listOf(100, 200, 500, 1000).map { it to it.toString() }, vm.mcSamples, vm::chooseMcSamples,
      enabled = vm.seatChoices.any { it.kind == Kind.MC },
    )
    Text(
      stringResource(R.string.opponents_info), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** The networks: the built-in one and those loaded from .onnx files, which can be deleted. */
@Composable
fun NetworksPage(vm: AppViewModel, onBack: () -> Unit) {
  val resolver = LocalContext.current.contentResolver
  val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importModel(resolver, uri) }
  var deleting by rememberSaveable { mutableStateOf<String?>(null) }
  SettingsSubpage(stringResource(R.string.networks), onBack) {
    for (m in vm.models) {
      Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(m.name, style = MaterialTheme.typography.bodyLarge)
          if (m.builtIn) Text(stringResource(R.string.built_in_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          else if (m.path in vm.legacyModels) Text(stringResource(R.string.legacy_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!m.builtIn) IconButton(onClick = { deleting = m.path }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
      }
      HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
    OutlinedButton(onClick = { load.launch(arrayOf("*/*")) }, Modifier.padding(16.dp).heightIn(min = 52.dp)) {
      Icon(Icons.Outlined.FileOpen, null, Modifier.padding(end = 8.dp))
      Text(stringResource(R.string.load_weights))
    }
    Text(
      stringResource(R.string.networks_info), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
  deleting?.let { path ->
    val m = vm.models.firstOrNull { it.path == path }
    if (m == null) deleting = null
    else AlertDialog(
      onDismissRequest = { deleting = null },
      confirmButton = { TextButton(onClick = { vm.deleteModel(m); deleting = null }) { Text(stringResource(R.string.delete)) } },
      dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
      text = { Text(stringResource(R.string.delete_network_confirm, m.name), style = MaterialTheme.typography.bodyLarge) },
    )
  }
}

/** Where the web version is. */
private const val WEB_HOST = "thirteen.1li.tw"
