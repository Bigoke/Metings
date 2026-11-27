package com.bigoke.metings.screens

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bigoke.metings.MetingsApp
import com.bigoke.metings.data.ActionItem
import com.bigoke.metings.data.Entity
import com.bigoke.metings.data.EntityType
import com.bigoke.metings.data.Meeting
import com.bigoke.metings.data.MeetingStatus
import com.bigoke.metings.data.QaItem
import com.bigoke.metings.data.Speaker
import com.bigoke.metings.data.Summary
import com.bigoke.metings.data.Utterance
import com.bigoke.metings.ui.HomeScreen
import com.bigoke.metings.ui.MeetingScreen
import com.bigoke.metings.ui.RecordScreen
import com.bigoke.metings.ui.theme.MetingsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the main screens to PNG files (build/screens) so the design can be reviewed without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = System.currentTimeMillis()

    private fun sample(): List<Meeting> {
        val speakers = listOf(Speaker(0, "Marietjie Botha"), Speaker(1, "Jan"), Speaker(2, "Persoon 3"))
        val lines = listOf(
            0 to "Goeiemôre almal. Dankie dat julle so vroeg kon kom. Ons begin met die begroting vir die nuwe kantoor in Pretoria.",
            1 to "Die kwotasie van Jacobs Meubels is R185 000, insluitend aflewering teen 15 Maart.",
            0 to "Goed, ons het besluit om met Jacobs te gaan. Jan, sal jy die bestelling teen volgende Vrydag plaas?",
            1 to "Ja, ek sal dit doen. Ek bel hulle môre by 082 555 1234.",
            2 to "Wat van die verf? Ek dink die mure moet ligblou wees, en die vergaderkamer donkergroen.",
            0 to "Kom ons kry eers 'n kwotasie. Ons vergader weer op 20 Maart om 14:30.",
        )
        var t = 0L
        val utts = lines.map { (s, txt) -> Utterance(s, t, t + 42_000, txt, lang = "af").also { t += 47_000 } }
        val ready = Meeting(
            id = "m1", title = "Kantoorbegroting Q1", createdAt = now - 2 * 3600_000, durationMs = t, language = "af",
            status = MeetingStatus.READY, speakers = speakers, utterances = utts, folder = "Bigoke", template = "board",
            entities = listOf(
                Entity(EntityType.PLACE, "Pretoria", "Pretoria", 0, 95, 103),
                Entity(EntityType.AMOUNT, "R185 000", "ZAR 185000.00", 1, 32, 40),
                Entity(EntityType.DATE, "15 Maart", "2027-03-15", 1, 66, 74),
                Entity(EntityType.PERSON, "Jan", "Jan", 2, 51, 54),
                Entity(EntityType.DATE, "volgende Vrydag", "2026-12-04", 2, 89, 104),
                Entity(EntityType.PHONE, "082 555 1234", "+27825551234", 3, 39, 51),
                Entity(EntityType.COLOUR, "ligblou", "#81D4FA", 4, 60, 67),
                Entity(EntityType.TASK, "Jan, sal jy die bestelling teen volgende Vrydag plaas?", "Jan|2026-12-04", 2, 46, 105),
            ),
            summary = Summary(
                overview = "Die span het die begroting vir die nuwe kantoor in Pretoria bespreek en besluit om die meubels by Jacobs Meubels te koop. Jan plaas die bestelling volgende week; die kleure vir die mure moet nog gekies word.",
                decisions = listOf("Meubels word by Jacobs Meubels gekoop (R185 000 insluitend aflewering)."),
                actions = listOf(ActionItem("Plaas die bestelling by Jacobs Meubels", "Jan", "4 Des"), ActionItem("Kry 'n kwotasie vir verf", "Marietjie Botha", "")),
                keyFigures = listOf("R185 000 — kwotasie vir meubels", "20 Maart 14:30 — volgende vergadering"),
                followUps = listOf("Kleur vir die vergaderkamer"),
                extraTitle = "Resolusies", extra = listOf("Voorgestel deur Marietjie, gesekondeer deur Jan: aanvaar kwotasie."),
                engine = "gemma", language = "af",
            ),
            qa = listOf(QaItem("Wie moet wat doen?", "Jan plaas die bestelling by Jacobs Meubels teen volgende Vrydag [1:34]. Marietjie kry 'n kwotasie vir die verf [3:55].", pending = false, engine = "gemma")),
            bookmarksMs = listOf(95_000),
        )
        return listOf(
            ready,
            Meeting("m2", "Kliëntkonsultasie — Smit", now - 26 * 3600_000, 1_260_000, "auto", MeetingStatus.PROCESSING),
            Meeting("m3", "Terreininspeksie Centurion", now - 3 * 86400_000L, 845_000, "af", MeetingStatus.NEW),
        )
    }

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        MetingsApp.instance.repo.showForPreview(sample())
        // Endless animations (record halo, progress) never go idle: drive the clock by hand.
        rule.mainClock.autoAdvance = false
        rule.setContent { MetingsTheme { content() } }
        rule.mainClock.advanceTimeBy(1500)
        // Draw the window directly (does not wait for endless animations to stop).
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width.coerceAtLeast(1), root.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bmp))
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun home() = shot("1-home") { HomeScreen({}, {}, {}, {}, {}, {}) }
    @Test fun record() = shot("2-record") { RecordScreen(false, {}, {}, {}) }
    @Test fun summary() = shot("3-summary") { MeetingScreen("m1", {}, {}, initialTab = 0) }
    @Test fun transcript() = shot("4-transcript") { MeetingScreen("m1", {}, {}, initialTab = 1) }
    @Test fun ask() = shot("5-ask") { MeetingScreen("m1", {}, {}, initialTab = 2) }
    @Test fun people() = shot("6-people") { MeetingScreen("m1", {}, {}, initialTab = 4) }
    @Test fun processing() = shot("7-processing") { MeetingScreen("m2", {}, {}) }
}
