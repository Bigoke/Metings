package com.bigoke.metings.ui

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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bigoke.metings.MetingsApp
import com.bigoke.metings.data.Entity
import com.bigoke.metings.data.EntityType
import com.bigoke.metings.data.Meeting
import com.bigoke.metings.data.MeetingStatus
import com.bigoke.metings.engine.EntityExtractor
import com.bigoke.metings.engine.TextCleaner
import com.bigoke.metings.service.ProcessingService
import com.bigoke.metings.ui.theme.MonoNumbers
import com.bigoke.metings.ui.theme.entityColor
import com.bigoke.metings.ui.theme.speakerColor
import com.bigoke.metings.util.EntityActions
import com.bigoke.metings.util.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

private fun reExtract(m: Meeting): Meeting =
    m.copy(entities = EntityExtractor(m.createdAt).extract(m.utterances, m.speakers.associate { it.id to it.name }))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingScreen(id: String, onBack: () -> Unit, onModels: () -> Unit) {
    val app = MetingsApp.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val meetings by app.repo.meetings.collectAsStateWithLifecycle()
    val progressMap by app.pipeline.progress.collectAsStateWithLifecycle()
    val m = meetings.firstOrNull { it.id == id }
    if (m == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val af = m.language == "af"
    val player = rememberAudioPlayer(app.repo.audio(m))
    var tab by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var sheetEntity by remember { mutableStateOf<Entity?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }

    fun save(change: (Meeting) -> Meeting) {
        scope.launch(Dispatchers.Default) { app.repo.update(id, change) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(m.title, maxLines = 1, modifier = Modifier.clickable { renaming = true })
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (m.status == MeetingStatus.READY) IconButton(onClick = { Exporter.share(ctx, m, includePdf = true, includeAudio = true) }) {
                        Icon(Icons.Outlined.Share, "Share")
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                        if (m.status == MeetingStatus.READY) {
                            DropdownMenuItem(text = { Text("Share PDF + audio") }, onClick = { menu = false; Exporter.share(ctx, m, true, true) })
                            DropdownMenuItem(text = { Text("Share PDF only") }, onClick = { menu = false; Exporter.share(ctx, m, true, false) })
                            DropdownMenuItem(text = { Text("Share as Word document") }, onClick = { menu = false; Exporter.shareWord(ctx, m) })
                            DropdownMenuItem(text = { Text("Share as text") }, onClick = { menu = false; Exporter.shareText(ctx, m) })
                            DropdownMenuItem(text = { Text("Share audio only") }, onClick = { menu = false; Exporter.share(ctx, m, false, true) })
                            DropdownMenuItem(text = { Text("Rewrite summary") }, onClick = { menu = false; ProcessingService.enqueue(ctx, id, summaryOnly = true) })
                        }
                        if (app.repo.audio(m).name.endsWith(".wav") && m.status != MeetingStatus.PROCESSING) {
                            DropdownMenuItem(text = { Text("Process again") }, onClick = {
                                menu = false
                                app.repo.update(id) { it.copy(status = MeetingStatus.QUEUED) }
                                ProcessingService.enqueue(ctx, id)
                            })
                        }
                        DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirmDelete = true })
                    }
                },
            )
        },
        bottomBar = { PlayerBar(player, m) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDate(m.createdAt) + " · " + formatDuration(m.durationMs),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    m.speakers.take(6).forEach { SpeakerAvatar(it.name, it.colorIndex, 24.dp) }
                }
            }
            when (m.status) {
                MeetingStatus.QUEUED, MeetingStatus.PROCESSING, MeetingStatus.RECORDING ->
                    ProcessingCard(m, progressMap[id]?.stage, progressMap[id]?.overall, app.models.coreReady, onModels) {
                        ProcessingService.cancel(ctx)
                    }
                MeetingStatus.FAILED -> FailedCard(m.error) {
                    app.repo.update(id) { it.copy(status = MeetingStatus.QUEUED, error = null) }
                    ProcessingService.enqueue(ctx, id)
                }
                MeetingStatus.READY -> {}
            }
            if (m.utterances.isNotEmpty() || m.status == MeetingStatus.READY) {
                val tabs = if (af) listOf("Opsomming", "Transkripsie", "Besonderhede", "Mense") else listOf("Summary", "Transcript", "Details", "People")
                PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) }) }
                }
                when (tab) {
                    0 -> SummaryTab(m, af, onModels, onToggle = { idx ->
                        save { mm -> mm.copy(summary = mm.summary?.let { sm -> sm.copy(actions = sm.actions.mapIndexed { i, a -> if (i == idx) a.copy(done = !a.done) else a }) }) }
                    }, onRewrite = { ProcessingService.enqueue(ctx, id, summaryOnly = true) })
                    1 -> TranscriptTab(m, player, onEntity = { sheetEntity = it }, onEdit = { editing = it })
                    2 -> DetailsTab(m, af, onEntity = { sheetEntity = it })
                    3 -> PeopleTab(m, af, onRename = { sid, name ->
                        save { mm ->
                            val old = mm.speakerName(sid)
                            val renamed = mm.copy(speakers = mm.speakers.map { if (it.id == sid) it.copy(name = name) else it })
                            val sum = renamed.summary?.let { s ->
                                fun r(x: String) = x.replace(old, name)
                                s.copy(
                                    overview = r(s.overview), decisions = s.decisions.map(::r), keyFigures = s.keyFigures.map(::r),
                                    followUps = s.followUps.map(::r), actions = s.actions.map { a -> a.copy(text = r(a.text), owner = r(a.owner)) },
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
            title = { Text("Rename meeting") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { renaming = false; save { it.copy(title = text.trim().ifEmpty { it.title }) } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete meeting?") },
            text = { Text("The recording, transcript and summary will be removed from this phone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; player.release(); app.repo.delete(id); onBack() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    editing?.let { idx ->
        val u = m.utterances.getOrNull(idx)
        if (u != null) {
            var text by remember(idx) { mutableStateOf(u.text) }
            var spk by remember(idx) { mutableIntStateOf(u.speaker) }
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text(if (af) "Wysig teks" else "Edit text") },
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
                                if (i == idx) x.copy(text = TextCleaner.clean(text, mm.language, false), speaker = spk, edited = true) else x
                            }))
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
            )
        }
    }
    sheetEntity?.let { e -> EntitySheet(m, e, af, player, onDismiss = { sheetEntity = null }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow2(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun ProcessingCard(m: Meeting, stage: String?, overall: Float?, coreReady: Boolean, onModels: () -> Unit, onStop: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            if (!coreReady && m.status == MeetingStatus.QUEUED) {
                Text("Waiting for the offline models", style = MaterialTheme.typography.titleSmall)
                Text("Your recording is saved. Download the models and it will be processed automatically.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onModels) { Text("Download models") }
            } else {
                Text(stage?.let { "$it…" } ?: "Waiting to start…", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                if (overall != null) LinearProgressIndicator(progress = { overall }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Everything runs on your phone. You can leave the app — you'll get a notification when it's ready.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onStop) { Text("Stop") }
                }
            }
        }
    }
}

@Composable
private fun FailedCard(error: String?, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, null)
            Spacer(Modifier.width(10.dp))
            Text(error ?: "Processing failed", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

// ---------------------------------------------------------------------------------------- player

@Composable
private fun PlayerBar(p: AudioPlayer, m: Meeting) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
            if (!p.available) {
                Text("Audio will be available after recording.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                return@Column
            }
            val dur = if (p.duration > 0) p.duration else m.durationMs
            Slider(
                value = if (dur > 0) (p.position.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                onValueChange = { p.seekTo((it * dur).toLong(), play = p.playing) },
                modifier = Modifier.height(28.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(p.position), style = MonoNumbers, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { p.skip(-10_000) }) { Icon(Icons.Outlined.Replay10, "Back 10 s") }
                Box(
                    Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape).clickable { p.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (p.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play", tint = MaterialTheme.colorScheme.onPrimary)
                }
                IconButton(onClick = { p.skip(10_000) }) { Icon(Icons.Outlined.Forward10, "Forward 10 s") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { p.cycleSpeed() }) { Text("${p.speed}×".replace(".0×", "×")) }
                Text(formatDuration(dur), style = MonoNumbers, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------- summary

@Composable
private fun SummaryTab(m: Meeting, af: Boolean, onModels: () -> Unit, onToggle: (Int) -> Unit, onRewrite: () -> Unit) {
    val s = m.summary
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (s == null) {
            item { Text(if (af) "Die opsomming word nog gemaak." else "The summary is still being prepared.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@LazyColumn
        }
        item {
            SummaryCard(if (af) "Oorsig" else "Overview") {
                Text(s.overview.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (s.engine == "gemma") "Written on this phone by Gemma" else "Key sentences (summary AI not installed)",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (s.engine != "gemma" && !MetingsApp.instance.models.isReady(com.bigoke.metings.engine.ModelKind.SUMMARY))
                        TextButton(onClick = onModels) { Text("Get AI") }
                    else TextButton(onClick = onRewrite) { Text(if (af) "Herskryf" else "Rewrite") }
                }
            }
        }
        if (s.decisions.isNotEmpty()) item {
            SummaryCard(if (af) "Besluite" else "Decisions") {
                s.decisions.forEach { Bullet(it) }
            }
        }
        if (s.actions.isNotEmpty()) item {
            SummaryCard(if (af) "Aksiepunte" else "Action items") {
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
                                color = if (a.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            val meta = listOfNotNull(a.owner.takeIf { it.isNotBlank() }, a.due.takeIf { it.isNotBlank() }).joinToString(" · ")
                            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
        if (s.keyFigures.isNotEmpty()) item {
            SummaryCard(if (af) "Sleutelsyfers" else "Key figures") { s.keyFigures.forEach { Bullet(it) } }
        }
        if (s.followUps.isNotEmpty()) item {
            SummaryCard(if (af) "Opvolging" else "Follow-ups") { s.followUps.forEach { Bullet(it) } }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SummaryCard(title: String, content: @Composable () -> Unit) {
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
private fun TranscriptTab(m: Meeting, player: AudioPlayer, onEntity: (Entity) -> Unit, onEdit: (Int) -> Unit) {
    val byUtterance = remember(m.entities) { m.entities.filter { it.type != EntityType.TASK }.groupBy { it.utterance } }
    val taskUtterances = remember(m.entities) { m.entities.filter { it.type == EntityType.TASK }.map { it.utterance }.toSet() }
    val listState = rememberLazyListState()
    val current = m.utterances.indexOfLast { it.startMs <= player.position + 150 }.takeIf { player.playing || player.position > 0 } ?: -1
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
                    .background(if (active) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(12.dp))
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
                        Text("● " + (if (m.language == "af") "taak" else "task"), style = MaterialTheme.typography.labelSmall, color = entityColor(EntityType.TASK))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onEdit(idx) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Edit, "Edit", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    highlighted(u.text, byUtterance[idx].orEmpty(), onEntity),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 34.dp).clickable { player.seekTo(u.startMs) },
                )
            }
        }
    }
}

private fun highlighted(text: String, entities: List<Entity>, onEntity: (Entity) -> Unit): AnnotatedString = buildAnnotatedString {
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
}

// ---------------------------------------------------------------------------------------- details

@Composable
private fun DetailsTab(m: Meeting, af: Boolean, onEntity: (Entity) -> Unit) {
    val groups = remember(m.entities) {
        m.entities.groupBy { it.type }.toSortedMap(compareBy { it.ordinal })
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (groups.isEmpty()) item {
            Text(if (af) "Geen besonderhede gevind nie." else "No details found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        groups.forEach { (type, list) ->
            item(key = type.name) {
                SummaryCard("${entityLabel(type, af)} · ${list.size}") {
                    if (type == EntityType.AMOUNT) {
                        val totals = list.mapNotNull { e ->
                            val parts = e.value.split(' ')
                            if (parts.size == 2) parts[0] to (parts[1].toDoubleOrNull() ?: 0.0) else null
                        }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
                        totals.forEach { (cur, sum) ->
                            Text(
                                (if (af) "Totaal genoem: " else "Total mentioned: ") + money(cur, sum),
                                style = MaterialTheme.typography.titleSmall, color = entityColor(EntityType.AMOUNT),
                            )
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
                                        Spacer(Modifier.width(6.dp))
                                    } else {
                                        Icon(entityIcon(type), null, Modifier.size(15.dp), tint = c)
                                        Spacer(Modifier.width(6.dp))
                                    }
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

private fun money(code: String, v: Double): String {
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
private fun PeopleTab(m: Meeting, af: Boolean, onRename: (Int, String) -> Unit, onMerge: (Int, Int) -> Unit) {
    var renameId by remember { mutableStateOf<Int?>(null) }
    var mergeId by remember { mutableStateOf<Int?>(null) }
    val talk = remember(m.utterances) {
        m.utterances.groupBy { it.speaker }.mapValues { (_, us) -> us.sumOf { (it.endMs - it.startMs).coerceAtLeast(0) } to us.sumOf { it.text.split(' ').size } }
    }
    val totalTalk = talk.values.sumOf { it.first }.coerceAtLeast(1)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                if (af) "Stemme word outomaties herken. Tik op 'n persoon om die naam te verander, of voeg twee saam as dit dieselfde persoon is."
                else "Voices are recognised automatically. Tap a person to set their name, or merge two that are the same person.",
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
                                    "${formatDuration(ms)} · ${(ms * 100 / totalTalk)}% · $words " + (if (af) "woorde" else "words"),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(progress = { ms.toFloat() / totalTalk }, color = speakerColor(s.colorIndex), modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { renameId = s.id }) { Text(if (af) "Hernoem" else "Rename") }
                            if (m.speakers.size > 1) OutlinedButton(onClick = { mergeId = s.id }) { Text(if (af) "Voeg saam" else "Merge") }
                        }
                    }
                }
            }
        }
    }
    renameId?.let { sid ->
        var text by remember(sid) { mutableStateOf(m.speakerName(sid).let { if (it.startsWith("Person ")) "" else it }) }
        AlertDialog(
            onDismissRequest = { renameId = null },
            title = { Text(if (af) "Wie is ${m.speakerName(sid)}?" else "Who is ${m.speakerName(sid)}?") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { renameId = null; if (text.isNotBlank()) onRename(sid, text.trim()) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameId = null }) { Text("Cancel") } },
        )
    }
    mergeId?.let { from ->
        AlertDialog(
            onDismissRequest = { mergeId = null },
            title = { Text(if (af) "Voeg ${m.speakerName(from)} saam met…" else "Merge ${m.speakerName(from)} into…") },
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
            dismissButton = { TextButton(onClick = { mergeId = null }) { Text("Cancel") } },
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
    val actions = remember(e) { EntityActions.forEntity(e, m.title, sentence, nearbyTime, af) }
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
                    Text(if (af) "Speel hierdie oomblik" else "Play this moment")
                }
                Spacer(Modifier.height(8.dp))
            }
            FlowRow2 {
                actions.forEach { a -> OutlinedButton(onClick = { a.run(ctx) }) { Text(a.label) } }
            }
            if (e.type == EntityType.AMOUNT) {
                Spacer(Modifier.height(12.dp))
                val v = e.value.split(' ').getOrNull(1)?.toDoubleOrNull()
                if (v != null && e.value.startsWith("ZAR")) {
                    Text(
                        (if (af) "Sonder BTW (15%): " else "Excl. VAT (15%): ") + money("ZAR", v / 1.15) +
                            "   ·   " + (if (af) "Met BTW: " else "Incl. VAT: ") + money("ZAR", v * 1.15),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
