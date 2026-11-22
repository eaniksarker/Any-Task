package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.R
import com.example.data.local.AnyTaskDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.toDomain
import com.example.domain.model.TaskList
import kotlinx.coroutines.runBlocking

class AnyTaskWidgetListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return AnyTaskWidgetListFactory(applicationContext, appWidgetId)
    }
}

internal class AnyTaskWidgetListFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private data class ListChoice(
        val id: String?,
        val name: String
    )

    private var entries: List<ListChoice> = emptyList()
    private val prefs = PreferencesManager(context)
    private val db = AnyTaskDatabase.getInstance(context)

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val lists: List<TaskList> = runBlocking {
            AnyTaskDatabase.ensureDefaultListExists(db)
            db.taskListDao().getActiveListsOnce().map { it.toDomain() }
        }
        entries = buildList {
            add(ListChoice(null, context.getString(R.string.all_lists_label)))
            lists.forEach { add(ListChoice(it.id, it.name)) }
        }
    }

    override fun onDestroy() {
        entries = emptyList()
    }

    override fun getCount(): Int = entries.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = entries.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_list_dropdown_item)

        val selectedListId = prefs.getWidgetListId(appWidgetId)
        val isSelected = selectedListId == item.id
        val darkGlass = prefs.isWidgetDarkGlass(appWidgetId)

        val options = runCatching {
            AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        }.getOrNull()
        val minWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) ?: 250
        val textSizeSp = when {
            minWidthDp < 190 -> 12.5f
            minWidthDp >= 310 -> 14f
            else -> 13.5f
        }

        val displayText: CharSequence = if (isSelected) {
            SpannableString(item.name).apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else {
            item.name
        }

        val views = RemoteViews(context.packageName, R.layout.widget_list_dropdown_item)
        views.setTextViewText(R.id.widget_list_item_name, displayText)
        views.setTextViewTextSize(R.id.widget_list_item_name, TypedValue.COMPLEX_UNIT_SP, textSizeSp)

        val textColor = when {
            isSelected && darkGlass -> Color.parseColor("#38BDF8")
            isSelected && !darkGlass -> Color.parseColor("#0284C7")
            darkGlass -> Color.parseColor("#F1F5F9")
            else -> Color.parseColor("#0F172A")
        }
        views.setTextColor(R.id.widget_list_item_name, textColor)
        views.setImageViewResource(
            R.id.widget_list_item_radio,
            if (isSelected) R.drawable.ic_widget_radio_checked else R.drawable.ic_widget_radio_unchecked
        )

        val fillInIntent = Intent().apply {
            putExtra(AnyTaskWidgetProvider.EXTRA_SELECTED_LIST_ID, item.id ?: "")
        }
        views.setOnClickFillInIntent(R.id.widget_list_dropdown_item_row, fillInIntent)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = entries.getOrNull(position)?.id?.hashCode()?.toLong() ?: -1L
    override fun hasStableIds(): Boolean = true
}
