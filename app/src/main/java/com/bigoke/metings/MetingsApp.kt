package com.bigoke.metings

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.bigoke.metings.data.MeetingRepository
import com.bigoke.metings.data.MeetingStatus
import com.bigoke.metings.data.Prefs
import com.bigoke.metings.data.VoiceBank
import com.bigoke.metings.engine.ModelManager
import com.bigoke.metings.engine.Pipeline
import com.bigoke.metings.service.ProcessingService
import com.bigoke.metings.util.EngineGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MetingsApp : Application() {

    lateinit var repo: MeetingRepository
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var models: ModelManager
        private set
    lateinit var pipeline: Pipeline
        private set
    lateinit var voices: VoiceBank
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        EngineGuard.install(this)
        repo = MeetingRepository(this)
        prefs = Prefs(this)
        models = ModelManager(this)
        voices = VoiceBank(this)
        pipeline = Pipeline(this, repo, models, prefs, voices)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_RECORD, "Opname / Recording", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_PROCESS, "Verwerking / Processing", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DONE, "Notule gereed / Minutes ready", NotificationManager.IMPORTANCE_DEFAULT))

        isEngine = getProcessName().endsWith(":engine")
        if (isEngine) return  // the processing process only needs the objects above

        val engineAlive = EngineGuard.engineRunning()
        if (!engineAlive) {
            // If the app was closed while a step was running, count it (the next try uses safe mode).
            EngineGuard.recoverAfterCrash()
        }
        // A meeting left "processing" or "recording" by a crash / reboot is put back in the queue.
        repo.meetings.value.forEach { m ->
            if (m.status == MeetingStatus.RECORDING || (m.status == MeetingStatus.PROCESSING && !engineAlive)) {
                repo.update(m.id) { it.copy(status = MeetingStatus.QUEUED) }
            }
        }
        watchEngine()
    }

    /**
     * Processing runs in a separate process (":engine"), so a crash there can never close the app.
     * This watches it: shows its progress, picks up its results, and restarts a meeting in safe
     * mode if the engine stopped unexpectedly.
     */
    private fun watchEngine() {
        scope.launch {
            while (isActive) {
                val busy = repo.meetings.value.any { it.status == MeetingStatus.PROCESSING || it.status == MeetingStatus.QUEUED }
                runCatching {
                    repo.refreshChanged()
                    val alive = EngineGuard.engineRunning()
                    pipeline.showExternal(if (alive) EngineGuard.readProgress() else null)
                    if (!alive) {
                        val crashed = if (EngineGuard.current() != null) EngineGuard.recoverAfterCrash() else null
                        val stuck = repo.meetings.value.filter { it.status == MeetingStatus.PROCESSING }.map { it.id }
                        (stuck + listOfNotNull(crashed)).distinct().forEach { id ->
                            repo.update(id) { it.copy(status = MeetingStatus.QUEUED) }
                        }
                        val next = repo.meetings.value.firstOrNull { it.status == MeetingStatus.QUEUED }
                        if ((crashed != null || stuck.isNotEmpty()) && next != null && models.coreReady) {
                            runCatching { ProcessingService.enqueue(this@MetingsApp, next.id) }
                        }
                    }
                }
                delay(if (busy) 1000 else 4000)
            }
        }
    }

    var isEngine = false
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CH_RECORD = "record"
        const val CH_PROCESS = "process"
        const val CH_DONE = "done"
        lateinit var instance: MetingsApp
            private set
    }
}
