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

class MainActivity : FragmentActivity() {

    private val pendingRoute = mutableStateOf<String?>(null)
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
                    else AppNav(pendingRoute.value, onConsumed = { pendingRoute.value = null }, onClose = { finish() })
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
        intent.getStringExtra("meeting")?.let { pendingRoute.value = "meeting/$it" }
        when (intent.getStringExtra("open")) {
            "record" -> pendingRoute.value = "record?auto=false"
            "quickRecord" -> pendingRoute.value = "record?auto=true"
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
private fun AppNav(pending: String?, onConsumed: () -> Unit, onClose: () -> Unit) {
    val nav = rememberNavController()
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
