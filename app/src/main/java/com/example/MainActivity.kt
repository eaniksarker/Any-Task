package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.domain.model.AppThemeMode
import com.example.ui.screens.AccountSyncScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.TaskBoardScreen
import com.example.ui.theme.AnyTaskTheme
import com.example.ui.viewmodel.AuthSyncViewModel
import com.example.ui.viewmodel.TaskBoardViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var pendingTaskIdState by mutableStateOf<String?>(null)
    private var pendingQuickAddState by mutableStateOf(false)
    private var pendingQuickAddListIdState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val app = application as AnyTaskApplication
            val themeMode by app.preferences.themeMode.collectAsStateWithLifecycle(
                initialValue = AppThemeMode.SYSTEM
            )

            AnyTaskTheme(themeMode = themeMode) {
                AnyTaskRoot(
                    themeMode = themeMode,
                    pendingOpenTaskId = pendingTaskIdState,
                    pendingQuickAdd = pendingQuickAddState,
                    pendingQuickAddListId = pendingQuickAddListIdState,
                    onConsumeDeepLink = {
                        pendingTaskIdState = null
                        pendingQuickAddState = false
                        pendingQuickAddListIdState = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val app = application as AnyTaskApplication

        // Direct completion from Home-Screen Widget checkbox
        val completeTaskId = intent.getStringExtra(EXTRA_COMPLETE_TASK_ID)
        if (!completeTaskId.isNullOrBlank()) {
            lifecycleScope.launch {
                app.repository.toggleTaskCompletion(completeTaskId, completed = true)
            }
            intent.removeExtra(EXTRA_COMPLETE_TASK_ID)
            return
        }

        // Open task editor from notification or widget row tap
        intent.getStringExtra(EXTRA_OPEN_TASK_ID)?.let { taskId ->
            pendingTaskIdState = taskId
        }

        // Open quick-add modal from widget '+' button
        if (intent.getBooleanExtra(EXTRA_QUICK_ADD, false)) {
            pendingQuickAddState = true
            pendingQuickAddListIdState = intent.getStringExtra(EXTRA_QUICK_ADD_LIST_ID)
        }
    }

    companion object {
        const val ROUTE_BOARD = "task_board"
        const val ROUTE_ACCOUNTS = "account_sync"

        const val EXTRA_OPEN_TASK_ID = "extra_open_task_id"
        const val EXTRA_COMPLETE_TASK_ID = "extra_complete_task_id"
        const val EXTRA_QUICK_ADD = "extra_quick_add"
        const val EXTRA_QUICK_ADD_LIST_ID = "extra_quick_add_list_id"
    }
}

@Composable
private fun AnyTaskRoot(
    themeMode: AppThemeMode,
    pendingOpenTaskId: String?,
    pendingQuickAdd: Boolean,
    pendingQuickAddListId: String?,
    onConsumeDeepLink: () -> Unit
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    AnimatedContent(
        targetState = currentUser?.uid,
        transitionSpec = {
            (fadeIn(tween(280)) + scaleIn(initialScale = 0.98f, animationSpec = tween(280)))
                .togetherWith(fadeOut(tween(200)))
        },
        label = "auth_to_dashboard"
    ) { uid ->
        if (uid == null) {
            AuthScreen(
                onAuthSuccess = { currentUser = Firebase.auth.currentUser }
            )
        } else {
            key(uid) {
                val navController = rememberNavController()
                val boardViewModel: TaskBoardViewModel = viewModel(
                    key = uid,
                    factory = viewModelFactory {
                        initializer {
                            val application = checkNotNull(this[APPLICATION_KEY]) as AnyTaskApplication
                            TaskBoardViewModel(
                                repository = application.repository,
                                prefs = application.preferences,
                                firestoreRepo = application.firestoreRepository,
                                syncEngine = application.syncEngine,
                                currentUserId = uid
                            )
                        }
                    }
                )
                val authSyncViewModel: AuthSyncViewModel = viewModel(
                    key = "sync_$uid",
                    factory = viewModelFactory {
                        initializer {
                            val application = checkNotNull(this[APPLICATION_KEY]) as AnyTaskApplication
                            AuthSyncViewModel(
                                repository = application.repository,
                                prefs = application.preferences
                            )
                        }
                    }
                )

                LaunchedEffect(pendingOpenTaskId, pendingQuickAdd, pendingQuickAddListId) {
                    if (pendingOpenTaskId != null || pendingQuickAdd) {
                        boardViewModel.setPendingDeepLink(pendingOpenTaskId, pendingQuickAdd, pendingQuickAddListId)
                        onConsumeDeepLink()
                    }
                }

                val isDarkGlass = when (themeMode) {
                    AppThemeMode.DARK -> true
                    AppThemeMode.LIGHT -> false
                    AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                }

                NavHost(
                    navController = navController,
                    startDestination = MainActivity.ROUTE_BOARD
                ) {
                    composable(MainActivity.ROUTE_BOARD) {
                        TaskBoardScreen(
                            viewModel = boardViewModel,
                            isDarkGlass = isDarkGlass,
                            onToggleLightDark = { authSyncViewModel.toggleLightDarkGlass(isDarkGlass) },
                            onOpenAccountSync = { navController.navigate(MainActivity.ROUTE_ACCOUNTS) }
                        )
                    }
                    composable(MainActivity.ROUTE_ACCOUNTS) {
                        AccountSyncScreen(
                            viewModel = authSyncViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
