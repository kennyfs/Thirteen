package io.github.kennyfs.thirteen.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kennyfs.thirteen.AppViewModel
import io.github.kennyfs.thirteen.Kind
import io.github.kennyfs.thirteen.R
import io.github.kennyfs.thirteen.Seat

@Composable
fun SettingsScreen(vm: AppViewModel) {
  val resolver = LocalContext.current.contentResolver
  val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) vm.importModel(resolver, uri)
  }
  // Seat choices are read from the settings; bump to redraw after a change.
  var version by remember { mutableIntStateOf(0) }
  Column(Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(stringResource(R.string.opponents), style = MaterialTheme.typography.titleSmall)
    key(version) {
      val kinds = stringArrayResource(R.array.kinds)
      for (i in 0 until 3) {
        val seat = vm.settings.seat(i)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(stringResource(R.string.seat_n, i + 1), Modifier.width(textRoom(64)))
          Picker("", Kind.entries.map { it.name to kinds[it.ordinal] }, seat.kind.name) {
            vm.setSeat(i, seat.copy(kind = Kind.valueOf(it))); version++
          }
          if (seat.kind == Kind.NET)
            Picker("", vm.models.map { it.path to modelLabel(it) }, vm.settings.model(seat.model).path) {
              vm.setSeat(i, seat.copy(model = it)); version++
            }
        }
      }
      var samples by remember { mutableStateOf(vm.settings.mcSamples) }
      Picker(stringResource(R.string.mc_samples), listOf(100, 200, 500, 1000).map { it.toString() to it.toString() }, samples.toString()) {
        samples = it.toInt(); vm.settings.mcSamples = samples
      }
    }
    HorizontalDivider()
    Text(stringResource(R.string.networks), style = MaterialTheme.typography.titleSmall)
    for (m in vm.models) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(modelLabel(m), Modifier.widthIn(max = 360.dp))
        if (!m.builtIn) TextButton(onClick = { vm.deleteModel(m); version++ }) { Text(stringResource(R.string.delete)) }
      }
    }
    OutlinedButton(onClick = { load.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.load_weights)) }
  }
}
