package io.github.kennyfs.thirteen.server

import io.github.kennyfs.thirteen.protocol.EngineError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A fixed number of `thirteen gui` processes, one request at a time each. The model runs here and nowhere else on
 * the server, so this is where its cost is bounded:
 *
 * - `workers` processes at most, each with one ONNX Runtime thread (NetModel), so the engine never takes more than
 *   `workers` cores whatever the rooms do;
 * - at most `maxQueue` requests wait for a process; beyond that a request fails at once (EngineError: busy) and its
 *   room closes, instead of piling up;
 * - a request that takes longer than `timeoutMs` kills its process, which is then started again.
 *
 * Only Room and Hub make requests, with values they checked, through EngineClient; the processes get no environment
 * but PATH and read nothing from clients but those requests.
 */
class EnginePool(
  private val binary: String,
  private val workers: Int,
  private val maxQueue: Int,
  private val timeoutMs: Long,
) {
  private inner class Worker {
    @Volatile private var process: Process? = null
    private var input: BufferedWriter? = null
    private var output: BufferedReader? = null

    private fun start() {
      val pb = ProcessBuilder(binary, "gui").redirectError(ProcessBuilder.Redirect.DISCARD)
      pb.environment().keys.retainAll(setOf("PATH"))
      val p = pb.start()
      process = p
      input = p.outputStream.bufferedWriter()
      output = p.inputStream.bufferedReader()
    }

    @Synchronized
    fun call(line: String): String {
      if (process?.isAlive != true) start()
      input!!.apply {
        write(line)
        newLine()
        flush()
      }
      return output!!.readLine() ?: throw EngineError("the engine stopped")
    }

    fun kill() {
      process?.destroyForcibly()?.waitFor(5, TimeUnit.SECONDS)
      process = null
    }
  }

  private val idle = Channel<Worker>(workers).also { ch -> repeat(workers) { ch.trySend(Worker()) } }
  private val waiting = AtomicInteger(0)

  init {
    require(File(binary).canExecute()) { "No engine at $binary" }
  }

  suspend fun call(line: String): String {
    if (waiting.incrementAndGet() > maxQueue + workers) {
      waiting.decrementAndGet()
      throw EngineError("busy")
    }
    try {
      val w = idle.receive()
      try {
        // A read from the process does not stop for a cancellation: the watchdog kills the process, which ends it.
        return coroutineScope {
          val watchdog = launch {
            delay(timeoutMs)
            w.kill()
          }
          try {
            withContext(Dispatchers.IO) { w.call(line) }
          } finally {
            watchdog.cancel()
          }
        }
      } catch (e: Exception) {
        // Timed out or broken: the process may be in the middle of an answer, so it is not used again.
        w.kill()
        throw if (e is EngineError) e else EngineError("engine: ${e.javaClass.simpleName}")
      } finally {
        idle.trySend(w)
      }
    } finally {
      waiting.decrementAndGet()
    }
  }

  fun close() {
    while (true) idle.tryReceive().getOrNull()?.kill() ?: break
  }
}
