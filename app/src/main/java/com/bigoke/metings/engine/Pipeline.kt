package com.bigoke.metings.engine

import android.content.Context
import android.util.Log
import com.bigoke.metings.data.Meeting
import com.bigoke.metings.data.MeetingRepository
import com.bigoke.metings.data.MeetingStatus
import com.bigoke.metings.data.Prefs
import com.bigoke.metings.data.Speaker
import com.bigoke.metings.data.Utterance
import com.bigoke.metings.data.VoiceBank
import com.bigoke.metings.util.EngineGuard
import com.bigoke.metings.util.Ui
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
    private val voices: VoiceBank,
    private val corrections: com.bigoke.metings.data.CorrectionBook,
) {
    companion object {
        private const val TAG = "Pipeline"
        private const val CHUNK_SECONDS = 300
        private const val GAP_SAMPLES = SAMPLE_RATE * 4 / 10
        private const val BLOCK_MINUTES = 40

        fun cosine(a: FloatArray, b: FloatArray): Float {
            if (a.isEmpty() || a.size != b.size) return 0f
            var dot = 0.0; var na = 0.0; var nb = 0.0
            for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
            return if (na == 0.0 || nb == 0.0) 0f else (dot / Math.sqrt(na * nb)).toFloat()
        }
    }

    private val _progress = MutableStateFlow<Map<String, ProcessProgress>>(emptyMap())
    val progress: StateFlow<Map<String, ProcessProgress>> = _progress

    @Volatile private var cancelled = false
    @Volatile private var transcriber: Transcriber? = null

    fun cancel() {
        cancelled = true
        transcriber?.abort()
    }

    @Volatile private var lastWrite = 0L

    private fun report(id: String, stage: String, fraction: Float, from: Float, to: Float) {
        val overall = from + (to - from) * fraction
        _progress.value = _progress.value + (id to ProcessProgress(stage, fraction, overall))
        val now = System.currentTimeMillis()
        if (now - lastWrite > 400 || fraction == 0f) {
            lastWrite = now
            EngineGuard.writeProgress(id, stage, fraction, overall)
        }
    }

    private fun clear(id: String) {
        _progress.value = _progress.value - id
        EngineGuard.clearProgress()
    }

    /** In the app process: show progress reported by the separate engine process. */
    fun showExternal(p: EngineGuard.Progress?) {
        val next = if (p == null) emptyMap() else mapOf(p.id to ProcessProgress(p.stage, p.fraction, p.overall))
        if (next != _progress.value) _progress.value = next
    }

    private data class Region(val start: Int, val end: Int)
    private data class Piece(val compactStart: Int, val origStart: Int, val length: Int)

    fun process(id: String, redoSummaryOnly: Boolean = false) {
        cancelled = false
        val started = System.currentTimeMillis()
        repo.get(id) ?: return
        val settings = prefs.settings.value
        // Safe mode after an earlier crash or out-of-memory close in a specific step.
        val crashes = EngineGuard.crashes(id)
        val crashedIn = EngineGuard.lastStage(id)
        if (crashes >= EngineGuard.MAX_CRASHES) {
            repo.update(id) {
                it.copy(status = MeetingStatus.FAILED, error = Ui.t(
                    "Processing kept stopping while ${EngineGuard.stageLabel(crashedIn)}. Allow Notule to run in the background (battery settings), then tap Retry. If it still stops, send a diagnostic report from Settings.",
                    "Verwerking het aanhoudend gestop terwyl dit ${EngineGuard.stageLabel(crashedIn)}. Laat Notule in die agtergrond loop (battery-instellings) en tik dan Probeer weer. As dit steeds stop, stuur 'n diagnoseverslag uit Instellings.",
                ))
            }
            return
        }
        val safe = crashes > 0
        val threads = if (safe) settings.threads.coerceAtMost(4) else settings.threads
        val beam = if (safe && crashedIn == EngineGuard.TRANSCRIBE) 1 else settings.beamSize
        val diarThreads = if (safe && crashedIn == EngineGuard.VOICES) 1 else threads.coerceAtMost(4)
        val aiCrashed = crashedIn == EngineGuard.PUNCTUATE || crashedIn == EngineGuard.SUMMARY
        val useGpu = settings.useGpuForSummary && !(safe && aiCrashed)
        val skipAi = aiCrashed && crashes >= 2
        if (safe) EngineGuard.log("safe mode for $id: crashed in $crashedIn x$crashes; threads=$threads beam=$beam gpu=$useGpu skipAi=$skipAi")
        try {
            repo.update(id) { it.copy(status = MeetingStatus.PROCESSING, error = null) }
            var m = repo.get(id)!!
            val keepTranscript = safe && aiCrashed && m.utterances.isNotEmpty()
            if (!redoSummaryOnly && !keepTranscript) m = transcribeMeeting(m, settings.removeFillers, threads, beam, diarThreads)
            if (cancelled) throw InterruptedException("Stopped")

            val llm = if (!skipAi && models.isReady(ModelKind.SUMMARY)) models.file(ModelKind.SUMMARY) else null
            val summarizer = Summarizer(context, llm, useGpu)
            try {
            // Readability: restore punctuation and capitals where the speech model left them out
            if (!redoSummaryOnly && !keepTranscript && llm != null) {
                EngineGuard.begin(id, EngineGuard.PUNCTUATE)
                report(id, "Adding punctuation", 0f, 0.80f, 0.86f)
                val fixed = summarizer.punctuate(m.utterances.map { it.text }, m.language) { f ->
                    report(id, "Adding punctuation", f, 0.80f, 0.86f)
                }
                m = m.copy(utterances = m.utterances.mapIndexed { i, u ->
                    u.copy(text = TextCleaner.clean(
                        fixed.getOrElse(i) { u.text }, u.lang.ifEmpty { m.language }, false, settings.vocabulary,
                        corrections.asMap(), settings.translateSlang,
                    ))
                })
                repo.save(m)
            }
            if (cancelled) throw InterruptedException("Stopped")

            // Details
            report(id, "Finding details", 0.5f, 0.86f, 0.88f)
            val names = m.speakers.associate { it.id to it.name }
            m = m.copy(entities = EntityExtractor(m.createdAt, settings.vocabulary).extract(m.utterances, names))
            repo.save(m)

            // Summary
            val sumLang = if (settings.summaryLanguage == "same") m.language else settings.summaryLanguage
            EngineGuard.begin(id, EngineGuard.SUMMARY)
            report(id, "Writing summary", 0f, 0.88f, 0.98f)
            val summary = summarizer.summarize(m, sumLang) { f ->
                report(id, "Writing summary", f, 0.88f, 0.98f)
            }
            m = m.copy(summary = summary)
            repo.save(m)
            } finally {
                summarizer.close()
            }

            // Keep the audio as a compact attachment
            if (m.audioFile.endsWith(".wav")) {
                EngineGuard.begin(id, EngineGuard.AUDIO)
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
            EngineGuard.clear(id)
            EngineGuard.log("done $id in ${secs}s")
        } catch (t: Throwable) {
            Log.e(TAG, "Processing failed", t)
            EngineGuard.log("failed $id: ${Log.getStackTraceString(t).take(3000)}")
            val msg = if (cancelled) "Processing was stopped. Tap Process again to restart." else (t.message ?: t.javaClass.simpleName)
            repo.update(id) { it.copy(status = MeetingStatus.FAILED, error = msg) }
        } finally {
            EngineGuard.end()
            transcriber?.close()
            transcriber = null
            clear(id)
        }
    }

    private fun transcribeMeeting(m0: Meeting, removeFillers: Boolean, threads: Int, beam: Int, diarThreads: Int): Meeting {
        val id = m0.id
        if (!models.coreReady) throw IllegalStateException("The speech models are not downloaded yet. Open Models to download them.")
        val wav = repo.audio(m0)
        if (!wav.exists() || !wav.name.endsWith(".wav")) throw IllegalStateException("The original recording is no longer available for re-processing.")

        report(id, "Loading audio", 0.5f, 0f, 0.02f)
        val totalSamples = AudioFiles.wavSampleCount(wav)
        val durationMs = totalSamples * 1000L / SAMPLE_RATE

        // 1. Who spoke when. Long meetings are handled in 40-minute blocks (to limit memory);
        //    voices are matched across blocks by comparing voiceprints.
        report(id, "Recognising voices", 0f, 0.02f, 0.25f)
        val block = BLOCK_MINUTES * 60 * SAMPLE_RATE
        val nBlocks = ((totalSamples + block - 1) / block).coerceAtLeast(1)
        EngineGuard.begin(id, EngineGuard.VOICES)
        val diarizer = Diarizer(models.file(ModelKind.SEGMENT), models.file(ModelKind.VOICEPRINT), diarThreads)
        val turns = ArrayList<SpeakerTurn>()
        val globalPrints = ArrayList<FloatArray>()
        try {
            for (b in 0 until nBlocks) {
                if (cancelled) throw InterruptedException("Stopped")
                val startSample = b * block
                val samples = AudioFiles.readWavFloats(wav, startSample, block)
                val local = diarizer.run(samples, m0.expectedSpeakers) { f ->
                    report(id, "Recognising voices", (b + f) / nBlocks, 0.02f, 0.25f)
                }
                val mapping = HashMap<Int, Int>()
                val prints = runCatching { diarizer.voiceprints(samples, local) }.getOrDefault(emptyMap())
                for (spk in local.map { it.speaker }.distinct()) {
                    val p = prints[spk]
                    var best = -1; var bestSim = 0.5f
                    if (p != null) globalPrints.forEachIndexed { gi, g ->
                        val sim = cosine(p, g)
                        if (sim > bestSim && gi !in mapping.values) { bestSim = sim; best = gi }
                    }
                    if (best < 0) {
                        globalPrints += (p ?: FloatArray(0))
                        best = globalPrints.size - 1
                    }
                    mapping[spk] = best
                }
                val offsetMs = startSample * 1000L / SAMPLE_RATE
                local.forEach { t -> turns += SpeakerTurn(t.startMs + offsetMs, t.endMs + offsetMs, mapping[t.speaker] ?: t.speaker) }
            }
        } finally {
            diarizer.release()
        }
        if (cancelled) throw InterruptedException("Stopped")

        // 2. Speech regions (silence removed), grouped into chunks of up to 5 minutes
        val autoLang = m0.language == "auto"
        // In Auto mode each part is short, so the language can switch between Afrikaans and English.
        val chunkSeconds = if (autoLang) 90 else CHUNK_SECONDS
        val regions = speechRegions(turns, totalSamples)
        val chunks = ArrayList<List<Region>>()
        var cur = ArrayList<Region>(); var curLen = 0
        for (r in regions) {
            val len = r.end - r.start
            if (curLen + len > chunkSeconds * SAMPLE_RATE && cur.isNotEmpty()) { chunks += cur; cur = ArrayList(); curLen = 0 }
            cur += r; curLen += len + GAP_SAMPLES
        }
        if (cur.isNotEmpty()) chunks += cur

        // 3. Speech to text
        val words = ArrayList<Word>()
        EngineGuard.begin(id, EngineGuard.TRANSCRIBE)
        EngineGuard.log("whisper: ${runCatching { WhisperNative.systemInfo() }.getOrDefault("?")}; chunks=${chunks.size} threads=$threads beam=$beam")
        val t = Transcriber(models.file(ModelKind.TRANSCRIBE))
        transcriber = t
        fun basePrompt(lang: String) = if (lang == "af")
            "Goeiemôre almal. Ons begin die vergadering. Die begroting is R15 000 en ons vergader weer op 15 Maart."
        else "Good morning everyone. Let's start the meeting. The budget is R15,000 and we meet again on 15 March."
        val vocab = prefs.settings.value.vocabulary
        val hints = (vocab + corrections.hints()).distinct()
        val vocabPrompt = if (hints.isEmpty()) "" else hints.take(40).joinToString(", ") + ". "
        var tail = ""
        var lastLang = ""
        chunks.forEachIndexed { ci, regs ->
            if (cancelled) throw InterruptedException("Stopped")
            val pieces = ArrayList<Piece>()
            val total = regs.sumOf { it.end - it.start } + GAP_SAMPLES * regs.size
            val buf = FloatArray(total)
            var pos = 0
            for (r in regs) {
                val part = AudioFiles.readWavFloats(wav, r.start, r.end - r.start)
                System.arraycopy(part, 0, buf, pos, part.size)
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
            val lang = if (autoLang) runCatching { t.detectLanguage(buf, threads) }.getOrDefault("af") else m0.language
            if (lang != lastLang) tail = ""
            lastLang = lang
            val punctuatedTail = tail.takeIf { tl -> tl.count { it in ".?!," } >= 2 && tl.any { it.isUpperCase() } } ?: ""
            val prompt = (vocabPrompt + basePrompt(lang) + " " + punctuatedTail).take(800)
            val got = try {
                t.transcribe(buf, lang, threads, beam, prompt)
            } finally {
                watcher.interrupt()
            }
            for (w in got) {
                val s = mapTime(w.startMs, pieces)
                val e = mapTime(w.endMs, pieces).coerceAtLeast(s)
                words += w.copy(startMs = s, endMs = e, lang = lang)
            }
            tail = got.takeLast(40).joinToString(" ") { it.text }
        }
        t.close()
        transcriber = null

        // 4. Attach each word to the voice speaking at that moment, then build readable paragraphs
        report(id, "Making it readable", 0.5f, 0.80f, 0.82f)
        val utterances = buildUtterances(words, turns, m0.language, removeFillers, vocab)
        // Meeting language = the language spoken most (for summaries, names and labels).
        val meetingLang = if (!autoLang) m0.language else
            utterances.groupBy { it.lang.ifEmpty { "af" } }.maxByOrNull { (_, us) -> us.sumOf { it.text.length } }?.key ?: "af"
        val ids = utterances.map { it.speaker }.distinct().sorted()
        val usedNames = HashSet<String>()
        val speakers = ids.map { sid ->
            val print = globalPrints.getOrNull(sid)?.toList() ?: emptyList()
            val existing = m0.speakers.firstOrNull { it.id == sid }
            if (existing != null && !com.bigoke.metings.data.isDefaultSpeakerName(existing.name)) return@map existing.copy(voiceprint = print)
            val known = voices.match(print)?.takeIf { it.name !in usedNames }
            if (known != null) {
                usedNames += known.name
                Speaker(sid, known.name, voiceprint = print, recognised = true)
            } else Speaker(sid, com.bigoke.metings.data.defaultSpeakerName(sid, meetingLang), voiceprint = print)
        }
        var m = m0.copy(
            language = meetingLang,
            durationMs = durationMs,
            utterances = utterances,
            speakers = speakers,
        )
        // Names said in the conversation ("my naam is Jan", "Sarah, will you…") label the voices.
        runCatching {
            val (named, r) = SpeakerNamer.apply(m, voices.voices.value.map { it.name })
            m = named
            // Someone who introduced themselves: remember the voice so they are recognised next time.
            r.introduced.forEach { sid -> m.speakers.firstOrNull { it.id == sid }?.let { sp -> voices.remember(sp.name, sp.voiceprint) } }
        }
        // Known voices heard again: strengthen what is remembered about them.
        m.speakers.filter { it.recognised }.forEach { sp -> runCatching { voices.remember(sp.name, sp.voiceprint) } }
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

    private fun buildUtterances(
        words: List<Word>, turns: List<SpeakerTurn>, lang: String, removeFillers: Boolean, vocab: List<String>,
    ): List<Utterance> {
        val out = ArrayList<Utterance>()
        var spk = -1
        var start = 0L; var end = 0L
        val sb = StringBuilder()
        val unsure = ArrayList<String>()
        val wordStarts = ArrayList<Int>()
        val fixes = corrections.asMap()
        val slang = prefs.settings.value.translateSlang
        val langCount = HashMap<String, Int>()
        fun flush() {
            if (sb.isEmpty()) return
            val uLang = if (lang == "auto") (langCount.maxByOrNull { it.value }?.key ?: "af") else lang
            val text = TextCleaner.clean(sb.toString(), uLang, removeFillers, vocab, fixes, slang)
            if (text.length > 1) out += Utterance(spk.coerceAtLeast(0), start, end, text, unsure = unsure.distinct(), lang = uLang, wordMs = wordStarts.toList())
            sb.clear()
            wordStarts.clear()
            unsure.clear()
            langCount.clear()
        }
        var sincePunct = 0
        for (w in words) {
            val s = speakerAt((w.startMs + w.endMs) / 2, turns, if (spk < 0) 0 else spk)
            // The model sometimes leaves out punctuation: use pauses in speech as sentence breaks.
            if (sb.isNotEmpty() && s == spk && w.startMs - end > 650 && sincePunct >= 6 && sb.last() !in ".?!,;:…") {
                sb.append('.')
                sincePunct = 0
            }
            val longPause = sb.isNotEmpty() && w.startMs - end > 3000
            val longPara = sb.length > 900 && sb.endsWith(".")
            if (s != spk || longPause || longPara) {
                flush()
                sincePunct = 0
                spk = s; start = w.startMs
            }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(w.text)
            wordStarts += (w.startMs - start).toInt().coerceAtLeast(0)
            val core = w.text.trim { !it.isLetterOrDigit() }
            if (w.prob < 0.45f && core.length >= 3) unsure += core.lowercase()
            if (w.lang.isNotEmpty()) langCount[w.lang] = (langCount[w.lang] ?: 0) + 1
            end = w.endMs
            if (w.text.last() in ".?!,;:…") sincePunct = 0 else sincePunct++
        }
        flush()
        // Merge tiny fragments (e.g. a single mis-assigned word) into the previous paragraph.
        val merged = ArrayList<Utterance>()
        for (u in out) {
            val prev = merged.lastOrNull()
            if (prev != null && u.text.split(" ").size <= 1 && u.startMs - prev.endMs < 1500) {
                merged[merged.size - 1] = prev.copy(
                    text = TextCleaner.clean(prev.text.trimEnd('.') + " " + u.text, prev.lang.ifEmpty { lang }, false, vocab),
                    endMs = u.endMs, unsure = (prev.unsure + u.unsure).distinct(),
                    wordMs = prev.wordMs + u.wordMs.map { it + (u.startMs - prev.startMs).toInt() },
                )
            } else merged += u
        }
        return merged
    }
}
