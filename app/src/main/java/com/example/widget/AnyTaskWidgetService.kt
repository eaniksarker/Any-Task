package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.MainActivity
import com.example.R
import com.example.data.local.AnyTaskDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.toDomain
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskPriority
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AnyTaskWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return AnyTaskWidgetFactory(applicationContext, appWidgetId)
    }
}

private class AnyTaskWidgetFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var tasks: List<TaskItem> = emptyList()
    private val prefs = PreferencesManager(context)
    private val db = AnyTaskDatabase.getInstance(context)

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val listId = prefs.getWidgetListId(appWidgetId)
        tasks = runBlocking {
            db.taskDao().getUpcomingWidgetTasks(listId, limit = 25).map { it.toDomain() }
        }
    }

    override fun onDestroy() {
        tasks = emptyList()
    }

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        val task = tasks.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.widget_task_item)
        val darkGlass = prefs.isWidgetDarkGlass(appWidgetId)

        val options = runCatching {
            AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        }.getOrNull()
        val minWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) ?: 250

        val titleSizeSp = when {
            minWidthDp < 190 -> 13f
            minWidthDp >= 310 -> 15f
            else -> 14f
        }
        val metaSizeSp = when {
            minWidthDp < 190 -> 11f
            minWidthDp >= 310 -> 12.5f
            else -> 12f
        }
        val badgeSizeSp = if (minWidthDp < 190) 9.5f else 10.5f

        val views = RemoteViews(context.packageName, R.layout.widget_task_item)
        views.setInt(
            R.id.widget_item_container,
            "setBackgroundResource",
            if (darkGlass) R.drawable.widget_item_glass_dark else R.drawable.widget_item_glass_light
        )
        views.setTextViewText(R.id.widget_item_title, task.title)
        views.setTextViewTextSize(R.id.widget_item_title, TypedValue.COMPLEX_UNIT_SP, titleSizeSp)
        views.setTextColor(
            R.id.widget_item_title,
            if (darkGlass) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        )

        val metaParts = buildList {
            task.dueDateMillis?.let { millis ->
                val fmt = if (task.hasDueTime) {
                    SimpleDateFormat("MMM d · h:mm a", Locale.getDefault())
                } else {
                    SimpleDateFormat("MMM d", Locale.getDefault())
                }
                add(fmt.format(Date(millis)))
            }
            if (task.subtasks.isNotEmpty()) {
                val done = task.subtasks.count { it.completed }
                add("$done/${task.subtasks.size} subtasks")
            }
        }
        val metaText = if (metaParts.isEmpty()) "Upcoming" else metaParts.joinToString(" • ")
        views.setTextViewText(R.id.widget_item_meta, metaText)
        views.setTextViewTextSize(R.id.widget_item_meta, TypedValue.COMPLEX_UNIT_SP, metaSizeSp)
        views.setTextColor(
            R.id.widget_item_meta,
            if (darkGlass) Color.parseColor("#CBD5E1") else Color.parseColor("#475569")
        )

        val (stripRes, badgeColor) = when (task.priority) {
            TaskPriority.HIGH -> R.drawable.widget_priority_high to Color.parseColor("#EF4444")
            TaskPriority.MEDIUM -> R.drawable.widget_priority_medium to Color.parseColor("#EAB308")
            TaskPriority.LOW -> R.drawable.widget_priority_low to Color.parseColor("#22C55E")
        }
        views.setImageViewResource(R.id.widget_item_priority_strip, stripRes)
        views.setTextViewText(R.id.widget_item_priority_badge, task.priority.label.uppercase())
        views.setTextViewTextSize(R.id.widget_item_priority_badge, TypedValue.COMPLEX_UNIT_SP, badgeSizeSp)
        views.setTextColor(R.id.widget_item_priority_badge, badgeColor)

        val toggleFillIn = Intent().apply {
            putExtra(MainActivity.EXTRA_COMPLETE_TASK_ID, task.id)
        }
        views.setOnClickFillInIntent(R.id.widget_item_check, toggleFillIn)

        val openFillIn = Intent().apply {
            putExtra(MainActivity.EXTRA_OPEN_TASK_ID, task.id)
        }
        views.setOnClickFillInIntent(R.id.widget_item_text_container, openFillIn)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = tasks.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
