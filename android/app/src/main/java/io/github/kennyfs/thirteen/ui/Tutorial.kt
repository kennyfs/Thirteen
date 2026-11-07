package io.github.kennyfs.thirteen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kennyfs.thirteen.Analysis
import io.github.kennyfs.thirteen.AppViewModel
import io.github.kennyfs.thirteen.Arrangement as Rows
import io.github.kennyfs.thirteen.Arranger
import io.github.kennyfs.thirteen.Cards
import io.github.kennyfs.thirteen.Engine
import io.github.kennyfs.thirteen.R
import io.github.kennyfs.thirteen.Rules
import io.github.kennyfs.thirteen.ui.theme.LocalTable
import kotlinx.coroutines.launch

private fun rows(f: String, m: String, b: String): Rows = listOf(Cards.parse(f), Cards.parse(m), Cards.parse(b))

private val GOAL = rows("Ac Qh 5h", "9c 9h 7d 7s 2c", "Ks Kd Kh 4s 4d")
// A five-card example of each kind, the biggest first (category 9 down to 1).
private val KINDS = listOf(
  9 to "9h 8h 7h 6h 5h", 8 to "Ks Kh Kd Kc 4s", 7 to "Qs Qh Qd 7c 7d", 6 to "Ad Jd 8d 6d 3d", 5 to "Tc 9s 8h 7d 6c",
  4 to "8s 8h 8d Kc 2s", 3 to "Js Jd 4h 4c Ad", 2 to "9c 9d Ah 7s 3h", 1 to "Ac Qd 9h 6s 4c",
).map { (c, s) -> c to Cards.parse(s) }
private val FOUL = rows("Qs Qh 5c", "Ad Kc 9s 7h 3d", "Th 8h 6h 4h 2h")
private val FIXED = rows("Ad Kc 5c", "Qs Qh 9s 7h 3d", "Th 8h 6h 4h 2h")
// The hand to try: the instinctive way (the aces with the queens' full house in the back) is far from the best (the
// aces in front, the sixes in the full house) under family and taiwan (python/tips/pick_hand.py, docs/tips.md).
private val TRY = Cards.parse("As Ad Kh Kd Qh Qd Qc Jh 7h 6s 6h 3h 2h")
private const val PAGES = 7

/**
 * How to play, for someone who has never played: the rows; which row hand is bigger; fouls; the points under the rules
 * now chosen; a hand to set and have the AI judge (not scored or recorded); tips from the AI's own analysis of 10,000
 * random hands (docs/tips.md); then practice or the tour. As the web page's Tutorial.svelte.
 */
@Composable
fun TutorialPage(vm: AppViewModel, onBack: () -> Unit, onPlay: () -> Unit, onTour: () -> Unit) {
  var page by rememberSaveable { mutableStateOf(0) }
  Column(Modifier.fillMaxSize()) {
    TitleBar(stringResource(R.string.tut_title), onBack = onBack) {
      Text(stringResource(R.string.tut_page, page + 1, PAGES), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
      val content = Modifier.readable(760.dp).fillMaxWidth().padding(horizontal = 16.dp)
      if (page == 4) TryPage(vm, content.fillMaxSize().padding(bottom = 8.dp))
      else Column(content.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (page) {
          0 -> GoalPage()
          1 -> RanksPage(vm)
          2 -> FoulPage(vm)
          3 -> ScorePage(vm)
          5 -> TipsPage()
          else -> EndPage(onPlay, onTour)
        }
      }
    }
    HorizontalDivider()
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = { page = (page - 1).coerceAtLeast(0) }, enabled = page > 0) { Text(stringResource(R.string.tut_prev)) }
      Spacer(Modifier.weight(1f))
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(PAGES) { i ->
          Box(Modifier.size(8.dp).clip(CircleShape).background(if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest))
        }
      }
      Spacer(Modifier.weight(1f))
      if (page < PAGES - 1) Button(onClick = { page++ }, Modifier.testTag("tut_next")) { Text(stringResource(R.string.tut_next)) }
      else Button(onClick = onPlay, Modifier.testTag("tut_next")) { Text(stringResource(R.string.tut_done)) }
    }
  }
}

@Composable
private fun Heading(id: Int) = Text(stringResource(id), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun Body(text: String, dim: Boolean = false, color: androidx.compose.ui.graphics.Color? = null, small: Boolean = false) = Text(
  text, style = if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
  color = color ?: if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
)

/** Three rows on the felt, named. */
/**
 * Rows of a name and its cards, the cards lined up after the widest name in any language (as wide as a third of the
 * room at most; a longer name wraps).
 */
@Composable
private fun LabelledRows(names: List<String>, style: TextStyle, gap: Dp, cards: @Composable (Int) -> Unit) {
  val measurer = rememberTextMeasurer()
  BoxWithConstraints {
    val widest = with(LocalDensity.current) { names.maxOf { measurer.measure(it, style).size.width }.toDp() }
    val width = minOf(widest, maxWidth / 3)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      names.forEachIndexed { i, name ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
          Text(name, style = style, modifier = Modifier.width(width))
          cards(i)
        }
      }
    }
  }
}

@Composable
private fun ThreeRowsExample(r: Rows, cats: List<Int>, card: Dp) {
  val t = LocalTable.current
  Surface(shape = MaterialTheme.shapes.medium, color = t.felt, contentColor = t.onFelt) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      LabelledRows(stringArrayResource(R.array.row_names).toList(), MaterialTheme.typography.labelLarge, 10.dp) { i ->
        RowCards(r[i], cats[i], card)
      }
    }
  }
}

@Composable
private fun GoalPage() {
  Heading(R.string.tut_goal_title)
  Body(stringResource(R.string.tut_goal_text))
  Body(stringResource(R.string.tut_goal_example), dim = true, small = true)
  ThreeRowsExample(GOAL, listOf(1, 3, 7), 40.dp)
}

@Composable
private fun RanksPage(vm: AppViewModel) {
  Heading(R.string.tut_ranks_title)
  Body(stringResource(R.string.tut_ranks_text))
  val names = stringArrayResource(R.array.category_names)
  LabelledRows(KINDS.map { names[it.first] }, MaterialTheme.typography.titleSmall, 12.dp) { i ->
    RowCards(KINDS[i].second, KINDS[i].first, 30.dp)
  }
  Body(stringResource(R.string.tut_ranks_front), small = true)
  val wheel = vm.ruleValues["wheel"]?.toInt() ?: 0
  Body(stringResource(R.string.tut_wheel, stringArrayResource(R.array.wheel_options)[wheel]), dim = true, small = true)
}

@Composable
private fun FoulPage(vm: AppViewModel) {
  Heading(R.string.tut_foul_title)
  Body(stringResource(R.string.tut_foul_text))
  if (vm.ruleValues["strict"] == 1.0) Body(stringResource(R.string.tut_foul_strict), small = true)
  Text(stringResource(R.string.tut_foul_bad), style = MaterialTheme.typography.titleSmall, color = BAD)
  ThreeRowsExample(FOUL, listOf(2, 1, 6), 36.dp)
  Text(stringResource(R.string.tut_foul_good), style = MaterialTheme.typography.titleSmall, color = GOOD)
  ThreeRowsExample(FIXED, listOf(1, 2, 6), 36.dp)
}

@Composable
private fun ScorePage(vm: AppViewModel) {
  val v = vm.ruleValues
  fun n(k: String) = Rules.fmt(v[k] ?: 0.0)
  val row = v["row"] ?: 0.0
  val royaltyNames = stringArrayResource(R.array.royalty_labels)
  val lines = buildList {
    add(if (row > 0) stringResource(R.string.tut_score_rows, n("row")) else stringResource(R.string.tut_score_norows))
    if ((v["scoop"] ?: 0.0) > 0) add(stringResource(if (row > 0) R.string.tut_score_scoop else R.string.tut_score_scoop_only, n("scoop")))
    if ((v["home_run"] ?: 0.0) > 0) add(stringResource(R.string.tut_score_home, n("home_run")))
    val special = Rules.ROYALTIES.mapIndexedNotNull { i, k -> (v[k] ?: 0.0).takeIf { it > 0 }?.let { "${royaltyNames[i]} ${Rules.fmt(it)}" } }
      .joinToString(stringResource(R.string.list_sep))
    if (special.isNotEmpty()) add(stringResource(if (v["on_win"] == 1.0) R.string.tut_score_special_win else R.string.tut_score_special, special))
    if ((v["dragon"] ?: 0.0) > 0) add(stringResource(R.string.tut_score_dragon, n("dragon")))
  }
  Heading(R.string.tut_score_title)
  Body(stringResource(R.string.tut_score_rules, vm.rulesName(v)))
  for (line in lines) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("•", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    Body(line)
  }
  Body(stringResource(R.string.tut_score_change), dim = true, small = true)
}

@Composable
private fun TipsPage() {
  Heading(R.string.tut_tips_title)
  Body(stringResource(R.string.tut_tips_text), dim = true, small = true)
  for ((i, tip) in stringArrayResource(R.array.tut_tips).withIndex()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("${i + 1}.", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Body(tip)
  }
}

@Composable
private fun EndPage(onPlay: () -> Unit, onTour: () -> Unit) {
  Heading(R.string.tut_end_title)
  Body(stringResource(R.string.tut_end_text))
  Button(onClick = onPlay, Modifier.fillMaxWidth().heightIn(min = BIG_BUTTON)) { Text(stringResource(R.string.tut_done)) }
  FilledTonalButton(onClick = onTour, Modifier.fillMaxWidth().heightIn(min = BIG_BUTTON)) { Text(stringResource(R.string.welcome_tour)) }
}

/** The hand to try on the real table, then the AI's verdict on it: its rank, and the best rows. */
@Composable
private fun TryPage(vm: AppViewModel, modifier: Modifier) {
  val scope = rememberCoroutineScope()
  val res = LocalContext.current.resources
  val arranger = remember { Arranger(scope, { vm.error = it }) { row, free -> res.getString(R.string.row_room, res.getStringArray(R.array.row_names)[row], free) } }
  var rules by remember { mutableStateOf("") }
  var checking by remember { mutableStateOf(false) }
  var verdict by remember { mutableStateOf<Triple<Analysis, Rows, List<Int>>?>(null) }
  val fill = stringResource(R.string.fill_rows_first)
  val fouled = stringResource(R.string.fouled)
  suspend fun reset() {
    rules = Engine.rulesFull(vm.ruleSpec.spec())
    verdict = null
    arranger.reset(TRY, rules)
  }
  LaunchedEffect(Unit) { runCatching { reset() }.onFailure { vm.error = it.message } }
  fun check() = scope.launch {
    val ev = arranger.eval()
    if (ev.valid != true) { vm.error = if (ev.valid == null) fill else fouled; return@launch }
    checking = true
    try {
      val mine = arranger.rows
      val a = Engine.analyze(TRY.sorted(), rules, vm.settings.models().first().path, listOf(mine))
      verdict = Triple(a, mine, ev.categories)
    } catch (e: Exception) {
      vm.error = e.message
    } finally {
      checking = false
    }
  }
  val v = verdict
  if (v == null) Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Heading(R.string.tut_try_title)
    Body(stringResource(R.string.tut_try_text))
    if (rules.isNotEmpty())
      Arranging(arranger, Rules.values(rules)["wheel"]?.toInt() ?: 0, checking, stringResource(R.string.tut_try_check), Modifier.weight(1f)) { check() }
  } else Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val (a, mine, cats) = v
    val i = a.cands.indexOfFirst { Cards.sameArrangement(it.arrangement, mine) }.takeIf { it >= 0 }
    val best = a.cands[0]
    val total = a.cands.count { !it.extra }
    val rank = i?.let { k -> a.cands.take(k + 1).count { !it.extra } }
    Heading(R.string.tut_try_title)
    Text(
      when {
        i == 0 -> stringResource(R.string.tut_try_best)
        i == null || a.cands[i].extra -> stringResource(R.string.tut_try_extra)
        else -> stringResource(R.string.tut_try_rank, rank ?: 0, total, "%.2f".format((best.q ?: 0.0) - (a.cands[i].q ?: 0.0)))
      },
      style = MaterialTheme.typography.titleMedium, color = if (i == 0) GOOD else MaterialTheme.colorScheme.onSurface,
    )
    if (i != 0) {
      Body(stringResource(R.string.yours), dim = true, small = true)
      ThreeRowsExample(mine, cats, 34.dp)
    }
    Body(stringResource(R.string.tut_try_best_rows), dim = true, small = true)
    ThreeRowsExample(best.arrangement, best.info.categories, 34.dp)
    // The words on why fit the best the network finds for this hand under the usual rules: the aces in front.
    if (best.arrangement[0].count { it >= 48 } == 2) Body(stringResource(R.string.tut_try_why))
    OutlinedButton(onClick = { scope.launch { reset() } }) {
      Icon(Icons.Outlined.Replay, null, Modifier.padding(end = 6.dp))
      Text(stringResource(R.string.tut_try_again))
    }
  }
}
