package com.bigoke.metings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigoke.metings.data.EntityType
import com.bigoke.metings.ui.theme.speakerColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** "Today · 14:12", "Yesterday · 09:30", "Mon 24 Nov · 15:00", "12 Mar 2025 · 10:00". */
fun formatDate(ms: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val d = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val today = java.time.LocalDate.now(zone)
    val loc = if (com.bigoke.metings.util.Ui.isAf()) Locale("af", "ZA") else Locale.getDefault()
    val time = SimpleDateFormat("HH:mm", loc).format(Date(ms))
    val day = when {
        d == today -> com.bigoke.metings.util.Ui.t("Today", "Vandag")
        d == today.minusDays(1) -> com.bigoke.metings.util.Ui.t("Yesterday", "Gister")
        d.year == today.year -> SimpleDateFormat("EEE d MMM", loc).format(Date(ms))
        else -> SimpleDateFormat("d MMM yyyy", loc).format(Date(ms))
    }
    return "$day · $time"
}

/** Small coloured status label used on meeting cards. */
@Composable
fun StatusPill(text: String, color: Color) {
    Box(
        Modifier.background(color.copy(alpha = 0.14f), androidx.compose.foundation.shape.RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.0f MB".format(b / (1L shl 20).toDouble())
    else -> "%.0f KB".format(b / 1024.0)
}

fun entityIcon(t: EntityType): ImageVector = when (t) {
    EntityType.AMOUNT -> Icons.Outlined.Payments
    EntityType.NUMBER -> Icons.Outlined.Numbers
    EntityType.PERCENT -> Icons.Outlined.Percent
    EntityType.MEASUREMENT -> Icons.Outlined.Straighten
    EntityType.DATE -> Icons.Outlined.CalendarMonth
    EntityType.TIME -> Icons.Outlined.AccessTime
    EntityType.PERSON -> Icons.Outlined.Person
    EntityType.PLACE -> Icons.Outlined.Place
    EntityType.ADDRESS -> Icons.Outlined.Home
    EntityType.PHONE -> Icons.Outlined.Phone
    EntityType.EMAIL -> Icons.Outlined.AlternateEmail
    EntityType.COLOUR -> Icons.Outlined.Palette
    EntityType.SHAPE -> Icons.Outlined.Category
    EntityType.TASK -> Icons.Outlined.TaskAlt
}

fun entityLabel(t: EntityType, af: Boolean): String = when (t) {
    EntityType.AMOUNT -> if (af) "Bedrae" else "Amounts"
    EntityType.NUMBER -> if (af) "Getalle" else "Numbers"
    EntityType.PERCENT -> if (af) "Persentasies" else "Percentages"
    EntityType.MEASUREMENT -> if (af) "Mates" else "Measurements"
    EntityType.DATE -> if (af) "Datums" else "Dates"
    EntityType.TIME -> if (af) "Tye" else "Times"
    EntityType.PERSON -> if (af) "Name" else "Names"
    EntityType.PLACE -> if (af) "Plekke" else "Places"
    EntityType.ADDRESS -> if (af) "Adresse" else "Addresses"
    EntityType.PHONE -> if (af) "Telefoonnommers" else "Phone numbers"
    EntityType.EMAIL -> if (af) "E-posadresse" else "E-mail addresses"
    EntityType.COLOUR -> if (af) "Kleure" else "Colours"
    EntityType.SHAPE -> if (af) "Vorms" else "Shapes"
    EntityType.TASK -> if (af) "Take" else "Tasks"
}

@Composable
fun SpeakerAvatar(name: String, colorIndex: Int, size: Dp = 32.dp) {
    val initials = name.split(" ").filter { it.isNotBlank() }.let { parts ->
        when {
            parts.isEmpty() -> "?"
            parts[0].equals("Person", true) && parts.size > 1 -> "P" + parts[1].take(2)
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> (parts[0].take(1) + parts.last().take(1)).uppercase()
        }
    }
    Box(
        Modifier.size(size).background(speakerColor(colorIndex), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = modifier,
    )
}
