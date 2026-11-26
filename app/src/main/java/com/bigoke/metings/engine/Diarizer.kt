package com.bigoke.metings.engine

import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.io.File

data class SpeakerTurn(val startMs: Long, val endMs: Long, val speaker: Int)

/**
 * Finds who spoke when: pyannote segmentation finds speech and voice changes, TitaNet turns each
 * stretch of speech into a voiceprint (pitch, timbre…), and clustering groups the voiceprints into
 * Person 1, Person 2, …
 */
class Diarizer(segmentationModel: File, embeddingModel: File, threads: Int) {

    private val config = OfflineSpeakerDiarizationConfig(
        segmentation = OfflineSpeakerSegmentationModelConfig(
            pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(model = segmentationModel.absolutePath),
            numThreads = threads,
        ),
        embedding = SpeakerEmbeddingExtractorConfig(model = embeddingModel.absolutePath, numThreads = threads),
        clustering = FastClusteringConfig(numClusters = -1, threshold = 0.5f),
        minDurationOn = 0.3f,
        minDurationOff = 0.5f,
    )

    private val sd = OfflineSpeakerDiarization(null, config)
    private val embedder by lazy {
        SpeakerEmbeddingExtractor(null, SpeakerEmbeddingExtractorConfig(model = embeddingModel.absolutePath, numThreads = threads))
    }

    /** One voiceprint per speaker in [turns] (times relative to [samples]), from up to ~20 s of their speech. */
    fun voiceprints(samples: FloatArray, turns: List<SpeakerTurn>): Map<Int, FloatArray> {
        val out = HashMap<Int, FloatArray>()
        for ((spk, list) in turns.groupBy { it.speaker }) {
            val stream = embedder.createStream()
            var used = 0
            for (t in list.sortedByDescending { it.endMs - it.startMs }) {
                val s = (t.startMs * SAMPLE_RATE / 1000).toInt().coerceIn(0, samples.size)
                val e = (t.endMs * SAMPLE_RATE / 1000).toInt().coerceIn(s, samples.size)
                if (e - s < SAMPLE_RATE / 2) continue
                stream.acceptWaveform(samples.copyOfRange(s, e), SAMPLE_RATE)
                used += e - s
                if (used > SAMPLE_RATE * 20) break
            }
            stream.inputFinished()
            if (used > 0 && embedder.isReady(stream)) out[spk] = embedder.compute(stream)
            stream.release()
        }
        return out
    }

    /**
     * @param expectedSpeakers 0 = detect automatically.
     * @param sensitivity lower threshold = more speakers.
     */
    fun run(
        samples: FloatArray,
        expectedSpeakers: Int,
        sensitivity: Float = 0.5f,
        onProgress: (Float) -> Unit,
    ): List<SpeakerTurn> {
        sd.setConfig(
            config.copy(
                clustering = FastClusteringConfig(
                    numClusters = if (expectedSpeakers > 0) expectedSpeakers else -1,
                    threshold = sensitivity,
                )
            )
        )
        val segs = sd.processWithCallback(samples, callback = { done, total, _ ->
            if (total > 0) onProgress(done.toFloat() / total)
            0
        })
        // Re-number speakers in order of first appearance so the first voice is always Person 1.
        val order = LinkedHashMap<Int, Int>()
        segs.sortedBy { it.start }.forEach { s -> order.getOrPut(s.speaker) { order.size } }
        return segs.map { SpeakerTurn((it.start * 1000).toLong(), (it.end * 1000).toLong(), order[it.speaker] ?: 0) }
            .sortedBy { it.startMs }
    }

    fun release() {
        sd.release()
        runCatching { embedder.release() }
    }
}
