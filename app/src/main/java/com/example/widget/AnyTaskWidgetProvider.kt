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
import android.view.View
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
        @Suppress("DEPRECATION")
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_dropdown_listview)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = PreferencesManager(context)
        appWidgetIds.forEach { prefs.deleteWidgetConfig(it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_ITEM_CLICK -> {
                val itemAction = intent.getStringExtra(EXTRA_ITEM_ACTION) ?: ITEM_ACTION_OPEN
                val taskId = intent.getStringExtra(EXTRA_WIDGET_TASK_ID) ?: return
                if (itemAction == ITEM_ACTION_COMPLETE) {
                    completeTaskInPlace(context, taskId)
                } else {
                    val openIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(MainActivity.EXTRA_OPEN_TASK_ID, taskId)
                    }
                    context.startActivity(openIntent)
                }
            }
            ACTION_TOGGLE_TASK -> {
                val taskId = intent.getStringExtra(EXTRA_WIDGET_TASK_ID) ?: return
                completeTaskInPlace(context, taskId)
            }
            ACTION_TOGGLE_LIST_MENU -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
                val prefs = PreferencesManager(context)
                val nextOpen = !prefs.isWidgetDropdownOpen(appWidgetId)
                prefs.setWidgetDropdownOpen(appWidgetId, nextOpen)
                val manager = AppWidgetManager.getInstance(context)
                if (nextOpen) {
                    @Suppress("DEPRECATION")
                    manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_dropdown_listview)
                }
                updateWidget(context, manager, appWidgetId)
            }
            ACTION_SELECT_LIST -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
                val rawListId = intent.getStringExtra(EXTRA_SELECTED_LIST_ID)
                val newListId = rawListId?.takeIf { it.isNotBlank() }
                val prefs = PreferencesManager(context)
                prefs.saveWidgetConfig(appWidgetId, newListId, prefs.isWidgetDarkGlass(appWidgetId))
                prefs.setWidgetDropdownOpen(appWidgetId, false)

                val manager = AppWidgetManager.getInstance(context)
                @Suppress("DEPRECATION")
                manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_task_list)
                @Suppress("DEPRECATION")
                manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_dropdown_listview)
                updateWidget(context, manager, appWidgetId)
            }
            ACTION_REFRESH -> {
                notifyWidgetDataChanged(context)
            }
        }
    }

    private fun completeTaskInPlace(context: Context, taskId: String) {
        val pendingResult: PendingResult? = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? AnyTaskApplication
                app?.repository?.toggleTaskCompletion(taskId, completed = true)
                notifyWidgetDataChanged(context)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_TASK = "com.example.widget.ACTION_TOGGLE_TASK"
        const val ACTION_WIDGET_ITEM_CLICK = "com.example.widget.ACTION_WIDGET_ITEM_CLICK"
        const val ACTION_TOGGLE_LIST_MENU = "com.example.widget.ACTION_TOGGLE_LIST_MENU"
        const val ACTION_SELECT_LIST = "com.example.widget.ACTION_SELECT_LIST"
        const val ACTION_REFRESH = "com.example.widget.ACTION_REFRESH"

        const val EXTRA_WIDGET_TASK_ID = "extra_widget_task_id"
        const val EXTRA_ITEM_ACTION = "extra_item_action"
        const val ITEM_ACTION_COMPLETE = "complete"
        const val ITEM_ACTION_OPEN = "open"
        const val EXTRA_SELECTED_LIST_ID = "extra_selected_list_id"

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = PreferencesManager(context)
            val listId = prefs.getWidgetListId(appWidgetId)
            val darkGlass = prefs.isWidgetDarkGlass(appWidgetId)
            val dropdownOpen = prefs.isWidgetDropdownOpen(appWidgetId)

            val options = runCatching { appWidgetManager.getAppWidgetOptions(appWidgetId) }.getOrNull()
            val minWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) ?: 250
            val titleSizeSp = when {
                minWidthDp < 190 -> 13.5f
                minWidthDp >= 310 -> 16f
                else -> 15f
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
            views.setInt(
                R.id.widget_list_selector,
                "setBackgroundResource",
                if (darkGlass) R.drawable.widget_selector_bg_dark else R.drawable.widget_selector_bg_light
            )
            views.setTextColor(
                R.id.widget_list_title,
                if (darkGlass) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
            )
            views.setTextViewTextSize(R.id.widget_list_title, TypedValue.COMPLEX_UNIT_SP, titleSizeSp)
            views.setTextViewText(R.id.widget_list_title, listTitle)
            views.setImageViewResource(
                R.id.widget_list_caret,
                if (dropdownOpen) R.drawable.ic_widget_arrow_drop_up else R.drawable.ic_widget_arrow_drop_down
            )

            // Dropdown overlay visibility & styling
            views.setViewVisibility(
                R.id.widget_list_dropdown_container,
                if (dropdownOpen) View.VISIBLE else View.GONE
            )
            views.setInt(
                R.id.widget_list_dropdown_container,
                "setBackgroundResource",
                if (darkGlass) R.drawable.widget_dropdown_bg_dark else R.drawable.widget_dropdown_bg_light
            )

            // Task List RemoteViewsService adapter
            val serviceIntent = Intent(context, AnyTaskWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            @Suppress("DEPRECATION")
            views.setRemoteAdapter(R.id.widget_task_list, serviceIntent)
            views.setEmptyView(R.id.widget_task_list, R.id.widget_empty_view)

            // List Dropdown RemoteViewsService adapter
            val listServiceIntent = Intent(context, AnyTaskWidgetListService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            @Suppress("DEPRECATION")
            views.setRemoteAdapter(R.id.widget_list_dropdown_listview, listServiceIntent)

            // Tap ListName ▾ in top-left header to toggle list-picker dropdown
            val toggleMenuIntent = Intent(context, AnyTaskWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_LIST_MENU
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("anytask://widget/$appWidgetId/toggle_menu")
            }
            views.setOnClickPendingIntent(
                R.id.widget_list_selector,
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 10 + 1,
                    toggleMenuIntent,
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

            // Broadcast template for task list items: check circle completes in-place without opening app;
            // tapping task text opens the task inside the app.
            val itemTemplateIntent = Intent(context, AnyTaskWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_ITEM_CLICK
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("anytask://widget/$appWidgetId/item_click")
            }
            views.setPendingIntentTemplate(
                R.id.widget_task_list,
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 10 + 4,
                    itemTemplateIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            )

            // Broadcast template for dropdown list selection
            val listTemplateIntent = Intent(context, AnyTaskWidgetProvider::class.java).apply {
                action = ACTION_SELECT_LIST
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("anytask://widget/$appWidgetId/select_list")
            }
            views.setPendingIntentTemplate(
                R.id.widget_list_dropdown_listview,
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 10 + 5,
                    listTemplateIntent,
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
                @Suppress("DEPRECATION")
                manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list_dropdown_listview)
                for (id in ids) {
                    updateWidget(context, manager, id)
                }
            }
        }
    }
}
