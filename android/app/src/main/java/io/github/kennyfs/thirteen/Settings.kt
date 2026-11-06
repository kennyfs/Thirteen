package io.github.kennyfs.thirteen

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** A network: the built-in one (an asset) or one the player loaded. The UI adds "(built-in)" to the name. */
data class Model(val name: String, val path: String, val builtIn: Boolean)

/** An opponent type; see spec(). Labels are R.array.kinds, in this order. */
enum class Kind { NET, MC, INDEP }

data class Seat(val kind: Kind, val model: String)

/** The look of the table: the brand's green felt, plain surfaces with the system's colors, or felt in the wallpaper's colors. */
enum class ColorStyle { TABLE, MINIMAL, WALLPAPER }

enum class DarkMode { SYSTEM, LIGHT, DARK }

/** The app's own text size, on top of the system's font size. */
enum class TextSize(val scale: Float) { SMALL(0.88f), NORMAL(1f), LARGE(1.15f) }

/** Settings and model files, in shared preferences and the app's files directory. */
class Settings(private val context: Context) {
  private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
  private val modelDir = File(context.filesDir, "models").apply { mkdirs() }

  init {
    // The built-in network, copied out of the APK once so the engine can open it by path.
    val target = File(context.filesDir, BUILT_IN)
    val size = context.assets.openFd(BUILT_IN).use { it.length }
    if (target.length() != size) context.assets.open(BUILT_IN).use { input -> target.outputStream().use { input.copyTo(it) } }
    // Earlier built-in networks left by an older version. Seats and the analysis that name them fall back to the
    // current one in model().
    for (old in RETIRED) File(context.filesDir, old).delete()
  }

  fun models(): List<Model> =
    listOf(Model(BUILT_IN.removeSuffix(".onnx"), File(context.filesDir, BUILT_IN).path, true)) +
      (modelDir.listFiles { f -> f.name.endsWith(".onnx") } ?: emptyArray()).sortedBy { it.name }
        .map { Model(it.name.removeSuffix(".onnx"), it.path, false) }

  fun model(path: String): Model = models().firstOrNull { it.path == path } ?: models().first()

  /** Copies a picked .onnx file in; the caller checks that it loads. */
  fun importModel(resolver: ContentResolver, uri: Uri): File {
    var name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) c.getString(0) else null
    } ?: "model.onnx"
    name = name.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    if (!name.endsWith(".onnx")) name += ".onnx"
    val file = File(modelDir, name)
    resolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
    return file
  }

  fun deleteModel(model: Model) {
    if (!model.builtIn) File(model.path).delete()
  }

  fun seat(i: Int): Seat {
    val kind = runCatching { Kind.valueOf(prefs.getString("seat$i.kind", null) ?: "NET") }.getOrDefault(Kind.NET)
    return Seat(kind, model(prefs.getString("seat$i.model", "") ?: "").path)
  }

  fun setSeat(i: Int, seat: Seat) {
    prefs.edit().putString("seat$i.kind", seat.kind.name).putString("seat$i.model", seat.model).apply()
  }

  /** The welcome card was shown (and answered, or put away): it comes once. */
  var welcomeSeen: Boolean
    get() = prefs.getBoolean("welcome.seen", false)
    set(v) = prefs.edit().putBoolean("welcome.seen", v).apply()

  var mcSamples: Int
    get() = prefs.getInt("mc.samples", 200)
    set(v) = prefs.edit().putInt("mc.samples", v).apply()

  var analyzeModel: String
    get() = model(prefs.getString("analyze.model", "") ?: "").path
    set(v) = prefs.edit().putString("analyze.model", v).apply()

  /** The engine's player spec (cpp/players/players.cpp) for a seat. */
  fun spec(seat: Seat): String = when (seat.kind) {
    Kind.NET -> "net:model=${model(seat.model).path}"
    Kind.MC -> "mc:samples=$mcSamples,opp=indep"
    Kind.INDEP -> "indep"
  }

  fun seatName(seat: Seat): String = when (seat.kind) {
    Kind.NET -> model(seat.model).name
    Kind.MC -> "mc $mcSamples"
    Kind.INDEP -> "indep"
  }

  var colorStyle: ColorStyle
    get() = enumPref("look.colors", ColorStyle.TABLE)
    set(v) = prefs.edit().putString("look.colors", v.name).apply()

  var darkMode: DarkMode
    get() = enumPref("look.dark", DarkMode.SYSTEM)
    set(v) = prefs.edit().putString("look.dark", v.name).apply()

  var textSize: TextSize
    // "LARGER", the largest of older versions, is the largest now.
    get() = if (prefs.getString("look.text", null) == "LARGER") TextSize.LARGE else enumPref("look.text", TextSize.NORMAL)
    set(v) = prefs.edit().putString("look.text", v.name).apply()

  private inline fun <reified E : Enum<E>> enumPref(key: String, default: E): E =
    runCatching { enumValueOf<E>(prefs.getString(key, null) ?: default.name) }.getOrDefault(default)

  /** The rules chosen on the rules page; a game takes them when it is dealt. */
  var rules: RuleSpec
    get() = RuleSpec.parse(prefs.getString("rules", "family") ?: "family")
    set(v) = prefs.edit().putString("rules", v.spec()).apply()

  /** The full rules (the engine's toString) the session totals were played under. */
  var sessionRules: String
    get() = prefs.getString("session.rules", "") ?: ""
    set(v) = prefs.edit().putString("session.rules", v).apply()

  /** Running totals of the hands played: hands, then each seat's total (0 is the player). */
  fun session(): Pair<Int, List<Double>> =
    prefs.getInt("session.hands", 0) to List(4) { prefs.getFloat("session.total$it", 0f).toDouble() }

  fun addToSession(result: List<Double>) {
    val (hands, totals) = session()
    val e = prefs.edit().putInt("session.hands", hands + 1)
    for (i in 0 until 4) e.putFloat("session.total$i", (totals[i] + result[i]).toFloat())
    e.apply()
  }

  fun resetSession() {
    val e = prefs.edit().putInt("session.hands", 0)
    for (i in 0 until 4) e.putFloat("session.total$i", 0f)
    e.apply()
  }

  companion object {
    const val BUILT_IN = "run2_net10.onnx"
    val RETIRED = listOf("net_10.onnx")
  }
}
