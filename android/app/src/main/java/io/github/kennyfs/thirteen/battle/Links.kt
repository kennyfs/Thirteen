package io.github.kennyfs.thirteen.battle

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import io.github.kennyfs.thirteen.protocol.BattleJson
import io.github.kennyfs.thirteen.protocol.ClientMsg
import io.github.kennyfs.thirteen.protocol.History
import io.github.kennyfs.thirteen.protocol.Hub
import io.github.kennyfs.thirteen.protocol.ServerMsg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** A client's way to its room: a server, a hosting phone nearby, or this phone's own hosted room. */
interface Link {
  fun send(msg: ClientMsg)
  fun close()
}

/** What a link reports, always on the main thread. */
interface LinkListener {
  fun onMessage(link: Link, msg: ServerMsg)
  /** The link is gone (not after close()). */
  fun onDown(link: Link, reason: String)
}

private val main = Handler(Looper.getMainLooper())

private fun decode(text: String): ServerMsg? = runCatching { BattleJson.decodeFromString(ServerMsg.serializer(), text) }.getOrNull()

private fun encode(msg: ClientMsg) = BattleJson.encodeToString(ClientMsg.serializer(), msg)

/**
 * The server's address as a WebSocket URL: "host[:port]" becomes wss://host[:port]/ws; ws:// is only for a server
 * on this device or the emulator's host (res/xml/network_security_config.xml); null for anything else.
 */
fun serverUrl(input: String): String? {
  var s = input.trim()
  if (s.isEmpty() || s.any { it.isWhitespace() }) return null
  s = when {
    s.startsWith("wss://") || s.startsWith("ws://") -> s
    s.startsWith("https://") -> "wss://" + s.removePrefix("https://")
    s.startsWith("http://") -> "ws://" + s.removePrefix("http://")
    else -> "wss://$s"
  }
  val rest = s.substringAfter("://")
  val host = rest.substringBefore("/").substringBefore(":").substringBefore("]").removePrefix("[")
  if (host.isEmpty()) return null
  if (s.startsWith("ws://") && host !in setOf("localhost", "127.0.0.1", "10.0.2.2")) return null
  return if ('/' in rest) s else "$s/ws"
}

class WsLink(url: String, private val listener: LinkListener) : Link {
  private var closed = false
  private val socket: WebSocket = CLIENT.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
    override fun onMessage(webSocket: WebSocket, text: String) {
      val msg = decode(text) ?: return
      main.post { if (!closed) listener.onMessage(this@WsLink, msg) }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
      webSocket.close(1000, null)
      down(reason)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = down(t.message ?: "failure")

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = down(reason)
  })

  private fun down(reason: String) {
    main.post {
      if (!closed) {
        closed = true
        listener.onDown(this, reason)
      }
    }
  }

  override fun send(msg: ClientMsg) {
    socket.send(encode(msg))
  }

  override fun close() {
    closed = true
    socket.close(1000, null)
  }

  companion object {
    private val CLIENT = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).connectTimeout(15, TimeUnit.SECONDS).build()
  }
}

/** The hosting phone's own seat in its hub: no network in between. */
class LocalLink(private val hub: Hub, private val scope: CoroutineScope, private val listener: LinkListener) : Link {
  private var closed = false
  private val conn = hub.open("local", { msg -> main.post { if (!closed) listener.onMessage(this, msg) } }, {})

  override fun send(msg: ClientMsg) {
    scope.launch { hub.receive(conn, encode(msg)) }
  }

  override fun close() {
    closed = true
    hub.closed(conn)
  }
}

const val SERVICE_ID = "io.github.kennyfs.thirteen.battle"
private val STRATEGY = Strategy.P2P_STAR
/** Nearby's limit for a bytes payload is 32 KB. */
private const val MAX_PAYLOAD = 30_000

/**
 * A room hosted on this phone for nearby phones: it advertises, accepts every connection (joining still takes the
 * room's PIN, checked by the hub with its failure limit) and passes payloads to the hub. Nearby encrypts the
 * connection. Every remote endpoint counts as one address for the hub's limits, as endpoint ids are new each time.
 */
class NearbyHost(context: Context, private val hub: Hub, private val scope: CoroutineScope, name: String, onError: (String) -> Unit) {
  private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
  private val conns = HashMap<String, Hub.Connection>()

  private val payloads = object : PayloadCallback() {
    override fun onPayloadReceived(endpointId: String, payload: Payload) {
      val conn = conns[endpointId] ?: return
      val bytes = payload.asBytes() ?: return
      if (bytes.size > 4096) return client.disconnectFromEndpoint(endpointId)
      scope.launch { hub.receive(conn, bytes.decodeToString()) }
    }

    override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
  }

  private val lifecycle = object : ConnectionLifecycleCallback() {
    override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
      client.acceptConnection(endpointId, payloads)
    }

    override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
      if (!result.status.isSuccess) return
      conns[endpointId] = hub.open("nearby", { msg -> sendTo(endpointId, msg) }, { client.disconnectFromEndpoint(endpointId) })
    }

    override fun onDisconnected(endpointId: String) {
      conns.remove(endpointId)?.let(hub::closed)
    }
  }

  init {
    client.startAdvertising(name, SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
      .addOnFailureListener { onError(it.message ?: it.toString()) }
  }

  /** A message to an endpoint; a long history goes in parts, each within a payload. */
  private fun sendTo(endpointId: String, msg: ServerMsg) {
    val bytes = BattleJson.encodeToString(ServerMsg.serializer(), msg).encodeToByteArray()
    if (bytes.size > MAX_PAYLOAD && msg is History && msg.outcomes.size > 1) {
      val half = msg.outcomes.size / 2
      sendTo(endpointId, History(msg.outcomes.take(half)))
      sendTo(endpointId, History(msg.outcomes.drop(half)))
      return
    }
    main.post { client.sendPayload(endpointId, Payload.fromBytes(bytes)) }
  }

  /** Stops advertising the room, keeping its connections. */
  fun stopAdvertising() = client.stopAdvertising()

  fun stop() {
    client.stopAdvertising()
    client.stopAllEndpoints()
    for (c in conns.values) hub.closed(c)
    conns.clear()
  }
}

/** Rooms advertised nearby: endpoint id to the host's advertised name. */
class NearbyFinder(context: Context, private val onChange: (Map<String, String>) -> Unit, onError: (String) -> Unit) {
  private val client = Nearby.getConnectionsClient(context)
  private val found = LinkedHashMap<String, String>()

  init {
    client.startDiscovery(SERVICE_ID, object : EndpointDiscoveryCallback() {
      override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
        if (info.serviceId != SERVICE_ID) return
        found[endpointId] = info.endpointName
        onChange(found.toMap())
      }

      override fun onEndpointLost(endpointId: String) {
        found.remove(endpointId)
        onChange(found.toMap())
      }
    }, DiscoveryOptions.Builder().setStrategy(STRATEGY).build()).addOnFailureListener { onError(it.message ?: it.toString()) }
  }

  fun stop() = client.stopDiscovery()
}

/** A connection to a room hosted nearby; messages sent before it is up wait for it. */
class NearbyLink(context: Context, private val endpointId: String, name: String, private val listener: LinkListener) : Link {
  private val client = Nearby.getConnectionsClient(context)
  private var up = false
  private var closed = false
  private val queue = mutableListOf<ClientMsg>()

  init {
    client.requestConnection(name, endpointId, object : ConnectionLifecycleCallback() {
      override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
        client.acceptConnection(endpointId, object : PayloadCallback() {
          override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val msg = payload.asBytes()?.let { decode(it.decodeToString()) } ?: return
            if (!closed) listener.onMessage(this@NearbyLink, msg)
          }

          override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
        })
      }

      override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
        if (!result.status.isSuccess) return down(result.status.statusMessage ?: "not connected")
        up = true
        queue.forEach(::send)
        queue.clear()
      }

      override fun onDisconnected(endpointId: String) = down("disconnected")
    }).addOnFailureListener { down(it.message ?: it.toString()) }
  }

  private fun down(reason: String) {
    if (closed) return
    closed = true
    listener.onDown(this, reason)
  }

  override fun send(msg: ClientMsg) {
    if (!up) queue += msg
    else client.sendPayload(endpointId, Payload.fromBytes(encode(msg).encodeToByteArray()))
  }

  override fun close() {
    closed = true
    client.disconnectFromEndpoint(endpointId)
  }
}
