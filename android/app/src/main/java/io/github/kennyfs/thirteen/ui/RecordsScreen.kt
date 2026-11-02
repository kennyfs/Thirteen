package io.github.kennyfs.thirteen.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kennyfs.thirteen.AppViewModel
import io.github.kennyfs.thirteen.R
import io.github.kennyfs.thirteen.Rules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** A record's time (stored in UTC, ISO 8601) in this phone's time zone; as stored if it does not parse. */
private fun localTime(stamp: String): String =
  runCatching { STAMP.format(Instant.parse(stamp).atZone(ZoneId.systemDefault())) }.getOrElse { stamp.take(16).replace('T', ' ') }

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordsScreen(vm: AppViewModel, onAnalyze: () -> Unit) {
  val context = LocalContext.current
  val resolver = context.contentResolver
  val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-ndjson")) { uri ->
    if (uri != null) vm.exportRecords(resolver, uri)
  }
  var confirmClear by remember { mutableStateOf(false) }
  var confirmReset by remember { mutableStateOf(false) }
  // The record picked to analyze, by id.
  var picked by remember { mutableStateOf<String?>(null) }
  // Newest first; re-read whenever the count changes.
  val recs = remember(vm.recordCount) { vm.records.all().asReversed() }
  Column(Modifier.fillMaxSize().padding(8.dp)) {
    val total = recs.sumOf { it.optDouble("result", 0.0) }
    Row {
      Text(stringResource(R.string.n_recorded, vm.recordCount) + " · ", style = MaterialTheme.typography.titleSmall)
      Text(
        stringResource(R.string.total, signed(total)), style = MaterialTheme.typography.titleSmall,
        color = if (total > 0) GOOD else if (total < 0) BAD else MaterialTheme.colorScheme.onSurface,
      )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      val rec = recs.firstOrNull { it.optString("id") == picked }
      Button(onClick = { vm.analyzeRecord(rec!!); onAnalyze() }, enabled = rec != null) { Text(stringResource(R.string.to_analyze)) }
      OutlinedButton(onClick = { export.launch("thirteen-records-${LocalDate.now()}.jsonl") }) { Text(stringResource(R.string.export)) }
      OutlinedButton(onClick = { shareRecords(context, vm) }) { Text(stringResource(R.string.share)) }
      OutlinedButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.clear_records)) }
      OutlinedButton(onClick = { confirmReset = true }) { Text(stringResource(R.string.reset_scores)) }
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    LazyColumn {
      items(recs) { r ->
        val on = r.optString("id") == picked
        Row(
          Modifier.fillMaxWidth()
            .background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable { picked = if (on) null else r.optString("id") }.padding(vertical = 4.dp),
        ) {
          // Records from before the result field have none.
          val result = if (r.has("result")) r.getDouble("result") else null
          Text(
            result?.let(::signed) ?: "", Modifier.width(36.dp), fontSize = 13.sp,
            color = if (result == null) MaterialTheme.colorScheme.onSurface else if (result >= 0) GOOD else BAD,
          )
          // Rules other than family by name; records from before the rules field are family.
          val rules = r.optString("rules").takeIf { it.isNotEmpty() }?.let { vm.rulesName(Rules.values(it)) }?.takeIf { it != "family" }
          if (rules != null) Text("$rules ", fontSize = 13.sp, color = MaterialTheme.colorScheme.tertiary)
          Text(
            "${localTime(r.optString("timestamp"))}   ${r.getString("front")} | ${r.getString("middle")} | ${r.getString("back")}" +
              "   (${r.optLong("time_ms") / 1000} s)",
            fontSize = 13.sp,
          )
        }
      }
    }
  }
  if (confirmClear)
    AlertDialog(
      onDismissRequest = { confirmClear = false },
      confirmButton = { TextButton(onClick = { vm.clearRecords(); confirmClear = false }) { Text(stringResource(R.string.delete)) } },
      dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
      text = { Text(stringResource(R.string.clear_records_confirm, vm.recordCount)) },
    )
  if (confirmReset)
    AlertDialog(
      onDismissRequest = { confirmReset = false },
      confirmButton = { TextButton(onClick = { vm.resetSession(); confirmReset = false }) { Text(stringResource(R.string.reset)) } },
      dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
      text = { Text(stringResource(R.string.reset_confirm)) },
    )
}
