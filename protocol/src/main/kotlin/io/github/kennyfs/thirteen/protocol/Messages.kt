package io.github.kennyfs.thirteen.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The battle protocol: JSON messages, one per WebSocket frame or Nearby payload, with their type in "t". The server
 * (or the hosting phone) is the only authority: it deals, keeps every hand secret until the hand closes, checks each
 * arrangement and scores. See docs/battle.md.
 *
 * Cards are ids as in cpp/game/cards.h (rank * 4 + suit); rows are [front, middle, back].
 */
const val PROTOCOL_VERSION = 2

val BattleJson = Json {
  classDiscriminator = "t"
  ignoreUnknownKeys = true
  encodeDefaults = true
}

typealias Rows = List<List<Int>>

@Serializable
enum class Mode {
  /** Everyone sets the same hands; the score is the sum of what each choice loses against the network's best. */
  PUZZLE,
  /** Up to four players at one table, networks in the empty seats; the score is the points won. */
  GAME,
}

/** A preset of docs/rules.md section 11 and changed values, as the app's rules page has them; c is always 0. */
@Serializable
data class RuleChoice(val preset: String = "family", val overrides: Map<String, Double> = emptyMap())

@Serializable
data class RoomSettings(
  val mode: Mode,
  val hands: Int = 10,
  val rules: RuleChoice = RuleChoice(),
  /** Seconds per hand, one of Limits.TIME_LIMITS; 0 is no limit of the room's own (the server's cap still holds). */
  val timeLimit: Int = 0,
)

@Serializable
enum class Phase {
  LOBBY,
  /** A hand is dealt and open for arrangements. */
  PLAYING,
  /** The hand is closed and its result shown, until everyone is ready for the next one. */
  REVIEW,
  OVER,
}

@Serializable
data class PlayerView(
  val id: Int,
  val name: String,
  val bot: Boolean = false,
  val connected: Boolean = true,
  /** Has set the current hand (only whether, never what). */
  val submitted: Boolean = false,
  val ready: Boolean = false,
  /** Over the closed hands: points (GAME) or the summed loss (PUZZLE). */
  val total: Double = 0.0,
)

@Serializable
data class RoomView(
  val pin: String,
  val settings: RoomSettings,
  /** The rules as the engine spells them out ("c=0,row=1,..."). */
  val rules: String,
  val phase: Phase,
  /** The current or last hand, from 0; -1 in the lobby. */
  val hand: Int,
  val host: Int,
  val players: List<PlayerView>,
  /** Time left in the current phase, when it has a limit. */
  val remainingMs: Long? = null,
)

/** One player's arrangement of a closed hand; `timedOut`: set by greedy for the player. */
@Serializable
data class Play(val player: Int, val rows: Rows, val timedOut: Boolean = false)

/** A table's score of one deal (as cpp/game/scoring.h): per seat, in the order of the plays. */
@Serializable
data class TableScore(
  val result: List<Double>,
  val points: List<Double>,
  val scoops: List<List<Boolean>>,
  val homeRun: List<Boolean>,
  /** pairs[i][j]: what seat j pays seat i, home runs included. */
  val pairs: List<List<Double>>,
)

/**
 * A closed hand. GAME: the four seats' plays and the score. PUZZLE: everyone's play of the same hand, the network's
 * best arrangement and each play's loss against it (in the order of the plays).
 */
@Serializable
data class HandOutcome(
  val index: Int,
  val plays: List<Play>,
  val score: TableScore? = null,
  val best: Rows? = null,
  val losses: List<Double>? = null,
)

@Serializable
sealed interface ClientMsg

@Serializable @SerialName("hello")
data class Hello(val version: Int, val name: String) : ClientMsg

@Serializable @SerialName("create")
data class Create(val settings: RoomSettings) : ClientMsg

@Serializable @SerialName("join")
data class Join(val pin: String) : ClientMsg

/** Back into a room after a dropped connection, with the token of Joined. */
@Serializable @SerialName("resume")
data class Resume(val room: String, val token: String) : ClientMsg

/** The host changes the settings in the lobby. */
@Serializable @SerialName("settings")
data class ChangeSettings(val settings: RoomSettings) : ClientMsg

@Serializable @SerialName("start")
data object Start : ClientMsg

@Serializable @SerialName("submit")
data class Submit(val hand: Int, val rows: Rows) : ClientMsg

/** Done looking at the result of a hand. */
@Serializable @SerialName("ready")
data class Ready(val hand: Int) : ClientMsg

@Serializable @SerialName("leave")
data object Leave : ClientMsg

@Serializable
sealed interface ServerMsg

@Serializable @SerialName("welcome")
data class Welcome(val version: Int) : ServerMsg

/** In a room as player `you`; `token` (secret) resumes the seat after a dropped connection. */
@Serializable @SerialName("joined")
data class Joined(val room: String, val you: Int, val token: String) : ServerMsg

@Serializable @SerialName("state")
data class State(val room: RoomView) : ServerMsg

/** The player's own hand only; `mine` is the player's accepted arrangement when resuming after setting it. */
@Serializable @SerialName("deal")
data class Deal(val hand: Int, val cards: List<Int>, val remainingMs: Long? = null, val mine: Rows? = null) : ServerMsg

@Serializable @SerialName("result")
data class HandResult(val outcome: HandOutcome) : ServerMsg

/** Every closed hand so far, after Resume. */
@Serializable @SerialName("history")
data class History(val outcomes: List<HandOutcome>) : ServerMsg

@Serializable @SerialName("error")
data class Error(val code: ErrorCode, val message: String = "") : ServerMsg

/** The room is gone (closed, or the player left); the connection may stay for another room. */
@Serializable @SerialName("closed")
data class Closed(val reason: String = "") : ServerMsg

@Serializable
enum class ErrorCode {
  VERSION, BAD_MESSAGE, NOT_ALLOWED, NO_ROOM, ROOM_FULL, STARTED, TOO_MANY, BUSY, BAD_SETTINGS, BAD_ROWS, RATE,
}
