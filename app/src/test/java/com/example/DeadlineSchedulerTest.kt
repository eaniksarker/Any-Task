package com.example

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.ReminderOffset
import com.example.notifications.DeadlineAlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeadlineSchedulerTest {

    @Test
    fun `computeTriggerMillis applies early reminder offsets accurately`() {
        val now = 1_700_000_000_000L
        val due = now + 2 * 60 * 60_000L // +2 hours

        assertEquals(due, DeadlineAlarmScheduler.computeTriggerMillis(due, ReminderOffset.AT_TIME, now))
        assertEquals(
            due - 15 * 60_000L,
            DeadlineAlarmScheduler.computeTriggerMillis(due, ReminderOffset.FIFTEEN_MINUTES, now)
        )
        assertEquals(
            due - 60 * 60_000L,
            DeadlineAlarmScheduler.computeTriggerMillis(due, ReminderOffset.ONE_HOUR, now)
        )
        assertNull(DeadlineAlarmScheduler.computeTriggerMillis(due, ReminderOffset.NONE, now))
        assertNull(DeadlineAlarmScheduler.computeTriggerMillis(null, ReminderOffset.AT_TIME, now))
    }

    @Test
    fun `ensureNotificationChannel registers high importance deadlines channel`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DeadlineAlarmScheduler.ensureNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = nm?.getNotificationChannel(context.getString(R.string.notification_channel_id))
            assertNotNull(channel)
            assertEquals(NotificationManager.IMPORTANCE_HIGH, channel?.importance)
        }
    }
}
