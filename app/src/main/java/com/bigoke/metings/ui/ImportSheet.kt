package com.bigoke.metings.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bigoke.metings.MetingsApp
import com.bigoke.metings.data.Templates
import com.bigoke.metings.engine.ImportCandidate
import com.bigoke.metings.engine.ImportOptions
import com.bigoke.metings.engine.ImportSource
import com.bigoke.metings.engine.ImportSources
import com.bigoke.metings.util.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Progress of the import running now (shown on the home screen). */
object ImportProgress {
    var fraction by mutableStateOf<Float?>(null)
    var label by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
}

fun sourceIcon(s: ImportSource): ImageVector = when (s) {
    ImportSource.WHATSAPP, ImportSource.SIGNAL -> Icons.Outlined.Chat
    ImportSource.TELEGRAM -> Icons.Outlined.Send
    ImportSource.CALLS, ImportSource.TRUECALLER -> Icons.Outlined.Call
    ImportSource.RECORDER -> Icons.Outlined.Mic
    ImportSource.OTHER -> Icons.Outlined.GraphicEq
}

fun sourceColor(s: ImportSource): Color = when (s) {
    ImportSource.WHATSAPP -> Color(0xFF25D366)
    ImportSource.TELEGRAM -> Color(0xFF2AABEE)
    ImportSource.SIGNAL -> Color(0xFF3A76F0)
    ImportSource.CALLS -> Color(0xFF7C5CFF)
    ImportSource.TRUECALLER -> Color(0xFF0087FF)
    ImportSource.RECORDER -> Color(0xFFE5484D)
    ImportSource.OTHER -> Color(0xFF6B7280)
}

private val mediaPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

/**
 * Import sheet: recent WhatsApp voice notes, call recordings and other audio in one tap, or any file.
 * [shared] holds files sent to Notule from another app (WhatsApp "Share", Truecaller, a call recorder…).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(
    shared: List<Uri>,
    onDismiss: () -> Unit,
    onImport: (List<Uri>, ImportOptions) -> Unit,
) {
    val ctx = LocalContext.current
    val app = MetingsApp.instance
    val s = app.prefs.settings.value
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var language by remember { mutableStateOf(s.importLanguage) }
    var template by remember { mutableStateOf(s.importTemplate) }
    var bySource by remember { mutableStateOf(s.importFolderBySource) }
    var transcribe by remember { mutableStateOf(s.importTranscribe) }
    var remember_ by remember { mutableStateOf(true) }
    var quick by remember { mutableStateOf(s.quickImport) }
    var showOptions by remember { mutableStateOf(shared.isNotEmpty()) }

    fun permitted() = ImportSources.hasFullAccess() ||
        mediaPermissions.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    var access by remember { mutableStateOf(permitted()) }
    var scanTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { val a = permitted(); if (a != access) { access = a; scanTick++ } }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { access = permitted(); scanTick++ }

    var candidates by remember { mutableStateOf<List<ImportCandidate>?>(null) }
    LaunchedEffect(access, scanTick) {
        if (shared.isEmpty() && access) candidates = withContext(Dispatchers.IO) { runCatching { ImportSources.recent(ctx) }.getOrDefault(emptyList()) }
    }
    var filter by remember { mutableStateOf<ImportSource?>(null) }
    var picked by remember { mutableStateOf(setOf<Uri>()) }

    fun options(): ImportOptions {
        if (remember_) app.prefs.update {
            it.copy(importLanguage = language, importTemplate = template, importFolderBySource = bySource, importTranscribe = transcribe, quickImport = quick)
        } else if (quick != s.quickImport) app.prefs.update { it.copy(quickImport = quick) }
        return ImportOptions(language, template, bySource, transcribe)
    }

    val browse = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onImport(uris, options())
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Import audio", "Voer klank in"), style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (shared.isNotEmpty()) tr("${shared.size} file(s) shared with Notule", "${shared.size} lêer(s) met Notule gedeel")
                        else tr("Voice notes, call recordings and other audio", "Stemnotas, oproepopnames en ander klank"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showOptions = !showOptions }) {
                    Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(tr("Options", "Opsies"))
                }
            }
            Spacer(Modifier.height(10.dp))

            if (showOptions) OptionsCard(
                language, { language = it }, template, { template = it }, bySource, { bySource = it },
                transcribe, { transcribe = it }, remember_, { remember_ = it }, quick, { quick = it },
            )

            if (shared.isNotEmpty()) {
                shared.take(6).forEach { u ->
                    val name = remember(u) { ImportSources.displayName(ctx, u) ?: u.lastPathSegment ?: "audio" }
                    val (src, kind) = remember(u) { ImportSources.classify(u.toString() + "/" + name, name) }
                    CandidateRow(src, kind, name, null, selected = true) {}
                }
                if (shared.size > 6) Text("+${shared.size - 6}", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onImport(shared, options()) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(tr("Import ${shared.size}", "Voer ${shared.size} in"))
                }
            } else {
                if (!access) Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(tr("See your WhatsApp voice notes and call recordings here", "Sien jou WhatsApp-stemnotas en oproepopnames hier"),
                            style = MaterialTheme.typography.titleSmall)
                        Text(
                            tr("Allow Notule to look for audio on this phone. Nothing leaves your phone.",
                                "Laat Notule toe om na klank op hierdie foon te soek. Niks verlaat jou foon nie."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { askPermission.launch(mediaPermissions) }) { Text(tr("Allow", "Laat toe")) }
                            if (Build.VERSION.SDK_INT >= 30) OutlinedButton(onClick = {
                                runCatching {
                                    ctx.startActivity(Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName)))
                                }
                            }) { Text(tr("All files (best)", "Alle lêers (beste)")) }
                        }
                    }
                }
                val list = candidates.orEmpty()
                val sources = remember(list) { list.map { it.source }.distinct() }
                if (sources.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filter == null, { filter = null }, label = { Text(tr("All", "Alles")) })
                    sources.forEach { src ->
                        FilterChip(filter == src, { filter = if (filter == src) null else src }, label = { Text(src.label) },
                            leadingIcon = { Icon(sourceIcon(src), null, Modifier.size(16.dp), tint = sourceColor(src)) })
                    }
                }
                val shown = list.filter { filter == null || it.source == filter }
                if (access && candidates == null) Text(tr("Looking…", "Soek…"), Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (access && shown.isEmpty()) Text(
                    tr("No recent voice notes or recordings found. Use Browse, or share a file to Notule from WhatsApp.",
                        "Geen onlangse stemnotas of opnames gevind nie. Gebruik Blaai, of deel 'n lêer vanuit WhatsApp na Notule."),
                    Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it.uri.toString() }) { c ->
                        CandidateRow(c.source, c.kind, c.name, c, selected = c.uri in picked) {
                            picked = if (c.uri in picked) picked - c.uri else picked + c.uri
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { browse.launch(arrayOf("audio/*", "video/*", "application/ogg")) }, modifier = Modifier.height(52.dp)) {
                        Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Browse", "Blaai"))
                    }
                    Button(
                        onClick = { onImport(shown.filter { it.uri in picked }.map { it.uri }, options()) },
                        enabled = picked.isNotEmpty(), modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text(if (picked.isEmpty()) tr("Pick files", "Kies lêers") else tr("Import ${picked.size}", "Voer ${picked.size} in")) }
                }
                Text(
                    tr("Tip: in WhatsApp, press and hold a voice note → Share → Notule.",
                        "Wenk: in WhatsApp, druk en hou 'n stemnota → Deel → Notule."),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CandidateRow(src: ImportSource, kind: String, name: String, c: ImportCandidate?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(sourceColor(src).copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (kind.contains("Video")) Icons.Outlined.Videocam else sourceIcon(src), null, Modifier.size(20.dp), tint = sourceColor(src))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${src.label} · $kind", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            val sub = buildList {
                if (c != null) add(formatDate(c.modified))
                if (c != null && c.durationMs > 0) add(formatDuration(c.durationMs))
                if (c != null && c.size > 0) add(if (c.size > 1_000_000) "%.1f MB".format(c.size / 1e6) else "${c.size / 1000} KB")
                add(name)
            }.joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun OptionsCard(
    language: String, onLanguage: (String) -> Unit,
    template: String, onTemplate: (String) -> Unit,
    bySource: Boolean, onBySource: (Boolean) -> Unit,
    transcribe: Boolean, onTranscribe: (Boolean) -> Unit,
    rememberIt: Boolean, onRemember: (Boolean) -> Unit,
    quick: Boolean, onQuick: (Boolean) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(tr("Language", "Taal"), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("auto" to tr("Auto", "Outo"), "af" to "Afrikaans", "en" to "English").forEach { (k, l) ->
                    FilterChip(language == k, { onLanguage(k) }, label = { Text(l) })
                }
            }
            Text(tr("Type", "Soort"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Templates.all.forEach { t -> FilterChip(template == t.id, { onTemplate(t.id) }, label = { Text(tr(t.en, t.af)) }) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SwitchRow(tr("Folder per source (WhatsApp, Calls…)", "Vouer per bron (WhatsApp, Oproepe…)"), bySource, onBySource)
            SwitchRow(tr("Transcribe straight away", "Transkribeer dadelik"), transcribe, onTranscribe)
            SwitchRow(tr("Remember these options", "Onthou hierdie opsies"), rememberIt, onRemember)
            SwitchRow(
                tr("Quick import: shared files skip this screen", "Vinnige invoer: gedeelde lêers slaan hierdie skerm oor"),
                quick, onQuick,
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(value, onChange)
    }
}
