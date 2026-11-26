package com.bigoke.metings.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bigoke.metings.MetingsApp
import com.bigoke.metings.data.MeetingStatus
import com.bigoke.metings.engine.DownloadState
import com.bigoke.metings.engine.ModelKind
import com.bigoke.metings.service.ProcessingService
import com.bigoke.metings.util.LocalAf
import com.bigoke.metings.util.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = MetingsApp.instance
    val af = LocalAf.current
    val status by app.models.status.collectAsStateWithLifecycle()
    val moving by app.models.moving.collectAsStateWithLifecycle()
    var sharedAllowed by remember { mutableStateOf(app.models.sharedAllowed()) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { app.models.downloadAll() }
    val allReady = status.all { it.state == DownloadState.READY }
    val remaining = status.filter { it.state != DownloadState.READY }.sumOf { it.spec.approxBytes }
    val free = app.models.freeSpaceBytes()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        sharedAllowed = app.models.sharedAllowed()
        app.models.refresh()
        if (sharedAllowed && app.models.privateModelCount() > 0) app.models.moveToShared()
    }
    LaunchedEffect(status) {
        if (app.models.coreReady) app.repo.meetings.value.firstOrNull { it.status == MeetingStatus.QUEUED }?.let { ProcessingService.enqueue(ctx, it.id) }
    }

    fun askAllFiles() {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName)))
        }.onFailure { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Offline models", "Vanlyn-modelle")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back", "Terug")) } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(if (allReady) tr("All set — works fully offline", "Gereed — werk heeltemal vanlyn") else tr("One-time download", "Eenmalige aflaai"),
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (allReady) tr("Recordings, transcripts and summaries never leave this phone.", "Opnames, transkripsies en opsommings verlaat nooit hierdie foon nie.")
                            else tr("About ${formatBytes(remaining)} to download. Use Wi-Fi if you can. Downloads continue in the background and resume if the connection drops.",
                                "Sowat ${formatBytes(remaining)} om af te laai. Gebruik Wi-Fi as jy kan. Aflaaie gaan voort in die agtergrond en hervat as die verbinding wegval."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (!allReady) {
                            Spacer(Modifier.height(4.dp))
                            Text(tr("Free space on phone: ", "Vrye spasie op foon: ") + formatBytes(free), style = MaterialTheme.typography.labelMedium,
                                color = if (free < remaining + 500L * 1024 * 1024) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = {
                                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else app.models.downloadAll()
                            }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text(tr("Download all", "Laai alles af"))
                            }
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = if (sharedAllowed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.FolderShared, null)
                            Spacer(Modifier.width(10.dp))
                            Text(if (sharedAllowed) tr("Models are kept safe", "Modelle word veilig gehou") else tr("Never download again", "Laai nooit weer af nie"),
                                style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (sharedAllowed) tr("Stored in ${app.models.sharedFolderLabel}. They stay there through app updates and even if you reinstall Notule — no re-download needed.",
                                "Gestoor in ${app.models.sharedFolderLabel}. Hulle bly daar deur opdaterings en selfs as jy Notule weer installeer — geen heraflaai nodig nie.")
                            else tr("Allow Notule to keep its models in ${app.models.sharedFolderLabel}. Then they survive updates and reinstalls, so you never download the 3.5 GB again. Already downloaded models are moved there.",
                                "Laat Notule toe om sy modelle in ${app.models.sharedFolderLabel} te hou. Dan oorleef hulle opdaterings en herinstallasies, sodat jy nooit weer die 3,5 GB aflaai nie. Reeds afgelaaide modelle word daarheen geskuif."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        moving?.let { f ->
                            Spacer(Modifier.height(8.dp))
                            Text(tr("Moving models… ", "Skuif modelle… ") + "${(f * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                            LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
                        }
                        if (!sharedAllowed) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = { askAllFiles() }) { Text(tr("Allow folder access", "Gee vouertoegang")) }
                            Text(tr("Android will open a settings page: switch on “Allow access to manage all files”, then come back.",
                                "Android maak 'n instellingsblad oop: skakel “Laat toegang toe om alle lêers te bestuur” aan en kom dan terug."),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            items(status, key = { it.spec.kind.name }) { s ->
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (af) s.spec.titleAf else s.spec.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    formatBytes(s.spec.approxBytes) + " · " + (if (s.spec.kind == ModelKind.SUMMARY) tr("optional", "opsioneel") else tr("required", "nodig")) +
                                        if (s.state == DownloadState.READY) " · " + (if (s.shared) tr("in shared folder", "in gedeelde vouer") else tr("in app folder", "in toepvouer")) else "",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            when (s.state) {
                                DownloadState.READY -> Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
                                DownloadState.FAILED -> Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                                else -> {}
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(if (af) s.spec.descriptionAf else s.spec.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        when (s.state) {
                            DownloadState.DOWNLOADING, DownloadState.QUEUED -> {
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(progress = { s.fraction }, modifier = Modifier.fillMaxWidth())
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (s.message == "waiting") tr("Waiting for network…", "Wag vir netwerk…")
                                        else "${formatBytes(s.downloaded)} / ${formatBytes(s.total)}",
                                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { app.models.cancel(s.spec.kind) }) { Text(tr("Cancel", "Kanselleer")) }
                                }
                            }
                            DownloadState.FAILED, DownloadState.MISSING -> {
                                if (s.state == DownloadState.FAILED) Text(tr("Download failed — check your connection and retry.", "Aflaai het misluk — kyk jou verbinding en probeer weer."),
                                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { app.models.download(s.spec.kind) }) {
                                        Text(if (s.state == DownloadState.FAILED) tr("Retry", "Probeer weer") else tr("Download", "Laai af"))
                                    }
                                }
                            }
                            DownloadState.READY -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { app.models.delete(s.spec.kind) }) { Text(tr("Remove", "Verwyder")) }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "Fluister turbo (DigiPhyte, MIT, OpenAI Whisper) · pyannote 3.0 · NVIDIA TitaNet (sherpa-onnx) · Gemma 4 E2B (Google, Apache-2.0)",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
