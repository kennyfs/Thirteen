package io.github.kennyfs.thirteen.protocol

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An engine with fixed answers: every arrangement is valid, greedy and the bots split the sorted hand. */
private class FakeEngine : GameEngine {
  var calls = 0
  override suspend fun rules(spec: String): String {
    calls++
    if ("strict=1" in spec && "wheel" in spec) throw EngineError("bad rules")
    return "c=0,$spec"
  }
  // The hand's highest card in front fouls.
  override suspend fun valid(rows: Rows, rules: String) = rows[0].max() < rows[2].max()
  override suspend fun bot(hand: List<Int>, rules: String) = split(hand)
  override suspend fun greedy(hand: List<Int>, rules: String) = split(hand)
  override suspend fun score(plays: List<Rows>, rules: String) = TableScore(
    listOf(3.0, -1.0, -1.0, -1.0), listOf(3.0, -1.0, -1.0, -1.0), List(4) { List(4) { false } }, List(4) { false }, List(4) { List(4) { 0.0 } },
  )
  override suspend fun losses(hand: List<Int>, rules: String, plays: List<Rows>) = split(hand) to plays.indices.map { 0.1 * it }
  override suspend fun difficulty(hand: List<Int>, rules: String): Double {
    calls++
    return 0.5
  }
  private fun split(hand: List<Int>): Rows = hand.sorted().let { listOf(it.take(3), it.subList(3, 8), it.drop(8)) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RoomTest {
  private class Client(val hub: Hub, address: String) {
    val got = mutableListOf<ServerMsg>()
    var dropped = false
    val conn = hub.open(address, { got += it }, { dropped = true })
    suspend fun send(msg: ClientMsg) = hub.receive(conn, BattleJson.encodeToString(ClientMsg.serializer(), msg))
    inline fun <reified T : ServerMsg> all() = got.filterIsInstance<T>()
    inline fun <reified T : ServerMsg> last() = got.filterIsInstance<T>().lastOrNull()
    fun errors() = all<Error>().map { it.code }
  }

  private val engine = FakeEngine()

  private fun TestScope.hub(limits: HubLimits = HubLimits()) = Hub(engine, backgroundScope as CoroutineScope, limits, { testScheduler.currentTime })

  private suspend fun TestScope.client(hub: Hub, name: String, address: String = name): Client =
    Client(hub, address).also { it.send(Hello(PROTOCOL_VERSION, name)); runCurrent() }

  /** A host who created a room and the others who joined it by its PIN. */
  private suspend fun TestScope.room(hub: Hub, settings: RoomSettings, vararg names: String): List<Client> {
    val host = client(hub, names[0])
    host.send(Create(settings))
    runCurrent()
    val pin = host.last<State>()!!.room.pin
    val others = names.drop(1).map { n -> client(hub, n).also { it.send(Join(pin)); runCurrent() } }
    return listOf(host) + others
  }

  private fun rowsOf(cards: List<Int>): Rows = cards.sorted().let { listOf(it.take(3), it.subList(3, 8), it.drop(8)) }

  @Test
  fun puzzleDealsOneHandAndHidesPlaysUntilItCloses() = runTest {
    val hub = hub()
    val (a, b) = room(hub, RoomSettings(Mode.PUZZLE, hands = 2), "A", "B")
    a.send(Start)
    runCurrent()
    val da = a.last<Deal>()!!
    val db = b.last<Deal>()!!
    assertEquals(da.cards, db.cards)
    a.send(Submit(0, rowsOf(da.cards)))
    runCurrent()
    // B learns that A has set the hand, nothing more.
    assertNull(b.last<HandResult>())
    assertTrue(b.last<State>()!!.room.players.first { it.name == "A" }.submitted)
    b.send(Submit(0, rowsOf(db.cards)))
    runCurrent()
    val r = b.last<HandResult>()!!.outcome
    assertEquals(listOf(0.0, 0.1), r.losses)
    assertEquals(Phase.REVIEW, b.last<State>()!!.room.phase)
    a.send(Ready(0))
    b.send(Ready(0))
    runCurrent()
    assertEquals(1, a.last<Deal>()!!.hand)
  }

  @Test
  fun aPlayMustUseTheDealtCardsOnceAndBeValid() = runTest {
    val hub = hub()
    val (a, b) = room(hub, RoomSettings(Mode.GAME, hands = 1), "A", "B")
    a.send(Start)
    runCurrent()
    val mine = a.last<Deal>()!!.cards
    val theirs = b.last<Deal>()!!.cards
    assertTrue(mine.intersect(theirs.toSet()).isEmpty())
    a.send(Submit(0, rowsOf(theirs)))
    a.send(Submit(0, listOf(mine.take(3), mine.subList(3, 8), mine.subList(8, 12) + mine[3])))
    a.send(Submit(1, rowsOf(mine)))
    runCurrent()
    assertEquals(listOf(ErrorCode.BAD_ROWS, ErrorCode.BAD_ROWS, ErrorCode.NOT_ALLOWED), a.errors())
    a.send(Submit(0, rowsOf(mine)))
    a.send(Submit(0, rowsOf(mine)))
    runCurrent()
    assertEquals(ErrorCode.NOT_ALLOWED, a.errors().last())
    b.send(Submit(0, rowsOf(theirs)))
    runCurrent()
    val outcome = a.last<HandResult>()!!.outcome
    // Two networks filled the empty seats.
    assertEquals(4, outcome.plays.size)
    assertEquals(Phase.OVER, a.last<State>()!!.room.phase)
    assertEquals(3.0, a.last<State>()!!.room.players[0].total, 0.0)
  }

  @Test
  fun aFouledPlayIsRefused() = runTest {
    val hub = hub()
    val (a) = room(hub, RoomSettings(Mode.GAME, hands = 1), "A")
    a.send(Start)
    runCurrent()
    val cards = a.last<Deal>()!!.cards.sortedDescending()
    a.send(Submit(0, listOf(cards.take(3), cards.subList(3, 8), cards.drop(8))))
    runCurrent()
    assertEquals(Error(ErrorCode.BAD_ROWS, "fouled"), a.last<Error>())
  }

  @Test
  fun timeRunsOutIntoGreedy() = runTest {
    val hub = hub()
    val (a, b) = room(hub, RoomSettings(Mode.PUZZLE, hands = 1, timeLimit = 30), "A", "B")
    a.send(Start)
    runCurrent()
    a.send(Submit(0, rowsOf(a.last<Deal>()!!.cards)))
    advanceTimeBy(29_000)
    runCurrent()
    assertNull(a.last<HandResult>())
    advanceTimeBy(2_000)
    runCurrent()
    val plays = a.last<HandResult>()!!.outcome.plays
    assertEquals(listOf(false, true), plays.map { it.timedOut })
  }

  @Test
  fun noTimeLimitStillCloses() = runTest {
    val hub = hub()
    val (a, _) = room(hub, RoomSettings(Mode.PUZZLE, hands = 1), "A", "B")
    a.send(Start)
    runCurrent()
    advanceTimeBy(Limits.HAND_CAP_MS + 1000)
    runCurrent()
    assertNotNull(a.last<HandResult>())
  }

  @Test
  fun onlyTheHostStartsAndOnlyBeforeTheStart() = runTest {
    val hub = hub()
    val (a, b) = room(hub, RoomSettings(Mode.PUZZLE, hands = 3), "A", "B")
    b.send(Start)
    b.send(ChangeSettings(RoomSettings(Mode.PUZZLE, hands = 5)))
    runCurrent()
    assertEquals(listOf(ErrorCode.NOT_ALLOWED, ErrorCode.NOT_ALLOWED), b.errors())
    a.send(Start)
    runCurrent()
    val late = client(hub, "C")
    late.send(Join(a.last<State>()!!.room.pin))
    runCurrent()
    assertEquals(ErrorCode.STARTED, late.errors().last())
  }

  @Test
  fun aPuzzleAloneIsAChallenge() = runTest {
    val hub = hub()
    val (a) = room(hub, RoomSettings(Mode.PUZZLE), "A")
    a.send(Start)
    runCurrent()
    assertEquals(13, a.last<Deal>()!!.cards.size)
  }

  @Test
  fun resumeTakesTheSeatBackWithItsHand() = runTest {
    val hub = hub()
    val (a, b) = room(hub, RoomSettings(Mode.PUZZLE, hands = 2), "A", "B")
    val token = b.last<Joined>()!!
    a.send(Start)
    runCurrent()
    val cards = b.last<Deal>()!!.cards
    b.send(Submit(0, rowsOf(cards)))
    runCurrent()
    hub.closed(b.conn)
    runCurrent()
    assertFalse(a.last<State>()!!.room.players[1].connected)
    val stranger = client(hub, "X")
    stranger.send(Resume(token.room, "wrong"))
    runCurrent()
    assertEquals(ErrorCode.NO_ROOM, stranger.errors().last())
    val back = client(hub, "B2")
    back.send(Resume(token.room, token.token))
    runCurrent()
    val deal = back.last<Deal>()!!
    assertEquals(cards, deal.cards)
    assertEquals(rowsOf(cards).map { it.sortedDescending() }, deal.mine)
    assertTrue(a.last<State>()!!.room.players[1].connected)
  }

  @Test
  fun aDisconnectedPlayerHoldsTheHandUpOnlyBriefly() = runTest {
    val hub = hub()
    val (a, b) = room(hub, RoomSettings(Mode.PUZZLE, hands = 1), "A", "B")
    a.send(Start)
    runCurrent()
    hub.closed(b.conn)
    a.send(Submit(0, rowsOf(a.last<Deal>()!!.cards)))
    runCurrent()
    advanceTimeBy(Limits.AWAY_GRACE_MS + 1000)
    runCurrent()
    assertTrue(a.last<HandResult>()!!.outcome.plays[1].timedOut)
  }

  @Test
  fun wrongPinsAreLimited() = runTest {
    val hub = hub()
    val (a) = room(hub, RoomSettings(Mode.PUZZLE), "A")
    val pin = a.last<State>()!!.room.pin
    val guesser = client(hub, "G", address = "1.2.3.4")
    for (i in 0 until 10) guesser.send(Join(if (pin == "123456") "654321" else "123456"))
    guesser.send(Join(pin))
    runCurrent()
    assertEquals(ErrorCode.RATE, guesser.errors().last())
    // Another address is not affected.
    val friend = client(hub, "F", address = "5.6.7.8")
    friend.send(Join(pin))
    runCurrent()
    assertNotNull(friend.last<Joined>())
  }

  @Test
  fun floodsAndGarbageGetTheConnectionDropped() = runTest {
    val hub = hub()
    val c = client(hub, "A")
    repeat(100) { hub.receive(c.conn, "{\"t\":\"nonsense\"}") }
    assertTrue(c.dropped)
    assertTrue(ErrorCode.RATE in c.errors())
  }

  @Test
  fun helloFirstAndTheRightVersion() = runTest {
    val hub = hub()
    val c = Client(hub, "A")
    c.send(Create(RoomSettings(Mode.PUZZLE)))
    c.send(Hello(PROTOCOL_VERSION + 1, "A"))
    assertEquals(listOf(ErrorCode.NOT_ALLOWED, ErrorCode.VERSION), c.errors())
    assertTrue(c.dropped)
  }

  @Test
  fun roomsPerAddressAreLimited() = runTest {
    val hub = hub(HubLimits(roomsPerAddress = 2))
    val clients = List(3) { client(hub, "A$it", address = "same") }
    for (c in clients) c.send(Create(RoomSettings(Mode.PUZZLE)))
    runCurrent()
    assertEquals(ErrorCode.TOO_MANY, clients[2].errors().last())
  }

  @Test
  fun hardHandsAskTheEngineABoundedNumberOfTimes() = runTest {
    val hub = hub()
    val (a, b) = room(hub, RoomSettings(Mode.PUZZLE, hands = 1, hard = true), "A", "B")
    val before = engine.calls
    a.send(Start)
    runCurrent()
    // No hand is hard enough for the fake engine: every try is made, and no more.
    assertEquals(Room.HARD_TRIES, engine.calls - before)
    assertNotNull(b.last<Deal>())
  }

  @Test
  fun settingsAreChecked() {
    assertNull(Validation.ruleSpec(RuleChoice("family", mapOf("c" to 1.0))))
    assertNull(Validation.ruleSpec(RuleChoice("family", mapOf("row" to 1000.0))))
    assertNull(Validation.ruleSpec(RuleChoice("family", mapOf("row" to Double.NaN))))
    assertNull(Validation.ruleSpec(RuleChoice("family", mapOf("wheel" to 1.5))))
    assertNull(Validation.ruleSpec(RuleChoice("family;rm", emptyMap())))
    assertEquals("taiwan,scoop=2.0,c=0", Validation.ruleSpec(RuleChoice("taiwan", mapOf("scoop" to 2.0))))
    assertNull(Validation.settings(RoomSettings(Mode.PUZZLE, hands = 31)))
    assertNull(Validation.settings(RoomSettings(Mode.PUZZLE, timeLimit = 7)))
    assertEquals("Alice Bob", Validation.name("  Ali‮ce\n\tBob "))
    assertEquals("Player", Validation.name("​\u0000"))
    assertEquals(Limits.NAME_LENGTH, Validation.name("x".repeat(100)).length)
  }
}
