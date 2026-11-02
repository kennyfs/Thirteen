package io.github.kennyfs.thirteen.ui

import androidx.compose.foundation.background
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kennyfs.thirteen.AppViewModel
import io.github.kennyfs.thirteen.Analysis
import io.github.kennyfs.thirteen.Arrangement as Rows
import io.github.kennyfs.thirteen.Candidate
import io.github.kennyfs.thirteen.Cards
import io.github.kennyfs.thirteen.Commentary
import io.github.kennyfs.thirteen.Model
import io.github.kennyfs.thirteen.R
import io.github.kennyfs.thirteen.Rules

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

private fun fmtQ(x: Double) = (if (x >= 0) "+" else "−") + "%.3f".format(kotlin.math.abs(x))
@Composable
fun qColor(q: Double) = if (q >= 0) GOOD else BAD

@Composable
fun AnalyzeScreen(vm: AppViewModel) {
  val a = vm.analysis
  if (vm.anaPage == 1 && a != null) AnalysisPage(vm, a) else HandPage(vm)
}

/**
 * The first sub-page: the 52 cards to pick the hand from across the width, as large as the room allows, and the
 * hand's controls below.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HandPage(vm: AppViewModel) {
  // The controls' height as last laid out: the cards take the rest.
  var controlsHeight by remember { mutableStateOf(CONTROLS_HEIGHT) }
  val density = LocalDensity.current
  BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
    val cellWidth = minOf((maxWidth - CARD_GAP * 12) / 13, 72.dp)
    val cellHeight = minOf((maxHeight - controlsHeight - 6.dp - CARD_GAP * 3) / 4, cellWidth * 1.4f).coerceAtLeast(24.dp)
    // Only cards too small to tap make the page scroll.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      CardPicker(vm.anaHand, cellWidth, cellHeight, vm::toggleAnalyzeCard)
      FlowRow(
        Modifier.onSizeChanged { controlsHeight = with(density) { it.height.toDp() } },
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          stringResource(R.string.n_cards, vm.anaHand.size), Modifier.align(Alignment.CenterVertically),
          style = MaterialTheme.typography.bodyMedium,
          color = if (vm.anaHand.size == 13) GOOD else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = vm::randomAnalyzeHand, contentPadding = COMPACT) { Text(stringResource(R.string.random)) }
        OutlinedButton(onClick = vm::analyzeLastHand, enabled = vm.result != null, contentPadding = COMPACT) { Text(stringResource(R.string.last_hand)) }
        OutlinedButton(onClick = { vm.setAnalyzeHand(emptySet()) }, contentPadding = COMPACT) { Text(stringResource(R.string.clear)) }
        Box(Modifier.align(Alignment.CenterVertically)) {
          Picker(
            stringResource(R.string.network), vm.models.map { it.path to modelLabel(it) }, vm.settings.model(vm.anaModel).path,
            vm::chooseAnalyzeModel,
          )
        }
        Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
          Text(
            stringResource(R.string.rules_label, vm.rulesName(vm.anaRuleValues)) + if (vm.anaRules != null) stringResource(R.string.this_game_rules) else "",
            style = MaterialTheme.typography.bodyMedium,
          )
          if (vm.anaRules != null && !Rules.same(vm.anaRuleValues, vm.ruleValues))
            TextButton(onClick = vm::useCurrentRules) { Text(stringResource(R.string.use_current_rules)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Button(onClick = { vm.analyze() }, enabled = !vm.analyzing, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp)) {
            Text(stringResource(R.string.analyze_go))
          }
          InfoButton(stringResource(R.string.analyze_info))
        }
        if (vm.analysis != null)
          TextButton(onClick = { vm.anaPage = 1 }) { Text(stringResource(R.string.to_candidates)) }
        val model = vm.settings.model(vm.anaModel)
        if (model.path in vm.legacyModels && vm.legacyMismatch(vm.anaRuleValues))
          Text(stringResource(R.string.legacy_warning, modelLabel(model)), color = BAD, style = MaterialTheme.typography.bodySmall)
        if (vm.analyzing) LinearProgressIndicator(Modifier.fillMaxWidth())
      }
    }
  }
}

private val CONTROLS_HEIGHT = 106.dp

/** The second sub-page: the candidates of the hand. */
@Composable
private fun AnalysisPage(vm: AppViewModel, a: Analysis) {
  Column(Modifier.fillMaxSize().padding(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
      TextButton(onClick = { vm.anaPage = 0 }) { Text(stringResource(R.string.back_to_hand)) }
      for (c in vm.anaHand.sortedDescending()) CardFace(c, SMALL_CARD)
      Box(Modifier.weight(1f))
      InfoButton(stringResource(R.string.analyze_info))
    }
    CandidateList(a, vm.anaMine, expandBest = vm.anaMine != null, legacyNote(vm, a))
  }
}

/** The warning for an analysis by a legacy network under rules other than family, or null. */
@Composable
fun legacyNote(vm: AppViewModel, a: Analysis): String? =
  if (a.legacyModel && vm.legacyMismatch(a.rules)) stringResource(R.string.legacy_warning, modelLabel(vm.settings.model(vm.anaModel))) else null

/**
 * The candidates, best Q first, with the player's arrangement (when known) pinned on top. The best one is labelled
 * as such; a candidate added only because it is the player's (dominated) has no rank. `tags` names others who
 * played a candidate, by its index (a battle's other players).
 */
@Composable
fun CandidateList(
  a: Analysis, mine: Rows?, expandBest: Boolean, legacyNote: String?, modifier: Modifier = Modifier, tags: Map<Int, String> = emptyMap(),
) {
  val mineAt = mine?.let { m -> a.cands.indexOfFirst { Cards.sameArrangement(it.arrangement, m) } }?.takeIf { it >= 0 }
  val ranks = a.cands.runningFold(0) { n, cd -> if (cd.extra) n else n + 1 }.drop(1)
  val best = stringResource(R.string.best)
  fun rank(i: Int) = when {
    a.cands[i].extra -> "−"
    ranks[i] == 1 -> best
    else -> ranks[i].toString()
  }
  // As wide as the widest rank label at the font size, and a gap.
  val measurer = rememberTextMeasurer()
  val rankStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
  val rankWidth = with(LocalDensity.current) {
    listOf(best, "−", (ranks.lastOrNull() ?: 0).toString()).maxOf { measurer.measure(it, rankStyle).size.width }.toDp()
  } + 6.dp
  // Open rows by index; PINNED is the player's row on top.
  var open by remember(a) { mutableStateOf(if (expandBest) setOf(PINNED, 0) else emptySet()) }
  fun toggle(i: Int) {
    open = if (i in open) open - i else open + i
  }
  var showAll by remember(a) { mutableStateOf(false) }
  val wheel = a.rules["wheel"]?.toInt() ?: 0
  // A Q this far below the best one is a costly choice.
  val qGap = Rules.notableQGap(a.rules)
  // Royalty types whose loss to a better row costs points under these rules.
  val lostShown = if (a.rules["on_win"] == 1.0) Rules.ROYALTIES.indices.filter { (a.rules[Rules.ROYALTIES[it]] ?: 0.0) > 0 } else emptyList()
  // The row hands of each candidate, and the room for them: the widest at the font size and a gap, so that the shape
  // names after them start at one place; at most 60% of the width, beyond which they wrap.
  val labels = rowLabels()
  val texts = remember(a, labels) { a.cands.map { cd -> cd.info.keys.joinToString(" | ") { Cards.rowLabel(it, labels, wheel) } } }
  val density = LocalDensity.current
  val textsWidth = remember(texts, density) {
    with(density) { (texts.maxOfOrNull { measurer.measure(it, LABEL_STYLE).size.width } ?: 0).toDp() } + 12.dp
  }
  BoxWithConstraints(modifier) {
    val labelWidth = minOf(textsWidth, maxWidth * 0.6f - rankWidth)
    Column(Modifier.fillMaxSize()) {
      if (a.dragon) Text(stringResource(R.string.dragon), style = MaterialTheme.typography.bodySmall, color = GOOD)
      legacyNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = BAD) }
      val remarks = remember(a, mine) { Commentary.remarks(a, mine) }
      val shown = if (showAll) a.cands else a.cands.take(30)
      LazyColumn(Modifier.weight(1f)) {
        if (remarks.isNotEmpty()) item { Remarks(remarks); HorizontalDivider() }
        if (mineAt != null) {
          item {
            Column(Modifier.background(MaterialTheme.colorScheme.secondaryContainer)) {
              Text(
                stringResource(R.string.yours), Modifier.padding(start = 4.dp, top = 2.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
              CandidateRow(rank(mineAt), rankWidth, texts[mineAt], labelWidth, a.cands[mineAt], PINNED in open, if (mineAt == 0) null else a.cands[0], wheel, lostShown, qGap, tags[mineAt]) { toggle(PINNED) }
            }
            HorizontalDivider()
          }
        }
        itemsIndexed(shown) { i, cd ->
          Box(if (i == mineAt) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier) {
            CandidateRow(rank(i), rankWidth, texts[i], labelWidth, cd, i in open, null, wheel, lostShown, qGap, tags[i]) { toggle(i) }
          }
          HorizontalDivider()
        }
        if (!showAll && a.cands.size > 30)
          item { TextButton(onClick = { showAll = true }) { Text(stringResource(R.string.show_all, a.cands.size)) } }
      }
    }
  }
}

private val LABEL_STYLE = TextStyle(fontSize = 13.sp)

private const val PINNED = -1

/**
 * One candidate: its rank, row hands and Q; open, its cards and probabilities. With `best`, the gaps to the best
 * candidate that matter are colored: green where this one does better, red where it does worse.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateRow(
  rank: String, rankWidth: Dp, text: String, labelWidth: Dp, cd: Candidate, open: Boolean, best: Candidate?, wheel: Int, lostShown: List<Int>, qGap: Double,
  tag: String?, onClick: () -> Unit,
) {
  Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(rank, Modifier.width(rankWidth), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1)
      Text(text, Modifier.width(labelWidth), style = LABEL_STYLE)
      Text(
        Commentary.shapeOf(cd.arrangement, cd.info.categories, wheel)?.let { shapeName(it) } ?: "", Modifier.weight(1f),
        style = LABEL_STYLE, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary,
      )
      if (tag != null) Text("$tag  ", style = LABEL_STYLE, color = MaterialTheme.colorScheme.tertiary, maxLines = 1)
      cd.q?.let { q ->
        if (best?.q != null && best.q - q >= qGap)
          Text("(${fmtQ(q - best.q)})  ", fontSize = 12.sp, color = BAD)
        Text(fmtQ(q), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = qColor(q))
      }
    }
    if (open) {
      FlowRow(Modifier.padding(start = rankWidth, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (r in 0 until 3) RowCards(cd.arrangement[r], cd.info.categories[r])
      }
      cd.probs?.let { p ->
        val names = stringArrayResource(R.array.prob_names)
        val good = GOOD
        val bad = BAD
        val text = buildAnnotatedString {
          for ((n, k) in SHOWN_PROBS.withIndex()) {
            if (n > 0) append(" · ")
            val gap = if (best?.probs != null) p[k] - best.probs[k] else 0.0
            val color = when {
              kotlin.math.abs(gap) < NOTABLE_GAP[k] -> null
              (gap > 0) == GOOD_WHEN_HIGH[k] -> good
              else -> bad
            }
            if (color == null) append("${names[k]} %.1f%%".format(100 * p[k]))
            else withStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium)) { append("${names[k]} %.1f%%".format(100 * p[k])) }
          }
          for (t in lostShown) append(" · ${names[ROYALTY_LOST + t]} %.1f%%".format(100 * p[ROYALTY_LOST + t]))
        }
        Text(text, Modifier.padding(start = rankWidth, top = 2.dp), style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

/** 4 × 13 toggles, one per card, by suit (rows) and rank (columns); the picked ones are gold with a thick border. */
@Composable
private fun CardPicker(hand: Set<Int>, width: Dp, height: Dp, onToggle: (Int) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
    for (suit in 3 downTo 0) {
      Row(horizontalArrangement = Arrangement.spacedBy(CARD_GAP)) {
        for (rank in 12 downTo 0) {
          val c = rank * 4 + suit
          val on = c in hand
          CardFace(c, width, height = height, fill = if (on) PICKED else Color.White, selected = on, lift = false, onClick = { onToggle(c) })
        }
      }
    }
  }
}

private val PICKED = Color(0xFFFFD970)

/** A network's name, with "(built-in)" for the one in the APK. */
@Composable
fun modelLabel(m: Model): String = if (m.builtIn) stringResource(R.string.built_in, m.name) else m.name

/** A labelled dropdown over (value, label) options. */
@Composable
fun Picker(label: String, options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Box {
      TextButton(onClick = { expanded = true }) { Text((options.firstOrNull { it.first == value } ?: options.first()).second + " ▾") }
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        for ((v, text) in options)
          DropdownMenuItem(text = { Text(text) }, onClick = { onPick(v); expanded = false })
      }
    }
  }
}
