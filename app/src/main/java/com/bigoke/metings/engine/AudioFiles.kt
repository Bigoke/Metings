package com.bigoke.metings.engine

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val SAMPLE_RATE = 16000

/** Streaming 16 kHz mono 16-bit PCM WAV writer. */
class WavWriter(file: File) {
    private val raf = RandomAccessFile(file, "rw")
    private var dataBytes = 0L

    init {
        raf.setLength(0)
        raf.write(ByteArray(44))
    }

    fun write(samples: ShortArray, count: Int) {
        val bb = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) bb.putShort(samples[i])
        raf.write(bb.array())
        dataBytes += count * 2
    }

    fun close() {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt((36 + dataBytes).toInt())
        h.put("WAVE".toByteArray()); h.put("fmt ".toByteArray())
        h.putInt(16); h.putShort(1); h.putShort(1)
        h.putInt(SAMPLE_RATE); h.putInt(SAMPLE_RATE * 2)
        h.putShort(2); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(dataBytes.toInt())
        raf.seek(0)
        raf.write(h.array())
        raf.close()
    }
}

object AudioFiles {

    /** Reads a 16-bit mono WAV written by [WavWriter] into floats in [-1, 1]. */
    fun readWavFloats(file: File): FloatArray {
        val total = ((file.length() - 44) / 2).toInt().coerceAtLeast(0)
        val out = FloatArray(total)
        BufferedInputStream(FileInputStream(file), 1 shl 16).use { input ->
            input.skip(44)
            val buf = ByteArray(1 shl 16)
            var idx = 0
            var carry = -1
            while (idx < total) {
                val n = input.read(buf)
                if (n <= 0) break
                var i = 0
                if (carry >= 0 && n > 0) {
                    val s = ((buf[0].toInt() shl 8) or carry).toShort()
                    out[idx++] = s / 32768f
                    carry = -1; i = 1
                }
                while (i + 1 < n && idx < total) {
                    val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xff)).toShort()
                    out[idx++] = s / 32768f
                    i += 2
                }
                if (i < n) carry = buf[i].toInt() and 0xff
            }
        }
        return out
    }

    fun wavDurationMs(file: File): Long = ((file.length() - 44).coerceAtLeast(0) / 2) * 1000L / SAMPLE_RATE

    /** Encodes the WAV to AAC in an .m4a container (64 kbps mono) to keep as the meeting attachment. */
    fun encodeToM4a(wav: File, out: File) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxerStarted = false
        val info = MediaCodec.BufferInfo()
        val input = BufferedInputStream(FileInputStream(wav), 1 shl 16)
        input.skip(44)
        val chunk = ByteArray(8192)
        var inputDone = false
        var outputDone = false
        var samplesQueued = 0L
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        buf.clear()
                        val toRead = minOf(chunk.size, buf.remaining()) and 1.inv()
                        val n = input.read(chunk, 0, toRead)
                        val pts = samplesQueued * 1_000_000L / SAMPLE_RATE
                        if (n <= 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buf.put(chunk, 0, n)
                            codec.queueInputBuffer(inIdx, 0, n, pts, 0)
                            samplesQueued += n / 2
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start(); muxerStarted = true
                    }
                    outIdx >= 0 -> {
                        val ob = codec.getOutputBuffer(outIdx)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxerStarted) {
                            ob.position(info.offset); ob.limit(info.offset + info.size)
                            muxer.writeSampleData(track, ob, info)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            input.close()
            runCatching { codec.stop() }; codec.release()
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
        }
    }
}
