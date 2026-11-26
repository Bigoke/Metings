package com.bigoke.metings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bigoke.metings.MetingsApp
import com.bigoke.metings.data.ActionItem
import com.bigoke.metings.data.Meeting
import com.bigoke.metings.util.Exporter
import com.bigoke.metings.util.tr

/** All action items from all meetings in one place. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val app = MetingsApp.instance
    val ctx = LocalContext.current
    val meetings by app.repo.meetings.collectAsStateWithLifecycle()
    var showDone by remember { mutableStateOf(false) }
    var owner by remember { mutableStateOf<String?>(null) }

    data class Row3(val meeting: Meeting, val index: Int, val item: ActionItem)
    val all = remember(meetings) {
        meetings.flatMap { m -> m.summary?.actions.orEmpty().mapIndexed { i, a -> Row3(m, i, a) } }
    }
    val owners = remember(all) { all.map { it.item.owner }.filter { it.isNotBlank() }.distinct().sorted() }
    val shown = all.filter { (showDone || !it.item.done) && (owner == null || it.item.owner == owner) }

    fun toggle(r: Row3) {
        app.repo.update(r.meeting.id) { mm ->
            mm.copy(summary = mm.summary?.let { s -> s.copy(actions = s.actions.mapIndexed { i, a -> if (i == r.index) a.copy(done = !a.done) else a }) })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Tasks", "Take")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back", "Terug")) } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !showDone, onClick = { showDone = false }, label = { Text(tr("Open", "Oop")) })
                    FilterChip(selected = showDone, onClick = { showDone = true }, label = { Text(tr("All", "Alles")) })
                }
                if (owners.isNotEmpty()) FlowRow2 {
                    owners.forEach { o -> FilterChip(selected = owner == o, onClick = { owner = if (owner == o) null else o }, label = { Text(o) }) }
                }
            }
            if (shown.isEmpty()) item {
                Text(tr("No open tasks. Nice work!", "Geen oop take nie. Mooi so!"), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp))
            }
            shown.groupBy { it.meeting }.forEach { (m, rows) ->
                item(key = m.id) {
                    SummaryCard(m.title) {
                        Text(formatDate(m.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onOpen(m.id) })
                        rows.forEach { r ->
                            Row(Modifier.fillMaxWidth().clickable { toggle(r) }.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                                Icon(
                                    if (r.item.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                                    tint = if (r.item.done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.item.text, textDecoration = if (r.item.done) TextDecoration.LineThrough else null,
                                        style = MaterialTheme.typography.bodyMedium)
                                    val meta = listOf(r.item.owner, r.item.due).filter { it.isNotBlank() }.joinToString(" · ")
                                    if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                }
                                if (!r.item.done && r.item.owner.isNotBlank()) IconButton(onClick = { Exporter.shareTasks(ctx, m, r.item.owner) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.AutoMirrored.Outlined.Send, tr("Send", "Stuur"), Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
