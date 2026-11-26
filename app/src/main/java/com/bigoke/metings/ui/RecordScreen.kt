package com.bigoke.metings.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.bigoke.metings.service.RecordingService
import com.bigoke.metings.ui.theme.MonoNumbers
import com.bigoke.metings.ui.theme.RecordRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(onBack: () -> Unit, onFinished: (String) -> Unit, onModels: () -> Unit) {
    val ctx = LocalContext.current
    val app = MetingsApp.instance
    val settings by app.prefs.settings.collectAsStateWithLifecycle()
    val rec by RecordingService.state.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf(settings.language) }
    var speakers by remember { mutableIntStateOf(0) }
    var wasActive by remember { mutableStateOf(rec.active) }
    var permissionDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.RECORD_AUDIO] == true) {
            RecordingService.start(ctx, title, lang, speakers)
        } else permissionDenied = true
    }

    fun startRecording() {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) RecordingService.start(ctx, title, lang, speakers) else launcher.launch(missing.toTypedArray())
    }

    LaunchedEffect(rec.active) {
        if (rec.active) wasActive = true
        if (wasActive && !rec.active && rec.meetingId != null) onFinished(rec.meetingId!!)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (rec.active) "Recording" else "New meeting") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
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
                    label = { Text("Meeting name (optional)") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(18.dp))
                SectionTitle("Main language", Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("af" to "Afrikaans", "en" to "English").forEachIndexed { i, (code, label) ->
                        SegmentedButton(
                            selected = lang == code, onClick = { lang = code },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
                Text(
                    "Mixing Afrikaans and English in one meeting is fine — choose the language spoken most.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                Spacer(Modifier.height(18.dp))
                SectionTitle("Number of people", Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 2, 3, 4, 5, 6).forEach { n ->
                        FilterChip(selected = speakers == n, onClick = { speakers = n }, label = { Text(if (n == 0) "Auto" else "$n") })
                    }
                }
                if (settings.consentReminder) {
                    Spacer(Modifier.height(18.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.padding(14.dp)) {
                            Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Let everyone know the meeting is being recorded and get their consent (POPIA). " +
                                    "Laat almal weet die vergadering word opgeneem.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (!app.models.coreReady) {
                    Spacer(Modifier.height(10.dp))
                    Card(onClick = onModels, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text(
                            "You can record now. The transcript and summary are made once the offline models are downloaded — tap to set up.",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp),
                        )
                    }
                }
                if (permissionDenied) {
                    Spacer(Modifier.height(10.dp))
                    Text("Microphone permission is needed to record.", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(40.dp))
                FilledIconButton(
                    onClick = { startRecording() },
                    modifier = Modifier.size(96.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = RecordRed),
                ) { Icon(Icons.Filled.Mic, "Start", Modifier.size(44.dp)) }
                Spacer(Modifier.height(10.dp))
                Text("Tap to start", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Spacer(Modifier.height(32.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(if (rec.paused) MaterialTheme.colorScheme.outline else RecordRed, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(if (rec.paused) "PAUSED" else "REC", style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(formatDuration(rec.elapsedMs), style = MonoNumbers.copy(fontSize = 56.sp, fontWeight = FontWeight.Light))
                Spacer(Modifier.height(28.dp))
                Waveform(rec.levels, rec.paused)
                Spacer(Modifier.height(12.dp))
                if (rec.bookmarks.isNotEmpty()) {
                    Text(
                        "📌 " + rec.bookmarks.joinToString("  ") { formatDuration(it) },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(40.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    OutlinedIconButton(onClick = { RecordingService.bookmark(ctx) }, modifier = Modifier.size(60.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                        Icon(Icons.Outlined.BookmarkAdd, "Bookmark")
                    }
                    FilledIconButton(
                        onClick = { RecordingService.stop(ctx) },
                        modifier = Modifier.size(88.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = RecordRed),
                    ) { Icon(Icons.Filled.Stop, "Stop", Modifier.size(40.dp)) }
                    OutlinedIconButton(
                        onClick = { if (rec.paused) RecordingService.resume(ctx) else RecordingService.pause(ctx) },
                        modifier = Modifier.size(60.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) { Icon(if (rec.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, "Pause") }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Recording continues with the screen off. Stop when the meeting ends.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Waveform(levels: List<Float>, paused: Boolean) {
    val color = if (paused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.secondary
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
