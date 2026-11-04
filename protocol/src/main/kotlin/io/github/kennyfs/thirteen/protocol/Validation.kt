package io.github.kennyfs.thirteen.protocol

/** What a room may be set to, and the time limits the server holds every room to whatever its settings. */
object Limits {
  const val MIN_HANDS = 1
  const val MAX_HANDS = 30
  val TIME_LIMITS = listOf(0, 30, 60, 120, 180, 300)
  /** A hand closes after this long even without a time limit, so an idle player cannot hold a room forever. */
  const val HAND_CAP_MS = 10 * 60_000L
  /** The result of a hand stays up this long at most before the next hand. */
  const val REVIEW_MS = 2 * 60_000L
  /** Once everyone connected has set a hand, a disconnected player is waited for this long. */
  const val AWAY_GRACE_MS = 30_000L
  const val TABLE = 4
  const val NAME_LENGTH = 16
  /** The largest rules value: far above any real scoring, small enough that no sum overflows anything. */
  const val MAX_RULE_VALUE = 100.0
}

object Validation {
  val PRESETS = listOf("family", "family_mid2", "taiwan", "pagat")

  /** The values a room may change (cpp/game/rules.cpp FEATURE_NAMES, without c), with their kind. */
  private val RULES = mapOf(
    "row" to Kind.POINTS, "scoop" to Kind.POINTS, "home_run" to Kind.POINTS,
    "front_trips" to Kind.POINTS, "middle_full_house" to Kind.POINTS, "middle_quads" to Kind.POINTS,
    "middle_straight_flush" to Kind.POINTS, "back_quads" to Kind.POINTS, "back_straight_flush" to Kind.POINTS,
    "dragon" to Kind.POINTS, "on_win" to Kind.FLAG, "strict" to Kind.FLAG, "wheel" to Kind.WHEEL,
  )

  private enum class Kind { POINTS, FLAG, WHEEL }

  /**
   * The engine's spec of a rules choice ("taiwan,scoop=2.0,c=0"), or null when it names anything unknown or a value
   * out of range. The engine then checks the rest (royalties that do not decrease with the category).
   */
  fun ruleSpec(choice: RuleChoice): String? {
    if (choice.preset !in PRESETS || choice.overrides.size > RULES.size) return null
    val parts = mutableListOf(choice.preset)
    for ((name, value) in choice.overrides.toSortedMap()) {
      val kind = RULES[name] ?: return null
      val ok = when (kind) {
        Kind.POINTS -> value.isFinite() && value >= 0.0 && value <= Limits.MAX_RULE_VALUE
        Kind.FLAG -> value == 0.0 || value == 1.0
        Kind.WHEEL -> value == 0.0 || value == 1.0 || value == 2.0
      }
      if (!ok) return null
      parts += "$name=$value"
    }
    parts += "c=0"
    return parts.joinToString(",")
  }

  /** The settings with everything checked but the rules' consistency, or null. */
  fun settings(s: RoomSettings): RoomSettings? {
    if (s.hands !in Limits.MIN_HANDS..Limits.MAX_HANDS || s.timeLimit !in Limits.TIME_LIMITS) return null
    if (ruleSpec(s.rules) == null) return null
    if (s.level != 0 && s.level !in Levels.EASIEST..Levels.HARDEST) return null
    return if (s.mode == Mode.GAME) s.copy(hard = false, level = 0) else s
  }

  /**
   * A display name: without control or format characters (no bidirectional overrides), spaces collapsed, at most
   * NAME_LENGTH characters; `fallback` when nothing is left.
   */
  fun name(raw: String, fallback: String = "Player"): String {
    val cleaned = StringBuilder()
    var i = 0
    while (i < raw.length && cleaned.codePointCount(0, cleaned.length) < Limits.NAME_LENGTH) {
      val cp = raw.codePointAt(i)
      i += Character.charCount(cp)
      val type = Character.getType(cp)
      val bad = type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt() || type == Character.SURROGATE.toInt() ||
        type == Character.PRIVATE_USE.toInt() || type == Character.UNASSIGNED.toInt() || type == Character.LINE_SEPARATOR.toInt() ||
        type == Character.PARAGRAPH_SEPARATOR.toInt()
      if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
        if (cleaned.isNotEmpty() && cleaned.last() != ' ') cleaned.append(' ')
      } else if (!bad) cleaned.appendCodePoint(cp)
    }
    return cleaned.toString().trim().ifEmpty { fallback }
  }

  /** Whether rows are a front of 3 and a middle and back of 5 that use exactly the hand's 13 cards. */
  fun usesHand(rows: Rows, hand: List<Int>): Boolean {
    if (rows.size != 3 || rows[0].size != 3 || rows[1].size != 5 || rows[2].size != 5) return false
    val cards = rows.flatten()
    return cards.all { it in 0 until 52 } && cards.toSet().size == 13 && cards.toSet() == hand.toSet()
  }
}
