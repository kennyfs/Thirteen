package io.github.kennyfs.thirteen.protocol

import kotlinx.serialization.Serializable

/**
 * One battle as a player's phone keeps it: the room's settings and rules, everyone in it with their totals, and every
 * closed hand with all the plays, so its hands can be looked at and analyzed again later. `me` is the player's id
 * in it; `left` that the player left before the end.
 */
@Serializable
data class BattleRecord(
  val id: String,
  /** When the first hand was dealt, in ms since the epoch. */
  val startedAt: Long,
  /** "online" or "nearby". */
  val via: String,
  val settings: RoomSettings,
  val rules: String,
  val me: Int,
  val players: List<PlayerView>,
  val outcomes: List<HandOutcome>,
  val left: Boolean = false,
) {
  /** The room as its last state showed it, for the pages that show a room's results. */
  fun view() = RoomView("", settings, rules, Phase.OVER, outcomes.lastOrNull()?.index ?: -1, -1, players, null)
}
