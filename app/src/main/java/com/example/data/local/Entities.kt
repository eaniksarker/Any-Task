package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.domain.model.ReminderOffset
import com.example.domain.model.Subtask
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskList
import com.example.domain.model.TaskPriority

@Entity(tableName = "task_lists")
data class TaskListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String = "#38BDF8",
    val sortOrder: Int = 0,
    val defaultList: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val dirty: Boolean = true
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("listId"), Index("dueDateMillis")]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val title: String,
    val notes: String = "",
    val priority: String = TaskPriority.LOW.name,
    val completed: Boolean = false,
    val completedAtMillis: Long? = null,
    val dueDateMillis: Long? = null,
    val hasDueTime: Boolean = false,
    val reminderOffsetMinutes: Int = ReminderOffset.AT_TIME.minutesBefore,
    val sortOrder: Int = 0,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val dirty: Boolean = true
)

@Entity(
    tableName = "subtasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class SubtaskEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val title: String,
    val completed: Boolean = false,
    val sortOrder: Int = 0
)

data class TaskWithSubtasksEntity(
    @Embedded val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "taskId"
    )
    val subtasks: List<SubtaskEntity>
)

fun TaskListEntity.toDomain(): TaskList = TaskList(
    id = id,
    name = name,
    colorHex = colorHex,
    sortOrder = sortOrder,
    defaultList = defaultList,
    updatedAtMillis = updatedAtMillis,
    deleted = deleted,
    dirty = dirty
)

fun TaskList.toEntity(dirty: Boolean = this.dirty): TaskListEntity = TaskListEntity(
    id = id,
    name = name,
    colorHex = colorHex,
    sortOrder = sortOrder,
    defaultList = defaultList,
    updatedAtMillis = updatedAtMillis,
    deleted = deleted,
    dirty = dirty
)

fun SubtaskEntity.toDomain(): Subtask = Subtask(
    id = id,
    taskId = taskId,
    title = title,
    completed = completed,
    sortOrder = sortOrder
)

fun Subtask.toEntity(): SubtaskEntity = SubtaskEntity(
    id = id,
    taskId = taskId,
    title = title,
    completed = completed,
    sortOrder = sortOrder
)

fun TaskWithSubtasksEntity.toDomain(): TaskItem = TaskItem(
    id = task.id,
    listId = task.listId,
    title = task.title,
    notes = task.notes,
    priority = TaskPriority.fromRaw(task.priority),
    completed = task.completed,
    completedAtMillis = task.completedAtMillis,
    dueDateMillis = task.dueDateMillis,
    hasDueTime = task.hasDueTime,
    reminderOffset = ReminderOffset.fromMinutes(task.reminderOffsetMinutes),
    sortOrder = task.sortOrder,
    subtasks = subtasks.sortedBy { it.sortOrder }.map { it.toDomain() },
    updatedAtMillis = task.updatedAtMillis,
    deleted = task.deleted,
    dirty = task.dirty
)

fun TaskItem.toEntity(dirty: Boolean = this.dirty): TaskEntity = TaskEntity(
    id = id,
    listId = listId,
    title = title,
    notes = notes,
    priority = priority.name,
    completed = completed,
    completedAtMillis = completedAtMillis,
    dueDateMillis = dueDateMillis,
    hasDueTime = hasDueTime,
    reminderOffsetMinutes = reminderOffset.minutesBefore,
    sortOrder = sortOrder,
    updatedAtMillis = updatedAtMillis,
    deleted = deleted,
    dirty = dirty
)
