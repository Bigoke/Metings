package com.bigoke.metings.engine

import com.bigoke.metings.data.Entity
import com.bigoke.metings.data.EntityType
import com.bigoke.metings.data.Utterance
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Finds the useful details in a transcript: amounts, numbers, dates, times, names, places, addresses,
 * phone numbers, e-mail addresses, colours, shapes, measurements and tasks. Rule based with fuzzy
 * matching so that slightly misspelt words from speech recognition still match.
 */
class EntityExtractor(meetingTime: Long, vocabulary: List<String> = emptyList()) {

    /** The user's own names and terms (folded) -> spelling, recognised as names. */
    private val vocab: Map<String, String> = vocabulary.filter { it.isNotBlank() }.associateBy { Fuzzy.fold(it.trim()) }

    private val today: LocalDate = Instant.ofEpochMilli(meetingTime).atZone(ZoneId.systemDefault()).toLocalDate()

    private class Ctx(val text: String, val ui: Int) {
        val taken = BooleanArray(text.length)
        val list = ArrayList<Entity>()
        fun free(s: Int, e: Int) = s >= 0 && e <= text.length && s < e && (s until e).none { taken[it] }
        fun add(type: EntityType, s: Int, e: Int, value: String = ""): Boolean {
            var st = s; var en = e
            while (st < en && text[st].isWhitespace()) st++
            while (en > st && (text[en - 1].isWhitespace() || text[en - 1] in ",;:")) en--
            if (!free(st, en)) return false
            for (i in st until en) taken[i] = true
            list.add(Entity(type, text.substring(st, en), value, ui, st, en))
            return true
        }
    }

    private data class Tok(val s: Int, val e: Int, val w: String) {
        val f: String = Fuzzy.fold(w)
        val cap: Boolean = w.firstOrNull()?.isUpperCase() == true
    }

    companion object {
        private const val NUM = """\d{1,3}(?:[  ,.]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d+)?"""
        private const val MULT = """miljoen|million|mil|miljard|billion|bn|duisend|thousand|k|m"""
        private val monthAlt = Gazetteer.months.keys.sortedByDescending { it.length }.joinToString("|")
        private val units = listOf(
            "mm", "cm", "km", "m", "meter", "meters", "metre", "metres", "millimeter", "sentimeter", "kilometer",
            "kilometers", "kilometres", "kg", "g", "gram", "grams", "kilogram", "kilograms", "kilo", "ton", "tonne",
            "tons", "l", "liter", "liters", "litre", "litres", "ml", "milliliter", "m²", "m2", "m³", "m3", "ha",
            "hektaar", "hectare", "hectares", "grade", "degrees", "°c", "°", "duim", "inch", "inches", "voet", "ft",
            "feet", "kw", "kwh", "watt", "volt", "mb", "gb", "tb", "ure", "uur", "hours", "hour", "minute",
            "minutes", "minuut", "dae", "days", "dag", "weke", "weeks", "maande", "months", "jaar", "jare", "years",
            "km/h", "vierkante meter", "square meters", "square metres", "kubieke meter", "cubic meters",
        ).sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        private val currencyWords = setOf("rand", "rande", "zar", "dollar", "dollars", "euro", "euros", "pond", "pounds", "sent", "cents")
        private val percentWords = setOf("persent", "percent", "persentasie")
        private val unitWords = setOf(
            "meter", "meters", "metre", "metres", "kilometer", "kilometers", "kilogram", "kilo", "kilos", "ton",
            "liter", "liters", "litre", "litres", "grade", "degrees", "ure", "uur", "hours", "minute", "minutes",
            "dae", "days", "weke", "weeks", "maande", "months", "jaar", "jare", "years", "hektaar", "hectares",
        )
        private val nameParticles = setOf("van", "der", "den", "du", "de", "le", "la", "von")
        // Names that are also ordinary words: only treated as names when written with a capital.
        private val ambiguousLower = setOf(
            "strand", "springs", "parys", "ceres", "wellington", "robertson", "montagu", "perth", "holland", "brits",
            "mark", "will", "bill", "grant", "dean", "lee", "sam", "kim", "neo", "mona", "ina", "frans", "rina",
            "louw", "nel", "smit", "roos", "musa", "zama", "precious",
        )

        private val taskAf = listOf(
            "ek sal", "ons sal", "ek gaan", "ons gaan", "jy moet", "julle moet", "ons moet", "hy moet", "sy moet",
            "kan jy", "kan julle", "sal jy", "sal julle", "sorg dat", "maak seker", "moet asseblief", "opvolg",
            "aksie", "stuur asseblief", "bel asseblief", "teen môre", "teen die einde", "voor vrydag",
        )
        private val taskEn = listOf(
            "i will", "i'll", "we will", "we'll", "you need to", "we need to", "need to", "can you", "could you",
            "would you", "please send", "please call", "make sure", "follow up", "action item", "take care of",
            "by tomorrow", "by friday", "by monday", "by the end", "before friday", "responsible for",
        )
        private val selfTask = setOf("ek sal", "ek gaan", "i will", "i'll")
    }

    fun extract(utterances: List<Utterance>, speakerNames: Map<Int, String>): List<Entity> {
        val out = ArrayList<Entity>()
        val knownNames = speakerNames.values.filterNot { com.bigoke.metings.data.isDefaultSpeakerName(it) }.map { Fuzzy.fold(it) }.toSet()
        utterances.forEachIndexed { ui, u ->
            out += extractOne(ui, u, speakerNames, knownNames)
        }
        return unifyNames(out)
    }

    private fun extractOne(ui: Int, u: Utterance, speakerNames: Map<Int, String>, knownNames: Set<String>): List<Entity> {
        val c = Ctx(u.text, ui)
        val text = u.text
        emails(c)
        phones(c)
        dates(c)
        times(c)
        amounts(c)
        percentsAndMeasures(c)
        addresses(c)
        val toks = Regex("""[\p{L}\d][\p{L}\d'’-]*""").findAll(text).map { Tok(it.range.first, it.range.last + 1, it.value) }.toList()
        vocabulary(c)
        numberWords(c, toks)
        digits(c)
        colours(c, toks)
        shapes(c, toks)
        places(c, toks)
        people(c, toks, knownNames)
        tasks(c, u, speakerNames)
        return c.list
    }

    // ---------------------------------------------------------------- contact details

    private fun emails(c: Ctx) {
        Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""").findAll(c.text).forEach {
            c.add(EntityType.EMAIL, it.range.first, it.range.last + 1, it.value.lowercase())
        }
        // Spoken: "jan at gmail dot com", "piet by webmail punt co punt za"
        Regex(
            """(?i)\b([a-z0-9._-]+)\s+(?:at|by|ät)\s+([a-z0-9-]+)((?:\s+(?:dot|punt)\s+[a-z]{2,6}){1,3})\b"""
        ).findAll(c.text).forEach { m ->
            val tail = m.groupValues[3].trim().split(Regex("""\s+""")).filter { it.lowercase() !in setOf("dot", "punt") }
            if (tail.isEmpty()) return@forEach
            val addr = "${m.groupValues[1]}@${m.groupValues[2]}.${tail.joinToString(".")}".lowercase()
            c.add(EntityType.EMAIL, m.range.first, m.range.last + 1, addr)
        }
    }

    private fun phones(c: Ctx) {
        Regex("""(?<![\d+])(?:\+27|0027|0)[\s-]?\(?\d{2}\)?[\s-]?\d{3}[\s-]?\d{4}(?!\d)""").findAll(c.text).forEach { m ->
            var digits = m.value.filter { it.isDigit() || it == '+' }
            if (digits.startsWith("0027")) digits = "+27" + digits.drop(4)
            else if (digits.startsWith("0")) digits = "+27" + digits.drop(1)
            c.add(EntityType.PHONE, m.range.first, m.range.last + 1, digits)
        }
        // Any grouping of digits that adds up to a South African number: "082 55 51 234", "0 8 2 5 5 5 1 2 3 4".
        Regex("""(?<![\d+])\+?\d[\d \-]{8,18}\d(?!\d)""").findAll(c.text).forEach { m ->
            val digits = m.value.filter { it.isDigit() }
            val plus = m.value.startsWith("+")
            val value = when {
                digits.length == 10 && digits.startsWith("0") -> "+27" + digits.drop(1)
                digits.length == 11 && digits.startsWith("27") -> "+$digits"
                plus && digits.length in 10..13 -> "+$digits"
                else -> null
            }
            if (value != null) c.add(EntityType.PHONE, m.range.first, m.range.last + 1, value)
        }
        Regex("""(?<![\d+])\+\d{1,3}[\s-]?\d[\d\s-]{7,13}\d(?!\d)""").findAll(c.text).forEach { m ->
            c.add(EntityType.PHONE, m.range.first, m.range.last + 1, m.value.filter { it.isDigit() || it == '+' })
        }
    }

    // ---------------------------------------------------------------- dates and times

    private fun iso(d: LocalDate) = d.toString()

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate.of(y, m, d) }.getOrNull()

    private fun dates(c: Ctx) {
        val t = c.text
        Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""").findAll(t).forEach { m ->
            safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                ?.let { c.add(EntityType.DATE, m.range.first, m.range.last + 1, iso(it)) }
        }
        Regex("""\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4})\b""").findAll(t).forEach { m ->
            var y = m.groupValues[3].toInt(); if (y < 100) y += 2000
            safeDate(y, m.groupValues[2].toInt(), m.groupValues[1].toInt())
                ?.let { c.add(EntityType.DATE, m.range.first, m.range.last + 1, iso(it)) }
        }
        val ord = """(?:st|nd|rd|th|ste|de|e)?"""
        Regex("""(?i)\b(\d{1,2})$ord\s+(?:of\s+|van\s+)?($monthAlt)\b\.?(?:,?\s+(\d{4}))?""").findAll(t).forEach { m ->
            val month = Gazetteer.months[Fuzzy.fold(m.groupValues[2])] ?: return@forEach
            val y = m.groupValues[3].toIntOrNull() ?: today.year
            safeDate(y, month, m.groupValues[1].toInt())?.let { c.add(EntityType.DATE, m.range.first, m.range.last + 1, iso(it)) }
        }
        Regex("""(?i)\b($monthAlt)\s+(?:die\s+|the\s+)?(\d{1,2})$ord\b(?:,?\s+(\d{4}))?""").findAll(t).forEach { m ->
            val month = Gazetteer.months[Fuzzy.fold(m.groupValues[1])] ?: return@forEach
            if (m.groupValues[1].length < 5 && !m.groupValues[1][0].isUpperCase()) return@forEach
            val y = m.groupValues[3].toIntOrNull() ?: today.year
            safeDate(y, month, m.groupValues[2].toInt())?.let { c.add(EntityType.DATE, m.range.first, m.range.last + 1, iso(it)) }
        }
        Regex("""\b(\p{Lu}\p{Ll}{4,})\s+(\d{4})\b""").findAll(t).forEach { m ->
            val month = Gazetteer.months[Fuzzy.fold(m.groupValues[1])] ?: return@forEach
            safeDate(m.groupValues[2].toInt(), month, 1)?.let {
                c.add(EntityType.DATE, m.range.first, m.range.last + 1, "%04d-%02d".format(Locale.ROOT, it.year, it.monthValue))
            }
        }
        // Relative expressions
        val rel = linkedMapOf(
            """einde van die maand|end of the month|month[- ]end|maandeinde""" to today.with(TemporalAdjusters.lastDayOfMonth()),
            """einde van die week|end of the week""" to today.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.FRIDAY)),
            """volgende maand|next month""" to today.plusMonths(1).withDayOfMonth(1),
            """volgende week|next week""" to today.with(TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)),
            """oormôre|oormore|day after tomorrow""" to today.plusDays(2),
            """môre|tomorrow""" to today.plusDays(1),
            """vandag|today""" to today,
            """gister|yesterday""" to today.minusDays(1),
        )
        rel.forEach { (pattern, date) ->
            Regex("""(?i)(?<![\p{L}])(?:$pattern)(?![\p{L}])""").findAll(t).forEach { m ->
                c.add(EntityType.DATE, m.range.first, m.range.last + 1, iso(date))
            }
        }
        val wd = Gazetteer.weekdays.keys.joinToString("|")
        Regex("""(?i)\b(?:(volgende|aanstaande|next|this|hierdie|coming|komende)\s+)?($wd)\b""").findAll(t).forEach { m ->
            val dow = Gazetteer.weekdays[m.groupValues[2].lowercase()] ?: return@forEach
            var d = today.with(TemporalAdjusters.next(java.time.DayOfWeek.of(dow)))
            val q = m.groupValues[1].lowercase()
            if ((q == "volgende" || q == "next") && d.toEpochDay() - today.toEpochDay() < 4) d = d.plusWeeks(1)
            c.add(EntityType.DATE, m.range.first, m.range.last + 1, iso(d))
        }
    }

    private fun times(c: Ctx) {
        val t = c.text
        Regex("""\b([01]?\d|2[0-3])[:hH]([0-5]\d)\b""").findAll(t).forEach { m ->
            c.add(EntityType.TIME, m.range.first, m.range.last + 1, "%02d:%s".format(Locale.ROOT, m.groupValues[1].toInt(), m.groupValues[2]))
        }
        Regex("""(?i)\b(1[0-2]|0?[1-9])(?::([0-5]\d))?\s?(am|pm|a\.m\.|p\.m\.|vm|nm)""").findAll(t).forEach { m ->
            var h = m.groupValues[1].toInt()
            val pm = m.groupValues[3].lowercase().let { it.startsWith("p") || it == "nm" }
            if (pm && h < 12) h += 12
            if (!pm && h == 12) h = 0
            val min = m.groupValues[2].ifEmpty { "00" }
            c.add(EntityType.TIME, m.range.first, m.range.last + 1, "%02d:%s".format(Locale.ROOT, h, min))
        }
        Regex("""(?i)\b(\d{1,2})\s?(uur|o'clock|o’clock)\b""").findAll(t).forEach { m ->
            val h = m.groupValues[1].toInt()
            if (h in 0..23) c.add(EntityType.TIME, m.range.first, m.range.last + 1, "%02d:00".format(Locale.ROOT, h))
        }
        Regex("""(?i)\bhalf\s+([\p{L}\d]+)\b""").findAll(t).forEach { m ->
            val v = m.groupValues[1].toIntOrNull()?.toLong() ?: NumberWords.wordValue(m.groupValues[1]) ?: return@forEach
            if (v in 1..12) c.add(EntityType.TIME, m.range.first, m.range.last + 1, "%02d:30".format(Locale.ROOT, (v - 1).toInt()))
        }
    }

    private fun vocabulary(c: Ctx) {
        if (vocab.isEmpty()) return
        val folded = Fuzzy.fold(c.text)
        // Fold keeps string length for Latin text; fall back to case-insensitive search otherwise.
        val sameLength = folded.length == c.text.length
        for ((key, term) in vocab.entries.sortedByDescending { it.key.length }) {
            var from = 0
            while (true) {
                val idx = if (sameLength) folded.indexOf(key, from) else c.text.indexOf(term, from, ignoreCase = true)
                if (idx < 0) break
                val end = idx + key.length
                val before = c.text.getOrNull(idx - 1)
                val after = c.text.getOrNull(end)
                if ((before == null || !before.isLetterOrDigit()) && (after == null || !after.isLetterOrDigit())) {
                    c.add(EntityType.PERSON, idx, end, term)
                }
                from = end
            }
        }
    }

    // ---------------------------------------------------------------- money and numbers

    private fun multiplier(m: String?): Double = when (m?.lowercase()?.trim()) {
        null, "" -> 1.0
        "k", "duisend", "thousand" -> 1e3
        "m", "mil", "miljoen", "million" -> 1e6
        "bn", "miljard", "billion" -> 1e9
        else -> 1.0
    }

    private fun currencyCode(sym: String): String = when (sym.lowercase()) {
        "r", "zar", "rand", "rande", "sent", "cents" -> "ZAR"
        "$", "us$", "usd", "dollar", "dollars" -> "USD"
        "€", "eur", "euro", "euros" -> "EUR"
        "£", "pond", "pounds" -> "GBP"
        else -> "ZAR"
    }

    private fun money(v: Double, code: String) = "%s %.2f".format(Locale.ROOT, code, v)

    private fun amounts(c: Ctx) {
        val t = c.text
        Regex("""(?<![\p{L}\d])(R|ZAR|US\$|\$|€|£|EUR|USD)\s?($NUM)(?:\s?(?i:($MULT)))?(?![\p{L}\d])""").findAll(t).forEach { m ->
            val v = NumberWords.parseDigits(m.groupValues[2]) ?: return@forEach
            val mult = m.groups[3]?.value
            c.add(EntityType.AMOUNT, m.range.first, m.range.last + 1, money(v * multiplier(mult), currencyCode(m.groupValues[1])))
        }
        Regex("""(?i)(?<![\p{L}\d])($NUM)(?:\s?($MULT))?\s+(rand|rande|zar|dollars?|euros?|pond|pounds|sent|cents)\b""").findAll(t).forEach { m ->
            var v = NumberWords.parseDigits(m.groupValues[1]) ?: return@forEach
            v *= multiplier(m.groups[2]?.value)
            val cur = m.groupValues[3]
            if (cur.lowercase() in setOf("sent", "cents")) v /= 100.0
            c.add(EntityType.AMOUNT, m.range.first, m.range.last + 1, money(v, currencyCode(cur)))
        }
    }

    private fun percentsAndMeasures(c: Ctx) {
        val t = c.text
        Regex("""(?i)(?<![\p{L}\d])($NUM)\s?(%|persent|percent|per cent)""").findAll(t).forEach { m ->
            val v = NumberWords.parseDigits(m.groupValues[1]) ?: return@forEach
            c.add(EntityType.PERCENT, m.range.first, m.range.last + 1, "${trimNum(v)}%")
        }
        Regex("""(?i)(?<![\p{L}\d])($NUM)(?:\s?(?:x|by|keer)\s?($NUM))?\s?($units)(?![\p{L}\d])""").findAll(t).forEach { m ->
            val a = NumberWords.parseDigits(m.groupValues[1]) ?: return@forEach
            val b = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { NumberWords.parseDigits(it) }
            val unit = m.groupValues[3]
            val value = if (b != null) "${trimNum(a)} × ${trimNum(b)} $unit" else "${trimNum(a)} $unit"
            c.add(EntityType.MEASUREMENT, m.range.first, m.range.last + 1, value)
        }
    }

    private fun trimNum(v: Double): String =
        if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else "%.2f".format(Locale.ROOT, v).trimEnd('0').trimEnd('.')

    private fun numberWords(c: Ctx, toks: List<Tok>) {
        var i = 0
        while (i < toks.size) {
            val first = NumberWords.wordValue(toks[i].w)
            if (first == null) { i++; continue }
            val values = arrayListOf(first)
            var j = i + 1
            while (j < toks.size) {
                val v = NumberWords.wordValue(toks[j].w)
                if (v != null) { values += v; j++; continue }
                if (NumberWords.isConnector(toks[j].w) && j + 1 < toks.size && NumberWords.wordValue(toks[j + 1].w) != null) { j++; continue }
                break
            }
            val value = NumberWords.combine(values)
            val s = toks[i].s
            var e = toks[j - 1].e
            val next = toks.getOrNull(j)?.f
            when {
                next != null && next in currencyWords -> {
                    e = toks[j].e
                    val v = if (next in setOf("sent", "cents")) value / 100.0 else value.toDouble()
                    c.add(EntityType.AMOUNT, s, e, money(v, currencyCode(next)))
                }
                next != null && next in percentWords -> c.add(EntityType.PERCENT, s, toks[j].e, "$value%")
                next != null && next in unitWords -> c.add(EntityType.MEASUREMENT, s, toks[j].e, "$value ${toks[j].w}")
                values.size > 1 || value >= 10 -> c.add(EntityType.NUMBER, s, e, value.toString())
            }
            i = j
        }
    }

    private fun digits(c: Ctx) {
        Regex("""(?<![\p{L}\d.,])($NUM)(?![\p{L}\d])""").findAll(c.text).forEach { m ->
            val v = NumberWords.parseDigits(m.value) ?: return@forEach
            c.add(EntityType.NUMBER, m.range.first, m.range.last + 1, trimNum(v))
        }
    }

    // ---------------------------------------------------------------- colours, shapes, places, people

    private fun colours(c: Ctx, toks: List<Tok>) {
        for ((i, t) in toks.withIndex()) {
            val hex = Gazetteer.colours[t.f] ?: continue
            if (t.f == "wit" && toks.getOrNull(i + 1)?.f in setOf("jy", "ek")) continue
            val prev = toks.getOrNull(i - 1)
            val start = if (prev != null && prev.f in Gazetteer.colourModifiers && t.s - prev.e <= 1) prev.s else t.s
            c.add(EntityType.COLOUR, start, t.e, hex)
        }
    }

    private fun shapes(c: Ctx, toks: List<Tok>) {
        for (t in toks) {
            val shape = Gazetteer.shapes[t.f] ?: continue
            c.add(EntityType.SHAPE, t.s, t.e, shape)
        }
    }

    private fun places(c: Ctx, toks: List<Tok>) {
        var i = 0
        while (i < toks.size) {
            var matched = false
            val lowerOk = toks[i].w.length >= 4 && toks[i].f !in ambiguousLower
            if (toks[i].cap || lowerOk) {
                for (n in Gazetteer.maxPlaceWords downTo 1) {
                    if (i + n > toks.size) continue
                    val phrase = toks.subList(i, i + n).joinToString(" ") { it.f }
                    val canonical = Gazetteer.places[phrase] ?: continue
                    if (c.add(EntityType.PLACE, toks[i].s, toks[i + n - 1].e, canonical)) { i += n; matched = true; break }
                }
                if (!matched && toks[i].cap && toks[i].w.length >= 6 && toks[i].f !in Gazetteer.firstNames) {
                    Fuzzy.best(toks[i].w, Gazetteer.placeSingleWords, 0.93)?.let { key ->
                        if (c.add(EntityType.PLACE, toks[i].s, toks[i].e, Gazetteer.places[key] ?: toks[i].w)) { i++; matched = true }
                    }
                }
            }
            if (!matched) i++
        }
    }

    private fun isSentenceStart(text: String, idx: Int): Boolean {
        var k = idx - 1
        while (k >= 0 && text[k].isWhitespace()) k--
        return k < 0 || text[k] in ".!?…:\"“"
    }

    private fun people(c: Ctx, toks: List<Tok>, knownNames: Set<String>) {
        var i = 0
        while (i < toks.size) {
            val t = toks[i]
            val prevIsTitle = i > 0 && toks[i - 1].f.trimEnd('.') in Gazetteer.titles
            val known = t.f in Gazetteer.firstNames || t.f in knownNames
            val unknownCap = t.cap && !isSentenceStart(c.text, t.s) && t.w.length >= 3 &&
                t.f !in Gazetteer.stopCapitalised && Gazetteer.months[t.f] == null && Gazetteer.weekdays[t.f] == null &&
                !t.w.all { it.isUpperCase() || it.isDigit() } && t.w.none { it.isDigit() }
            val lowerKnown = !t.cap && known && t.f !in ambiguousLower && t.w.length >= 3
            if (((t.cap && (known || prevIsTitle || unknownCap)) || lowerKnown) && t.f !in Gazetteer.stopCapitalised) {
                val start = if (prevIsTitle) toks[i - 1].s else t.s
                var j = i + 1
                // Extend with surname particles and further capitalised words: "Jan van der Merwe".
                while (j < toks.size && (toks[j].cap || toks[j].f in nameParticles) && toks[j].s - toks[j - 1].e <= 1 &&
                    toks[j].f !in Gazetteer.stopCapitalised && Gazetteer.places[toks[j].f] == null
                ) j++
                while (j - 1 > i && toks[j - 1].f in nameParticles) j--
                val name = c.text.substring(t.s, toks[j - 1].e).split(' ').joinToString(" ") { w ->
                    if (w in nameParticles) w else w.replaceFirstChar { ch -> ch.uppercase() }
                }
                if (c.add(EntityType.PERSON, start, toks[j - 1].e, name)) { i = j; continue }
            }
            i++
        }
    }

    private fun addresses(c: Ctx) {
        val street = """Street|St|Road|Rd|Avenue|Ave|Drive|Dr|Lane|Crescent|Close|Way|Boulevard|Place|Straat|Laan|Rylaan|Weg|Singel|Plein|[\p{Lu}][\p{L}-]*(?:straat|laan|weg|singel|rylaan|plein|oord)"""
        val suburb = """(?:,\s*[\p{Lu}][\p{L}'’-]+(?:\s+[\p{Lu}][\p{L}'’-]+)?){0,2}"""
        Regex("""\b\d{1,5}[A-Za-z]?\s+(?:[\p{Lu}][\p{L}'’-]*\s+){0,4}(?:$street)\b\.?$suburb""").findAll(c.text).forEach { m ->
            c.add(EntityType.ADDRESS, m.range.first, m.range.last + 1, m.value.trim().trimEnd(',', '.'))
        }
        Regex("""\b[\p{Lu}][\p{L}-]*(?:straat|laan|weg|singel|rylaan|plein)\s+\d{1,5}[A-Za-z]?\b$suburb""").findAll(c.text).forEach { m ->
            c.add(EntityType.ADDRESS, m.range.first, m.range.last + 1, m.value.trim().trimEnd(',', '.'))
        }
    }

    // ---------------------------------------------------------------- tasks

    private val actionVerbs = setOf(
        "stuur", "bel", "maak", "doen", "reel", "reël", "betaal", "lewer", "voltooi", "finaliseer", "bestel", "kontak",
        "skryf", "berei", "kry", "koop", "send", "call", "prepare", "finish", "submit", "deliver", "pay", "order",
        "contact", "write", "book", "arrange", "complete", "update", "check", "confirm", "bevestig",
    )
    private val modalAfterName = setOf("sal", "gaan", "moet", "will", "must", "should", "gaan")

    private fun tasks(c: Ctx, u: Utterance, speakerNames: Map<Int, String>) {
        val t = c.text
        Regex("""[^.!?…]+[.!?…]?""").findAll(t).forEach { m ->
            val sentence = m.value
            val text = sentence.trim()
            if (text.length < 8) return@forEach
            val sStart = m.range.first
            val sEnd = m.range.last + 1
            val low = " " + Fuzzy.fold(sentence).replace(Regex("""[,;:]"""), " ") + " "
            val inSentence = c.list.filter { it.start >= sStart && it.end <= sEnd }
            val persons = inSentence.filter { it.type == EntityType.PERSON }
            val date = inSentence.firstOrNull { it.type == EntityType.DATE }?.value ?: ""
            val words = low.trim().split(Regex("""\s+"""))

            val phrase = (taskAf + taskEn).firstOrNull { low.contains(" " + Fuzzy.fold(it) + " ") }
            // "Jan sal …", "Sarah will …": a named person followed by a commitment word.
            val namedOwner = persons.firstOrNull { p ->
                val after = t.substring(p.end).trimStart().split(Regex("""\s+""")).firstOrNull()?.let { Fuzzy.fold(it) }
                after in modalAfterName
            }
            val dueAction = date.isNotEmpty() && words.any { it in actionVerbs }
            if (phrase == null && namedOwner == null && !dueAction) return@forEach

            val owner = when {
                namedOwner != null -> namedOwner.value.ifEmpty { namedOwner.text }
                phrase != null && phrase in selfTask -> speakerNames[u.speaker] ?: "Person ${u.speaker + 1}"
                persons.isNotEmpty() -> persons.first().value.ifEmpty { persons.first().text }
                else -> ""
            }
            val off = sentence.indexOf(text)
            // Tasks overlap other details by design, so they bypass the "taken" check.
            c.list.add(Entity(EntityType.TASK, text, "$owner|$date", c.ui, sStart + off, sStart + off + text.length))
        }
    }

    // ---------------------------------------------------------------- fuzzy name grouping

    private fun unifyNames(all: List<Entity>): List<Entity> {
        val people = all.filter { it.type == EntityType.PERSON }
        if (people.isEmpty()) return all
        val groups = ArrayList<MutableList<String>>()
        for (p in people.map { it.value.ifEmpty { it.text } }) {
            val f = Fuzzy.fold(p)
            val g = groups.firstOrNull { grp -> Fuzzy.jaroWinkler(Fuzzy.fold(grp.first()), f) >= 0.92 }
            if (g != null) g.add(p) else groups.add(mutableListOf(p))
        }
        val canonical = HashMap<String, String>()
        for (g in groups) {
            val best = g.groupingBy { it }.eachCount().maxWith(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key.length }).key
            g.forEach { canonical[it] = best }
        }
        return all.map { if (it.type == EntityType.PERSON) it.copy(value = canonical[it.value.ifEmpty { it.text }] ?: it.value) else it }
    }
}
