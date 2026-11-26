package com.bigoke.metings.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settings(
    /** Meeting (speech) language: "auto" (detect Afrikaans/English per part), "af", "en" */
    val language: String = "auto",
    /** Replace English slang with the Afrikaans word in Afrikaans sentences. */
    val translateSlang: Boolean = true,
    /** "same" = same as meeting language, "af", "en" */
    val summaryLanguage: String = "same",
    /** App menus and buttons: "system", "af", "en" */
    val uiLanguage: String = "system",
    val removeFillers: Boolean = true,
    val markUnsure: Boolean = true,
    val beamSize: Int = 5,
    val threads: Int = 6,
    val useGpuForSummary: Boolean = true,
    val consentReminder: Boolean = true,
    val keepWav: Boolean = false,
    /** Start transcribing as soon as a recording stops or an import finishes. */
    val autoTranscribe: Boolean = false,
    val appLock: Boolean = false,
    /** Names, companies and terms that should be recognised and spelt correctly. */
    val vocabulary: List<String> = emptyList(),
    val companyName: String = "",
    val preparedBy: String = "",
    val lastFolder: String = "",
    val lastTemplate: String = "general",
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings

    private fun load() = Settings(
        language = sp.getString("language", "auto") ?: "auto",
        translateSlang = sp.getBoolean("translateSlang", true),
        summaryLanguage = sp.getString("summaryLanguage", "same") ?: "same",
        uiLanguage = sp.getString("uiLanguage", "system") ?: "system",
        removeFillers = sp.getBoolean("removeFillers", true),
        markUnsure = sp.getBoolean("markUnsure", true),
        beamSize = sp.getInt("beamSize", 5),
        threads = sp.getInt("threads", defaultThreads()),
        useGpuForSummary = sp.getBoolean("useGpuForSummary", true),
        consentReminder = sp.getBoolean("consentReminder", true),
        keepWav = sp.getBoolean("keepWav", false),
        autoTranscribe = sp.getBoolean("autoTranscribe", false),
        appLock = sp.getBoolean("appLock", false),
        vocabulary = (sp.getString("vocabulary", "") ?: "").lines().map { it.trim() }.filter { it.isNotEmpty() },
        companyName = sp.getString("companyName", "") ?: "",
        preparedBy = sp.getString("preparedBy", "") ?: "",
        lastFolder = sp.getString("lastFolder", "") ?: "",
        lastTemplate = sp.getString("lastTemplate", "general") ?: "general",
    )

    fun update(change: (Settings) -> Settings) {
        val s = change(_settings.value)
        sp.edit()
            .putString("language", s.language)
            .putBoolean("translateSlang", s.translateSlang)
            .putString("summaryLanguage", s.summaryLanguage)
            .putString("uiLanguage", s.uiLanguage)
            .putBoolean("removeFillers", s.removeFillers)
            .putBoolean("markUnsure", s.markUnsure)
            .putInt("beamSize", s.beamSize)
            .putInt("threads", s.threads)
            .putBoolean("useGpuForSummary", s.useGpuForSummary)
            .putBoolean("consentReminder", s.consentReminder)
            .putBoolean("keepWav", s.keepWav)
            .putBoolean("autoTranscribe", s.autoTranscribe)
            .putBoolean("appLock", s.appLock)
            .putString("vocabulary", s.vocabulary.joinToString("\n"))
            .putString("companyName", s.companyName)
            .putString("preparedBy", s.preparedBy)
            .putString("lastFolder", s.lastFolder)
            .putString("lastTemplate", s.lastTemplate)
            .apply()
        _settings.value = s
    }

    companion object {
        fun defaultThreads(): Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 8)
    }
}
