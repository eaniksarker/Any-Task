package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import com.example.AnyTaskApplication
import com.example.MainActivity
import com.example.R
import com.example.data.local.AnyTaskDatabase
import com.example.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class AnyTaskWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId)
        @Suppress("DEPRECATION")
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_task_list)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = PreferencesManager(context)
        appWidgetIds.forEach { prefs.deleteWidgetConfig(it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE_TASK -> {
                val taskId = intent.getStringExtra(EXTRA_WIDGET_TASK_ID) ?: return
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val app = context.applicationContext as? AnyTaskApplication
                        app?.repository?.toggleTaskCompletion(taskId, completed = true)
                        notifyWidgetDataChanged(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_REFRESH -> {
                notifyWidgetDataChanged(context)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_TASK = "com.example.widget.ACTION_TOGGLE_TASK"
        const val ACTION_REFRESH = "com.example.widget.ACTION_REFRESH"
        const val EXTRA_WIDGET_TASK_ID = "extra_widget_task_id"

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = PreferencesManager(context)
            val listId = prefs.getWidgetListId(appWidgetId)
            val darkGlass = prefs.isWidgetDarkGlass(appWidgetId)

            val options = runCatching { appWidgetManager.getAppWidgetOptions(appWidgetId) }.getOrNull()
            val minWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) ?: 250
            val titleSizeSp = when {
                minWidthDp < 190 -> 14f
                minWidthDp >= 310 -> 17f
                else -> 16f
            }
            val subtitleSizeSp = when {
                minWidthDp < 190 -> 11f
                minWidthDp >= 310 -> 12.5f
                else -> 11.5f
            }

            val listTitle = if (listId == null) {
                context.getString(R.string.all_lists_label)
            } else {
                runBlocking(Dispatchers.IO) {
                    AnyTaskDatabase.getInstance(context).taskListDao().getById(listId)?.name
                } ?: context.getString(R.string.all_lists_label)
            }

            val views = RemoteViews(context.packageName, R.layout.widget_anytask)
            views.setInt(
                R.id.widget_root,
                "setBackgroundResource",
                if (darkGlass) R.drawable.widget_glass_bg_dark else R.drawable.widget_glass_bg_light
            )
            views.setTextColor(
                R.id.widget_title,
                if (darkGlass) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
            )
            views.setTextColor(
                R.id.widget_subtitle,
                if (darkGlass) Color.parseColor("#94A3B8") else Color.parseColor("#475569")
            )
            views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, titleSizeSp)
            views.setTextViewTextSize(R.id.widget_subtitle, TypedValue.COMPLEX_UNIT_SP, subtitleSizeSp)
            views.setTextViewText(R.id.widget_subtitle, listTitle)

            // RemoteViewsService adapter
            val serviceIntent = Intent(context, AnyTaskWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            @Suppress("DEPRECATION")
            views.setRemoteAdapter(R.id.widget_task_list, serviceIntent)
            views.setEmptyView(R.id.widget_task_list, R.id.widget_empty_view)

            // Header tap opens main app
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setOnClickPendingIntent(
                R.id.widget_header_tap,
                PendingIntent.getActivity(
                    context,
                    appWidgetId * 10 + 1,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // Quick add '+' button opens quick-add modal
            val quickAddIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_QUICK_ADD, true)
                if (listId != null) putExtra(MainActivity.EXTRA_QUICK_ADD_LIST_ID, listId)
            }
            views.setOnClickPendingIntent(
                R.id.widget_add_button,
                PendingIntent.getActivity(
                    context,
                    appWidgetId * 10 + 2,
                    quickAddIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // Refresh button
            val refreshIntent = Intent(context, AnyTaskWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            views.setOnClickPendingIntent(
                R.id.widget_refresh_button,
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 10 + 3,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // Item click template
            val itemTemplateIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setPendingIntentTemplate(
                R.id.widget_task_list,
                PendingIntent.getActivity(
                    context,
                    appWidgetId * 10 + 4,
                    itemTemplateIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun notifyWidgetDataChanged(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, AnyTaskWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(componentName)
            if (ids.isNotEmpty()) {
                @Suppress("DEPRECATION")
                manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_task_list)
                for (id in ids) {
                    updateWidget(context, manager, id)
                }
            }
        }
    }
}
