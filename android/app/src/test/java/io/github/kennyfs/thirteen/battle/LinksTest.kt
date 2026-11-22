package io.github.kennyfs.thirteen.battle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The server field as the player types it (the web page's serverUrl in battle.svelte.ts does the same). */
class LinksTest {
  @Test
  fun names() {
    assertEquals("wss://thirteen.example.com/ws", serverUrl("thirteen.example.com"))
    assertEquals("wss://thirteen.example.com:8443/ws", serverUrl(" https://thirteen.example.com:8443 "))
    assertEquals("ws://10.0.2.2:8090/ws", serverUrl("ws://10.0.2.2:8090"))
    assertEquals("wss://a.example/x", serverUrl("wss://a.example/x"))
  }

  @Test
  fun ipv6() {
    // The host is what the brackets hold: splitting at the first ':' left it empty, and the address was refused.
    assertEquals("wss://[2001:db8::1]:8443/ws", serverUrl("[2001:db8::1]:8443"))
    assertEquals("wss://[2001:db8::1]/ws", serverUrl("wss://[2001:db8::1]"))
  }

  @Test
  fun refused() {
    assertNull(serverUrl(""))
    assertNull(serverUrl("a b"))
    assertNull(serverUrl("[]:8090"))
    assertNull(serverUrl("[2001:db8::1"))
    // Without TLS only on this device or the emulator's host.
    assertNull(serverUrl("ws://thirteen.example.com"))
    assertNull(serverUrl("http://[2001:db8::1]:8090"))
    // What OkHttp cannot take: an IME's full-width colon, a port out of range. It threw, and the app closed.
    assertNull(serverUrl("半歐安歐兜兜："))
    assertNull(serverUrl("thirteen.example.com:99999"))
  }
}
