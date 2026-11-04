package io.github.kennyfs.thirteen.battle

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kennyfs.thirteen.Arranger
import io.github.kennyfs.thirteen.Engine
import io.github.kennyfs.thirteen.R
import io.github.kennyfs.thirteen.BattleStore
import io.github.kennyfs.thirteen.RecordStore
import io.github.kennyfs.thirteen.Settings
import io.github.kennyfs.thirteen.protocol.BattleRecord
import io.github.kennyfs.thirteen.protocol.Backup
import io.github.kennyfs.thirteen.protocol.RoomSnapshot
import io.github.kennyfs.thirteen.protocol.Attest
import io.github.kennyfs.thirteen.protocol.ChangeSettings
import io.github.kennyfs.thirteen.protocol.ClientMsg
import io.github.kennyfs.thirteen.protocol.Closed
import io.github.kennyfs.thirteen.protocol.Create
import io.github.kennyfs.thirteen.protocol.Deal
import io.github.kennyfs.thirteen.protocol.EngineClient
import io.github.kennyfs.thirteen.protocol.Error
import io.github.kennyfs.thirteen.protocol.ErrorCode
import io.github.kennyfs.thirteen.protocol.HandOutcome
import io.github.kennyfs.thirteen.protocol.HandResult
import io.github.kennyfs.thirteen.protocol.Hello
import io.github.kennyfs.thirteen.protocol.History
import io.github.kennyfs.thirteen.protocol.Hub
import io.github.kennyfs.thirteen.protocol.HubLimits
import io.github.kennyfs.thirteen.protocol.Join
import io.github.kennyfs.thirteen.protocol.Joined
import io.github.kennyfs.thirteen.protocol.Leave
import io.github.kennyfs.thirteen.protocol.Mode
import io.github.kennyfs.thirteen.protocol.PROTOCOL_VERSION
import io.github.kennyfs.thirteen.protocol.Phase
import io.github.kennyfs.thirteen.protocol.Ready
import io.github.kennyfs.thirteen.protocol.Resume
import io.github.kennyfs.thirteen.protocol.RoomSettings
import io.github.kennyfs.thirteen.protocol.RoomView
import io.github.kennyfs.thirteen.protocol.RuleChoice
import io.github.kennyfs.thirteen.protocol.ServerMsg
import io.github.kennyfs.thirteen.protocol.Start
import io.github.kennyfs.thirteen.protocol.State
import io.github.kennyfs.thirteen.protocol.Submit
import io.github.kennyfs.thirteen.protocol.Welcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Via { ONLINE, NEARBY }

/** The project's public server (see docs/battle.md). */
const val DEFAULT_SERVER = "thirteen.1li.tw"

/** The entry page's two ways into a room. */
enum class Entry { CREATE, JOIN }

/**
 * Battles: a room on a server or hosted on a phone nearby (this one or another), its hands and their results. The
 * room (Hub and Room in protocol/) decides everything; this keeps what it has said and sends the player's choices.
 */
class BattleViewModel(app: Application) : AndroidViewModel(app), LinkListener {
  private val prefs = app.getSharedPreferences("battle", Context.MODE_PRIVATE)
  private val settings = Settings(app)
  private val records = RecordStore(app.filesDir)
  private val battles = BattleStore(app.filesDir)

  private fun str(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)

  var name by mutableStateOf(prefs.getString("name", "") ?: "")
  /** The name of a player who gives none: "Player" and a number of their own, so two of them can be told apart. */
  val defaultName: String = prefs.getString("default_name", null) ?: ("Player" + kotlin.random.Random.nextInt(1000, 100000)).also {
    prefs.edit().putString("default_name", it).apply()
  }
  private val playerName: String get() = name.ifBlank { defaultName }
  // An empty address (as saved before there was a default) means the default.
  var server by mutableStateOf(prefs.getString("server", null)?.ifBlank { null } ?: DEFAULT_SERVER)
  var via by mutableStateOf(Via.ONLINE); private set
  /** Which way into a room the entry page shows; null for the choice between them. */
  var entry by mutableStateOf<Entry?>(null); private set
  var pin by mutableStateOf("")
  var error by mutableStateOf<String?>(null)
  /** Connecting or waiting for the room's answer. */
  var busy by mutableStateOf(false); private set

  var room by mutableStateOf<RoomView?>(null); private set
  /** When `room` arrived, for its countdown. */
  var roomAt by mutableStateOf(0L); private set
  var me by mutableStateOf(-1); private set
  var deal by mutableStateOf<Deal?>(null); private set
  /** The closed hands and the pages that look at them, shared with the records' battles. */
  val review = BattleReview(viewModelScope, { settings.models().first().path }) { error = it }
  val outcomes: List<HandOutcome> get() = review.outcomes
  var submitting by mutableStateOf(false); private set
  /** The room is gone but its results stay to look at. */
  var finished by mutableStateOf(false); private set
  /** In a room still going (the lobby too): the app keeps to the battle until the player leaves it. */
  val inRoom: Boolean get() = room != null && !finished && room?.phase != Phase.OVER
  /** This phone hosts the room (nearby). */
  var hosting by mutableStateOf(false); private set
  /** Rooms advertised nearby, endpoint id to name, while searching. */
  var found by mutableStateOf<Map<String, String>?>(null); private set

  /** The table of the last hand has been shown (it comes before the final standings, whatever the room says). */
  var lastSeen by mutableStateOf(false); private set

  val arranger = Arranger(viewModelScope, { error = it }) { row, free ->
    str(R.string.row_room, getApplication<Application>().resources.getStringArray(R.array.row_names)[row], free)
  }

  private var link: Link? = null
  private var joined: Joined? = null
  /** Sent once the room says welcome: Create, Join or Resume. */
  private var pending: ClientMsg? = null
  private var hub: Hub? = null
  private var host: NearbyHost? = null
  private var finder: NearbyFinder? = null
  /** This player is the room's backup (a room on a phone): the latest snapshot, to carry on the room if it goes. */
  private var backup: RoomSnapshot? = null
  /** While the room is found again: a phone that carries it on (endpoint, epoch). */
  private var mover: NearbyFinder? = null
  private var moved: Pair<String, Int>? = null
  /** The nearby endpoint of the room, to reconnect to. */
  private var endpoint: String? = null
  private var reconnect: Job? = null

  private fun savePrefs() {
    prefs.edit().putString("name", name).putString("server", server).apply()
  }

  /** New rooms start with the rules page's rules and the last hands and time chosen. */
  fun defaults(mode: Mode) = RoomSettings(
    mode, prefs.getInt("hands", 10), settings.rules.let { RuleChoice(it.preset, it.overrides) }, prefs.getInt("time", 0),
  )

  // ---- Into a room ----

  fun create(mode: Mode) = start(Create(defaults(mode)))

  fun join() {
    if (!Regex("\\d{6}").matches(pin)) {
      error = str(R.string.battle_pin_bad)
      return
    }
    start(Join(pin))
  }

  private fun start(msg: ClientMsg) {
    savePrefs()
    clear()
    pending = msg
    when (via) {
      Via.ONLINE -> {
        val url = serverUrl(server) ?: run {
          error = str(R.string.battle_server_bad)
          return
        }
        open(WsLink(url, this))
      }
      Via.NEARBY -> if (msg is Create) {
        val h = Hub(EngineClient(settings.models().first().path, Engine::raw), viewModelScope, HubLimits.NEARBY)
        hub = h
        hosting = true
        host = NearbyHost(getApplication(), h, viewModelScope, advertisedName(msg.settings.mode)) { error = str(R.string.battle_nearby_failed, it) }
        open(LocalLink(h, viewModelScope, this))
      } else {
        val e = picked ?: return
        endpoint = e
        open(NearbyLink(getApplication(), e, playerName, this))
      }
    }
  }

  private fun advertisedName(mode: Mode) =
    "${playerName} · ${str(if (mode == Mode.PUZZLE) R.string.battle_puzzle else R.string.battle_game)}"

  private fun open(l: Link) {
    busy = true
    link = l
    l.send(Hello(PROTOCOL_VERSION, playerName))
  }

  fun chooseVia(v: Via) {
    via = v
    stopSearch()
  }

  /** Into a way of the entry page, or back to the choice (null). Joining nearby looks for rooms anew each time. */
  fun chooseEntry(e: Entry?) {
    entry = e
    stopSearch()
  }

  /**
   * Looks for rooms hosted nearby, from an empty list: what an earlier search found may be long gone, and Nearby
   * does not always say when a room stops advertising.
   */
  fun search() {
    finder?.stop()
    found = emptyMap()
    finder = NearbyFinder(getApplication(), { found = it }) { error = str(R.string.battle_nearby_failed, it) }
  }

  fun stopSearch() {
    finder?.stop()
    finder = null
    found = null
    picked = null
  }

  fun pick(endpointId: String) {
    picked = endpointId
  }

  /** The room picked among those found nearby, to join. */
  var picked by mutableStateOf<String?>(null); private set

  // ---- In the room ----

  fun changeSettings(s: RoomSettings) {
    prefs.edit().putInt("hands", s.hands).putInt("time", s.timeLimit).apply()
    link?.send(ChangeSettings(s))
  }

  /** The rules page's current rules, for the room. */
  fun rulesFromPage(): RuleChoice = settings.rules.let { RuleChoice(it.preset, it.overrides) }

  fun startGame() = link?.send(Start)

  fun submit() {
    val d = deal ?: return
    viewModelScope.launch {
      val eval = arranger.eval()
      if (eval.valid != true) {
        error = str(if (eval.valid == null) R.string.fill_rows_first else R.string.fouled)
        return@launch
      }
      submitting = true
      link?.send(Submit(d.hand, arranger.rows))
    }
  }

  fun ready() {
    room?.let { link?.send(Ready(it.hand)) }
  }

  /** Leaves the room (for good) and the results. */
  fun leave() {
    saveBattle(left = true)
    link?.send(Leave)
    // Hosting: the room hears the Leave and sends its backup the last snapshot before the phone stops it.
    val h = host
    val l = link
    if (h != null) {
      host = null
      link = null
      viewModelScope.launch {
        delay(1500)
        l?.close()
        h.stop()
      }
    }
    clear()
    chooseEntry(null)
  }

  /** Back from the results of a finished room. */
  fun dismiss() {
    clear()
    chooseEntry(null)
  }

  private fun clear() {
    reconnect?.cancel()
    mover?.stop()
    mover = null
    moved = null
    backup = null
    link?.close()
    link = null
    host?.stop()
    host = null
    hub = null
    hosting = false
    joined = null
    pending = null
    room = null
    me = -1
    deal = null
    review.clear()
    battleId = null
    battleStart = 0L
    lastSeen = false
    submitting = false
    finished = false
    busy = false
    review.closeDetail()
  }

  override fun onCleared() {
    clear()
    finder?.stop()
  }

  // ---- What the room says ----

  override fun onMessage(link: Link, msg: ServerMsg) {
    if (link !== this.link) return
    when (msg) {
      is Welcome -> {
        val next = pending
        val nonce = msg.nonce
        if (nonce == null || !Integrity.enabled) next?.let(link::send)
        // The server checks clients: the token first (if one comes), then the room.
        else viewModelScope.launch {
          Integrity.token(getApplication(), nonce)?.let { link.send(Attest(it)) }
          if (link === this@BattleViewModel.link) next?.let(link::send)
        }
      }
      is Joined -> {
        joined = msg
        me = msg.you
        review.me = msg.you
        busy = false
        // In a room: no more looking for others (the endpoint to reconnect to is kept).
        stopSearch()
        mover?.stop()
        mover = null
        moved = null
        // A resume tells everything again.
        pending = Resume(msg.room, msg.token)
      }
      is State -> {
        val previous = room
        room = msg.room
        review.rules = msg.room.rules
        review.names = msg.room.players.associate { it.id to it.name }
        if (msg.room.phase != Phase.LOBBY && battleId == null) {
          battleId = java.util.UUID.randomUUID().toString()
          battleStart = System.currentTimeMillis()
        }
        // The totals change with each hand: the battle as it stands.
        if (previous?.hand != msg.room.hand || previous?.phase != msg.room.phase) saveBattle()
        roomAt = System.currentTimeMillis()
        // An ended room takes no one in, nor back: no longer shown to phones looking for rooms.
        if (msg.room.phase == Phase.OVER) host?.stopAdvertising()
        if (msg.room.players.firstOrNull { it.id == me }?.submitted == true) submitting = false
        if (previous?.hand != msg.room.hand && msg.room.phase == Phase.PLAYING) {
          submitting = false
          if (review.fromReview) review.closeDetail()
        }
      }
      is Deal -> {
        deal = msg
        submitting = false
        arranger.reset(msg.cards, room?.rules ?: "family", msg.mine ?: Arranger.EMPTY)
      }
      is HandResult -> addOutcomes(listOf(msg.outcome))
      is Backup -> backup = msg.snapshot
      is History -> addOutcomes(msg.outcomes)
      is Error -> {
        busy = false
        submitting = false
        error = errorText(msg)
        if (joined == null && msg.code in setOf(ErrorCode.VERSION, ErrorCode.NO_ROOM, ErrorCode.ROOM_FULL, ErrorCode.STARTED, ErrorCode.TOO_MANY, ErrorCode.BUSY, ErrorCode.RATE, ErrorCode.UNVERIFIED)) {
          link.close()
          this.link = null
        }
      }
      is Closed -> {
        if (msg.reason == "resumed elsewhere" || msg.reason == "left") return
        roomGone(if (room?.phase == Phase.OVER) null else str(R.string.battle_closed))
      }
    }
  }

  override fun onDown(link: Link, reason: String) {
    if (link !== this.link) return
    this.link = null
    val j = joined
    if (j == null || room?.phase == Phase.OVER) {
      if (j == null) {
        busy = false
        error = str(R.string.battle_no_connection, reason)
      }
      return roomGone(null)
    }
    // A dropped connection in a game: the seat is kept, so try to take it back. Nearby, the hosting phone may be
    // gone: its backup carries on the room after a try at the phone, and the others look for it there too.
    val r = room
    val snap = backup
    if (via == Via.NEARBY && r != null && !hosting) {
      if (r.backup == me && snap != null) {
        reconnect = viewModelScope.launch {
          busy = true
          val l = endpoint?.let { NearbyLink(getApplication(), it, playerName, this@BattleViewModel) }
          if (l != null) {
            pending = Resume(j.room, j.token)
            open(l)
            delay(4000)
            if (this@BattleViewModel.link === l && !busy) return@launch
            if (this@BattleViewModel.link === l) {
              l.close()
              this@BattleViewModel.link = null
            }
          }
          takeOver(snap, j, r)
        }
        return
      }
      lookForMove(j.room, r.epoch)
    }
    reconnect = viewModelScope.launch {
      busy = true
      for (attempt in 0 until 6) {
        delay(1000L shl attempt)
        pending = Resume(j.room, j.token)
        val l = when (via) {
          Via.ONLINE -> serverUrl(server)?.let { WsLink(it, this@BattleViewModel) }
          // The room's new phone once found, else the old one.
          Via.NEARBY -> (moved?.first ?: endpoint)?.let { e ->
            if (moved != null) endpoint = e
            NearbyLink(getApplication(), e, playerName, this@BattleViewModel)
          }
        } ?: break
        open(l)
        // Up within a while, or the next attempt.
        delay(8000)
        if (this@BattleViewModel.link === l && !busy) return@launch
        if (this@BattleViewModel.link === l) {
          l.close()
          this@BattleViewModel.link = null
        }
      }
      busy = false
      roomGone(str(R.string.battle_lost))
    }
  }

  /** This phone carries on the room from its snapshot: it hosts it now, and takes its own seat back. */
  private fun takeOver(snap: RoomSnapshot, j: Joined, r: RoomView) {
    viewModelScope.launch {
      try {
        val h = Hub(EngineClient(settings.models().first().path, Engine::raw), viewModelScope, HubLimits.NEARBY)
        h.restore(snap, outcomes)
        hub = h
        hosting = true
        backup = null
        host = NearbyHost(getApplication(), h, viewModelScope, advertised(advertisedName(r.settings.mode), snap.id, snap.epoch)) {
          error = str(R.string.battle_nearby_failed, it)
        }
        pending = Resume(j.room, j.token)
        open(LocalLink(h, viewModelScope, this@BattleViewModel))
      } catch (e: Exception) {
        busy = false
        roomGone(str(R.string.battle_lost))
      }
    }
  }

  /** Looks for the room carried on by another phone: its advertisement names the room and a later epoch. */
  private fun lookForMove(roomId: String, epoch: Int) {
    mover?.stop()
    moved = null
    mover = NearbyFinder(getApplication(), { found ->
      val best = found.mapNotNull { (e, name) -> roomTag(name)?.takeIf { it.first == roomId.take(8) && it.second > epoch }?.let { e to it.second } }
        .maxByOrNull { it.second }
      if (best != null && (moved == null || best.second > moved!!.second)) moved = best
    }) {}
  }

  /** The room is gone; what it showed stays when anything was played. */
  private fun roomGone(message: String?) {
    message?.let { error = it }
    link?.close()
    link = null
    host?.stop()
    host = null
    hub = null
    if (outcomes.isEmpty()) {
      val keep = error
      clear()
      error = keep
    } else finished = true
  }

  private fun addOutcomes(list: List<HandOutcome>) {
    for (o in review.add(list)) record(o)
    saveBattle()
  }

  /** This battle's id in the records (BattleStore), from its first hand, and when that was dealt. */
  private var battleId: String? = null
  private var battleStart = 0L

  /** The battle as it stands, to the records, once it has a closed hand. */
  private fun saveBattle(left: Boolean = false) {
    val id = battleId ?: return
    val r = room ?: return
    if (outcomes.isEmpty()) return
    val b = BattleRecord(id, battleStart, via.name.lowercase(), r.settings, r.rules, me, r.players, outcomes, left)
    runCatching { battles.save(b) }
  }

  /** The player's play of a closed hand goes to the records, labelled "battle" (a table game) or "puzzle". */
  private fun record(o: HandOutcome) {
    val r = room ?: return
    val i = o.plays.indexOfFirst { it.player == me }
    if (i < 0) return
    val play = o.plays[i]
    val result = o.score?.result?.get(i) ?: -(o.losses?.get(i) ?: 0.0)
    records.add(
      play.rows.flatten(), play.rows, 0.0, r.rules, 0, result, label = if (r.settings.mode == Mode.PUZZLE) "puzzle" else "battle",
      battle = battleId, battleHand = o.index,
    )
  }

  private fun errorText(e: Error) = when (e.code) {
    ErrorCode.VERSION -> str(R.string.battle_err_version)
    ErrorCode.NO_ROOM -> str(R.string.battle_err_no_room)
    ErrorCode.ROOM_FULL -> str(R.string.battle_err_full)
    ErrorCode.STARTED -> str(R.string.battle_err_started)
    ErrorCode.TOO_MANY, ErrorCode.RATE -> str(R.string.battle_err_rate)
    ErrorCode.BUSY -> str(R.string.battle_err_busy)
    ErrorCode.BAD_SETTINGS -> str(R.string.battle_err_settings)
    ErrorCode.BAD_ROWS -> str(if (e.message == "fouled") R.string.fouled else R.string.battle_err_rows)
    ErrorCode.NOT_ALLOWED, ErrorCode.BAD_MESSAGE -> str(R.string.battle_err_not_allowed)
    ErrorCode.UNVERIFIED -> str(R.string.battle_err_unverified)
  }

  // ---- Results ----

  /** Analyzes a player's hand of the hand just closed, from its result. */
  fun analyzeHand(player: Int) {
    val index = room?.hand ?: return
    review.analyzeFromResult(index, player)
  }

  /** The last hand's table was seen; the final standings come next. */
  fun toSummary() {
    lastSeen = true
  }
}
