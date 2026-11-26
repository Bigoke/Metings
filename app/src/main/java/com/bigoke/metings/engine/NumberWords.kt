package com.bigoke.metings.engine

/** Parses spoken numbers in Afrikaans and English ("vyf-en-twintig duisend", "twenty five thousand"). */
object NumberWords {

    private val units = mapOf(
        // Afrikaans
        "nul" to 0, "een" to 1, "twee" to 2, "drie" to 3, "vier" to 4, "vyf" to 5, "ses" to 6, "sewe" to 7,
        "agt" to 8, "nege" to 9, "tien" to 10, "elf" to 11, "twaalf" to 12, "dertien" to 13, "veertien" to 14,
        "vyftien" to 15, "sestien" to 16, "sewentien" to 17, "agtien" to 18, "negentien" to 19,
        // English
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val tens = mapOf(
        "twintig" to 20, "dertig" to 30, "veertig" to 40, "vyftig" to 50, "sestig" to 60, "sewentig" to 70,
        "tagtig" to 80, "negentig" to 90,
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70,
        "eighty" to 80, "ninety" to 90,
    )
    private val scales = mapOf(
        "honderd" to 100L, "hundred" to 100L, "duisend" to 1_000L, "thousand" to 1_000L,
        "miljoen" to 1_000_000L, "million" to 1_000_000L, "miljard" to 1_000_000_000L, "billion" to 1_000_000_000L,
    )
    private val unitPattern = units.keys.filter { (units[it] ?: 0) in 1..9 }.joinToString("|")
    private val tensPattern = tens.keys.joinToString("|")
    private val afCompound = Regex("^($unitPattern)-?en-?($tensPattern)$")
    private val enCompound = Regex("^($tensPattern)-($unitPattern)$")

    /** Value of a single (possibly compound) word, or null. */
    fun wordValue(raw: String): Long? {
        val w = Fuzzy.fold(raw).trim(',', '.', '!', '?', ';', ':')
        if (w.isEmpty()) return null
        units[w]?.let { return it.toLong() }
        tens[w]?.let { return it.toLong() }
        scales[w]?.let { return it }
        afCompound.matchEntire(w)?.let { m -> return (tens[m.groupValues[2]]!! + units[m.groupValues[1]]!!).toLong() }
        enCompound.matchEntire(w)?.let { m -> return (tens[m.groupValues[1]]!! + units[m.groupValues[2]]!!).toLong() }
        // Afrikaans writes "tweehonderd", "vyfduisend", "tweehonderdvyftig" as one word.
        val byLength = scales.entries.sortedByDescending { it.key.length }
        for ((sw, sc) in byLength) {
            if (w.endsWith(sw) && w.length > sw.length) {
                val left = wordValue(w.dropLast(sw.length)) ?: continue
                return left * sc
            }
        }
        for ((sw, sc) in byLength) {
            val idx = w.indexOf(sw)
            if (idx >= 0 && idx + sw.length < w.length) {
                val left = if (idx == 0) 1L else (wordValue(w.substring(0, idx)) ?: continue)
                val right = wordValue(w.substring(idx + sw.length).removePrefix("en")) ?: continue
                return left * sc + right
            }
        }
        return null
    }

    fun isConnector(w: String): Boolean = Fuzzy.fold(w).trim(',') in setOf("en", "and")

    /** Combines a run of number word values (e.g. [2, 100, 50, 1000]) into one number. */
    fun combine(values: List<Long>): Long {
        var total = 0L
        var current = 0L
        for (v in values) {
            when {
                v == 100L -> current = (if (current == 0L) 1 else current) * 100
                v >= 1000L -> { total += (if (current == 0L) 1 else current) * v; current = 0 }
                else -> current += v
            }
        }
        return total + current
    }

    /** Parses numbers written with digits in SA style: "1 500", "1,500", "1 500,50", "2.5". */
    fun parseDigits(s: String): Double? {
        var t = s.trim().replace(' ', ' ')
        val groups = Regex("""^\d{1,3}([ ,.]\d{3})+([.,]\d{1,2})?$""")
        if (groups.matches(t)) {
            val dec = Regex("""[.,]\d{1,2}$""").find(t)?.value
            var intPart = if (dec != null) t.dropLast(dec.length) else t
            intPart = intPart.replace(" ", "").replace(",", "").replace(".", "")
            t = if (dec != null) intPart + "." + dec.drop(1) else intPart
            return t.toDoubleOrNull()
        }
        return t.replace(" ", "").replace(',', '.').toDoubleOrNull()
    }
}
