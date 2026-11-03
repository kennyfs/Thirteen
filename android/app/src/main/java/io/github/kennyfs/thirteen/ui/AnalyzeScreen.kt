package io.github.kennyfs.thirteen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kennyfs.thirteen.AppViewModel
import io.github.kennyfs.thirteen.Analysis
import io.github.kennyfs.thirteen.Arrangement as Rows
import io.github.kennyfs.thirteen.Candidate
import io.github.kennyfs.thirteen.Cards
import io.github.kennyfs.thirteen.Commentary
import io.github.kennyfs.thirteen.Model
import io.github.kennyfs.thirteen.R
import io.github.kennyfs.thirteen.Rules
import io.github.kennyfs.thirteen.ui.theme.LocalDark
import io.github.kennyfs.thirteen.ui.theme.LocalTable
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// A candidate's first probabilities are R.array.prob_names (Scoring targets, as in gui/play.html). The dealer (4) is
// left out: the app always plays and analyzes with c = 0.
private val SHOWN_PROBS = listOf(0, 1, 2, 3, 5, 6, 7)
// Whether more of an event is good for the player, and the gap to the best candidate worth a color: about the 75th
// percentile of |best - k-th best| for k = 2..6 over 300 random hands, with run2's net_10 under family, family_mid2,
// taiwan and pagat alike (the probabilities are the same events whatever the rules).
private val GOOD_WHEN_HIGH = booleanArrayOf(true, false, true, false, false, true, true, true)
private val NOTABLE_GAP = doubleArrayOf(0.03, 0.03, 0.005, 0.005, 0.03, 0.10, 0.10, 0.10)
// The chances that an opponent's royalty row beats mine (Scoring::ROYALTY_LOST_*), by Rules.ROYALTIES; shown, without
// colors, when royalties are paid only on won rows.
private const val ROYALTY_LOST = 11

/** A Q to two places, signed: "+0.57", "−1.02". */
fun fmtQ(x: Double) = (if (x >= 0) "+" else "−") + "%.2f".format(kotlin.math.abs(x))

@Composable
fun qColor(q: Double) = if (q >= 0) GOOD else BAD

/** Analyze: pick 13 cards (or take them from somewhere), then the candidates. */
@Composable
fun AnalyzeScreen(vm: AppViewModel, onRules: () -> Unit) {
  val a = vm.analysis
  BackHandler(vm.anaPage == 1) { vm.anaPage = 0 }
  if (vm.anaPage == 1 && a != null) AnalysisPage(vm, a) else HandPage(vm, onRules)
}

/** The rules and network of the analysis, as chips for a title bar. */
@Composable
private fun AnalysisChoices(vm: AppViewModel, onRules: () -> Unit) {
  if (vm.anaRules != null) {
    val options = buildList {
      add(0 to stringResource(R.string.this_game_rules_named, vm.rulesName(vm.anaRuleValues)))
      if (!Rules.same(vm.anaRuleValues, vm.ruleValues)) add(1 to stringResource(R.string.use_current_rules_named, vm.rulesName(vm.ruleValues)))
    }
    MenuChip(stringResource(R.string.this_game_rules_named, vm.rulesName(vm.anaRuleValues)), options, { if (it == 1) vm.useCurrentRules() })
  } else if (vm.ruleValues.isNotEmpty()) Chip(vm.rulesName(vm.ruleValues), onRules, trailingMenu = true)
  MenuChip(modelLabel(vm.settings.model(vm.anaModel)), vm.models.map { it.path to modelLabel(it) }, vm::chooseAnalyzeModel)
}

/** The first page: the 52 cards to pick the hand from, as large as the room allows, and the hand's actions below. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HandPage(vm: AppViewModel, onRules: () -> Unit) {
  var pickRecord by rememberSaveable { mutableStateOf(false) }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val wide = maxWidth > maxHeight * 1.15f
    Column(Modifier.fillMaxSize()) {
      FlowRow(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
      ) {
        Text(stringResource(R.string.nav_analyze), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 4.dp))
        Text(stringResource(R.string.pick_13_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        AnalysisChoices(vm, onRules)
      }
      BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
        val perLine = if (wide) 13 else 7
        val lines = if (wide) 4 else 8
        val gap = 4.dp
        val cellWidth = minOf((maxWidth - gap * (perLine - 1)) / perLine, 72.dp)
        val cellHeight = minOf((maxHeight - gap * (lines - 1) - (if (wide) 0.dp else 8.dp * 3)) / lines, cellWidth * 1.3f).coerceAtLeast(40.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (wide) gap else 8.dp)) {
          for (suit in 3 downTo 0) {
            val ranks = (12 downTo 0).toList()
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
              for (line in ranks.chunked(perLine)) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (rank in line) {
                  val c = rank * 4 + suit
                  val on = c in vm.anaHand
                  CardFace(c, cellWidth, height = cellHeight, fill = if (on) (if (LocalDark.current) PICKED_DARK else PICKED) else CARD_FACE, selected = on, lift = false, onClick = { vm.toggleAnalyzeCard(c) })
                }
              }
            }
          }
        }
      }
      val model = vm.settings.model(vm.anaModel)
      if (model.path in vm.legacyModels && vm.legacyMismatch(vm.anaRuleValues))
        Text(stringResource(R.string.legacy_warning, modelLabel(model)), Modifier.padding(horizontal = 16.dp), color = BAD, style = MaterialTheme.typography.bodyMedium)
      val go: @Composable (Modifier) -> Unit = { m ->
        Button(
          onClick = { vm.analyze() }, m.heightIn(min = BIG_BUTTON).widthIn(min = 132.dp), enabled = vm.anaHand.size == 13 && !vm.analyzing,
          shape = RoundedCornerShape(18.dp),
        ) {
          if (vm.analyzing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
          else Text(
            if (vm.anaHand.size < 13) stringResource(R.string.pick_n, 13 - vm.anaHand.size) else stringResource(R.string.analyze_go),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
          )
        }
      }
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          "${vm.anaHand.size} / 13", style = MaterialTheme.typography.headlineSmall,
          color = if (vm.anaHand.size == 13) GOOD else MaterialTheme.colorScheme.onSurface,
        )
        if (wide) Box(Modifier.weight(1f)) {
          Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { for (c in vm.anaHand.sortedDescending()) CardFace(c, 22.dp) }
        } else Spacer(Modifier.weight(1f))
        SourceMenu(vm) { pickRecord = true }
        OutlinedIconButton(onClick = { vm.setAnalyzeHand(emptySet()) }, Modifier.size(52.dp), enabled = vm.anaHand.isNotEmpty()) {
          Icon(Icons.Outlined.Clear, stringResource(R.string.clear))
        }
        // On a narrow screen the button gets a row of its own, below.
        if (wide) go(Modifier)
      }
      if (!wide) go(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp))
      if (vm.analysis != null && !vm.analyzing)
        TextButton(onClick = { vm.anaPage = 1 }, Modifier.padding(start = 8.dp, bottom = 4.dp)) { Text(stringResource(R.string.to_candidates)) }
    }
  }
  if (pickRecord) RecordPicker(vm, onDismiss = { pickRecord = false })
}

private val PICKED = Color(0xFFFFE7A6)
private val PICKED_DARK = Color(0xFF6B5A24)

/** Where a hand can come from: random, the last hand played, or a record. */
@Composable
private fun SourceMenu(vm: AppViewModel, onRecords: () -> Unit) {
  var open by remember { mutableStateOf(false) }
  Box {
    OutlinedButton(onClick = { open = true }, Modifier.heightIn(min = 52.dp)) {
      Text(stringResource(R.string.source))
      Icon(Icons.Filled.ArrowDropDown, null)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      DropdownMenuItem({ Text(stringResource(R.string.random), style = MaterialTheme.typography.bodyLarge) }, { vm.randomAnalyzeHand(); open = false }, Modifier.heightIn(min = 52.dp))
      DropdownMenuItem(
        { Text(stringResource(R.string.last_hand), style = MaterialTheme.typography.bodyLarge) }, { vm.analyzeLastHand(); open = false },
        Modifier.heightIn(min = 52.dp), enabled = vm.result != null,
      )
      DropdownMenuItem(
        { Text(stringResource(R.string.from_records), style = MaterialTheme.typography.bodyLarge) }, { open = false; onRecords() },
        Modifier.heightIn(min = 52.dp), enabled = vm.recordCount > 0,
      )
    }
  }
}

private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** A record's time (stored in UTC, ISO 8601) in this phone's time zone; as stored if it does not parse. */
fun localTime(stamp: String): String =
  runCatching { STAMP.format(Instant.parse(stamp).atZone(ZoneId.systemDefault())) }.getOrElse { stamp.take(16).replace('T', ' ') }

/** The saved hands, then the records newest first, to pick one to analyze. */
@Composable
private fun RecordPicker(vm: AppViewModel, onDismiss: () -> Unit) {
  val recs = remember(vm.recordCount) { vm.records.all().asReversed() }
  val saved = remember(vm.favoriteIds) { vm.favorites.all().asReversed() }
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    title = { Text(stringResource(R.string.from_records)) },
    text = {
      LazyColumn(Modifier.heightIn(max = 420.dp)) {
        if (saved.isNotEmpty()) {
          item { SectionHeader(stringResource(R.string.favorites_title)) }
          items(saved) { r -> RecordLine(r, Modifier.clickable { vm.analyzeRecord(r); onDismiss() }) }
          item { SectionHeader(stringResource(R.string.records_list)) }
        }
        items(recs) { r -> RecordLine(r, Modifier.clickable { vm.analyzeRecord(r); onDismiss() }) }
      }
    },
  )
}

/** A record in a list: its result, when, and the three rows as cards. */
@Composable
fun RecordLine(r: JSONObject, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
  val result = if (r.has("result")) r.getDouble("result") else null
  Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(
        result?.let(::signed) ?: "", style = MaterialTheme.typography.titleMedium,
        color = if (result == null) MaterialTheme.colorScheme.onSurface else if (result > 0) GOOD else if (result < 0) BAD else MaterialTheme.colorScheme.onSurface,
      )
      Text(localTime(r.optString("timestamp")), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (trailing != null) {
        Spacer(Modifier.weight(1f))
        trailing()
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      for (row in listOf("front", "middle", "back")) RowCards(Cards.parse(r.getString(row)), 0, 18.dp, 2.dp)
    }
  }
}

/** The second page: the candidates of the hand. */
@Composable
private fun AnalysisPage(vm: AppViewModel, a: Analysis) {
  Column(Modifier.fillMaxSize()) {
    TitleBar(stringResource(R.string.candidates), onBack = { vm.anaPage = 0 }) {
      InfoButton(stringResource(R.string.analyze_info))
    }
    CandidateList(a, vm.anaMine, legacyNote(vm, a), Modifier.weight(1f))
  }
}

/** The warning for an analysis by a legacy network under rules other than family, or null. */
@Composable
fun legacyNote(vm: AppViewModel, a: Analysis): String? =
  if (a.legacyModel && vm.legacyMismatch(a.rules)) stringResource(R.string.legacy_warning, modelLabel(vm.settings.model(vm.anaModel))) else null

private const val PINNED = -1

/**
 * The candidates, best Q first, with the player's arrangement (when known) pinned on top; when it is the best one, the
 * best one says so instead. Each shows its cards; opened, its rows, Q and the probabilities, colored where they differ
 * notably from the best one's. On a wide screen the list is on the left and the opened one on the right. `tags` names
 * others who played a candidate, by its index (a battle's other players). `mineLabel` pins `mine` as someone else's
 * play (their name), without the remarks on it, which speak to the player.
 */
@Composable
fun CandidateList(
  a: Analysis, mine: Rows?, legacyNote: String?, modifier: Modifier = Modifier, tags: Map<Int, String> = emptyMap(),
  mineLabel: String? = null,
) {
  val mineAt = mine?.let { m -> a.cands.indexOfFirst { Cards.sameArrangement(it.arrangement, m) } }?.takeIf { it >= 0 }
  val pinned = mineAt != null && mineAt != 0
  val ranks = a.cands.runningFold(0) { n, cd -> if (cd.extra) n else n + 1 }.drop(1)
  val wheel = a.rules["wheel"]?.toInt() ?: 0
  val remarks = remember(a, mine, mineLabel) {
    Commentary.remarks(a, mine).filter { mineLabel == null || (it !is Commentary.Same && it !is Commentary.Mistake) }
  }
  val labels = rowLabels()
  val texts = remember(a, labels) { a.cands.map { cd -> cd.info.keys.map { Cards.rowLabel(it, labels, wheel) } } }
  var showAll by rememberSaveable(a) { mutableStateOf(false) }
  val shown = if (showAll) a.cands.indices.toList() else a.cands.indices.take(30)
  val yours = mineLabel ?: stringResource(R.string.yours)
  val best = stringResource(R.string.best)
  @Composable
  fun badgeOf(i: Int, pin: Boolean): Pair<String, Boolean> = when {
    pin -> (if (a.cands[i].extra) yours else stringResource(R.string.yours_rank, yours, ranks[i])) to true
    i == 0 && mineAt == 0 -> "$best · $yours" to true
    a.cands[i].extra -> "−" to false
    i == 0 -> best to true
    else -> ranks[i].toString() to false
  }
  BoxWithConstraints(modifier.fillMaxSize()) {
    val wide = maxWidth >= 760.dp
    val listWidth = if (wide) minOf(maxWidth * 0.45f, 460.dp) else maxWidth
    val listCard = ((listWidth - 32.dp - 20.dp - 2.dp * 10) / 13).coerceIn(14.dp, 30.dp)
    var openSet by rememberSaveable(a) { mutableStateOf(if (pinned) listOf(PINNED, 0) else listOf(0)) }
    var current by rememberSaveable(a) { mutableStateOf(if (pinned) PINNED else 0) }
    fun index(key: Int) = if (key == PINNED) mineAt!! else key
    val list = @Composable { m: Modifier ->
      LazyColumn(m) {
        item {
          Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (a.dragon) Text(stringResource(R.string.dragon), style = MaterialTheme.typography.bodyLarge, color = GOOD)
            legacyNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = BAD) }
            if (remarks.isNotEmpty()) Remarks(remarks)
          }
        }
        if (pinned) item(key = "pinned") {
          val (label, strong) = badgeOf(mineAt!!, true)
          CandidateItem(
            label, strong, texts[mineAt], a.cands[mineAt], a.cands[0], wheel, listCard, tags[mineAt], open = if (wide) current == PINNED else PINNED in openSet,
            mine = true, detail = !wide, a = a,
          ) { if (wide) current = PINNED else openSet = if (PINNED in openSet) openSet - PINNED else openSet + PINNED }
          HorizontalDivider(Modifier.padding(horizontal = 12.dp))
        }
        items(shown, key = { it }) { i ->
          val (label, strong) = badgeOf(i, false)
          CandidateItem(
            label, strong, texts[i], a.cands[i], if (i == 0) null else a.cands[0], wheel, listCard, tags[i],
            open = if (wide) current == i else i in openSet, mine = i == mineAt, detail = !wide, a = a,
          ) { if (wide) current = i else openSet = if (i in openSet) openSet - i else openSet + i }
        }
        if (!showAll && a.cands.size > 30)
          item { TextButton(onClick = { showAll = true }, Modifier.padding(8.dp)) { Text(stringResource(R.string.show_all, a.cands.size)) } }
      }
    }
    if (wide) Row(Modifier.fillMaxSize()) {
      list(Modifier.width(listWidth).fillMaxHeight())
      val i = index(current)
      val (label, _) = badgeOf(i, current == PINNED)
      Column(
        Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
          Badge(label, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
          Commentary.shapeOf(a.cands[i].arrangement, a.cands[i].info.categories, wheel)?.let {
            Text(shapeName(it), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
          }
        }
        CandidateDetail(a, i, texts[i], wheel, if (i == 0) null else a.cands[0], mineAt, big = true)
      }
    } else list(Modifier.fillMaxSize())
  }
}

/** One candidate in the list: its label, shape name, Q and cards; opened (on a narrow screen) its details below. */
@Composable
private fun CandidateItem(
  label: String, strong: Boolean, texts: List<String>, cd: Candidate, best: Candidate?, wheel: Int, card: Dp, tag: String?, open: Boolean,
  mine: Boolean, detail: Boolean, a: Analysis, onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val qGap = Rules.notableQGap(a.rules)
  Column(
    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(RoundedCornerShape(14.dp))
      .background(if (open) colors.secondaryContainer else if (mine) colors.surfaceContainerHigh else Color.Transparent)
      .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (strong) Badge(label, colors.primary, colors.onPrimary) else Badge(label, colors.surfaceContainerHighest, colors.onSurface)
      Text(
        Commentary.shapeOf(cd.arrangement, cd.info.categories, wheel)?.let { shapeName(it) } ?: "", Modifier.weight(1f),
        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
      )
      if (tag != null) Text(tag, style = MaterialTheme.typography.bodyMedium, color = colors.tertiary, maxLines = 1)
      cd.q?.let { q ->
        if (best?.q != null && best.q - q >= qGap) Text("(${fmtQ(q - best.q)})", style = MaterialTheme.typography.bodyMedium, color = BAD, maxLines = 1, softWrap = false)
        Text(fmtQ(q), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = qColor(q), maxLines = 1, softWrap = false)
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      for (r in 0 until 3) RowCards(cd.arrangement[r], cd.info.categories[r], card, 2.dp)
    }
    if (open && detail) CandidateDetail(a, a.cands.indexOf(cd), texts, wheel, best, null, big = false)
  }
}

/**
 * A candidate's details: each row with its hand, the expected points and the gap to the best (or to the player's), and
 * the probabilities, colored where they differ notably from the best one's.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateDetail(a: Analysis, i: Int, texts: List<String>, wheel: Int, best: Candidate?, mineAt: Int?, big: Boolean) {
  val cd = a.cands[i]
  val rowNames = stringArrayResource(R.array.row_names)
  val colors = MaterialTheme.colorScheme
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (big) {
      BoxWithConstraints {
        val card = ((maxWidth - 8.dp * 4) / 5).coerceAtMost(48.dp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          for (r in 0 until 3) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${rowNames[r]}　${texts[r]}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            RowCards(cd.arrangement[r], cd.info.categories[r], card)
          }
        }
      }
    } else Text(texts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    cd.q?.let { q ->
      FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(stringResource(R.string.expected), style = MaterialTheme.typography.bodyLarge)
          Text(fmtQ(q), style = MaterialTheme.typography.titleLarge, color = qColor(q))
        }
        val bq = best?.q
        if (bq != null && bq > q) Text(stringResource(R.string.less_than_best, "%.2f".format(bq - q)), style = MaterialTheme.typography.bodyLarge, color = BAD)
        val mq = mineAt?.takeIf { it != i }?.let { a.cands[it].q }
        if (best == null && mq != null && q > mq) Text(stringResource(R.string.more_than_yours, "%.2f".format(q - mq)), style = MaterialTheme.typography.bodyLarge, color = GOOD)
      }
    }
    cd.probs?.let { p ->
      val names = stringArrayResource(R.array.prob_names)
      val lostShown = if (a.rules["on_win"] == 1.0) Rules.ROYALTIES.indices.filter { (a.rules[Rules.ROYALTIES[it]] ?: 0.0) > 0 } else emptyList()
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (k in SHOWN_PROBS) {
          val gap = if (best?.probs != null) p[k] - best.probs[k] else 0.0
          val tone = when {
            kotlin.math.abs(gap) < NOTABLE_GAP[k] -> 0
            (gap > 0) == GOOD_WHEN_HIGH[k] -> 1
            else -> -1
          }
          ProbTile(names[k], p[k], tone, if (gap > 0) "▲" else "▼")
        }
        for (t in lostShown) ProbTile(names[ROYALTY_LOST + t], p[ROYALTY_LOST + t], 0, "")
      }
    }
  }
}

@Composable
private fun ProbTile(name: String, p: Double, tone: Int, arrow: String) {
  val colors = MaterialTheme.colorScheme
  Column(
    Modifier.widthIn(min = textRoom(88)).clip(RoundedCornerShape(10.dp)).background(colors.surfaceContainerHighest).padding(horizontal = 10.dp, vertical = 5.dp),
  ) {
    Text(name, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
    Text(
      "%.1f%%".format(100 * p) + if (tone != 0) " $arrow" else "", style = MaterialTheme.typography.titleMedium,
      color = when (tone) { 1 -> GOOD; -1 -> BAD; else -> colors.onSurface }, fontWeight = if (tone != 0) FontWeight.Bold else FontWeight.Normal,
    )
  }
}

/** A network's name, with "(built-in)" for the one in the APK. */
@Composable
fun modelLabel(m: Model): String = if (m.builtIn) stringResource(R.string.built_in, m.name) else m.name
