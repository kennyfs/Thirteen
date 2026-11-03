package io.github.kennyfs.thirteen.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import org.json.JSONObject
import io.github.kennyfs.thirteen.Cards
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.kennyfs.thirteen.AppViewModel
import io.github.kennyfs.thirteen.R
import java.io.File
import java.time.LocalDate

/**
 * The records through the system's share sheet (Quick Share, a drive, a chat app...): a copy in the cache, which
 * res/xml/file_paths.xml makes the FileProvider's.
 */
private fun shareRecords(context: Context, vm: AppViewModel) {
  val file = vm.records.copyTo(File(context.cacheDir, "shared"), "thirteen-records-${LocalDate.now()}.jsonl")
  val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
  val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  context.startActivity(Intent.createChooser(send, null))
}

/** One hand in a list: the record, a star to save it or drop it, and a tap for what to do with it. */
@Composable
private fun RecordEntry(vm: AppViewModel, r: JSONObject, onClick: () -> Unit) {
  val saved = r.optString("id") in vm.favoriteIds
  RecordLine(
    r, Modifier.clickable(onClick = onClick),
    trailing = {
      IconButton(onClick = { vm.toggleFavorite(r) }) {
        Icon(
          if (saved) Icons.Filled.Star else Icons.Outlined.StarBorder, stringResource(if (saved) R.string.favorite_remove else R.string.favorite_add),
          tint = if (saved) Color(0xFFE0A100) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
  )
}

/** What to do with a hand of the lists: analyze it, or play it again to arrange it another way. */
@Composable
private fun HandActions(vm: AppViewModel, r: JSONObject, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    title = { RecordLine(r) },
    text = {
      Column {
        TextButton(onClick = { vm.analyzeRecordOnScreen(r); onDismiss() }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
          Text(stringResource(R.string.analyze_this), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        }
        TextButton(
          onClick = { vm.replay(Cards.parse(r.getString("hand"))); onDismiss() }, Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("hand_replay"),
        ) { Text(stringResource(R.string.replay_hand), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth()) }
      }
    },
  )
}

/** The score chart: the total over the records of one set of rules, which a menu chooses when there are several. */
@Composable
private fun ChartSection(vm: AppViewModel, recs: List<JSONObject>) {
  // Oldest first; played again and empty ones out. Rules without the dealer's cost, as the totals are kept.
  val groups = remember(recs) {
    recs.asReversed().filter { it.optString("label") != "replay" && it.has("result") }
      .groupBy { it.optString("rules").ifEmpty { "family" }.split(",").filterNot { p -> p.startsWith("c=") }.joinToString(",") }
  }
  if (groups.isEmpty()) {
    Text(stringResource(R.string.chart_empty), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    return
  }
  val current = vm.gameRules.split(",").filterNot { it.startsWith("c=") }.joinToString(",")
  var chosen by rememberSaveable { mutableStateOf("") }
  val key = chosen.takeIf { it in groups } ?: current.takeIf { it in groups } ?: groups.maxBy { it.value.size }.key
  val list = groups.getValue(key)
  val totals = remember(list) { list.runningFold(0.0) { sum, r -> sum + r.getDouble("result") }.drop(1) }
  val names = groups.keys.map { it to vm.rulesLabel(it) }
  Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (groups.size > 1) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.chart_rules), style = MaterialTheme.typography.bodyLarge)
        MenuChip(names.first { it.first == key }.second, names.map { (k, n) -> k to n }, { chosen = it })
      }
    }
    val last = totals.last()
    Text(
      stringResource(R.string.chart_summary, list.size, signed(last), signed(last / list.size)),
      style = MaterialTheme.typography.titleMedium,
    )
    ScoreChart(
      totals, localTime(list.first().optString("timestamp")).take(10), localTime(list.last().optString("timestamp")).take(10),
      stringResource(R.string.chart_desc, list.size, signed(last)),
    )
  }
}

/** The chart, the saved hands, all the records, and sharing them with the developer. */
@Composable
fun RecordsPage(vm: AppViewModel, onBack: () -> Unit) {
  val context = LocalContext.current
  val resolver = context.contentResolver
  val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-ndjson")) { uri ->
    if (uri != null) vm.exportRecords(resolver, uri)
  }
  var confirmClear by rememberSaveable { mutableStateOf(false) }
  var shown by rememberSaveable { mutableStateOf(20) }
  var acting by remember { mutableStateOf<JSONObject?>(null) }
  LaunchedEffect(Unit) { vm.refreshRecords() }
  // Newest first; re-read whenever the count changes.
  val recs = remember(vm.recordCount) { vm.records.all().asReversed() }
  val saved = remember(vm.favoriteIds) { vm.favorites.all().asReversed() }
  SettingsSubpage(stringResource(R.string.records_title), onBack) {
    SectionHeader(stringResource(R.string.chart_title))
    ChartSection(vm, recs)

    SectionHeader(stringResource(R.string.favorites_title))
    if (saved.isEmpty())
      Text(stringResource(R.string.favorites_empty), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    else Column(Modifier.padding(horizontal = 16.dp)) {
      for (r in saved) {
        RecordEntry(vm, r) { acting = r }
        HorizontalDivider()
      }
    }

    if (recs.isNotEmpty()) {
      SectionHeader(stringResource(R.string.records_list))
      Column(Modifier.padding(horizontal = 16.dp)) {
        for (r in recs.take(shown)) {
          RecordEntry(vm, r) { acting = r }
          HorizontalDivider()
        }
        if (recs.size > shown) TextButton(onClick = { shown += 50 }) { Text(stringResource(R.string.show_more)) }
      }
    }

    SectionHeader(stringResource(R.string.records_share_title))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(stringResource(R.string.records_info), style = MaterialTheme.typography.bodyLarge)
      Text(stringResource(R.string.n_recorded, vm.recordCount), style = MaterialTheme.typography.titleMedium)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { shareRecords(context, vm) }, Modifier.heightIn(min = BIG_BUTTON), enabled = vm.recordCount > 0) {
          Icon(Icons.Outlined.Share, null, Modifier.padding(end = 8.dp))
          Text(stringResource(R.string.share))
        }
        OutlinedButton(
          onClick = { export.launch("thirteen-records-${LocalDate.now()}.jsonl") }, Modifier.heightIn(min = BIG_BUTTON), enabled = vm.recordCount > 0,
        ) {
          Icon(Icons.Outlined.Save, null, Modifier.padding(end = 8.dp))
          Text(stringResource(R.string.export))
        }
      }
    }
    if (recs.isNotEmpty())
      TextButton(
        onClick = { confirmClear = true }, Modifier.padding(8.dp).heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
      ) { Text(stringResource(R.string.clear_records)) }
  }
  acting?.let { HandActions(vm, it) { acting = null } }
  if (confirmClear)
    AlertDialog(
      onDismissRequest = { confirmClear = false },
      confirmButton = { TextButton(onClick = { vm.clearRecords(); confirmClear = false }) { Text(stringResource(R.string.delete)) } },
      dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
      text = { Text(stringResource(R.string.clear_records_confirm, vm.recordCount), style = MaterialTheme.typography.bodyLarge) },
    )
}
