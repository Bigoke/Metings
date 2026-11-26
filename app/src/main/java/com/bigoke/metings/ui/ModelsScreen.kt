package com.bigoke.metings.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bigoke.metings.MetingsApp
import com.bigoke.metings.data.MeetingStatus
import com.bigoke.metings.engine.DownloadState
import com.bigoke.metings.engine.ModelKind
import com.bigoke.metings.service.ProcessingService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = MetingsApp.instance
    val status by app.models.status.collectAsStateWithLifecycle()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { app.models.downloadAll() }
    val allReady = status.all { it.state == DownloadState.READY }
    val remaining = status.filter { it.state != DownloadState.READY }.sumOf { it.spec.approxBytes }
    val free = app.models.freeSpaceBytes()

    LaunchedEffect(Unit) { app.models.refresh() }
    // When the speech models arrive, process any recordings that were waiting.
    LaunchedEffect(status) {
        if (app.models.coreReady && app.repo.meetings.value.any { it.status == MeetingStatus.QUEUED }) {
            app.repo.meetings.value.firstOrNull { it.status == MeetingStatus.QUEUED }?.let { ProcessingService.enqueue(ctx, it.id) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline models") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(if (allReady) "All set — works fully offline" else "One-time download", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (allReady) "Recordings, transcripts and summaries never leave this phone."
                            else "About ${formatBytes(remaining)} to download. Use Wi-Fi if you can. Downloads continue in the background and resume if the connection drops.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (!allReady) {
                            Spacer(Modifier.height(4.dp))
                            Text("Free space on phone: ${formatBytes(free)}", style = MaterialTheme.typography.labelMedium,
                                color = if (free < remaining + 500L * 1024 * 1024) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = {
                                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else app.models.downloadAll()
                            }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text("Download all")
                            }
                        }
                    }
                }
            }
            items(status, key = { it.spec.kind.name }) { s ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.spec.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    formatBytes(s.spec.approxBytes) + if (s.spec.kind == ModelKind.SUMMARY) " · optional" else " · required",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            when (s.state) {
                                DownloadState.READY -> Icon(Icons.Outlined.CheckCircle, "Ready", tint = MaterialTheme.colorScheme.secondary)
                                DownloadState.FAILED -> Icon(Icons.Outlined.ErrorOutline, "Failed", tint = MaterialTheme.colorScheme.error)
                                else -> {}
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(s.spec.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        when (s.state) {
                            DownloadState.DOWNLOADING, DownloadState.QUEUED -> {
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(progress = { s.fraction }, modifier = Modifier.fillMaxWidth())
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        s.message ?: "${formatBytes(s.downloaded)} of ${formatBytes(s.total)}",
                                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { app.models.cancel(s.spec.kind) }) { Text("Cancel") }
                                }
                            }
                            DownloadState.FAILED, DownloadState.MISSING -> {
                                s.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { app.models.download(s.spec.kind) }) { Text(if (s.state == DownloadState.FAILED) "Retry" else "Download") }
                                }
                            }
                            DownloadState.READY -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { app.models.delete(s.spec.kind) }) { Text("Remove") }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "Models: Fluister turbo (DigiPhyte, MIT, based on OpenAI Whisper), pyannote 3.0 and NVIDIA TitaNet via sherpa-onnx, Gemma 4 E2B (Google, Apache-2.0).",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
