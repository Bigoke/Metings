package com.bigoke.metings

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.bigoke.metings.data.MeetingRepository
import com.bigoke.metings.data.MeetingStatus
import com.bigoke.metings.data.Prefs
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

    override fun onCreate() {
        super.onCreate()
        instance = this
        repo = MeetingRepository(this)
        prefs = Prefs(this)
        models = ModelManager(this)
        pipeline = Pipeline(this, repo, models, prefs)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_RECORD, "Recording", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_PROCESS, "Processing meetings", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DONE, "Meeting ready", NotificationManager.IMPORTANCE_DEFAULT))

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
