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
        com.bigoke.metings.util.EngineGuard.install(this)
        repo = MeetingRepository(this)
        prefs = Prefs(this)
        models = ModelManager(this)
        voices = VoiceBank(this)
        pipeline = Pipeline(this, repo, models, prefs, voices)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_RECORD, "Opname / Recording", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_PROCESS, "Verwerking / Processing", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DONE, "Notule gereed / Minutes ready", NotificationManager.IMPORTANCE_DEFAULT))

        // If the app was closed while a step was running, count it (the next try uses safe mode).
        com.bigoke.metings.util.EngineGuard.recoverAfterCrash()
        // A meeting left "processing" or "recording" by a crash / reboot is put back in the queue.
        repo.meetings.value.forEach { m ->
            if (m.status == MeetingStatus.PROCESSING || m.status == MeetingStatus.RECORDING) {
                repo.update(m.id) { it.copy(status = MeetingStatus.QUEUED) }
            }
        }
    }

    companion object {
        const val CH_RECORD = "record"
        const val CH_PROCESS = "process"
        const val CH_DONE = "done"
        lateinit var instance: MetingsApp
            private set
    }
}
