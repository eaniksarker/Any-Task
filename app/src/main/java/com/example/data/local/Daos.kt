package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskListDao {
    @Query("SELECT * FROM task_lists WHERE deleted = 0 ORDER BY sortOrder ASC, updatedAtMillis ASC")
    fun observeActiveLists(): Flow<List<TaskListEntity>>

    @Query("SELECT * FROM task_lists WHERE deleted = 0 ORDER BY sortOrder ASC, updatedAtMillis ASC")
    suspend fun getActiveListsOnce(): List<TaskListEntity>

    @Query("SELECT * FROM task_lists")
    suspend fun getAllIncludingDeleted(): List<TaskListEntity>

    @Query("SELECT * FROM task_lists WHERE id = :listId LIMIT 1")
    suspend fun getById(listId: String): TaskListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(list: TaskListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(lists: List<TaskListEntity>)

    @Query("UPDATE task_lists SET deleted = 1, dirty = 1, updatedAtMillis = :updatedAt WHERE id = :listId")
    suspend fun markDeleted(listId: String, updatedAt: Long)

    @Query("UPDATE task_lists SET dirty = 0 WHERE id = :listId")
    suspend fun markSynced(listId: String)
}

@Dao
interface TaskDao {
    @Transaction
    @Query("SELECT * FROM tasks WHERE deleted = 0 ORDER BY completed ASC, sortOrder ASC, updatedAtMillis DESC")
    fun observeAllActiveTasks(): Flow<List<TaskWithSubtasksEntity>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE deleted = 0 ORDER BY completed ASC, sortOrder ASC, updatedAtMillis DESC")
    suspend fun getAllActiveTasksOnce(): List<TaskWithSubtasksEntity>

    @Transaction
    @Query("SELECT * FROM tasks")
    suspend fun getAllIncludingDeleted(): List<TaskWithSubtasksEntity>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskWithSubtasksById(taskId: String): TaskWithSubtasksEntity?

    @Transaction
    @Query("SELECT * FROM tasks WHERE deleted = 0 AND completed = 0 AND (:listId IS NULL OR listId = :listId) ORDER BY sortOrder ASC, dueDateMillis ASC LIMIT :limit")
    suspend fun getUpcomingWidgetTasks(listId: String?, limit: Int = 25): List<TaskWithSubtasksEntity>

    @Transaction
    @Query("SELECT * FROM tasks WHERE deleted = 0 AND completed = 0 AND dueDateMillis IS NOT NULL")
    suspend fun getTasksWithDeadlines(): List<TaskWithSubtasksEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<TaskEntity>)

    @Query("UPDATE tasks SET completed = :completed, completedAtMillis = :completedAt, dirty = 1, updatedAtMillis = :updatedAt WHERE id = :taskId")
    suspend fun updateCompletion(taskId: String, completed: Boolean, completedAt: Long?, updatedAt: Long)

    @Query("UPDATE tasks SET sortOrder = :sortOrder, dirty = 1, updatedAtMillis = :updatedAt WHERE id = :taskId")
    suspend fun updateSortOrder(taskId: String, sortOrder: Int, updatedAt: Long)

    @Query("UPDATE tasks SET deleted = 1, dirty = 1, updatedAtMillis = :updatedAt WHERE id = :taskId")
    suspend fun markDeleted(taskId: String, updatedAt: Long)

    @Query("UPDATE tasks SET deleted = 1, dirty = 1, updatedAtMillis = :updatedAt WHERE id IN (:taskIds)")
    suspend fun markDeletedByIds(taskIds: List<String>, updatedAt: Long)

    @Query("UPDATE tasks SET deleted = 1, dirty = 1, updatedAtMillis = :updatedAt WHERE listId = :listId")
    suspend fun markDeletedByList(listId: String, updatedAt: Long)

    @Query("UPDATE tasks SET dirty = 0 WHERE id = :taskId")
    suspend fun markSynced(taskId: String)
}

@Dao
interface SubtaskDao {
    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY sortOrder ASC")
    suspend fun getSubtasksForTask(taskId: String): List<SubtaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(subtasks: List<SubtaskEntity>)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteByTaskId(taskId: String)

    @Transaction
    suspend fun replaceForTask(taskId: String, subtasks: List<SubtaskEntity>) {
        deleteByTaskId(taskId)
        if (subtasks.isNotEmpty()) {
            upsertAll(subtasks)
        }
    }
}
