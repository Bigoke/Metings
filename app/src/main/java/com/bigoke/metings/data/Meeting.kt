package com.bigoke.metings.data

import kotlinx.serialization.Serializable

@Serializable
enum class MeetingStatus { RECORDING, QUEUED, PROCESSING, READY, FAILED }

@Serializable
data class Speaker(
    val id: Int,
    val name: String,
    val colorIndex: Int = id,
)

@Serializable
data class Utterance(
    val speaker: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val edited: Boolean = false,
)

@Serializable
enum class EntityType {
    AMOUNT, NUMBER, PERCENT, MEASUREMENT, DATE, TIME, PERSON, PLACE, ADDRESS,
    PHONE, EMAIL, COLOUR, SHAPE, TASK
}

@Serializable
data class Entity(
    val type: EntityType,
    /** Text exactly as it appears in the utterance. */
    val text: String,
    /** Normalised value, e.g. "1500.00" for an amount, "2026-03-15" for a date, "#FF0000" for a colour. */
    val value: String = "",
    val utterance: Int,
    val start: Int,
    val end: Int,
)

@Serializable
data class ActionItem(
    val text: String,
    val owner: String = "",
    val due: String = "",
    val done: Boolean = false,
)

@Serializable
data class Summary(
    val overview: String = "",
    val decisions: List<String> = emptyList(),
    val actions: List<ActionItem> = emptyList(),
    val keyFigures: List<String> = emptyList(),
    val followUps: List<String> = emptyList(),
    /** "gemma" when written by the on-device AI, "extractive" for the key-sentences fallback. */
    val engine: String = "",
    val language: String = "af",
)

@Serializable
data class Meeting(
    val id: String,
    val title: String,
    val createdAt: Long,
    val durationMs: Long = 0,
    val language: String = "af",
    val status: MeetingStatus = MeetingStatus.QUEUED,
    /** File name inside the meeting folder: "audio.wav" while processing, "audio.m4a" when done. */
    val audioFile: String = "audio.wav",
    val speakers: List<Speaker> = emptyList(),
    val utterances: List<Utterance> = emptyList(),
    val entities: List<Entity> = emptyList(),
    val summary: Summary? = null,
    val bookmarksMs: List<Long> = emptyList(),
    val expectedSpeakers: Int = 0,
    val error: String? = null,
    val processingSeconds: Long = 0,
) {
    fun speakerName(id: Int): String = speakers.firstOrNull { it.id == id }?.name ?: "Person ${id + 1}"
    fun speaker(id: Int): Speaker = speakers.firstOrNull { it.id == id } ?: Speaker(id, "Person ${id + 1}")
}
