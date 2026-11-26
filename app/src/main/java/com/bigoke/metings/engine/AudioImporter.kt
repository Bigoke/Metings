package com.bigoke.metings.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteOrder

/**
 * Turns any audio file Android can play (m4a, mp3, ogg/opus from WhatsApp, wav, video sound…)
 * into the 16 kHz mono WAV the speech engine needs.
 */
object AudioImporter {

    fun import(context: Context, uri: Uri, outWav: File, onProgress: (Float) -> Unit = {}): Long {
        val ex = MediaExtractor()
        ex.setDataSource(context, uri, null)
        val track = (0 until ex.trackCount).firstOrNull {
            ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IllegalArgumentException("No audio found in this file")
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val mime = fmt.getString(MediaFormat.KEY_MIME)!!
        val durationUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        var inRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(fmt, null, null, 0)
        codec.start()
        val writer = WavWriter(outWav)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        // Linear resampler state
        var pos = 0.0
        var prev = 0f
        var written = 0L
        val outBuf = ShortArray(8192)
        var outN = 0
        fun emit(v: Float) {
            outBuf[outN++] = (v.coerceIn(-1f, 1f) * 32767).toInt().toShort()
            if (outN == outBuf.size) { writer.write(outBuf, outN); written += outN; outN = 0 }
        }
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            if (durationUs > 0) onProgress((ex.sampleTime.toFloat() / durationUs).coerceIn(0f, 1f))
                            ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    inRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else if (o >= 0) {
                    val ob = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN)
                    ob.position(info.offset); ob.limit(info.offset + info.size)
                    val shorts = ob.asShortBuffer()
                    val frames = shorts.remaining() / channels.coerceAtLeast(1)
                    val step = inRate.toDouble() / SAMPLE_RATE
                    for (fIdx in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until channels) sum += shorts.get(fIdx * channels + c) / 32768f
                        val cur = sum / channels
                        // Emit every output sample that falls between prev (t-1) and cur (t).
                        while (pos <= 1.0) {
                            emit(prev + (cur - prev) * pos.toFloat())
                            pos += step
                        }
                        pos -= 1.0
                        prev = cur
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            if (outN > 0) { writer.write(outBuf, outN); written += outN }
        } finally {
            writer.close()
            runCatching { codec.stop() }; codec.release()
            ex.release()
        }
        return written * 1000L / SAMPLE_RATE
    }
}
