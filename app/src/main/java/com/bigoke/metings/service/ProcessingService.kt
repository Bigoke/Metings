package com.bigoke.metings.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.bigoke.metings.MainActivity
import com.bigoke.metings.MetingsApp
import com.bigoke.metings.R
import com.bigoke.metings.data.MeetingStatus
import com.bigoke.metings.util.Ui
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

/** Runs the processing pipeline in the background, one meeting at a time. */
class ProcessingService : Service() {

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_SUMMARY_ONLY = "summaryOnly"
        private const val EXTRA_ASK = "ask"
        private const val ACTION_CANCEL = "cancel"
        private const val NOTIF_ID = 22

        fun enqueue(ctx: Context, id: String, summaryOnly: Boolean = false) {
            ctx.startForegroundService(
                Intent(ctx, ProcessingService::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_SUMMARY_ONLY, summaryOnly)
            )
        }

        /** Ask Notule a question about a meeting; the answer is saved in the meeting. */
        fun ask(ctx: Context, id: String, question: String) {
            ctx.startForegroundService(Intent(ctx, ProcessingService::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_ASK, question))
        }

        fun cancel(ctx: Context) {
            ctx.startService(Intent(ctx, ProcessingService::class.java).setAction(ACTION_CANCEL))
        }
    }

    private data class Job2(val id: String, val summaryOnly: Boolean, val ask: String? = null)

    private val queue = ConcurrentLinkedQueue<Job2>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var worker: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentTitle = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(this, NOTIF_ID, progressNotification(Ui.t("Preparing…", "Berei voor…"), 0f), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        val app = application as MetingsApp
        if (intent?.action == ACTION_CANCEL) {
            queue.clear()
            app.pipeline.cancel()
            if (worker?.isActive != true) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }
        val id = intent?.getStringExtra(EXTRA_ID)
        app.repo.reload()
        val question = intent?.getStringExtra(EXTRA_ASK)
        if (id != null && question != null) queue.add(Job2(id, false, question))
        else if (id != null && queue.none { it.id == id && it.ask == null }) queue.add(Job2(id, intent.getBooleanExtra(EXTRA_SUMMARY_ONLY, false)))
        // Also pick up anything else waiting (e.g. after a reboot).
        app.repo.meetings.value.filter { it.status == MeetingStatus.QUEUED && queue.none { q -> q.id == it.id } }
            .forEach { queue.add(Job2(it.id, false)) }
        if (worker?.isActive != true) startWorker()
        return START_NOT_STICKY
    }

    private fun startWorker() {
        val app = application as MetingsApp
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "metings:process").apply { acquire(4 * 60 * 60 * 1000L) }
        val progressJob = scope.launch {
            app.pipeline.progress.collectLatest { map ->
                val p = map.values.firstOrNull() ?: return@collectLatest
                getSystemService(NotificationManager::class.java).notify(NOTIF_ID, progressNotification(p.stage, p.overall))
            }
        }
        worker = scope.launch(Dispatchers.IO) {
            while (true) {
                val job = queue.poll() ?: break
                app.repo.reload()  // the app process may have changed meetings since this process started
                val m = app.repo.get(job.id) ?: continue
                currentTitle = m.title
                if (job.ask != null) {
                    answer(app, m.id, job.ask)
                    continue
                }
                app.pipeline.process(job.id, job.summaryOnly)
                val done = app.repo.get(job.id)
                if (done?.status == MeetingStatus.READY) notifyDone(done.id, done.title)
            }
            progressJob.cancel()
            wakeLock?.let { if (it.isHeld) it.release() }
            ServiceCompat.stopForeground(this@ProcessingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            // End the engine process so the next job starts with fresh settings and free memory.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 800)
        }
    }

    private fun answer(app: MetingsApp, id: String, question: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, progressNotification(Ui.t("Thinking about your question…", "Dink oor jou vraag…"), 0.5f))
        val s = app.prefs.settings.value
        val llm = if (app.models.isReady(com.bigoke.metings.engine.ModelKind.SUMMARY)) app.models.file(com.bigoke.metings.engine.ModelKind.SUMMARY) else null
        com.bigoke.metings.util.EngineGuard.begin(id, com.bigoke.metings.util.EngineGuard.ASK)
        val summarizer = com.bigoke.metings.engine.Summarizer(this, llm, s.useGpuForSummary)
        val (text, engine) = try {
            val m = app.repo.get(id) ?: return
            summarizer.ask(m, question)
        } catch (t: Throwable) {
            Ui.t("Sorry, I couldn't answer that right now.", "Jammer, ek kon dit nie nou beantwoord nie.") to "error"
        } finally {
            summarizer.close()
            com.bigoke.metings.util.EngineGuard.end()
        }
        app.repo.update(id) { mm ->
            val i = mm.qa.indexOfLast { it.pending && it.question == question }
            if (i < 0) mm.copy(qa = mm.qa + com.bigoke.metings.data.QaItem(question, text, pending = false, engine = engine))
            else mm.copy(qa = mm.qa.mapIndexed { k, q -> if (k == i) q.copy(answer = text, pending = false, engine = engine) else q })
        }
    }

    private fun openIntent(id: String?): PendingIntent = PendingIntent.getActivity(
        this, id?.hashCode() ?: 0,
        Intent(this, MainActivity::class.java).apply { if (id != null) putExtra("meeting", id) },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(stage: String, overall: Float): Notification {
        val stop = PendingIntent.getService(
            this, 3, Intent(this, ProcessingService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, MetingsApp.CH_PROCESS)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(currentTitle.ifEmpty { Ui.t("Processing meeting", "Verwerk vergadering") })
            .setContentText("$stage · ${(overall * 100).toInt()}%")
            .setProgress(100, (overall * 100).toInt(), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent(null))
            .addAction(0, Ui.t("Stop", "Stop"), stop)
            .build()
    }

    private fun notifyDone(id: String, title: String) {
        val n = NotificationCompat.Builder(this, MetingsApp.CH_DONE)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(Ui.t("Minutes ready", "Notule gereed"))
            .setContentText(title + Ui.t(" — transcript and summary are ready", " — transkripsie en opsomming is gereed"))
            .setAutoCancel(true)
            .setContentIntent(openIntent(id))
            .build()
        getSystemService(NotificationManager::class.java).notify(id.hashCode(), n)
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
