package com.bigoke.metings.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bigoke.metings.data.Meeting
import com.bigoke.metings.ui.theme.BrandGradient
import com.bigoke.metings.ui.theme.MonoNumbers
import com.bigoke.metings.ui.theme.RecordGradient
import com.bigoke.metings.ui.theme.RecordRed
import com.bigoke.metings.ui.theme.speakerColor
import com.bigoke.metings.util.tr

/** Deep brand gradient panel with rounded bottom corners, used as the hero at the top of screens. */
@Composable
fun HeroPanel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(BrandGradient),
        content = content,
    )
}

/** Small frosted tile on the hero: a number and a label. */
@Composable
fun HeroStat(value: String, label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
    }
}

/** The big red record button with a soft breathing halo. */
@Composable
fun RecordOrb(onClick: () -> Unit, size: Dp = 76.dp, icon: ImageVector = Icons.Filled.Mic, breathing: Boolean = true) {
    val t = rememberInfiniteTransition(label = "orb")
    val halo by t.animateFloat(1f, 1.35f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "halo")
    val alpha by t.animateFloat(0.35f, 0f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "alpha")
    Box(Modifier.size(size * 1.45f), contentAlignment = Alignment.Center) {
        if (breathing) Box(Modifier.size(size).scale(halo).clip(CircleShape).background(RecordRed.copy(alpha = alpha)))
        Box(
            Modifier.size(size).clip(CircleShape).background(RecordGradient).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, tr("Record", "Neem op"), tint = Color.White, modifier = Modifier.size(size * 0.42f))
        }
    }
}

data class TimelineMarker(val ms: Long, val color: Color)

/**
 * "Who spoke when": one lane per speaker across the whole meeting, with the play position and
 * markers for search hits, bookmarks and tasks. Tap or drag to jump there.
 */
@Composable
fun SpeakerTimeline(
    m: Meeting, durationMs: Long, positionMs: Long, markers: List<TimelineMarker>, onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (durationMs <= 0 || m.utterances.isEmpty()) return
    val lanes = m.speakers.take(6)
    val laneH = 7.dp
    val head = MaterialTheme.colorScheme.onSurface
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                lanes.forEach { s -> SpeakerAvatar(s.name, s.colorIndex, laneH + 3.dp) }
            }
            Spacer(Modifier.width(8.dp))
            Canvas(
                Modifier.weight(1f).height((laneH + 3.dp) * lanes.size + 3.dp * (lanes.size - 1) + 10.dp)
                    .pointerInput(durationMs) { detectTapGestures { o -> onSeek((o.x / size.width * durationMs).toLong().coerceIn(0, durationMs)) } }
                    .pointerInput(durationMs) {
                        detectHorizontalDragGestures { change, _ -> onSeek((change.position.x / size.width * durationMs).toLong().coerceIn(0, durationMs)) }
                    },
            ) {
                val w = size.width
                val lh = laneH.toPx(); val gap = 3.dp.toPx() + 3.dp.toPx(); val top = 6.dp.toPx()
                lanes.forEachIndexed { li, s ->
                    val y = top + li * (lh + gap) + 1.5.dp.toPx()
                    drawRoundRect(track, Offset(0f, y), Size(w, lh), CornerRadius(lh / 2))
                    m.utterances.filter { it.speaker == s.id }.forEach { u ->
                        val x0 = w * u.startMs / durationMs
                        val x1 = (w * u.endMs / durationMs).coerceAtLeast(x0 + 2f)
                        drawRoundRect(speakerColor(s.colorIndex), Offset(x0, y), Size(x1 - x0, lh), CornerRadius(lh / 2))
                    }
                }
                markers.forEach { mk ->
                    val x = w * mk.ms / durationMs
                    drawCircle(mk.color, 3.dp.toPx(), Offset(x, 2.5.dp.toPx()))
                }
                val px = w * (positionMs.coerceIn(0, durationMs)).toFloat() / durationMs
                drawLine(head, Offset(px, 0f), Offset(px, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}

/** Ring chart of who talked how much. */
@Composable
fun TalkRing(slices: List<Pair<Color, Float>>, centerTop: String, centerBottom: String, size: Dp = 150.dp) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            val total = slices.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(0.0001f)
            var start = -90f
            slices.forEach { (c, v) ->
                val sweep = 360f * v / total
                drawArc(c, start + 1.5f, (sweep - 3f).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, style = MaterialTheme.typography.titleLarge.merge(MonoNumbers))
            Text(centerBottom, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Step-by-step progress while a meeting is processed, with the current step animated. */
@Composable
fun ProcessingStages(stage: String?, overall: Float?) {
    val labels = listOf(
        tr("Recognising voices", "Herken stemme"),
        tr("Transcribing", "Transkribeer"),
        tr("Making it readable", "Maak dit leesbaar"),
        tr("Finding details", "Soek besonderhede"),
        tr("Writing the minutes", "Skryf die notule"),
    )
    val cur = when (stage) {
        "Loading audio", "Recognising voices" -> 0
        "Transcribing" -> 1
        "Making it readable", "Adding punctuation" -> 2
        "Finding details" -> 3
        "Writing summary", "Saving audio" -> 4
        else -> -1
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        labels.forEachIndexed { idx, label ->
            val done = idx < cur
            val active = idx == cur
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    done -> Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
                    active -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.secondary)
                    else -> Box(Modifier.size(22.dp).padding(3.dp).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    label, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (done || active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (overall != null) {
            Text("${(overall * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium.merge(MonoNumbers), color = MaterialTheme.colorScheme.secondary)
        }
    }
}

/** Animated "thinking" dots for the AI. */
@Composable
fun ThinkingDots(color: Color = MaterialTheme.colorScheme.secondary) {
    val t = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        (0..2).forEach { i ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 180), RepeatMode.Reverse), label = "d$i")
            Box(Modifier.size(7.dp).clip(CircleShape).background(color.copy(alpha = a)))
        }
    }
}

/** Gradient pill button. */
@Composable
fun GradientPill(text: String, brush: Brush, onClick: () -> Unit, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(brush).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}
