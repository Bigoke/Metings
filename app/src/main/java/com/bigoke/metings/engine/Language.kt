package com.bigoke.metings.engine

/** Afrikaans/English helpers: slang translation and spoken phone numbers. */
object Language {

    /** Everyday English words heard in Afrikaans speech, with the Afrikaans word to use instead. */
    val slang: Map<String, String> = mapOf(
        "nice" to "lekker", "cool" to "oulik", "awesome" to "wonderlik", "anyway" to "in elk geval",
        "anyways" to "in elk geval", "actually" to "eintlik", "basically" to "basies", "obviously" to "natuurlik",
        "definitely" to "beslis", "probably" to "waarskynlik", "maybe" to "miskien", "really" to "regtig",
        "sorry" to "jammer", "please" to "asseblief", "thanks" to "dankie", "thank you" to "dankie",
        "okay" to "goed", "ok" to "goed", "meeting" to "vergadering", "meetings" to "vergaderings",
        "deadline" to "sperdatum", "deadlines" to "sperdatums", "budget" to "begroting", "invoice" to "faktuur",
        "invoices" to "fakture", "quote" to "kwotasie", "quotes" to "kwotasies", "email" to "e-pos",
        "e-mail" to "e-pos", "emails" to "e-posse", "phone" to "foon", "weekend" to "naweek", "office" to "kantoor",
        "client" to "kliënt", "clients" to "kliënte", "boss" to "baas", "job" to "werk", "money" to "geld",
        "problem" to "probleem", "problems" to "probleme", "issue" to "kwessie", "issues" to "kwessies",
        "feedback" to "terugvoer", "report" to "verslag", "project" to "projek", "projects" to "projekte",
        "team" to "span", "staff" to "personeel", "payment" to "betaling", "payments" to "betalings",
        "account" to "rekening", "contract" to "kontrak", "supplier" to "verskaffer", "suppliers" to "verskaffers",
        "delivery" to "aflewering", "price" to "prys", "cheap" to "goedkoop", "expensive" to "duur",
        "busy" to "besig", "ready" to "gereed", "important" to "belangrik", "already" to "reeds",
        "tomorrow" to "môre", "today" to "vandag", "yesterday" to "gister",
    )

    private val englishMarkers = setOf(
        "the", "and", "is", "are", "was", "were", "we", "you", "they", "it", "that", "this", "have", "has",
        "will", "would", "can", "should", "to", "of", "for", "with", "on", "at", "be", "not", "but", "what",
    )
    private val afrikaansMarkers = setOf(
        "die", "en", "is", "was", "ons", "jy", "hulle", "dit", "dat", "het", "sal", "kan", "moet", "van",
        "vir", "met", "op", "nie", "maar", "wat", "ek", "'n", "om", "te", "ook", "baie", "hier", "daar",
    )

    /** True when a sentence is mostly English (then English words are left alone). */
    fun mostlyEnglish(sentence: String): Boolean {
        val words = Regex("""[\p{L}']+""").findAll(sentence.lowercase()).map { it.value }.toList()
        val en = words.count { it in englishMarkers && it !in afrikaansMarkers }
        val af = words.count { it in afrikaansMarkers && it !in englishMarkers }
        return en > af
    }

    /** Replaces English slang with Afrikaans words in Afrikaans sentences. */
    fun translateSlang(text: String): String {
        val sentences = Regex("""[^.!?…]+[.!?…]?\s*""").findAll(text).map { it.value }.toList()
        if (sentences.isEmpty()) return text
        val keys = slang.keys.sortedByDescending { it.length }
        val pattern = Regex("""(?i)(?<![\p{L}\-])(""" + keys.joinToString("|") { Regex.escape(it) } + """)(?![\p{L}\-])""")
        return sentences.joinToString("") { s ->
            if (mostlyEnglish(s)) s else pattern.replace(s) { m ->
                val repl = slang[m.value.lowercase()] ?: return@replace m.value
                if (m.value.first().isUpperCase()) repl.replaceFirstChar { it.uppercase() } else repl
            }
        }
    }

    // ------------------------------------------------------------------ spoken phone numbers

    private val digitWords = mapOf(
        "nul" to "0", "zero" to "0", "oh" to "0", "o" to "0",
        "een" to "1", "one" to "1", "twee" to "2", "two" to "2", "drie" to "3", "three" to "3",
        "vier" to "4", "four" to "4", "vyf" to "5", "five" to "5", "ses" to "6", "six" to "6",
        "sewe" to "7", "seven" to "7", "agt" to "8", "eight" to "8", "ag" to "8", "nege" to "9", "nine" to "9",
    )
    private val repeatWords = mapOf("dubbel" to 2, "double" to 2, "trippel" to 3, "triple" to 3)

    private fun tokenDigits(raw: String): String? {
        val w = Fuzzy.fold(raw).trim(',', '.', ';', ':')
        if (w.isEmpty()) return null
        if (w.all { it.isDigit() }) return w
        if (w.matches(Regex("""\d+(-\d+)+"""))) return w.replace("-", "")
        digitWords[w]?.let { return it }
        // Two-digit groups said as words: "twaalf", "vier-en-dertig", "thirty-four"
        val v = NumberWords.wordValue(w) ?: return null
        return if (v in 10..99) v.toString() else null
    }

    private fun formatPhone(d: String): String = when {
        d.length == 10 && d.startsWith("0") -> "${d.substring(0, 3)} ${d.substring(3, 6)} ${d.substring(6)}"
        d.length == 11 && d.startsWith("27") -> "+27 ${d.substring(2, 4)} ${d.substring(4, 7)} ${d.substring(7)}"
        else -> d
    }

    /**
     * Turns phone numbers said as words ("nul agt twee vyf vyf vyf een twee drie vier",
     * "zero eight two double five…") into digits: "082 555 1234".
     */
    fun spokenPhoneNumbers(text: String): String {
        val toks = Regex("""\+?[\p{L}\d][\p{L}\d\-']*""").findAll(text).toList()
        if (toks.size < 4) return text
        val out = StringBuilder()
        var last = 0
        var i = 0
        while (i < toks.size) {
            var j = i
            val digits = StringBuilder()
            var plus = false
            var wordsUsed = 0
            if (toks[i].value.startsWith("+") || Fuzzy.fold(toks[i].value) in setOf("plus")) {
                plus = true
                if (!toks[i].value.startsWith("+")) j++ else {
                    val d = tokenDigits(toks[i].value.drop(1)); if (d != null) { digits.append(d); j++ }
                }
            }
            while (j < toks.size) {
                // Only spaces, commas or hyphens between the parts of a phone number.
                if (j > i && text.substring(toks[j - 1].range.last + 1, toks[j].range.first).any { it !in " ,-" }) break
                val f = Fuzzy.fold(toks[j].value)
                val rep = repeatWords[f]
                if (rep != null && j + 1 < toks.size) {
                    val d = tokenDigits(toks[j + 1].value)
                    if (d != null && d.length == 1) { repeat(rep) { digits.append(d) }; j += 2; wordsUsed += 2; continue }
                }
                var d = tokenDigits(toks[j].value) ?: break
                if (f.length == 1 && f == "o" && digits.isEmpty()) break  // a lone "o" does not start a number
                if (!toks[j].value.all { it.isDigit() }) wordsUsed++
                // "thirty four" said as two words is one pair of digits: 34
                if (d.length == 2 && d[1] == '0' && d[0] != '1' && !toks[j].value.all { it.isDigit() } && j + 1 < toks.size &&
                    text.substring(toks[j].range.last + 1, toks[j + 1].range.first).isBlank()
                ) {
                    val next = Fuzzy.fold(toks[j + 1].value).let { digitWords[it] }
                    if (next != null && next != "0") { d = d.substring(0, 1) + next; j++; wordsUsed++ }
                }
                digits.append(d)
                j++
            }
            val d = digits.toString()
            val looksLikePhone = wordsUsed >= 3 && (
                (d.length == 10 && d.startsWith("0")) || (d.length == 11 && d.startsWith("27")) || (plus && d.length in 10..13)
            )
            if (looksLikePhone) {
                out.append(text, last, toks[i].range.first)
                out.append(if (plus && !d.startsWith("27")) "+$d" else formatPhone(d))
                last = toks[j - 1].range.last + 1
                i = j
            } else i++
        }
        out.append(text.substring(last))
        return out.toString()
    }
}
