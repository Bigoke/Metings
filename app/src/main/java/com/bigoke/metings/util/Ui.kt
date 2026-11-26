package com.bigoke.metings.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.bigoke.metings.MetingsApp
import java.util.Locale

/** App-wide Afrikaans / English for menus and buttons (separate from the meeting language). */
object Ui {
    fun isAf(): Boolean = when (MetingsApp.instance.prefs.settings.value.uiLanguage) {
        "af" -> true
        "en" -> false
        else -> Locale.getDefault().language == "af"
    }

    fun t(en: String, af: String): String = if (isAf()) af else en
}

val LocalAf = compositionLocalOf { false }

/** Translate inside composables; recomposes when the language setting changes. */
@Composable
fun tr(en: String, af: String): String = if (LocalAf.current) af else en
