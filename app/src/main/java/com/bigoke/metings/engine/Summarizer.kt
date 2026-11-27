package com.bigoke.metings.engine

import android.content.Context
import android.util.Log
import com.bigoke.metings.data.ActionItem
import com.bigoke.metings.data.Entity
import com.bigoke.metings.data.EntityType
import com.bigoke.metings.data.Meeting
import com.bigoke.metings.data.Summary
import com.bigoke.metings.data.Templates
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File

/** Writes the meeting summary with Gemma on the phone; falls back to picking key sentences. */
class Summarizer(
    private val context: Context,
    private val modelFile: File?,
    private val useGpu: Boolean,
) : AutoCloseable {

    private var engine: Engine? = null

    private fun engine(): Engine = engine ?: openEngine(modelFile!!, useGpu).also { engine = it }

    override fun close() {
        runCatching { engine?.close() }
        engine = null
    }

    private fun needsPunctuation(t: String): Boolean {
        val words = t.split(' ').size
        if (words < 8) return false
        val marks = t.dropLast(1).count { it in ".?!,;:" }
        return marks * 18 < words || t.count { it.isUpperCase() } <= 1
    }

    /**
     * Adds punctuation and capital letters to transcript paragraphs that lack them, without changing words.
     * Returns the input unchanged for any paragraph where the AI changed the words.
     */
    fun punctuate(lines: List<String>, language: String, onProgress: (Float) -> Unit): List<String> {
        if (modelFile == null || !modelFile.exists()) return lines
        val todo = lines.indices.filter { needsPunctuation(lines[it]) }
        if (todo.isEmpty()) return lines
        val out = lines.toMutableList()
        val lang = langName(language)
        val system = "You restore punctuation and capitalisation in $lang (and English) speech transcripts. " +
            "You never change, add, remove, translate or reorder words."
        val batches = ArrayList<List<Int>>()
        var cur = ArrayList<Int>(); var len = 0
        for (i in todo) {
            if (len + lines[i].length > 2500 && cur.isNotEmpty()) { batches += cur; cur = ArrayList(); len = 0 }
            cur += i; len += lines[i].length
        }
        if (cur.isNotEmpty()) batches += cur
        try {
            val eng = engine()
            batches.forEachIndexed { bi, batch ->
                val prompt = "Add correct punctuation (full stops, commas, question marks) and capital letters " +
                    "(sentence starts, names of people, places, companies, months and days) to each numbered line. " +
                    "Keep every word exactly as written and in the same order. Answer with the same numbered lines only.\n\n" +
                    batch.mapIndexed { k, i -> "${k + 1}| ${lines[i]}" }.joinToString("\n")
                val answer = runCatching { ask(eng, system, prompt) }.getOrNull()
                if (answer != null) {
                    val parsed = HashMap<Int, String>()
                    Regex("""^\s*(\d+)\s*[|.):]\s*(.+)$""", RegexOption.MULTILINE).findAll(answer).forEach { m ->
                        parsed[m.groupValues[1].toInt()] = m.groupValues[2].trim()
                    }
                    batch.forEachIndexed { k, i ->
                        val fixed = parsed[k + 1]
                        if (fixed != null && sameWords(lines[i], fixed)) out[i] = fixed
                    }
                }
                onProgress((bi + 1f) / batches.size)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Punctuation pass failed", t)
        }
        return out
    }

    private fun normWords(t: String) = Fuzzy.fold(t).split(Regex("""[^\p{L}\d']+""")).filter { it.isNotEmpty() }

    private fun sameWords(a: String, b: String): Boolean {
        val x = normWords(a); val y = normWords(b)
        if (x == y) return true
        if (x.size != y.size || x.isEmpty()) return false
        val same = x.indices.count { x[it] == y[it] }
        return same * 100 >= x.size * 95
    }

    companion object {
        private const val TAG = "Summarizer"
        private const val CHUNK_CHARS = 6000
    }

    fun summarize(
        meeting: Meeting,
        language: String,
        onProgress: (Float) -> Unit,
    ): Summary {
        val transcript = meeting.utterances.joinToString("\n") { "${meeting.speakerName(it.speaker)}: ${it.text}" }
        if (transcript.isBlank()) return Summary(overview = emptyText(language), engine = "extractive", language = language)
        if (modelFile != null && modelFile.exists()) {
            try {
                return llmSummary(meeting, transcript, language, onProgress)
            } catch (t: Throwable) {
                Log.w(TAG, "AI summary failed, using key sentences", t)
            }
        }
        return extractive(meeting, language)
    }

    private fun emptyText(lang: String) =
        if (lang == "af") "Geen spraak is in hierdie opname gevind nie." else "No speech was found in this recording."

    // ------------------------------------------------------------------ on-device AI

    private fun openEngine(modelFile: File, useGpu: Boolean): Engine {
        val backends = if (useGpu) listOf(Backend.GPU(), Backend.CPU()) else listOf(Backend.CPU())
        var last: Throwable? = null
        for (b in backends) {
            try {
                val engine = Engine(
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = b,
                        maxNumTokens = 8192,
                        cacheDir = context.cacheDir.path,
                    )
                )
                engine.initialize()
                return engine
            } catch (t: Throwable) {
                last = t
                Log.w(TAG, "Backend ${b.name} failed", t)
            }
        }
        throw last ?: IllegalStateException("No backend")
    }

    private fun ask(engine: Engine, system: String, prompt: String): String {
        val cfg = ConversationConfig(
            systemInstruction = Contents.of(system),
            samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.3),
        )
        engine.createConversation(cfg).use { conv ->
            return conv.sendMessage(prompt).toString().trim()
        }
    }

    private fun langName(lang: String) = if (lang == "af") "Afrikaans" else "English"

    private fun llmSummary(
        meeting: Meeting, transcript: String, language: String,
        onProgress: (Float) -> Unit,
    ): Summary {
        val lang = langName(language)
        val system = "You are a professional meeting secretary in South Africa. You write clear, factual, " +
            "well-structured meeting minutes in $lang. Never invent facts that are not in the transcript. " +
            "Keep speaker names exactly as given."
        val engine = engine()
        run {
            onProgress(0.05f)
            val chunks = chunk(transcript)
            val notes = if (chunks.size == 1) transcript else {
                chunks.mapIndexed { i, c ->
                    val r = ask(
                        engine, system,
                        "This is part ${i + 1} of ${chunks.size} of a meeting transcript (Afrikaans and/or English).\n" +
                            "Write compact notes in $lang: key points, decisions, tasks with who must do them and by when, " +
                            "and every important number, amount, date, name and place mentioned.\n\nTRANSCRIPT PART:\n$c"
                    )
                    onProgress(0.1f + 0.6f * (i + 1) / chunks.size)
                    r
                }.joinToString("\n\n")
            }
            val known = keyFiguresFromEntities(meeting).take(12).joinToString("\n") { "- $it" }
            val tpl = Templates.get(meeting.template)
            val extraLayout = if (tpl.extraEn.isNotEmpty()) "EXTRA:\n- <${tpl.extraEn.lowercase()} item>\n" else ""
            val extraInstr = if (tpl.instruction.isNotEmpty()) tpl.instruction + "\n\n" else ""
            val final = ask(
                engine, system,
                extraInstr + "Write the minutes of this meeting in $lang using EXACTLY this layout with these English headings:\n\n" +
                    "OVERVIEW:\n<3 to 5 sentences describing the purpose and outcome of the meeting>\n" +
                    "DECISIONS:\n- <decision>\n" +
                    "ACTIONS:\n- <task> | <person responsible or blank> | <due date or blank>\n" +
                    "KEY FIGURES:\n- <important amount, number or date and what it is for>\n" +
                    "FOLLOW-UPS:\n- <open question or item for the next meeting>\n" + extraLayout + "\n" +
                    "Write 'None' under a heading if there is nothing for it. No other text.\n\n" +
                    (if (known.isNotEmpty()) "Details found in the transcript:\n$known\n\n" else "") +
                    (if (chunks.size == 1) "TRANSCRIPT:\n$notes" else "NOTES FROM THE MEETING:\n$notes")
            )
            onProgress(0.95f)
            val parsed = parse(final, language).let { p ->
                if (tpl.extraEn.isEmpty()) p else p.copy(extraTitle = if (language == "af") tpl.extraAf else tpl.extraEn)
            }
            if (parsed.overview.isBlank() && parsed.decisions.isEmpty() && parsed.actions.isEmpty()) {
                return extractive(meeting, language).copy(overview = final.take(1500), engine = "gemma")
            }
            return parsed
        }
    }

    private fun chunk(t: String): List<String> {
        if (t.length <= CHUNK_CHARS) return listOf(t)
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (line in t.lines()) {
            if (sb.length + line.length > CHUNK_CHARS && sb.isNotEmpty()) { out += sb.toString(); sb.clear() }
            if (line.length > CHUNK_CHARS) line.chunked(CHUNK_CHARS).forEach { out += it } else sb.appendLine(line)
        }
        if (sb.isNotBlank()) out += sb.toString()
        return out
    }

    private fun parse(text: String, language: String): Summary {
        val sections = HashMap<String, MutableList<String>>()
        var current = ""
        val headings = mapOf(
            "OVERVIEW" to "OVERVIEW", "DECISIONS" to "DECISIONS", "ACTIONS" to "ACTIONS",
            "KEY FIGURES" to "KEY FIGURES", "FOLLOW-UPS" to "FOLLOW-UPS", "FOLLOW UPS" to "FOLLOW-UPS",
            "EXTRA" to "EXTRA",
        )
        for (raw in text.lines()) {
            val line = raw.trim().trim('*', '#').trim()
            if (line.isEmpty()) continue
            val h = headings.entries.firstOrNull { line.uppercase().startsWith(it.key) }
            if (h != null) {
                current = h.value
                val rest = line.substring(h.key.length).trimStart(':', ' ').trim()
                if (rest.isNotEmpty()) sections.getOrPut(current) { mutableListOf() }.add(rest)
                continue
            }
            if (current.isNotEmpty()) sections.getOrPut(current) { mutableListOf() }.add(line)
        }
        fun items(key: String) = sections[key].orEmpty()
            .map { it.removePrefix("-").removePrefix("•").removePrefix("*").trim() }
            .filter { it.isNotBlank() && it.lowercase() !in setOf("none", "geen", "n/a", "nvt") }
        val actions = items("ACTIONS").map { a ->
            val parts = a.split('|').map { it.trim() }
            ActionItem(
                text = parts.getOrElse(0) { a },
                owner = parts.getOrElse(1) { "" }.takeUnless { it.lowercase() in setOf("blank", "none", "-", "geen", "n/a", "nvt", "n.v.t.", "onbekend", "unknown") } ?: "",
                due = parts.getOrElse(2) { "" }.takeUnless { it.lowercase() in setOf("blank", "none", "-", "geen", "n/a", "nvt", "n.v.t.", "onbekend", "unknown") } ?: "",
            )
        }
        return Summary(
            overview = items("OVERVIEW").joinToString(" "),
            decisions = items("DECISIONS"),
            actions = actions,
            keyFigures = items("KEY FIGURES"),
            followUps = items("FOLLOW-UPS"),
            extra = items("EXTRA"),
            engine = "gemma",
            language = language,
        )
    }

    // ------------------------------------------------------------------ Ask Notule

    private fun ts(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

    /** Picks the parts of the transcript most related to the question (keeps the prompt small). */
    private fun relevant(meeting: Meeting, question: String, maxChars: Int): List<Int> {
        val q = words(question).toSet()
        val all = meeting.utterances.indices.toList()
        if (meeting.utterances.sumOf { it.text.length } <= maxChars) return all
        val scored = all.map { i ->
            val w = words(meeting.utterances[i].text)
            i to w.count { it in q }.toDouble() / Math.sqrt(w.size.coerceAtLeast(1).toDouble())
        }.sortedByDescending { it.second }
        val picked = sortedSetOf<Int>()
        var size = 0
        for ((i, _) in scored) {
            for (k in listOf(i - 1, i, i + 1)) {
                if (k !in all || k in picked) continue
                val len = meeting.utterances[k].text.length
                if (size + len > maxChars) continue
                picked += k; size += len
            }
            if (size > maxChars * 0.9) break
        }
        return picked.toList()
    }

    /** Answers a question about the meeting, citing moments as [m:ss]. */
    fun ask(meeting: Meeting, question: String): Pair<String, String> {
        val afWords = setOf("wat", "wie", "hoeveel", "wanneer", "waar", "hoekom", "watter", "het", "die", "ons", "sal", "gesê", "besluit")
        val qWords = question.lowercase().split(Regex("""[^\p{L}']+"""))
        val af = qWords.any { it in afWords } || (!Language.mostlyEnglish(question) && meeting.language == "af")
        val idx = relevant(meeting, question, 9000)
        if (modelFile != null && modelFile.exists()) {
            runCatching {
                val transcript = idx.joinToString("\n") { i ->
                    val u = meeting.utterances[i]
                    "[${ts(u.startMs)}] ${meeting.speakerName(u.speaker)}: ${u.text}"
                }
                val lang = if (af) "Afrikaans" else "English"
                val answer = ask(
                    engine(),
                    "You answer questions about one meeting, using only its transcript. You are precise and brief.",
                    "Answer in $lang. Use only the transcript below. After each fact, give the time it was said as [m:ss] " +
                        "exactly as written in the transcript. If the transcript does not contain the answer, say so plainly.\n\n" +
                        "QUESTION: $question\n\nTRANSCRIPT:\n$transcript",
                )
                if (answer.isNotBlank()) return answer to "gemma"
            }.onFailure { Log.w(TAG, "Ask failed", it) }
        }
        // Without the AI: the best matching moments.
        val q = words(question).toSet()
        val best = meeting.utterances.withIndex()
            .map { (i, u) -> i to words(u.text).count { it in q } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(3).sortedBy { it.first }
        val text = if (best.isEmpty()) (if (af) "Ek kon niks daaroor in hierdie vergadering vind nie." else "I couldn't find anything about that in this meeting.")
        else (if (af) "Hier is waar dit bespreek is:\n" else "Here is where it was discussed:\n") + best.joinToString("\n") { (i, _) ->
            val u = meeting.utterances[i]; "• [${ts(u.startMs)}] ${meeting.speakerName(u.speaker)}: ${u.text.take(220)}"
        }
        return text to "search"
    }

    // ------------------------------------------------------------------ key sentences fallback

    private val stop = setOf(
        "die", "en", "van", "is", "in", "dit", "het", "wat", "ek", "jy", "ons", "hulle", "nie", "op", "vir", "met",
        "te", "om", "sal", "kan", "moet", "was", "dat", "sy", "hy", "maar", "ook", "so", "as", "na", "by", "aan",
        "the", "and", "of", "to", "a", "an", "in", "is", "it", "that", "for", "on", "with", "we", "you", "i", "be",
        "this", "are", "was", "have", "will", "so", "but", "they", "at", "not", "just", "like", "yes", "ja", "nee",
        "okay", "ok", "net", "nou", "baie", "goed", "dan", "daar", "hier", "'n", "n", "my", "jou", "our", "your",
    )
    private val decisionWords = listOf(
        "besluit", "ooreengekom", "goedgekeur", "aanvaar", "finaal", "afgehandel", "decided", "agreed", "approved",
        "resolved", "confirmed", "bevestig", "final",
    )

    fun extractive(meeting: Meeting, language: String): Summary {
        data class Sent(val text: String, val speaker: Int, val order: Int)
        val sents = ArrayList<Sent>()
        meeting.utterances.forEach { u ->
            Regex("""[^.!?…]+[.!?…]?""").findAll(u.text).map { it.value.trim() }.filter { it.split(" ").size >= 5 }
                .forEach { sents += Sent(it, u.speaker, sents.size) }
        }
        if (sents.isEmpty()) return Summary(overview = emptyText(language), engine = "extractive", language = language)
        val freq = HashMap<String, Int>()
        sents.forEach { s -> words(s.text).forEach { freq[it] = (freq[it] ?: 0) + 1 } }
        val maxF = (freq.values.maxOrNull() ?: 1).toDouble()
        fun score(s: Sent): Double {
            val ws = words(s.text)
            if (ws.isEmpty()) return 0.0
            var sc = ws.sumOf { (freq[it] ?: 0) / maxF } / Math.sqrt(ws.size.toDouble())
            if (Regex("""\d""").containsMatchIn(s.text)) sc *= 1.2
            if (decisionWords.any { s.text.lowercase().contains(it) }) sc *= 1.5
            return sc
        }
        val n = (sents.size / 12).coerceIn(3, 7)
        val top = sents.sortedByDescending { score(it) }.take(n).sortedBy { it.order }
        val overview = top.joinToString(" ") { "${meeting.speakerName(it.speaker)}: ${it.text}" }
        val decisions = sents.filter { s -> decisionWords.any { s.text.lowercase().contains(it) } }.take(8).map { it.text }
        val actions = meeting.entities.filter { it.type == EntityType.TASK }.take(15).map { e ->
            val (owner, due) = e.value.split('|').let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
            ActionItem(e.text, owner, due)
        }
        val follow = sents.filter { it.text.endsWith("?") }.takeLast(5).map { "${meeting.speakerName(it.speaker)}: ${it.text}" }
        val tpl = Templates.get(meeting.template)
        val extra = if (tpl.keywords.isEmpty()) emptyList() else
            sents.filter { s -> tpl.keywords.any { s.text.lowercase().contains(it) } }.take(10).map { it.text }
        return Summary(
            overview = overview,
            decisions = decisions,
            actions = actions,
            keyFigures = keyFiguresFromEntities(meeting),
            followUps = follow,
            extraTitle = if (tpl.extraEn.isEmpty()) "" else if (language == "af") tpl.extraAf else tpl.extraEn,
            extra = extra,
            engine = "extractive",
            language = language,
        )
    }

    private fun words(t: String) = Regex("""[\p{L}\d']+""").findAll(t.lowercase()).map { it.value }
        .filter { it.length > 2 && it !in stop }.toList()

    private fun keyFiguresFromEntities(meeting: Meeting): List<String> {
        val wanted = setOf(EntityType.AMOUNT, EntityType.PERCENT, EntityType.DATE, EntityType.MEASUREMENT)
        return meeting.entities.filter { it.type in wanted }
            .distinctBy { it.type to it.value }
            .take(20)
            .map { e -> "${e.text} — ${contextOf(meeting, e)}" }
    }

    private fun contextOf(meeting: Meeting, e: Entity): String {
        val t = meeting.utterances.getOrNull(e.utterance)?.text ?: return ""
        val s = t.lastIndexOfAny(charArrayOf('.', '!', '?'), (e.start - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val end = t.indexOfAny(charArrayOf('.', '!', '?'), e.end).let { if (it < 0) t.length else it + 1 }
        return t.substring(s, end).trim().take(160)
    }
}
