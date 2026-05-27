package com.mymusic.muy

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews

class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val views = determineLayout(context, options)
            setupClickToOpenApp(context, views)
            setupMediaControls(context, views)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        val views = determineLayout(context, newOptions)
        setupClickToOpenApp(context, views)
        setupMediaControls(context, views)
        appWidgetManager.updateAppWidget(appWidgetId, views)

        val intent = Intent(context, MusicService::class.java).apply { action = MusicService.ACTION_REFRESH_WIDGET }
        context.startService(intent)
    }

    private fun determineLayout(context: Context, options: Bundle): RemoteViews {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

        // STRUKTUR WHEN FIX DARI CHATGPT: PRIORITAS TINGGI DULUAN & THRESHOLD LUAS LAUNCHER
        return when {
            minWidth <= 110 -> {
                RemoteViews(context.packageName, R.layout.widget_music_1x1)
            }
            minHeight >= 160 -> {
                RemoteViews(context.packageName, R.layout.widget_music_2x4)
            }
            minWidth <= 180 -> {
                RemoteViews(context.packageName, R.layout.widget_music_1x2)
            }
            minWidth <= 250 -> {
                RemoteViews(context.packageName, R.layout.widget_music_1x3)
            }
            else -> {
                RemoteViews(context.packageName, R.layout.widget_music_complex) // Default 1x4
            }
        }
    }

    private fun setupClickToOpenApp(context: Context, views: RemoteViews) {
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPI = PendingIntent.getActivity(context, 99, mainIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        views.setOnClickPendingIntent(R.id.widget_click_area, mainPI)
    }

    private fun setupMediaControls(context: Context, views: RemoteViews) {
        val flag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        try { views.setOnClickPendingIntent(R.id.btn_prev, PendingIntent.getService(context, 10, Intent(context, MusicService::class.java).apply { action = MusicService.ACTION_PREV }, flag)) } catch(e: Exception){}
        try { views.setOnClickPendingIntent(R.id.btn_play_pause, PendingIntent.getService(context, 11, Intent(context, MusicService::class.java).apply { action = MusicService.ACTION_TOGGLE }, flag)) } catch(e: Exception){}
        try { views.setOnClickPendingIntent(R.id.btn_next, PendingIntent.getService(context, 12, Intent(context, MusicService::class.java).apply { action = MusicService.ACTION_NEXT }, flag)) } catch(e: Exception){}
    }
}
