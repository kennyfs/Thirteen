package io.github.kennyfs.thirteen

import io.github.kennyfs.thirteen.protocol.BattleRecord
import io.github.kennyfs.thirteen.protocol.Mode
import io.github.kennyfs.thirteen.protocol.PlayerView
import io.github.kennyfs.thirteen.protocol.RoomSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecordsTest {
  @get:Rule val tmp = TemporaryFolder()

  private fun battle(id: String, startedAt: Long) = BattleRecord(
    id = id,
    startedAt = startedAt,
    via = "online",
    settings = RoomSettings(Mode.PUZZLE, hands = 1),
    rules = "c=0,row=0,scoop=1",
    me = 0,
    players = listOf(PlayerView(0, "A", total = 0.25)),
    outcomes = emptyList(),
  )

  @Test
  fun battleStoreRoundTripAndCorruptFileSkipped() {
    val store = BattleStore(tmp.root)
    store.save(battle("b1", 100L))
    store.save(battle("b2", 200L))
    // A truncated or corrupt JSON file does not break reading the others.
    File(tmp.root, "battles/corrupt.json").writeText("{not json")

    val all = store.all()
    assertEquals(listOf("b2", "b1"), all.map { it.id })
    assertEquals(0.25, store.get("b1")!!.players[0].total, 1e-9)
    assertNull(store.get("corrupt"))

    store.remove("b1")
    assertEquals(listOf("b2"), store.all().map { it.id })
    store.clear()
    assertEquals(emptyList<BattleRecord>(), store.all())
  }
}
