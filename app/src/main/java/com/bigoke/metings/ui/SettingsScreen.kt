package com.bigoke.metings.ui

import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon as AndroidIcon
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bigoke.metings.BuildConfig
import com.bigoke.metings.MetingsApp
import com.bigoke.metings.R
import com.bigoke.metings.service.QuickRecordTile
import com.bigoke.metings.service.RecordWidget
import com.bigoke.metings.util.EngineGuard
import com.bigoke.metings.util.Exporter
import com.bigoke.metings.util.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onModels: () -> Unit) {
    val ctx = LocalContext.current
    val app = MetingsApp.instance
    val prefs = app.prefs
    val s by prefs.settings.collectAsStateWithLifecycle()
    val voices by app.voices.voices.collectAsStateWithLifecycle()
    val learned by app.corrections.items.collectAsStateWithLifecycle()
    var vocabText by remember { mutableStateOf(s.vocabulary.joinToString("\n")) }
    var company by remember { mutableStateOf(s.companyName) }
    var preparedBy by remember { mutableStateOf(s.preparedBy) }
    var logoVersion by remember { mutableIntStateOf(0) }
    val logoFile = Exporter.logoFile(ctx)
    val logo = remember(logoVersion) { if (logoFile.exists()) runCatching { BitmapFactory.decodeFile(logoFile.absolutePath) }.getOrNull() else null }

    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) runCatching {
            val bmp = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
            val scale = minOf(1f, 600f / maxOf(bmp.width, bmp.height))
            val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
            logoFile.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            logoVersion++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Settings", "Instellings")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back", "Terug")) } },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            SectionTitle(tr("Language", "Taal"), Modifier.padding(top = 8.dp, bottom = 8.dp))
            Text(tr("App language", "Toeptaal"), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Segmented(listOf("system" to tr("Phone", "Foon"), "af" to "Afrikaans", "en" to "English"), s.uiLanguage) { v -> prefs.update { it.copy(uiLanguage = v) } }
            Spacer(Modifier.height(14.dp))
            Text(tr("Default meeting language", "Verstek vergaderingtaal"), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Segmented(listOf("auto" to tr("Auto", "Outo"), "af" to "Afrikaans", "en" to "English"), s.language) { v -> prefs.update { it.copy(language = v) } }
            Text(tr("Auto recognises Afrikaans and English by itself, also when people switch between them.",
                "Outo herken self Afrikaans en Engels, ook wanneer mense tussen die twee wissel."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            ToggleRow(tr("Translate English slang", "Vertaal Engelse sleng"),
                tr("In Afrikaans sentences, words like “nice”, “meeting” or “deadline” become “lekker”, “vergadering”, “sperdatum”.",
                    "In Afrikaanse sinne word woorde soos “nice”, “meeting” of “deadline” “lekker”, “vergadering”, “sperdatum”."),
                s.translateSlang) { v -> prefs.update { it.copy(translateSlang = v) } }
            Spacer(Modifier.height(14.dp))
            Text(tr("Summary language", "Opsommingtaal"), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Segmented(listOf("same" to tr("Same", "Dieselfde"), "af" to "Afrikaans", "en" to "English"), s.summaryLanguage) { v -> prefs.update { it.copy(summaryLanguage = v) } }

            HorizontalDivider(Modifier.padding(vertical = 18.dp))
            SectionTitle(tr("Your words", "Jou woorde"), Modifier.padding(bottom = 4.dp))
            Text(
                tr("Names, companies and terms you use often — one per line. Notule listens for them, spells them correctly and highlights them.",
                    "Name, maatskappye en terme wat jy gereeld gebruik — een per reël. Notule luister daarvoor, spel dit reg en merk dit uit."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = vocabText, onValueChange = { vocabText = it }, minLines = 4, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Bigoke (Edms) Bpk\nMarietjie Botha\nEMP201") },
            )
            TextButton(onClick = { prefs.update { it.copy(vocabulary = vocabText.lines().map { l -> l.trim() }.filter { l -> l.isNotEmpty() }) } }) {
                Text(tr("Save words", "Stoor woorde"))
            }

            HorizontalDivider(Modifier.padding(vertical = 18.dp))
            SectionTitle(tr("Learned corrections", "Geleerde regstellings"), Modifier.padding(bottom = 4.dp))
            if (learned.isEmpty()) Text(
                tr("None yet. When you edit a transcript, Notule remembers the corrected words and fixes them next time.",
                    "Nog geen. Wanneer jy 'n transkripsie wysig, onthou Notule die reggestelde woorde en herstel dit volgende keer."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            learned.forEach { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.from, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                    Text("  →  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(c.to, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { app.corrections.remove(c.from) }) { Icon(Icons.Outlined.Delete, tr("Forget", "Vergeet")) }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 18.dp))
            SectionTitle(tr("Known voices", "Bekende stemme"), Modifier.padding(bottom = 4.dp))
            if (voices.isEmpty()) Text(
                tr("None yet. Name a person in a meeting's People tab and their voice is recognised next time.",
                    "Nog geen. Gee 'n persoon 'n naam in 'n vergadering se Mense-oortjie en hulle stem word volgende keer herken."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            voices.forEach { v ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(v.name, style = MaterialTheme.typography.bodyLarge)
                        Text(tr("Heard in ${v.samples} meeting(s)", "Gehoor in ${v.samples} vergadering(s)"), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { app.voices.forget(v.name) }) { Icon(Icons.Outlined.Delete, tr("Forget", "Vergeet")) }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 18.dp))
            SectionTitle(tr("Minutes letterhead", "Notule-briefhoof"), Modifier.padding(bottom = 4.dp))
            OutlinedTextField(company, { company = it }, label = { Text(tr("Company name", "Maatskappynaam")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(preparedBy, { preparedBy = it }, label = { Text(tr("Prepared by (your name)", "Opgestel deur (jou naam)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                if (logo != null) {
                    Image(logo.asImageBitmap(), null, Modifier.size(48.dp))
                    Spacer(Modifier.width(10.dp))
                }
                OutlinedButton(onClick = { pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(if (logo == null) tr("Add logo", "Voeg logo by") else tr("Change logo", "Verander logo"))
                }
                if (logo != null) TextButton(onClick = { logoFile.delete(); logoVersion++ }) { Text(tr("Remove", "Verwyder")) }
            }
            TextButton(onClick = { prefs.update { it.copy(companyName = company.trim(), preparedBy = preparedBy.trim()) } }) {
                Text(tr("Save letterhead", "Stoor briefhoof"))
            }

            HorizontalDivider(Modifier.padding(vertical = 18.dp))
            SectionTitle(tr("Transcript", "Transkripsie"), Modifier.padding(bottom = 4.dp))
            ToggleRow(tr("Transcribe automatically", "Transkribeer outomaties"),
                tr("Start transcribing as soon as a recording stops. When off, tap Transcribe on the meeting yourself.",
                    "Begin transkribeer sodra 'n opname stop. As dit af is, tik self Transkribeer by die vergadering."),
                s.autoTranscribe) { v -> prefs.update { it.copy(autoTranscribe = v) } }
            ToggleRow(tr("Remove filler words", "Verwyder stopwoorde"), tr("Drops “uhm”, “eh”, “er” so the text reads cleanly.", "Laat “uhm”, “eh”, “er” weg sodat die teks skoon lees."), s.removeFillers) { v -> prefs.update { it.copy(removeFillers = v) } }
            ToggleRow(tr("Mark uncertain words", "Merk onsekere woorde"), tr("Underlines words the speech model was unsure about, so you can check them.", "Onderstreep woorde waaroor die spraakmodel onseker was, sodat jy dit kan nagaan."), s.markUnsure) { v -> prefs.update { it.copy(markUnsure = v) } }
            ToggleRow(tr("Keep original WAV", "Hou oorspronklike WAV"), tr("Uses more space (≈115 MB per hour) but lets you process a meeting again later.", "Gebruik meer spasie (≈115 MB per uur) maar laat jou toe om 'n vergadering later weer te verwerk."), s.keepWav) { v -> prefs.update { it.copy(keepWav = v) } }
            Spacer(Modifier.height(10.dp))
            Text(tr("Accuracy vs speed: ", "Akkuraatheid teenoor spoed: ") + s.beamSize, style = MaterialTheme.typography.bodyMedium)
            Slider(value = s.beamSize.toFloat(), onValueChange = { v -> prefs.update { it.copy(beamSize = v.toInt()) } }, valueRange = 1f..8f, steps = 6)
            Text(tr("Higher is more accurate but slower. 5 is the best-quality default.", "Hoër is meer akkuraat maar stadiger. 5 is die beste-gehalte verstek."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Text(tr("Processor threads: ", "Verwerkerdrade: ") + s.threads, style = MaterialTheme.typography.bodyMedium)
            Slider(value = s.threads.toFloat(), onValueChange = { v -> prefs.update { it.copy(threads = v.toInt()) } }, valueRange = 2f..8f, steps = 5)
            ToggleRow(tr("Use graphics chip for the AI", "Gebruik grafiese skyf vir die KI"), tr("Faster. Turn off if summaries fail on your phone.", "Vinniger. Skakel af as opsommings op jou foon misluk."), s.useGpuForSummary) { v -> prefs.update { it.copy(useGpuForSummary = v) } }

            HorizontalDivider(Modifier.padding(vertical = 18.dp))
            SectionTitle(tr("Privacy & security", "Privaatheid en sekuriteit"), Modifier.padding(bottom = 4.dp))
            ToggleRow(tr("App lock", "Toepslot"), tr("Ask for your fingerprint, face or PIN when opening Notule.", "Vra jou vingerafdruk, gesig of PIN wanneer Notule oopgemaak word."), s.appLock) { v -> prefs.update { it.copy(appLock = v) } }
            ToggleRow(tr("Consent reminder", "Toestemming-herinnering"), tr("Show a POPIA reminder before each recording.", "Wys 'n POPIA-herinnering voor elke opname."), s.consentReminder) { v -> prefs.update { it.copy(consentReminder = v) } }
            Text(
                tr("Transcripts, summaries and voices are stored encrypted with a key kept in your phone's secure hardware. Nothing is sent anywhere — the only internet use is the one-time model download.",
                    "Transkripsies, opsommings en stemme word geïnkripteer gestoor met 'n sleutel in jou foon se veilige hardeware. Niks word gestuur nie — die enigste internetgebruik is die eenmalige modelaflaai."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 18.dp))
            SectionTitle(tr("Quick start", "Vinnige begin"), Modifier.padding(bottom = 4.dp))
            Text(tr("Start recording in one tap from your home screen or quick settings.", "Begin opneem met een tik vanaf jou tuisskerm of vinnige instellings."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = 6.dp)) {
                OutlinedButton(onClick = {
                    val awm = ctx.getSystemService(AppWidgetManager::class.java)
                    if (awm.isRequestPinAppWidgetSupported) awm.requestPinAppWidget(ComponentName(ctx, RecordWidget::class.java), null, null)
                }) { Text(tr("Add widget", "Voeg legstuk by")) }
                Spacer(Modifier.width(8.dp))
                if (Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = {
                    ctx.getSystemService(StatusBarManager::class.java).requestAddTileService(
                        ComponentName(ctx, QuickRecordTile::class.java), "Notule",
                        AndroidIcon.createWithResource(ctx, R.drawable.ic_mic), ctx.mainExecutor,
                    ) { }
                }) { Text(tr("Add quick tile", "Voeg teël by")) }
            }

            HorizontalDivider(Modifier.padding(vertical = 18.dp))
            Row(Modifier.fillMaxWidth().clickable { onModels() }.padding(vertical = 12.dp)) {
                Text(tr("Manage offline models", "Bestuur vanlyn-modelle"), style = MaterialTheme.typography.bodyLarge)
            }
            Row(Modifier.fillMaxWidth().clickable {
                Exporter.shareDiagnostics(ctx, EngineGuard.report(
                    "Models: " + app.models.status.value.joinToString { "${it.spec.kind}=${it.state}${if (it.shared) "(shared)" else ""}" } +
                        "\nSettings: ${s.copy(vocabulary = emptyList(), companyName = "", preparedBy = "")}" +
                        "\nMeetings: " + app.repo.meetings.value.joinToString { "${it.id}:${it.status}:${it.durationMs / 1000}s" }
                ))
            }.padding(vertical = 12.dp)) {
                Column {
                    Text(tr("Send diagnostic report", "Stuur diagnoseverslag"), style = MaterialTheme.typography.bodyLarge)
                    Text(tr("Technical log only — no recordings or transcripts.", "Slegs tegniese log — geen opnames of transkripsies nie."),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Notule ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
