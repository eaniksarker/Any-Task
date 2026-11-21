package com.example.data.remote

import androidx.annotation.Keep
import com.example.domain.model.ReminderOffset
import com.example.domain.model.Subtask
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskList
import com.example.domain.model.TaskPriority
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class FirestoreTaskList(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val colorHex: String? = "#38BDF8",
    val sortOrder: Long = 0L,
    val defaultList: Boolean = false,
    val deleted: Boolean = false,
    val updatedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toDomain(): TaskList = TaskList(
        id = id,
        name = name.ifBlank { "Untitled List" },
        colorHex = colorHex ?: "#38BDF8",
        sortOrder = sortOrder.toInt(),
        defaultList = defaultList,
        updatedAtMillis = updatedAtMillis,
        deleted = deleted,
        dirty = false
    )

    companion object {
        fun toCreateMap(userId: String, list: TaskList): Map<String, Any> {
            val sanitizedName = list.name.trim().take(80).ifEmpty { "Untitled List" }
            return mapOf(
                "userId" to userId,
                "name" to sanitizedName,
                "colorHex" to list.colorHex.take(16),
                "sortOrder" to list.sortOrder.coerceIn(0, 100000).toLong(),
                "defaultList" to list.defaultList,
                "deleted" to list.deleted,
                "updatedAtMillis" to list.updatedAtMillis.coerceAtLeast(0L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        }

        fun toUpdateMap(list: TaskList): Map<String, Any> {
            val sanitizedName = list.name.trim().take(80).ifEmpty { "Untitled List" }
            return mapOf(
                "name" to sanitizedName,
                "colorHex" to list.colorHex.take(16),
                "sortOrder" to list.sortOrder.coerceIn(0, 100000).toLong(),
                "defaultList" to list.defaultList,
                "deleted" to list.deleted,
                "updatedAtMillis" to list.updatedAtMillis.coerceAtLeast(0L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class FirestoreTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val listId: String = "",
    val title: String = "",
    val notes: String? = null,
    val priority: String = TaskPriority.LOW.name,
    val completed: Boolean = false,
    val completedAtMillis: Long? = null,
    val dueDateMillis: Long? = null,
    val hasDueTime: Boolean = false,
    val reminderOffsetMinutes: Long = 0L,
    val sortOrder: Long = 0L,
    val subtasks: List<Map<String, Any?>> = emptyList(),
    val deleted: Boolean = false,
    val updatedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toDomain(): TaskItem {
        val parsedSubtasks = subtasks.mapIndexedNotNull { index, raw ->
            val subId = raw["id"] as? String ?: return@mapIndexedNotNull null
            val subTitle = raw["title"] as? String ?: return@mapIndexedNotNull null
            val subCompleted = raw["completed"] as? Boolean ?: false
            val subOrder = (raw["sortOrder"] as? Number)?.toInt() ?: index
            Subtask(
                id = subId,
                taskId = id,
                title = subTitle,
                completed = subCompleted,
                sortOrder = subOrder
            )
        }.sortedBy { it.sortOrder }

        return TaskItem(
            id = id,
            listId = listId,
            title = title.ifBlank { "Untitled Task" },
            notes = notes.orEmpty(),
            priority = TaskPriority.fromRaw(priority),
            completed = completed,
            completedAtMillis = completedAtMillis,
            dueDateMillis = dueDateMillis,
            hasDueTime = hasDueTime,
            reminderOffset = ReminderOffset.fromMinutes(reminderOffsetMinutes.toInt()),
            sortOrder = sortOrder.toInt(),
            subtasks = parsedSubtasks,
            updatedAtMillis = updatedAtMillis,
            deleted = deleted,
            dirty = false
        )
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun toCreateMap(userId: String, task: TaskItem): Map<String, Any> {
            val sanitizedTitle = task.title.trim().take(300).ifEmpty { "Untitled Task" }
            val sanitizedNotes = task.notes.trim().take(4000).takeIf { it.isNotEmpty() }
            val subtaskMaps = task.subtasks.take(50).mapIndexed { index, sub ->
                mapOf(
                    "id" to sub.id,
                    "title" to sub.title.trim().take(200),
                    "completed" to sub.completed,
                    "sortOrder" to index.toLong()
                )
            }
            return mapOf(
                "userId" to userId,
                "listId" to task.listId,
                "title" to sanitizedTitle,
                "notes" to sanitizedNotes,
                "priority" to task.priority.name,
                "completed" to task.completed,
                "completedAtMillis" to task.completedAtMillis?.coerceAtLeast(0L),
                "dueDateMillis" to task.dueDateMillis?.coerceAtLeast(0L),
                "hasDueTime" to task.hasDueTime,
                "reminderOffsetMinutes" to task.reminderOffset.minutesBefore.coerceIn(-1, 10080).toLong(),
                "sortOrder" to task.sortOrder.coerceIn(0, 1000000).toLong(),
                "subtasks" to subtaskMaps,
                "deleted" to task.deleted,
                "updatedAtMillis" to task.updatedAtMillis.coerceAtLeast(0L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
        }

        @Suppress("UNCHECKED_CAST")
        fun toUpdateMap(task: TaskItem): Map<String, Any> {
            val sanitizedTitle = task.title.trim().take(300).ifEmpty { "Untitled Task" }
            val sanitizedNotes = task.notes.trim().take(4000)
            val subtaskMaps = task.subtasks.take(50).mapIndexed { index, sub ->
                mapOf(
                    "id" to sub.id,
                    "title" to sub.title.trim().take(200),
                    "completed" to sub.completed,
                    "sortOrder" to index.toLong()
                )
            }
            return mapOf(
                "listId" to task.listId,
                "title" to sanitizedTitle,
                "notes" to sanitizedNotes,
                "priority" to task.priority.name,
                "completed" to task.completed,
                "completedAtMillis" to task.completedAtMillis?.coerceAtLeast(0L),
                "dueDateMillis" to task.dueDateMillis?.coerceAtLeast(0L),
                "hasDueTime" to task.hasDueTime,
                "reminderOffsetMinutes" to task.reminderOffset.minutesBefore.coerceIn(-1, 10080).toLong(),
                "sortOrder" to task.sortOrder.coerceIn(0, 1000000).toLong(),
                "subtasks" to subtaskMaps,
                "deleted" to task.deleted,
                "updatedAtMillis" to task.updatedAtMillis.coerceAtLeast(0L),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
        }
    }
}
