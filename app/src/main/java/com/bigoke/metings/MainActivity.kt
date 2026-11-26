package com.bigoke.metings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bigoke.metings.ui.HomeScreen
import com.bigoke.metings.ui.MeetingScreen
import com.bigoke.metings.ui.ModelsScreen
import com.bigoke.metings.ui.RecordScreen
import com.bigoke.metings.ui.SettingsScreen
import com.bigoke.metings.ui.theme.MetingsTheme

class MainActivity : ComponentActivity() {

    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            MetingsTheme { AppNav(pendingRoute.value) { pendingRoute.value = null } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        intent.getStringExtra("meeting")?.let { pendingRoute.value = "meeting/$it" }
        if (intent.getStringExtra("open") == "record") pendingRoute.value = "record"
    }
}

@Composable
private fun AppNav(pending: String?, consumed: () -> Unit) {
    val nav = rememberNavController()
    LaunchedEffect(pending) {
        if (pending != null) {
            nav.navigate(pending) { launchSingleTop = true }
            consumed()
        }
    }
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onRecord = { nav.navigate("record") },
                onOpen = { id -> nav.navigate("meeting/$id") },
                onModels = { nav.navigate("models") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("record") {
            RecordScreen(
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
    }
}
