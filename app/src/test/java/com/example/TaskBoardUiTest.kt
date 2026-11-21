package com.example

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.AppThemeMode
import com.example.ui.screens.AccountSyncScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.AnyTaskTheme
import com.example.ui.viewmodel.AuthSyncViewModel
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaskBoardUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
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
    fun `account sync screen renders only Google sync option and modern Sync Now button`() {
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
        composeRule.onNodeWithText("Google Account & Sync").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_now_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("provider_google_connect_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("provider_microsoft_connect_button").assertDoesNotExist()
        composeRule.onNodeWithTag("provider_facebook_connect_button").assertDoesNotExist()
    }

    @Test
    fun `widget task item layout uses wrap_content heights and includeFontPadding for descenders`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val itemView = LayoutInflater.from(context).inflate(R.layout.widget_task_item, null, false)
        val titleView = itemView.findViewById<TextView>(R.id.widget_item_title)
        val metaView = itemView.findViewById<TextView>(R.id.widget_item_meta)
        val badgeView = itemView.findViewById<TextView>(R.id.widget_item_priority_badge)

        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, titleView.layoutParams.height)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, metaView.layoutParams.height)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, badgeView.layoutParams.height)
        assertTrue(titleView.includeFontPadding)
        assertTrue(metaView.includeFontPadding)
        assertTrue(titleView.lineSpacingExtra > 0f)
    }
}
