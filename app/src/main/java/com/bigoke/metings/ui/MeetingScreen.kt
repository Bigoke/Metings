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
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
fun MeetingScreen(id: String, onBack: () -> Unit, onModels: () -> Unit, initialTab: Int = 0) {
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
    var tab by remember { mutableIntStateOf(initialTab) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var sheetEntity by remember { mutableStateOf<Entity?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var editingWord by remember { mutableStateOf<Pair<Int, WordSpan>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var audioNext by remember { mutableStateOf(false) }
    var askAudio by remember { mutableStateOf(false) }
    // After the PDF was sent in a chat app, offer to send the audio as the second step.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (audioNext) { audioNext = false; askAudio = true } }
    var query by remember { mutableStateOf("") }
    var hit by remember { mutableIntStateOf(0) }
    val matches = remember(m.utterances, query) {
        if (query.length < 2) emptyList() else m.utterances.indices.filter { m.utterances[it].text.contains(query, ignoreCase = true) }
    }

    // Meetings transcribed before names were picked up from the conversation: do it once now.
    LaunchedEffect(m.id, m.status, m.namesChecked) {
        if (m.status == MeetingStatus.READY && !m.namesChecked && m.utterances.isNotEmpty()) {
            val known = app.voices.voices.value.map { it.name }
            scope.launch(Dispatchers.Default) { app.repo.update(id) { mm -> reExtract(com.bigoke.metings.engine.SpeakerNamer.apply(mm, known).first) } }
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val learnedMsg = tr("Learned: ", "Geleer: ")
    val fixAllLabel = tr("Fix everywhere", "Herstel oral")

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
                        if (m.status == MeetingStatus.READY) IconButton(onClick = { sharing = true }) {
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
                            DropdownMenuItem(text = { Text(tr("Share / export…", "Deel / voer uit…")) }, onClick = { menu = false; sharing = true })
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
        snackbarHost = { SnackbarHost(snackbar) },
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
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                FlowRow2 {
                    MetaChip(formatDate(m.createdAt))
                    MetaChip(formatDuration(m.durationMs))
                    if (m.speakers.isNotEmpty()) MetaChip("${m.speakers.size} " + tr("people", "mense"))
                    if (m.folder.isNotBlank()) MetaChip(m.folder)
                    Templates.get(m.template).takeIf { it.id != "general" }?.let { MetaChip(if (af) it.af else it.en) }
                }
                if (m.utterances.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    val tlMarkers = remember(matches, m.bookmarksMs, m.entities) {
                        matches.map { TimelineMarker(m.utterances[it].startMs, Color(0xFFF4B740)) } +
                            m.bookmarksMs.map { TimelineMarker(it, Color(0xFF3DD9C1)) } +
                            m.entities.filter { it.type == EntityType.TASK }.mapNotNull { e -> m.utterances.getOrNull(e.utterance)?.let { TimelineMarker(it.startMs, Color(0xFFE5484D)) } }
                    }
                    SpeakerTimeline(
                        m, if (player.duration > 0) player.duration else m.durationMs, player.position, tlMarkers,
                        onSeek = { ms -> player.seekTo(ms, play = player.playing) },
                    )
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
                val tabs = listOf(tr("Summary", "Opsomming"), tr("Transcript", "Transkripsie"), tr("Ask", "Vra"), tr("Details", "Besonderhede"), tr("People", "Mense"))
                PrimaryScrollableTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background, edgePadding = 12.dp) {
                    tabs.forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = {
                            if (i == 2) Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(t, maxLines = 1)
                            } else Text(t, maxLines = 1)
                        })
                    }
                }
                when (tab) {
                    0 -> SummaryTab(m, onModels, onToggle = { idx ->
                        save { mm -> mm.copy(summary = mm.summary?.let { sm -> sm.copy(actions = sm.actions.mapIndexed { i, a -> if (i == idx) a.copy(done = !a.done) else a }) }) }
                    }, onRewrite = { ProcessingService.enqueue(ctx, id, summaryOnly = true) })
                    1 -> TranscriptTab(
                        m, player, settings.markUnsure, query.takeIf { searching && it.length >= 2 },
                        matches.getOrNull(hit), onEntity = { sheetEntity = it }, onEdit = { editing = it },
                        onEditWord = { i, w -> editingWord = i to w },
                    )
                    2 -> AskTab(m, player) { q ->
                        save { mm -> mm.copy(qa = mm.qa + com.bigoke.metings.data.QaItem(q)) }
                        ProcessingService.ask(ctx, id, q)
                    }
                    3 -> DetailsTab(m, af, onEntity = { sheetEntity = it })
                    4 -> PeopleTab(m, onRename = { sid, name, rememberVoice ->
                        val sp = m.speaker(sid)
                        if (rememberVoice) scope.launch(Dispatchers.Default) { app.voices.remember(name, sp.voiceprint) }
                        save { mm -> reExtract(com.bigoke.metings.engine.SpeakerNamer.rename(mm, sid, name)) }
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
                        OutlinedTextField(text, { text = it }, minLines = 4, modifier = Modifier.fillMaxWidth())
                        Text(
                            tr("Notule learns from your corrections and fixes the same words in future transcripts.",
                                "Notule leer uit jou regstellings en herstel dieselfde woorde in toekomstige transkripsies."),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        editing = null
                        val newText = text
                        // Learn from the edit: word changes are remembered and fixed in future transcripts.
                        val learned = com.bigoke.metings.data.CorrectionBook.diff(u.text, newText)
                        save { mm ->
                            reExtract(mm.copy(utterances = mm.utterances.mapIndexed { i, x ->
                                if (i == idx) x.copy(text = TextCleaner.clean(newText, x.lang.ifEmpty { mm.language }, false), speaker = spk, edited = true, unsure = emptyList()) else x
                            }))
                        }
                        if (learned.isNotEmpty()) {
                            app.corrections.add(learned)
                            val others = m.utterances.withIndex().count { (i, x) ->
                                i != idx && learned.any { (f, _) -> x.text.contains(f, ignoreCase = true) }
                            }
                            scope.launch {
                                val msg = learnedMsg + learned.joinToString(", ") { (f, t) -> "$f → $t" }
                                val res = snackbar.showSnackbar(
                                    msg, actionLabel = if (others > 0) fixAllLabel + " ($others)" else null,
                                    withDismissAction = true, duration = SnackbarDuration.Long,
                                )
                                if (res == SnackbarResult.ActionPerformed) save { mm ->
                                    val map = learned.toMap()
                                    reExtract(mm.copy(utterances = mm.utterances.map { x ->
                                        x.copy(text = TextCleaner.clean(x.text, x.lang.ifEmpty { mm.language }, false, emptyList(), map))
                                    }))
                                }
                            }
                        }
                    }) { Text(tr("Save", "Stoor")) }
                },
                dismissButton = { TextButton(onClick = { editing = null }) { Text(tr("Cancel", "Kanselleer")) } },
            )
        }
    }
    editingWord?.let { (idx, w) ->
        val u = m.utterances.getOrNull(idx)
        if (u != null && w.end <= u.text.length) {
            val raw = u.text.substring(w.start, w.end)
            val lead = raw.takeWhile { !it.isLetterOrDigit() }
            val trail = raw.takeLastWhile { !it.isLetterOrDigit() }
            val core = raw.removePrefix(lead).removeSuffix(trail).ifEmpty { raw }
            var text by remember(idx, w) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(core, androidx.compose.ui.text.TextRange(0, core.length))) }
            val coreRe = remember(core) { Regex("""(?<![\p{L}\d])""" + Regex.escape(core) + """(?![\p{L}\d])""", RegexOption.IGNORE_CASE) }
            val elsewhere = remember(core, m.utterances) { m.utterances.withIndex().sumOf { (i, x) -> coreRe.findAll(x.text).count() - if (i == idx) 1 else 0 } }
            var everywhere by remember(idx, w) { mutableStateOf(true) }
            val focus = remember { androidx.compose.ui.focus.FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            AlertDialog(
                onDismissRequest = { editingWord = null },
                title = { Text(tr("Correct word", "Korrigeer woord")) },
                text = {
                    Column {
                        TextButton(onClick = { player.seekTo(w.ms - 400, play = true) }, contentPadding = PaddingValues(0.dp)) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                            Text(tr("Listen", "Luister") + " · " + formatDuration(w.ms))
                        }
                        OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus))
                        if (elsewhere > 0) Row(Modifier.clickable { everywhere = !everywhere }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = everywhere, onCheckedChange = { everywhere = it })
                            Text(tr("Also fix the other $elsewhere in this meeting", "Herstel ook die ander $elsewhere in hierdie vergadering"), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            tr("Notule remembers this and spells it correctly in future transcripts.",
                                "Notule onthou dit en spel dit reg in toekomstige transkripsies."),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { editingWord = null; editing = idx }, contentPadding = PaddingValues(0.dp)) {
                            Text(tr("Edit the whole paragraph or speaker…", "Wysig die hele paragraaf of spreker…"))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        editingWord = null
                        val new = text.text.trim()
                        if (new.isEmpty() || new == core) return@TextButton
                        val replacedHere = u.text.substring(0, w.start) + lead + new + trail + u.text.substring(w.end)
                        val learned = com.bigoke.metings.data.CorrectionBook.diff(u.text, replacedHere).ifEmpty { listOf(core to new) }
                        app.corrections.add(learned)
                        save { mm ->
                            reExtract(mm.copy(utterances = mm.utterances.mapIndexed { i, x ->
                                when {
                                    i == idx -> x.copy(text = if (everywhere) coreRe.replace(replacedHere) { new } else replacedHere, edited = true, unsure = x.unsure - core.lowercase())
                                    everywhere && coreRe.containsMatchIn(x.text) -> x.copy(text = coreRe.replace(x.text) { new }, edited = true, unsure = x.unsure - core.lowercase())
                                    else -> x
                                }
                            }))
                        }
                        scope.launch { snackbar.showSnackbar(learnedMsg + "$core → $new", withDismissAction = true) }
                    }) { Text(tr("Save", "Stoor")) }
                },
                dismissButton = { TextButton(onClick = { editingWord = null }) { Text(tr("Cancel", "Kanselleer")) } },
            )
        }
    }
    sheetEntity?.let { e -> EntitySheet(m, e, af, player, onDismiss = { sheetEntity = null }) }
    val savedMsg = tr("Saved in ", "Gestoor in ")
    val noAudioMsg = tr("This meeting has no audio file.", "Hierdie vergadering het geen klanklêer nie.")
    if (sharing) ShareSheet(
        onDismiss = { sharing = false },
        onEmailBoth = { sharing = false; Exporter.emailBoth(ctx, m) },
        onOneByOne = { sharing = false; audioNext = true; Exporter.sharePdf(ctx, m) },
        onPdf = { sharing = false; Exporter.sharePdf(ctx, m) },
        onAudio = { sharing = false; if (!Exporter.shareAudio(ctx, m)) scope.launch { snackbar.showSnackbar(noAudioMsg) } },
        onWord = { sharing = false; Exporter.shareWord(ctx, m) },
        onText = { sharing = false; Exporter.shareText(ctx, m) },
        onSave = {
            sharing = false
            scope.launch {
                val where = runCatching { kotlinx.coroutines.withContext(Dispatchers.IO) { Exporter.saveToDownloads(ctx, m) } }.getOrNull()
                if (where != null) snackbar.showSnackbar(savedMsg + where, duration = SnackbarDuration.Long)
            }
        },
    )
    if (askAudio) AlertDialog(
        onDismissRequest = { askAudio = false },
        title = { Text(tr("Send the audio too?", "Stuur ook die klank?")) },
        text = { Text(tr("The minutes were shared. Tap Send to share the audio recording to the same chat.",
            "Die notule is gedeel. Tik Stuur om die klankopname na dieselfde klets te stuur.")) },
        confirmButton = { TextButton(onClick = { askAudio = false; Exporter.shareAudio(ctx, m) }) { Text(tr("Send audio", "Stuur klank")) } },
        dismissButton = { TextButton(onClick = { askAudio = false }) { Text(tr("Not now", "Nie nou nie")) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(
    onDismiss: () -> Unit, onEmailBoth: () -> Unit, onOneByOne: () -> Unit, onPdf: () -> Unit,
    onAudio: () -> Unit, onWord: () -> Unit, onText: () -> Unit, onSave: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(tr("Share the minutes", "Deel die notule"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            ShareRow(Icons.Outlined.Email, tr("E-mail minutes + audio", "E-pos notule + klank"), tr("Both files as attachments in one e-mail", "Albei lêers as aanhegsels in een e-pos"), onEmailBoth)
            ShareRow(Icons.AutoMirrored.Outlined.Send, tr("WhatsApp / chat: minutes, then audio", "WhatsApp / klets: notule, dan klank"),
                tr("Sends the PDF first, then offers the audio", "Stuur eers die PDF, dan bied dit die klank aan"), onOneByOne)
            ShareRow(Icons.Outlined.Download, tr("Save minutes + audio to phone", "Stoor notule + klank op foon"), tr("In Download/Notule", "In Download/Notule"), onSave)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            ShareRow(Icons.Outlined.PictureAsPdf, tr("Minutes (PDF) only", "Slegs notule (PDF)"), null, onPdf)
            ShareRow(Icons.Outlined.Audiotrack, tr("Audio only", "Slegs klank"), null, onAudio)
            ShareRow(Icons.Outlined.Description, tr("Word document", "Word-dokument"), null, onWord)
            ShareRow(Icons.Outlined.Notes, tr("Text", "Teks"), null, onText)
        }
    }
}

@Composable
private fun ShareRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow2(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun ProcessingCard(m: Meeting, stage: String?, overall: Float?, coreReady: Boolean, onModels: () -> Unit, onStop: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(20.dp)) {
            if (!coreReady && m.status == MeetingStatus.QUEUED) {
                Text(tr("Waiting for the offline models", "Wag vir die vanlyn-modelle"), style = MaterialTheme.typography.titleSmall)
                Text(tr("Your recording is saved. Download the models and it will be processed automatically.",
                    "Jou opname is gestoor. Laai die modelle af en dit word outomaties verwerk."), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onModels) { Text(tr("Download models", "Laai modelle af")) }
            } else {
                Text(if (stage == null) tr("Waiting to start…", "Wag om te begin…") else tr("Creating your minutes", "Jou notule word geskep"),
                    style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                ProcessingStages(stage, overall)
                Spacer(Modifier.height(8.dp))
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlanceTile(formatDuration(m.durationMs), tr("duration", "duur"), Modifier.weight(1f))
                GlanceTile("${m.speakers.size}", tr("people", "mense"), Modifier.weight(1f))
                GlanceTile("${s.decisions.size}", tr("decisions", "besluite"), Modifier.weight(1f))
                GlanceTile("${s.actions.count { !it.done }}", tr("open tasks", "oop take"), Modifier.weight(1f))
            }
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

/** A word in an utterance's text with the moment it was spoken. */
data class WordSpan(val start: Int, val end: Int, val ms: Long)

private val wordRe = Regex("""\S+""")

/** Word positions and times. Uses the speech model's word timings, spread over the (possibly edited) text. */
fun wordSpans(u: com.bigoke.metings.data.Utterance): List<WordSpan> {
    val found = wordRe.findAll(u.text).toList()
    if (found.isEmpty()) return emptyList()
    val n = found.size
    val len = u.text.length.coerceAtLeast(1)
    val dur = (u.endMs - u.startMs).coerceAtLeast(1)
    return found.mapIndexed { i, mr ->
        val ms = if (u.wordMs.isNotEmpty()) {
            val raw = (i.toLong() * u.wordMs.size / n).toInt().coerceIn(0, u.wordMs.size - 1)
            u.startMs + u.wordMs[raw]
        } else u.startMs + dur * mr.range.first / len
        WordSpan(mr.range.first, mr.range.last + 1, ms)
    }
}

@Composable
private fun TranscriptTab(
    m: Meeting, player: AudioPlayer, markUnsure: Boolean, query: String?, focus: Int?,
    onEntity: (Entity) -> Unit, onEdit: (Int) -> Unit, onEditWord: (Int, WordSpan) -> Unit,
) {
    val byUtterance = remember(m.entities) { m.entities.filter { it.type != EntityType.TASK }.groupBy { it.utterance } }
    val taskUtterances = remember(m.entities) { m.entities.filter { it.type == EntityType.TASK }.map { it.utterance }.toSet() }
    val spans = remember(m.utterances) { m.utterances.map { wordSpans(it) } }
    val listState = rememberLazyListState()
    val headerItems = if (m.bookmarksMs.isNotEmpty()) 1 else 0
    LaunchedEffect(focus) { if (focus != null) listState.animateScrollToItem(focus + headerItems) }
    val started = player.playing || player.position > 0
    val current = if (!started) -1 else m.utterances.indexOfLast { it.startMs <= player.position + 120 }
    val activeWord = if (current < 0) -1 else spans[current].indexOfLast { it.ms <= player.position + 90 }
    // Follow the audio: keep the paragraph being played on screen.
    LaunchedEffect(current) {
        if (player.playing && current >= 0 && !listState.isScrollInProgress) {
            val item = current + headerItems
            val visible = listState.layoutInfo.visibleItemsInfo
            val shownFully = visible.any { it.index == item && it.offset >= 0 && it.offset + it.size <= listState.layoutInfo.viewportEndOffset }
            if (!shownFully) listState.animateScrollToItem(item, -120)
        }
    }
    val hitBg = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.30f)
    val wordBg = com.bigoke.metings.ui.theme.Teal
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    LazyColumn(state = listState, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp)) {
        item {
            Text(
                tr("Tap a word to play from there · hold a word to correct it", "Tik 'n woord om daarvandaan te speel · hou 'n woord om dit reg te maak"),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
        }
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
                            active -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
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
                val ents = byUtterance[idx].orEmpty()
                val ws = spans[idx]
                val activeSpan = if (active) ws.getOrNull(activeWord) else null
                var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                Text(
                    highlighted(u.text, ents, if (markUnsure) u.unsure else emptyList(), query, hitBg, activeSpan, wordBg, ws, if (active) activeWord else -1),
                    style = MaterialTheme.typography.bodyLarge,
                    onTextLayout = { layout = it },
                    modifier = Modifier.padding(start = 34.dp).pointerInput(u.text, ents) {
                        fun wordAt(pos: androidx.compose.ui.geometry.Offset): Pair<Int, WordSpan?> {
                            val off = layout?.getOffsetForPosition(pos) ?: return -1 to null
                            return off to (ws.firstOrNull { off >= it.start && off < it.end } ?: ws.lastOrNull { it.start <= off })
                        }
                        detectTapGestures(
                            onTap = { pos ->
                                val (off, w) = wordAt(pos)
                                val e = ents.firstOrNull { off >= it.start && off < it.end }
                                if (e != null) onEntity(e) else player.seekTo((w?.ms ?: u.startMs) - 150, play = true)
                            },
                            onLongPress = { pos ->
                                val (_, w) = wordAt(pos)
                                if (w != null) { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onEditWord(idx, w) }
                            },
                        )
                    },
                )
            }
        }
    }
}

private fun highlighted(
    text: String, entities: List<Entity>, unsure: List<String>, query: String?, hitBg: Color,
    activeSpan: WordSpan?, wordBg: Color, spans: List<WordSpan>, activeIndex: Int,
): AnnotatedString = buildAnnotatedString {
    append(text)
    // Words already played in the current paragraph are slightly faded so the eye follows the audio.
    if (activeIndex > 0) {
        val until = spans[activeIndex].start
        addStyle(SpanStyle(color = Color(0xFF8A94A6)), 0, until)
    }
    for (e in entities) {
        if (e.start < 0 || e.end > text.length || e.start >= e.end) continue
        val c = entityColor(e.type)
        addStyle(SpanStyle(color = c, fontWeight = FontWeight.SemiBold, background = c.copy(alpha = 0.10f)), e.start, e.end)
    }
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
    if (activeSpan != null && activeSpan.end <= text.length) {
        addStyle(SpanStyle(background = wordBg, color = Color.White, fontWeight = FontWeight.SemiBold), activeSpan.start, activeSpan.end)
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
    var assignName by remember { mutableStateOf<String?>(null) }
    val peopleOthers = remember(m.addressed, m.entities, m.speakers) {
        val speaking = m.speakers.map { it.name.split(' ')[0].lowercase() }.toSet()
        (m.addressed + m.entities.filter { it.type == EntityType.PERSON }.map { it.text })
            .map { it.trim() }.filter { it.length > 1 && it.split(' ')[0].lowercase() !in speaking }
            .distinctBy { it.lowercase() }.take(12)
    }
    val talk = remember(m.utterances) {
        m.utterances.groupBy { it.speaker }.mapValues { (_, us) -> us.sumOf { (it.endMs - it.startMs).coerceAtLeast(0) } to us.sumOf { it.text.split(' ').size } }
    }
    val totalTalk = talk.values.sumOf { it.first }.coerceAtLeast(1)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (m.speakers.size > 1) item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TalkRing(
                    m.speakers.map { speakerColor(it.colorIndex) to (talk[it.id]?.first ?: 0L).toFloat() },
                    "${m.speakers.size}", tr("speakers", "sprekers"),
                )
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    m.speakers.sortedByDescending { talk[it.id]?.first ?: 0L }.forEach { sp ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(speakerColor(sp.colorIndex), CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(sp.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                            Text("  ${((talk[sp.id]?.first ?: 0L) * 100 / totalTalk)}%", style = MaterialTheme.typography.labelLarge.merge(MonoNumbers))
                        }
                    }
                }
            }
        }
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
                                if (s.nameSource == "said") Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                                    Text(" " + tr("Named from the conversation", "Naam uit die gesprek gehaal"),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                }
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
                            TextButton(onClick = { renameId = s.id }) { Text(if (s.recognised || s.nameSource == "said") tr("Not them? Rename", "Nie hulle nie? Hernoem") else tr("Rename", "Hernoem")) }
                            if (m.speakers.size > 1) OutlinedButton(onClick = { mergeId = s.id }) { Text(tr("Merge", "Voeg saam")) }
                        }
                    }
                }
            }
        }
        if (peopleOthers.isNotEmpty()) item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("Also in the conversation", "Ook in die gesprek"), style = MaterialTheme.typography.titleSmall)
                    Text(
                        tr("Spoken to or about, but not heard as a separate voice. Tap a name to give it to a speaker.",
                            "Mee gepraat of oor gepraat, maar nie as aparte stem gehoor nie. Tik 'n naam om dit aan 'n spreker te gee."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow2 {
                        peopleOthers.forEach { n ->
                            AssistChip(onClick = { assignName = n }, label = { Text(n) },
                                leadingIcon = { SpeakerAvatar(n, 7, 20.dp) })
                        }
                    }
                }
            }
        }
    }
    assignName?.let { n ->
        AlertDialog(
            onDismissRequest = { assignName = null },
            title = { Text(tr("Who is $n?", "Wie is $n?")) },
            text = {
                Column {
                    m.speakers.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().clickable { assignName = null; onRename(s.id, n, s.voiceprint.isNotEmpty()) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SpeakerAvatar(s.name, s.colorIndex, 28.dp); Spacer(Modifier.width(10.dp)); Text(s.name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { assignName = null }) { Text(tr("Not a speaker", "Nie 'n spreker nie")) } },
        )
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
                    if (peopleOthers.isNotEmpty()) {
                        Text(tr("Named in this meeting", "Genoem in hierdie vergadering"), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        FlowRow2 { peopleOthers.take(8).forEach { n -> AssistChip(onClick = { text = n }, label = { Text(n) }) } }
                    }
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


@Composable
private fun MetaChip(text: String) {
    Box(
        Modifier.padding(vertical = 2.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
}

@Composable
private fun GlanceTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium.merge(MonoNumbers), maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------- Ask Notule

private val timeRef = Regex("""\[(\d{1,3}):(\d{2})\]""")

private fun answerText(text: String, link: Color, onSeek: (Long) -> Unit): AnnotatedString = buildAnnotatedString {
    var pos = 0
    for (mt in timeRef.findAll(text)) {
        append(text.substring(pos, mt.range.first))
        val ms = (mt.groupValues[1].toLong() * 60 + mt.groupValues[2].toLong()) * 1000
        withLink(LinkAnnotation.Clickable("t$ms", TextLinkStyles(SpanStyle(color = link, fontWeight = FontWeight.SemiBold, background = link.copy(alpha = 0.12f)))) { onSeek(ms) }) {
            append("▶ " + mt.groupValues[1] + ":" + mt.groupValues[2])
        }
        pos = mt.range.last + 1
    }
    append(text.substring(pos))
}

@Composable
private fun AskTab(m: Meeting, player: AudioPlayer, onAsk: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val busy = m.qa.any { it.pending }
    LaunchedEffect(m.qa.size, busy) { if (m.qa.isNotEmpty()) listState.animateScrollToItem(m.qa.size) }
    val suggestions = listOf(
        tr("What was decided?", "Wat is besluit?"),
        tr("Who has to do what, and by when?", "Wie moet wat doen, en teen wanneer?"),
        tr("Which amounts and dates were mentioned?", "Watter bedrae en datums is genoem?"),
        tr("What questions are still open?", "Watter vrae is nog oop?"),
    )
    val link = MaterialTheme.colorScheme.secondary
    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        onAsk(t); q = ""
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(com.bigoke.metings.ui.theme.AiGradient).padding(20.dp),
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(tr("Ask Notule", "Vra Notule"), style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(
                        tr("Ask anything about this meeting. Answers come from the transcript, worked out on this phone. Tap a time to hear that moment.",
                            "Vra enigiets oor hierdie vergadering. Antwoorde kom uit die transkripsie en word op hierdie foon uitgewerk. Tik 'n tyd om daardie oomblik te hoor."),
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(12.dp))
                    FlowRow2 {
                        suggestions.forEach { sug ->
                            Box(
                                Modifier.padding(vertical = 3.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f))
                                    .clickable(enabled = !busy) { send(sug) }.padding(horizontal = 12.dp, vertical = 7.dp),
                            ) { Text(sug, color = Color.White, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
            m.qa.forEach { qa ->
                item {
                    Column(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Text(
                                qa.question, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 48.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Box(Modifier.size(30.dp).clip(CircleShape).background(com.bigoke.metings.ui.theme.AiGradient), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.padding(end = 24.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                if (qa.pending) Row(verticalAlignment = Alignment.CenterVertically) {
                                    ThinkingDots()
                                    Spacer(Modifier.width(10.dp))
                                    Text(tr("Reading the meeting…", "Lees die vergadering…"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else Text(answerText(qa.answer, link) { ms -> player.seekTo(ms) }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        Surface(tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, modifier = Modifier.weight(1f), maxLines = 3,
                    placeholder = { Text(tr("Ask about this meeting…", "Vra oor hierdie vergadering…")) },
                    shape = RoundedCornerShape(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(if (q.isBlank() || busy) SolidColor(MaterialTheme.colorScheme.surfaceVariant) else com.bigoke.metings.ui.theme.AiGradient)
                        .clickable(enabled = q.isNotBlank() && !busy) { send(q) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Outlined.Send, tr("Ask", "Vra"), tint = if (q.isBlank() || busy) MaterialTheme.colorScheme.onSurfaceVariant else Color.White) }
            }
        }
    }
}
