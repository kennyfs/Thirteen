package io.github.kennyfs.thirteen.server

import io.github.kennyfs.thirteen.protocol.BattleJson
import io.github.kennyfs.thirteen.protocol.ClientMsg
import io.github.kennyfs.thirteen.protocol.Create
import io.github.kennyfs.thirteen.protocol.Deal
import io.github.kennyfs.thirteen.protocol.EngineClient
import io.github.kennyfs.thirteen.protocol.Error
import io.github.kennyfs.thirteen.protocol.ErrorCode
import io.github.kennyfs.thirteen.protocol.HandResult
import io.github.kennyfs.thirteen.protocol.Hello
import io.github.kennyfs.thirteen.protocol.Hub
import io.github.kennyfs.thirteen.protocol.Join
import io.github.kennyfs.thirteen.protocol.Mode
import io.github.kennyfs.thirteen.protocol.PROTOCOL_VERSION
import io.github.kennyfs.thirteen.protocol.RoomSettings
import io.github.kennyfs.thirteen.protocol.RuleChoice
import io.github.kennyfs.thirteen.protocol.ServerMsg
import io.github.kennyfs.thirteen.protocol.Start
import io.github.kennyfs.thirteen.protocol.State
import io.github.kennyfs.thirteen.protocol.Submit
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** The server with the real engine: a puzzle hand from two WebSocket clients, and what a client cannot do. */
class ServerTest {
  private val binary = System.getenv("THIRTEEN_BINARY") ?: ""
  private val model = System.getenv("THIRTEEN_MODEL") ?: ""

  private suspend fun DefaultClientWebSocketSession.send(msg: ClientMsg) =
    outgoing.send(Frame.Text(BattleJson.encodeToString(ClientMsg.serializer(), msg)))

  private suspend inline fun <reified T : ServerMsg> DefaultClientWebSocketSession.next(): T = withTimeout(20_000) {
    while (true) {
      val f = incoming.receive() as Frame.Text
      val msg = BattleJson.decodeFromString(ServerMsg.serializer(), f.readText())
      if (msg is T) return@withTimeout msg
    }
    @Suppress("UNREACHABLE_CODE") throw IllegalStateException()
  }

  @Test
  fun puzzleHandOverWebSockets() {
    assumeTrue(File(binary).canExecute() && File(model).isFile)
    val pool = EnginePool(binary, 2, 16, 10_000)
    val hub = Hub(EngineClient(model, pool::call), CoroutineScope(SupervisorJob() + Dispatchers.Default))
    testApplication {
      application { battle(hub, Config(trustProxy = false)) }
      val client = createClient { install(WebSockets) }
      client.webSocket("/ws") {
        send(Hello(PROTOCOL_VERSION, "Host"))
        send(Create(RoomSettings(Mode.PUZZLE, hands = 1, rules = RuleChoice("taiwan"), hard = true)))
        val room = next<State>().room
        val pin = room.pin
        val other = async {
          client.webSocket("/ws") {
            send(Hello(PROTOCOL_VERSION, "Guest"))
            send(Join(pin))
            val deal = next<Deal>()
            // A hand of someone else's cards is refused.
            send(Submit(0, listOf(listOf(0, 1, 2), listOf(3, 4, 5, 6, 7), listOf(8, 9, 10, 11, 12)).takeIf { deal.cards != (0..12).toList() } ?: listOf(listOf(13, 14, 15), listOf(16, 17, 18, 19, 20), listOf(21, 22, 23, 24, 25))))
            assertEquals(ErrorCode.BAD_ROWS, next<Error>().code)
            send(Submit(0, EngineClient(model, pool::call).greedy(deal.cards, room.rules)))
            next<HandResult>()
          }
        }
        // Waits for the guest before starting.
        while (next<State>().room.players.size < 2) Unit
        send(Start)
        val deal = next<Deal>()
        send(Submit(0, EngineClient(model, pool::call).greedy(deal.cards, room.rules)))
        val result = next<HandResult>().outcome
        assertEquals(2, result.plays.size)
        assertTrue(result.losses!!.all { it >= 0.0 })
        other.await()
      }
    }
    pool.close()
  }

  @Test
  fun addressesForLimits() {
    assertEquals("1.2.3.4", clientAddress("10.0.0.2", "9.9.9.9, 1.2.3.4"))
    assertEquals("10.0.0.2", clientAddress("10.0.0.2", null))
    assertEquals("20:01:0d:b8:00:00:00:01::/64", clientAddress("2001:db8:0:1:2:3:4:5", null))
    assertEquals("example.com", clientAddress("10.0.0.2", "example.com"))
  }
}
