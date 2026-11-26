package com.bigoke.metings.data

import android.content.Context
import com.bigoke.metings.engine.Fuzzy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Correction(
    /** What the speech model wrote (lower case). */
    val from: String,
    /** What you corrected it to. */
    val to: String,
    val times: Int = 1,
)

/** Corrections learned from your transcript edits, applied to future transcripts. Stored encrypted. */
class CorrectionBook(context: Context) {

    private val file = File(context.filesDir, "corrections.bin")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Correction.serializer())
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Correction>> = _items

    private fun load(): List<Correction> = runCatching {
        if (!file.exists()) emptyList() else json.decodeFromString(serializer, Crypto.decrypt(file.readBytes()))
    }.getOrDefault(emptyList())

    private fun persist(list: List<Correction>) {
        file.writeBytes(Crypto.encrypt(json.encodeToString(serializer, list)))
        _items.value = list
    }

    /** lower-case "from" -> "to", for the text cleaner. */
    fun asMap(): Map<String, String> = _items.value.associate { it.from to it.to }

    /** Correct spellings, used as hints for the speech model. */
    fun hints(): List<String> = _items.value.sortedByDescending { it.times }.map { it.to }.distinct()

    @Synchronized
    fun add(found: List<Pair<String, String>>) {
        if (found.isEmpty()) return
        val list = _items.value.toMutableList()
        for ((from, to) in found) {
            val i = list.indexOfFirst { it.from == from }
            if (i >= 0) list[i] = list[i].copy(to = to, times = list[i].times + 1) else list += Correction(from, to)
        }
        persist(list)
    }

    @Synchronized
    fun remove(from: String) = persist(_items.value.filterNot { it.from == from })

    companion object {
        private val tooCommon = setOf(
            "die", "en", "is", "was", "het", "dit", "dat", "van", "vir", "met", "op", "nie", "ons", "ek", "jy", "hy", "sy",
            "hulle", "sal", "kan", "moet", "'n", "n", "te", "om", "the", "and", "a", "an", "to", "of", "in", "it", "is",
            "we", "you", "i", "he", "she", "they", "that", "this", "for", "on", "be", "are", "was", "so", "ja", "nee",
        )

        private fun words(t: String) = Regex("""[\p{L}\d][\p{L}\d'’\-]*""").findAll(t).map { it.value }.toList()

        /**
         * Compares the transcript before and after your edit and returns the word changes worth learning,
         * e.g. "johannesberg" -> "Johannesburg", "kanto" -> "kantoor", "pieter se" -> "Pietersee".
         */
        fun diff(before: String, after: String): List<Pair<String, String>> {
            val a = words(before); val b = words(after)
            val fa = a.map { Fuzzy.fold(it) }; val fb = b.map { Fuzzy.fold(it) }
            // Longest common subsequence on folded words (case changes count as different below).
            val n = a.size; val m = b.size
            val dp = Array(n + 1) { IntArray(m + 1) }
            for (i in n - 1 downTo 0) for (j in m - 1 downTo 0)
                dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
            val out = ArrayList<Pair<String, String>>()
            var i = 0; var j = 0
            while (i < n || j < m) {
                if (i < n && j < m && a[i] == b[j]) { i++; j++; continue }
                val si = i; val sj = j
                while (i < n || j < m) {
                    if (i < n && j < m && a[i] == b[j]) break
                    if (j < m && (i >= n || dp[i][j + 1] >= dp[i + 1][j])) j++ else i++
                }
                val from = a.subList(si, i); val to = b.subList(sj, j)
                if (from.isEmpty() || to.isEmpty() || from.size > 3 || to.size > 3) continue
                val f = from.joinToString(" ").lowercase()
                val t = to.joinToString(" ")
                if (f.length < 3 || f in tooCommon) continue
                if (fa.subList(si, i) == fb.subList(sj, j) && to.none { it.first().isUpperCase() }) continue
                if (f == t) continue
                out += f to t
            }
            return out
        }
    }
}
