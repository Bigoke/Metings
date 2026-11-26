package com.bigoke.metings.engine

/** Makes raw speech-to-text output pleasant to read. */
object TextCleaner {

    private val fillers = setOf(
        "uhm", "uh", "um", "umm", "eh", "ehm", "ehh", "er", "erm", "hmm", "hm", "mm", "mmm", "uhh", "ahm", "aah",
    )

    // Whisper sometimes slips into Dutch spelling for Afrikaans words.
    private val dutchToAfrikaans = mapOf(
        "gebouw" to "gebou", "mensen" to "mense", "eindelijk" to "eintlik", "niet" to "nie",
        "zal" to "sal", "zou" to "sou", "zo" to "so", "zeker" to "seker", "zeggen" to "sê",
        "ik" to "ek", "jij" to "jy", "wij" to "ons",
        "misschien" to "miskien", "vandaag" to "vandag", "morgen" to "môre", "gisteren" to "gister",
        "alleen" to "alleen", "alstublieft" to "asseblief", "dank" to "dankie", "zelf" to "self",
        "zien" to "sien", "zeven" to "sewe", "acht" to "agt", "negen" to "nege", "twintig" to "twintig",
        "vijf" to "vyf", "zes" to "ses", "veertig" to "veertig", "zestig" to "sestig", "zeventig" to "sewentig",
        "tachtig" to "tagtig", "negentig" to "negentig", "duizend" to "duisend", "geld" to "geld",
        "straat" to "straat", "huis" to "huis", "tijd" to "tyd", "mijn" to "my", "jouw" to "jou",
        "hun" to "hulle", "wordt" to "word",
        "nodig" to "nodig", "moeten" to "moet", "kunnen" to "kan", "willen" to "wil", "gaan" to "gaan",
    ).filter { it.key != it.value }

    private val wordRegex = Regex("""[\p{L}'’]+""")

    fun clean(raw: String, language: String, removeFillers: Boolean): String {
        var t = raw.replace(Regex("""\s+"""), " ").trim()
        if (removeFillers) {
            t = t.split(" ").filter { w ->
                val core = w.lowercase().trim(',', '.', '!', '?', ';', ':', '…')
                core !in fillers
            }.joinToString(" ")
        }
        if (language == "af") {
            t = wordRegex.replace(t) { m ->
                val w = m.value
                val repl = dutchToAfrikaans[w.lowercase()] ?: return@replace w
                if (w.first().isUpperCase()) repl.replaceFirstChar { it.uppercase() } else repl
            }
        }
        // Collapse immediate word repeats ("die die" -> "die"), keep deliberate "baie baie" style only once.
        t = Regex("""\b(\p{L}+)(\s+\1\b)+""", RegexOption.IGNORE_CASE).replace(t) { it.groupValues[1] }
        // Tidy punctuation spacing.
        t = t.replace(Regex("""\s+([,.!?;:])"""), "$1")
            .replace(Regex("""([,!?;])(?=\p{L})"""), "$1 ")
            .replace(Regex("""(\p{Ll}{2,})\.(?=\p{Lu}\p{Ll})"""), "$1. ")
            .replace(Regex(""",\s*,+"""), ",")
            .replace(Regex("""\.{4,}"""), "…")
            .replace(Regex("""^[,.;:\s]+"""), "")
            .trim()
        if (t.isEmpty()) return t
        // Afrikaans "'n" stays lower case at the start of a sentence; capitalise the next word instead.
        t = if (t.startsWith("'n ") || t.startsWith("’n ")) {
            t.substring(0, 3) + t.substring(3).replaceFirstChar { it.uppercase() }
        } else t.replaceFirstChar { it.uppercase() }
        // Capital after sentence end.
        t = Regex("""([.!?]\s+)(\p{Ll})""").replace(t) { it.groupValues[1] + it.groupValues[2].uppercase() }
        if (t.last() !in ".!?…") t += "."
        return t
    }
}
