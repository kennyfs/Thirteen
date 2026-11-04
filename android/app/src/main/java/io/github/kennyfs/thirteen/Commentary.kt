package io.github.kennyfs.thirteen

/**
 * Short remarks on an analysis: names for common arrangements, what kind of choice the best one is, and how the
 * player's arrangement could do better. Kept free of Android so the unit tests can run it; the UI words them
 * (ui/Remarks.kt).
 */
object Commentary {
  // Row categories (HandEval::Category).
  const val HIGH = 1
  const val PAIR = 2
  const val TWO_PAIR = 3
  const val TRIPS = 4
  const val STRAIGHT = 5
  const val FLUSH = 6
  const val FULL_HOUSE = 7

  // Scoring::Target indices in a candidate's probabilities.
  private const val SCOOP = 0
  private const val SCOOPED = 1
  private const val HOME_RUN = 2
  private const val WIN = 5
  private const val LOSE = 8

  /** Names of arrangements by their row categories, a classification only: none of them scores anything. */
  enum class Shape {
    THREE_PAIRS, // 三輪車: a pair in every row.
    FOUR_PAIRS, // 四輪車: a pair in front and middle, two pair in back.
    FIVE_PAIRS, // 五輪車: a pair in front, two pair in middle and back.
    TWO_FLUSHES, THREE_FLUSHES, TWO_STRAIGHTS, THREE_STRAIGHTS, STRAIGHT_AND_FLUSH, TWO_FULL_HOUSES, TWO_TRIPS,
  }

  /** The name of an arrangement, or null. `wheel` is the rules' ranking of A-2-3-4-5 (2: not a straight). */
  fun shapeOf(rows: Arrangement, categories: List<Int>, wheel: Int = 0): Shape? {
    val (f, m, b) = categories
    return when {
      f == PAIR && m == PAIR && b == PAIR -> Shape.THREE_PAIRS
      f == PAIR && m == PAIR && b == TWO_PAIR -> Shape.FOUR_PAIRS
      f == PAIR && m == TWO_PAIR && b == TWO_PAIR -> Shape.FIVE_PAIRS
      m == FLUSH && b == FLUSH -> if (rows[0].map(Cards::suit).toSet().size == 1) Shape.THREE_FLUSHES else Shape.TWO_FLUSHES
      m == STRAIGHT && b == STRAIGHT -> if (frontStraight(rows[0], wheel)) Shape.THREE_STRAIGHTS else Shape.TWO_STRAIGHTS
      m == STRAIGHT && b == FLUSH -> Shape.STRAIGHT_AND_FLUSH
      m == FULL_HOUSE && b == FULL_HOUSE -> Shape.TWO_FULL_HOUSES
      m == TRIPS && b == TRIPS -> Shape.TWO_TRIPS
      else -> null
    }
  }

  /** Three consecutive ranks; A-2-3 too unless A-2-3-4-5 is not a straight. */
  private fun frontStraight(front: List<Int>, wheel: Int): Boolean {
    val r = front.map(Cards::rank).sorted()
    if (r.toSet().size != 3) return false
    return r[2] - r[0] == 2 || (wheel != 2 && r == listOf(0, 1, 12))
  }

  /**
   * How far below the best a choice must be for a "you could do better" remark. The network is not exact: in
   * exploitability tests a best response beat run2's net_10 on 38% of hands by about 0.014 points on those hands
   * under family (0.06 under taiwan), so half of the red-mark gap (0.05 under family) leaves a margin of several
   * times that.
   */
  fun mistakeGap(rules: Map<String, Double>) = 0.5 * Rules.notableQGap(rules)

  sealed interface Remark

  /** The player's arrangement is the best one, or as good as the network can tell (`loss` below mistakeGap). */
  data class Same(val loss: Double) : Remark

  /**
   * The player's arrangement loses `loss` against the best; `kind` says what the best one does differently, `vsMine`
   * how the best one's scoops and scooped compare with the player's.
   */
  data class Mistake(
    val loss: Double, val kind: MistakeKind, val categories: List<Int>, val replaced: List<Int>,
    val vsMine: StyleKind?,
  ) : Remark

  enum class MistakeKind {
    MISSED, // The best has made rows (`categories`) that the player's has not.
    SWAPPED, // As MISSED, but in place of other made rows of the player's (`replaced`).
    BROKE, // The best breaks made rows of the player's (`categories`).
    PAIR_TO_FRONT, // The best puts a pair in front.
    PAIR_OUT_OF_FRONT, // The best keeps the pairs together and the front without one.
    STRONGER_FRONT, // Same categories, a stronger front.
    DETAIL, // Same categories, other cards elsewhere.
    OTHER, // Other categories.
  }

  /** The only valid category triple. */
  data object OnlyChoice : Remark

  /** The best of another category triple is nearly as good. */
  data class Close(val gap: Double) : Remark

  /**
   * Against the best of another category triple, when rows do not pay: better on both scoops and scooped, or a choice
   * between them (more scoops and more scooped: attack; fewer of both: defense), with the strongest gain in `row`.
   */
  data class Style(val kind: StyleKind, val row: Int) : Remark

  enum class StyleKind { BOTH, ATTACK, DEFENSE }

  /** When rows pay, against the best of another category triple: better on rows and scoops, or one of them first. */
  data class RowsOrScoops(val kind: RowsKind) : Remark

  enum class RowsKind { BOTH, ROWS, SCOOPS }

  data class Strong(val scoop: Double) : Remark
  data class Weak(val scooped: Double) : Remark
  data class HomeRunChance(val p: Double) : Remark

  /**
   * The remarks on an analysis with the network's Q and probabilities, the player's first when `mine` is one of the
   * candidates. Empty without a network.
   */
  fun remarks(a: Analysis, mine: Arrangement?): List<Remark> {
    val best = a.cands.firstOrNull() ?: return emptyList()
    val bq = best.q ?: return emptyList()
    val bp = best.probs ?: return emptyList()
    val qGap = Rules.notableQGap(a.rules)
    val out = mutableListOf<Remark>()

    val m = mine?.let { mn -> a.cands.firstOrNull { Cards.sameArrangement(it.arrangement, mn) } }
    if (m?.q != null && m.probs != null) {
      val loss = bq - m.q
      out += if (loss < mistakeGap(a.rules)) Same(loss) else mistake(loss, m, best)
    }

    val alt = a.cands.drop(1).firstOrNull { it.info.categories != best.info.categories && it.q != null && it.probs != null }
    if (alt == null) out += OnlyChoice
    else {
      val gap = bq - alt.q!!
      val ap = alt.probs!!
      if (gap < 0.3 * qGap) out += Close(gap)
      else if ((a.rules["row"] ?: 0.0) > 0) {
        val rows = (0 until 3).sumOf { bp[WIN + it] - bp[LOSE + it] - ap[WIN + it] + ap[LOSE + it] }
        val scoops = bp[SCOOP] - bp[SCOOPED] - ap[SCOOP] + ap[SCOOPED]
        when {
          rows >= -ROW_TOL && styleOf(bp, ap) == StyleKind.BOTH -> out += RowsOrScoops(RowsKind.BOTH)
          rows > 0 && scoops < 0 -> out += RowsOrScoops(RowsKind.ROWS)
          rows < 0 && scoops > 0 -> out += RowsOrScoops(RowsKind.SCOOPS)
        }
      }
      else styleOf(bp, ap)?.let { out += Style(it, (0 until 3).maxBy { r -> bp[WIN + r] - ap[WIN + r] }) }
    }

    when {
      bp[SCOOP] >= 0.3 -> out += Strong(bp[SCOOP])
      bp[SCOOPED] >= 0.3 -> out += Weak(bp[SCOOPED])
    }
    if (bp[HOME_RUN] >= 0.02) out += HomeRunChance(bp[HOME_RUN])
    return out
  }

  private const val TOL = 0.01
  private const val ROW_TOL = 0.02

  /** How `best` compares with `other` on scoops and scooped; null when neither is better by more than TOL. */
  private fun styleOf(best: List<Double>, other: List<Double>): StyleKind? {
    val ds = best[SCOOP] - other[SCOOP]
    val dd = best[SCOOPED] - other[SCOOPED]
    return when {
      ds >= -TOL && dd <= TOL -> if (ds > TOL || dd < -TOL) StyleKind.BOTH else null
      ds > 0 && dd > 0 -> StyleKind.ATTACK
      ds < 0 && dd < 0 -> StyleKind.DEFENSE
      else -> null
    }
  }

  private fun mistake(loss: Double, m: Candidate, best: Candidate): Mistake {
    val mc = m.info.categories
    val bc = best.info.categories
    val vsMine = styleOf(best.probs!!, m.probs!!)
    fun of(kind: MistakeKind, categories: List<Int> = emptyList(), replaced: List<Int> = emptyList()) =
      Mistake(loss, kind, categories, replaced, vsMine)
    if (mc == bc) {
      return of(if (best.info.keys[0] > m.info.keys[0]) MistakeKind.STRONGER_FRONT else MistakeKind.DETAIL)
    }
    // Made rows: a straight or better, in the middle or back.
    val bestMade = (1 until 3).filter { bc[it] >= STRAIGHT }
    val mineMade = (1 until 3).map { mc[it] }.filter { it >= STRAIGHT }.toMutableList()
    val missed = bestMade.filter { r -> !mineMade.remove(bc[r]) }
    if (missed.isNotEmpty())
      return of(if (mineMade.isEmpty()) MistakeKind.MISSED else MistakeKind.SWAPPED, missed.map { bc[it] }, mineMade)
    if (mineMade.isNotEmpty()) return of(MistakeKind.BROKE, mineMade)
    if (mc[0] == HIGH && bc[0] == PAIR) return of(MistakeKind.PAIR_TO_FRONT)
    if (mc[0] == PAIR && bc[0] == HIGH) return of(MistakeKind.PAIR_OUT_OF_FRONT)
    return of(MistakeKind.OTHER)
  }
}
