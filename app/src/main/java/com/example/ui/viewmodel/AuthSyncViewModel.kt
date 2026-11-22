package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AnyTaskRepository
import com.example.data.local.PreferencesManager
import com.example.domain.model.AppThemeMode
import com.example.domain.model.ProviderAccountStatus
import com.example.domain.model.SyncConflictSummary
import com.example.domain.model.SyncProviderType
import com.example.domain.model.SyncState
import com.example.ui.auth.authStateFlow
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountSyncUiState(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val googleStatus: ProviderAccountStatus = ProviderAccountStatus(SyncProviderType.GOOGLE),
    val lastSyncedMillis: Long? = null,
    val autoSyncEnabled: Boolean = true,
    val syncState: SyncState = SyncState.Idle,
    val recentConflicts: List<SyncConflictSummary> = emptyList(),
    val statusMessage: String? = null,
    val googlePhotoUrl: String? = null,
    val completionSoundEnabled: Boolean = true
)

class AuthSyncViewModel(
    private val repository: AnyTaskRepository,
    private val prefs: PreferencesManager
) : ViewModel() {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    private val _recentConflicts = MutableStateFlow<List<SyncConflictSummary>>(emptyList())
    private val _statusMessage = MutableStateFlow<String?>(null)

    private data class SyncMeta(
        val themeMode: AppThemeMode,
        val lastSynced: Long?,
        val autoSync: Boolean,
        val completionSound: Boolean,
        val syncState: SyncState,
        val conflicts: List<SyncConflictSummary>,
        val statusMessage: String?
    )

    private val metaFlow = combine(
        prefs.themeMode,
        prefs.lastSyncedMillis,
        prefs.autoSyncEnabled,
        prefs.completionSoundEnabled,
        _syncState
    ) { theme, lastSynced, autoSync, completionSound, syncState ->
        SyncMeta(theme, lastSynced, autoSync, completionSound, syncState, emptyList(), null)
    }.combine(_recentConflicts) { meta, conflicts ->
        meta.copy(conflicts = conflicts)
    }.combine(_statusMessage) { meta, msg ->
        meta.copy(statusMessage = msg)
    }

    val uiState: StateFlow<AccountSyncUiState> = combine(
        Firebase.auth.authStateFlow(),
        metaFlow
    ) { googleUser, meta ->
        val googleConnected = googleUser != null
        val googleStatus = ProviderAccountStatus(
            provider = SyncProviderType.GOOGLE,
            connected = googleConnected,
            accountName = googleUser?.displayName,
            accountEmail = googleUser?.email,
            configured = true,
            lastSyncedMillis = meta.lastSynced,
            statusNote = if (googleConnected) {
                "Google Cloud Firestore sync active"
            } else {
                "Sign in with Google to sync tasks across devices"
            }
        )
        AccountSyncUiState(
            themeMode = meta.themeMode,
            googleStatus = googleStatus,
            lastSyncedMillis = meta.lastSynced,
            autoSyncEnabled = meta.autoSync,
            syncState = meta.syncState,
            recentConflicts = meta.conflicts,
            statusMessage = meta.statusMessage,
            googlePhotoUrl = googleUser?.photoUrl?.toString(),
            completionSoundEnabled = meta.completionSound
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = AccountSyncUiState()
    )

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            prefs.setThemeMode(mode)
        }
    }

    fun toggleLightDarkGlass(currentlyDark: Boolean) {
        val next = if (currentlyDark) AppThemeMode.LIGHT else AppThemeMode.DARK
        setThemeMode(next)
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setAutoSyncEnabled(enabled)
        }
    }

    fun setCompletionSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setCompletionSoundEnabled(enabled)
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            repository.syncNow()
                .onSuccess { report ->
                    _recentConflicts.value = report.conflicts
                    _syncState.value = SyncState.Synced(report.timestampMillis, report.conflicts.size)
                    _statusMessage.value = if (report.conflicts.isEmpty()) {
                        "All tasks synced cleanly with Google."
                    } else {
                        "Synced with ${report.conflicts.size} last-write-wins resolution(s)."
                    }
                }
                .onFailure { error ->
                    val msg = error.localizedMessage ?: "Sign in with Google to sync."
                    _syncState.value = SyncState.Error(msg)
                    _statusMessage.value = msg
                }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
