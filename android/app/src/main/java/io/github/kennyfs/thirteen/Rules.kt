package io.github.kennyfs.thirteen

/**
 * Scoring rules as the engine takes them (cpp/game/rules.h, docs/rules.md section 11): a preset plus name=value
 * overrides. The app always plays with c = 0, so c is never set here.
 */
data class RuleSpec(val preset: String = "family", val overrides: Map<String, Double> = emptyMap()) {
  /** The spec syntax, "taiwan,scoop=2". */
  fun spec(): String = (listOf(preset) + overrides.map { (k, v) -> "$k=${Rules.fmt(v)}" }).joinToString(",")

  companion object {
    fun parse(s: String): RuleSpec {
      val parts = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }
      if (parts.isEmpty() || parts[0] !in Rules.PRESETS) return RuleSpec()
      return RuleSpec(parts[0], parts.drop(1).mapNotNull { p ->
        val kv = p.split("=")
        kv.getOrNull(1)?.toDoubleOrNull()?.let { kv[0] to it }
      }.toMap())
    }
  }
}

object Rules {
  val PRESETS = listOf("family", "family_mid2", "taiwan", "pagat")
  /** The presets offered to choose from; the others are still named when a game or record has them. */
  val OFFERED = listOf("family", "taiwan", "pagat")
  /** The numbers on the rules page, in R.array.rule_labels order. */
  val POINTS = listOf("row", "scoop", "home_run", "dragon")
  val ROYALTIES = listOf(
    "front_trips", "middle_full_house", "middle_quads", "middle_straight_flush", "back_quads", "back_straight_flush",
  )

  /** The highest value a stepper offers. */
  const val MAX_VALUE = 100.0
  /** Royalties the engine wants in order, low to high (cpp/game/rules.cpp, Rules::check). */
  private val ORDERED = listOf(listOf("middle_full_house", "middle_quads", "middle_straight_flush"), listOf("back_quads", "back_straight_flush"))

  /** The values `name` may take given the others: at least 0 and the royalty below it, at most the one above. */
  fun range(name: String, values: Map<String, Double>): ClosedFloatingPointRange<Double> {
    val chain = ORDERED.firstOrNull { name in it } ?: return 0.0..MAX_VALUE
    val i = chain.indexOf(name)
    val lo = chain.getOrNull(i - 1)?.let { values[it] } ?: 0.0
    val hi = chain.getOrNull(i + 1)?.let { values[it] } ?: MAX_VALUE
    return lo..hi
  }

  fun fmt(x: Double): String = if (x == kotlin.math.floor(x)) "%.0f".format(x) else x.toString()

  /** The values of a full spec ("c=0,row=1,...", as the engine's toString and the records' rules field). */
  fun values(full: String): Map<String, Double> =
    full.split(",").mapNotNull { p -> p.split("=").takeIf { it.size == 2 }?.let { (k, v) -> v.toDoubleOrNull()?.let { k.trim() to it } } }.toMap()

  /** Whether two sets of values are the same rules, whatever the dealer cost. */
  fun same(a: Map<String, Double>, b: Map<String, Double>) = (a.keys + b.keys).all { it == "c" || a[it] == b[it] }

  /** The preset with these values, or null for custom rules. */
  fun presetOf(values: Map<String, Double>, presets: Map<String, Map<String, Double>>): String? =
    PRESETS.firstOrNull { p -> presets[p]?.let { same(it, values) } == true }

  /**
   * The Q gap to the best candidate worth a red mark: 0.1 under family, scaled with what a hand is worth. With run2's
   * net_10 over 300 random hands, the median gap from the best to the 2nd..6th candidate is 0.049 under family and
   * about 3, 8.7 and 9 times that under taiwan with row=0, pagat and taiwan, which scoop + 6 * row matches.
   */
  fun notableQGap(values: Map<String, Double>): Double =
    0.1 * ((values["scoop"] ?: 1.0) + 6 * (values["row"] ?: 0.0)).coerceAtLeast(1.0)
}
