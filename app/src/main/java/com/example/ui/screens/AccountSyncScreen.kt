package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.AppThemeMode
import com.example.domain.model.ProviderAccountStatus
import com.example.domain.model.SyncState
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.auth.signOutGoogle
import com.example.ui.components.GoogleGLogo
import com.example.ui.components.GoogleSyncLogo
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.theme.LocalLiquidGlassPalette
import com.example.ui.viewmodel.AuthSyncViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSyncScreen(
    viewModel: AuthSyncViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val palette = LocalLiquidGlassPalette.current
    var googleLoading by remember { mutableStateOf(false) }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatusMessage()
    }

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 620.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Bar
                    LiquidGlassSurface(elevated = true, shape = RoundedCornerShape(22.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("accounts_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back to tasks",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = stringResource(R.string.account_sync_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }

                    // Theme Glass Switcher using Material 3 SegmentedButton
                    LiquidGlassSurface(shape = RoundedCornerShape(22.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Liquid Glass Appearance",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                AppThemeMode.entries.forEachIndexed { index, mode ->
                                    SegmentedButton(
                                        selected = state.themeMode == mode,
                                        onClick = { viewModel.setThemeMode(mode) },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = AppThemeMode.entries.size
                                        ),
                                        colors = SegmentedButtonDefaults.colors(
                                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            inactiveContainerColor = Color.Transparent,
                                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            activeBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                            inactiveBorderColor = palette.divider
                                        ),
                                        modifier = Modifier.testTag("theme_chip_${mode.name.lowercase()}")
                                    ) {
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Modern Material 3 Cloud Sync & Conflict Engine Card
                    LiquidGlassSurface(elevated = true, shape = RoundedCornerShape(24.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Google Cloud Sync",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val formattedTime = state.lastSyncedMillis?.let {
                                        SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(it))
                                    } ?: stringResource(R.string.last_synced_never)
                                    Text(
                                        text = "Last synced: $formattedTime",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                val isSyncing = state.syncState is SyncState.Syncing
                                val statusText = when {
                                    isSyncing -> "Syncing"
                                    state.googleStatus.connected -> "Active"
                                    else -> "Local Only"
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (state.googleStatus.connected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        palette.glassSurfaceBottom
                                    },
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (state.googleStatus.connected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                        } else {
                                            palette.divider
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (state.googleStatus.connected) {
                                                Icons.Rounded.CloudDone
                                            } else {
                                                Icons.Rounded.CloudOff
                                            },
                                            contentDescription = null,
                                            tint = if (state.googleStatus.connected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            // Modern Material Design 3 Sync Now action button (full-width, single-line)
                            FilledTonalButton(
                                onClick = { viewModel.syncNow() },
                                enabled = state.syncState !is SyncState.Syncing,
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("sync_now_button")
                            ) {
                                if (state.syncState is SyncState.Syncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Syncing Tasks…",
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.sync_now),
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            HorizontalDivider(color = palette.divider)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Automatic Background Sync",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Last-write-wins conflict resolution",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = state.autoSyncEnabled,
                                    onCheckedChange = viewModel::setAutoSyncEnabled
                                )
                            }

                            if (state.recentConflicts.isNotEmpty()) {
                                HorizontalDivider(color = palette.divider)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Recent Conflict Resolutions",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    state.recentConflicts.take(3).forEach { conflict ->
                                        Text(
                                            text = "• \"${conflict.itemTitle}\" -> ${conflict.winner}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Google Account Card (only sync provider)
                    GoogleAccountSyncCard(
                        status = state.googleStatus,
                        avatarUrl = state.googlePhotoUrl,
                        isBusy = googleLoading,
                        onConnect = {
                            googleLoading = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    googleLoading = false
                                    viewModel.syncNow()
                                },
                                onAuthError = { googleLoading = false },
                                scope = scope,
                                onAuthCancelled = { googleLoading = false }
                            )
                        },
                        onDisconnect = {
                            signOutGoogle(credentialManager, scope)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun GoogleAccountSyncCard(
    status: ProviderAccountStatus,
    avatarUrl: String?,
    isBusy: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val palette = LocalLiquidGlassPalette.current

    LiquidGlassSurface(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = status.accountName ?: "Google Account",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                    )
                } else {
                    GoogleSyncLogo(size = 48.dp)
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (status.connected) {
                            status.accountName ?: "Google Account"
                        } else {
                            "Google Account"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (status.connected) {
                            status.accountEmail ?: "Connected to Google Cloud Firestore"
                        } else {
                            "Sign in with Google to back up and sync lists"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    status.statusNote?.let { note ->
                        Text(
                            text = note,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (status.connected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            if (status.connected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, palette.divider),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("provider_google_disconnect_button")
                ) {
                    Text(
                        text = "Sign Out",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                Button(
                    onClick = onConnect,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (palette.isDark) Color.White else MaterialTheme.colorScheme.primary,
                        contentColor = if (palette.isDark) Color(0xFF0F172A) else MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("provider_google_connect_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                GoogleGLogo(size = 15.dp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.sign_in_with_google),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
