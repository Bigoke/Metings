package com.bigoke.metings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bigoke.metings.BuildConfig
import com.bigoke.metings.MetingsApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onModels: () -> Unit) {
    val prefs = MetingsApp.instance.prefs
    val s by prefs.settings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            SectionTitle("Language", Modifier.padding(top = 8.dp, bottom = 8.dp))
            Text("Default meeting language", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Segmented(listOf("af" to "Afrikaans", "en" to "English"), s.language) { v -> prefs.update { it.copy(language = v) } }
            Spacer(Modifier.height(14.dp))
            Text("Summary language", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Segmented(listOf("same" to "Same", "af" to "Afrikaans", "en" to "English"), s.summaryLanguage) { v -> prefs.update { it.copy(summaryLanguage = v) } }

            HorizontalDivider(Modifier.padding(vertical = 18.dp))
            SectionTitle("Transcript", Modifier.padding(bottom = 4.dp))
            ToggleRow("Remove filler words", "Drops “uhm”, “eh”, “er” so the text reads cleanly.", s.removeFillers) { v -> prefs.update { it.copy(removeFillers = v) } }
            ToggleRow("Keep original WAV", "Uses more space (≈115 MB per hour) but lets you process a meeting again later.", s.keepWav) { v -> prefs.update { it.copy(keepWav = v) } }
            Spacer(Modifier.height(10.dp))
            Text("Accuracy vs speed (search width: ${s.beamSize})", style = MaterialTheme.typography.bodyMedium)
            Slider(value = s.beamSize.toFloat(), onValueChange = { v -> prefs.update { it.copy(beamSize = v.toInt()) } }, valueRange = 1f..8f, steps = 6)
            Text("Higher is more accurate but slower. 5 is the best-quality default.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Text("Processor threads: ${s.threads}", style = MaterialTheme.typography.bodyMedium)
            Slider(value = s.threads.toFloat(), onValueChange = { v -> prefs.update { it.copy(threads = v.toInt()) } }, valueRange = 2f..8f, steps = 5)

            HorizontalDivider(Modifier.padding(vertical = 18.dp))
            SectionTitle("Summary", Modifier.padding(bottom = 4.dp))
            ToggleRow("Use graphics chip for summary", "Faster. Turn off if summaries fail on your phone.", s.useGpuForSummary) { v -> prefs.update { it.copy(useGpuForSummary = v) } }

            HorizontalDivider(Modifier.padding(vertical = 18.dp))
            SectionTitle("Privacy", Modifier.padding(bottom = 4.dp))
            ToggleRow("Consent reminder", "Show a POPIA reminder before each recording.", s.consentReminder) { v -> prefs.update { it.copy(consentReminder = v) } }
            Text("Metings has no internet features apart from the one-time model download. Recordings stay on this phone.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider(Modifier.padding(vertical = 18.dp))
            Row(Modifier.fillMaxWidth().clickable { onModels() }.padding(vertical = 12.dp)) {
                Text("Manage offline models", style = MaterialTheme.typography.bodyLarge)
            }
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (v, label) ->
            SegmentedButton(selected = selected == v, onClick = { onSelect(v) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label) }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}
