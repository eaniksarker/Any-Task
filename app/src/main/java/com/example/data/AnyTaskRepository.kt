package com.example.data

import android.content.Context
import com.example.audio.CompletionSoundPlayer
import com.example.data.local.AnyTaskDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.data.remote.FirestoreTaskRepository
import com.example.data.remote.SyncEngine
import com.example.domain.model.ReminderOffset
import com.example.domain.model.Subtask
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskList
import com.example.domain.model.TaskPriority
import com.example.notifications.DeadlineAlarmScheduler
import com.example.widget.AnyTaskWidgetProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Offline-first repository backed by Room as the immediate source of truth,
 * triggering exact deadline alarms, home-screen widget updates, and optional
 * Cloud Firestore synchronization when a Google account is signed in.
 */
class AnyTaskRepository(
    private val context: Context,
    private val db: AnyTaskDatabase,
    private val prefs: PreferencesManager,
    private val firestoreRepo: FirestoreTaskRepository,
    private val syncEngine: SyncEngine
) {
    val taskListsFlow: Flow<List<TaskList>> =
        db.taskListDao().observeActiveLists().map { list -> list.map { it.toDomain() } }

    val tasksFlow: Flow<List<TaskItem>> =
        db.taskDao().observeAllActiveTasks().map { list -> list.map { it.toDomain() } }

    suspend fun initializeDefaults() {
        AnyTaskDatabase.ensureDefaultListExists(db)
    }

    suspend fun getTaskById(taskId: String): TaskItem? =
        db.taskDao().getTaskWithSubtasksById(taskId)?.toDomain()

    suspend fun createTaskList(name: String, colorHex: String = "#38BDF8"): TaskList {
        val current = db.taskListDao().getActiveListsOnce()
        val now = System.currentTimeMillis()
        val list = TaskList(
            id = AnyTaskDatabase.newValidId(),
            name = name.trim().take(80).ifEmpty { "New List" },
            colorHex = colorHex,
            sortOrder = current.size,
            defaultList = false,
            updatedAtMillis = now,
            deleted = false,
            dirty = true
        )
        db.taskListDao().upsert(list.toEntity(dirty = true))
        pushTaskListIfSignedIn(list)
        AnyTaskWidgetProvider.notifyWidgetDataChanged(context)
        return list
    }

    suspend fun renameTaskList(listId: String, newName: String) {
        val existing = db.taskListDao().getById(listId)?.toDomain() ?: return
        val updated = existing.copy(
            name = newName.trim().take(80).ifEmpty { existing.name },
            updatedAtMillis = System.currentTimeMillis(),
            dirty = true
        )
        db.taskListDao().upsert(updated.toEntity(dirty = true))
        pushTaskListIfSignedIn(updated)
        AnyTaskWidgetProvider.notifyWidgetDataChanged(context)
    }

    suspend fun deleteTaskList(listId: String) {
        val existing = db.taskListDao().getById(listId)?.toDomain() ?: return
        if (existing.defaultList) return
        val now = System.currentTimeMillis()
        db.taskListDao().markDeleted(listId, now)
        db.taskDao().markDeletedByList(listId, now)
        val deletedList = existing.copy(deleted = true, updatedAtMillis = now, dirty = true)
        pushTaskListIfSignedIn(deletedList)
        AnyTaskWidgetProvider.notifyWidgetDataChanged(context)
    }

    suspend fun saveTask(
        existingTaskId: String?,
        listId: String,
        title: String,
        notes: String,
        priority: TaskPriority,
        dueDateMillis: Long?,
        hasDueTime: Boolean,
        reminderOffset: ReminderOffset,
        subtasks: List<Subtask>
    ): TaskItem {
        val now = System.currentTimeMillis()
        val existing = existingTaskId?.let { db.taskDao().getTaskWithSubtasksById(it)?.toDomain() }
        val taskId = existing?.id ?: AnyTaskDatabase.newValidId()
        val sortOrder = existing?.sortOrder ?: db.taskDao().getAllActiveTasksOnce().size

        val normalizedSubtasks = subtasks.mapIndexed { idx, sub ->
            sub.copy(
                id = sub.id.ifBlank { AnyTaskDatabase.newValidId() },
                taskId = taskId,
                sortOrder = idx
            )
        }

        val item = TaskItem(
            id = taskId,
            listId = listId,
            title = title.trim().take(300).ifEmpty { "Untitled Task" },
            notes = notes.trim().take(4000),
            priority = priority,
            completed = existing?.completed ?: false,
            completedAtMillis = existing?.completedAtMillis,
            dueDateMillis = dueDateMillis,
            hasDueTime = hasDueTime,
            reminderOffset = reminderOffset,
            sortOrder = sortOrder,
            subtasks = normalizedSubtasks,
            updatedAtMillis = now,
            deleted = false,
            dirty = true
        )

        db.taskDao().upsertTask(item.toEntity(dirty = true))
        db.subtaskDao().replaceForTask(taskId, normalizedSubtasks.map { it.toEntity() })
        DeadlineAlarmScheduler.scheduleTaskReminder(context, item)
        pushTaskIfSignedIn(item)
        AnyTaskWidgetProvider.notifyWidgetDataChanged(context)
        return item
    }

    suspend fun toggleTaskCompletion(taskId: String, completed: Boolean? = null) {
        val existing = db.taskDao().getTaskWithSubtasksById(taskId)?.toDomain() ?: return
        val newCompleted = completed ?: !existing.completed
        val now = System.currentTimeMillis()
        val completedAt = if (newCompleted) now else null
        db.taskDao().updateCompletion(taskId, newCompleted, completedAt, now)

        val updated = existing.copy(
            completed = newCompleted,
            completedAtMillis = completedAt,
            updatedAtMillis = now,
            dirty = true
        )
        if (newCompleted) {
            DeadlineAlarmScheduler.cancelTaskReminder(context, taskId)
            if (!existing.completed) {
                val soundEnabled = prefs.isCompletionSoundEnabled()
                CompletionSoundPlayer.playTaskCompletedBeep(context, soundEnabled)
            }
        } else {
            DeadlineAlarmScheduler.scheduleTaskReminder(context, updated)
        }
        pushTaskIfSignedIn(updated)
        AnyTaskWidgetProvider.notifyWidgetDataChanged(context)
    }

    suspend fun toggleSubtaskCompletion(taskId: String, subtaskId: String) {
        val existing = db.taskDao().getTaskWithSubtasksById(taskId)?.toDomain() ?: return
        val updatedSubs = existing.subtasks.map { sub ->
            if (sub.id == subtaskId) sub.copy(completed = !sub.completed) else sub
        }
        val now = System.currentTimeMillis()
        val updated = existing.copy(subtasks = updatedSubs, updatedAtMillis = now, dirty = true)
        db.taskDao().upsertTask(updated.toEntity(dirty = true))
        db.subtaskDao().replaceForTask(taskId, updatedSubs.map { it.toEntity() })
        pushTaskIfSignedIn(updated)
        AnyTaskWidgetProvider.notifyWidgetDataChanged(context)
    }

    suspend fun deleteTask(taskId: String) {
        val existing = db.taskDao().getTaskWithSubtasksById(taskId)?.toDomain() ?: return
        val now = System.currentTimeMillis()
        db.taskDao().markDeleted(taskId, now)
        DeadlineAlarmScheduler.cancelTaskReminder(context, taskId)
        val deleted = existing.copy(deleted = true, updatedAtMillis = now, dirty = true)
        pushTaskIfSignedIn(deleted)
        AnyTaskWidgetProvider.notifyWidgetDataChanged(context)
    }

    suspend fun deleteCompletedTasks(taskIds: List<String>): Int {
        if (taskIds.isEmpty()) return 0
        val existingTasks = taskIds.mapNotNull { id ->
            db.taskDao().getTaskWithSubtasksById(id)?.toDomain()
        }.filter { it.completed && !it.deleted }
        if (existingTasks.isEmpty()) return 0

        val idsToDelete = existingTasks.map { it.id }
        val now = System.currentTimeMillis()
        db.taskDao().markDeletedByIds(idsToDelete, now)
        existingTasks.forEach { task ->
            DeadlineAlarmScheduler.cancelTaskReminder(context, task.id)
            pushTaskIfSignedIn(task.copy(deleted = true, updatedAtMillis = now, dirty = true))
        }
        AnyTaskWidgetProvider.notifyWidgetDataChanged(context)
        return existingTasks.size
    }

    suspend fun reorderTasks(orderedActiveIds: List<String>) {
        val now = System.currentTimeMillis()
        orderedActiveIds.forEachIndexed { index, taskId ->
            db.taskDao().updateSortOrder(taskId, index, now)
        }
        val uid = Firebase.auth.currentUser?.uid
        if (!uid.isNullOrBlank()) {
            orderedActiveIds.forEach { taskId ->
                db.taskDao().getTaskWithSubtasksById(taskId)?.toDomain()?.let { pushTaskIfSignedIn(it) }
            }
        }
        AnyTaskWidgetProvider.notifyWidgetDataChanged(context)
    }

    suspend fun syncNow(): Result<SyncEngine.SyncReport> {
        val uid = Firebase.auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Sign in with Google to sync to the cloud."))
        }
        val result = syncEngine.syncWithCloud(uid)
        if (result.isSuccess) {
            DeadlineAlarmScheduler.rescheduleAllActiveReminders(context, db)
            AnyTaskWidgetProvider.notifyWidgetDataChanged(context)
        }
        return result
    }

    private suspend fun pushTaskListIfSignedIn(list: TaskList) {
        val uid = Firebase.auth.currentUser?.uid
        if (!uid.isNullOrBlank()) {
            val result = firestoreRepo.upsertTaskList(list)
            if (result.isSuccess) {
                db.taskListDao().markSynced(list.id)
                prefs.setLastSyncedMillis(System.currentTimeMillis())
            }
        }
    }

    private suspend fun pushTaskIfSignedIn(task: TaskItem) {
        val uid = Firebase.auth.currentUser?.uid
        if (!uid.isNullOrBlank()) {
            val result = firestoreRepo.upsertTask(task)
            if (result.isSuccess) {
                db.taskDao().markSynced(task.id)
                prefs.setLastSyncedMillis(System.currentTimeMillis())
            }
        }
    }
}
