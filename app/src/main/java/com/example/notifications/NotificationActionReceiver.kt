package com.example.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.AnyTaskApplication
import com.example.widget.AnyTaskWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(DeadlineAlarmScheduler.EXTRA_TASK_ID) ?: return
        val action = intent.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm?.cancel(taskId.hashCode())

                val app = context.applicationContext as? AnyTaskApplication
                when (action) {
                    ACTION_MARK_COMPLETE -> {
                        app?.repository?.toggleTaskCompletion(taskId, completed = true)
                        AnyTaskWidgetProvider.notifyWidgetDataChanged(context)
                    }
                    ACTION_SNOOZE_15M -> {
                        val title = intent.getStringExtra(DeadlineAlarmScheduler.EXTRA_TASK_TITLE) ?: "Task"
                        val notes = intent.getStringExtra(DeadlineAlarmScheduler.EXTRA_TASK_NOTES).orEmpty()
                        val prio = intent.getStringExtra(DeadlineAlarmScheduler.EXTRA_TASK_PRIORITY) ?: "LOW"
                        DeadlineAlarmScheduler.scheduleSnooze(
                            context = context,
                            taskId = taskId,
                            taskTitle = title,
                            taskNotes = notes,
                            priorityName = prio,
                            delayMillis = 15 * 60_000L
                        )
                    }
                    ACTION_SNOOZE_1H -> {
                        val title = intent.getStringExtra(DeadlineAlarmScheduler.EXTRA_TASK_TITLE) ?: "Task"
                        val notes = intent.getStringExtra(DeadlineAlarmScheduler.EXTRA_TASK_NOTES).orEmpty()
                        val prio = intent.getStringExtra(DeadlineAlarmScheduler.EXTRA_TASK_PRIORITY) ?: "LOW"
                        DeadlineAlarmScheduler.scheduleSnooze(
                            context = context,
                            taskId = taskId,
                            taskTitle = title,
                            taskNotes = notes,
                            priorityName = prio,
                            delayMillis = 60 * 60_000L
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_COMPLETE = "com.example.notifications.ACTION_MARK_COMPLETE"
        const val ACTION_SNOOZE_15M = "com.example.notifications.ACTION_SNOOZE_15M"
        const val ACTION_SNOOZE_1H = "com.example.notifications.ACTION_SNOOZE_1H"
    }
}
