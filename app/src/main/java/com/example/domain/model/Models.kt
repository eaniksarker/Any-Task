package com.example.domain.model

import androidx.compose.runtime.Immutable

/**
 * Priority level for a task, directly mapped to the Liquid Glass color indicator system:
 * - [HIGH]: Red (#EF4444)
 * - [MEDIUM]: Amber / Yellow (#EAB308)
 * - [LOW]: Green (#22C55E)
 */
enum class TaskPriority(val label: String, val sortWeight: Int) {
    HIGH("High", 0),
    MEDIUM("Medium", 1),
    LOW("Low", 2);

    companion object {
        fun fromRaw(raw: String?): TaskPriority =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: LOW
    }
}

/**
 * Early reminder offset relative to a task's due deadline.
 */
enum class ReminderOffset(val minutesBefore: Int, val label: String) {
    NONE(-1, "No reminder"),
    AT_TIME(0, "At deadline"),
    FIFTEEN_MINUTES(15, "15 min before"),
    ONE_HOUR(60, "1 hour before"),
    ONE_DAY(1440, "1 day before");

    companion object {
        fun fromMinutes(minutes: Int): ReminderOffset =
            entries.firstOrNull { it.minutesBefore == minutes } ?: AT_TIME
    }
}

/**
 * Quick filter options for due dates on the main task board.
 */
enum class DueDateFilter(val label: String) {
    ALL("Any Date"),
    TODAY("Today"),
    UPCOMING("Upcoming"),
    OVERDUE("Overdue"),
    NO_DATE("No Date")
}

/**
 * Theme mode supporting System default, Light Glass, and Dark Glass.
 */
enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light Glass"),
    DARK("Dark Glass");

    companion object {
        fun fromRaw(raw: String?): AppThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

/**
 * Supported cloud sync identity platform (Google Sign-In + Cloud Firestore).
 */
enum class SyncProviderType(val displayName: String, val subtitle: String) {
    GOOGLE("Google", "Cloud Firestore Sync & Google Sign-In")
}

@Immutable
data class ProviderAccountStatus(
    val provider: SyncProviderType,
    val connected: Boolean = false,
    val accountName: String? = null,
    val accountEmail: String? = null,
    val configured: Boolean = true,
    val lastSyncedMillis: Long? = null,
    val statusNote: String? = null
)

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Synced(val timestampMillis: Long, val conflictsResolved: Int = 0) : SyncState
    data object Offline : SyncState
    data class Error(val message: String) : SyncState
}

@Immutable
data class Subtask(
    val id: String,
    val taskId: String,
    val title: String,
    val completed: Boolean = false,
    val sortOrder: Int = 0
)

@Immutable
data class TaskItem(
    val id: String,
    val listId: String,
    val title: String,
    val notes: String = "",
    val priority: TaskPriority = TaskPriority.LOW,
    val completed: Boolean = false,
    val completedAtMillis: Long? = null,
    val dueDateMillis: Long? = null,
    val hasDueTime: Boolean = false,
    val reminderOffset: ReminderOffset = ReminderOffset.AT_TIME,
    val sortOrder: Int = 0,
    val subtasks: List<Subtask> = emptyList(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val dirty: Boolean = false
)

@Immutable
data class TaskList(
    val id: String,
    val name: String,
    val colorHex: String = "#38BDF8",
    val sortOrder: Int = 0,
    val defaultList: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val dirty: Boolean = false
)

@Immutable
data class SyncConflictSummary(
    val itemTitle: String,
    val winner: String,
    val resolvedAtMillis: Long
)
