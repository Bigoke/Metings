package com.bigoke.metings.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bigoke.metings.MetingsApp
import com.bigoke.metings.data.Meeting
import com.bigoke.metings.data.MeetingStatus
import com.bigoke.metings.engine.AudioImporter
import com.bigoke.metings.engine.ProcessProgress
import com.bigoke.metings.service.ProcessingService
import com.bigoke.metings.ui.theme.RecordRed
import com.bigoke.metings.util.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onRecord: () -> Unit, onOpen: (String) -> Unit, onModels: () -> Unit, onSettings: () -> Unit, onTasks: () -> Unit) {
    val app = MetingsApp.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val meetings by app.repo.meetings.collectAsStateWithLifecycle()
    val progress by app.pipeline.progress.collectAsStateWithLifecycle()
    val modelStatus by app.models.status.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(setOf<String>()) }
    val snackbar = remember { SnackbarHostState() }
    val deletedMsg = tr("deleted", "geskrap")
    val undoLabel = tr("Undo", "Ontdoen")
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    fun transcribe(ids: Collection<String>) {
        ids.forEach { id -> app.repo.update(id) { it.copy(status = MeetingStatus.QUEUED, error = null) } }
        ids.firstOrNull()?.let { ProcessingService.enqueue(ctx, it) }
    }
    var folder by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf<Float?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    val coreReady = remember(modelStatus) { app.models.coreReady }
    val folders = remember(meetings) { meetings.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted() }
    val openTasks = remember(meetings) { meetings.sumOf { m -> m.summary?.actions?.count { !it.done } ?: 0 } }
    val importFailed = tr("Could not read this audio file.", "Kon nie hierdie klanklêer lees nie.")
    val importedTitle = tr("Imported", "Ingevoer")
    val pm = ctx.getSystemService(PowerManager::class.java)
    var batteryOk by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(ctx.packageName)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { batteryOk = pm.isIgnoringBatteryOptimizations(ctx.packageName) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val s = app.prefs.settings.value
        val id = app.repo.newId()
        val name = runCatching {
            ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()?.substringBeforeLast('.') ?: importedTitle
        importing = 0f
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val wav = File(app.repo.folder(id), "audio.wav")
                    val dur = AudioImporter.import(ctx, uri, wav) { f -> importing = f }
                    app.repo.save(
                        Meeting(
                            id = id, title = name, createdAt = System.currentTimeMillis(), durationMs = dur,
                            language = s.language, status = if (s.autoTranscribe) MeetingStatus.QUEUED else MeetingStatus.NEW,
                            folder = folder ?: "", template = s.lastTemplate,
                        )
                    )
                }.isSuccess
            }
            importing = null
            if (ok) { if (s.autoTranscribe) ProcessingService.enqueue(ctx, id); onOpen(id) } else { app.repo.delete(id); importError = importFailed }
        }
    }

    val filtered = remember(meetings, query, folder, pendingDelete) {
        meetings.filter { m -> m.id !in pendingDelete && (folder == null || m.folder == folder) }.filter { m ->
            if (query.isBlank()) true else {
                val q = query.lowercase()
                m.title.lowercase().contains(q) || m.summary?.overview?.lowercase()?.contains(q) == true ||
                    m.utterances.any { it.text.lowercase().contains(q) } || m.speakers.any { it.name.lowercase().contains(q) } ||
                    m.folder.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                val canTranscribe = meetings.filter { it.id in selected }.any { it.status == MeetingStatus.NEW || it.status == MeetingStatus.FAILED }
                TopAppBar(
                    title = { Text(tr("${selected.size} selected", "${selected.size} gekies")) },
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Outlined.Close, tr("Cancel", "Kanselleer")) } },
                    actions = {
                        if (canTranscribe && coreReady) IconButton(onClick = {
                            transcribe(meetings.filter { it.id in selected && (it.status == MeetingStatus.NEW || it.status == MeetingStatus.FAILED) }.map { it.id })
                            selected = emptySet()
                        }) { Icon(Icons.Outlined.Subtitles, tr("Transcribe", "Transkribeer")) }
                        IconButton(onClick = { selected = meetings.map { it.id }.toSet() }) { Icon(Icons.Outlined.SelectAll, tr("Select all", "Kies alles")) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, tr("Delete", "Skrap"), tint = MaterialTheme.colorScheme.error) }
                    },
                )
            } else TopAppBar(
                title = {
                    Column {
                        Text("Notule", style = MaterialTheme.typography.headlineSmall)
                        Text(tr("Offline meeting minutes", "Vergaderings, vasgelê en opgesom"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onTasks) {
                        Box {
                            Icon(Icons.Outlined.TaskAlt, tr("Tasks", "Take"))
                            if (openTasks > 0) Text(
                                "$openTasks", style = MaterialTheme.typography.labelSmall, color = RecordRed,
                                modifier = Modifier.align(Alignment.TopEnd).padding(start = 18.dp),
                            )
                        }
                    }
                    IconButton(onClick = onModels) { Icon(Icons.Outlined.CloudDownload, tr("Models", "Modelle")) }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, tr("Settings", "Instellings")) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = { importer.launch(arrayOf("audio/*", "video/*")) }) {
                    Icon(Icons.Outlined.AudioFile, tr("Import audio", "Voer klank in"))
                }
                ExtendedFloatingActionButton(
                    onClick = onRecord,
                    containerColor = RecordRed,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.Mic, null) },
                    text = { Text(tr("Record", "Neem op")) },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val folderOk = app.models.sharedAllowed()
            val steps = listOf(coreReady, folderOk, batteryOk)
            if (steps.any { !it }) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(tr("Get Notule ready", "Maak Notule gereed") + "  ·  ${steps.count { it }}/3", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(progress = { steps.count { it } / 3f }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            SetupStep(coreReady, tr("Download the offline models (${formatBytes(app.models.totalBytes)}, once)", "Laai die vanlyn-modelle af (${formatBytes(app.models.totalBytes)}, eenmalig)"),
                                tr("Open", "Maak oop"), onModels)
                            SetupStep(folderOk, tr("Keep models safe through updates", "Hou modelle veilig deur opdaterings"), tr("Open", "Maak oop"), onModels)
                            SetupStep(batteryOk, tr("Allow processing in the background", "Laat verwerking in die agtergrond toe"), tr("Allow", "Laat toe")) {
                                runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName))) }
                            }
                        }
                    }
                }
            }
            importing?.let { f ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(tr("Importing audio…", "Voer klank in…"), style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            importError?.let { e ->
                item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { importError = null }) }
            }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(tr("Search meetings, people, words…", "Soek vergaderings, mense, woorde…")) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            }
            if (folders.isNotEmpty()) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = folder == null, onClick = { folder = null }, label = { Text(tr("All", "Alles")) })
                        folders.forEach { f ->
                            FilterChip(
                                selected = folder == f, onClick = { folder = if (folder == f) null else f },
                                label = { Text(f) }, leadingIcon = { Icon(Icons.Outlined.Folder, null, Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Mic, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(12.dp))
                            Text(if (query.isBlank()) tr("No meetings yet", "Nog geen vergaderings nie") else tr("Nothing found", "Niks gevind nie"),
                                style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (query.isBlank()) tr("Tap Record to capture your first meeting.", "Tik Neem op vir jou eerste vergadering.")
                                else tr("Try another word.", "Probeer 'n ander woord."),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                if (meetings.isNotEmpty() && selected.isEmpty()) Text(
                    tr("Tip: press and hold a meeting to select, transcribe or delete.", "Wenk: druk en hou 'n vergadering om te kies, te transkribeer of te skrap."),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(filtered, key = { it.id }) { m ->
                MeetingCard(
                    m, progress[m.id], isSelected = m.id in selected, coreReady = coreReady,
                    onClick = { if (selected.isNotEmpty()) selected = if (m.id in selected) selected - m.id else selected + m.id else onOpen(m.id) },
                    onLongClick = { selected = if (m.id in selected) selected - m.id else selected + m.id },
                    onTranscribe = { transcribe(listOf(m.id)) },
                )
            }
        }
    }

    if (confirmDelete) {
        val n = selected.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Delete $n meeting(s)?", "Skrap $n vergadering(s)?")) },
            text = { Text(tr("The recordings, transcripts and summaries will be removed from this phone. This can't be undone.",
                "Die opnames, transkripsies en opsommings word van hierdie foon verwyder. Dit kan nie ontdoen word nie.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val busy = meetings.any { it.id in selected && (it.status == MeetingStatus.PROCESSING) }
                    if (busy) ProcessingService.cancel(ctx)
                    val ids = selected
                    selected = emptySet()
                    pendingDelete = pendingDelete + ids
                    scope.launch {
                        val res = snackbar.showSnackbar("${ids.size} $deletedMsg", actionLabel = undoLabel, duration = SnackbarDuration.Long)
                        if (res == SnackbarResult.ActionPerformed) pendingDelete = pendingDelete - ids
                        else { withContext(Dispatchers.IO) { ids.forEach { app.repo.delete(it) } }; pendingDelete = pendingDelete - ids }
                    }
                }) { Text(tr("Delete", "Skrap"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancel", "Kanselleer")) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MeetingCard(
    m: Meeting, p: ProcessProgress?, isSelected: Boolean, coreReady: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, onTranscribe: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(m.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (m.folder.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.Folder, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" " + m.folder, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDate(m.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                when (m.status) {
                    MeetingStatus.READY -> StatusPill(tr("Ready", "Gereed"), MaterialTheme.colorScheme.secondary)
                    MeetingStatus.NEW -> StatusPill(tr("Not transcribed", "Nie getranskribeer"), MaterialTheme.colorScheme.onSurfaceVariant)
                    MeetingStatus.QUEUED -> StatusPill(tr("Waiting", "Wag"), MaterialTheme.colorScheme.primary)
                    MeetingStatus.PROCESSING -> StatusPill(p?.let { "${(it.overall * 100).toInt()}%" } ?: tr("Working", "Besig"), MaterialTheme.colorScheme.primary)
                    MeetingStatus.FAILED -> StatusPill(tr("Needs attention", "Aandag nodig"), MaterialTheme.colorScheme.error)
                    MeetingStatus.RECORDING -> StatusPill("● REC", RecordRed)
                }
            }
            Spacer(Modifier.height(10.dp))
            when (m.status) {
                MeetingStatus.READY -> {
                    m.summary?.overview?.takeIf { it.isNotBlank() }?.let {
                        Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(formatDuration(m.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(14.dp))
                        Icon(Icons.Outlined.Groups, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text("${m.speakers.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                            m.speakers.take(5).forEach { SpeakerAvatar(it.name, it.colorIndex, 24.dp) }
                        }
                    }
                }
                MeetingStatus.PROCESSING, MeetingStatus.QUEUED -> {
                    Text(
                        p?.let { "${stageText(it.stage)}… ${(it.overall * 100).toInt()}%" } ?: tr("Waiting to process…", "Wag om verwerk te word…"),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (p != null) LinearProgressIndicator(progress = { p.overall }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                MeetingStatus.NEW -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(formatDuration(m.durationMs) + " · " + tr("not transcribed", "nie getranskribeer nie"),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (coreReady) TextButton(onClick = onTranscribe) { Text(tr("Transcribe", "Transkribeer")) }
                }
                MeetingStatus.RECORDING -> Text("● " + tr("Recording", "Neem op"), color = RecordRed, style = MaterialTheme.typography.labelLarge)
                MeetingStatus.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(m.error ?: tr("Processing failed", "Verwerking het misluk"), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, maxLines = 3)
                }
            }
        }
    }
}

@Composable
private fun SetupStep(done: Boolean, label: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
            tint = if (done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        if (!done) TextButton(onClick = onAction) { Text(action) }
    }
}

/** Processing stage names (the pipeline reports them in English). */
@Composable
fun stageText(stage: String): String = when (stage) {
    "Loading audio" -> tr("Loading audio", "Laai klank")
    "Recognising voices" -> tr("Recognising voices", "Herken stemme")
    "Transcribing" -> tr("Transcribing", "Transkribeer")
    "Making it readable" -> tr("Making it readable", "Maak dit leesbaar")
    "Adding punctuation" -> tr("Adding punctuation", "Voeg leestekens by")
    "Finding details" -> tr("Finding details", "Soek besonderhede")
    "Writing summary" -> tr("Writing summary", "Skryf opsomming")
    "Saving audio" -> tr("Saving audio", "Stoor klank")
    else -> stage
}
