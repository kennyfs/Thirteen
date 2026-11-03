package io.github.kennyfs.thirteen.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
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

/** The kinds of records, by label: practice (and older ones without a label), table games, puzzle contests. */
enum class RecordKind { PRACTICE, GAME, PUZZLE }

/** A record's kind; null for a hand played again by an older version, which is in none. */
fun kindOf(r: JSONObject): RecordKind? = when (r.optString("label")) {
  "replay" -> null
  "battle" -> RecordKind.GAME
  "puzzle" -> RecordKind.PUZZLE
  else -> RecordKind.PRACTICE
}

/**
 * A record: its result, when, a star to save it or drop it (with `vm`), and its three rows. `stacked`: the rows one
 * under another, for a grid of two records a line; else side by side, as large as `card` and the width allow.
 */
@Composable
fun RecordLine(r: JSONObject, modifier: Modifier = Modifier, vm: AppViewModel? = null, stacked: Boolean = false, card: Dp = LocalCardSizes.current.record) {
  val result = if (r.has("result")) r.getDouble("result") else null
  val colors = MaterialTheme.colorScheme
  val score = @Composable {
    Text(
      result?.let(::signed) ?: "", Modifier.widthIn(min = textRoom(44)), style = MaterialTheme.typography.titleMedium, maxLines = 1,
      color = if (result == null) colors.onSurface else if (result > 0) GOOD else if (result < 0) BAD else colors.onSurface,
    )
  }
  val time = @Composable { m: Modifier ->
    Text(localTime(r.optString("timestamp")), m, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
  val star = @Composable {
    if (vm != null) {
      val saved = r.optString("id") in vm.favoriteIds
      IconButton(onClick = { vm.toggleFavorite(r) }, Modifier.size(40.dp)) {
        Icon(
          if (saved) Icons.Filled.Star else Icons.Outlined.StarBorder, stringResource(if (saved) R.string.favorite_remove else R.string.favorite_add),
          tint = if (saved) Color(0xFFE0A100) else colors.onSurfaceVariant,
        )
      }
    }
  }
  val rows = listOf("front", "middle", "back").map { Cards.parse(r.getString(it)) }
  Column(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      score()
      if (stacked) Spacer(Modifier.weight(1f)) else time(Modifier.weight(1f))
      star()
    }
    if (stacked) {
      time(Modifier)
      BoxWithConstraints(Modifier.fillMaxWidth()) {
        val c = minOf(card, (maxWidth - LINE_GAP * 4) / 5)
        Column(verticalArrangement = Arrangement.spacedBy(LINE_GAP * 2)) { for (row in rows) RowCards(row, 0, c, LINE_GAP) }
      }
    } else BoxWithConstraints(Modifier.fillMaxWidth()) {
      val c = minOf(card, lineCard(maxWidth))
      Row(horizontalArrangement = Arrangement.spacedBy(LINE_GROUP_GAP)) { for (row in rows) RowCards(row, 0, c, LINE_GAP) }
    }
  }
}

/** Records in a list, or on a narrow screen two a line with their rows one under another (larger cards). */
@Composable
fun RecordList(recs: List<JSONObject>, vm: AppViewModel?, onClick: (JSONObject) -> Unit) {
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val stacked = maxWidth < 560.dp
    if (stacked) Column {
      for (pair in recs.chunked(2)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          for (r in pair) RecordLine(r, Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { onClick(r) }.padding(horizontal = 4.dp), vm, stacked = true)
          if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
        HorizontalDivider()
      }
    } else Column {
      for (r in recs) {
        RecordLine(r, Modifier.clickable { onClick(r) }, vm)
        HorizontalDivider()
      }
    }
  }
}

/** What to do with a hand of the lists: analyze it, or play it again to arrange it another way. */
@Composable
private fun HandActions(vm: AppViewModel, r: JSONObject, onDismiss: () -> Unit) {
  // Nearly as wide as the screen, so that the cards are the size they are elsewhere.
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(Modifier.padding(horizontal = 8.dp).widthIn(max = 560.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
      Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        RecordLine(r, Modifier.padding(horizontal = 8.dp))
        TextButton(onClick = { vm.analyzeRecordOnScreen(r); onDismiss() }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
          Text(stringResource(R.string.analyze_this), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        }
        TextButton(
          onClick = { vm.replay(Cards.parse(r.getString("hand"))); onDismiss() }, Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("hand_replay"),
        ) { Text(stringResource(R.string.replay_hand), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth()) }
        TextButton(onClick = onDismiss, Modifier.align(Alignment.End)) { Text(stringResource(R.string.cancel)) }
      }
    }
  }
}

/** The score chart of one kind of records: the total over the records of one set of rules, which a menu chooses when there are several. */
@Composable
private fun ChartSection(vm: AppViewModel, recs: List<JSONObject>, kind: RecordKind) {
  // Oldest first; empty ones out. Rules without the dealer's cost, as the totals are kept.
  val groups = remember(recs) {
    recs.asReversed().filter { it.has("result") }
      .groupBy { it.optString("rules").ifEmpty { "family" }.split(",").filterNot { p -> p.startsWith("c=") }.joinToString(",") }
  }
  if (groups.isEmpty()) {
    Text(stringResource(R.string.records_none), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    if (kind == RecordKind.PUZZLE)
      Text(stringResource(R.string.chart_puzzle_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ScoreChart(
      totals, localTime(list.first().optString("timestamp")).take(10), localTime(list.last().optString("timestamp")).take(10),
      stringResource(R.string.chart_desc, list.size, signed(last)),
    )
  }
}

/** The records, a screen of their own: by kind, the chart and the list; the saved hands; sharing them with the developer. */
@Composable
fun RecordsPage(vm: AppViewModel) {
  val context = LocalContext.current
  val resolver = context.contentResolver
  val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-ndjson")) { uri ->
    if (uri != null) vm.exportRecords(resolver, uri)
  }
  var confirmClear by rememberSaveable { mutableStateOf(false) }
  var shown by rememberSaveable { mutableStateOf(20) }
  var kind by rememberSaveable { mutableStateOf(RecordKind.PRACTICE) }
  var acting by remember { mutableStateOf<JSONObject?>(null) }
  LaunchedEffect(Unit) { vm.refreshRecords() }
  // Newest first; re-read whenever the count changes.
  val recs = remember(vm.recordCount) { vm.records.all().asReversed() }
  val ofKind = remember(recs, kind) { recs.filter { kindOf(it) == kind } }
  val saved = remember(vm.favoriteIds) { vm.favorites.all().asReversed() }
  Column(Modifier.fillMaxSize()) {
    TitleBar(stringResource(R.string.records_title))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
      val names = listOf(R.string.records_practice, R.string.records_game, R.string.records_puzzle).map { stringResource(it) }
      ToggleGroup(RecordKind.entries.map { it to names[it.ordinal] }, kind, { kind = it; shown = 20 }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
      SectionHeader(stringResource(R.string.chart_title))
      ChartSection(vm, ofKind, kind)

      if (ofKind.isNotEmpty()) {
        SectionHeader(stringResource(R.string.records_list))
        Column(Modifier.padding(horizontal = 16.dp)) {
          RecordList(ofKind.take(shown), vm) { acting = it }
          if (ofKind.size > shown) TextButton(onClick = { shown += 50 }) { Text(stringResource(R.string.show_more)) }
        }
      }

      SectionHeader(stringResource(R.string.favorites_title))
      if (saved.isEmpty())
        Text(stringResource(R.string.favorites_empty), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      else Column(Modifier.padding(horizontal = 16.dp)) { RecordList(saved, vm) { acting = it } }

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
