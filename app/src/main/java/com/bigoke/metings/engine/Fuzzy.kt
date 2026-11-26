package com.bigoke.metings.engine

import java.text.Normalizer

object Fuzzy {

    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('’', '\'')

    /** Jaro-Winkler similarity in [0, 1]. Tolerates transcription misspellings ("Johannesberg"). */
    fun jaroWinkler(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val range = (maxOf(a.length, b.length) / 2 - 1).coerceAtLeast(0)
        val aM = BooleanArray(a.length)
        val bM = BooleanArray(b.length)
        var matches = 0
        for (i in a.indices) {
            val lo = maxOf(0, i - range)
            val hi = minOf(i + range + 1, b.length)
            for (j in lo until hi) {
                if (bM[j] || a[i] != b[j]) continue
                aM[i] = true; bM[j] = true; matches++; break
            }
        }
        if (matches == 0) return 0.0
        var t = 0
        var k = 0
        for (i in a.indices) {
            if (!aM[i]) continue
            while (!bM[k]) k++
            if (a[i] != b[k]) t++
            k++
        }
        val m = matches.toDouble()
        val jaro = (m / a.length + m / b.length + (m - t / 2.0) / m) / 3.0
        var prefix = 0
        while (prefix < minOf(4, a.length, b.length) && a[prefix] == b[prefix]) prefix++
        return jaro + prefix * 0.1 * (1 - jaro)
    }

    /** Best fuzzy match of [word] against [candidates] (already folded), or null below [threshold]. */
    fun best(word: String, candidates: Collection<String>, threshold: Double): String? {
        val w = fold(word)
        var best: String? = null
        var score = threshold
        for (c in candidates) {
            if (kotlin.math.abs(c.length - w.length) > 3) continue
            val s = jaroWinkler(w, c)
            if (s >= score) { score = s; best = c }
        }
        return best
    }
}
