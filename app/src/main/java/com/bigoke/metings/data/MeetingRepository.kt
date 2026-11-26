package com.bigoke.metings.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Stores each meeting as a folder: meetings/<id>/meeting.bin (encrypted) + audio file. */
class MeetingRepository(context: Context) {

    private val root = File(context.filesDir, "meetings").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }
    private val lock = Any()

    private val _meetings = MutableStateFlow<List<Meeting>>(emptyList())
    val meetings: StateFlow<List<Meeting>> = _meetings.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        val list = root.listFiles()?.mapNotNull { dir ->
            val enc = File(dir, "meeting.bin")
            val legacy = File(dir, "meeting.json")
            when {
                enc.exists() -> runCatching { json.decodeFromString<Meeting>(Crypto.decrypt(enc.readBytes())) }.getOrNull()
                legacy.exists() -> runCatching { json.decodeFromString<Meeting>(legacy.readText()) }.getOrNull()
                    ?.also { m -> runCatching { write(m); legacy.delete() } }
                else -> null
            }
        } ?: emptyList()
        _meetings.value = list.sortedByDescending { it.createdAt }
    }

    fun newId(): String = UUID.randomUUID().toString().substring(0, 13)

    fun folder(id: String): File = File(root, id).apply { mkdirs() }

    fun audio(m: Meeting): File = File(folder(m.id), m.audioFile)

    fun get(id: String): Meeting? = _meetings.value.firstOrNull { it.id == id }

    private fun write(m: Meeting) {
        val dir = folder(m.id)
        val tmp = File(dir, "meeting.bin.tmp")
        tmp.writeBytes(Crypto.encrypt(json.encodeToString(Meeting.serializer(), m)))
        tmp.renameTo(File(dir, "meeting.bin"))
    }

    val folders: List<String> get() = _meetings.value.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted()

    fun save(m: Meeting) {
        synchronized(lock) {
            write(m)
            _meetings.value = (_meetings.value.filterNot { it.id == m.id } + m).sortedByDescending { it.createdAt }
        }
    }

    fun update(id: String, change: (Meeting) -> Meeting): Meeting? {
        synchronized(lock) {
            val current = get(id) ?: return null
            val updated = change(current)
            save(updated)
            return updated
        }
    }

    fun delete(id: String) {
        synchronized(lock) {
            File(root, id).deleteRecursively()
            _meetings.value = _meetings.value.filterNot { it.id == id }
        }
    }
}
