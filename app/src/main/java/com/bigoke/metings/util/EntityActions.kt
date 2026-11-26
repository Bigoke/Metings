package com.bigoke.metings.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.widget.Toast
import com.bigoke.metings.data.Entity
import com.bigoke.metings.data.EntityType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class EntityAction(val label: String, val run: (Context) -> Unit)

object EntityActions {

    fun copy(ctx: Context, text: String) {
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Metings", text))
        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun start(ctx: Context, intent: Intent) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(ctx, "No app found for this action", Toast.LENGTH_SHORT).show()
        }
    }

    private fun maps(ctx: Context, query: String) =
        start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))))

    private fun calendar(ctx: Context, title: String, date: LocalDate, time: LocalTime?) {
        val zone = ZoneId.systemDefault()
        val begin = (if (time != null) date.atTime(time) else date.atStartOfDay()).atZone(zone).toInstant().toEpochMilli()
        val end = begin + if (time != null) 60 * 60 * 1000L else 24 * 60 * 60 * 1000L
        start(
            ctx, Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, time == null)
                .putExtra(CalendarContract.Events.TITLE, title)
        )
    }

    /** Actions offered when a highlighted detail is tapped. */
    fun forEntity(e: Entity, meetingTitle: String, sentence: String, nearbyTime: String?, af: Boolean): List<EntityAction> {
        val list = ArrayList<EntityAction>()
        when (e.type) {
            EntityType.DATE -> {
                val d = runCatching { LocalDate.parse(if (e.value.length == 7) e.value + "-01" else e.value) }.getOrNull()
                if (d != null) {
                    val t = nearbyTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                    list += EntityAction(if (af) "Voeg by kalender" else "Add to calendar") { calendar(it, "$meetingTitle: ${sentence.take(80)}", d, t) }
                }
            }
            EntityType.TIME -> {
                val t = runCatching { LocalTime.parse(e.value) }.getOrNull()
                if (t != null) list += EntityAction(if (af) "Voeg vandag by kalender" else "Add to calendar today") {
                    calendar(it, "$meetingTitle: ${sentence.take(80)}", LocalDate.now(), t)
                }
                list += EntityAction(if (af) "Stel herinnering" else "Set alarm") { ctx ->
                    if (t != null) start(ctx, Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
                        .putExtra(android.provider.AlarmClock.EXTRA_HOUR, t.hour)
                        .putExtra(android.provider.AlarmClock.EXTRA_MINUTES, t.minute)
                        .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, meetingTitle))
                }
            }
            EntityType.PLACE, EntityType.ADDRESS ->
                list += EntityAction(if (af) "Wys op kaart" else "Open in Maps") { maps(it, e.value.ifEmpty { e.text }) }
            EntityType.PHONE -> {
                list += EntityAction(if (af) "Bel" else "Call") { start(it, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + e.value))) }
                list += EntityAction("WhatsApp") {
                    start(it, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + e.value.trimStart('+'))))
                }
                list += EntityAction(if (af) "Stoor as kontak" else "Save contact") {
                    start(it, Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
                        .putExtra(ContactsContract.Intents.Insert.PHONE, e.value))
                }
            }
            EntityType.EMAIL -> {
                list += EntityAction(if (af) "Stuur e-pos" else "Send e-mail") {
                    start(it, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + e.value)).putExtra(Intent.EXTRA_SUBJECT, meetingTitle))
                }
                list += EntityAction(if (af) "Stoor as kontak" else "Save contact") {
                    start(it, Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
                        .putExtra(ContactsContract.Intents.Insert.EMAIL, e.value))
                }
            }
            EntityType.PERSON -> list += EntityAction(if (af) "Stoor as kontak" else "Save contact") {
                start(it, Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
                    .putExtra(ContactsContract.Intents.Insert.NAME, e.value.ifEmpty { e.text }))
            }
            EntityType.TASK -> {
                val due = e.value.split('|').getOrNull(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                list += EntityAction(if (af) "Voeg by kalender" else "Add to calendar") {
                    calendar(it, e.text.take(100), due ?: LocalDate.now().plusDays(1), null)
                }
            }
            else -> {}
        }
        list += EntityAction(if (af) "Kopieer" else "Copy") { copy(it, if (e.type in setOf(EntityType.PERSON, EntityType.TASK)) e.text else e.value.ifEmpty { e.text }) }
        list += EntityAction(if (af) "Deel" else "Share") {
            start(it, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, e.text), null))
        }
        return list
    }
}
