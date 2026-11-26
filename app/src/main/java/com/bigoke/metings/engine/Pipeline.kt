package com.bigoke.metings.engine

import android.content.Context
import android.util.Log
import com.bigoke.metings.data.Meeting
import com.bigoke.metings.data.MeetingRepository
import com.bigoke.metings.data.MeetingStatus
import com.bigoke.metings.data.Prefs
import com.bigoke.metings.data.Speaker
import com.bigoke.metings.data.Utterance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class ProcessProgress(val stage: String, val fraction: Float, val overall: Float)

/**
 * Turns a recording into a finished meeting:
 * voices → speech regions → transcript → readable text → details → summary → compressed audio.
 */
class Pipeline(
    private val context: Context,
    private val repo: MeetingRepository,
    private val models: ModelManager,
    private val prefs: Prefs,
) {
    companion object {
        private const val TAG = "Pipeline"
        private const val CHUNK_SECONDS = 300
        private const val GAP_SAMPLES = SAMPLE_RATE * 4 / 10
    }

    private val _progress = MutableStateFlow<Map<String, ProcessProgress>>(emptyMap())
    val progress: StateFlow<Map<String, ProcessProgress>> = _progress

    @Volatile private var cancelled = false
    @Volatile private var transcriber: Transcriber? = null

    fun cancel() {
        cancelled = true
        transcriber?.abort()
    }

    private fun report(id: String, stage: String, fraction: Float, from: Float, to: Float) {
        _progress.value = _progress.value + (id to ProcessProgress(stage, fraction, from + (to - from) * fraction))
    }

    private fun clear(id: String) {
        _progress.value = _progress.value - id
    }

    private data class Region(val start: Int, val end: Int)
    private data class Piece(val compactStart: Int, val origStart: Int, val length: Int)

    fun process(id: String, redoSummaryOnly: Boolean = false) {
        cancelled = false
        val started = System.currentTimeMillis()
        repo.get(id) ?: return
        val settings = prefs.settings.value
        try {
            repo.update(id) { it.copy(status = MeetingStatus.PROCESSING, error = null) }
            var m = repo.get(id)!!
            if (!redoSummaryOnly) m = transcribeMeeting(m, settings.removeFillers, settings.threads, settings.beamSize)
            if (cancelled) throw InterruptedException("Stopped")

            // Details
            report(id, "Finding details", 0.5f, 0.82f, 0.86f)
            val names = m.speakers.associate { it.id to it.name }
            m = m.copy(entities = EntityExtractor(m.createdAt).extract(m.utterances, names))
            repo.save(m)

            // Summary
            val sumLang = if (settings.summaryLanguage == "same") m.language else settings.summaryLanguage
            report(id, "Writing summary", 0f, 0.86f, 0.98f)
            val llm = if (models.isReady(ModelKind.SUMMARY)) models.file(ModelKind.SUMMARY) else null
            val summary = Summarizer(context).summarize(m, llm, sumLang, settings.useGpuForSummary) { f ->
                report(id, "Writing summary", f, 0.86f, 0.98f)
            }
            m = m.copy(summary = summary)
            repo.save(m)

            // Keep the audio as a compact attachment
            if (m.audioFile.endsWith(".wav")) {
                report(id, "Saving audio", 0.5f, 0.98f, 1f)
                val wav = repo.audio(m)
                val m4a = File(repo.folder(id), "audio.m4a")
                runCatching { AudioFiles.encodeToM4a(wav, m4a) }
                    .onSuccess {
                        if (m4a.length() > 1000) {
                            m = m.copy(audioFile = "audio.m4a")
                            if (!settings.keepWav) wav.delete()
                        }
                    }
                    .onFailure { Log.w(TAG, "AAC encoding failed, keeping WAV", it) }
            }
            val secs = (System.currentTimeMillis() - started) / 1000
            repo.save(m.copy(status = MeetingStatus.READY, processingSeconds = if (redoSummaryOnly) m.processingSeconds else secs))
        } catch (t: Throwable) {
            Log.e(TAG, "Processing failed", t)
            val msg = if (cancelled) "Processing was stopped. Tap Process again to restart." else (t.message ?: t.javaClass.simpleName)
            repo.update(id) { it.copy(status = MeetingStatus.FAILED, error = msg) }
        } finally {
            transcriber?.close()
            transcriber = null
            clear(id)
        }
    }

    private fun transcribeMeeting(m0: Meeting, removeFillers: Boolean, threads: Int, beam: Int): Meeting {
        val id = m0.id
        if (!models.coreReady) throw IllegalStateException("The speech models are not downloaded yet. Open Models to download them.")
        val wav = repo.audio(m0)
        if (!wav.exists() || !wav.name.endsWith(".wav")) throw IllegalStateException("The original recording is no longer available for re-processing.")

        report(id, "Loading audio", 0.5f, 0f, 0.02f)
        val samples = AudioFiles.readWavFloats(wav)
        val durationMs = samples.size * 1000L / SAMPLE_RATE

        // 1. Who spoke when
        report(id, "Recognising voices", 0f, 0.02f, 0.25f)
        val diarizer = Diarizer(models.file(ModelKind.SEGMENT), models.file(ModelKind.VOICEPRINT), threads.coerceAtMost(4))
        val turns = try {
            diarizer.run(samples, m0.expectedSpeakers) { f -> report(id, "Recognising voices", f, 0.02f, 0.25f) }
        } finally {
            diarizer.release()
        }
        if (cancelled) throw InterruptedException("Stopped")

        // 2. Speech regions (silence removed), grouped into chunks of up to 5 minutes
        val regions = speechRegions(turns, samples.size)
        val chunks = ArrayList<List<Region>>()
        var cur = ArrayList<Region>(); var curLen = 0
        for (r in regions) {
            val len = r.end - r.start
            if (curLen + len > CHUNK_SECONDS * SAMPLE_RATE && cur.isNotEmpty()) { chunks += cur; cur = ArrayList(); curLen = 0 }
            cur += r; curLen += len + GAP_SAMPLES
        }
        if (cur.isNotEmpty()) chunks += cur

        // 3. Speech to text
        val words = ArrayList<Word>()
        val t = Transcriber(models.file(ModelKind.TRANSCRIBE))
        transcriber = t
        val basePrompt = if (m0.language == "af")
            "Goeiemôre almal. Ons begin die vergadering. Die begroting is R15 000 en ons vergader weer op 15 Maart."
        else "Good morning everyone. Let's start the meeting. The budget is R15,000 and we meet again on 15 March."
        var tail = ""
        chunks.forEachIndexed { ci, regs ->
            if (cancelled) throw InterruptedException("Stopped")
            val pieces = ArrayList<Piece>()
            val total = regs.sumOf { it.end - it.start } + GAP_SAMPLES * regs.size
            val buf = FloatArray(total)
            var pos = 0
            for (r in regs) {
                System.arraycopy(samples, r.start, buf, pos, r.end - r.start)
                pieces += Piece(pos, r.start, r.end - r.start)
                pos += (r.end - r.start) + GAP_SAMPLES
            }
            val from = 0.25f + 0.55f * ci / chunks.size
            val to = 0.25f + 0.55f * (ci + 1) / chunks.size
            report(id, "Transcribing", 0f, from, to)
            val watcher = Thread {
                while (!Thread.currentThread().isInterrupted) {
                    report(id, "Transcribing", t.progress() / 100f, from, to)
                    try { Thread.sleep(700) } catch (_: InterruptedException) { break }
                }
            }.apply { isDaemon = true; start() }
            val prompt = (basePrompt + " " + tail).takeLast(600)
            val got = try {
                t.transcribe(buf, m0.language, threads, beam, prompt)
            } finally {
                watcher.interrupt()
            }
            for (w in got) {
                val s = mapTime(w.startMs, pieces)
                val e = mapTime(w.endMs, pieces).coerceAtLeast(s)
                words += w.copy(startMs = s, endMs = e)
            }
            tail = got.takeLast(40).joinToString(" ") { it.text }
        }
        t.close()
        transcriber = null

        // 4. Attach each word to the voice speaking at that moment, then build readable paragraphs
        report(id, "Making it readable", 0.5f, 0.80f, 0.82f)
        val utterances = buildUtterances(words, turns, m0.language, removeFillers)
        val ids = utterances.map { it.speaker }.distinct().sorted()
        val speakers = ids.map { sid -> m0.speakers.firstOrNull { it.id == sid } ?: Speaker(sid, "Person ${sid + 1}") }
        val m = m0.copy(
            durationMs = durationMs,
            utterances = utterances,
            speakers = speakers,
        )
        repo.save(m)
        return m
    }

    private fun speechRegions(turns: List<SpeakerTurn>, nSamples: Int): List<Region> {
        if (turns.isEmpty()) return if (nSamples > SAMPLE_RATE / 2) listOf(Region(0, nSamples)) else emptyList()
        val pad = SAMPLE_RATE / 4
        val raw = turns.map {
            Region(((it.startMs * SAMPLE_RATE / 1000).toInt() - pad).coerceAtLeast(0),
                ((it.endMs * SAMPLE_RATE / 1000).toInt() + pad).coerceAtMost(nSamples))
        }.sortedBy { it.start }
        val merged = ArrayList<Region>()
        for (r in raw) {
            val last = merged.lastOrNull()
            if (last != null && r.start <= last.end + SAMPLE_RATE * 6 / 10) merged[merged.size - 1] = Region(last.start, maxOf(last.end, r.end))
            else merged += r
        }
        // Very long regions are fine for whisper.cpp (it windows internally), but keep chunk sizes bounded.
        val out = ArrayList<Region>()
        val maxLen = CHUNK_SECONDS * SAMPLE_RATE
        for (r in merged) {
            var s = r.start
            while (r.end - s > maxLen) { out += Region(s, s + maxLen); s += maxLen }
            if (r.end > s) out += Region(s, r.end)
        }
        return out
    }

    private fun mapTime(compactMs: Long, pieces: List<Piece>): Long {
        val sample = (compactMs * SAMPLE_RATE / 1000).toInt()
        var best = pieces.first()
        for (p in pieces) {
            if (sample >= p.compactStart) best = p else break
        }
        val offset = (sample - best.compactStart).coerceIn(0, best.length)
        return (best.origStart + offset).toLong() * 1000 / SAMPLE_RATE
    }

    private fun speakerAt(ms: Long, turns: List<SpeakerTurn>, fallback: Int): Int {
        if (turns.isEmpty()) return 0
        var bestDist = Long.MAX_VALUE
        var best = fallback
        for (t in turns) {
            if (ms in t.startMs..t.endMs) return t.speaker
            val d = if (ms < t.startMs) t.startMs - ms else ms - t.endMs
            if (d < bestDist) { bestDist = d; best = t.speaker }
        }
        return best
    }

    private fun buildUtterances(words: List<Word>, turns: List<SpeakerTurn>, lang: String, removeFillers: Boolean): List<Utterance> {
        val out = ArrayList<Utterance>()
        var spk = -1
        var start = 0L; var end = 0L
        val sb = StringBuilder()
        fun flush() {
            if (sb.isEmpty()) return
            val text = TextCleaner.clean(sb.toString(), lang, removeFillers)
            if (text.length > 1) out += Utterance(spk.coerceAtLeast(0), start, end, text)
            sb.clear()
        }
        for (w in words) {
            val s = speakerAt((w.startMs + w.endMs) / 2, turns, if (spk < 0) 0 else spk)
            val longPause = sb.isNotEmpty() && w.startMs - end > 3000
            val longPara = sb.length > 900 && sb.endsWith(".")
            if (s != spk || longPause || longPara) {
                flush()
                spk = s; start = w.startMs
            }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(w.text)
            end = w.endMs
        }
        flush()
        // Merge tiny fragments (e.g. a single mis-assigned word) into the previous paragraph.
        val merged = ArrayList<Utterance>()
        for (u in out) {
            val prev = merged.lastOrNull()
            if (prev != null && u.text.split(" ").size <= 1 && u.startMs - prev.endMs < 1500) {
                merged[merged.size - 1] = prev.copy(text = TextCleaner.clean(prev.text.trimEnd('.') + " " + u.text, lang, false), endMs = u.endMs)
            } else merged += u
        }
        return merged
    }
}
