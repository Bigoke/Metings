package com.bigoke.metings.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settings(
    /** "af", "en" */
    val language: String = "af",
    /** "same" = same as meeting language, "af", "en" */
    val summaryLanguage: String = "same",
    val removeFillers: Boolean = true,
    val beamSize: Int = 5,
    val threads: Int = 6,
    val useGpuForSummary: Boolean = true,
    val consentReminder: Boolean = true,
    val keepWav: Boolean = false,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings

    private fun load() = Settings(
        language = sp.getString("language", "af") ?: "af",
        summaryLanguage = sp.getString("summaryLanguage", "same") ?: "same",
        removeFillers = sp.getBoolean("removeFillers", true),
        beamSize = sp.getInt("beamSize", 5),
        threads = sp.getInt("threads", defaultThreads()),
        useGpuForSummary = sp.getBoolean("useGpuForSummary", true),
        consentReminder = sp.getBoolean("consentReminder", true),
        keepWav = sp.getBoolean("keepWav", false),
    )

    fun update(change: (Settings) -> Settings) {
        val s = change(_settings.value)
        sp.edit()
            .putString("language", s.language)
            .putString("summaryLanguage", s.summaryLanguage)
            .putBoolean("removeFillers", s.removeFillers)
            .putInt("beamSize", s.beamSize)
            .putInt("threads", s.threads)
            .putBoolean("useGpuForSummary", s.useGpuForSummary)
            .putBoolean("consentReminder", s.consentReminder)
            .putBoolean("keepWav", s.keepWav)
            .apply()
        _settings.value = s
    }

    companion object {
        fun defaultThreads(): Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 8)
    }
}
