package com.example.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.TaskPriority

class DeadlineNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(DeadlineAlarmScheduler.EXTRA_TASK_ID) ?: return
        val title = intent.getStringExtra(DeadlineAlarmScheduler.EXTRA_TASK_TITLE) ?: "Task Reminder"
        val notes = intent.getStringExtra(DeadlineAlarmScheduler.EXTRA_TASK_NOTES).orEmpty()
        val priorityRaw = intent.getStringExtra(DeadlineAlarmScheduler.EXTRA_TASK_PRIORITY)
        val isEarly = intent.getBooleanExtra(DeadlineAlarmScheduler.EXTRA_IS_EARLY_REMINDER, false)
        val priority = TaskPriority.fromRaw(priorityRaw)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        DeadlineAlarmScheduler.ensureNotificationChannel(context)
        val notificationId = taskId.hashCode()

        val openTaskIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TASK_ID, taskId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openTaskIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_COMPLETE
            putExtra(DeadlineAlarmScheduler.EXTRA_TASK_ID, taskId)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze15Intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE_15M
            putExtra(DeadlineAlarmScheduler.EXTRA_TASK_ID, taskId)
            putExtra(DeadlineAlarmScheduler.EXTRA_TASK_TITLE, title)
            putExtra(DeadlineAlarmScheduler.EXTRA_TASK_NOTES, notes)
            putExtra(DeadlineAlarmScheduler.EXTRA_TASK_PRIORITY, priority.name)
        }
        val snooze15PendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            snooze15Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze1hIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE_1H
            putExtra(DeadlineAlarmScheduler.EXTRA_TASK_ID, taskId)
            putExtra(DeadlineAlarmScheduler.EXTRA_TASK_TITLE, title)
            putExtra(DeadlineAlarmScheduler.EXTRA_TASK_NOTES, notes)
            putExtra(DeadlineAlarmScheduler.EXTRA_TASK_PRIORITY, priority.name)
        }
        val snooze1hPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 3,
            snooze1hIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val accentColor = when (priority) {
            TaskPriority.HIGH -> Color.parseColor("#EF4444")
            TaskPriority.MEDIUM -> Color.parseColor("#EAB308")
            TaskPriority.LOW -> Color.parseColor("#22C55E")
        }

        val prefix = if (isEarly) "Upcoming (${priority.label})" else "Due Now (${priority.label})"
        val bodyText = notes.ifBlank { "Tap to view details or act directly from the tray." }

        val notification = NotificationCompat.Builder(
            context,
            context.getString(R.string.notification_channel_id)
        )
            .setSmallIcon(R.drawable.ic_notification_task)
            .setColor(accentColor)
            .setContentTitle("$prefix · $title")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, context.getString(R.string.action_mark_complete), completePendingIntent)
            .addAction(0, context.getString(R.string.action_snooze_15m), snooze15PendingIntent)
            .addAction(0, context.getString(R.string.action_snooze_1h), snooze1hPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
