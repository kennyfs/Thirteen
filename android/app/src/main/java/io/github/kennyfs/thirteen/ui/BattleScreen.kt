package io.github.kennyfs.thirteen.ui

import io.github.kennyfs.thirteen.protocol.BattleRecord
import io.github.kennyfs.thirteen.battle.BattleReview
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.rememberCoroutineScope
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.kennyfs.thirteen.AppViewModel
import io.github.kennyfs.thirteen.R
import io.github.kennyfs.thirteen.Rules
import io.github.kennyfs.thirteen.battle.BattleViewModel
import io.github.kennyfs.thirteen.battle.Entry
import io.github.kennyfs.thirteen.battle.Via
import io.github.kennyfs.thirteen.battle.shownName
import io.github.kennyfs.thirteen.protocol.HandOutcome
import io.github.kennyfs.thirteen.protocol.Limits
import io.github.kennyfs.thirteen.protocol.Mode
import io.github.kennyfs.thirteen.protocol.Phase
import io.github.kennyfs.thirteen.protocol.PlayerView
import io.github.kennyfs.thirteen.protocol.RoomView
import io.github.kennyfs.thirteen.protocol.RuleChoice
import io.github.kennyfs.thirteen.ui.theme.LocalTable
import kotlinx.coroutines.delay

/** Battles: the puzzle contest first, then the table game; online or with phones nearby. */
@Composable
fun BattleScreen(vm: BattleViewModel, app: AppViewModel) {
  val room = vm.room
  // Back: one page up within the battle, where there is one.
  val over = room != null && (vm.finished || room.phase == Phase.OVER)
  var confirmLeave by rememberSaveable { mutableStateOf(false) }
  val back: (() -> Unit)? = when {
    vm.review.detail != null && room != null -> if (vm.review.analyzed != null && !vm.review.fromReview) vm.review::closeAnalysis else vm.review::closeDetail
    room == null -> if (vm.entry != null) ({ vm.chooseEntry(null) }) else null
    over && !vm.lastSeen && vm.outcomes.isNotEmpty() -> vm::toSummary
    over -> vm::dismiss
    // In the room, with no page to go back to: leave it, once confirmed.
    else -> ({ confirmLeave = true })
  }
  BackHandler(back != null) { back?.invoke() }
  if (confirmLeave && room != null && vm.inRoom) AlertDialog(
    onDismissRequest = { confirmLeave = false },
    confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leave() }) { Text(stringResource(R.string.battle_leave)) } },
    dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel)) } },
    title = { Text(stringResource(R.string.battle_leave_title)) },
    text = {
      Text(
        stringResource(if (room.phase == Phase.LOBBY) R.string.battle_leave_lobby else R.string.battle_leave_game),
        style = MaterialTheme.typography.bodyLarge,
      )
    },
  )
  Column(Modifier.fillMaxSize()) {
    when {
      vm.review.detail != null && room != null -> if (vm.review.analyzed != null) PlayerAnalysisPage(vm.review, room) else DetailPage(vm.review, room)
      room == null -> EntryPage(vm)
      (vm.finished || room.phase == Phase.OVER) && !vm.lastSeen && vm.outcomes.isNotEmpty() -> ReviewPage(vm, room, last = true)
      vm.finished || room.phase == Phase.OVER -> SummaryPage(vm.review, room, app, vm::dismiss)
      room.phase == Phase.LOBBY -> LobbyPage(vm, room, app)
      room.phase == Phase.PLAYING && room.players.firstOrNull { it.id == vm.me }?.submitted != true -> PlayingPage(vm, room)
      room.phase == Phase.PLAYING -> WaitingPage(vm, room)
      else -> ReviewPage(vm, room)
    }
  }
}

/**
 * The permissions nearby rooms need. From Android 17 an app that targets it also needs local network access, or Nearby
 * Connections fails with 8: INTERNAL_ERROR once it tries the Wi-Fi LAN.
 */
private fun nearbyPermissions(): Array<String> = when {
  Build.VERSION.SDK_INT >= 37 -> arrayOf(
    Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_LOCAL_NETWORK,
  )
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

/** Entering a battle: the name, how to connect, and three large choices: a puzzle contest, a table game, or a room to join. */
@Composable
private fun EntryPage(vm: BattleViewModel) {
  val context = LocalContext.current
  // What to do once the nearby permissions are granted.
  var then by remember { mutableStateOf<(() -> Unit)?>(null) }
  var editServer by rememberSaveable { mutableStateOf(false) }
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
  fun create(mode: Mode) = if (vm.via == Via.NEARBY) nearby { vm.create(mode) } else vm.create(mode)
  Column(Modifier.fillMaxSize()) {
    TitleBar(stringResource(R.string.nav_battle))
    // Its room kept while not busy, so the page does not move.
    Box(Modifier.fillMaxWidth().height(4.dp)) { if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth()) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val wide = maxWidth > maxHeight || maxWidth >= 700.dp
      val compact = maxHeight < 440.dp
      val scroll = Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp).padding(bottom = 12.dp)
      val connection = @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
          OutlinedTextField(
            vm.name, { vm.name = it.take(Limits.NAME_LENGTH) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.battle_name)) },
            placeholder = { Text(vm.defaultName) },
            singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
          )
          Text(stringResource(R.string.battle_how), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          ToggleGroup(
            listOf(Via.ONLINE to stringResource(R.string.battle_online), Via.NEARBY to stringResource(R.string.battle_nearby)), vm.via, vm::chooseVia,
            Modifier.fillMaxWidth(),
          )
          if (vm.via == Via.ONLINE) {
            if (editServer) OutlinedTextField(
              vm.server, { vm.server = it.trim() }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.battle_server)) },
              placeholder = { Text(stringResource(R.string.battle_server_hint)) }, singleLine = true,
            )
            else Row(verticalAlignment = Alignment.CenterVertically) {
              Text(stringResource(R.string.battle_server_is, vm.server), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              TextButton(onClick = { editServer = true }) { Text(stringResource(R.string.change)) }
            }
          } else Text(stringResource(R.string.battle_nearby_info), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      val choices = @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
          val (fill, onFill) = tileColors()
          ChoiceTile(stringResource(R.string.battle_new_puzzle), stringResource(R.string.battle_puzzle_info), fill, onFill, compact = compact, enabled = !vm.busy) { create(Mode.PUZZLE) }
          ChoiceTile(
            stringResource(R.string.battle_new_game), stringResource(R.string.battle_game_info), fill, onFill, compact = compact,
            enabled = !vm.busy,
          ) { create(Mode.GAME) }
          JoinTile(vm, compact = compact) { nearby(it) }
        }
      }
      if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(0.95f).then(scroll)) { connection() }
        Column(Modifier.weight(1.25f).then(scroll)) { choices() }
      }
      else Column(Modifier.fillMaxSize().then(scroll).readable(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        connection()
        choices()
      }
    }
  }
}

/**
 * What a large choice is filled with: the felt, or with the minimal colors (whose felt is the page's own surface, so a
 * tile of it would not look like a button) a container.
 */
@Composable
private fun tileColors(): Pair<Color, Color> {
  val t = LocalTable.current
  val colors = MaterialTheme.colorScheme
  return if (t.felt == colors.surface) colors.secondaryContainer to colors.onSecondaryContainer else t.felt to t.onFelt
}

/** A large choice: a title in large type and a line about it. */
@Composable
private fun ChoiceTile(title: String, info: String, container: Color, content: Color, compact: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
  Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(16.dp), color = container, contentColor = content, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
      Text(info, style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.88f))
    }
  }
}

/** Joining a room by its number; nearby, the rooms found around first. */
@Composable
private fun JoinTile(vm: BattleViewModel, compact: Boolean = false, nearby: (() -> Unit) -> Unit) {
  val colors = MaterialTheme.colorScheme
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(2.dp, colors.outlineVariant, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp),
    verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
  ) {
    Text(stringResource(R.string.battle_join_room), style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.battle_join_room_info), style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    if (vm.via == Via.NEARBY) {
      val found = vm.found
      if (vm.entry != Entry.JOIN || found == null)
        OutlinedButton(onClick = { vm.chooseEntry(Entry.JOIN); nearby { vm.search() } }, Modifier.heightIn(min = 52.dp)) { Text(stringResource(R.string.battle_search)) }
      else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          if (found.isEmpty()) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
          Text(
            stringResource(if (found.isEmpty()) R.string.battle_no_rooms else R.string.battle_searching), Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
          )
          TextButton(onClick = { nearby { vm.search() } }) { Text(stringResource(R.string.battle_search_again)) }
        }
        for ((id, name) in found)
          Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
              .background(if (vm.picked == id) colors.secondaryContainer else colors.surfaceContainer).clickable { vm.pick(id) }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) { Text(shownName(name), style = MaterialTheme.typography.bodyLarge) }
      }
    }
    if (vm.via == Via.ONLINE || vm.picked != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      OutlinedTextField(
        vm.pin, { vm.pin = it.filter(Char::isDigit).take(6) }, Modifier.widthIn(max = 220.dp).weight(1f),
        label = { Text(stringResource(R.string.battle_pin)) }, singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 6.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { if (vm.pin.length == 6) vm.join() }),
      )
      Button(
        onClick = { vm.join() }, Modifier.heightIn(min = BIG_BUTTON),
        enabled = !vm.busy && vm.pin.length == 6 && (vm.via == Via.ONLINE || vm.picked != null),
      ) { Text(stringResource(R.string.battle_join), style = MaterialTheme.typography.titleMedium) }
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

/** What else to say about a player: you, the host, away. */
@Composable
private fun playerNotes(vm: BattleViewModel, room: RoomView, p: PlayerView): String = buildList {
  if (p.id == vm.me) add(stringResource(R.string.you))
  if (p.id == room.host) add(stringResource(R.string.battle_host))
  // A network's seat is already named "AI n".
  if (!p.bot && !p.connected) add(stringResource(R.string.battle_away))
}.joinToString(stringResource(R.string.list_sep))

@Composable
private fun Standings(vm: BattleViewModel, room: RoomView, modifier: Modifier = Modifier, marks: Boolean = false) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(stringResource(R.string.battle_standings), style = MaterialTheme.typography.titleMedium)
    for ((i, p) in standings(room).withIndex())
      Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
          .background(if (p.id == vm.me) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("${i + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f)) {
          Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
          playerNotes(vm, room, p).takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (marks) {
          val done = p.submitted || (room.phase == Phase.REVIEW && p.ready)
          Text(stringResource(if (done) R.string.battle_done_mark else R.string.battle_thinking), style = MaterialTheme.typography.bodyMedium, color = if (done) GOOD else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(totalText(room, p), style = MaterialTheme.typography.bodyLarge)
      }
  }
}

/** The room: its number in large type, the players, and the settings (which the host changes). */
@Composable
private fun LobbyPage(vm: BattleViewModel, room: RoomView, app: AppViewModel) {
  val isHost = room.host == vm.me
  val context = LocalContext.current
  val s = room.settings
  Column(Modifier.fillMaxSize()) {
    TitleBar(modeName(s.mode), onBack = vm::leave) {
      OutlinedButton(onClick = vm::leave, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.battle_leave)) }
    }
    BoxWithConstraints(Modifier.weight(1f)) {
      val wide = maxWidth >= 720.dp
      val code = @Composable {
        val (fill, onFill) = tileColors()
        val dim = onFill.copy(alpha = 0.8f)
        Column(
          Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(fill).padding(horizontal = 18.dp, vertical = 14.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(stringResource(R.string.battle_room_number), style = MaterialTheme.typography.bodyLarge, color = dim)
          Text(
            room.pin.chunked(3).joinToString(" "), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
            color = onFill, letterSpacing = 4.sp,
          )
          Text(stringResource(R.string.battle_room_number_info), style = MaterialTheme.typography.bodyMedium, color = dim, textAlign = TextAlign.Center)
          if (vm.hosting) Text(stringResource(R.string.battle_nearby_host_note), style = MaterialTheme.typography.bodySmall, color = dim, textAlign = TextAlign.Center)
        }
      }
      val players = @Composable {
        Column {
          SectionHeader(stringResource(R.string.battle_players, room.players.size))
          for (p in room.players)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              Avatar(p.name)
              Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(playerNotes(vm, room, p), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
      }
      val settings = @Composable {
        Column {
          SectionHeader(stringResource(R.string.battle_settings))
          val ruleName = app.rulesName(Rules.values(room.rules))
          if (isHost) {
            SelectRow(
              stringResource(R.string.battle_hands), (listOf(1, 3, 5, 10, 15, 20, 30) + s.hands).distinct().sorted().map { it to context.getString(R.string.battle_hands_n, it) },
              s.hands, { vm.changeSettings(s.copy(hands = it)) },
            )
            SelectRow(
              stringResource(R.string.battle_time),
              Limits.TIME_LIMITS.map { it to if (it == 0) context.getString(R.string.battle_no_limit) else context.getString(R.string.battle_seconds, it) },
              s.timeLimit, { vm.changeSettings(s.copy(timeLimit = it)) },
            )
            val names = stringArrayResource(R.array.preset_names)
            val current = if (s.rules.overrides.isEmpty()) Rules.PRESETS.indexOf(s.rules.preset) else -2
            val options = Rules.OFFERED.map { Rules.PRESETS.indexOf(it) }.let { if (current >= 0 && current !in it) it + current else it }.map { it to names[it] } + listOf(-1 to stringResource(R.string.battle_rules_page_named, app.rulesName(app.ruleValues))) +
              (if (current == -2) listOf(-2 to ruleName) else emptyList())
            SelectRow(stringResource(R.string.nav_rules), options, current, { i ->
              when {
                i >= 0 -> vm.changeSettings(s.copy(rules = RuleChoice(Rules.PRESETS[i])))
                i == -1 -> vm.changeSettings(s.copy(rules = vm.rulesFromPage()))
              }
            })
          } else {
            SettingRow(stringResource(R.string.battle_hands), stringResource(R.string.battle_hands_n, s.hands))
            SettingRow(stringResource(R.string.battle_time), if (s.timeLimit == 0) stringResource(R.string.battle_no_limit) else stringResource(R.string.battle_seconds, s.timeLimit))
            SettingRow(stringResource(R.string.nav_rules), ruleName)
          }
        }
      }
      if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { code(); players() }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { settings() }
      } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).readable()) {
        code()
        players()
        settings()
      }
    }
    Box(Modifier.fillMaxWidth().padding(16.dp)) {
      if (isHost) Button(onClick = { vm.startGame() }, Modifier.fillMaxWidth().heightIn(min = BIG_BUTTON).readable(), shape = RoundedCornerShape(18.dp)) {
        Text(stringResource(R.string.battle_start_mode, modeName(s.mode)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }
      else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        Text(stringResource(R.string.battle_wait_host), style = MaterialTheme.typography.bodyLarge)
      }
    }
  }
}

/** The hand's place in the game, the time left, who has set it, and the player's standing: chips under the title. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HandHeader(
  vm: BattleViewModel, room: RoomView, info: @Composable () -> Unit = {}, hint: String? = null, index: Int = room.hand, clock: Boolean = true,
  actions: @Composable RowScope.() -> Unit = {},
) {
  // The facts wrap in their own room; the buttons stay at the right end.
  Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
  FlowRow(
    Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp),
    itemVerticalAlignment = Alignment.CenterVertically,
  ) {
    Text(stringResource(R.string.battle_hand_of, index + 1, room.settings.hands), style = MaterialTheme.typography.titleLarge)
    if (clock) remaining(room, vm.roomAt)?.let { left ->
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Outlined.Timer, null, tint = if (left < 15_000) BAD else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(clock(left), style = MaterialTheme.typography.titleMedium, color = if (left < 15_000) BAD else MaterialTheme.colorScheme.onSurface)
      }
    }
    // A hint in the counts' place: the table under the header stays where it is.
    if (hint != null) {
      Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
      return@FlowRow
    }
    if (!clock) { info(); return@FlowRow }
    val humans = room.players.filter { !it.bot }
    Text(
      if (room.phase == Phase.PLAYING) stringResource(R.string.battle_set_count, humans.count { it.submitted }, humans.size)
      else stringResource(R.string.battle_ready_count, humans.count { it.ready }, humans.size),
      style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (room.settings.mode == Mode.PUZZLE) {
      val rank = standings(room).indexOfFirst { it.id == vm.me } + 1
      if (rank > 0 && room.hand > 0) Text(stringResource(R.string.battle_your_rank, rank), style = MaterialTheme.typography.bodyLarge)
    }
    info()
  }
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
  }
}

@Composable
private fun PlayingPage(vm: BattleViewModel, room: RoomView) {
  HandHeader(vm, room)
  Arranging(vm.arranger, Rules.values(room.rules)["wheel"]?.toInt() ?: 0, vm.submitting, stringResource(R.string.battle_submit), confirmShort = stringResource(R.string.battle_submit_short),
    maxCard = LocalCardSizes.current.battle) { vm.submit() }
}

@Composable
private fun WaitingPage(vm: BattleViewModel, room: RoomView) {
  HandHeader(vm, room)
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val wide = maxWidth > maxHeight
    val mine = @Composable { m: Modifier ->
      val t = LocalTable.current
      Surface(m, shape = MaterialTheme.shapes.large, color = t.felt, contentColor = t.onFelt) {
        BoxWithConstraints(Modifier.padding(14.dp)) {
          val card = ((maxWidth - CARD_GAP * 4) / 5).coerceAtMost(LocalCardSizes.current.battle)
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp, color = t.onFelt)
              Text(stringResource(R.string.battle_waiting), style = MaterialTheme.typography.titleMedium)
            }
            val eval = vm.arranger.rowsEval
            for (r in 0 until 3) RowCards(vm.arranger.rows[r], eval?.categories?.get(r) ?: 0, width = card)
          }
        }
      }
    }
    if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      mine(Modifier.weight(1f))
      Standings(vm, room, Modifier.weight(1f).verticalScroll(rememberScrollState()), marks = true)
    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      mine(Modifier.fillMaxWidth())
      Standings(vm, room, marks = true)
    }
  }
}

/** One play's three rows as the analysis shows them: the cards that make each hand first, tinted by its category. */
@Composable
private fun PlayRows(r: BattleReview, rows: List<List<Int>>, modifier: Modifier = Modifier) {
  ThreeRows(rows, r.categoriesOf(rows), modifier)
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

/** What a table game's hand gave the player, as the practice screen shows it; nothing in a puzzle. */
@Composable
private fun MyHandResult(r: BattleReview, o: HandOutcome?) {
  val score = o?.score ?: return
  val i = o.plays.indexOfFirst { it.player == r.me }
  if (i < 0) return
  val x = score.result[i]
  Text(
    stringResource(R.string.battle_this_hand, signed(x)), style = MaterialTheme.typography.headlineSmall,
    color = if (x > 0) GOOD else if (x < 0) BAD else MaterialTheme.colorScheme.onSurface,
  )
}

/** Rank by standing, equal totals sharing a rank. */
private fun rankOf(room: RoomView, p: PlayerView): Int {
  val puzzle = room.settings.mode == Mode.PUZZLE
  return 1 + room.players.count { if (puzzle) it.total < p.total - 1e-9 else it.total > p.total + 1e-9 }
}

/**
 * A closed hand for everyone: the network's best (a puzzle), then each player by standing with their rows, what this
 * hand gave them and their total. The cards take the width the names and numbers leave; on a narrow screen each
 * player's cards go under their line.
 */
@Composable
private fun ResultTable(r: BattleReview, room: RoomView, o: HandOutcome, onPlayer: ((Int) -> Unit)? = null) {
  val puzzle = room.settings.mode == Mode.PUZZLE
  val nameStyle = MaterialTheme.typography.bodyLarge
  val numStyle = MaterialTheme.typography.titleMedium
  val measurer = rememberTextMeasurer()
  val density = LocalDensity.current
  val best = stringResource(R.string.battle_best_rows)
  val names = room.players.map { it.name } + if (puzzle) listOf(best) else emptyList()
  val nameNeeded = with(density) { names.maxOf { measurer.measure(it, nameStyle).size.width }.toDp() } + 8.dp
  val handHead = stringResource(if (puzzle) R.string.battle_col_hand_loss else R.string.battle_col_hand)
  val totalHead = stringResource(if (puzzle) R.string.battle_col_total_loss else R.string.battle_col_total)
  val numWidth = with(density) { listOf(handHead, totalHead, "00.000").maxOf { measurer.measure(it, numStyle).size.width }.toDp() } + 8.dp
  val rankWidth = textRoom(24)
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val nameWidth = minOf(nameNeeded, maxWidth * 0.22f)
    // The cards on the player's line when they are the usual size there, else under it, across the whole width.
    val stacked = lineCard(maxWidth - rankWidth - nameWidth - numWidth * 2 - 8.dp * 4) < LocalCardSizes.current.other - 0.5.dp
    @Composable
    fun Line(rank: String, name: String, note: String?, rows: List<List<Int>>?, hand: String, handColor: Color, total: String, me: Boolean, onClick: (() -> Unit)? = null) {
      Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (me) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
          .let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 6.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(rank, Modifier.width(rankWidth), style = numStyle, textAlign = TextAlign.End)
          Column(if (stacked) Modifier.weight(1f) else Modifier.width(nameWidth)) {
            Text(name, style = nameStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
          }
          if (!stacked) {
            if (rows != null) PlayRows(r, rows, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
          }
          Text(hand, Modifier.width(numWidth), style = numStyle, color = handColor, textAlign = TextAlign.End, maxLines = 1)
          Text(total, Modifier.width(numWidth), style = numStyle, textAlign = TextAlign.End, maxLines = 1)
        }
        if (stacked && rows != null) PlayRows(r, rows, Modifier.fillMaxWidth())
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
          o.plays[i].rows, handValue(o, i), handColor(room, o, i), if (puzzle) fmtLoss(p.total) else signed(p.total), p.id == r.me,
          onPlayer?.let { f -> { f(p.id) } },
        )
      }
    }
  }
}

/**
 * A hand's table as large as both the width and the height allow, so that a table of four fits without scrolling. A
 * table game's hand is shown as on the practice screen, from the player's seat, once its full score is in.
 */
@Composable
private fun HandTable(r: BattleReview, room: RoomView, o: HandOutcome?, modifier: Modifier = Modifier, onPlayer: ((Int) -> Unit)? = null) {
  // The hand once its page has all it needs, all at once; a skeleton of it before.
  if (o == null || !r.isReady(o.index)) {
    if (room.settings.mode == Mode.PUZZLE) PanelSkeleton(modifier = modifier, rows = maxOf(2, room.players.size))
    else SeatsSkeleton(room.players.size.coerceAtLeast(2), modifier.padding(start = 8.dp, end = 12.dp, bottom = 10.dp))
    return
  }
  val table = r.tables[o.index]
  if (table != null) {
    val (order, score) = table
    val plays = order.map { o.plays[it] }
    val byId = room.players.associateBy { it.id }
    // Each player's total up to this hand.
    val totals = plays.map { p ->
      r.outcomes.filter { it.index <= o.index }.sumOf { h -> h.plays.indexOfFirst { it.player == p.player }.let { i -> if (i < 0) 0.0 else h.score?.result?.get(i) ?: 0.0 } }
    }
    val you = stringResource(R.string.you)
    val timedOut = stringResource(R.string.battle_timed_out).trim()
    val t = LocalTable.current
    Surface(modifier.fillMaxSize().padding(start = 8.dp, end = 12.dp, bottom = 10.dp), shape = MaterialTheme.shapes.large, color = t.felt, contentColor = t.onFelt) {
      SeatResults(
        plays.map { it.rows }, score, Rules.values(room.rules)["wheel"]?.toInt() ?: 0,
        plays.map { if (it.player == r.me) you else byId[it.player]?.name ?: "?" },
        plays.map { if (it.timedOut) timedOut else null }, totals, Modifier.padding(8.dp),
        onPlayer?.let { f -> { s: Int -> f(plays[s].player) } },
      )
    }
    return
  }
  BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
      ResultTable(r, room, o, onPlayer)
    }
  }
}

/**
 * A hand's result while the room waits for everyone to go on: the table, an analysis button and the next button at the
 * right. A puzzle's analysis is of the one hand everyone set; at a table the button turns on an analysis mode, where a
 * tap on a player opens the analysis of their hand (a hint in the header says so). The last hand's result, which the
 * room skips (the game is over), is the same page, its button to the final standings.
 */
@Composable
private fun ReviewPage(vm: BattleViewModel, room: RoomView, last: Boolean = false) {
  val me = room.players.firstOrNull { it.id == vm.me }
  val arrived = if (last) vm.outcomes.lastOrNull() else vm.outcomes.lastOrNull { it.index == room.hand }
  val index = arrived?.index ?: room.hand
  // The hand once its page has all it needs, all at once.
  val o = arrived?.takeIf { vm.review.isReady(it.index) }
  val puzzle = room.settings.mode == Mode.PUZZLE
  var analysisMode by rememberSaveable(index) { mutableStateOf(false) }
  HandHeader(
    vm, room, info = { MyHandResult(vm.review, o) }, hint = if (analysisMode && !puzzle) stringResource(R.string.battle_tap_player) else null,
    index = index, clock = !last,
  ) {
    val label = stringResource(R.string.battle_analyze)
    if (o != null && (!puzzle || o.plays.any { it.player == vm.me })) {
      val content: @Composable RowScope.() -> Unit = {
        Icon(Icons.Outlined.Analytics, null, Modifier.size(20.dp))
        Text(label, Modifier.padding(start = 4.dp), maxLines = 1, softWrap = false)
      }
      val m = Modifier.heightIn(min = BIG_BUTTON)
      val pad = PaddingValues(horizontal = 12.dp)
      if (puzzle) OutlinedButton(onClick = { vm.analyzeHand(index, vm.me ?: return@OutlinedButton) }, m, shape = RoundedCornerShape(18.dp), contentPadding = pad, content = content)
      else if (analysisMode) FilledTonalButton(onClick = { analysisMode = false }, m, shape = RoundedCornerShape(18.dp), contentPadding = pad, content = content)
      else OutlinedButton(onClick = { analysisMode = true }, m, shape = RoundedCornerShape(18.dp), contentPadding = pad, content = content)
    }
    if (last) Button(onClick = vm::toSummary, Modifier.heightIn(min = BIG_BUTTON), shape = RoundedCornerShape(18.dp)) {
      FitText(stringResource(R.string.battle_to_summary), stringResource(R.string.battle_to_summary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
    else Button(onClick = vm::ready, Modifier.heightIn(min = BIG_BUTTON), enabled = me?.ready != true, shape = RoundedCornerShape(18.dp)) {
      FitText(stringResource(R.string.battle_next), stringResource(R.string.battle_next_short), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
  }
  HandTable(vm.review, room, o, onPlayer = if (analysisMode && !puzzle) { p -> vm.analyzeHand(index, p) } else null)
}

/** The final standings, then every hand with the player's rows, what it gave them and their place in it. */
@Composable
fun SummaryPage(
  r: BattleReview, room: RoomView, app: AppViewModel, onDone: (() -> Unit)?, title: String? = null, onBack: (() -> Unit)? = null,
  actions: @Composable RowScope.() -> Unit = {},
) {
  val puzzle = room.settings.mode == Mode.PUZZLE
  Column(Modifier.fillMaxSize()) {
    TitleBar(title ?: (stringResource(R.string.battle_over) + " · " + modeName(room.settings.mode)), onBack) {
      actions()
      if (onDone != null) Button(onClick = onDone, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.battle_done)) }
    }
    val numStyle = MaterialTheme.typography.titleMedium
    val handHead = stringResource(if (puzzle) R.string.battle_col_hand_loss else R.string.battle_col_hand)
    val rankHead = stringResource(R.string.battle_col_hand_rank)
    val measurer = rememberTextMeasurer()
    val numWidth = with(LocalDensity.current) { listOf(handHead, rankHead, "00.000").maxOf { measurer.measure(it, numStyle).size.width }.toDp() } + 8.dp
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
      val indexWidth = textRoom(26)
      val stacked = lineCard(maxWidth - indexWidth - numWidth * 2 - 8.dp * 3) < LocalCardSizes.current.other - 0.5.dp
      LazyColumn {
        item {
          Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (p in standings(room)) {
              val mine = p.id == r.me
              val place = rankOf(room, p)
              Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
                  .background(if (mine) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Text(stringResource(R.string.battle_place, place), style = if (place == 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
                Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal)
                Text(if (puzzle) stringResource(R.string.battle_total_loss, fmtLoss(p.total)) else signed(p.total), style = MaterialTheme.typography.titleMedium)
              }
            }
            Text(settingsLine(room, app), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        item {
          Row(Modifier.padding(top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.battle_hand_list), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(handHead, Modifier.width(numWidth), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End)
            Text(rankHead, Modifier.width(numWidth), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End)
          }
        }
        items(r.outcomes) { o ->
          val i = o.plays.indexOfFirst { it.player == r.me }
          Column(Modifier.fillMaxWidth().clickable(enabled = i >= 0) { r.openDetail(o.index) }.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("${o.index + 1}", Modifier.width(indexWidth), style = numStyle, textAlign = TextAlign.End)
              if (i >= 0) {
                if (!stacked) PlayRows(r, o.plays[i].rows, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                Column(Modifier.width(numWidth), horizontalAlignment = Alignment.End) {
                  Text(handValue(o, i), style = numStyle, color = handColor(room, o, i))
                  if (o.plays[i].timedOut)
                    Text(stringResource(R.string.battle_timed_out).trim(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // The player's place in this hand alone, equal results sharing it.
                val mine = o.losses?.get(i) ?: -(o.score?.result?.get(i) ?: 0.0)
                val place = 1 + o.plays.indices.count { j -> (o.losses?.get(j) ?: -(o.score?.result?.get(j) ?: 0.0)) < mine - 1e-9 }
                Text(stringResource(R.string.battle_hand_rank, place, o.plays.size), Modifier.width(numWidth), style = numStyle, textAlign = TextAlign.End)
              }
            }
            if (stacked && i >= 0) PlayRows(r, o.plays[i].rows, Modifier.fillMaxWidth().padding(top = 4.dp))
          }
          HorizontalDivider()
        }
      }
    }
  }
}

@Composable
private fun settingsLine(room: RoomView, app: AppViewModel): String {
  val s = room.settings
  val parts = mutableListOf(
    stringResource(R.string.battle_hands_n, s.hands),
    app.rulesName(Rules.values(room.rules)),
    stringResource(R.string.battle_time) + " " + if (s.timeLimit == 0) stringResource(R.string.battle_no_limit) else stringResource(R.string.battle_seconds, s.timeLimit),
  )
  return parts.joinToString(stringResource(R.string.clause_sep))
}

/** A hand from the results: its table; a player's line opens the analysis of their hand. */
@Composable
fun DetailPage(r: BattleReview, room: RoomView, actions: @Composable RowScope.(HandOutcome) -> Unit = {}) {
  val o = r.outcomes.firstOrNull { it.index == r.detail } ?: return
  TitleBar(stringResource(R.string.battle_hand_of, o.index + 1, room.settings.hands), onBack = r::closeDetail) {
    if (r.isReady(o.index)) MyHandResult(r, o)
    actions(o)
  }
  Text(
    stringResource(R.string.battle_tap_player), Modifier.padding(horizontal = 16.dp, vertical = 2.dp), style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  HandTable(r, room, o) { r.openAnalysis(it) }
}

/** One player's hand of a hand from the results, analyzed, their play pinned. */
@Composable
fun PlayerAnalysisPage(r: BattleReview, room: RoomView) {
  val o = r.outcomes.firstOrNull { it.index == r.detail } ?: return
  val play = o.plays.firstOrNull { it.player == r.analyzed } ?: return
  val name = room.players.firstOrNull { it.id == play.player }?.name ?: "?"
  TitleBar("${stringResource(R.string.battle_hand_of, o.index + 1, room.settings.hands)} · $name", onBack = if (r.fromReview) r::closeDetail else r::closeAnalysis)
  val a = r.detailAnalysis
  if (a == null) PanelSkeleton(stringResource(R.string.analyzing))
  else CandidateList(a, play.rows, legacyNote = null, tags = r.detailTags, mineLabel = if (play.player == r.me) null else name)
}

/**
 * A battle from the records: its standings and hands as at its end, a hand's table, a player's analysis. A star saves
 * the battle, and in a hand's table, the player's own hand of it (as a record).
 */
@Composable
fun BattleRecordPage(app: AppViewModel, record: BattleRecord, onBack: () -> Unit) {
  val scope = rememberCoroutineScope()
  val r = remember(record.id) {
    BattleReview(scope, { app.settings.models().first().path }) { app.error = it }.apply {
      me = record.me
      rules = record.rules
      names = record.players.associate { it.id to it.name }
      add(record.outcomes)
    }
  }
  val room = remember(record) { record.view() }
  BackHandler(r.detail != null) { if (r.analyzed != null) r.closeAnalysis() else r.closeDetail() }
  val star = @Composable { saved: Boolean, label: Int, onClick: () -> Unit ->
    IconButton(onClick = onClick) {
      Icon(
        if (saved) Icons.Filled.Star else Icons.Outlined.StarBorder, stringResource(label),
        tint = if (saved) Color(0xFFE0A100) else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
  Column(Modifier.fillMaxSize()) {
    when {
      r.detail != null && r.analyzed != null -> PlayerAnalysisPage(r, room)
      r.detail != null -> DetailPage(r, room) { o ->
        // The player's own hand of it, as a record, to save.
        val rec = remember(o.index, app.recordCount) { app.battleHandRecord(record.id, o.index) }
        if (rec != null) {
          val saved = rec.optString("id") in app.favoriteIds
          star(saved, if (saved) R.string.favorite_remove else R.string.favorite_add) { app.toggleFavorite(rec) }
        }
      }
      else -> {
        val saved = record.id in app.favoriteBattleIds
        SummaryPage(
          r, room, app, onDone = null, title = localTime(java.time.Instant.ofEpochMilli(record.startedAt).toString()) + " · " + modeName(record.settings.mode),
          onBack = onBack,
        ) { star(saved, if (saved) R.string.favorite_battle_remove else R.string.favorite_battle_add) { app.toggleFavoriteBattle(record) } }
      }
    }
  }
}
