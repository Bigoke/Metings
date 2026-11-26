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
    const val MAX_CRASHES = 3

    private lateinit var appContext: Context
    private val sp by lazy { appContext.getSharedPreferences("engine_guard", Context.MODE_PRIVATE) }
    private val logFile by lazy { File(appContext.filesDir, "diagnostics.log") }

    fun install(context: Context) {
        appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                log("CRASH in thread ${thread.name} during ${sp.getString("current", "-")}\n$sw")
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun log(text: String) = runCatching {
        if (logFile.length() > 200_000) logFile.writeText(logFile.readText().takeLast(100_000))
        logFile.appendText("[${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())}] $text\n")
    }

    fun begin(id: String, stage: String) {
        sp.edit().putString("current", "$id|$stage").commit()
        log("start $stage for $id")
    }

    fun end() {
        sp.edit().remove("current").commit()
    }

    fun crashes(id: String): Int = sp.getInt("crashes.$id", 0)
    fun lastStage(id: String): String = sp.getString("stage.$id", "") ?: ""

    fun clear(id: String) {
        sp.edit().remove("crashes.$id").remove("stage.$id").apply()
    }

    /** Call at app start: if a step was running when the app last closed, count it as a crash. Returns the meeting id. */
    fun recoverAfterCrash(): String? {
        val cur = sp.getString("current", null) ?: return null
        val (id, stage) = cur.split('|').let { it[0] to it.getOrElse(1) { "" } }
        val n = crashes(id) + 1
        sp.edit().putInt("crashes.$id", n).putString("stage.$id", stage).remove("current").commit()
        log("app closed unexpectedly during $stage for $id (crash $n)")
        recordExitReasons()
        return id
    }

    private fun recordExitReasons() = runCatching {
        if (Build.VERSION.SDK_INT < 30) return@runCatching
        val am = appContext.getSystemService(ActivityManager::class.java)
        am.getHistoricalProcessExitReasons(appContext.packageName, 0, 3).forEach { e ->
            log("exit: reason=${reasonName(e.reason)} importance=${e.importance} pss=${e.pss / 1024}MB rss=${e.rss / 1024}MB desc=${e.description}")
        }
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
