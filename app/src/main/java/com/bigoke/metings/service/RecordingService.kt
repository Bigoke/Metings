package com.bigoke.metings.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.bigoke.metings.MainActivity
import com.bigoke.metings.MetingsApp
import com.bigoke.metings.R
import com.bigoke.metings.data.Meeting
import com.bigoke.metings.data.MeetingStatus
import com.bigoke.metings.engine.SAMPLE_RATE
import com.bigoke.metings.engine.WavWriter
import com.bigoke.metings.util.Ui
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

data class RecorderState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val meetingId: String? = null,
    val elapsedMs: Long = 0,
    val level: Float = 0f,
    val levels: List<Float> = emptyList(),
    val bookmarks: List<Long> = emptyList(),
)

/** Records 16 kHz mono audio in the background (screen off is fine) as a foreground service. */
class RecordingService : Service() {

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_PAUSE = "pause"
        private const val ACTION_RESUME = "resume"
        private const val ACTION_STOP = "stop"
        private const val ACTION_BOOKMARK = "bookmark"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_LANG = "lang"
        private const val EXTRA_SPEAKERS = "speakers"
        private const val EXTRA_FOLDER = "folder"
        private const val EXTRA_TEMPLATE = "template"
        private const val NOTIF_ID = 11

        private val _state = MutableStateFlow(RecorderState())
        val state: StateFlow<RecorderState> = _state

        fun start(ctx: Context, title: String, language: String, expectedSpeakers: Int, folder: String = "", template: String = "general") {
            ctx.startForegroundService(Intent(ctx, RecordingService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_LANG, language).putExtra(EXTRA_SPEAKERS, expectedSpeakers)
                .putExtra(EXTRA_FOLDER, folder).putExtra(EXTRA_TEMPLATE, template))
        }
        fun pause(ctx: Context) = send(ctx, ACTION_PAUSE)
        fun resume(ctx: Context) = send(ctx, ACTION_RESUME)
        fun stop(ctx: Context) = send(ctx, ACTION_STOP)
        fun bookmark(ctx: Context) = send(ctx, ACTION_BOOKMARK)
        private fun send(ctx: Context, action: String) {
            ctx.startService(Intent(ctx, RecordingService::class.java).setAction(action))
        }
    }

    @Volatile private var running = false
    @Volatile private var paused = false
    private var thread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var meetingId: String? = null
    private var accumulatedMs = 0L
    private var segmentStart = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (!running) startRecording(
                intent.getStringExtra(EXTRA_TITLE) ?: "",
                intent.getStringExtra(EXTRA_LANG) ?: "af",
                intent.getIntExtra(EXTRA_SPEAKERS, 0),
                intent.getStringExtra(EXTRA_FOLDER) ?: "",
                intent.getStringExtra(EXTRA_TEMPLATE) ?: "general",
            )
            ACTION_PAUSE -> if (running && !paused) {
                paused = true
                accumulatedMs += SystemClock.elapsedRealtime() - segmentStart
                _state.value = _state.value.copy(paused = true)
                updateNotification()
            }
            ACTION_RESUME -> if (running && paused) {
                paused = false
                segmentStart = SystemClock.elapsedRealtime()
                _state.value = _state.value.copy(paused = false)
                updateNotification()
            }
            ACTION_BOOKMARK -> if (running) {
                _state.value = _state.value.copy(bookmarks = _state.value.bookmarks + elapsed())
            }
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun elapsed(): Long = accumulatedMs + if (paused) 0 else SystemClock.elapsedRealtime() - segmentStart

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).putExtra("open", "record"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, MetingsApp.CH_RECORD)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(if (paused) Ui.t("Recording paused", "Opname onderbreek") else Ui.t("Recording meeting", "Neem vergadering op"))
            .setContentText(Ui.t("Tap to open Notule", "Tik om Notule oop te maak"))
            .setContentIntent(open)
            .setOngoing(true)
            .setUsesChronometer(!paused)
            .setWhen(System.currentTimeMillis() - elapsed())
            .addAction(0, Ui.t("Stop", "Stop"), stop)
            .build()
    }

    private fun updateNotification() {
        getSystemService(android.app.NotificationManager::class.java).notify(NOTIF_ID, notification())
    }

    @SuppressLint("MissingPermission")
    private fun startRecording(titleIn: String, language: String, expectedSpeakers: Int, folder: String, template: String) {
        val app = application as MetingsApp
        val id = app.repo.newId()
        meetingId = id
        val now = System.currentTimeMillis()
        val title = titleIn.ifBlank {
            (if (language == "af") "Vergadering " else "Meeting ") + SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(now))
        }
        val wavFile = File(app.repo.folder(id), "audio.wav")
        app.repo.save(Meeting(id = id, title = title, createdAt = now, language = language, status = MeetingStatus.RECORDING, expectedSpeakers = expectedSpeakers, folder = folder, template = template))

        accumulatedMs = 0; paused = false
        segmentStart = SystemClock.elapsedRealtime()
        running = true
        ServiceCompat.startForeground(this, NOTIF_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "metings:record").apply { acquire(6 * 60 * 60 * 1000L) }
        _state.value = RecorderState(active = true, meetingId = id)

        thread = Thread {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf * 4, SAMPLE_RATE),
            )
            val writer = WavWriter(wavFile)
            val buf = ShortArray(SAMPLE_RATE / 10)
            val levels = ArrayDeque<Float>()
            try {
                rec.startRecording()
                var tick = 0L
                while (running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    if (paused) continue
                    writer.write(buf, n)
                    var sum = 0.0
                    var peak = 0
                    for (i in 0 until n) { val v = buf[i].toInt(); sum += v * v; peak = maxOf(peak, abs(v)) }
                    val rms = sqrt(sum / n) / 32768.0
                    val level = (rms * 6).coerceIn(0.0, 1.0).toFloat()
                    levels.addLast(level); if (levels.size > 120) levels.removeFirst()
                    val now2 = SystemClock.elapsedRealtime()
                    if (now2 - tick > 90) {
                        tick = now2
                        _state.value = _state.value.copy(elapsedMs = elapsed(), level = level, levels = levels.toList())
                    }
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
                writer.close()
            }
        }.apply { name = "recorder"; start() }
    }

    private fun stopRecording() {
        if (!running) { stopSelf(); return }
        running = false
        thread?.join(3000)
        thread = null
        val app = application as MetingsApp
        val id = meetingId
        val total = elapsed()
        val marks = _state.value.bookmarks
        if (id != null) {
            app.repo.update(id) { it.copy(status = MeetingStatus.QUEUED, durationMs = total, bookmarksMs = marks) }
            ProcessingService.enqueue(this, id)
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        _state.value = RecorderState(active = false, meetingId = id)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (running) stopRecording()
        super.onDestroy()
    }
}
