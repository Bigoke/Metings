package com.bigoke.metings.util

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.bigoke.metings.ui.theme.speakerColor
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.bigoke.metings.MetingsApp
import com.bigoke.metings.data.ActionItem
import com.bigoke.metings.data.EntityType
import com.bigoke.metings.data.Meeting
import com.bigoke.metings.data.Templates
import com.bigoke.metings.ui.entityLabel
import com.bigoke.metings.ui.formatDuration
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Exporter {

    fun logoFile(ctx: Context) = File(ctx.filesDir, "company_logo.png")

    private fun safeName(m: Meeting) = m.title.replace(Regex("""[^\p{L}\d _-]"""), "").trim().ifEmpty { "notule" }.take(60)

    private fun docAf(m: Meeting) = (m.summary?.language ?: m.language) == "af"

    private fun date(ms: Long, af: Boolean) =
        SimpleDateFormat("EEEE d MMMM yyyy", if (af) Locale("af", "ZA") else Locale("en", "ZA")).format(Date(ms))

    private fun time(ms: Long) = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(ms))

    fun asText(m: Meeting): String {
        val af = docAf(m)
        val sb = StringBuilder()
        val company = MetingsApp.instance.prefs.settings.value.companyName
        if (company.isNotBlank()) sb.appendLine(company)
        sb.appendLine((if (af) "NOTULE: " else "MINUTES: ") + m.title)
        sb.appendLine(date(m.createdAt, af) + " · " + time(m.createdAt) + " · " + formatDuration(m.durationMs))
        sb.appendLine((if (af) "Teenwoordig: " else "Present: ") + m.speakers.joinToString(", ") { it.name })
        sb.appendLine()
        m.summary?.let { s ->
            sb.appendLine(if (af) "OPSOMMING" else "SUMMARY").appendLine(s.overview).appendLine()
            if (s.decisions.isNotEmpty()) { sb.appendLine(if (af) "BESLUITE" else "DECISIONS"); s.decisions.forEach { sb.appendLine("• $it") }; sb.appendLine() }
            if (s.extra.isNotEmpty()) { sb.appendLine(s.extraTitle.uppercase()); s.extra.forEach { sb.appendLine("• $it") }; sb.appendLine() }
            if (s.actions.isNotEmpty()) {
                sb.appendLine(if (af) "AKSIEPUNTE" else "ACTION ITEMS")
                s.actions.forEach { a -> sb.appendLine(actionLine(a)) }
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
        m.utterances.forEach { u -> sb.appendLine("[${formatDuration(u.startMs)}] ${m.speakerName(u.speaker)}: ${u.text}") }
        return sb.toString()
    }

    private fun actionLine(a: ActionItem): String {
        val sb = StringBuilder(if (a.done) "☑ " else "☐ ").append(a.text)
        if (a.owner.isNotBlank()) sb.append(" — ").append(a.owner)
        if (a.due.isNotBlank()) sb.append(" (").append(a.due).append(")")
        return sb.toString()
    }

    // --------------------------------------------------------------------------- branded PDF minutes

    private class Pdf(val ctx: Context, val footerLeft: String, val pageLabel: String) {
        val doc = PdfDocument()
        val w = 595; val h = 842; val margin = 50f
        val width = w - margin * 2
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        var y = 0f
        val ink = Color.rgb(20, 33, 61)
        val teal = Color.rgb(14, 138, 126)
        val grey = Color.rgb(100, 106, 120)
        val body = TextPaint().apply { textSize = 10.5f; color = Color.rgb(30, 32, 38); isAntiAlias = true }
        val bold = TextPaint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val small = TextPaint(body).apply { textSize = 8.5f; color = grey }
        val heading = TextPaint(body).apply { textSize = 11.5f; typeface = Typeface.DEFAULT_BOLD; color = Color.WHITE; letterSpacing = 0.06f }
        val line = Paint().apply { color = Color.rgb(210, 214, 222); strokeWidth = 0.8f }

        init { newPage() }

        fun newPage() {
            if (pageNo > 0) { footer(); doc.finishPage(page) }
            page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, ++pageNo).create())
            y = margin
        }

        fun footer() {
            val c = page.canvas
            c.drawLine(margin, h - 36f, w - margin, h - 36f, line)
            c.drawText(footerLeft, margin, h - 24f, small)
            val p = "$pageLabel $pageNo"
            c.drawText(p, w - margin - small.measureText(p), h - 24f, small)
        }

        fun layout(text: CharSequence, paint: TextPaint, width: Float): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(10))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(1.5f, 1f).build()

        fun ensure(height: Float) { if (y + height > h - margin - 20) newPage() }

        fun text(t: CharSequence, paint: TextPaint = body, indent: Float = 0f, after: Float = 5f) {
            val l = layout(t, paint, width - indent)
            var i = 0
            while (i < l.lineCount) {
                val lh = (l.getLineBottom(i) - l.getLineTop(i)).toFloat()
                ensure(lh)
                val c = page.canvas
                c.save()
                c.translate(margin + indent, y - l.getLineTop(i))
                c.clipRect(0f, l.getLineTop(i).toFloat(), width - indent, l.getLineBottom(i).toFloat())
                l.draw(c)
                c.restore()
                y += lh; i++
            }
            y += after
        }

        fun sectionBar(title: String) {
            ensure(60f)
            y += 6f
            val c = page.canvas
            val p = Paint().apply { color = ink; isAntiAlias = true }
            c.drawRoundRect(RectF(margin, y, w - margin, y + 20f), 3f, 3f, p)
            c.drawText(title.uppercase(), margin + 8f, y + 14f, heading)
            y += 28f
        }

        fun bullet(t: String) {
            val c0 = y
            text(t, body, 14f, 3f)
            val c = page.canvas
            if (y > c0) c.drawCircle(margin + 5f, c0 + 7f, 1.8f, Paint().apply { color = teal; isAntiAlias = true })
        }

        /** A simple table; [weights] are relative column widths. */
        fun table(headers: List<String>, rows: List<List<String>>, weights: List<Float>) {
            val total = weights.sum()
            val cols = weights.map { it / total * width }
            fun row(cells: List<String>, paint: TextPaint, fill: Int?) {
                val layouts = cells.mapIndexed { i, t -> layout(t, paint, cols[i] - 10f) }
                val rh = (layouts.maxOf { it.height } + 10).toFloat()
                ensure(rh)
                val c = page.canvas
                if (fill != null) c.drawRect(margin, y, w - margin, y + rh, Paint().apply { color = fill })
                var x = margin
                layouts.forEachIndexed { i, l ->
                    c.save(); c.translate(x + 5f, y + 5f); l.draw(c); c.restore()
                    x += cols[i]
                }
                c.drawLine(margin, y + rh, w - margin, y + rh, line)
                y += rh
            }
            row(headers, bold, Color.rgb(237, 239, 243))
            rows.forEach { row(it, body, null) }
            y += 8f
        }

        fun finish(out: File) {
            footer(); doc.finishPage(page)
            out.outputStream().use { doc.writeTo(it) }
            doc.close()
        }
    }

    fun pdf(ctx: Context, m: Meeting, includeTranscript: Boolean = true): File {
        val af = docAf(m)
        val settings = MetingsApp.instance.prefs.settings.value
        val company = settings.companyName.trim()
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val out = File(dir, safeName(m) + ".pdf")
        val pdf = Pdf(ctx, (if (company.isNotEmpty()) "$company · " else "") + (if (af) "Notule opgestel met Notule" else "Minutes prepared with Notule"),
            if (af) "Bladsy" else "Page")

        // Letterhead
        val c = pdf.page.canvas
        var textX = pdf.margin
        val logo = logoFile(ctx)
        if (logo.exists()) runCatching {
            val bmp = BitmapFactory.decodeFile(logo.absolutePath)
            val hgt = 48f
            val wid = hgt * bmp.width / bmp.height
            c.drawBitmap(bmp, null, RectF(pdf.margin, pdf.y, pdf.margin + wid, pdf.y + hgt), Paint(Paint.FILTER_BITMAP_FLAG))
            textX = pdf.margin + wid + 14f
        }
        val coPaint = TextPaint(pdf.bold).apply { textSize = 15f; color = pdf.ink }
        val docTitle = TextPaint(pdf.bold).apply { textSize = 22f; color = pdf.teal; letterSpacing = 0.12f }
        if (company.isNotEmpty()) c.drawText(company, textX, pdf.y + 18f, coPaint)
        c.drawText(if (af) "NOTULE" else "MINUTES", textX, pdf.y + 44f, docTitle)
        pdf.y += 60f
        c.drawRect(pdf.margin, pdf.y, pdf.w - pdf.margin, pdf.y + 2.5f, Paint().apply { color = pdf.teal })
        pdf.y += 14f
        pdf.text(m.title, TextPaint(pdf.bold).apply { textSize = 16f; color = pdf.ink }, after = 8f)

        val tpl = Templates.get(m.template)
        val details = buildList {
            add(listOf(if (af) "Datum" else "Date", date(m.createdAt, af)))
            add(listOf(if (af) "Tyd" else "Time", time(m.createdAt) + "  (" + formatDuration(m.durationMs) + ")"))
            if (tpl.id != "general") add(listOf(if (af) "Soort" else "Type", if (af) tpl.af else tpl.en))
            add(listOf(if (af) "Teenwoordig" else "Present", m.speakers.joinToString(", ") { it.name }))
            if (settings.preparedBy.isNotBlank()) add(listOf(if (af) "Opgestel deur" else "Prepared by", settings.preparedBy))
            if (includeTranscript && m.utterances.isNotEmpty()) add(listOf(if (af) "Transkripsie" else "Transcript",
                if (af) "Volledige transkripsie aan die einde aangeheg" else "Full transcript attached at the end"))
        }
        pdf.table(listOf(if (af) "Besonderheid" else "Item", ""), details, listOf(1f, 3.2f))

        m.summary?.let { s ->
            pdf.sectionBar(if (af) "Opsomming" else "Summary")
            pdf.text(s.overview.ifBlank { "—" }, after = 8f)
            if (s.decisions.isNotEmpty()) { pdf.sectionBar(if (af) "Besluite" else "Decisions"); s.decisions.forEach { pdf.bullet(it) } }
            if (s.extra.isNotEmpty()) { pdf.sectionBar(s.extraTitle); s.extra.forEach { pdf.bullet(it) } }
            if (s.actions.isNotEmpty()) {
                pdf.sectionBar(if (af) "Aksiepunte" else "Action items")
                pdf.table(
                    listOf("#", if (af) "Taak" else "Task", if (af) "Verantwoordelik" else "Responsible", if (af) "Teen" else "Due", "✓"),
                    s.actions.mapIndexed { i, a -> listOf("${i + 1}", a.text, a.owner, a.due, if (a.done) "✓" else "") },
                    listOf(0.35f, 4f, 1.6f, 1.2f, 0.4f),
                )
            }
            if (s.keyFigures.isNotEmpty()) { pdf.sectionBar(if (af) "Sleutelsyfers" else "Key figures"); s.keyFigures.forEach { pdf.bullet(it) } }
            if (s.followUps.isNotEmpty()) { pdf.sectionBar(if (af) "Opvolging" else "Follow-ups"); s.followUps.forEach { pdf.bullet(it) } }
        }

        // Signatures
        pdf.ensure(110f)
        pdf.y += 24f
        val sc = pdf.page.canvas
        val colW = (pdf.width - 30f) / 2
        listOf(if (af) "Voorsitter" else "Chairperson", if (af) "Sekretaris" else "Secretary").forEachIndexed { i, label ->
            val x = pdf.margin + i * (colW + 30f)
            sc.drawLine(x, pdf.y + 30f, x + colW, pdf.y + 30f, Paint().apply { color = pdf.ink; strokeWidth = 0.8f })
            sc.drawText(label, x, pdf.y + 44f, pdf.small)
            sc.drawLine(x, pdf.y + 74f, x + colW * 0.55f, pdf.y + 74f, Paint().apply { color = pdf.ink; strokeWidth = 0.8f })
            sc.drawText(if (af) "Datum" else "Date", x, pdf.y + 88f, pdf.small)
        }
        pdf.y += 100f

        if (includeTranscript && m.utterances.isNotEmpty()) {
            pdf.newPage()
            pdf.sectionBar(if (af) "Volledige transkripsie" else "Full transcript")
            pdf.text(
                (if (af) "Woord vir woord, met tyd en spreker. " else "Word for word, with time and speaker. ") +
                    m.speakers.joinToString(" · ") { it.name } + " · " + formatDuration(m.durationMs),
                pdf.small, after = 10f,
            )
            m.utterances.forEach { u ->
                val sp = m.speaker(u.speaker)
                val colour = speakerColor(sp.colorIndex).toArgb()
                val head = android.text.SpannableStringBuilder()
                val n0 = head.length
                head.append(sp.name)
                head.setSpan(android.text.style.ForegroundColorSpan(colour), n0, head.length, 0)
                head.setSpan(android.text.style.StyleSpan(Typeface.BOLD), n0, head.length, 0)
                val t0 = head.length
                head.append("   " + formatDuration(u.startMs))
                head.setSpan(android.text.style.ForegroundColorSpan(pdf.grey), t0, head.length, 0)
                pdf.ensure(40f)
                pdf.text(head, TextPaint(pdf.small).apply { textSize = 9f }, after = 1f)
                // A thin speaker-coloured rule next to the paragraph, like the app's transcript.
                val y0 = pdf.y
                val pageBefore = pdf.pageNo
                pdf.text(u.text, pdf.body, indent = 8f, after = 8f)
                if (pdf.pageNo == pageBefore) pdf.page.canvas.drawRect(pdf.margin, y0 + 1f, pdf.margin + 2.2f, pdf.y - 9f, Paint().apply { color = colour })
            }
        }
        pdf.finish(out)
        return out
    }

    // --------------------------------------------------------------------------- sharing

    private fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)

    fun share(ctx: Context, m: Meeting, includePdf: Boolean, includeAudio: Boolean) {
        val uris = ArrayList<Uri>()
        if (includePdf) uris += uri(ctx, pdf(ctx, m))
        if (includeAudio) {
            val audio = MetingsApp.instance.repo.audio(m)
            if (audio.exists()) {
                val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
                val copy = File(dir, safeName(m) + " (" + Ui.t("audio", "klank") + ")." + audio.extension)
                audio.copyTo(copy, overwrite = true)
                uris += uri(ctx, copy)
            }
        }
        if (uris.isEmpty()) return
        startShare(ctx, uris, m.title, m.summary?.overview ?: m.title)
    }

    /**
     * Shares one or more files. Every file is put in the ClipData of both the share intent and the
     * chooser, so the receiving app is allowed to read all of them (not only the first).
     */
    private fun startShare(ctx: Context, uris: List<Uri>, subject: String, text: String) {
        val types = uris.map { ctx.contentResolver.getType(it) ?: "application/octet-stream" }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).setType(types[0]).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            val common = types.map { it.substringBefore('/') }.distinct().singleOrNull()?.let { "$it/*" } ?: "*/*"
            Intent(Intent.ACTION_SEND_MULTIPLE).setType(common).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        val clip = android.content.ClipData.newUri(ctx.contentResolver, subject, uris[0])
        uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
        intent.clipData = clip
        intent.putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(intent, subject)
        chooser.clipData = clip
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }

    private fun audioCopy(ctx: Context, m: Meeting): File? {
        val audio = MetingsApp.instance.repo.audio(m)
        if (!audio.exists()) return null
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        return File(dir, safeName(m) + " - " + Ui.t("audio", "klank") + "." + audio.extension).also { audio.copyTo(it, overwrite = true) }
    }

    /** PDF + audio in one e-mail (e-mail apps accept several attachments; chat apps often don't). */
    fun emailBoth(ctx: Context, m: Meeting) {
        val uris = arrayListOf(uri(ctx, pdf(ctx, m)))
        audioCopy(ctx, m)?.let { uris += uri(ctx, it) }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            .putExtra(Intent.EXTRA_SUBJECT, (if (docAf(m)) "Notule: " else "Minutes: ") + m.title)
            .putExtra(Intent.EXTRA_TEXT, m.summary?.overview ?: m.title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val clip = android.content.ClipData.newUri(ctx.contentResolver, m.title, uris[0])
        uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
        intent.clipData = clip
        // Only e-mail apps
        intent.selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        runCatching { ctx.startActivity(intent) }.onFailure {
            intent.selector = null
            startShareIntent(ctx, intent, m.title, clip)
        }
    }

    private fun startShareIntent(ctx: Context, intent: Intent, title: String, clip: android.content.ClipData) {
        val chooser = Intent.createChooser(intent, title)
        chooser.clipData = clip
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }

    fun sharePdf(ctx: Context, m: Meeting) = startShare(ctx, listOf(uri(ctx, pdf(ctx, m))), m.title, m.summary?.overview ?: m.title)

    fun shareAudio(ctx: Context, m: Meeting): Boolean {
        val f = audioCopy(ctx, m) ?: return false
        startShare(ctx, listOf(uri(ctx, f)), m.title, m.title)
        return true
    }

    /** Saves the PDF minutes and the audio in Download/Notule on the phone. Returns the folder shown to the user. */
    fun saveToDownloads(ctx: Context, m: Meeting): String {
        val folder = "Notule/" + safeName(m)
        val files = buildList {
            add(pdf(ctx, m) to "application/pdf")
            audioCopy(ctx, m)?.let { add(it to (if (it.extension == "wav") "audio/wav" else "audio/mp4")) }
        }
        val resolver = ctx.contentResolver
        for ((f, mime) in files) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, f.name)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, mime)
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/" + folder)
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }
            val item = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: continue
            resolver.openOutputStream(item)?.use { out -> f.inputStream().use { it.copyTo(out) } }
            values.clear(); values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(item, values, null, null)
        }
        return "Download/$folder"
    }

    fun shareText(ctx: Context, m: Meeting) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, m.title).putExtra(Intent.EXTRA_TEXT, asText(m))
        ctx.startActivity(Intent.createChooser(i, m.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Sends one person their action items (WhatsApp, e-mail, SMS… via the share sheet). */
    fun shareTasks(ctx: Context, m: Meeting, owner: String) {
        val af = docAf(m)
        val tasks = m.summary?.actions.orEmpty().filter { !it.done && (owner.isEmpty() || it.owner.equals(owner, true)) }
        if (tasks.isEmpty()) return
        val greeting = if (owner.isNotBlank()) (if (af) "Hallo $owner,\n\n" else "Hi $owner,\n\n") else ""
        val body = greeting +
            (if (af) "Jou aksiepunte uit \"${m.title}\" (${date(m.createdAt, true)}):\n" else "Your action items from \"${m.title}\" (${date(m.createdAt, false)}):\n") +
            tasks.joinToString("\n") { "• " + it.text + if (it.due.isNotBlank()) " — ${if (af) "teen" else "by"} ${it.due}" else "" } +
            (if (af) "\n\nDankie!" else "\n\nThank you!")
        val i = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, (if (af) "Aksiepunte: " else "Action items: ") + m.title)
            .putExtra(Intent.EXTRA_TEXT, body)
        ctx.startActivity(Intent.createChooser(i, owner.ifBlank { m.title }).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Word-compatible document: an HTML file Word opens directly as .doc. */
    fun shareWord(ctx: Context, m: Meeting) {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val f = File(dir, safeName(m) + ".doc")
        val esc = { s: String -> s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") }
        val html = StringBuilder("<html><head><meta charset='utf-8'></head><body style='font-family:Calibri,Arial'>")
        asText(m).lines().forEach { l ->
            if (l.isNotEmpty() && l == l.uppercase() && l.any { it.isLetter() }) html.append("<h3 style='color:#0E8A7E'>").append(esc(l)).append("</h3>")
            else html.append("<p style='margin:2px 0'>").append(esc(l)).append("</p>")
        }
        html.append("</body></html>")
        f.writeText(html.toString())
        val i = Intent(Intent.ACTION_SEND).setType("application/msword").putExtra(Intent.EXTRA_STREAM, uri(ctx, f))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, m.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareDiagnostics(ctx: Context, report: String) {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val f = File(dir, "notule-diagnostics.txt")
        f.writeText(report)
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri(ctx, f))
            .putExtra(Intent.EXTRA_SUBJECT, "Notule diagnostic report").putExtra(Intent.EXTRA_TEXT, report.take(3000))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Notule").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
