package com.bigoke.metings

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bigoke.metings.data.MeetingStatus
import com.bigoke.metings.service.ProcessingService
import com.bigoke.metings.ui.HomeScreen
import com.bigoke.metings.ui.MeetingScreen
import com.bigoke.metings.ui.ModelsScreen
import com.bigoke.metings.ui.RecordScreen
import com.bigoke.metings.ui.SettingsScreen
import com.bigoke.metings.ui.TasksScreen
import com.bigoke.metings.ui.theme.MetingsTheme
import com.bigoke.metings.util.LocalAf
import com.bigoke.metings.util.Ui
import com.bigoke.metings.util.tr
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val pendingRoute = mutableStateOf<String?>(null)
    /** Audio files shared to Notule from another app (WhatsApp, Truecaller, a call recorder…). */
    private val sharedUris = mutableStateOf<List<android.net.Uri>>(emptyList())
    private val openImport = mutableStateOf(false)
    private val locked = mutableStateOf(false)
    private var backgroundAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = MetingsApp.instance
        locked.value = app.prefs.settings.value.appLock
        handle(intent)
        setContent {
            val settings by app.prefs.settings.collectAsStateWithLifecycle()
            val af = when (settings.uiLanguage) { "af" -> true; "en" -> false; else -> Locale.getDefault().language == "af" }
            CompositionLocalProvider(LocalAf provides af) {
                MetingsTheme {
                    if (locked.value && settings.appLock) LockScreen { authenticate() }
                    else AppNav(
                        pendingRoute.value, onConsumed = { pendingRoute.value = null }, onClose = { finish() },
                        shared = sharedUris.value, onSharedConsumed = { sharedUris.value = emptyList() },
                        openImport = openImport.value, onImportOpened = { openImport.value = false },
                    )
                }
            }
        }
        if (locked.value) authenticate()
    }

    override fun onStop() {
        super.onStop()
        backgroundAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val app = MetingsApp.instance
        if (app.prefs.settings.value.appLock && backgroundAt > 0 && SystemClock.elapsedRealtime() - backgroundAt > 2 * 60_000) {
            locked.value = true
            authenticate()
        }
        // Pick up meetings that are still waiting (e.g. after the app was closed while processing).
        if (app.models.coreReady && app.repo.meetings.value.any { it.status == MeetingStatus.QUEUED }) {
            runCatching { ProcessingService.enqueue(this, app.repo.meetings.value.first { it.status == MeetingStatus.QUEUED }.id) }
        }
    }

    private fun authenticate() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            locked.value = false // no screen lock set up on the phone: nothing to check against
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                locked.value = false
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(Ui.t("Unlock Notule", "Ontsluit Notule"))
                .setSubtitle(Ui.t("Your meetings are protected", "Jou vergaderings is beskerm"))
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val u = (if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                    else intent.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri) ?: intent.data
                if (u != null) sharedUris.value = listOf(u)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val list = if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                    else intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                if (!list.isNullOrEmpty()) sharedUris.value = list.toList()
            }
            Intent.ACTION_VIEW -> intent.data?.let { sharedUris.value = listOf(it) }
        }
        intent.getStringExtra("meeting")?.let { pendingRoute.value = "meeting/$it" }
        when (intent.getStringExtra("open")) {
            "record" -> pendingRoute.value = "record?auto=false"
            "quickRecord" -> pendingRoute.value = "record?auto=true"
            "import" -> openImport.value = true
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Notule", style = MaterialTheme.typography.headlineMedium)
            Text(tr("Locked", "Gesluit"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock) { Text(tr("Unlock", "Ontsluit")) }
        }
    }
}

@Composable
private fun AppNav(
    pending: String?, onConsumed: () -> Unit, onClose: () -> Unit,
    shared: List<android.net.Uri>, onSharedConsumed: () -> Unit,
    openImport: Boolean, onImportOpened: () -> Unit,
) {
    val nav = rememberNavController()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var sheet by remember { mutableStateOf(false) }
    var sheetShared by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    val failMsg = tr("Could not read this audio file.", "Kon nie hierdie klanklêer lees nie.")
    val doneMsg = tr("imported", "ingevoer")
    fun runImport(uris: List<android.net.Uri>, opts: com.bigoke.metings.engine.ImportOptions) {
        sheet = false
        if (uris.isEmpty()) return
        com.bigoke.metings.ui.ImportProgress.fraction = 0f
        com.bigoke.metings.ui.ImportProgress.error = null
        nav.popBackStack("home", inclusive = false)
        scope.launch {
            val ids = ArrayList<String>()
            uris.forEachIndexed { i, u ->
                com.bigoke.metings.ui.ImportProgress.label = if (uris.size > 1) " ${i + 1}/${uris.size}" else ""
                val id = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.bigoke.metings.engine.ImportSources.importOne(ctx, u, opts) { f ->
                        com.bigoke.metings.ui.ImportProgress.fraction = (i + f) / uris.size
                    }
                }
                if (id != null) ids += id
            }
            com.bigoke.metings.ui.ImportProgress.fraction = null
            com.bigoke.metings.ui.ImportProgress.label = ""
            if (ids.size < uris.size) com.bigoke.metings.ui.ImportProgress.error = failMsg + if (ids.isNotEmpty()) " (${uris.size - ids.size})" else ""
            com.bigoke.metings.engine.ImportSources.startProcessing(ctx, ids)
            if (ids.size == 1) nav.navigate("meeting/${ids[0]}")
            else if (ids.size > 1) android.widget.Toast.makeText(ctx, "${ids.size} $doneMsg", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(shared) {
        if (shared.isNotEmpty()) {
            val s = MetingsApp.instance.prefs.settings.value
            if (s.quickImport) runImport(shared, com.bigoke.metings.engine.ImportOptions(s.importLanguage, s.importTemplate, s.importFolderBySource, s.importTranscribe))
            else { sheetShared = shared; sheet = true }
            onSharedConsumed()
        }
    }
    LaunchedEffect(openImport) { if (openImport) { sheetShared = emptyList(); sheet = true; onImportOpened() } }
    if (sheet) com.bigoke.metings.ui.ImportSheet(sheetShared, onDismiss = { sheet = false }, onImport = { u, o -> runImport(u, o) })
    LaunchedEffect(pending) {
        if (pending != null) {
            nav.navigate(pending) { launchSingleTop = true }
            onConsumed()
        }
    }
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            var confirmExit by remember { mutableStateOf(false) }
            BackHandler { confirmExit = true }
            HomeScreen(
                onRecord = { nav.navigate("record?auto=false") },
                onOpen = { id -> nav.navigate("meeting/$id") },
                onModels = { nav.navigate("models") },
                onSettings = { nav.navigate("settings") },
                onTasks = { nav.navigate("tasks") },
                onImport = { sheetShared = emptyList(); sheet = true },
            )
            if (confirmExit) {
                AlertDialog(
                    onDismissRequest = { confirmExit = false },
                    title = { Text(tr("Close Notule?", "Maak Notule toe?")) },
                    text = { Text(tr("Recording and processing carry on in the background.", "Opname en verwerking gaan voort in die agtergrond.")) },
                    confirmButton = { TextButton(onClick = { confirmExit = false; onClose() }) { Text(tr("Close", "Maak toe")) } },
                    dismissButton = { TextButton(onClick = { confirmExit = false }) { Text(tr("Stay", "Bly")) } },
                )
            }
        }
        composable(
            "record?auto={auto}",
            arguments = listOf(navArgument("auto") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            RecordScreen(
                autoStart = entry.arguments?.getBoolean("auto") ?: false,
                onBack = { nav.popBackStack() },
                onFinished = { id ->
                    nav.popBackStack()
                    nav.navigate("meeting/$id")
                },
                onModels = { nav.navigate("models") },
            )
        }
        composable("meeting/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            MeetingScreen(id = id, onBack = { nav.popBackStack() }, onModels = { nav.navigate("models") })
        }
        composable("models") { ModelsScreen(onBack = { nav.popBackStack() }) }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }, onModels = { nav.navigate("models") }) }
        composable("tasks") { TasksScreen(onBack = { nav.popBackStack() }, onOpen = { id -> nav.navigate("meeting/$id") }) }
    }
}
