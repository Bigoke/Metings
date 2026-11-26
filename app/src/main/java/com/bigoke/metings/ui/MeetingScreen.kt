package com.bigoke.metings.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bigoke.metings.MetingsApp
import com.bigoke.metings.data.Entity
import com.bigoke.metings.data.EntityType
import com.bigoke.metings.data.Meeting
import com.bigoke.metings.data.MeetingStatus
import com.bigoke.metings.data.Templates
import com.bigoke.metings.engine.EntityExtractor
import com.bigoke.metings.engine.TextCleaner
import com.bigoke.metings.service.ProcessingService
import com.bigoke.metings.ui.theme.MonoNumbers
import com.bigoke.metings.ui.theme.entityColor
import com.bigoke.metings.ui.theme.speakerColor
import com.bigoke.metings.util.EngineGuard
import com.bigoke.metings.util.EntityActions
import com.bigoke.metings.util.Exporter
import com.bigoke.metings.util.LocalAf
import com.bigoke.metings.util.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

private fun reExtract(m: Meeting): Meeting = m.copy(
    entities = EntityExtractor(m.createdAt, MetingsApp.instance.prefs.settings.value.vocabulary)
        .extract(m.utterances, m.speakers.associate { it.id to it.name })
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingScreen(id: String, onBack: () -> Unit, onModels: () -> Unit) {
    val app = MetingsApp.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val af = LocalAf.current
    val meetings by app.repo.meetings.collectAsStateWithLifecycle()
    val progressMap by app.pipeline.progress.collectAsStateWithLifecycle()
    val settings by app.prefs.settings.collectAsStateWithLifecycle()
    val m = meetings.firstOrNull { it.id == id }
    if (m == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val player = rememberAudioPlayer(app.repo.audio(m))
    var tab by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var sheetEntity by remember { mutableStateOf<Entity?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hit by remember { mutableIntStateOf(0) }
    val matches = remember(m.utterances, query) {
        if (query.length < 2) emptyList() else m.utterances.indices.filter { m.utterances[it].text.contains(query, ignoreCase = true) }
    }

    fun save(change: (Meeting) -> Meeting) {
        scope.launch(Dispatchers.Default) { app.repo.update(id, change) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it; hit = 0 }, singleLine = true,
                            placeholder = { Text(tr("Search this meeting", "Soek in hierdie vergadering")) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else Text(m.title, maxLines = 1, modifier = Modifier.clickable { renaming = true })
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back", "Terug")) } },
                actions = {
                    if (searching) {
                        IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.Outlined.Close, tr("Close search", "Maak soek toe")) }
                    } else {
                        if (m.utterances.isNotEmpty()) IconButton(onClick = { searching = true; tab = 1 }) { Icon(Icons.Outlined.Search, tr("Search", "Soek")) }
                        if (m.status == MeetingStatus.READY) IconButton(onClick = { Exporter.share(ctx, m, includePdf = true, includeAudio = true) }) {
                            Icon(Icons.Outlined.Share, tr("Share", "Deel"))
                        }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, tr("More", "Meer")) }
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(tr("Rename", "Hernoem")) }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text(tr("Move to folder", "Skuif na vouer")) }, onClick = { menu = false; moving = true })
                        if (m.status == MeetingStatus.NEW) DropdownMenuItem(text = { Text(tr("Transcribe", "Transkribeer")) }, onClick = {
                            menu = false
                            app.repo.update(id) { it.copy(status = MeetingStatus.QUEUED, error = null) }
                            ProcessingService.enqueue(ctx, id)
                        })
                        if (m.status == MeetingStatus.READY) {
                            DropdownMenuItem(text = { Text(tr("Share minutes (PDF) + audio", "Deel notule (PDF) + klank")) }, onClick = { menu = false; Exporter.share(ctx, m, true, true) })
                            DropdownMenuItem(text = { Text(tr("Share minutes (PDF) only", "Deel slegs notule (PDF)")) }, onClick = { menu = false; Exporter.share(ctx, m, true, false) })
                            DropdownMenuItem(text = { Text(tr("Share as Word document", "Deel as Word-dokument")) }, onClick = { menu = false; Exporter.shareWord(ctx, m) })
                            DropdownMenuItem(text = { Text(tr("Share as text", "Deel as teks")) }, onClick = { menu = false; Exporter.shareText(ctx, m) })
                            DropdownMenuItem(text = { Text(tr("Share audio only", "Deel slegs klank")) }, onClick = { menu = false; Exporter.share(ctx, m, false, true) })
                            DropdownMenuItem(text = { Text(tr("Rewrite summary", "Herskryf opsomming")) }, onClick = { menu = false; ProcessingService.enqueue(ctx, id, summaryOnly = true) })
                        }
                        if (app.repo.audio(m).name.endsWith(".wav") && m.status != MeetingStatus.PROCESSING && m.status != MeetingStatus.NEW) {
                            DropdownMenuItem(text = { Text(tr("Process again", "Verwerk weer")) }, onClick = {
                                menu = false
                                EngineGuard.clear(id)
                                app.repo.update(id) { it.copy(status = MeetingStatus.QUEUED) }
                                ProcessingService.enqueue(ctx, id)
                            })
                        }
                        DropdownMenuItem(text = { Text(tr("Delete", "Skrap"), color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirmDelete = true })
                    }
                },
            )
        },
        bottomBar = {
            Column {
                if (searching && query.length >= 2) {
                    Surface(tonalElevation = 2.dp) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (matches.isEmpty()) tr("No matches", "Niks gevind nie") else "${hit + 1} / ${matches.size}",
                                style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { if (matches.isNotEmpty()) hit = (hit - 1 + matches.size) % matches.size }) { Icon(Icons.Outlined.KeyboardArrowUp, null) }
                            IconButton(onClick = { if (matches.isNotEmpty()) hit = (hit + 1) % matches.size }) { Icon(Icons.Outlined.KeyboardArrowDown, null) }
                        }
                    }
                }
                PlayerBar(player, m, markers = matches.map { m.utterances[it].startMs }, bookmarks = m.bookmarksMs)
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(
                        formatDate(m.createdAt), formatDuration(m.durationMs), m.folder.takeIf { it.isNotBlank() },
                        Templates.get(m.template).takeIf { it.id != "general" }?.let { if (af) it.af else it.en },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f), maxLines = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    m.speakers.take(6).forEach { SpeakerAvatar(it.name, it.colorIndex, 24.dp) }
                }
            }
            when (m.status) {
                MeetingStatus.NEW -> NotTranscribedCard(app.models.coreReady, onModels) {
                    app.repo.update(id) { it.copy(status = MeetingStatus.QUEUED, error = null) }
                    ProcessingService.enqueue(ctx, id)
                }
                MeetingStatus.QUEUED, MeetingStatus.PROCESSING, MeetingStatus.RECORDING ->
                    ProcessingCard(m, progressMap[id]?.stage, progressMap[id]?.overall, app.models.coreReady, onModels) {
                        ProcessingService.cancel(ctx)
                    }
                MeetingStatus.FAILED -> FailedCard(m.error) {
                    EngineGuard.clear(id)
                    app.repo.update(id) { it.copy(status = MeetingStatus.QUEUED, error = null) }
                    ProcessingService.enqueue(ctx, id)
                }
                MeetingStatus.READY -> {}
            }
            if (m.utterances.isNotEmpty() || m.status == MeetingStatus.READY) {
                val tabs = listOf(tr("Summary", "Opsomming"), tr("Transcript", "Transkripsie"), tr("Details", "Besonderhede"), tr("People", "Mense"))
                PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) }) }
                }
                when (tab) {
                    0 -> SummaryTab(m, onModels, onToggle = { idx ->
                        save { mm -> mm.copy(summary = mm.summary?.let { sm -> sm.copy(actions = sm.actions.mapIndexed { i, a -> if (i == idx) a.copy(done = !a.done) else a }) }) }
                    }, onRewrite = { ProcessingService.enqueue(ctx, id, summaryOnly = true) })
                    1 -> TranscriptTab(
                        m, player, settings.markUnsure, query.takeIf { searching && it.length >= 2 },
                        matches.getOrNull(hit), onEntity = { sheetEntity = it }, onEdit = { editing = it },
                    )
                    2 -> DetailsTab(m, af, onEntity = { sheetEntity = it })
                    3 -> PeopleTab(m, onRename = { sid, name, rememberVoice ->
                        val sp = m.speaker(sid)
                        if (rememberVoice) scope.launch(Dispatchers.Default) { app.voices.remember(name, sp.voiceprint) }
                        save { mm ->
                            val old = mm.speakerName(sid)
                            val renamed = mm.copy(speakers = mm.speakers.map { if (it.id == sid) it.copy(name = name, recognised = false) else it })
                            val sum = renamed.summary?.let { s ->
                                fun r(x: String) = x.replace(old, name)
                                s.copy(
                                    overview = r(s.overview), decisions = s.decisions.map(::r), keyFigures = s.keyFigures.map(::r),
                                    followUps = s.followUps.map(::r), extra = s.extra.map(::r),
                                    actions = s.actions.map { a -> a.copy(text = r(a.text), owner = r(a.owner)) },
                                )
                            }
                            reExtract(renamed.copy(summary = sum))
                        }
                    }, onMerge = { from, into ->
                        save { mm ->
                            reExtract(mm.copy(
                                utterances = mm.utterances.map { if (it.speaker == from) it.copy(speaker = into) else it },
                                speakers = mm.speakers.filterNot { it.id == from },
                            ))
                        }
                    })
                }
            }
        }
    }

    if (renaming) {
        var text by remember { mutableStateOf(m.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(tr("Rename meeting", "Hernoem vergadering")) },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { renaming = false; save { it.copy(title = text.trim().ifEmpty { it.title }) } }) { Text(tr("Save", "Stoor")) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(tr("Cancel", "Kanselleer")) } },
        )
    }
    if (moving) {
        var text by remember { mutableStateOf(m.folder) }
        val folders = remember(meetings) { meetings.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted() }
        AlertDialog(
            onDismissRequest = { moving = false },
            title = { Text(tr("Folder / client", "Vouer / kliënt")) },
            text = {
                Column {
                    OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text(tr("New folder name", "Nuwe vouernaam")) })
                    Spacer(Modifier.height(8.dp))
                    FlowRow2 { folders.forEach { f -> AssistChip(onClick = { text = f }, label = { Text(f) }) } }
                }
            },
            confirmButton = { TextButton(onClick = { moving = false; save { it.copy(folder = text.trim()) } }) { Text(tr("Save", "Stoor")) } },
            dismissButton = { TextButton(onClick = { moving = false }) { Text(tr("Cancel", "Kanselleer")) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Delete meeting?", "Skrap vergadering?")) },
            text = { Text(tr("The recording, transcript and summary will be removed from this phone.", "Die opname, transkripsie en opsomming word van hierdie foon verwyder.")) },
            confirmButton = { TextButton(onClick = {
                confirmDelete = false
                if (m.status == MeetingStatus.PROCESSING || m.status == MeetingStatus.QUEUED) ProcessingService.cancel(ctx)
                player.release(); app.repo.delete(id); onBack()
            }) { Text(tr("Delete", "Skrap"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancel", "Kanselleer")) } },
        )
    }
    editing?.let { idx ->
        val u = m.utterances.getOrNull(idx)
        if (u != null) {
            var text by remember(idx) { mutableStateOf(u.text) }
            var spk by remember(idx) { mutableIntStateOf(u.speaker) }
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text(tr("Edit text", "Wysig teks")) },
                text = {
                    Column {
                        FlowRow2 {
                            m.speakers.forEach { s ->
                                AssistChip(onClick = { spk = s.id }, label = { Text(s.name) },
                                    leadingIcon = { if (spk == s.id) Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp)) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(text, { text = it }, minLines = 4)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        editing = null
                        save { mm ->
                            reExtract(mm.copy(utterances = mm.utterances.mapIndexed { i, x ->
                                if (i == idx) x.copy(text = TextCleaner.clean(text, mm.language, false), speaker = spk, edited = true, unsure = emptyList()) else x
                            }))
                        }
                    }) { Text(tr("Save", "Stoor")) }
                },
                dismissButton = { TextButton(onClick = { editing = null }) { Text(tr("Cancel", "Kanselleer")) } },
            )
        }
    }
    sheetEntity?.let { e -> EntitySheet(m, e, af, player, onDismiss = { sheetEntity = null }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow2(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun ProcessingCard(m: Meeting, stage: String?, overall: Float?, coreReady: Boolean, onModels: () -> Unit, onStop: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            if (!coreReady && m.status == MeetingStatus.QUEUED) {
                Text(tr("Waiting for the offline models", "Wag vir die vanlyn-modelle"), style = MaterialTheme.typography.titleSmall)
                Text(tr("Your recording is saved. Download the models and it will be processed automatically.",
                    "Jou opname is gestoor. Laai die modelle af en dit word outomaties verwerk."), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onModels) { Text(tr("Download models", "Laai modelle af")) }
            } else {
                Text(stage?.let { stageText(it) + "…" } ?: tr("Waiting to start…", "Wag om te begin…"), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                if (overall != null) LinearProgressIndicator(progress = { overall }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("Everything runs on your phone. You can leave the app — you'll get a notification when it's ready.",
                            "Alles gebeur op jou foon. Jy kan die toep verlaat — jy kry 'n kennisgewing wanneer dit gereed is."),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onStop) { Text(tr("Stop", "Stop")) }
                }
            }
        }
    }
}

@Composable
private fun NotTranscribedCard(coreReady: Boolean, onModels: () -> Unit, onTranscribe: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(tr("Not transcribed yet", "Nog nie getranskribeer nie"), style = MaterialTheme.typography.titleSmall)
            Text(
                tr("You can listen to the recording below. Tap Transcribe to make the transcript, details and summary — it all happens on this phone.",
                    "Jy kan hieronder na die opname luister. Tik Transkribeer om die transkripsie, besonderhede en opsomming te maak — alles gebeur op hierdie foon."),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            if (coreReady) Button(onClick = onTranscribe) { Text(tr("Transcribe", "Transkribeer")) }
            else Button(onClick = onModels) { Text(tr("Download models first", "Laai eers modelle af")) }
        }
    }
}

@Composable
private fun FailedCard(error: String?, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, null)
            Spacer(Modifier.width(10.dp))
            Text(error ?: tr("Processing failed", "Verwerking het misluk"), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) { Text(tr("Retry", "Probeer weer")) }
        }
    }
}

// ---------------------------------------------------------------------------------------- player

@Composable
private fun PlayerBar(p: AudioPlayer, m: Meeting, markers: List<Long>, bookmarks: List<Long>) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
            if (!p.available) {
                Text(tr("Audio will be available after recording.", "Klank is beskikbaar na die opname."), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                return@Column
            }
            val dur = if (p.duration > 0) p.duration else m.durationMs
            if (dur > 0 && (markers.isNotEmpty() || bookmarks.isNotEmpty())) {
                val hitColor = MaterialTheme.colorScheme.tertiary
                val markColor = MaterialTheme.colorScheme.secondary
                Canvas(Modifier.fillMaxWidth().height(8.dp).padding(horizontal = 10.dp)) {
                    bookmarks.forEach { b ->
                        val x = size.width * (b.toFloat() / dur).coerceIn(0f, 1f)
                        drawLine(markColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 4f)
                    }
                    markers.forEach { t ->
                        val x = size.width * (t.toFloat() / dur).coerceIn(0f, 1f)
                        drawCircle(hitColor, radius = 4f, center = Offset(x, size.height / 2))
                    }
                }
            }
            Slider(
                value = if (dur > 0) (p.position.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                onValueChange = { p.seekTo((it * dur).toLong(), play = p.playing) },
                modifier = Modifier.height(28.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(p.position), style = MonoNumbers, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { p.skip(-10_000) }) { Icon(Icons.Outlined.Replay10, "-10 s") }
                Box(
                    Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape).clickable { p.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (p.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, tr("Play", "Speel"), tint = MaterialTheme.colorScheme.onPrimary)
                }
                IconButton(onClick = { p.skip(10_000) }) { Icon(Icons.Outlined.Forward10, "+10 s") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { p.cycleSpeed() }) { Text("${p.speed}×".replace(".0×", "×")) }
                Text(formatDuration(dur), style = MonoNumbers, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------- summary

@Composable
private fun SummaryTab(m: Meeting, onModels: () -> Unit, onToggle: (Int) -> Unit, onRewrite: () -> Unit) {
    val ctx = LocalContext.current
    val s = m.summary
    var sendTasks by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (s == null) {
            item { Text(tr("The summary is still being prepared.", "Die opsomming word nog gemaak."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@LazyColumn
        }
        item {
            SummaryCard(tr("Overview", "Oorsig")) {
                Text(s.overview.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (s.engine == "gemma") tr("Written on this phone by Gemma", "Op hierdie foon deur Gemma geskryf")
                        else tr("Key sentences (summary AI not used)", "Sleutelsinne (opsomming-KI nie gebruik nie)"),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (s.engine != "gemma" && !MetingsApp.instance.models.isReady(com.bigoke.metings.engine.ModelKind.SUMMARY))
                        TextButton(onClick = onModels) { Text(tr("Get AI", "Kry KI")) }
                    else TextButton(onClick = onRewrite) { Text(tr("Rewrite", "Herskryf")) }
                }
            }
        }
        if (s.decisions.isNotEmpty()) item { SummaryCard(tr("Decisions", "Besluite")) { s.decisions.forEach { Bullet(it) } } }
        if (s.extra.isNotEmpty()) item { SummaryCard(s.extraTitle) { s.extra.forEach { Bullet(it) } } }
        if (s.actions.isNotEmpty()) item {
            SummaryCard(tr("Action items", "Aksiepunte")) {
                s.actions.forEachIndexed { i, a ->
                    Row(Modifier.fillMaxWidth().clickable { onToggle(i) }.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            if (a.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                            tint = if (a.done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.text, style = MaterialTheme.typography.bodyMedium,
                                textDecoration = if (a.done) TextDecoration.LineThrough else null,
                                color = if (a.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            val meta = listOfNotNull(a.owner.takeIf { it.isNotBlank() }, a.due.takeIf { it.isNotBlank() }).joinToString(" · ")
                            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { sendTasks = true }) {
                    Icon(Icons.AutoMirrored.Outlined.Send, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                    Text(tr("Send tasks to people", "Stuur take aan mense"))
                }
            }
        }
        if (s.keyFigures.isNotEmpty()) item { SummaryCard(tr("Key figures", "Sleutelsyfers")) { s.keyFigures.forEach { Bullet(it) } } }
        if (s.followUps.isNotEmpty()) item { SummaryCard(tr("Follow-ups", "Opvolging")) { s.followUps.forEach { Bullet(it) } } }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if (sendTasks && s != null) {
        val owners = s.actions.filter { !it.done }.map { it.owner }.distinct()
        AlertDialog(
            onDismissRequest = { sendTasks = false },
            title = { Text(tr("Send tasks to…", "Stuur take aan…")) },
            text = {
                Column {
                    if (owners.isEmpty()) Text(tr("All tasks are done.", "Alle take is klaar."))
                    owners.forEach { o ->
                        val n = s.actions.count { !it.done && it.owner == o }
                        Row(
                            Modifier.fillMaxWidth().clickable { sendTasks = false; Exporter.shareTasks(ctx, m, o) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(o.ifBlank { tr("Unassigned", "Nie toegewys nie") }, Modifier.weight(1f))
                            Text("$n", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    if (owners.size > 1) {
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().clickable { sendTasks = false; Exporter.shareTasks(ctx, m, "") }.padding(vertical = 10.dp)) {
                            Text(tr("Everyone (all open tasks)", "Almal (alle oop take)"))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { sendTasks = false }) { Text(tr("Cancel", "Kanselleer")) } },
        )
    }
}

@Composable
fun SummaryCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle(title)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Box(Modifier.padding(top = 8.dp).size(6.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------------------------------- transcript

@Composable
private fun TranscriptTab(
    m: Meeting, player: AudioPlayer, markUnsure: Boolean, query: String?, focus: Int?,
    onEntity: (Entity) -> Unit, onEdit: (Int) -> Unit,
) {
    val byUtterance = remember(m.entities) { m.entities.filter { it.type != EntityType.TASK }.groupBy { it.utterance } }
    val taskUtterances = remember(m.entities) { m.entities.filter { it.type == EntityType.TASK }.map { it.utterance }.toSet() }
    val listState = rememberLazyListState()
    val headerItems = if (m.bookmarksMs.isNotEmpty()) 1 else 0
    LaunchedEffect(focus) { if (focus != null) listState.animateScrollToItem(focus + headerItems) }
    val current = m.utterances.indexOfLast { it.startMs <= player.position + 150 }.takeIf { player.playing || player.position > 0 } ?: -1
    val hitBg = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.30f)
    LazyColumn(state = listState, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp)) {
        if (m.bookmarksMs.isNotEmpty()) item {
            FlowRow2 {
                m.bookmarksMs.forEach { b ->
                    AssistChip(onClick = { player.seekTo(b) }, label = { Text(formatDuration(b)) },
                        leadingIcon = { Icon(Icons.Outlined.Bookmark, null, Modifier.size(16.dp)) })
                }
            }
        }
        itemsIndexed(m.utterances) { idx, u ->
            val sp = m.speaker(u.speaker)
            val active = idx == current
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .background(
                        when {
                            idx == focus -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                            active -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            else -> Color.Transparent
                        }, RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpeakerAvatar(sp.name, sp.colorIndex, 26.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(sp.name, style = MaterialTheme.typography.labelLarge, color = speakerColor(sp.colorIndex))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatDuration(u.startMs), style = MonoNumbers, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { player.seekTo(u.startMs) }.padding(4.dp),
                    )
                    if (idx in taskUtterances) {
                        Spacer(Modifier.width(4.dp))
                        Text("● " + tr("task", "taak"), style = MaterialTheme.typography.labelSmall, color = entityColor(EntityType.TASK))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onEdit(idx) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Edit, tr("Edit", "Wysig"), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    highlighted(u.text, byUtterance[idx].orEmpty(), if (markUnsure) u.unsure else emptyList(), query, hitBg, onEntity),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 34.dp).clickable { player.seekTo(u.startMs) },
                )
            }
        }
    }
}

private fun highlighted(
    text: String, entities: List<Entity>, unsure: List<String>, query: String?, hitBg: Color, onEntity: (Entity) -> Unit,
): AnnotatedString = buildAnnotatedString {
    var pos = 0
    for (e in entities.sortedBy { it.start }) {
        if (e.start < pos || e.end > text.length) continue
        append(text.substring(pos, e.start))
        val c = entityColor(e.type)
        val style = SpanStyle(color = c, fontWeight = FontWeight.SemiBold, background = c.copy(alpha = 0.10f))
        withLink(LinkAnnotation.Clickable(tag = "${e.type}:${e.start}", styles = TextLinkStyles(style = style)) { onEntity(e) }) {
            append(text.substring(e.start, e.end))
        }
        pos = e.end
    }
    if (pos < text.length) append(text.substring(pos))
    // Words the speech model was unsure about: underlined in amber so they can be checked against the audio.
    if (unsure.isNotEmpty()) {
        val lower = text.lowercase()
        for (w in unsure) {
            var from = 0
            while (true) {
                val i = lower.indexOf(w, from)
                if (i < 0) break
                val before = lower.getOrNull(i - 1); val after = lower.getOrNull(i + w.length)
                if ((before == null || !before.isLetterOrDigit()) && (after == null || !after.isLetterOrDigit())) {
                    addStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = Color(0xFFB7791F)), i, i + w.length)
                }
                from = i + w.length
            }
        }
    }
    if (!query.isNullOrBlank()) {
        val lower = text.lowercase(); val q = query.lowercase()
        var from = 0
        while (true) {
            val i = lower.indexOf(q, from)
            if (i < 0) break
            addStyle(SpanStyle(background = hitBg, fontWeight = FontWeight.Bold), i, i + q.length)
            from = i + q.length
        }
    }
}

// ---------------------------------------------------------------------------------------- details

@Composable
private fun DetailsTab(m: Meeting, af: Boolean, onEntity: (Entity) -> Unit) {
    val groups = remember(m.entities) { m.entities.groupBy { it.type }.toSortedMap(compareBy { it.ordinal }) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (groups.isEmpty()) item { Text(tr("No details found.", "Geen besonderhede gevind nie."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        groups.forEach { (type, list) ->
            item(key = type.name) {
                SummaryCard("${entityLabel(type, af)} · ${list.size}") {
                    if (type == EntityType.AMOUNT) {
                        val totals = list.mapNotNull { e ->
                            val parts = e.value.split(' ')
                            if (parts.size == 2) parts[0] to (parts[1].toDoubleOrNull() ?: 0.0) else null
                        }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
                        totals.forEach { (cur, sum) ->
                            Text(tr("Total mentioned: ", "Totaal genoem: ") + money(cur, sum),
                                style = MaterialTheme.typography.titleSmall, color = entityColor(EntityType.AMOUNT))
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    if (type == EntityType.TASK) {
                        list.forEach { e ->
                            val (owner, due) = e.value.split('|').let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
                            Column(Modifier.fillMaxWidth().clickable { onEntity(e) }.padding(vertical = 6.dp)) {
                                Text(e.text, style = MaterialTheme.typography.bodyMedium)
                                val meta = listOf(owner, due).filter { it.isNotBlank() }.joinToString(" · ")
                                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    } else {
                        FlowRow2 {
                            list.distinctBy { it.value.ifEmpty { it.text }.lowercase() }.forEach { e ->
                                val c = entityColor(type)
                                Row(
                                    Modifier.padding(vertical = 3.dp)
                                        .border(1.dp, c.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                        .background(c.copy(alpha = 0.07f), RoundedCornerShape(10.dp))
                                        .clickable { onEntity(e) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (type == EntityType.COLOUR) {
                                        Box(Modifier.size(14.dp).background(parseColor(e.value), CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                                    } else Icon(entityIcon(type), null, Modifier.size(15.dp), tint = c)
                                    Spacer(Modifier.width(6.dp))
                                    Text(displayValue(e), style = MaterialTheme.typography.bodyMedium, color = c)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

fun money(code: String, v: Double): String {
    val sym = when (code) { "ZAR" -> "R"; "USD" -> "$"; "EUR" -> "€"; "GBP" -> "£"; else -> "$code " }
    val nf = java.text.NumberFormat.getNumberInstance(Locale("en", "ZA")).apply { maximumFractionDigits = 2; minimumFractionDigits = if (v % 1.0 == 0.0) 0 else 2 }
    return sym + nf.format(v).replace(',', ' ')
}

private fun displayValue(e: Entity): String = when (e.type) {
    EntityType.AMOUNT -> e.value.split(' ').let { if (it.size == 2) money(it[0], it[1].toDoubleOrNull() ?: 0.0) else e.text }
    EntityType.DATE -> runCatching {
        val d = java.time.LocalDate.parse(if (e.value.length == 7) e.value + "-01" else e.value)
        d.format(java.time.format.DateTimeFormatter.ofPattern(if (e.value.length == 7) "MMMM yyyy" else "EEE d MMM yyyy"))
    }.getOrDefault(e.text)
    EntityType.PERSON, EntityType.PLACE -> e.value.ifEmpty { e.text }
    EntityType.COLOUR, EntityType.SHAPE -> e.text
    else -> e.value.ifEmpty { e.text }
}

private fun parseColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

// ---------------------------------------------------------------------------------------- people

@Composable
private fun PeopleTab(m: Meeting, onRename: (Int, String, Boolean) -> Unit, onMerge: (Int, Int) -> Unit) {
    var renameId by remember { mutableStateOf<Int?>(null) }
    var mergeId by remember { mutableStateOf<Int?>(null) }
    val talk = remember(m.utterances) {
        m.utterances.groupBy { it.speaker }.mapValues { (_, us) -> us.sumOf { (it.endMs - it.startMs).coerceAtLeast(0) } to us.sumOf { it.text.split(' ').size } }
    }
    val totalTalk = talk.values.sumOf { it.first }.coerceAtLeast(1)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                tr("Voices are recognised automatically. Name a person once and Notule recognises their voice in future meetings.",
                    "Stemme word outomaties herken. Gee 'n persoon een keer 'n naam en Notule herken hulle stem in toekomstige vergaderings."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        m.speakers.forEach { s ->
            item(key = s.id) {
                val (ms, words) = talk[s.id] ?: (0L to 0)
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SpeakerAvatar(s.name, s.colorIndex, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${formatDuration(ms)} · ${(ms * 100 / totalTalk)}% · $words " + tr("words", "woorde"),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (s.recognised) Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.RecordVoiceOver, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                                    Text(" " + tr("Recognised from earlier meetings", "Herken uit vorige vergaderings"),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(progress = { ms.toFloat() / totalTalk }, color = speakerColor(s.colorIndex), modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { renameId = s.id }) { Text(if (s.recognised) tr("Not them? Rename", "Nie hulle nie? Hernoem") else tr("Rename", "Hernoem")) }
                            if (m.speakers.size > 1) OutlinedButton(onClick = { mergeId = s.id }) { Text(tr("Merge", "Voeg saam")) }
                        }
                    }
                }
            }
        }
    }
    renameId?.let { sid ->
        var text by remember(sid) { mutableStateOf(m.speakerName(sid).let { if (com.bigoke.metings.data.isDefaultSpeakerName(it)) "" else it }) }
        var rememberVoice by remember(sid) { mutableStateOf(true) }
        val hasPrint = m.speaker(sid).voiceprint.isNotEmpty()
        AlertDialog(
            onDismissRequest = { renameId = null },
            title = { Text(tr("Who is ${m.speakerName(sid)}?", "Wie is ${m.speakerName(sid)}?")) },
            text = {
                Column {
                    OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text(tr("Name", "Naam")) })
                    if (hasPrint) Row(Modifier.clickable { rememberVoice = !rememberVoice }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = rememberVoice, onCheckedChange = { rememberVoice = it })
                        Text(tr("Recognise this voice in future meetings", "Herken hierdie stem in toekomstige vergaderings"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { renameId = null; if (text.isNotBlank()) onRename(sid, text.trim(), rememberVoice && hasPrint) }) { Text(tr("Save", "Stoor")) } },
            dismissButton = { TextButton(onClick = { renameId = null }) { Text(tr("Cancel", "Kanselleer")) } },
        )
    }
    mergeId?.let { from ->
        AlertDialog(
            onDismissRequest = { mergeId = null },
            title = { Text(tr("Merge ${m.speakerName(from)} into…", "Voeg ${m.speakerName(from)} saam met…")) },
            text = {
                Column {
                    m.speakers.filter { it.id != from }.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().clickable { mergeId = null; onMerge(from, s.id) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SpeakerAvatar(s.name, s.colorIndex, 28.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(s.name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { mergeId = null }) { Text(tr("Cancel", "Kanselleer")) } },
        )
    }
}

// ---------------------------------------------------------------------------------------- detail sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntitySheet(m: Meeting, e: Entity, af: Boolean, player: AudioPlayer, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val u = m.utterances.getOrNull(e.utterance)
    val sentence = remember(e) {
        val t = u?.text ?: ""
        val s = t.lastIndexOfAny(charArrayOf('.', '!', '?'), (e.start - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val end = t.indexOfAny(charArrayOf('.', '!', '?'), e.end.coerceAtMost(t.length)).let { if (it < 0) t.length else it + 1 }
        if (t.isEmpty()) "" else t.substring(s.coerceAtMost(t.length), end.coerceAtLeast(s)).trim()
    }
    val nearbyTime = m.entities.firstOrNull { it.type == EntityType.TIME && it.utterance == e.utterance }?.value
    val actions = remember(e, af) { EntityActions.forEntity(e, m.title, sentence, nearbyTime, af) }
    val c = entityColor(e.type)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (e.type == EntityType.COLOUR) Box(Modifier.size(28.dp).background(parseColor(e.value), CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                else Icon(entityIcon(e.type), null, tint = c)
                Spacer(Modifier.width(10.dp))
                Text(entityLabel(e.type, af).uppercase(), style = MaterialTheme.typography.labelMedium, color = c)
            }
            Spacer(Modifier.height(8.dp))
            Text(displayValue(e), style = MaterialTheme.typography.headlineSmall)
            if (e.type == EntityType.TASK) {
                val (owner, due) = e.value.split('|').let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
                val meta = listOf(owner, due).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, color = MaterialTheme.colorScheme.secondary)
            }
            if (u != null && e.type != EntityType.TASK) {
                Spacer(Modifier.height(10.dp))
                Text("“$sentence”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("— ${m.speakerName(u.speaker)}, ${formatDuration(u.startMs)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            if (u != null) {
                Button(onClick = { player.seekTo(u.startMs); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp))
                    Text(tr("Play this moment", "Speel hierdie oomblik"))
                }
                Spacer(Modifier.height(8.dp))
            }
            FlowRow2 { actions.forEach { a -> OutlinedButton(onClick = { a.run(ctx) }) { Text(a.label) } } }
            if (e.type == EntityType.AMOUNT) {
                Spacer(Modifier.height(12.dp))
                val v = e.value.split(' ').getOrNull(1)?.toDoubleOrNull()
                if (v != null && e.value.startsWith("ZAR")) {
                    Text(
                        tr("Excl. VAT (15%): ", "Sonder BTW (15%): ") + money("ZAR", v / 1.15) + "   ·   " + tr("Incl. VAT: ", "Met BTW: ") + money("ZAR", v * 1.15),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
