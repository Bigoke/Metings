package com.bigoke.metings.engine

import com.bigoke.metings.data.Meeting
import com.bigoke.metings.data.isDefaultSpeakerName

/**
 * Picks up people's names from what is said:
 *  - a speaker introducing themselves ("my naam is Jan", "this is Sarah speaking", "Jan hier") names that voice;
 *  - a speaker talking to someone ("Jan, sal jy…?", "…, Sarah?") points at whoever answers next;
 *  - "dankie Jan" / "thanks Sarah" points at whoever spoke just before.
 * Names already given by the user or recognised from a remembered voice are never changed.
 */
object SpeakerNamer {

    data class Result(
        /** Speaker id → name found in the conversation. */
        val named: Map<Int, String>,
        /** Ids named because they introduced themselves (safe to remember their voice). */
        val introduced: Set<Int>,
        /** People spoken to by name who were not matched to a voice (they may not have spoken). */
        val addressed: List<String>,
    )

    private const val NAME = """([A-Za-zÀ-ÿ'][a-zà-ÿ'-]{1,20})(?:\s+(?:van\s+der\s+|van\s+|du\s+|de\s+|le\s+)?([A-Z][a-zà-ÿ'-]{1,20}))?"""
    private val strongIntro = Regex(
        """(?i:\b(?:my\s+naam\s+is|my\s+name\s+is|my\s+name's|ek\s+heet|i'm\s+called|you\s+can\s+call\s+me|hier\s+praat|this\s+is|dit\s+is|dis|it's|here\s+is|hier\s+is|ek\s+is|i\s+am|i'm))\s+$NAME""",
    )
    private val strongTriggers = Regex("""(?i)^(my\s+naam|my\s+name|ek\s+heet|i'm\s+called|you\s+can\s+call|hier\s+praat)""")
    private val selfSuffix = Regex("""^\s*(?:,\s*)?(hier|here|speaking|wat\s+praat|van\b|from\b|en\s+ek|and\s+i|[.,!]|$)""", RegexOption.IGNORE_CASE)
    private val nameHere = Regex("""^\s*$NAME\s+(?:hier|here|speaking)\b""")
    private val vocativeStart = Regex("""(?:^|[.!?]\s+)(?:(?i:ja|nee|ok|okay|oukei|so|en|and|well|goed|right)\s*,?\s+)?$NAME\s*[,?]""")
    private val vocativeEnd = Regex(""",\s*$NAME\s*[?.!]?\s*$""")
    private val thanks = Regex("""(?i:\b(?:dankie|baie\s+dankie|thanks|thank\s+you|thank\s+you\s+very\s+much)),?\s+$NAME""")

    private fun cap(w: String) = w.replaceFirstChar { it.uppercase() }

    private fun fullName(m: MatchResult): String =
        listOfNotNull(m.groupValues[1].takeIf { it.isNotEmpty() }, m.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }).joinToString(" ") { cap(it) }

    fun find(m: Meeting, knownNames: Collection<String> = emptyList()): Result {
        val known = HashSet<String>()
        known += Gazetteer.firstNames
        knownNames.forEach { n -> n.split(' ').firstOrNull()?.let { known += Fuzzy.fold(it) } }
        m.speakers.filterNot { isDefaultSpeakerName(it.name) }.forEach { s -> s.name.split(' ').firstOrNull()?.let { known += Fuzzy.fold(it) } }
        fun isName(first: String, needKnown: Boolean): Boolean {
            val f = Fuzzy.fold(first)
            if (f.length < 2 || f in Gazetteer.stopCapitalised) return false
            if (Gazetteer.months[f] != null || Gazetteer.weekdays[f] != null || Gazetteer.places[f] != null) return false
            return f in known || (!needKnown && first.first().isUpperCase())
        }

        val us = m.utterances
        val score = HashMap<Int, HashMap<String, Int>>()
        val introduced = HashMap<Int, String>()
        val addressed = LinkedHashSet<String>()
        fun vote(spk: Int, name: String, w: Int) {
            val byName = score.getOrPut(spk) { HashMap() }
            // Treat "Jan" and "Jan Botha" as the same person; keep the longer form.
            val key = byName.keys.firstOrNull { it.startsWith(name.split(' ')[0] + " ") || it == name.split(' ')[0] || name.startsWith("$it ") }
            if (key != null && key != name && name.length > key.length) { byName[name] = (byName.remove(key) ?: 0) + w }
            else byName[key ?: name] = (byName[key ?: name] ?: 0) + w
        }

        us.forEachIndexed { i, u ->
            val text = u.text
            val head = text.split(' ').take(14).joinToString(" ")
            // Introductions
            strongIntro.findAll(text).forEach { mr ->
                val trigger = mr.value.substring(0, (mr.groups[1]?.range?.first ?: mr.range.first) - mr.range.first)
                val strong = strongTriggers.containsMatchIn(trigger.trimStart())
                val first = mr.groupValues[1]
                val after = text.substring(mr.range.last + 1)
                val early = mr.range.first < head.length
                val ok = if (strong) isName(first, needKnown = false)
                    else isName(first, needKnown = true) && early && selfSuffix.containsMatchIn(after)
                if (ok) {
                    val n = fullName(mr)
                    vote(u.speaker, n, if (strong) 4 else 3)
                    introduced[u.speaker] = n
                }
            }
            nameHere.find(text)?.let { mr ->
                if (isName(mr.groupValues[1], needKnown = true)) { val n = fullName(mr); vote(u.speaker, n, 3); introduced[u.speaker] = n }
            }
            // Talking to someone: the next different voice is probably that person.
            val vocs = (vocativeStart.findAll(text) + listOfNotNull(vocativeEnd.find(text))).toList()
            for (voc in vocs) {
                if (!isName(voc.groupValues[1], needKnown = true)) continue
                val n = fullName(voc)
                addressed += n
                val next = us.drop(i + 1).take(3).firstOrNull { it.speaker != u.speaker }
                if (next != null && next.startMs - u.endMs < 20_000) vote(next.speaker, n, 1)
                // The person talking is not the one being spoken to.
                vote(u.speaker, n, -2)
            }
            thanks.find(text)?.let { mr ->
                if (isName(mr.groupValues[1], needKnown = true)) {
                    val n = fullName(mr)
                    addressed += n
                    val prev = us.take(i).lastOrNull { it.speaker != u.speaker }
                    if (prev != null) vote(prev.speaker, n, 1)
                    vote(u.speaker, n, -2)
                }
            }
        }

        // Assign best names greedily, highest confidence first; each name to one voice.
        val candidates = score.flatMap { (spk, names) -> names.map { (n, s) -> Triple(spk, n, s) } }
            .filter { it.third >= 2 }
            .sortedByDescending { it.third }
        val named = HashMap<Int, String>()
        val usedFirst = HashSet<String>()
        m.speakers.filterNot { isDefaultSpeakerName(it.name) }.forEach { usedFirst += Fuzzy.fold(it.name.split(' ')[0]) }
        for ((spk, n, _) in candidates) {
            val sp = m.speakers.firstOrNull { it.id == spk } ?: continue
            if (!isDefaultSpeakerName(sp.name) || spk in named) continue
            val f = Fuzzy.fold(n.split(' ')[0])
            if (f in usedFirst) continue
            named[spk] = n; usedFirst += f
        }
        val allNames = (m.speakers.map { it.name } + named.values).map { Fuzzy.fold(it.split(' ')[0]) }.toSet()
        return Result(
            named,
            named.filter { (spk, n) -> introduced[spk] == n }.keys,
            addressed.filter { Fuzzy.fold(it.split(' ')[0]) !in allNames }.distinctBy { Fuzzy.fold(it.split(' ')[0]) },
        )
    }

    /** Renames a speaker everywhere it appears (speaker list and summary text). */
    fun rename(m: Meeting, sid: Int, name: String, source: String = ""): Meeting {
        val old = m.speakerName(sid)
        val renamed = m.copy(speakers = m.speakers.map { if (it.id == sid) it.copy(name = name, recognised = false, nameSource = source) else it })
        val sum = renamed.summary?.let { s ->
            fun r(x: String) = x.replace(old, name)
            s.copy(
                overview = r(s.overview), decisions = s.decisions.map(::r), keyFigures = s.keyFigures.map(::r),
                followUps = s.followUps.map(::r), extra = s.extra.map(::r),
                actions = s.actions.map { a -> a.copy(text = r(a.text), owner = r(a.owner)) },
            )
        }
        return renamed.copy(summary = sum)
    }

    /** Applies [find] to the meeting. */
    fun apply(m: Meeting, knownNames: Collection<String> = emptyList()): Pair<Meeting, Result> {
        val r = find(m, knownNames)
        var out = m
        r.named.forEach { (sid, n) -> out = rename(out, sid, n, "said") }
        return out.copy(namesChecked = true, addressed = r.addressed) to r
    }
}
