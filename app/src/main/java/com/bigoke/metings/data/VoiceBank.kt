package com.bigoke.metings.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class KnownVoice(
    val name: String,
    /** Running average of the voiceprints confirmed for this person. */
    val print: List<Float>,
    val samples: Int = 1,
    val lastSeen: Long = System.currentTimeMillis(),
)

/** Voices the user has named, so the same people are recognised in later meetings. Stored encrypted. */
class VoiceBank(context: Context) {

    private val file = File(context.filesDir, "voices.bin")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(KnownVoice.serializer())
    private val _voices = MutableStateFlow(load())
    val voices: StateFlow<List<KnownVoice>> = _voices

    companion object {
        /** Cosine similarity needed to call two voiceprints the same person. */
        const val MATCH = 0.62f

        fun cosine(a: List<Float>, b: List<Float>): Float {
            if (a.isEmpty() || a.size != b.size) return 0f
            var dot = 0.0; var na = 0.0; var nb = 0.0
            for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
            return if (na == 0.0 || nb == 0.0) 0f else (dot / Math.sqrt(na * nb)).toFloat()
        }
    }

    private fun load(): List<KnownVoice> = runCatching {
        if (!file.exists()) emptyList() else json.decodeFromString(serializer, Crypto.decrypt(file.readBytes()))
    }.getOrDefault(emptyList())

    private fun persist(list: List<KnownVoice>) {
        file.writeBytes(Crypto.encrypt(json.encodeToString(serializer, list)))
        _voices.value = list
    }

    /** Best known voice for [print], or null. */
    fun match(print: List<Float>): KnownVoice? {
        if (print.isEmpty()) return null
        return _voices.value.map { it to cosine(it.print, print) }
            .filter { it.second >= MATCH }
            .maxByOrNull { it.second }?.first
    }

    @Synchronized
    fun remember(name: String, print: List<Float>) {
        if (print.isEmpty() || name.isBlank() || isDefaultSpeakerName(name)) return
        val list = _voices.value.toMutableList()
        val i = list.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        if (i >= 0) {
            val v = list[i]
            val n = v.samples.coerceAtMost(9)
            val avg = v.print.indices.map { k -> (v.print[k] * n + print[k]) / (n + 1) }
            list[i] = v.copy(print = if (v.print.size == print.size) avg else print, samples = v.samples + 1, lastSeen = System.currentTimeMillis())
        } else list += KnownVoice(name, print)
        persist(list)
    }

    @Synchronized
    fun forget(name: String) = persist(_voices.value.filterNot { it.name == name })

    @Synchronized
    fun rename(old: String, new: String) = persist(_voices.value.map { if (it.name == old) it.copy(name = new) else it })
}
