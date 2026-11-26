package com.bigoke.metings.engine

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
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
    val description: String,
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
) {
    val fraction: Float get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
}

class ModelManager(private val context: Context) {

    companion object {
        private const val RELEASE = "https://github.com/Bigoke/Metings/releases/download/models-v1"
        private const val MB = 1024L * 1024L

        val SPECS = listOf(
            ModelSpec(
                ModelKind.TRANSCRIBE,
                "Afrikaans + English speech",
                "Fluister turbo — Whisper tuned for South African Afrikaans and English, incl. code-switching.",
                "ggml-fluister-turbo-q8_0.bin",
                "$RELEASE/ggml-fluister-turbo-q8_0.bin",
                870 * MB, 600 * MB,
            ),
            ModelSpec(
                ModelKind.SEGMENT,
                "Speech detection",
                "Finds where people speak and where voices change (pyannote 3.0).",
                "pyannote-segmentation-3-0.onnx",
                "$RELEASE/pyannote-segmentation-3-0.onnx",
                6 * MB, 4 * MB,
            ),
            ModelSpec(
                ModelKind.VOICEPRINT,
                "Voice recognition",
                "Tells voices apart by pitch and sound to label Person 1, Person 2… (TitaNet large).",
                "nemo_en_titanet_large.onnx",
                "$RELEASE/nemo_en_titanet_large.onnx",
                97 * MB, 80 * MB,
            ),
            ModelSpec(
                ModelKind.SUMMARY,
                "Meeting summary AI",
                "Gemma 4 E2B — writes the summary, decisions and action items on the phone.",
                "gemma-4-E2B-it.litertlm",
                "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
                2590 * MB, 2000 * MB,
            ),
        )
    }

    private val dir: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "models").apply { mkdirs() }
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val sp = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var poller: Job? = null

    private val _status = MutableStateFlow(SPECS.map { computeStatus(it) })
    val status: StateFlow<List<ModelStatus>> = _status

    init {
        if (_status.value.any { it.state == DownloadState.DOWNLOADING || it.state == DownloadState.QUEUED }) startPolling()
    }

    fun file(kind: ModelKind): File = File(dir, SPECS.first { it.kind == kind }.fileName)

    fun isReady(kind: ModelKind): Boolean {
        val spec = SPECS.first { it.kind == kind }
        val f = File(dir, spec.fileName)
        return f.exists() && f.length() >= spec.minBytes
    }

    /** Speech engine is required; the summary AI is optional (falls back to key sentences). */
    val coreReady: Boolean
        get() = isReady(ModelKind.TRANSCRIBE) && isReady(ModelKind.SEGMENT) && isReady(ModelKind.VOICEPRINT)

    val totalBytes: Long get() = SPECS.sumOf { it.approxBytes }

    fun downloadAll() {
        SPECS.forEach { if (!isReady(it.kind)) download(it.kind) }
    }

    fun download(kind: ModelKind) {
        val spec = SPECS.first { it.kind == kind }
        if (isReady(kind)) return
        val existing = sp.getLong(spec.fileName, -1L)
        if (existing >= 0 && queryState(existing)?.first in listOf(DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED)) {
            startPolling(); return
        }
        File(dir, spec.fileName + ".part").delete()
        val req = DownloadManager.Request(Uri.parse(spec.url))
            .setTitle("Metings: ${spec.title}")
            .setDescription(spec.description)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(File(dir, spec.fileName + ".part")))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val id = dm.enqueue(req)
        sp.edit().putLong(spec.fileName, id).apply()
        refresh()
        startPolling()
    }

    fun cancel(kind: ModelKind) {
        val spec = SPECS.first { it.kind == kind }
        val id = sp.getLong(spec.fileName, -1L)
        if (id >= 0) dm.remove(id)
        sp.edit().remove(spec.fileName).apply()
        File(dir, spec.fileName + ".part").delete()
        refresh()
    }

    fun delete(kind: ModelKind) {
        cancel(kind)
        file(kind).delete()
        refresh()
    }

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
        val f = File(dir, spec.fileName)
        if (f.exists() && f.length() >= spec.minBytes) return ModelStatus(spec, DownloadState.READY, f.length(), f.length())
        val id = sp.getLong(spec.fileName, -1L)
        if (id < 0) return ModelStatus(spec, DownloadState.MISSING)
        val q = queryState(id) ?: run {
            sp.edit().remove(spec.fileName).apply()
            return ModelStatus(spec, DownloadState.MISSING)
        }
        val (st, done, total) = q
        val t = if (total > 0) total else spec.approxBytes
        return when (st) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                val part = File(dir, spec.fileName + ".part")
                if (part.exists() && part.length() >= spec.minBytes) {
                    part.renameTo(f)
                    sp.edit().remove(spec.fileName).apply()
                    ModelStatus(spec, DownloadState.READY, f.length(), f.length())
                } else {
                    sp.edit().remove(spec.fileName).apply()
                    part.delete()
                    ModelStatus(spec, DownloadState.FAILED, message = "Download incomplete — tap to retry")
                }
            }
            DownloadManager.STATUS_FAILED -> {
                sp.edit().remove(spec.fileName).apply()
                ModelStatus(spec, DownloadState.FAILED, message = "Download failed — check your connection and retry")
            }
            DownloadManager.STATUS_PENDING -> ModelStatus(spec, DownloadState.QUEUED, done, t)
            DownloadManager.STATUS_PAUSED -> ModelStatus(spec, DownloadState.DOWNLOADING, done, t, "Waiting for network…")
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

    fun freeSpaceBytes(): Long = dir.usableSpace
}
