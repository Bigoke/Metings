package com.bigoke.metings.engine

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class ModelKind { TRANSCRIBE, SEGMENT, VOICEPRINT, SUMMARY }

data class ModelSpec(
    val kind: ModelKind,
    val title: String,
    val titleAf: String,
    val description: String,
    val descriptionAf: String,
    val fileName: String,
    val url: String,
    val approxBytes: Long,
    /** A smaller file than this is treated as broken / incomplete. */
    val minBytes: Long,
)

enum class DownloadState { MISSING, QUEUED, DOWNLOADING, READY, FAILED }

data class ModelStatus(
    val spec: ModelSpec,
    val state: DownloadState,
    val downloaded: Long = 0,
    val total: Long = spec.approxBytes,
    val message: String? = null,
    val shared: Boolean = false,
) {
    val fraction: Float get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * Downloads and finds the offline models.
 *
 * Models are kept in Download/Notule/models when "All files access" is allowed: that folder is
 * outside the app, so models survive app updates, reinstalls and even a different signing key.
 * Without that permission they are kept in the app's own folder (kept across normal updates).
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val RELEASE = "https://github.com/Bigoke/Metings/releases/download/models-v1"
        private const val MB = 1024L * 1024L
        const val SHARED_SUBDIR = "Notule/models"

        val SPECS = listOf(
            ModelSpec(
                ModelKind.TRANSCRIBE,
                "Afrikaans + English speech", "Afrikaanse + Engelse spraak",
                "Fluister turbo — Whisper tuned for South African Afrikaans and English, incl. code-switching.",
                "Fluister turbo — Whisper ingestel vir Suid-Afrikaanse Afrikaans en Engels, ook gemengde spraak.",
                "ggml-fluister-turbo-q8_0.bin",
                "$RELEASE/ggml-fluister-turbo-q8_0.bin",
                834 * MB, 600 * MB,
            ),
            ModelSpec(
                ModelKind.SEGMENT,
                "Speech detection", "Spraakherkenning",
                "Finds where people speak and where voices change (pyannote 3.0).",
                "Vind waar mense praat en waar stemme verander (pyannote 3.0).",
                "pyannote-segmentation-3-0.onnx",
                "$RELEASE/pyannote-segmentation-3-0.onnx",
                6 * MB, 4 * MB,
            ),
            ModelSpec(
                ModelKind.VOICEPRINT,
                "Voice recognition", "Stemherkenning",
                "Tells voices apart by pitch and sound to label Person 1, Person 2… (TitaNet large).",
                "Onderskei stemme volgens toonhoogte en klank: Persoon 1, Persoon 2… (TitaNet large).",
                "nemo_en_titanet_large.onnx",
                "$RELEASE/nemo_en_titanet_large.onnx",
                97 * MB, 80 * MB,
            ),
            ModelSpec(
                ModelKind.SUMMARY,
                "Meeting summary AI", "Opsomming-KI",
                "Gemma 4 E2B — punctuation, summary, decisions and action items, on the phone.",
                "Gemma 4 E2B — leestekens, opsomming, besluite en aksiepunte, op die foon.",
                "gemma-4-E2B-it.litertlm",
                "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
                2470 * MB, 2000 * MB,
            ),
        )
    }

    private val privateDir: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "models").apply { mkdirs() }
    private val sharedDir: File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SHARED_SUBDIR)
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val sp = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var poller: Job? = null

    private val _status = MutableStateFlow(SPECS.map { computeStatus(it) })
    val status: StateFlow<List<ModelStatus>> = _status

    private val _moving = MutableStateFlow<Float?>(null)
    /** Progress of moving models into the shared folder, or null when idle. */
    val moving: StateFlow<Float?> = _moving

    init {
        if (_status.value.any { it.state == DownloadState.DOWNLOADING || it.state == DownloadState.QUEUED }) startPolling()
    }

    /** "All files access" granted, so the shared folder can be used. */
    fun sharedAllowed(): Boolean = Environment.isExternalStorageManager()

    val sharedFolderLabel: String get() = "Download/$SHARED_SUBDIR"

    private fun spec(kind: ModelKind) = SPECS.first { it.kind == kind }

    private fun ok(f: File, spec: ModelSpec) = runCatching { f.exists() && f.length() >= spec.minBytes }.getOrDefault(false)

    /** Where the model currently is (shared folder first), or where it would be downloaded to. */
    fun file(kind: ModelKind): File {
        val s = spec(kind)
        if (sharedAllowed()) {
            val shared = File(sharedDir, s.fileName)
            if (ok(shared, s)) return shared
        }
        val private = File(privateDir, s.fileName)
        if (ok(private, s)) return private
        return File(targetDir(), s.fileName)
    }

    private fun targetDir(): File =
        if (sharedAllowed()) sharedDir.apply { mkdirs() } else privateDir

    fun isReady(kind: ModelKind): Boolean = ok(file(kind), spec(kind))

    /** Speech engine is required; the summary AI is optional (falls back to key sentences). */
    val coreReady: Boolean
        get() = isReady(ModelKind.TRANSCRIBE) && isReady(ModelKind.SEGMENT) && isReady(ModelKind.VOICEPRINT)

    val totalBytes: Long get() = SPECS.sumOf { it.approxBytes }

    fun downloadAll() {
        SPECS.forEach { if (!isReady(it.kind)) download(it.kind) }
    }

    fun download(kind: ModelKind) {
        val spec = spec(kind)
        if (isReady(kind)) return
        val existing = sp.getLong(spec.fileName, -1L)
        if (existing >= 0 && queryState(existing)?.first in listOf(DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED)) {
            startPolling(); return
        }
        val shared = sharedAllowed()
        val req = DownloadManager.Request(Uri.parse(spec.url))
            .setTitle("Notule: ${spec.title}")
            .setDescription(spec.description)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        if (shared) {
            sharedDir.mkdirs()
            File(sharedDir, spec.fileName + ".part").delete()
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$SHARED_SUBDIR/${spec.fileName}.part")
        } else {
            File(privateDir, spec.fileName + ".part").delete()
            req.setDestinationUri(Uri.fromFile(File(privateDir, spec.fileName + ".part")))
        }
        val id = dm.enqueue(req)
        sp.edit().putLong(spec.fileName, id).putBoolean(spec.fileName + ".shared", shared).apply()
        refresh()
        startPolling()
    }

    fun cancel(kind: ModelKind) {
        val spec = spec(kind)
        val id = sp.getLong(spec.fileName, -1L)
        if (id >= 0) dm.remove(id)
        sp.edit().remove(spec.fileName).apply()
        File(privateDir, spec.fileName + ".part").delete()
        runCatching { File(sharedDir, spec.fileName + ".part").delete() }
        refresh()
    }

    fun delete(kind: ModelKind) {
        cancel(kind)
        val spec = spec(kind)
        File(privateDir, spec.fileName).delete()
        runCatching { File(sharedDir, spec.fileName).delete() }
        refresh()
    }

    /** Moves already downloaded models from the app folder into the shared folder (no re-download). */
    fun moveToShared() {
        if (!sharedAllowed() || _moving.value != null) return
        scope.launch {
            try {
                sharedDir.mkdirs()
                val toMove = SPECS.filter { ok(File(privateDir, it.fileName), it) && !ok(File(sharedDir, it.fileName), it) }
                val total = toMove.sumOf { File(privateDir, it.fileName).length() }.coerceAtLeast(1)
                var done = 0L
                _moving.value = 0f
                for (s in toMove) {
                    val src = File(privateDir, s.fileName)
                    val dst = File(sharedDir, s.fileName)
                    val tmp = File(sharedDir, s.fileName + ".moving")
                    if (!src.renameTo(dst)) {
                        src.inputStream().use { input ->
                            tmp.outputStream().use { out ->
                                val buf = ByteArray(1 shl 20)
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    done += n
                                    _moving.value = done.toFloat() / total
                                }
                            }
                        }
                        if (tmp.length() == src.length() && tmp.renameTo(dst)) src.delete() else tmp.delete()
                    } else done += dst.length()
                    _moving.value = done.toFloat() / total
                }
            } finally {
                _moving.value = null
                refresh()
            }
        }
    }

    /** Models still in the app's own folder that could be moved to the shared folder. */
    fun privateModelCount(): Int = SPECS.count { ok(File(privateDir, it.fileName), it) }

    private fun queryState(id: Long): Triple<Int, Long, Long>? {
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return null
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return Triple(status, done, total)
        }
        return null
    }

    private fun computeStatus(spec: ModelSpec): ModelStatus {
        val sharedOk = sharedAllowed() && ok(File(sharedDir, spec.fileName), spec)
        if (sharedOk) return File(sharedDir, spec.fileName).let { ModelStatus(spec, DownloadState.READY, it.length(), it.length(), shared = true) }
        val priv = File(privateDir, spec.fileName)
        if (ok(priv, spec)) return ModelStatus(spec, DownloadState.READY, priv.length(), priv.length())
        val id = sp.getLong(spec.fileName, -1L)
        if (id < 0) return ModelStatus(spec, DownloadState.MISSING)
        val q = queryState(id) ?: run {
            sp.edit().remove(spec.fileName).apply()
            return ModelStatus(spec, DownloadState.MISSING)
        }
        val (st, done, total) = q
        val t = if (total > 0) total else spec.approxBytes
        val toShared = sp.getBoolean(spec.fileName + ".shared", false)
        val dir = if (toShared) sharedDir else privateDir
        return when (st) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                val part = File(dir, spec.fileName + ".part")
                val f = File(dir, spec.fileName)
                if (ok(part, spec) && part.renameTo(f)) {
                    sp.edit().remove(spec.fileName).apply()
                    ModelStatus(spec, DownloadState.READY, f.length(), f.length(), shared = toShared)
                } else if (ok(f, spec)) {
                    sp.edit().remove(spec.fileName).apply()
                    ModelStatus(spec, DownloadState.READY, f.length(), f.length(), shared = toShared)
                } else {
                    sp.edit().remove(spec.fileName).apply()
                    runCatching { part.delete() }
                    ModelStatus(spec, DownloadState.FAILED, message = "incomplete")
                }
            }
            DownloadManager.STATUS_FAILED -> {
                sp.edit().remove(spec.fileName).apply()
                ModelStatus(spec, DownloadState.FAILED, message = "failed")
            }
            DownloadManager.STATUS_PENDING -> ModelStatus(spec, DownloadState.QUEUED, done, t)
            DownloadManager.STATUS_PAUSED -> ModelStatus(spec, DownloadState.DOWNLOADING, done, t, "waiting")
            else -> ModelStatus(spec, DownloadState.DOWNLOADING, done, t)
        }
    }

    fun refresh() {
        _status.value = SPECS.map { computeStatus(it) }
    }

    private fun startPolling() {
        if (poller?.isActive == true) return
        poller = scope.launch {
            while (isActive) {
                refresh()
                if (_status.value.none { it.state == DownloadState.DOWNLOADING || it.state == DownloadState.QUEUED }) break
                delay(1000)
            }
        }
    }

    fun freeSpaceBytes(): Long = privateDir.usableSpace
}
