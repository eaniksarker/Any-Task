package com.example.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.R
import com.example.data.local.AnyTaskDatabase
import com.example.data.local.toDomain
import com.example.domain.model.ReminderOffset
import com.example.domain.model.TaskItem

object DeadlineAlarmScheduler {

    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_TASK_TITLE = "extra_task_title"
    const val EXTRA_TASK_NOTES = "extra_task_notes"
    const val EXTRA_TASK_PRIORITY = "extra_task_priority"
    const val EXTRA_IS_EARLY_REMINDER = "extra_is_early_reminder"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channelId = context.getString(R.string.notification_channel_id)
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Computes the exact trigger timestamp for a task's reminder given its due date and
     * optional early offset. Returns null if reminders are disabled or no due date is set.
     */
    fun computeTriggerMillis(
        dueDateMillis: Long?,
        offset: ReminderOffset,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        if (dueDateMillis == null || offset == ReminderOffset.NONE) return null
        val trigger = dueDateMillis - (offset.minutesBefore * 60_000L)
        return trigger.takeIf { it > nowMillis - 60_000L }
    }

    fun scheduleTaskReminder(context: Context, task: TaskItem) {
        cancelTaskReminder(context, task.id)
        if (task.completed || task.deleted) return

        val triggerAt = computeTriggerMillis(task.dueDateMillis, task.reminderOffset) ?: return
        scheduleAlarmAt(
            context = context,
            taskId = task.id,
            taskTitle = task.title,
            taskNotes = task.notes,
            priorityName = task.priority.name,
            triggerAtMillis = triggerAt.coerceAtLeast(System.currentTimeMillis() + 2_000L),
            isEarly = task.reminderOffset.minutesBefore > 0
        )
    }

    fun scheduleSnooze(
        context: Context,
        taskId: String,
        taskTitle: String,
        taskNotes: String,
        priorityName: String,
        delayMillis: Long
    ) {
        scheduleAlarmAt(
            context = context,
            taskId = taskId,
            taskTitle = taskTitle,
            taskNotes = taskNotes,
            priorityName = priorityName,
            triggerAtMillis = System.currentTimeMillis() + delayMillis,
            isEarly = false
        )
    }

    private fun scheduleAlarmAt(
        context: Context,
        taskId: String,
        taskTitle: String,
        taskNotes: String,
        priorityName: String,
        triggerAtMillis: Long,
        isEarly: Boolean
    ) {
        ensureNotificationChannel(context)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildAlarmPendingIntent(
            context = context,
            taskId = taskId,
            taskTitle = taskTitle,
            taskNotes = taskNotes,
            priorityName = priorityName,
            isEarly = isEarly
        )
        try {
            if (canScheduleExactAlarms(context)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancelTaskReminder(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, DeadlineNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.cancel(taskId.hashCode())
    }

    suspend fun rescheduleAllActiveReminders(context: Context, db: AnyTaskDatabase) {
        val activeTasks = db.taskDao().getTasksWithDeadlines().map { it.toDomain() }
        for (task in activeTasks) {
            scheduleTaskReminder(context, task)
        }
    }

    private fun buildAlarmPendingIntent(
        context: Context,
        taskId: String,
        taskTitle: String,
        taskNotes: String,
        priorityName: String,
        isEarly: Boolean
    ): PendingIntent {
        val intent = Intent(context, DeadlineNotificationReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, taskTitle)
            putExtra(EXTRA_TASK_NOTES, taskNotes)
            putExtra(EXTRA_TASK_PRIORITY, priorityName)
            putExtra(EXTRA_IS_EARLY_REMINDER, isEarly)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
