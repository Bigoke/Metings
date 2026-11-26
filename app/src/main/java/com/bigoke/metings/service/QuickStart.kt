package com.bigoke.metings.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.bigoke.metings.MainActivity
import com.bigoke.metings.R
import com.bigoke.metings.util.Ui

private fun quickRecordIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
    ctx, 77,
    Intent(ctx, MainActivity::class.java).putExtra("open", "quickRecord")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/** Quick-settings tile: opens Notule and starts recording straight away. */
class QuickRecordTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            label = Ui.t("Record meeting", "Neem vergadering op")
            state = if (RecordingService.state.value.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(quickRecordIntent(this))
        else @Suppress("DEPRECATION") startActivityAndCollapse(
            Intent(this, MainActivity::class.java).putExtra("open", "quickRecord").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Home-screen widget with a single Record button. */
class RecordWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_record)
            views.setTextViewText(R.id.widget_label, Ui.t("Record meeting", "Neem vergadering op"))
            views.setOnClickPendingIntent(R.id.widget_root, quickRecordIntent(context))
            manager.updateAppWidget(id, views)
        }
    }
}
