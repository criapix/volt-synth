package com.criapix.volt

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Plain-text log kept on the phone (app-private `files/volt-log.txt`) so a test session can be
 * read back over USB with `tools\pull-log.ps1`. Writes happen on one background thread, so it is
 * safe to call from the UI thread and from the MIDI binder thread. Never call it from the audio thread.
 */
object AppLog {
  private const val MAX_BYTES = 2_000_000L
  private var file: File? = null
  private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "volt-log").apply { isDaemon = true } }
  private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)  // used on the log thread only

  fun init(context: Context) {
    val f = File(context.filesDir, "volt-log.txt")
    file = f
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    log("=== sessão iniciada · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · app $version")
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
      log("CRASH na thread ${t.name}: ${e.stackTraceToString()}")
      flush()
      previous?.uncaughtException(t, e)
    }
  }

  fun log(msg: String) {
    val f = file ?: return
    val now = System.currentTimeMillis()
    exec.execute {
      try {
        if (f.length() > MAX_BYTES) {
          val old = File(f.parentFile, "volt-log.old.txt")
          old.delete()
          f.renameTo(old)
        }
        f.appendText("${fmt.format(Date(now))} $msg\n")
      } catch (_: Throwable) {
      }
    }
  }

  /** Waits (briefly) for queued lines to reach the disk. */
  fun flush() {
    runCatching { exec.submit {}.get(1, TimeUnit.SECONDS) }
  }
}
