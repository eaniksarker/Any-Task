package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.anyTaskDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anytask_preferences"
)

class PreferencesManager(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SELECTED_LIST_ID = stringPreferencesKey("selected_list_id")
        val LAST_SYNCED_MILLIS = longPreferencesKey("last_synced_millis")
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val OFFLINE_MODE_ACKNOWLEDGED = booleanPreferencesKey("offline_mode_acknowledged")
        val COMPLETION_SOUND_ENABLED = booleanPreferencesKey("completion_sound_enabled")
    }

    val themeMode: Flow<AppThemeMode> = context.anyTaskDataStore.data.map { prefs ->
        AppThemeMode.fromRaw(prefs[Keys.THEME_MODE])
    }

    val selectedListId: Flow<String?> = context.anyTaskDataStore.data.map { prefs ->
        prefs[Keys.SELECTED_LIST_ID]
    }

    val lastSyncedMillis: Flow<Long?> = context.anyTaskDataStore.data.map { prefs ->
        prefs[Keys.LAST_SYNCED_MILLIS]
    }

    val autoSyncEnabled: Flow<Boolean> = context.anyTaskDataStore.data.map { prefs ->
        prefs[Keys.AUTO_SYNC_ENABLED] ?: true
    }

    val offlineModeAcknowledged: Flow<Boolean> = context.anyTaskDataStore.data.map { prefs ->
        prefs[Keys.OFFLINE_MODE_ACKNOWLEDGED] ?: false
    }

    val completionSoundEnabled: Flow<Boolean> = context.anyTaskDataStore.data.map { prefs ->
        prefs[Keys.COMPLETION_SOUND_ENABLED] ?: true
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.anyTaskDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setSelectedListId(listId: String?) {
        context.anyTaskDataStore.edit { prefs ->
            if (listId == null) prefs.remove(Keys.SELECTED_LIST_ID)
            else prefs[Keys.SELECTED_LIST_ID] = listId
        }
    }

    suspend fun setLastSyncedMillis(timestamp: Long) {
        context.anyTaskDataStore.edit { it[Keys.LAST_SYNCED_MILLIS] = timestamp }
    }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        context.anyTaskDataStore.edit { it[Keys.AUTO_SYNC_ENABLED] = enabled }
    }

    suspend fun setOfflineModeAcknowledged(acknowledged: Boolean) {
        context.anyTaskDataStore.edit { it[Keys.OFFLINE_MODE_ACKNOWLEDGED] = acknowledged }
    }

    suspend fun setCompletionSoundEnabled(enabled: Boolean) {
        context.anyTaskDataStore.edit { it[Keys.COMPLETION_SOUND_ENABLED] = enabled }
    }

    suspend fun isCompletionSoundEnabled(): Boolean = completionSoundEnabled.first()

    // Widget per-instance preferences stored via SharedPreferences for synchronous RemoteViews reads
    fun saveWidgetConfig(appWidgetId: Int, listId: String?, darkGlass: Boolean) {
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).edit()
            .putString("widget_list_$appWidgetId", listId)
            .putBoolean("widget_dark_$appWidgetId", darkGlass)
            .apply()
    }

    fun getWidgetListId(appWidgetId: Int): String? =
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            .getString("widget_list_$appWidgetId", null)

    fun isWidgetDarkGlass(appWidgetId: Int): Boolean =
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            .getBoolean("widget_dark_$appWidgetId", true)

    fun setWidgetDropdownOpen(appWidgetId: Int, isOpen: Boolean) {
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("widget_dropdown_$appWidgetId", isOpen)
            .apply()
    }

    fun isWidgetDropdownOpen(appWidgetId: Int): Boolean =
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            .getBoolean("widget_dropdown_$appWidgetId", false)

    fun deleteWidgetConfig(appWidgetId: Int) {
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).edit()
            .remove("widget_list_$appWidgetId")
            .remove("widget_dark_$appWidgetId")
            .remove("widget_dropdown_$appWidgetId")
            .apply()
    }

    companion object {
        private const val WIDGET_PREFS = "anytask_widget_prefs"
    }
}
