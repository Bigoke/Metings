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
        private const val ACTION_CANCEL = "cancel"
        private const val NOTIF_ID = 22

        fun enqueue(ctx: Context, id: String, summaryOnly: Boolean = false) {
            ctx.startForegroundService(
                Intent(ctx, ProcessingService::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_SUMMARY_ONLY, summaryOnly)
            )
        }

        fun cancel(ctx: Context) {
            ctx.startService(Intent(ctx, ProcessingService::class.java).setAction(ACTION_CANCEL))
        }
    }

    private data class Job2(val id: String, val summaryOnly: Boolean)

    private val queue = ConcurrentLinkedQueue<Job2>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var worker: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentTitle = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(this, NOTIF_ID, progressNotification("Preparing…", 0f), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
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
        if (id != null && queue.none { it.id == id }) queue.add(Job2(id, intent.getBooleanExtra(EXTRA_SUMMARY_ONLY, false)))
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
                val m = app.repo.get(job.id) ?: continue
                currentTitle = m.title
                app.pipeline.process(job.id, job.summaryOnly)
                val done = app.repo.get(job.id)
                if (done?.status == MeetingStatus.READY) notifyDone(done.id, done.title)
            }
            progressJob.cancel()
            wakeLock?.let { if (it.isHeld) it.release() }
            ServiceCompat.stopForeground(this@ProcessingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
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
            .setContentTitle(currentTitle.ifEmpty { "Processing meeting" })
            .setContentText("$stage · ${(overall * 100).toInt()}%")
            .setProgress(100, (overall * 100).toInt(), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent(null))
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun notifyDone(id: String, title: String) {
        val n = NotificationCompat.Builder(this, MetingsApp.CH_DONE)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Meeting ready")
            .setContentText("$title — transcript and summary are ready")
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
