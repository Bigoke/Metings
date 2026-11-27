package com.bigoke.metings.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.bigoke.metings.ui.theme.BrandGradient
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bigoke.metings.MetingsApp
import com.bigoke.metings.data.Templates
import com.bigoke.metings.service.RecordingService
import com.bigoke.metings.ui.theme.MonoNumbers
import com.bigoke.metings.ui.theme.RecordRed
import com.bigoke.metings.util.LocalAf
import com.bigoke.metings.util.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(autoStart: Boolean, onBack: () -> Unit, onFinished: (String) -> Unit, onModels: () -> Unit) {
    val ctx = LocalContext.current
    val app = MetingsApp.instance
    val af = LocalAf.current
    val settings by app.prefs.settings.collectAsStateWithLifecycle()
    val rec by RecordingService.state.collectAsStateWithLifecycle()
    val meetings by app.repo.meetings.collectAsStateWithLifecycle()
    val folders = remember(meetings) { meetings.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted() }
    var title by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf(settings.language) }
    var speakers by remember { mutableIntStateOf(0) }
    var folder by remember { mutableStateOf(settings.lastFolder) }
    var template by remember { mutableStateOf(settings.lastTemplate) }
    var wasActive by remember { mutableStateOf(rec.active) }
    var permissionDenied by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    fun begin() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        app.prefs.update { it.copy(lastFolder = folder.trim(), lastTemplate = template) }
        RecordingService.start(ctx, title, lang, speakers, folder.trim(), template)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.RECORD_AUDIO] == true) begin() else permissionDenied = true
    }

    fun startRecording() {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) begin() else launcher.launch(missing.toTypedArray())
    }

    LaunchedEffect(autoStart) { if (autoStart && !rec.active) startRecording() }

    LaunchedEffect(rec.active) {
        if (rec.active) wasActive = true
        if (wasActive && !rec.active && rec.meetingId != null) onFinished(rec.meetingId!!)
    }

    val view = androidx.compose.ui.platform.LocalView.current
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    androidx.compose.runtime.DisposableEffect(rec.active) {
        val window = (view.context as? android.app.Activity)?.window
        val ctl = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        ctl?.isAppearanceLightStatusBars = !rec.active && !darkTheme
        onDispose { ctl?.isAppearanceLightStatusBars = !darkTheme }
    }
    Box(Modifier.fillMaxSize().then(if (rec.active) Modifier.background(BrandGradient) else Modifier)) {
    Scaffold(
        containerColor = if (rec.active) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (rec.active) "" else tr("New meeting", "Nuwe vergadering")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back", "Terug"),
                    tint = if (rec.active) Color.White else MaterialTheme.colorScheme.onSurface) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!rec.active) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(tr("Meeting name (optional)", "Naam van vergadering (opsioneel)")) },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(16.dp))
                SectionTitle(tr("Main language", "Hooftaal"), Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("auto" to tr("Auto", "Outo"), "af" to "Afrikaans", "en" to "English").forEachIndexed { i, (code, label) ->
                        SegmentedButton(selected = lang == code, onClick = { lang = code }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
                    }
                }
                Text(
                    if (lang == "auto") tr("Notule recognises Afrikaans and English by itself, also when people switch.",
                        "Notule herken self Afrikaans en Engels, ook wanneer mense wissel.")
                    else tr("Mixing Afrikaans and English is fine — choose the language spoken most.",
                        "Afrikaans en Engels gemeng is reg — kies die taal wat die meeste gepraat word."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                Spacer(Modifier.height(16.dp))
                SectionTitle(tr("Type of meeting", "Soort vergadering"), Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Templates.all.forEach { t ->
                        FilterChip(selected = template == t.id, onClick = { template = t.id }, label = { Text(if (af) t.af else t.en) })
                    }
                }
                Spacer(Modifier.height(16.dp))
                SectionTitle(tr("Number of people", "Aantal mense"), Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 2, 3, 4, 5, 6).forEach { n ->
                        FilterChip(selected = speakers == n, onClick = { speakers = n }, label = { Text(if (n == 0) tr("Auto", "Outo") else "$n") })
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = folder, onValueChange = { folder = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(tr("Folder / client (optional)", "Vouer / kliënt (opsioneel)")) },
                    leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                )
                if (folders.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        folders.forEach { f -> FilterChip(selected = folder == f, onClick = { folder = if (folder == f) "" else f }, label = { Text(f) }) }
                    }
                }
                if (settings.consentReminder) {
                    Spacer(Modifier.height(16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.padding(14.dp)) {
                            Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                tr("Let everyone know the meeting is being recorded and get their consent (POPIA).",
                                    "Laat almal weet die vergadering word opgeneem en kry hulle toestemming (POPIA)."),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (!app.models.coreReady) {
                    Spacer(Modifier.height(10.dp))
                    Card(onClick = onModels, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text(
                            tr("You can record now. The transcript and summary are made once the offline models are downloaded — tap to set up.",
                                "Jy kan nou opneem. Die transkripsie en opsomming word gemaak sodra die vanlyn-modelle afgelaai is — tik om op te stel."),
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp),
                        )
                    }
                }
                if (permissionDenied) {
                    Spacer(Modifier.height(10.dp))
                    Text(tr("Microphone permission is needed to record.", "Mikrofoontoestemming is nodig om op te neem."), color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(24.dp))
                RecordOrb(onClick = { startRecording() }, size = 96.dp)
                Spacer(Modifier.height(4.dp))
                Text(tr("Tap to start", "Tik om te begin"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Spacer(Modifier.height(24.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(9.dp).background(if (rec.paused) Color.White.copy(alpha = 0.5f) else RecordRed, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(if (rec.paused) tr("PAUSED", "ONDERBREEK") else tr("RECORDING", "OPNAME"), style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 2.sp, color = Color.White)
                }
                Spacer(Modifier.height(18.dp))
                Text(formatDuration(rec.elapsedMs), style = MaterialTheme.typography.displaySmall.merge(MonoNumbers).copy(fontSize = 64.sp, fontWeight = FontWeight.Light), color = Color.White)
                Text(title.ifBlank { tr("Meeting in progress", "Vergadering aan die gang") }, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.75f))
                Spacer(Modifier.height(36.dp))
                Waveform(rec.levels, rec.paused)
                Spacer(Modifier.height(16.dp))
                if (rec.bookmarks.isNotEmpty()) {
                    Text("📌 " + rec.bookmarks.joinToString("   ") { formatDuration(it) },
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                }
                Spacer(Modifier.height(48.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    RoundGlass(Icons.Outlined.BookmarkAdd, tr("Bookmark", "Boekmerk")) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); RecordingService.bookmark(ctx)
                    }
                    RecordOrb(onClick = { confirmStop = true }, size = 84.dp, icon = Icons.Filled.Stop, breathing = !rec.paused)
                    RoundGlass(if (rec.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, tr("Pause", "Onderbreek")) {
                        if (rec.paused) RecordingService.resume(ctx) else RecordingService.pause(ctx)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(66.dp)) {
                    Text(tr("Mark", "Merk"), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    Text(tr("Stop", "Stop"), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    Text(if (rec.paused) tr("Resume", "Hervat") else tr("Pause", "Pouse"), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    tr("Recording continues with the screen off. Everything stays on this phone.",
                        "Opname gaan voort met die skerm af. Alles bly op hierdie foon."),
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    }
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(tr("Stop recording?", "Stop opname?")) },
            text = { Text(tr("The recording (${formatDuration(rec.elapsedMs)}) is saved to this phone.", "Die opname (${formatDuration(rec.elapsedMs)}) word op hierdie foon gestoor.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    RecordingService.stop(ctx)
                }) { Text(tr("Stop and save", "Stop en stoor")) }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(tr("Keep recording", "Hou aan opneem")) } },
        )
    }
}

@Composable
private fun Waveform(levels: List<Float>, paused: Boolean) {
    val color = if (paused) Color.White.copy(alpha = 0.35f) else com.bigoke.metings.ui.theme.Mint
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val n = 60
        val data = levels.takeLast(n)
        val barW = size.width / n
        val mid = size.height / 2
        for (i in 0 until n) {
            val v = data.getOrNull(i - (n - data.size)) ?: 0f
            val h = (v.coerceIn(0.02f, 1f)) * size.height
            drawRoundRect(
                color = color,
                topLeft = Offset(i * barW + barW * 0.2f, mid - h / 2),
                size = Size(barW * 0.6f, h),
                cornerRadius = CornerRadius(barW * 0.3f, barW * 0.3f),
            )
        }
    }
}

@Composable
private fun RoundGlass(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(60.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White) }
}
