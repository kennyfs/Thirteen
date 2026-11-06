package io.github.kennyfs.thirteen.protocol

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import java.util.Base64

/** What one hub allows; the server reads them from its environment, a hosting phone uses NEARBY. */
data class HubLimits(
  val maxRooms: Int = 200,
  /** Rooms a client address may have open at once, and create per hour. */
  val roomsPerAddress: Int = 3,
  val createsPerAddressPerHour: Int = 20,
  val maxPuzzlePlayers: Int = 64,
  /** Failed joins or resumes (a wrong PIN or token) an address may make per window before it is refused for one. */
  val failuresPerAddress: Int = 10,
  val failureWindowMs: Long = 10 * 60_000L,
  /** Messages per second a connection may send on average, and in a burst. */
  val messagesPerSecond: Double = 5.0,
  val messageBurst: Int = 20,
  /** Bad or refused messages after which a connection is dropped. */
  val maxStrikes: Int = 20,
  val lobbyIdleMs: Long = 30 * 60_000L,
  val keepOverMs: Long = 10 * 60_000L,
  val roomLifetimeMs: Long = 6 * 3600_000L,
  /** Rooms keep a backup player (a phone's room, carried on by another phone should it go away). */
  val backups: Boolean = false,
) {
  companion object {
    val NEARBY = HubLimits(maxRooms = 1, roomsPerAddress = 1, maxPuzzlePlayers = 16, backups = true)
  }
}

/**
 * The rooms of a server (or of a hosting phone, with one room): connections say hello, create or join a room by its
 * PIN, and are then routed to it. A connection is one client; `address` is what limits apply to (the client's IP,
 * or a Nearby endpoint). Text from a client is parsed here and nowhere else.
 */
class Hub(
  private val engine: GameEngine,
  private val scope: CoroutineScope,
  private val limits: HubLimits = HubLimits(),
  private val now: () -> Long = System::currentTimeMillis,
  private val random: SecureRandom = SecureRandom(),
) {
  inner class Connection internal constructor(val address: String, private val sink: (ServerMsg) -> Unit, private val drop: () -> Unit) : Peer {
    override val local get() = address == LOCAL
    internal var name: String? = null
    internal var room: Room? = null
    internal var player = -1
    internal var strikes = 0
    private var tokens = limits.messageBurst.toDouble()
    private var refilled = now()
    @Volatile var open = true; private set

    override fun send(msg: ServerMsg) {
      if (open) sink(msg)
    }

    internal fun take(): Boolean {
      val t = now()
      tokens = minOf(limits.messageBurst.toDouble(), tokens + (t - refilled) / 1000.0 * limits.messagesPerSecond)
      refilled = t
      if (tokens < 1.0) return false
      tokens -= 1.0
      return true
    }

    internal fun close() {
      open = false
      drop()
    }
  }

  private val mutex = Mutex()
  private val rooms = HashMap<String, Room>()
  private val byPin = HashMap<String, Room>()
  private val owners = HashMap<Room, String>()
  private val creates = HashMap<String, ArrayDeque<Long>>()
  private val failures = HashMap<String, ArrayDeque<Long>>()

  init {
    scope.launch {
      while (isActive) {
        delay(30_000)
        sweep()
      }
    }
  }

  fun open(address: String, sink: (ServerMsg) -> Unit, drop: () -> Unit) = Connection(address, sink, drop)

  /** One text message from a connection. */
  suspend fun receive(c: Connection, text: String) {
    if (!c.open) return
    if (!c.take()) return strike(c, Error(ErrorCode.RATE))
    val msg = try {
      BattleJson.decodeFromString(ClientMsg.serializer(), text)
    } catch (e: Exception) {
      return strike(c, Error(ErrorCode.BAD_MESSAGE))
    }
    if (c.name == null) {
      if (msg !is Hello) return strike(c, Error(ErrorCode.NOT_ALLOWED, "hello first"))
      if (msg.version != PROTOCOL_VERSION) {
        c.send(Error(ErrorCode.VERSION, "server $PROTOCOL_VERSION"))
        return c.close()
      }
      c.name = Validation.name(msg.name)
      return c.send(Welcome(PROTOCOL_VERSION))
    }
    val room = c.room?.takeIf { !it.closed }
    when (msg) {
      is Hello -> strike(c, Error(ErrorCode.NOT_ALLOWED))
      is Create -> if (room != null) strike(c, Error(ErrorCode.NOT_ALLOWED)) else create(c, msg.settings)
      is Join -> if (room != null) strike(c, Error(ErrorCode.NOT_ALLOWED)) else join(c, msg.pin)
      is Resume -> if (room != null) strike(c, Error(ErrorCode.NOT_ALLOWED)) else resume(c, msg)
      else -> if (room == null) strike(c, Error(ErrorCode.NO_ROOM)) else {
        room.message(c.player, c, msg)
        if (msg is Leave) c.room = null
      }
    }
  }

  /** The connection is gone; its seat stays for a resume. */
  fun closed(c: Connection) {
    c.room?.let { if (c.player >= 0) it.disconnected(c.player, c) }
    c.room = null
  }

  private fun strike(c: Connection, error: Error) {
    c.send(error)
    if (++c.strikes > limits.maxStrikes) c.close()
  }

  private suspend fun create(c: Connection, settings: RoomSettings) {
    val s = Validation.settings(settings) ?: return strike(c, Error(ErrorCode.BAD_SETTINGS))
    val rules = try {
      engine.rules(Validation.ruleSpec(s.rules)!!)
    } catch (e: EngineError) {
      return strike(c, Error(ErrorCode.BAD_SETTINGS, e.message ?: ""))
    }
    val room = mutex.withLock {
      if (rooms.size >= limits.maxRooms) return c.send(Error(ErrorCode.BUSY))
      if (owners.values.count { it == c.address } >= limits.roomsPerAddress) return c.send(Error(ErrorCode.TOO_MANY))
      val times = window(creates, c.address, 3600_000L)
      if (times.size >= limits.createsPerAddressPerHour) return c.send(Error(ErrorCode.TOO_MANY))
      times.addLast(now())
      var pin: String
      do pin = (random.nextInt(900_000) + 100_000).toString() while (pin in byPin)
      val id = ByteArray(12).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
      val max = if (s.mode == Mode.GAME) Limits.TABLE else limits.maxPuzzlePlayers
      val room = Room(id, pin, s, rules, engine, scope, random, now, max, ::removed, limits.backups)
      rooms[id] = room
      byPin[pin] = room
      owners[room] = c.address
      room
    }
    enter(c, room, room.join(c, c.name!!))
  }

  private suspend fun join(c: Connection, pin: String) {
    if (refused(c)) return
    val room = mutex.withLock { byPin[pin]?.takeIf { pin.length == 6 && !it.closed } }
    if (room == null) {
      failed(c)
      return c.send(Error(ErrorCode.NO_ROOM))
    }
    enter(c, room, room.join(c, c.name!!))
  }

  private suspend fun resume(c: Connection, msg: Resume) {
    if (refused(c)) return
    val room = mutex.withLock { rooms[msg.room]?.takeIf { !it.closed } }
    val joined = room?.resume(c, msg.token)
    if (joined == null) {
      failed(c)
      return c.send(Error(ErrorCode.NO_ROOM))
    }
    enter(c, room, joined)
  }

  private fun enter(c: Connection, room: Room, joined: Joined?) {
    if (joined == null) return
    c.room = room
    c.player = joined.you
  }

  private suspend fun refused(c: Connection): Boolean {
    val n = mutex.withLock { window(failures, c.address, limits.failureWindowMs).size }
    if (n < limits.failuresPerAddress) return false
    c.send(Error(ErrorCode.RATE))
    return true
  }

  private suspend fun failed(c: Connection) {
    mutex.withLock { window(failures, c.address, limits.failureWindowMs).addLast(now()) }
    strike(c, Error(ErrorCode.NO_ROOM))
  }

  /** The times of an address's events within the last `span`, older ones dropped. */
  private fun window(map: HashMap<String, ArrayDeque<Long>>, address: String, span: Long): ArrayDeque<Long> {
    val q = map.getOrPut(address) { ArrayDeque() }
    while (q.isNotEmpty() && q.first() < now() - span) q.removeFirst()
    return q
  }

  private fun removed(room: Room) {
    scope.launch {
      mutex.withLock {
        rooms.remove(room.id)
        byPin.remove(room.pin)
        owners.remove(room)
      }
    }
  }

  /** Closes rooms idle in the lobby, finished a while ago, or simply too old; forgets old limit windows. */
  suspend fun sweep() {
    val t = now()
    val stale = mutex.withLock {
      creates.values.removeAll { q -> q.isEmpty() || q.last() < t - 3600_000L }
      failures.values.removeAll { q -> q.isEmpty() || q.last() < t - limits.failureWindowMs }
      rooms.values.filter { r ->
        t - r.createdAt > limits.roomLifetimeMs ||
          (r.phase == Phase.LOBBY && t - r.lastActivity > limits.lobbyIdleMs) ||
          (r.phase == Phase.OVER && t - r.lastActivity > limits.keepOverMs)
      }
    }
    for (r in stale) r.requestClose("expired")
  }

  suspend fun roomCount() = mutex.withLock { rooms.size }

  /**
   * Carries on a phone's room from its snapshot (this phone was its backup and the phone is gone): the players take
   * their seats back with Resume. `closed`: the room's closed hands, as this phone has them.
   */
  suspend fun restore(snapshot: RoomSnapshot, closed: List<HandOutcome>): Room = mutex.withLock {
    val max = if (snapshot.settings.mode == Mode.GAME) Limits.TABLE else limits.maxPuzzlePlayers
    val room = Room.restore(snapshot, closed, engine, scope, random, now, max, ::removed)
    rooms[room.id] = room
    byPin[room.pin] = room
    room
  }

  companion object {
    /** The address of the hosting phone's own connection (Peer.local). */
    const val LOCAL = "local"
  }
}
