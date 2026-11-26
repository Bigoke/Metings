package com.bigoke.metings.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.bigoke.metings.MetingsApp
import com.bigoke.metings.data.EntityType
import com.bigoke.metings.data.Meeting
import com.bigoke.metings.ui.entityLabel
import com.bigoke.metings.ui.formatDate
import com.bigoke.metings.ui.formatDuration
import java.io.File

object Exporter {

    private fun safeName(m: Meeting) = m.title.replace(Regex("""[^\p{L}\d _-]"""), "").trim().ifEmpty { "meeting" }.take(60)

    fun asText(m: Meeting): String {
        val af = m.language == "af"
        val sb = StringBuilder()
        sb.appendLine(m.title).appendLine(formatDate(m.createdAt) + " · " + formatDuration(m.durationMs))
        sb.appendLine((if (af) "Deelnemers: " else "Participants: ") + m.speakers.joinToString(", ") { it.name })
        sb.appendLine()
        m.summary?.let { s ->
            sb.appendLine(if (af) "OPSOMMING" else "SUMMARY").appendLine(s.overview).appendLine()
            if (s.decisions.isNotEmpty()) { sb.appendLine(if (af) "BESLUITE" else "DECISIONS"); s.decisions.forEach { sb.appendLine("• $it") }; sb.appendLine() }
            if (s.actions.isNotEmpty()) {
                sb.appendLine(if (af) "AKSIEPUNTE" else "ACTION ITEMS")
                s.actions.forEach { a ->
                    sb.append("☐ ").append(a.text)
                    if (a.owner.isNotBlank()) sb.append(" — ").append(a.owner)
                    if (a.due.isNotBlank()) sb.append(" (").append(a.due).append(")")
                    sb.appendLine()
                }
                sb.appendLine()
            }
            if (s.keyFigures.isNotEmpty()) { sb.appendLine(if (af) "SLEUTELSYFERS" else "KEY FIGURES"); s.keyFigures.forEach { sb.appendLine("• $it") }; sb.appendLine() }
            if (s.followUps.isNotEmpty()) { sb.appendLine(if (af) "OPVOLGING" else "FOLLOW-UPS"); s.followUps.forEach { sb.appendLine("• $it") }; sb.appendLine() }
        }
        val details = m.entities.filter { it.type != EntityType.TASK }.groupBy { it.type }
        if (details.isNotEmpty()) {
            sb.appendLine(if (af) "BESONDERHEDE" else "DETAILS")
            details.forEach { (t, list) ->
                sb.appendLine(entityLabel(t, af) + ": " + list.distinctBy { it.value.ifEmpty { it.text } }.joinToString(", ") { it.text })
            }
            sb.appendLine()
        }
        sb.appendLine(if (af) "TRANSKRIPSIE" else "TRANSCRIPT")
        m.utterances.forEach { u ->
            sb.appendLine("[${formatDuration(u.startMs)}] ${m.speakerName(u.speaker)}: ${u.text}")
        }
        return sb.toString()
    }

    fun pdf(ctx: Context, m: Meeting): File {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val out = File(dir, safeName(m) + ".pdf")
        val doc = PdfDocument()
        val pageW = 595; val pageH = 842; val margin = 48
        val width = pageW - margin * 2
        val body = TextPaint().apply { textSize = 10.5f; color = Color.rgb(30, 32, 38); isAntiAlias = true }
        val title = TextPaint(body).apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(20, 33, 61) }
        val heading = TextPaint(body).apply { textSize = 12f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(14, 138, 126) }
        val muted = TextPaint(body).apply { color = Color.rgb(100, 106, 120); textSize = 9.5f }

        var pageNo = 0
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, ++pageNo).create())
        var y = margin.toFloat()

        fun newPage() {
            doc.finishPage(page)
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, ++pageNo).create())
            y = margin.toFloat()
        }
        fun draw(text: String, paint: TextPaint, spaceAfter: Float = 6f, indent: Int = 0) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width - indent)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f, 1f).build()
            var line = 0
            while (line < layout.lineCount) {
                val lineH = (layout.getLineBottom(line) - layout.getLineTop(line)).toFloat()
                if (y + lineH > pageH - margin) newPage()
                val c = page.canvas
                c.save()
                c.translate((margin + indent).toFloat(), y - layout.getLineTop(line))
                c.clipRect(0f, layout.getLineTop(line).toFloat(), (width - indent).toFloat(), layout.getLineBottom(line).toFloat())
                layout.draw(c)
                c.restore()
                y += lineH
                line++
            }
            y += spaceAfter
        }

        val af = m.language == "af"
        draw(m.title, title, 4f)
        draw(formatDate(m.createdAt) + "  ·  " + formatDuration(m.durationMs) + "  ·  " + m.speakers.joinToString(", ") { it.name }, muted, 16f)
        m.summary?.let { s ->
            draw(if (af) "Opsomming" else "Summary", heading)
            draw(s.overview, body, 12f)
            if (s.decisions.isNotEmpty()) { draw(if (af) "Besluite" else "Decisions", heading); s.decisions.forEach { draw("•  $it", body, 3f, 8) }; y += 9f }
            if (s.actions.isNotEmpty()) {
                draw(if (af) "Aksiepunte" else "Action items", heading)
                s.actions.forEach { a ->
                    val extra = listOfNotNull(a.owner.takeIf { it.isNotBlank() }, a.due.takeIf { it.isNotBlank() }).joinToString(" · ")
                    draw("☐  ${a.text}" + if (extra.isNotEmpty()) "  —  $extra" else "", body, 3f, 8)
                }
                y += 9f
            }
            if (s.keyFigures.isNotEmpty()) { draw(if (af) "Sleutelsyfers" else "Key figures", heading); s.keyFigures.forEach { draw("•  $it", body, 3f, 8) }; y += 9f }
            if (s.followUps.isNotEmpty()) { draw(if (af) "Opvolging" else "Follow-ups", heading); s.followUps.forEach { draw("•  $it", body, 3f, 8) }; y += 9f }
        }
        draw(if (af) "Transkripsie" else "Transcript", heading)
        m.utterances.forEach { u ->
            draw("${formatDuration(u.startMs)}   ${m.speakerName(u.speaker)}", muted, 1f)
            draw(u.text, body, 8f)
        }
        doc.finishPage(page)
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)

    fun share(ctx: Context, m: Meeting, includePdf: Boolean, includeAudio: Boolean) {
        val uris = ArrayList<Uri>()
        if (includePdf) uris += uri(ctx, pdf(ctx, m))
        if (includeAudio) {
            val audio = MetingsApp.instance.repo.audio(m)
            if (audio.exists()) {
                val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
                val copy = File(dir, safeName(m) + "." + audio.extension)
                audio.copyTo(copy, overwrite = true)
                uris += uri(ctx, copy)
            }
        }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).setType(if (includePdf) "application/pdf" else "audio/*").putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, m.title).putExtra(Intent.EXTRA_TEXT, m.summary?.overview ?: m.title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(intent, m.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareText(ctx: Context, m: Meeting) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, m.title).putExtra(Intent.EXTRA_TEXT, asText(m))
        ctx.startActivity(Intent.createChooser(i, m.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Word-compatible document: an HTML file Word opens directly as .doc. */
    fun shareWord(ctx: Context, m: Meeting) {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val f = File(dir, safeName(m) + ".doc")
        val esc = { s: String -> s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") }
        val html = StringBuilder("<html><head><meta charset='utf-8'></head><body style='font-family:Calibri,Arial'>")
        asText(m).lines().forEach { line ->
            if (line.isNotEmpty() && line == line.uppercase() && line.any { it.isLetter() }) html.append("<h3 style='color:#0E8A7E'>").append(esc(line)).append("</h3>")
            else html.append("<p style='margin:2px 0'>").append(esc(line)).append("</p>")
        }
        html.append("</body></html>")
        f.writeText(html.toString())
        val i = Intent(Intent.ACTION_SEND).setType("application/msword").putExtra(Intent.EXTRA_STREAM, uri(ctx, f))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, m.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
