package com.bigoke.metings.engine

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WhisperNative {
    init {
        System.loadLibrary("metings_whisper")
    }

    external fun initContext(modelPath: String): Long
    external fun freeContext(ptr: Long)
    external fun requestAbort(value: Boolean)
    external fun progress(): Int
    external fun systemInfo(): String
    external fun transcribe(
        ptr: Long, samples: FloatArray, language: String, threads: Int, beamSize: Int, prompt: String,
    ): ByteArray?
}

/** One recognised word with times in milliseconds relative to the audio passed in. */
data class Word(val startMs: Long, val endMs: Long, val text: String, val prob: Float, val segment: Int)

class Transcriber(modelFile: File) : AutoCloseable {

    private val ctx: Long = WhisperNative.initContext(modelFile.absolutePath)

    init {
        require(ctx != 0L) { "Could not load the speech model. Re-download it in Models." }
    }

    fun transcribe(samples: FloatArray, language: String, threads: Int, beamSize: Int, prompt: String): List<Word> {
        WhisperNative.requestAbort(false)
        val bytes = WhisperNative.transcribe(ctx, samples, language, threads, beamSize, prompt)
            ?: throw IllegalStateException("Transcription stopped")
        return parse(bytes)
    }

    fun progress(): Int = WhisperNative.progress()

    fun abort() = WhisperNative.requestAbort(true)

    override fun close() {
        WhisperNative.freeContext(ctx)
    }

    private class Tok(val t0: Int, val t1: Int, val seg: Int, val p: Float, val bytes: ByteArray)

    private fun parse(bytes: ByteArray): List<Word> {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val toks = ArrayList<Tok>()
        while (bb.remaining() >= 20) {
            val t0 = bb.int; val t1 = bb.int; val seg = bb.int; val p = bb.float; val len = bb.int
            if (len < 0 || len > bb.remaining()) break
            val b = ByteArray(len); bb.get(b)
            toks.add(Tok(t0, t1, seg, p, b))
        }
        // Group tokens into words: a token whose bytes begin with a space starts a new word.
        val words = ArrayList<Word>()
        var cur = java.io.ByteArrayOutputStream()
        var start = -1; var end = 0; var seg = 0; var probSum = 0f; var n = 0
        fun flush() {
            if (n == 0) return
            val text = String(cur.toByteArray(), Charsets.UTF_8).trim()
            if (text.isNotEmpty() && !text.startsWith("[") && !text.startsWith("(")) {
                words.add(Word(start * 10L, end * 10L, text, probSum / n, seg))
            }
            cur = java.io.ByteArrayOutputStream(); start = -1; probSum = 0f; n = 0
        }
        for (t in toks) {
            val newWord = t.bytes.isNotEmpty() && t.bytes[0] == ' '.code.toByte()
            if ((newWord || t.seg != seg) && n > 0) flush()
            if (start < 0) start = t.t0
            end = maxOf(end, t.t1)
            seg = t.seg
            cur.write(t.bytes)
            probSum += t.p; n++
        }
        flush()
        return words
    }
}
