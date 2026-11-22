package com.example

import android.app.Application
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.audio.CompletionSoundPlayer
import com.example.data.local.AnyTaskDatabase
import com.example.data.local.PreferencesManager
import com.example.domain.model.AppThemeMode
import com.example.domain.model.ReminderOffset
import com.example.domain.model.TaskPriority
import com.example.ui.screens.AccountSyncScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.TaskBoardScreen
import com.example.ui.theme.AnyTaskTheme
import com.example.ui.viewmodel.AuthSyncViewModel
import com.example.ui.viewmodel.TaskBoardViewModel
import com.example.widget.AnyTaskWidgetFactory
import com.example.widget.AnyTaskWidgetListFactory
import com.example.widget.AnyTaskWidgetProvider
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaskBoardUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = com.google.firebase.FirebaseOptions.Builder()
                .setApplicationId("com.aistudio.anytask.qvnzkr")
                .setApiKey("fake-api-key")
                .setProjectId("ai-studio-android-anytask")
                .build()
            FirebaseApp.initializeApp(context, options)
        }
    }

    @Test
    fun `auth screen renders minimal welcome logo, note, and Continue with Google button`() {
        composeRule.setContent {
            AnyTaskTheme(themeMode = AppThemeMode.DARK) {
                AuthScreen(onAuthSuccess = {})
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("app_welcome_logo").assertIsDisplayed()
        composeRule.onNodeWithText("Any Task").assertIsDisplayed()
        composeRule.onNodeWithText("Welcome to Any Task — Organize your day with clarity and color-coded focus.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
        composeRule.onNodeWithText("Continue with Google").assertIsDisplayed()
        composeRule.onNodeWithTag("continue_offline_button").assertDoesNotExist()
    }

    @Test
    fun `settings screen renders Google sync, completion beep sound toggle, and Anik Sarker developer credit`() {
        val app = ApplicationProvider.getApplicationContext<AnyTaskApplication>()
        val viewModel = AuthSyncViewModel(app.repository, app.preferences)

        composeRule.setContent {
            AnyTaskTheme(themeMode = AppThemeMode.DARK) {
                AccountSyncScreen(
                    viewModel = viewModel,
                    onBack = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_now_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("provider_google_connect_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("provider_microsoft_connect_button").assertDoesNotExist()
        composeRule.onNodeWithTag("provider_facebook_connect_button").assertDoesNotExist()

        composeRule.onNodeWithTag("sound_feedback_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Completion Beep Sound").assertIsDisplayed()
        composeRule.onNodeWithTag("completion_sound_switch").assertIsDisplayed()

        composeRule.onNodeWithTag("about_section_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("About").assertIsDisplayed()
        composeRule.onNodeWithText("v${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        composeRule.onNodeWithTag("developer_credit_text").assertIsDisplayed()
        composeRule.onNodeWithText("Built and developed by Anik Sarker").assertIsDisplayed()
    }

    @Test
    fun `task board renders All Task priority chip selected by default and filters priorities`() {
        val app = ApplicationProvider.getApplicationContext<AnyTaskApplication>()
        val viewModel = TaskBoardViewModel(
            repository = app.repository,
            prefs = app.preferences,
            firestoreRepo = null,
            syncEngine = null,
            currentUserId = null
        )

        composeRule.setContent {
            AnyTaskTheme(themeMode = AppThemeMode.DARK) {
                TaskBoardScreen(
                    viewModel = viewModel,
                    isDarkGlass = true,
                    onToggleLightDark = {},
                    onOpenAccountSync = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("filter_priority_all").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithText("All Task").assertIsDisplayed()

        composeRule.onNodeWithTag("filter_priority_high").performClick()
        composeRule.waitForIdle()
        assertEquals(TaskPriority.HIGH, viewModel.uiState.value.priorityFilter)

        composeRule.onNodeWithTag("filter_priority_all").performClick()
        composeRule.waitForIdle()
        assertNull(viewModel.uiState.value.priorityFilter)
    }

    @Test
    fun `delete all completed action removes all completed tasks after confirmation`() {
        val app = ApplicationProvider.getApplicationContext<AnyTaskApplication>()
        runBlocking {
            app.repository.initializeDefaults()
            val t1 = app.repository.saveTask(
                existingTaskId = null,
                listId = AnyTaskDatabase.DEFAULT_LIST_ID,
                title = "Done Task One",
                notes = "",
                priority = TaskPriority.MEDIUM,
                dueDateMillis = null,
                hasDueTime = false,
                reminderOffset = ReminderOffset.NONE,
                subtasks = emptyList()
            )
            val t2 = app.repository.saveTask(
                existingTaskId = null,
                listId = AnyTaskDatabase.DEFAULT_LIST_ID,
                title = "Done Task Two",
                notes = "",
                priority = TaskPriority.LOW,
                dueDateMillis = null,
                hasDueTime = false,
                reminderOffset = ReminderOffset.NONE,
                subtasks = emptyList()
            )
            app.repository.toggleTaskCompletion(t1.id, completed = true)
            app.repository.toggleTaskCompletion(t2.id, completed = true)
        }

        val viewModel = TaskBoardViewModel(
            repository = app.repository,
            prefs = app.preferences,
            firestoreRepo = null,
            syncEngine = null,
            currentUserId = null
        )

        composeRule.setContent {
            AnyTaskTheme(themeMode = AppThemeMode.DARK) {
                TaskBoardScreen(
                    viewModel = viewModel,
                    isDarkGlass = true,
                    onToggleLightDark = {},
                    onOpenAccountSync = {}
                )
            }
        }

        composeRule.waitUntil(5_000) { viewModel.uiState.value.completedTasks.size >= 2 }

        composeRule.onNodeWithTag("delete_all_completed_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("delete_all_completed_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_delete_all_completed_button").performClick()

        val deadline = System.currentTimeMillis() + 5_000L
        while (viewModel.uiState.value.completedTasks.isNotEmpty() && System.currentTimeMillis() < deadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            composeRule.waitForIdle()
            if (viewModel.uiState.value.completedTasks.isNotEmpty()) {
                Thread.sleep(50)
            }
        }
        assertEquals(0, viewModel.uiState.value.completedTasks.size)
    }

    @Test
    fun `widget dropdown toggles lists cleanly and in-place completion does not launch MainActivity`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val anyTaskApp = app as AnyTaskApplication

        val task = runBlocking {
            anyTaskApp.repository.initializeDefaults()
            anyTaskApp.repository.saveTask(
                existingTaskId = null,
                listId = AnyTaskDatabase.DEFAULT_LIST_ID,
                title = "Widget Inline Complete",
                notes = "",
                priority = TaskPriority.HIGH,
                dueDateMillis = null,
                hasDueTime = false,
                reminderOffset = ReminderOffset.NONE,
                subtasks = emptyList()
            )
        }

        val itemView = LayoutInflater.from(app).inflate(R.layout.widget_task_item, null, false)
        val titleView = itemView.findViewById<TextView>(R.id.widget_item_title)
        val metaView = itemView.findViewById<TextView>(R.id.widget_item_meta)
        val badgeView = itemView.findViewById<TextView>(R.id.widget_item_priority_badge)

        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, titleView.layoutParams.height)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, metaView.layoutParams.height)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, badgeView.layoutParams.height)
        assertTrue(titleView.includeFontPadding)
        assertTrue(metaView.includeFontPadding)
        assertTrue(titleView.lineSpacingExtra > 0f)

        val dropdownItemView = LayoutInflater.from(app).inflate(R.layout.widget_list_dropdown_item, null, false)
        val dropdownLabel = dropdownItemView.findViewById<TextView>(R.id.widget_list_item_name)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, dropdownLabel.layoutParams.height)
        assertTrue(dropdownLabel.includeFontPadding)

        val manager = AppWidgetManager.getInstance(app)
        val shadowManager = shadowOf(manager)
        val widgetId = shadowManager.createWidget(AnyTaskWidgetProvider::class.java, R.layout.widget_anytask)
        val prefs = PreferencesManager(app)
        val provider = AnyTaskWidgetProvider()

        // Verify dropdown RemoteViewsFactory builds valid RemoteViews that apply without launcher ActionException
        val listFactory = AnyTaskWidgetListFactory(app, widgetId)
        listFactory.onDataSetChanged()
        assertTrue(listFactory.count >= 2)
        val container = FrameLayout(app)
        val appliedLabels = (0 until listFactory.count).map { index ->
            val applied = listFactory.getViewAt(index).apply(app, container)
            applied.findViewById<TextView>(R.id.widget_list_item_name).text.toString()
        }
        assertTrue(appliedLabels.contains("All Lists"))
        assertTrue(appliedLabels.contains("My Tasks"))

        // Verify task row RemoteViewsFactory applies cleanly without launcher ActionException
        val taskFactory = AnyTaskWidgetFactory(app, widgetId)
        taskFactory.onDataSetChanged()
        assertTrue(taskFactory.count >= 1)
        val appliedTaskView = taskFactory.getViewAt(0).apply(app, container)
        assertEquals("Widget Inline Complete", appliedTaskView.findViewById<TextView>(R.id.widget_item_title).text.toString())

        // Toggle dropdown open
        provider.onReceive(
            app,
            Intent(AnyTaskWidgetProvider.ACTION_TOGGLE_LIST_MENU).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
        )
        assertTrue(prefs.isWidgetDropdownOpen(widgetId))
        val openView = shadowManager.getViewFor(widgetId)
        assertEquals(
            View.VISIBLE,
            openView.findViewById<View>(R.id.widget_list_dropdown_container).visibility
        )

        // Select default list
        provider.onReceive(
            app,
            Intent(AnyTaskWidgetProvider.ACTION_SELECT_LIST).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(AnyTaskWidgetProvider.EXTRA_SELECTED_LIST_ID, AnyTaskDatabase.DEFAULT_LIST_ID)
            }
        )
        assertEquals(AnyTaskDatabase.DEFAULT_LIST_ID, prefs.getWidgetListId(widgetId))
        assertFalse(prefs.isWidgetDropdownOpen(widgetId))
        val closedView = shadowManager.getViewFor(widgetId)
        assertEquals(
            View.GONE,
            closedView.findViewById<View>(R.id.widget_list_dropdown_container).visibility
        )
        assertEquals("My Tasks", closedView.findViewById<TextView>(R.id.widget_list_title).text.toString())

        // In-place completion via check circle does NOT start MainActivity
        shadowOf(app).clearNextStartedActivities()
        provider.onReceive(
            app,
            Intent(AnyTaskWidgetProvider.ACTION_WIDGET_ITEM_CLICK).apply {
                putExtra(AnyTaskWidgetProvider.EXTRA_ITEM_ACTION, AnyTaskWidgetProvider.ITEM_ACTION_COMPLETE)
                putExtra(AnyTaskWidgetProvider.EXTRA_WIDGET_TASK_ID, task.id)
            }
        )
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertNull(shadowOf(app).nextStartedActivity)

        // Tapping task row body DOES open MainActivity to that task
        provider.onReceive(
            app,
            Intent(AnyTaskWidgetProvider.ACTION_WIDGET_ITEM_CLICK).apply {
                putExtra(AnyTaskWidgetProvider.EXTRA_ITEM_ACTION, AnyTaskWidgetProvider.ITEM_ACTION_OPEN)
                putExtra(AnyTaskWidgetProvider.EXTRA_WIDGET_TASK_ID, task.id)
            }
        )
        val launched = shadowOf(app).nextStartedActivity
        assertNotNull(launched)
        assertEquals(task.id, launched.getStringExtra(MainActivity.EXTRA_OPEN_TASK_ID))
    }

    @Test
    fun `completion sound player respects preference, ringer mode, volume, and DND`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioManager = context.getSystemService(AudioManager::class.java)

        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 5, 0)

        assertTrue(CompletionSoundPlayer.shouldPlayBeep(context, preferenceEnabled = true))
        assertFalse(CompletionSoundPlayer.shouldPlayBeep(context, preferenceEnabled = false))

        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
        assertFalse(CompletionSoundPlayer.shouldPlayBeep(context, preferenceEnabled = true))

        assertTrue(
            CompletionSoundPlayer.shouldPlayBeepForState(
                preferenceEnabled = true,
                ringerMode = AudioManager.RINGER_MODE_NORMAL,
                notifVolume = 4,
                systemVolume = 3,
                isNotifMuted = false,
                interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL
            )
        )
        assertFalse(
            CompletionSoundPlayer.shouldPlayBeepForState(
                preferenceEnabled = true,
                ringerMode = AudioManager.RINGER_MODE_NORMAL,
                notifVolume = 4,
                systemVolume = 3,
                isNotifMuted = false,
                interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
        )
        assertFalse(
            CompletionSoundPlayer.shouldPlayBeepForState(
                preferenceEnabled = true,
                ringerMode = AudioManager.RINGER_MODE_NORMAL,
                notifVolume = 0,
                systemVolume = 0,
                isNotifMuted = true,
                interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL
            )
        )

        // Verify chime synthesis produces non-zero PCM waveform
        val pcm = CompletionSoundPlayer.synthesizeCompletionChimePcm()
        assertTrue(pcm.isNotEmpty())
        assertTrue(pcm.any { it != 0.toShort() })
    }
}
