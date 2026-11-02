package io.github.kennyfs.thirteen.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kennyfs.thirteen.AppViewModel
import io.github.kennyfs.thirteen.Kind
import io.github.kennyfs.thirteen.R
import io.github.kennyfs.thirteen.Rules

/**
 * The scoring rules: a preset, then each value. The two that change the play (strict rows and the wheel) are kept
 * apart from the ones that only change the points.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(vm: AppViewModel) {
  val spec = vm.ruleSpec
  val values = vm.ruleValues
  val colors = MaterialTheme.colorScheme
  Column(Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      for (p in Rules.PRESETS)
        FilterChip(selected = spec.preset == p && spec.overrides.isEmpty(), onClick = { vm.choosePreset(p) }, label = { Text(p) })
      if (spec.overrides.isNotEmpty()) {
        Text(
          stringResource(R.string.custom_from, spec.preset), Modifier.align(Alignment.CenterVertically),
          color = colors.tertiary, style = MaterialTheme.typography.labelLarge,
        )
        TextButton(onClick = { vm.choosePreset(spec.preset) }) { Text(stringResource(R.string.restore_preset)) }
      }
    }
    Text(
      stringArrayResource(R.array.preset_notes)[Rules.PRESETS.indexOf(spec.preset)],
      style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
    )
    Text(stringResource(R.string.rules_next_hand), style = MaterialTheme.typography.bodySmall)
    // Networks in a seat or the analysis that would play these rules as family.
    val legacy = (List(3) { vm.settings.seat(it) }.filter { it.kind == Kind.NET }.map { it.model } + vm.anaModel)
      .map { vm.settings.model(it) }.distinct().filter { it.path in vm.legacyModels }
    if (vm.legacyMismatch(values))
      for (m in legacy) Text(stringResource(R.string.legacy_warning, modelLabel(m)), color = BAD, style = MaterialTheme.typography.bodySmall)
    vm.rulesError?.let { Text(it, color = BAD, style = MaterialTheme.typography.bodySmall) }
    if (values.isEmpty()) return@Column

    // Side by side when the width allows, else one under another.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Column(
        Modifier.width(textRoom(260)).border(2.dp, colors.tertiary, RoundedCornerShape(8.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(stringResource(R.string.rules_play), style = MaterialTheme.typography.titleSmall, color = colors.tertiary)
        Text(stringResource(R.string.rules_play_note), style = MaterialTheme.typography.bodySmall)
        SwitchLine(stringResource(R.string.strict_rows), values["strict"] == 1.0) { vm.setRule("strict", if (it) 1.0 else 0.0) }
        Text(stringResource(R.string.wheel), style = MaterialTheme.typography.bodyMedium)
        val wheel = values["wheel"]?.toInt() ?: 0
        for ((i, label) in stringArrayResource(R.array.wheel_options).withIndex())
          Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = wheel == i, onClick = { vm.setRule("wheel", i.toDouble()) })
            Text(label, style = MaterialTheme.typography.bodyMedium)
          }
      }
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.rules_scoring), style = MaterialTheme.typography.titleSmall)
        for ((i, label) in stringArrayResource(R.array.point_labels).withIndex())
          NumberLine(label, Rules.POINTS[i], values, vm::setRule)
      }
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.rules_royalties), style = MaterialTheme.typography.titleSmall)
        for ((i, label) in stringArrayResource(R.array.royalty_labels).withIndex())
          NumberLine(label, Rules.ROYALTIES[i], values, vm::setRule)
        Box(Modifier.width(textRoom(130) + 56.dp)) {
          SwitchLine(stringResource(R.string.on_win), values["on_win"] == 1.0) { vm.setRule("on_win", if (it) 1.0 else 0.0) }
        }
      }
    }
  }
}

@Composable
private fun SwitchLine(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Switch(checked = on, onCheckedChange = onChange)
    Text(label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium)
  }
}

/** A label and a small number field; a value is set once the text is a number >= 0. */
@Composable
private fun NumberLine(label: String, name: String, values: Map<String, Double>, onSet: (String, Double) -> Unit) {
  val value = values[name] ?: 0.0
  var text by remember(value) { mutableStateOf(Rules.fmt(value)) }
  val colors = MaterialTheme.colorScheme
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.width(textRoom(130)), style = MaterialTheme.typography.bodyMedium)
    BasicTextField(
      text,
      onValueChange = { t ->
        text = t
        t.toDoubleOrNull()?.takeIf { it >= 0 && it != value }?.let { onSet(name, it) }
      },
      Modifier.width(56.dp).border(1.dp, if (text.toDoubleOrNull() == null) BAD else colors.outline, RoundedCornerShape(4.dp))
        .padding(horizontal = 6.dp, vertical = 4.dp),
      singleLine = true,
      textStyle = TextStyle(fontSize = 15.sp, color = colors.onSurface, textAlign = TextAlign.End),
      cursorBrush = SolidColor(colors.primary),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
  }
}
