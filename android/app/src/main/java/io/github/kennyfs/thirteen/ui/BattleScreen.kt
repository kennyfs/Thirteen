package io.github.kennyfs.thirteen.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
      vm.detail != null && room != null -> DetailPage(vm, room)
      room == null -> EntryPage(vm)
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
  Row(Modifier.fillMaxSize().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(stringResource(R.string.battle_waiting), style = MaterialTheme.typography.titleSmall)
      val eval = vm.arranger.rowsEval
      for (r in 0 until 3) RowCards(vm.arranger.rows[r], eval?.categories?.get(r) ?: 0, width = 34.dp)
    }
    Standings(vm, room, Modifier.weight(1f).verticalScroll(rememberScrollState()), marks = true)
  }
}

/** One play's rows, small, in a line. */
@Composable
private fun PlayRows(rows: List<List<Int>>) {
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { for (r in rows) RowCards(r, 0, width = 20.dp) }
}

@Composable
private fun OutcomeTable(vm: BattleViewModel, room: RoomView, o: HandOutcome) {
  val names = room.players.associateBy { it.id }
  val losses = o.losses
  val order = o.plays.indices.let { idx -> if (losses != null) idx.sortedBy { losses[it] } else idx.toList() }
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    o.best?.let {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.battle_best_rows), Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        PlayRows(it)
      }
    }
    for (i in order) {
      val play = o.plays[i]
      val p = names[play.player]
      Row(
        Modifier.background(if (play.player == vm.me) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          (p?.name ?: "?") + if (play.timedOut) stringResource(R.string.battle_timed_out) else "", Modifier.width(110.dp),
          style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        PlayRows(play.rows)
        val value = o.losses?.get(i)?.let { "−" + fmtLoss(it) } ?: o.score?.result?.get(i)?.let(::signed) ?: ""
        val good = o.losses?.get(i)?.let { it < 1e-9 } ?: ((o.score?.result?.get(i) ?: 0.0) >= 0)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = if (good) GOOD else BAD)
      }
    }
  }
}

@Composable
private fun ReviewPage(vm: BattleViewModel, room: RoomView) {
  val o = vm.outcomes.lastOrNull { it.index == room.hand }
  val me = room.players.firstOrNull { it.id == vm.me }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(handHeader(vm, room), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
    Button(onClick = vm::ready, enabled = me?.ready != true) { Text(stringResource(R.string.battle_next)) }
  }
  Row(Modifier.fillMaxSize().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Column(Modifier.weight(1.4f).verticalScroll(rememberScrollState())) {
      if (o != null) OutcomeTable(vm, room, o)
    }
    Standings(vm, room, Modifier.weight(1f).verticalScroll(rememberScrollState()), marks = true)
  }
}

@Composable
private fun SummaryPage(vm: BattleViewModel, room: RoomView, app: AppViewModel) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(R.string.battle_over) + " · " + modeName(room.settings.mode), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
    Button(onClick = vm::dismiss) { Text(stringResource(R.string.battle_done)) }
  }
  Text(settingsLine(room, app), style = MaterialTheme.typography.bodySmall)
  Row(Modifier.fillMaxSize().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Column(Modifier.weight(1.4f)) {
      Text(stringResource(R.string.battle_hand_list), style = MaterialTheme.typography.labelLarge)
      LazyColumn {
        items(vm.outcomes) { o ->
          val i = o.plays.indexOfFirst { it.player == vm.me }
          Row(
            Modifier.fillMaxWidth().clickable(enabled = i >= 0) { vm.openDetail(o.index) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text("${o.index + 1}", Modifier.width(24.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            if (i >= 0) {
              Row(horizontalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                for (c in o.plays[i].rows.flatten().sortedDescending()) CardFace(c, 20.dp)
              }
              val value = o.losses?.get(i)?.let { "−" + fmtLoss(it) } ?: o.score?.result?.get(i)?.let(::signed) ?: ""
              val good = o.losses?.get(i)?.let { it < 1e-9 } ?: ((o.score?.result?.get(i) ?: 0.0) >= 0)
              Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = if (good) GOOD else BAD)
              if (o.plays[i].timedOut) Text(stringResource(R.string.battle_timed_out), style = MaterialTheme.typography.bodySmall)
            }
          }
          HorizontalDivider()
        }
      }
    }
    Standings(vm, room, Modifier.weight(1f).verticalScroll(rememberScrollState()))
  }
}

@Composable
private fun DetailPage(vm: BattleViewModel, room: RoomView) {
  val o = vm.outcomes.firstOrNull { it.index == vm.detail } ?: return
  val mine = o.plays.firstOrNull { it.player == vm.me }
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    TextButton(onClick = vm::closeDetail) { Text(stringResource(R.string.battle_back)) }
    Text(stringResource(R.string.hand_n, o.index + 1) + "  ", style = MaterialTheme.typography.labelLarge)
    mine?.let { for (c in it.rows.flatten().sortedDescending()) CardFace(c, SMALL_CARD) }
  }
  // At a table the others had other hands: their rows and the score are shown as they were.
  if (room.settings.mode == Mode.GAME) OutcomeTable(vm, room, o)
  val a = vm.detailAnalysis
  if (a == null) LinearProgressIndicator(Modifier.fillMaxWidth())
  else CandidateList(a, mine?.rows, expandBest = true, legacyNote = null, tags = vm.detailTags)
}
