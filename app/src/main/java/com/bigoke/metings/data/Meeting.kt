package com.bigoke.metings.data

import kotlinx.serialization.Serializable

private val defaultName = Regex("""^(Person|Persoon) \d+$""")

/** "Person 1" / "Persoon 1": a speaker that has not been named yet. */
fun isDefaultSpeakerName(name: String) = defaultName.matches(name.trim())

fun defaultSpeakerName(id: Int, language: String) = (if (language == "af") "Persoon " else "Person ") + (id + 1)

@Serializable
/** NEW = recorded or imported, not transcribed yet (transcribe on request). */
enum class MeetingStatus { RECORDING, NEW, QUEUED, PROCESSING, READY, FAILED }

@Serializable
data class Speaker(
    val id: Int,
    val name: String,
    val colorIndex: Int = id,
    /** Voiceprint (TitaNet embedding) used to recognise this voice in later meetings. */
    val voiceprint: List<Float> = emptyList(),
    /** True when the name came from a voice remembered in an earlier meeting. */
    val recognised: Boolean = false,
    /** "said" when the name was picked up from the conversation ("my name is…", "Jan, will you…"). */
    val nameSource: String = "",
)

@Serializable
data class Utterance(
    val speaker: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val edited: Boolean = false,
    /** Words the speech model was unsure about (shown underlined so they can be checked). */
    val unsure: List<String> = emptyList(),
    /** "af" or "en" — detected per paragraph when the meeting language is Auto. */
    val lang: String = "",
    /** Start of each spoken word, in ms after [startMs] (used to follow the audio word by word). */
    val wordMs: List<Int> = emptyList(),
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
    /** Template-specific section, e.g. "Resolutions" for a board meeting. */
    val extraTitle: String = "",
    val extra: List<String> = emptyList(),
    /** "gemma" when written by the on-device AI, "extractive" for the key-sentences fallback. */
    val engine: String = "",
    val language: String = "af",
)

@Serializable
data class QaItem(
    val question: String,
    val answer: String = "",
    val askedAt: Long = System.currentTimeMillis(),
    val pending: Boolean = true,
    /** "gemma" or "search" (best matching moments when the AI is not installed). */
    val engine: String = "",
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
    val folder: String = "",
    val template: String = "general",
    /** Questions asked about this meeting ("Ask Notule") and their answers. */
    val qa: List<QaItem> = emptyList(),
    /** True once names said in the conversation have been matched to the voices. */
    val namesChecked: Boolean = false,
    /** People spoken to by name who were not matched to a voice (maybe they did not speak). */
    val addressed: List<String> = emptyList(),
) {
    fun speakerName(id: Int): String = speakers.firstOrNull { it.id == id }?.name ?: defaultSpeakerName(id, language)
    fun speaker(id: Int): Speaker = speakers.firstOrNull { it.id == id } ?: Speaker(id, defaultSpeakerName(id, language))
}
