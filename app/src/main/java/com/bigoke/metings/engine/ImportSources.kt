package com.bigoke.metings.engine

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.bigoke.metings.MetingsApp
import com.bigoke.metings.data.Meeting
import com.bigoke.metings.data.MeetingStatus
import com.bigoke.metings.service.ProcessingService
import com.bigoke.metings.util.Ui
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where an audio file came from. */
enum class ImportSource(val en: String, val af: String) {
    WHATSAPP("WhatsApp", "WhatsApp"),
    TELEGRAM("Telegram", "Telegram"),
    SIGNAL("Signal", "Signal"),
    CALLS("Calls", "Oproepe"),
    TRUECALLER("Truecaller", "Truecaller"),
    RECORDER("Voice recorder", "Stemopnemer"),
    OTHER("Other", "Ander");

    val label get() = Ui.t(en, af)
}

data class ImportCandidate(
    val uri: Uri,
    val name: String,
    val source: ImportSource,
    /** Finer detail, e.g. "Voice note" or "Video". */
    val kind: String,
    val modified: Long,
    val size: Long,
    val durationMs: Long = 0,
)

data class ImportOptions(
    val language: String,
    val template: String,
    val folderBySource: Boolean,
    val transcribe: Boolean,
)

/**
 * Quick import of WhatsApp voice notes, call recordings (Truecaller, the phone's own recorder, Cube ACR…),
 * Telegram/Signal audio and other recordings.
 */
object ImportSources {

    private val audioExt = setOf("opus", "ogg", "oga", "m4a", "mp3", "aac", "amr", "3gp", "3ga", "wav", "flac", "wma", "mp4", "mkv", "webm", "mov", "awb")
    private val videoExt = setOf("mp4", "mkv", "webm", "mov", "3gp")

    fun classify(path: String, name: String = path.substringAfterLast('/')): Pair<ImportSource, String> {
        val p = path.lowercase()
        val n = name.lowercase()
        val video = n.substringAfterLast('.') in videoExt && ("video" in p || n.startsWith("vid"))
        val kindVideo = Ui.t("Video", "Video")
        return when {
            "whatsapp" in p || n.startsWith("ptt-") || Regex("""-wa\d{4}""").containsMatchIn(n) -> ImportSource.WHATSAPP to when {
                "voice notes" in p || n.startsWith("ptt-") -> Ui.t("Voice note", "Stemnota")
                video -> kindVideo
                else -> Ui.t("Audio", "Klank")
            }
            "telegram" in p -> ImportSource.TELEGRAM to if (video) kindVideo else Ui.t("Voice / audio", "Stem / klank")
            "signal" in p -> ImportSource.SIGNAL to if (video) kindVideo else Ui.t("Audio", "Klank")
            "truecaller" in p -> ImportSource.TRUECALLER to Ui.t("Call recording", "Oproepopname")
            "call" in p || "oproep" in p || n.startsWith("call") || "acr" in p.split('/') -> ImportSource.CALLS to Ui.t("Call recording", "Oproepopname")
            "record" in p || "opname" in p || "sound" in p || "voice" in p -> ImportSource.RECORDER to Ui.t("Recording", "Opname")
            else -> ImportSource.OTHER to if (video) kindVideo else Ui.t("Audio", "Klank")
        }
    }

    /** Folders that commonly hold chat audio and call recordings (relative to the shared storage root). */
    private val knownDirs = listOf(
        "Android/media/com.whatsapp/WhatsApp/Media",
        "Android/media/com.whatsapp.w4b/WhatsApp Business/Media",
        "WhatsApp/Media",
        "Android/media/org.telegram.messenger/Telegram",
        "Telegram",
        "Android/media/org.thoughtcrime.securesms",
        "Recordings", "Music/Recordings", "Music", "Call", "CallRecordings", "Call Recordings",
        "MIUI/sound_recorder", "Sounds", "Voice Recorder", "Record", "Recorder",
        "Android/media/com.truecaller", "Truecaller",
        "Download", "Documents",
    )

    fun hasFullAccess(): Boolean = Build.VERSION.SDK_INT >= 30 && runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)

    /** Recent audio/video files (newest first). Uses direct folder access when allowed, otherwise the media library. */
    fun recent(ctx: Context, days: Int = 60, limit: Int = 300): List<ImportCandidate> {
        val since = System.currentTimeMillis() - days * 86_400_000L
        val out = LinkedHashMap<String, ImportCandidate>()
        if (hasFullAccess()) {
            val root = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
            if (root != null) for (rel in knownDirs) {
                val dir = File(root, rel)
                if (!dir.isDirectory) continue
                dir.walkTopDown().maxDepth(5)
                    .onEnter { !it.name.startsWith(".") && it.name != "Sent" || rel.contains("whatsapp", true) }
                    .filter { it.isFile && it.extension.lowercase() in audioExt && it.lastModified() >= since && it.length() > 2_000 }
                    .forEach { f ->
                        if (f.absolutePath in out) return@forEach
                        val (src, kind) = classify(f.absolutePath, f.name)
                        out[f.absolutePath] = ImportCandidate(Uri.fromFile(f), f.name, src, kind, f.lastModified(), f.length())
                    }
            }
        }
        if (out.isEmpty()) out += mediaStore(ctx, since).associateBy { it.uri.toString() }
        return out.values.sortedByDescending { it.modified }.take(limit)
    }

    private fun mediaStore(ctx: Context, since: Long): List<ImportCandidate> {
        val list = ArrayList<ImportCandidate>()
        val tables = listOf(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        )
        for (table in tables) runCatching {
            val cols = arrayOf(
                MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DURATION,
            )
            ctx.contentResolver.query(
                table, cols, "${MediaStore.MediaColumns.DATE_MODIFIED} >= ?", arrayOf((since / 1000).toString()),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1) ?: continue
                    val rel = c.getString(2) ?: ""
                    val (src, kind) = classify("$rel/$name", name)
                    // Plain videos from the camera are not what people want here.
                    if (table == MediaStore.Video.Media.EXTERNAL_CONTENT_URI && src == ImportSource.OTHER) continue
                    list += ImportCandidate(ContentUris.withAppendedId(table, id), name, src, kind, c.getLong(3) * 1000, c.getLong(4), c.getLong(5))
                }
            }
        }
        return list
    }

    fun displayName(ctx: Context, uri: Uri): String? = runCatching {
        if (uri.scheme == "file") return uri.lastPathSegment
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** A readable meeting title, e.g. "WhatsApp voice note · 27 Nov 10:42". */
    fun title(name: String?, source: ImportSource, kind: String, at: Long): String {
        val stamp = SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(at))
        val base = name?.substringBeforeLast('.')?.trim().orEmpty()
        val machine = base.isEmpty() || Regex("""^(PTT|AUD|VID|audio|voice|rec|call|record)[-_ ]?\d|^\d{6,}|-WA\d+""", RegexOption.IGNORE_CASE).containsMatchIn(base)
        return when {
            !machine -> base
            source == ImportSource.OTHER -> Ui.t("Imported", "Ingevoer") + " · " + stamp
            else -> "${source.label} ${kind.lowercase()} · $stamp"
        }
    }

    /** Imports one file as a new meeting. Returns the meeting id, or null when the file has no readable audio. */
    fun importOne(ctx: Context, uri: Uri, opts: ImportOptions, sourceHint: ImportSource? = null, onProgress: (Float) -> Unit = {}): String? {
        val app = MetingsApp.instance
        val name = displayName(ctx, uri)
        val (src, kind) = classify(uri.toString() + "/" + (name ?: ""), name ?: "").let { if (sourceHint != null && it.first == ImportSource.OTHER) sourceHint to it.second else it }
        val id = app.repo.newId()
        return runCatching {
            val wav = File(app.repo.folder(id), "audio.wav")
            val dur = AudioImporter.import(ctx, uri, wav, onProgress)
            val now = System.currentTimeMillis()
            app.repo.save(
                Meeting(
                    id = id, title = title(name, src, kind, now), createdAt = now, durationMs = dur,
                    language = opts.language,
                    status = if (opts.transcribe && app.models.coreReady) MeetingStatus.QUEUED else MeetingStatus.NEW,
                    folder = if (opts.folderBySource && src != ImportSource.OTHER) src.label else "",
                    template = opts.template,
                )
            )
            id
        }.getOrElse { app.repo.delete(id); null }
    }

    fun startProcessing(ctx: Context, ids: List<String>) {
        val app = MetingsApp.instance
        ids.firstOrNull { app.repo.get(it)?.status == MeetingStatus.QUEUED }?.let { ProcessingService.enqueue(ctx, it) }
    }
}
