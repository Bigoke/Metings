package com.bigoke.metings.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.bigoke.metings.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Remembers which processing step was running, so that if the app is closed by a crash or by the
 * phone running out of memory, the next attempt runs in a safer mode instead of crashing again,
 * and the user can send a diagnostic report.
 */
object EngineGuard {

    const val VOICES = "voices"
    const val TRANSCRIBE = "transcribe"
    const val PUNCTUATE = "punctuate"
    const val SUMMARY = "summary"
    const val AUDIO = "audio"
    const val ASK = "ask"
    const val MAX_CRASHES = 4

    private lateinit var appContext: Context
    /** Shared between the app and the separate engine process, so it is a plain file read fresh each time. */
    private val stateFile by lazy { File(appContext.filesDir, "engine_state.properties") }
    private val progressFile by lazy { File(appContext.filesDir, "engine_progress.properties") }

    @Synchronized
    private fun load(f: File = stateFile): java.util.Properties = java.util.Properties().apply {
        runCatching { if (f.exists()) f.inputStream().use { load(it) } }
    }

    @Synchronized
    private fun edit(f: File = stateFile, change: (java.util.Properties) -> Unit) {
        val p = load(f)
        change(p)
        val tmp = File(f.path + ".tmp")
        tmp.outputStream().use { p.store(it, null) }
        tmp.renameTo(f)
    }

    fun current(): String? = load().getProperty("current")

    /** Written by the engine process; read by the app to show progress. */
    fun writeProgress(id: String, stage: String, fraction: Float, overall: Float) = runCatching {
        edit(progressFile) { p ->
            p.clear()
            p.setProperty("id", id); p.setProperty("stage", stage)
            p.setProperty("fraction", fraction.toString()); p.setProperty("overall", overall.toString())
            p.setProperty("time", System.currentTimeMillis().toString())
        }
    }

    fun clearProgress() = runCatching { progressFile.delete() }

    data class Progress(val id: String, val stage: String, val fraction: Float, val overall: Float, val time: Long)

    fun readProgress(): Progress? = runCatching {
        val p = load(progressFile)
        val id = p.getProperty("id") ?: return null
        Progress(id, p.getProperty("stage", ""), p.getProperty("fraction", "0").toFloat(), p.getProperty("overall", "0").toFloat(), p.getProperty("time", "0").toLong())
    }.getOrNull()
    private val logFile by lazy { File(appContext.filesDir, "diagnostics.log") }

    /** True when the separate processing process (":engine") is alive. */
    fun engineRunning(): Boolean = runCatching {
        appContext.getSystemService(ActivityManager::class.java).runningAppProcesses
            ?.any { it.processName.endsWith(":engine") } == true
    }.getOrDefault(false)

    fun install(context: Context) {
        appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                log("CRASH in thread ${thread.name} during ${current() ?: "-"}\n$sw")
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun log(text: String) = runCatching {
        if (logFile.length() > 200_000) logFile.writeText(logFile.readText().takeLast(100_000))
        logFile.appendText("[${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())}] $text\n")
    }

    fun begin(id: String, stage: String) {
        edit { it.setProperty("current", "$id|$stage") }
        log("start $stage for $id")
    }

    fun end() {
        edit { it.remove("current") }
    }

    fun crashes(id: String): Int = load().getProperty("crashes.$id", "0").toIntOrNull() ?: 0
    fun lastStage(id: String): String = load().getProperty("stage.$id", "")

    fun clear(id: String) {
        edit { it.remove("crashes.$id"); it.remove("stage.$id") }
    }

    /** Call at app start: if a step was running when the app last closed, count it as a crash. Returns the meeting id. */
    fun recoverAfterCrash(): String? {
        val cur = current() ?: return null
        val (id, stage) = cur.split('|').let { it[0] to it.getOrElse(1) { "" } }
        val n = crashes(id) + 1
        edit { it.setProperty("crashes.$id", n.toString()); it.setProperty("stage.$id", stage); it.remove("current") }
        clearProgress()
        log("app closed unexpectedly during $stage for $id (crash $n)")
        recordExitReasons()
        return id
    }

    private fun recordExitReasons() = runCatching {
        if (Build.VERSION.SDK_INT < 30) return@runCatching
        val am = appContext.getSystemService(ActivityManager::class.java)
        val seen = load().getProperty("loggedExits", "")
        val logged = ArrayList<String>()
        am.getHistoricalProcessExitReasons(appContext.packageName, 0, 5).forEach { e ->
            val key = e.timestamp.toString()
            logged += key
            if (key in seen.split(',')) return@forEach
            log("exit: ${e.processName} reason=${reasonName(e.reason)} importance=${e.importance} desc=${e.description}")
            // Native crashes come with a tombstone; keep its readable text (abort message, backtrace).
            if (e.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) runCatching {
                e.traceInputStream?.use { input ->
                    val bytes = input.readBytes()
                    val text = StringBuilder()
                    val cur = StringBuilder()
                    for (b in bytes) {
                        val c = b.toInt() and 0xff
                        if (c in 32..126) cur.append(c.toChar()) else {
                            if (cur.length >= 6) text.append(cur).append('\n')
                            cur.clear()
                        }
                        if (text.length > 6000) break
                    }
                    log("tombstone:\n$text")
                }
            }
        }
        edit { it.setProperty("loggedExits", logged.joinToString(",")) }
    }

    private fun reasonName(r: Int) = when (r) {
        ApplicationExitInfo.REASON_CRASH -> "crash (Java)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash (native)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
        ApplicationExitInfo.REASON_ANR -> "not responding"
        ApplicationExitInfo.REASON_SIGNALED -> "killed by signal"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "closed by user"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive resource use"
        ApplicationExitInfo.REASON_OTHER -> "other"
        else -> "code $r"
    }

    fun stageLabel(stage: String): String = when (stage) {
        VOICES -> Ui.t("recognising voices", "stemme herken")
        TRANSCRIBE -> Ui.t("transcribing", "transkribeer")
        PUNCTUATE -> Ui.t("adding punctuation", "leestekens byvoeg")
        SUMMARY -> Ui.t("writing the summary", "opsomming skryf")
        AUDIO -> Ui.t("saving audio", "klank stoor")
        else -> stage
    }

    fun report(extra: String): String {
        recordExitReasons()
        val sb = StringBuilder()
        sb.appendLine("Notule ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.SUPPORTED_ABIS.joinToString()}")
        val mi = ActivityManager.MemoryInfo()
        appContext.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        sb.appendLine("RAM: ${mi.totalMem / (1 shl 20)} MB total, ${mi.availMem / (1 shl 20)} MB free; heap max ${Runtime.getRuntime().maxMemory() / (1 shl 20)} MB")
        sb.appendLine(extra)
        sb.appendLine("---- log ----")
        sb.appendLine(runCatching { logFile.readText().takeLast(60_000) }.getOrDefault("(empty)"))
        return sb.toString()
    }
}
