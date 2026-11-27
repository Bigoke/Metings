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
    private val seen = HashMap<String, Long>()

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

    /** Re-reads only meetings whose file changed on disk (written by the engine process). */
    fun refreshChanged() {
        if (preview) return
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return
        val ids = HashSet<String>()
        for (dir in dirs) {
            val f = File(dir, "meeting.bin")
            if (!f.exists()) continue
            ids += dir.name
            val mod = f.lastModified()
            if (seen[dir.name] == mod) continue
            seen[dir.name] = mod
            val m = runCatching { json.decodeFromString<Meeting>(Crypto.decrypt(f.readBytes())) }.getOrNull() ?: continue
            synchronized(lock) { _meetings.value = (_meetings.value.filterNot { it.id == m.id } + m).sortedByDescending { it.createdAt } }
        }
        if (_meetings.value.any { it.id !in ids && File(root, it.id).exists().not() }) {
            synchronized(lock) { _meetings.value = _meetings.value.filter { File(root, it.id).exists() } }
        }
    }

    /** For design previews / screenshot tests only: show meetings without saving them. */
    @Volatile private var preview = false

    fun showForPreview(list: List<Meeting>) {
        preview = true
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
        val f = File(dir, "meeting.bin")
        tmp.renameTo(f)
        seen[m.id] = f.lastModified()
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
