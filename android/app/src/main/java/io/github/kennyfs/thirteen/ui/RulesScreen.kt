package io.github.kennyfs.thirteen.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.kennyfs.thirteen.AppViewModel
import io.github.kennyfs.thirteen.Kind
import io.github.kennyfs.thirteen.R
import io.github.kennyfs.thirteen.Rules

/**
 * The scoring rules: a preset, then each value. The two that change the play (strict rows and the wheel) are kept
 * apart from the ones that only change the points.
 */
@Composable
fun RulesPage(vm: AppViewModel, onBack: () -> Unit) {
  val spec = vm.ruleSpec
  val values = vm.ruleValues
  val colors = MaterialTheme.colorScheme
  val names = stringArrayResource(R.array.preset_names)
  val notes = stringArrayResource(R.array.preset_notes)
  // Leaving the page (not turning the screen) puts the rules into the practice game at once.
  val activity = androidx.activity.compose.LocalActivity.current
  androidx.compose.runtime.DisposableEffect(Unit) { onDispose { if (activity?.isChangingConfigurations != true) vm.rulesClosed() } }
  SettingsSubpage(stringResource(R.string.nav_rules), onBack) {
    Text(
      stringResource(R.string.rules_next_hand), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
    )
    Column(Modifier.padding(horizontal = 12.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      for (p in Rules.OFFERED + listOfNotNull(spec.preset.takeIf { it !in Rules.OFFERED })) {
        val i = Rules.PRESETS.indexOf(p)
        val on = spec.preset == p
        Card(
          Modifier.fillMaxWidth().selectable(on, role = Role.RadioButton) { vm.choosePreset(p) },
          colors = CardDefaults.cardColors(containerColor = if (on) colors.secondaryContainer else colors.surfaceContainerLow),
          border = if (on) BorderStroke(2.dp, colors.primary) else null,
        ) {
          Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = on, onClick = null, Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
              Text(
                names[i] + if (on && spec.overrides.isNotEmpty()) stringResource(R.string.rules_changed) else "",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(notes[i], style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
          }
        }
      }
    }
    // Always there (off when nothing is changed): nothing on the page comes and goes, so nothing moves.
    OutlinedButton(
      onClick = { vm.choosePreset(spec.preset) }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp),
      enabled = spec.overrides.isNotEmpty(),
    ) {
      Text(stringResource(R.string.restore_preset))
    }
    // Networks in a seat or the analysis that would play these rules as family.
    val legacy = (vm.seatChoices.filter { it.kind == Kind.NET }.map { it.model } + vm.anaModel)
      .map { vm.settings.model(it) }.distinct().filter { it.path in vm.legacyModels }
    // Shown while such a network is chosen, whatever the rules (they only set its color), so it does not come and go.
    val mismatch = vm.legacyMismatch(values)
    for (m in legacy) Text(
      stringResource(R.string.legacy_warning, modelLabel(m)), Modifier.padding(horizontal = 16.dp),
      color = if (mismatch) BAD else colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
    )
    if (values.isEmpty()) return@SettingsSubpage

    SectionHeader(stringResource(R.string.rules_play))
    Text(stringResource(R.string.rules_play_note), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    SwitchRow(stringResource(R.string.strict_rows), values["strict"] == 1.0) { vm.setRule("strict", if (it) 1.0 else 0.0) }
    Text(stringResource(R.string.wheel), Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyLarge)
    val wheel = values["wheel"]?.toInt() ?: 0
    Column(Modifier.selectableGroup()) {
      for ((i, label) in stringArrayResource(R.array.wheel_options).withIndex())
        Row(
          Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(wheel == i, role = Role.RadioButton) { vm.setRule("wheel", i.toDouble()) }
            .padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(selected = wheel == i, onClick = null, Modifier.padding(end = 12.dp))
          Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }

    SectionHeader(stringResource(R.string.rules_scoring))
    for ((i, label) in stringArrayResource(R.array.point_labels).withIndex()) NumberRow(label, Rules.POINTS[i], values, vm::setRule)
    SectionHeader(stringResource(R.string.rules_royalties))
    for ((i, label) in stringArrayResource(R.array.royalty_labels).withIndex()) NumberRow(label, Rules.ROYALTIES[i], values, vm::setRule)
    SwitchRow(stringResource(R.string.on_win), values["on_win"] == 1.0) { vm.setRule("on_win", if (it) 1.0 else 0.0) }
  }
}

@Composable
private fun SwitchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
  Row(
    Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(on, role = Role.Switch, onValueChange = onChange).padding(horizontal = 16.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    Switch(checked = on, onCheckedChange = null)
  }
}

/**
 * A label and its value with − and + buttons, which stop at the value's range: royalties keep their order (a middle
 * row's quads pay no less than its full house), so the range follows the neighbours' values and is shown under the label.
 */
@Composable
private fun NumberRow(label: String, name: String, values: Map<String, Double>, onSet: (String, Double) -> Unit) {
  val value = values[name] ?: 0.0
  val range = Rules.range(name, values)
  Row(
    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 12.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Column(Modifier.weight(1f)) {
      Text(label, style = MaterialTheme.typography.bodyLarge)
      Text(
        stringResource(R.string.rule_range, Rules.fmt(range.start), Rules.fmt(range.endInclusive)),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    NumberStepper(value, { onSet(name, it) }, min = range.start, max = range.endInclusive, text = Rules.fmt(value))
  }
}
