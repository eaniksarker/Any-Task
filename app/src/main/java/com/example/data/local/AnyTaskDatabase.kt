package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.UUID

@Database(
    entities = [TaskListEntity::class, TaskEntity::class, SubtaskEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AnyTaskDatabase : RoomDatabase() {
    abstract fun taskListDao(): TaskListDao
    abstract fun taskDao(): TaskDao
    abstract fun subtaskDao(): SubtaskDao

    companion object {
        const val DEFAULT_LIST_ID = "default-my-tasks"

        @Volatile
        private var INSTANCE: AnyTaskDatabase? = null

        fun getInstance(context: Context): AnyTaskDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnyTaskDatabase::class.java,
                    "anytask_offline.db"
                ).build().also { INSTANCE = it }
            }
        }

        /**
         * Ensures at least one default task list exists on first launch so users can
         * capture tasks immediately offline or online.
         */
        suspend fun ensureDefaultListExists(db: AnyTaskDatabase) {
            val existing = db.taskListDao().getActiveListsOnce()
            if (existing.isEmpty()) {
                val now = System.currentTimeMillis()
                db.taskListDao().upsert(
                    TaskListEntity(
                        id = DEFAULT_LIST_ID,
                        name = "My Tasks",
                        colorHex = "#38BDF8",
                        sortOrder = 0,
                        defaultList = true,
                        updatedAtMillis = now,
                        deleted = false,
                        dirty = true
                    )
                )
            }
        }

        fun newValidId(): String = UUID.randomUUID().toString()
    }
}
