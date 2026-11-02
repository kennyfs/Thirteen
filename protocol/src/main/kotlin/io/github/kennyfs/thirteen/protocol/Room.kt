package io.github.kennyfs.thirteen.protocol

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Where a room sends a player's messages: a WebSocket, a Nearby endpoint, or the hosting phone's own screen. */
interface Peer {
  fun send(msg: ServerMsg)
}

/**
 * One room: its players, its hands and their results. Every change runs on one coroutine, in the order the events
 * arrive (an actor), so the state needs no locks. What keeps the game fair:
 *
 * - Hands are dealt here with a SecureRandom; a player is only ever sent their own hand (in a puzzle everyone's is
 *   the same) and nothing about the others' plays until the hand closes, except whether each has set it.
 * - A play is accepted once per hand, only for the open hand, only with exactly the dealt cards, and only if the
 *   engine finds it valid. Scores and losses are computed here, never taken from a client.
 * - A player who runs out of time gets greedy's arrangement, never the network's, so waiting out the clock cannot
 *   pay. The engine is only asked what the room itself needs, a bounded number of times per hand.
 */
class Room(
  val id: String,
  val pin: String,
  settings: RoomSettings,
  rules: String,
  private val engine: GameEngine,
  private val scope: CoroutineScope,
  private val random: SecureRandom,
  private val now: () -> Long,
  private val maxPlayers: Int,
  private val onClosed: (Room) -> Unit,
) {
  var settings = settings; private set
  var rules = rules; private set
  val createdAt = now()
  @Volatile var lastActivity = createdAt; private set
  @Volatile var phase = Phase.LOBBY; private set
  @Volatile var closed = false; private set

  private class Player(val id: Int, val name: String, val token: String, val bot: Boolean) {
    var peer: Peer? = null
    /** Left for good: plays by timeout from now on and is waited for by nobody. */
    var left = false
    var total = 0.0
    var ready = false
    var rows: Rows? = null
    var timedOut = false
    val away get() = !bot && (left || peer == null)
  }

  private val players = mutableListOf<Player>()
  private var host = -1
  private var nextId = 0
  private var hand = -1
  /** The current hand's cards by player id (a puzzle deals one hand to all). */
  private var cards = emptyMap<Int, List<Int>>()
  private val outcomes = mutableListOf<HandOutcome>()
  private var deadline: Long? = null
  private var timer: Job? = null
  private var engineCalls = 0

  private sealed interface Event
  private class JoinEvent(val peer: Peer, val name: String, val reply: CompletableDeferred<Joined?>) : Event
  private class ResumeEvent(val peer: Peer, val token: String, val reply: CompletableDeferred<Joined?>) : Event
  private class MessageEvent(val player: Int, val peer: Peer, val msg: ClientMsg) : Event
  private class DisconnectEvent(val player: Int, val peer: Peer) : Event
  private class TimerEvent(val hand: Int, val phase: Phase) : Event
  private class CloseEvent(val reason: String) : Event

  private val events = Channel<Event>(Channel.UNLIMITED)

  init {
    scope.launch {
      for (e in events) {
        if (closed) break
        try {
          handle(e)
        } catch (ex: CancellationException) {
          throw ex
        } catch (ex: Exception) {
          // An engine that fails or times out, or the room's compute budget used up: the room cannot go on.
          close(if (ex is EngineError) "engine" else "error")
        }
      }
      // Whoever still waits for an answer gets none.
      while (true) {
        val e = events.tryReceive().getOrNull() ?: break
        if (e is JoinEvent) e.reply.complete(null)
        if (e is ResumeEvent) e.reply.complete(null)
      }
    }
  }

  // ---- The Hub's side: each call queues an event. ----

  /** A new player; null when the room is full or already started. */
  suspend fun join(peer: Peer, name: String): Joined? = ask { JoinEvent(peer, name, it) }

  /** The seat with this token, now on `peer`; null for an unknown token. */
  suspend fun resume(peer: Peer, token: String): Joined? = ask { ResumeEvent(peer, token, it) }

  private suspend fun ask(event: (CompletableDeferred<Joined?>) -> Event): Joined? {
    val reply = CompletableDeferred<Joined?>()
    if (events.trySend(event(reply)).isFailure) return null
    return reply.await()
  }

  /** A message from `peer`, which must still be the player's connection. */
  fun message(player: Int, peer: Peer, msg: ClientMsg) {
    events.trySend(MessageEvent(player, peer, msg))
  }

  fun disconnected(player: Int, peer: Peer) {
    events.trySend(DisconnectEvent(player, peer))
  }

  fun requestClose(reason: String) {
    events.trySend(CloseEvent(reason))
  }

  // ---- Events ----

  private suspend fun handle(e: Event) {
    lastActivity = now()
    when (e) {
      is JoinEvent -> e.reply.complete(join(e))
      is ResumeEvent -> e.reply.complete(resume(e))
      is MessageEvent -> players.firstOrNull { it.id == e.player && it.peer === e.peer }?.let { message(it, e.msg) }
      is DisconnectEvent -> players.firstOrNull { it.id == e.player && it.peer === e.peer }?.let { p ->
        p.peer = null
        if (phase == Phase.LOBBY) removeFromLobby(p) else {
          broadcastState()
          checkClose()
          checkReady()
        }
      }
      is TimerEvent -> if (e.hand == hand && e.phase == phase) when (phase) {
        Phase.PLAYING -> closeHand()
        Phase.REVIEW -> nextHand()
        else -> {}
      }
      is CloseEvent -> close(e.reason)
    }
  }

  private fun humans() = players.filter { !it.bot }

  private fun join(e: JoinEvent): Joined? {
    if (phase != Phase.LOBBY) {
      e.peer.send(Error(ErrorCode.STARTED))
      return null
    }
    if (humans().size >= maxPlayers) {
      e.peer.send(Error(ErrorCode.ROOM_FULL))
      return null
    }
    val p = Player(nextId++, Validation.name(e.name), token(), bot = false)
    p.peer = e.peer
    players += p
    if (host < 0) host = p.id
    val joined = Joined(id, p.id, p.token)
    e.peer.send(joined)
    broadcastState()
    return joined
  }

  private fun resume(e: ResumeEvent): Joined? {
    val p = humans().firstOrNull { !it.left && constantTimeEquals(it.token, e.token) } ?: return null
    if (p.peer !== e.peer) p.peer?.send(Closed("resumed elsewhere"))
    p.peer = e.peer
    val joined = Joined(id, p.id, p.token)
    e.peer.send(joined)
    broadcastState()
    if (outcomes.isNotEmpty()) e.peer.send(History(outcomes.toList()))
    if (phase == Phase.PLAYING) e.peer.send(Deal(hand, cards.getValue(p.id), remaining(), p.rows))
    return joined
  }

  private suspend fun message(p: Player, msg: ClientMsg) {
    val peer = p.peer ?: return
    when (msg) {
      is ChangeSettings -> {
        if (p.id != host || phase != Phase.LOBBY) return peer.send(Error(ErrorCode.NOT_ALLOWED))
        val s = Validation.settings(msg.settings.copy(mode = settings.mode)) ?: return peer.send(Error(ErrorCode.BAD_SETTINGS))
        rules = try {
          useEngine { engine.rules(Validation.ruleSpec(s.rules)!!) }
        } catch (ex: EngineError) {
          return peer.send(Error(ErrorCode.BAD_SETTINGS, ex.message ?: ""))
        }
        settings = s
        broadcastState()
      }
      Start -> {
        val enough = if (settings.mode == Mode.PUZZLE) humans().size >= 2 else humans().isNotEmpty()
        if (p.id != host || phase != Phase.LOBBY || !enough) return peer.send(Error(ErrorCode.NOT_ALLOWED))
        if (settings.mode == Mode.GAME)
          for (i in 1..Limits.TABLE - players.size) players += Player(nextId++, "AI $i", "", bot = true)
        startHand(0)
      }
      is Submit -> submit(p, msg)
      is Ready -> if (phase == Phase.REVIEW && msg.hand == hand && !p.ready) {
        p.ready = true
        broadcastState()
        checkReady()
      }
      Leave -> {
        peer.send(Closed("left"))
        p.peer = null
        if (phase == Phase.LOBBY) removeFromLobby(p) else {
          p.left = true
          if (humans().all { it.left }) return close("everyone left")
          if (host == p.id) host = humans().firstOrNull { !it.left }?.id ?: host
          broadcastState()
          checkClose()
          checkReady()
        }
      }
      is Hello, is Create, is Join, is Resume -> peer.send(Error(ErrorCode.NOT_ALLOWED))
    }
  }

  private fun removeFromLobby(p: Player) {
    players.remove(p)
    if (humans().isEmpty()) return close("empty")
    if (host == p.id) host = humans().first().id
    broadcastState()
  }

  private suspend fun submit(p: Player, msg: Submit) {
    val peer = p.peer ?: return
    if (phase != Phase.PLAYING || msg.hand != hand || p.rows != null) return peer.send(Error(ErrorCode.NOT_ALLOWED))
    val dealt = cards.getValue(p.id)
    if (!Validation.usesHand(msg.rows, dealt)) return peer.send(Error(ErrorCode.BAD_ROWS))
    val rows = msg.rows.map { it.sortedDescending() }
    if (!useEngine { engine.valid(rows, rules) }) return peer.send(Error(ErrorCode.BAD_ROWS, "fouled"))
    p.rows = rows
    broadcastState()
    checkClose()
  }

  // ---- Hands ----

  private suspend fun startHand(k: Int) {
    hand = k
    phase = Phase.PLAYING
    for (p in players) {
      p.rows = null
      p.timedOut = false
      p.ready = false
    }
    cards = if (settings.mode == Mode.GAME) {
      val deck = (0 until 52).toMutableList().also { it.shuffle(random) }
      players.withIndex().associate { (i, p) -> p.id to deck.subList(13 * i, 13 * i + 13).sorted() }
    } else {
      val h = puzzleHand()
      players.associate { it.id to h }
    }
    val limit = if (settings.timeLimit > 0) settings.timeLimit * 1000L else Limits.HAND_CAP_MS
    setTimer(limit)
    for (p in humans()) p.peer?.send(Deal(hand, cards.getValue(p.id), remaining()))
    for (p in players.filter { it.bot }) p.rows = useEngine { engine.bot(cards.getValue(p.id), rules) }
    broadcastState()
    checkClose()
  }

  /** A random hand, or with `hard`, the hardest of a few random ones (the first hard enough ends the search). */
  private suspend fun puzzleHand(): List<Int> {
    fun random13() = (0 until 52).toMutableList().also { it.shuffle(random) }.take(13).sorted()
    if (!settings.hard) return random13()
    var best = random13()
    var bestD = -1.0
    for (t in 0 until HARD_TRIES) {
      val h = if (t == 0) best else random13()
      val d = useEngine { engine.difficulty(h, rules) }
      if (d > bestD) {
        best = h
        bestD = d
      }
      if (d >= HARD_ENOUGH) break
    }
    return best
  }

  /**
   * Closes the hand once everyone has set it; when only players who are away have not, after AWAY_GRACE_MS (or the
   * deadline, whichever is first), so a dropped connection does not hold everyone up but a quick reconnect still
   * counts.
   */
  private suspend fun checkClose() {
    if (phase != Phase.PLAYING) return
    val missing = players.filter { it.rows == null && !it.left }
    if (missing.isEmpty()) return closeHand()
    if (missing.all { it.away } && players.any { !it.bot && !it.away }) {
      val left = remaining() ?: return
      if (left > Limits.AWAY_GRACE_MS) setTimer(Limits.AWAY_GRACE_MS)
    }
  }

  private suspend fun closeHand() {
    for (p in players) if (p.rows == null) {
      p.rows = useEngine { engine.greedy(cards.getValue(p.id), rules) }
      p.timedOut = true
    }
    val plays = players.map { Play(it.id, it.rows!!, it.timedOut) }
    val outcome = if (settings.mode == Mode.GAME) {
      val score = useEngine { engine.score(plays.map { it.rows }, rules) }
      players.forEachIndexed { i, p -> p.total += score.result[i] }
      HandOutcome(hand, plays, score = score)
    } else {
      val (best, losses) = useEngine { engine.losses(cards.values.first(), rules, plays.map { it.rows }) }
      players.forEachIndexed { i, p -> p.total += losses[i] }
      HandOutcome(hand, plays, best = best, losses = losses)
    }
    outcomes += outcome
    if (hand + 1 >= settings.hands) {
      phase = Phase.OVER
      timer?.cancel()
      deadline = null
    } else {
      phase = Phase.REVIEW
      setTimer(Limits.REVIEW_MS)
    }
    broadcast(HandResult(outcome))
    broadcastState()
    checkReady()
  }

  private suspend fun checkReady() {
    if (phase == Phase.REVIEW && players.all { it.ready || it.bot || it.away }) nextHand()
  }

  private suspend fun nextHand() {
    if (phase == Phase.REVIEW) startHand(hand + 1)
  }

  private fun setTimer(ms: Long) {
    timer?.cancel()
    deadline = now() + ms
    val h = hand
    val ph = phase
    timer = scope.launch {
      delay(ms)
      events.send(TimerEvent(h, ph))
    }
  }

  private fun remaining(): Long? = deadline?.let { (it - now()).coerceAtLeast(0) }

  /** Counts the room's engine requests against its budget: settings.hands hands, each a bounded number of calls. */
  private suspend fun <T> useEngine(block: suspend () -> T): T {
    if (++engineCalls > budget()) throw IllegalStateException("engine budget")
    return block()
  }

  private fun budget() = 20 + settings.hands * (2 * HARD_TRIES + 3 * Limits.TABLE + 4) + 8 * maxPlayers

  // ---- Output ----

  private fun view() = RoomView(
    pin, settings, rules, phase, hand, host,
    players.map { PlayerView(it.id, it.name, it.bot, it.bot || !it.away, it.rows != null, it.ready, it.total) },
    remaining(),
  )

  private fun broadcast(msg: ServerMsg) {
    for (p in players) p.peer?.send(msg)
  }

  private fun broadcastState() = broadcast(State(view()))

  private fun close(reason: String) {
    if (closed) return
    closed = true
    timer?.cancel()
    broadcast(Closed(reason))
    for (p in players) p.peer = null
    events.close()
    onClosed(this)
  }

  private fun token(): String = ByteArray(16).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

  companion object {
    /** Up to this many hands are tried for a hard puzzle hand: about a third of random hands are hard enough. */
    const val HARD_TRIES = 12
    const val HARD_ENOUGH = 1.0

    fun constantTimeEquals(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
  }
}
