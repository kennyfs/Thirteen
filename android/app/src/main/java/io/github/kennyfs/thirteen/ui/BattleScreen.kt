package io.github.kennyfs.thirteen.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.kennyfs.thirteen.AppViewModel
import io.github.kennyfs.thirteen.R
import io.github.kennyfs.thirteen.Rules
import io.github.kennyfs.thirteen.battle.BattleViewModel
import io.github.kennyfs.thirteen.battle.Via
import io.github.kennyfs.thirteen.protocol.HandOutcome
import io.github.kennyfs.thirteen.protocol.Limits
import io.github.kennyfs.thirteen.protocol.Mode
import io.github.kennyfs.thirteen.protocol.Phase
import io.github.kennyfs.thirteen.protocol.PlayerView
import io.github.kennyfs.thirteen.protocol.RoomView
import kotlinx.coroutines.delay

/** Battles: the puzzle contest first, then the table game; online or with phones nearby. */
@Composable
fun BattleScreen(vm: BattleViewModel, app: AppViewModel) {
  val room = vm.room
  Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
    when {
      vm.detail != null && room != null -> if (vm.analyzed != null) PlayerAnalysisPage(vm, room) else DetailPage(vm, room)
      room == null -> EntryPage(vm)
      (vm.finished || room.phase == Phase.OVER) && !vm.lastSeen && vm.outcomes.isNotEmpty() -> LastHandPage(vm, room)
      vm.finished || room.phase == Phase.OVER -> SummaryPage(vm, room, app)
      room.phase == Phase.LOBBY -> LobbyPage(vm, room, app)
      room.phase == Phase.PLAYING && room.players.firstOrNull { it.id == vm.me }?.submitted != true -> PlayingPage(vm, room)
      room.phase == Phase.PLAYING -> WaitingPage(vm, room)
      else -> ReviewPage(vm, room)
    }
  }
}

private fun nearbyPermissions(): Array<String> = when {
  Build.VERSION.SDK_INT >= 33 -> arrayOf(
    Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.NEARBY_WIFI_DEVICES,
  )
  Build.VERSION.SDK_INT >= 31 -> arrayOf(
    Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.ACCESS_FINE_LOCATION,
  )
  else -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

@Composable
private fun modeName(mode: Mode) = stringResource(if (mode == Mode.PUZZLE) R.string.battle_puzzle else R.string.battle_game)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryPage(vm: BattleViewModel) {
  val context = LocalContext.current
  // What to do once the nearby permissions are granted.
  var then by remember { mutableStateOf<(() -> Unit)?>(null) }
  val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
    if (granted.values.all { it }) then?.invoke() else vm.error = context.getString(R.string.battle_permissions)
    then = null
  }
  fun nearby(action: () -> Unit) {
    val missing = nearbyPermissions().filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
    if (missing.isEmpty()) action() else {
      then = action
      permissions.launch(missing.toTypedArray())
    }
  }
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      OutlinedTextField(vm.name, { vm.name = it.take(Limits.NAME_LENGTH) }, Modifier.widthIn(max = 220.dp), label = { Text(stringResource(R.string.battle_name)) }, singleLine = true)
      FilterChip(vm.via == Via.ONLINE, { vm.via = Via.ONLINE; vm.stopSearch() }, { Text(stringResource(R.string.battle_online)) }, Modifier.align(Alignment.CenterVertically))
      FilterChip(vm.via == Via.NEARBY, { vm.via = Via.NEARBY }, { Text(stringResource(R.string.battle_nearby)) }, Modifier.align(Alignment.CenterVertically))
      if (vm.via == Via.ONLINE)
        OutlinedTextField(
          vm.server, { vm.server = it.trim() }, Modifier.widthIn(max = 320.dp), label = { Text(stringResource(R.string.battle_server)) },
          placeholder = { Text(stringResource(R.string.battle_server_hint)) }, singleLine = true,
        )
    }
    if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    for (mode in listOf(Mode.PUZZLE, Mode.GAME)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
          Text(modeName(mode), style = MaterialTheme.typography.titleSmall)
          Text(
            stringResource(if (mode == Mode.PUZZLE) R.string.battle_puzzle_info else R.string.battle_game_info),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Button(onClick = { if (vm.via == Via.NEARBY) nearby { vm.create(mode) } else vm.create(mode) }, enabled = !vm.busy) {
          Text(stringResource(R.string.battle_create))
        }
      }
    }
    HorizontalDivider()
    if (vm.via == Via.NEARBY) {
      val found = vm.found
      if (found == null) OutlinedButton(onClick = { nearby { vm.search() } }) { Text(stringResource(R.string.battle_search)) }
      else {
        Text(stringResource(R.string.battle_searching), style = MaterialTheme.typography.bodySmall)
        for ((id, name) in found)
          Row(
            Modifier.fillMaxWidth().background(if (vm.picked == id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
              .clickable { vm.pick(id) }.padding(6.dp),
          ) { Text(name) }
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        vm.pin, { vm.pin = it.filter(Char::isDigit).take(6) }, Modifier.width(140.dp), label = { Text(stringResource(R.string.battle_pin)) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
      )
      Button(
        onClick = { vm.join() },
        enabled = !vm.busy && vm.pin.length == 6 && (vm.via == Via.ONLINE || vm.picked != null),
      ) { Text(stringResource(R.string.battle_join)) }
    }
  }
}

/** Time left in the room's current phase, counting down from what the room last said. */
@Composable
private fun remaining(room: RoomView, at: Long): Long? {
  val left = room.remainingMs ?: return null
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(room, at) {
    while (true) {
      now = System.currentTimeMillis()
      delay(500)
    }
  }
  return (left - (now - at)).coerceAtLeast(0)
}

private fun clock(ms: Long): String = (ms / 1000).let { "%d:%02d".format(it / 60, it % 60) }

private fun fmtLoss(x: Double) = "%.3f".format(x)

/** Players by standing: the least loss first in a puzzle, the most points at a table. */
private fun standings(room: RoomView): List<PlayerView> =
  if (room.settings.mode == Mode.PUZZLE) room.players.sortedBy { it.total } else room.players.sortedByDescending { it.total }

@Composable
private fun totalText(room: RoomView, p: PlayerView) =
  if (room.settings.mode == Mode.PUZZLE) stringResource(R.string.battle_total_loss, fmtLoss(p.total)) else signed(p.total)

@Composable
private fun playerName(vm: BattleViewModel, room: RoomView, p: PlayerView): String = buildString {
  append(p.name)
  if (p.id == vm.me) append(stringResource(R.string.battle_you_mark))
  if (p.id == room.host) append(" · " + stringResource(R.string.battle_host))
  // A network's seat is already named "AI n".
  if (!p.bot && !p.connected) append(" · " + stringResource(R.string.battle_away))
}

@Composable
private fun Standings(vm: BattleViewModel, room: RoomView, modifier: Modifier = Modifier, marks: Boolean = false) {
  Column(modifier) {
    for ((i, p) in standings(room).withIndex())
      Row(
        Modifier.fillMaxWidth().background(if (p.id == vm.me) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(stringResource(R.string.battle_rank, i + 1), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        Text(playerName(vm, room, p), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (marks) Text(if (p.submitted || (room.phase == Phase.REVIEW && p.ready)) "✓" else "…", style = MaterialTheme.typography.bodySmall)
        Text(totalText(room, p), style = MaterialTheme.typography.bodySmall)
      }
  }
}

@Composable
private fun settingsLine(room: RoomView, app: AppViewModel): String {
  val s = room.settings
  val parts = mutableListOf(
    stringResource(R.string.battle_hands_n, s.hands),
    stringResource(R.string.rules_label, app.rulesName(Rules.values(room.rules))),
    stringResource(R.string.battle_time) + " " + if (s.timeLimit == 0) stringResource(R.string.battle_no_limit) else stringResource(R.string.battle_seconds, s.timeLimit),
  )
  if (s.hard) parts += stringResource(R.string.battle_hard)
  return parts.joinToString(" · ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LobbyPage(vm: BattleViewModel, room: RoomView, app: AppViewModel) {
  val isHost = room.host == vm.me
  val context = LocalContext.current
  val s = room.settings
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(modeName(s.mode), style = MaterialTheme.typography.titleMedium)
      Text(stringResource(R.string.battle_room_pin, room.pin), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.weight(1f))
      OutlinedButton(onClick = vm::leave) { Text(stringResource(R.string.battle_leave)) }
      if (isHost) {
        val enough = if (s.mode == Mode.PUZZLE) room.players.count { !it.bot } >= 2 else true
        Button(onClick = { vm.startGame() }, enabled = enough) { Text(stringResource(R.string.battle_start)) }
      }
    }
    if (vm.hosting) Text(stringResource(R.string.battle_nearby_host_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(settingsLine(room, app), style = MaterialTheme.typography.bodyMedium)
    if (isHost) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Picker(
        stringResource(R.string.battle_hands), (listOf(1, 3, 5, 10, 15, 20, 30) + s.hands).distinct().sorted().map { it.toString() to it.toString() },
        s.hands.toString(),
      ) { vm.changeSettings(s.copy(hands = it.toInt())) }
      Picker(
        stringResource(R.string.battle_time),
        Limits.TIME_LIMITS.map { it.toString() to if (it == 0) context.getString(R.string.battle_no_limit) else context.getString(R.string.battle_seconds, it) },
        s.timeLimit.toString(),
      ) { vm.changeSettings(s.copy(timeLimit = it.toInt())) }
      if (s.mode == Mode.PUZZLE) Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.battle_hard), style = MaterialTheme.typography.bodyMedium)
        Switch(s.hard, { vm.changeSettings(s.copy(hard = it)) }, Modifier.padding(horizontal = 4.dp))
        InfoButton(stringResource(R.string.battle_hard_info))
      }
      TextButton(onClick = { vm.changeSettings(s.copy(rules = vm.rulesFromPage())) }, Modifier.align(Alignment.CenterVertically)) {
        Text(stringResource(R.string.battle_rules_page))
      }
    }
    HorizontalDivider()
    Text(stringResource(R.string.battle_players, room.players.size), style = MaterialTheme.typography.titleSmall)
    for (p in room.players) Text(playerName(vm, room, p), style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun handHeader(vm: BattleViewModel, room: RoomView): String {
  val parts = mutableListOf(stringResource(R.string.battle_hand_of, room.hand + 1, room.settings.hands))
  remaining(room, vm.roomAt)?.let { parts += stringResource(R.string.battle_left_time, clock(it)) }
  val humans = room.players.filter { !it.bot }
  if (room.phase == Phase.PLAYING) parts += stringResource(R.string.battle_set_count, humans.count { it.submitted }, humans.size)
  else parts += stringResource(R.string.battle_ready_count, humans.count { it.ready }, humans.size)
  if (room.settings.mode == Mode.PUZZLE) {
    val rank = standings(room).indexOfFirst { it.id == vm.me } + 1
    if (rank > 0 && room.hand > 0) parts += stringResource(R.string.battle_your_rank, rank)
  }
  return parts.joinToString(" · ")
}

@Composable
private fun PlayingPage(vm: BattleViewModel, room: RoomView) {
  Text(handHeader(vm, room), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
  Arranging(vm.arranger, Rules.values(room.rules)["wheel"]?.toInt() ?: 0, vm.submitting) { vm.submit() }
}

@Composable
private fun WaitingPage(vm: BattleViewModel, room: RoomView) {
  Text(handHeader(vm, room), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
  Row(Modifier.fillMaxSize().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    BoxWithConstraints(Modifier.weight(1.3f)) {
      val card = ((maxWidth - CARD_GAP * 4) / 5).coerceAtMost(56.dp)
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.battle_waiting), style = MaterialTheme.typography.titleSmall)
        val eval = vm.arranger.rowsEval
        for (r in 0 until 3) RowCards(vm.arranger.rows[r], eval?.categories?.get(r) ?: 0, width = card)
      }
    }
    Standings(vm, room, Modifier.weight(1f).verticalScroll(rememberScrollState()), marks = true)
  }
}

private val ROW_GAP = 8.dp

/** The card width at which 13 cards in three rows fill `width`, up to `max`. */
private fun cardFor(width: Dp, max: Dp = 48.dp): Dp = ((width - CARD_GAP * 10 - ROW_GAP * 2) / 13).coerceIn(14.dp, max)

/** One play's three rows as the analysis shows them: the cards that make each hand first, tinted by its category. */
@Composable
private fun PlayRows(vm: BattleViewModel, rows: List<List<Int>>, card: Dp) {
  val cats = vm.categoriesOf(rows)
  Row(horizontalArrangement = Arrangement.spacedBy(ROW_GAP)) {
    for (r in 0 until 3) RowCards(rows[r], cats?.get(r) ?: 0, width = card)
  }
}

/** A hand's value: a puzzle's loss ("0.144", nothing lost "0"), or a table's points ("+3"). */
private fun handValue(o: HandOutcome, i: Int): String =
  o.losses?.get(i)?.let { if (it < 0.0005) "0" else fmtLoss(it) } ?: o.score?.result?.get(i)?.let(::signed) ?: ""

@Composable
private fun handColor(room: RoomView, o: HandOutcome, i: Int): Color {
  val loss = o.losses?.get(i)
  return when {
    loss != null && loss < 0.0005 -> GOOD
    loss != null -> if (loss >= 0.5 * Rules.notableQGap(Rules.values(room.rules))) BAD else MaterialTheme.colorScheme.onSurface
    (o.score?.result?.get(i) ?: 0.0) > 0 -> GOOD
    (o.score?.result?.get(i) ?: 0.0) < 0 -> BAD
    else -> MaterialTheme.colorScheme.onSurface
  }
}

/** Rank by standing, equal totals sharing a rank. */
private fun rankOf(room: RoomView, p: PlayerView): Int {
  val puzzle = room.settings.mode == Mode.PUZZLE
  return 1 + room.players.count { if (puzzle) it.total < p.total - 1e-9 else it.total > p.total + 1e-9 }
}

/**
 * A closed hand for everyone: the network's best (a puzzle), then each player by standing with their rows, what this
 * hand gave them and their total. The cards take the width the names and numbers leave.
 */
@Composable
private fun ResultTable(vm: BattleViewModel, room: RoomView, o: HandOutcome, maxCard: Dp = 48.dp, onPlayer: ((Int) -> Unit)? = null) {
  val puzzle = room.settings.mode == Mode.PUZZLE
  val nameStyle = MaterialTheme.typography.bodyMedium
  val numStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
  val measurer = rememberTextMeasurer()
  val density = LocalDensity.current
  val best = stringResource(R.string.battle_best_rows)
  val names = room.players.map { it.name } + if (puzzle) listOf(best) else emptyList()
  val nameNeeded = with(density) { names.maxOf { measurer.measure(it, nameStyle).size.width }.toDp() } + 8.dp
  val handHead = stringResource(if (puzzle) R.string.battle_col_hand_loss else R.string.battle_col_hand)
  val totalHead = stringResource(if (puzzle) R.string.battle_col_total_loss else R.string.battle_col_total)
  val numWidth = with(density) { listOf(handHead, totalHead, "00.000").maxOf { measurer.measure(it, numStyle).size.width }.toDp() } + 8.dp
  val rankWidth = textRoom(22)
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val nameWidth = minOf(nameNeeded, maxWidth * 0.2f)
    val card = cardFor(maxWidth - rankWidth - nameWidth - numWidth * 2 - 8.dp * 4, maxCard)
    @Composable
    fun Line(rank: String, name: String, note: String?, rows: List<List<Int>>?, hand: String, handColor: Color, total: String, me: Boolean, onClick: (() -> Unit)? = null) {
      Row(
        Modifier.fillMaxWidth().background(if (me) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
          .let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(rank, Modifier.width(rankWidth), style = numStyle, textAlign = TextAlign.End)
        Column(Modifier.width(nameWidth)) {
          Text(name, style = nameStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
          if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (rows != null) PlayRows(vm, rows, card) else Spacer(Modifier.width(card * 13 + CARD_GAP * 10 + ROW_GAP * 2))
        Text(hand, Modifier.width(numWidth), style = numStyle, color = handColor, textAlign = TextAlign.End, maxLines = 1)
        Text(total, Modifier.width(numWidth), style = numStyle, textAlign = TextAlign.End, maxLines = 1)
      }
    }
    Column {
      Line("", "", null, null, handHead, MaterialTheme.colorScheme.onSurfaceVariant, totalHead, false)
      o.best?.let { Line("", best, null, it, "", MaterialTheme.colorScheme.onSurface, "", false) }
      for (p in standings(room)) {
        val i = o.plays.indexOfFirst { it.player == p.id }
        if (i < 0) continue
        Line(
          // The player's own line is highlighted: no "(you)" to cut the name short.
          stringResource(R.string.battle_rank, rankOf(room, p)), p.name,
          if (o.plays[i].timedOut) stringResource(R.string.battle_timed_out).trim() else null,
          o.plays[i].rows, handValue(o, i), handColor(room, o, i), if (puzzle) fmtLoss(p.total) else signed(p.total), p.id == vm.me,
          onPlayer?.let { f -> { f(p.id) } },
        )
      }
    }
  }
}

/** A hand's table as large as both the width and the height allow, so that a table of four fits without scrolling. */
@Composable
private fun HandTable(vm: BattleViewModel, room: RoomView, o: HandOutcome?, onPlayer: ((Int) -> Unit)? = null) {
  BoxWithConstraints(Modifier.fillMaxSize().padding(top = 4.dp)) {
    val lines = room.players.size + (if (o?.best != null) 1 else 0)
    val maxCard = (((maxHeight - textRoom(24)) / lines - 6.dp) / 1.4f).coerceIn(20.dp, 48.dp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
      if (o != null) ResultTable(vm, room, o, maxCard, onPlayer)
    }
  }
}

@Composable
private fun ReviewPage(vm: BattleViewModel, room: RoomView) {
  val me = room.players.firstOrNull { it.id == vm.me }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(handHeader(vm, room), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
    Button(onClick = vm::ready, enabled = me?.ready != true) { Text(stringResource(R.string.battle_next)) }
  }
  HandTable(vm, room, vm.outcomes.lastOrNull { it.index == room.hand })
}

/** The last hand's table, which the room skips (the game is over): shown before the final standings. */
@Composable
private fun LastHandPage(vm: BattleViewModel, room: RoomView) {
  val o = vm.outcomes.last()
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      stringResource(R.string.battle_hand_of, o.index + 1, room.settings.hands), Modifier.weight(1f), maxLines = 1,
      style = MaterialTheme.typography.labelLarge,
    )
    Button(onClick = vm::toSummary) { Text(stringResource(R.string.battle_to_summary)) }
  }
  HandTable(vm, room, o)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryPage(vm: BattleViewModel, room: RoomView, app: AppViewModel) {
  val puzzle = room.settings.mode == Mode.PUZZLE
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(R.string.battle_over) + " · " + modeName(room.settings.mode), style = MaterialTheme.typography.titleSmall)
    Text(settingsLine(room, app), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Button(onClick = vm::dismiss) { Text(stringResource(R.string.battle_done)) }
  }
  // The standings, one entry per player, across the width.
  FlowRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    for (p in standings(room)) {
      val mine = p.id == vm.me
      Text(
        "${stringResource(R.string.battle_rank, rankOf(room, p))} ${p.name}  " + (if (puzzle) stringResource(R.string.battle_total_loss, fmtLoss(p.total)) else signed(p.total)),
        Modifier.background(if (mine) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(6.dp))
          .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium, fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
      )
    }
  }
  val numStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
  val handHead = stringResource(if (puzzle) R.string.battle_col_hand_loss else R.string.battle_col_hand)
  val rankHead = stringResource(R.string.battle_col_hand_rank)
  val measurer = rememberTextMeasurer()
  val numWidth = with(LocalDensity.current) { listOf(handHead, rankHead, "00.000").maxOf { measurer.measure(it, numStyle).size.width }.toDp() } + 8.dp
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val indexWidth = textRoom(24)
    // A little smaller than a hand's result, so that more hands show at once.
    val card = cardFor(maxWidth - indexWidth - numWidth * 2 - 8.dp * 3, 40.dp)
    Column {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.battle_hand_list), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Text(handHead, Modifier.width(numWidth), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End)
        Text(rankHead, Modifier.width(numWidth), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End)
      }
      LazyColumn {
        items(vm.outcomes) { o ->
          val i = o.plays.indexOfFirst { it.player == vm.me }
          Row(
            Modifier.fillMaxWidth().clickable(enabled = i >= 0) { vm.openDetail(o.index) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text("${o.index + 1}", Modifier.width(indexWidth), style = numStyle, textAlign = TextAlign.End)
            if (i >= 0) {
              Box(Modifier.weight(1f)) { PlayRows(vm, o.plays[i].rows, card) }
              Column(Modifier.width(numWidth), horizontalAlignment = Alignment.End) {
                Text(handValue(o, i), style = numStyle, color = handColor(room, o, i))
                if (o.plays[i].timedOut)
                  Text(stringResource(R.string.battle_timed_out).trim(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              // The player's place in this hand alone, equal results sharing it.
              val mine = o.losses?.get(i) ?: -(o.score?.result?.get(i) ?: 0.0)
              val place = 1 + o.plays.indices.count { j -> (o.losses?.get(j) ?: -(o.score?.result?.get(j) ?: 0.0)) < mine - 1e-9 }
              Text(stringResource(R.string.battle_hand_rank, place, o.plays.size), Modifier.width(numWidth), style = numStyle, textAlign = TextAlign.End)
            }
          }
          HorizontalDivider()
        }
      }
    }
  }
}

/** A hand from the results: its table; a player's line opens the analysis of their hand. */
@Composable
private fun DetailPage(vm: BattleViewModel, room: RoomView) {
  val o = vm.outcomes.firstOrNull { it.index == vm.detail } ?: return
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    TextButton(onClick = vm::closeDetail) { Text(stringResource(R.string.battle_back)) }
    Text(stringResource(R.string.battle_hand_of, o.index + 1, room.settings.hands), style = MaterialTheme.typography.labelLarge)
    Text(stringResource(R.string.battle_tap_player), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
  HandTable(vm, room, o) { vm.openAnalysis(it) }
}

/** One player's hand of a hand from the results, analyzed, their play pinned. */
@Composable
private fun PlayerAnalysisPage(vm: BattleViewModel, room: RoomView) {
  val o = vm.outcomes.firstOrNull { it.index == vm.detail } ?: return
  val play = o.plays.firstOrNull { it.player == vm.analyzed } ?: return
  val name = room.players.firstOrNull { it.id == play.player }?.name ?: "?"
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    TextButton(onClick = vm::closeAnalysis) { Text(stringResource(R.string.battle_back)) }
    Text("${stringResource(R.string.battle_hand_of, o.index + 1, room.settings.hands)} · $name  ", style = MaterialTheme.typography.labelLarge)
    for (c in play.rows.flatten().sortedDescending()) CardFace(c, SMALL_CARD)
  }
  val a = vm.detailAnalysis
  if (a == null) LinearProgressIndicator(Modifier.fillMaxWidth())
  else CandidateList(
    a, play.rows, expandBest = true, legacyNote = null, tags = vm.detailTags,
    mineLabel = if (play.player == vm.me) null else name,
  )
}
