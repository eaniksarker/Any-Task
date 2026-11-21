package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.AnyTaskDatabase
import com.example.domain.model.DueDateFilter
import com.example.domain.model.SyncState
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskList
import com.example.domain.model.TaskPriority
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.LiquidGlassCheckCircle
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.PriorityBadge
import com.example.ui.theme.LocalLiquidGlassPalette
import com.example.ui.theme.accentColor
import com.example.ui.theme.glassTintColor
import com.example.ui.viewmodel.TaskBoardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TaskBoardScreen(
    viewModel: TaskBoardViewModel,
    isDarkGlass: Boolean,
    onToggleLightDark: () -> Unit,
    onOpenAccountSync: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingOpenTaskId by viewModel.pendingOpenTaskId.collectAsStateWithLifecycle()
    val pendingQuickAdd by viewModel.pendingQuickAdd.collectAsStateWithLifecycle()
    val palette = LocalLiquidGlassPalette.current

    var showSearch by remember { mutableStateOf(false) }
    var editorTask by remember { mutableStateOf<TaskItem?>(null) }
    var showEditorForNew by remember { mutableStateOf(false) }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var listToRename by remember { mutableStateOf<TaskList?>(null) }
    var listToDelete by remember { mutableStateOf<TaskList?>(null) }

    // Handle deep links from widget or notifications
    LaunchedEffect(pendingOpenTaskId, state.activeTasks, state.completedTasks) {
        val targetId = pendingOpenTaskId ?: return@LaunchedEffect
        val found = (state.activeTasks + state.completedTasks).firstOrNull { it.id == targetId }
        if (found != null) {
            editorTask = found
            showEditorForNew = false
            viewModel.consumePendingOpenTask()
        }
    }

    LaunchedEffect(pendingQuickAdd) {
        if (pendingQuickAdd) {
            editorTask = null
            showEditorForNew = true
            viewModel.consumePendingQuickAdd()
        }
    }

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        editorTask = null
                        showEditorForNew = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("add_task_fab")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.add_task_fab)
                    )
                }
            }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val isWide = maxWidth >= 720.dp

                Row(modifier = Modifier.fillMaxSize()) {
                    if (isWide) {
                        // Canonical Tablet/Foldable side rail for Task Lists
                        LiquidGlassSurface(
                            elevated = true,
                            shape = RoundedCornerShape(26.dp),
                            modifier = Modifier
                                .width(240.dp)
                                .fillMaxHeight()
                                .padding(start = 16.dp, top = 12.dp, bottom = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Lists", style = MaterialTheme.typography.titleMedium)
                                    IconButton(onClick = { showCreateListDialog = true }) {
                                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Create list")
                                    }
                                }
                                ListChipRowItem(
                                    label = stringResource(R.string.all_lists_label),
                                    selected = state.selectedListId == null,
                                    onClick = { viewModel.selectList(null) }
                                )
                                state.lists.forEach { list ->
                                    ListChipRowItem(
                                        label = list.name,
                                        selected = state.selectedListId == list.id,
                                        onClick = { viewModel.selectList(list.id) },
                                        onRename = { listToRename = list },
                                        onDelete = if (!list.defaultList) ({ listToDelete = list }) else null
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Top Glass Header
                        LiquidGlassSurface(
                            elevated = true,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val syncLabel = when (val s = state.syncState) {
                                        is SyncState.Synced -> "Synced"
                                        SyncState.Syncing -> "Syncing…"
                                        SyncState.Offline -> "Offline Local"
                                        is SyncState.Error -> "Sync paused"
                                        SyncState.Idle -> "Ready"
                                    }
                                    Text(
                                        text = "${state.activeTasks.size} active • $syncLabel",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { showSearch = !showSearch },
                                        modifier = Modifier.testTag("toggle_search_button")
                                    ) {
                                        Icon(
                                            Icons.Rounded.Search,
                                            contentDescription = "Search tasks",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    IconButton(
                                        onClick = onToggleLightDark,
                                        modifier = Modifier.testTag("toggle_theme_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isDarkGlass) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                            contentDescription = "Toggle Light/Dark Glass",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.triggerManualSync() },
                                        modifier = Modifier.testTag("quick_sync_button")
                                    ) {
                                        Icon(
                                            imageVector = when (state.syncState) {
                                                SyncState.Offline -> Icons.Rounded.CloudOff
                                                is SyncState.Synced -> Icons.Rounded.CloudDone
                                                else -> Icons.Rounded.Sync
                                            },
                                            contentDescription = stringResource(R.string.sync_now),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = onOpenAccountSync,
                                        modifier = Modifier.testTag("open_accounts_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ManageAccounts,
                                            contentDescription = stringResource(R.string.account_sync_title),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Expandable Search Bar
                        AnimatedVisibility(visible = showSearch) {
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = viewModel::updateSearchQuery,
                                placeholder = { Text(stringResource(R.string.search_tasks_hint)) },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (state.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_tasks_input")
                            )
                        }

                        // Mobile Horizontal List Switcher
                        if (!isWide) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = state.selectedListId == null,
                                    onClick = { viewModel.selectList(null) },
                                    label = { Text(stringResource(R.string.all_lists_label)) },
                                    modifier = Modifier.testTag("list_chip_all")
                                )
                                state.lists.forEach { list ->
                                    val selected = state.selectedListId == list.id
                                    FilterChip(
                                        selected = selected,
                                        onClick = { viewModel.selectList(list.id) },
                                        label = { Text(list.name) },
                                        trailingIcon = if (selected) {
                                            {
                                                Icon(
                                                    Icons.Rounded.Edit,
                                                    contentDescription = "Manage list",
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable { listToRename = list }
                                                )
                                            }
                                        } else null,
                                        modifier = Modifier.testTag("list_chip_${list.id}")
                                    )
                                }
                                FilterChip(
                                    selected = false,
                                    onClick = { showCreateListDialog = true },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    label = { Text("New List") },
                                    modifier = Modifier.testTag("create_list_chip")
                                )
                            }
                        }

                        // Priority & Due Date Filter Chip Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TaskPriority.entries.forEach { prio ->
                                val selected = state.priorityFilter == prio
                                val accent = prio.accentColor()
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setPriorityFilter(prio) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(accent)
                                        )
                                    },
                                    label = { Text(prio.label) },
                                    modifier = Modifier.testTag("filter_priority_${prio.name.lowercase()}")
                                )
                            }

                            DueDateFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = state.dueDateFilter == filter,
                                    onClick = { viewModel.setDueDateFilter(filter) },
                                    label = { Text(filter.label) },
                                    modifier = Modifier.testTag("filter_due_${filter.name.lowercase()}")
                                )
                            }
                        }

                        // Task List with Drag-and-Drop Reorder + Collapsible Completed Section
                        if (state.activeTasks.isEmpty() && state.completedTasks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                LiquidGlassSurface(
                                    shape = RoundedCornerShape(28.dp),
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircleOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.empty_tasks_title),
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Text(
                                            text = stringResource(R.string.empty_tasks_subtitle),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            var draggedIndex by remember { mutableIntStateOf(-1) }
                            var dragOffsetY by remember { mutableFloatStateOf(0f) }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(
                                    items = state.activeTasks,
                                    key = { _, item -> item.id }
                                ) { index, task ->
                                    val isDragging = draggedIndex == index
                                    val cardElevation by animateDpAsState(
                                        targetValue = if (isDragging) 18.dp else 6.dp,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        label = "drag_elevation"
                                    )

                                    TaskGlassCard(
                                        task = task,
                                        elevation = cardElevation,
                                        modifier = Modifier
                                            .zIndex(if (isDragging) 1f else 0f)
                                            .offset {
                                                IntOffset(
                                                    0,
                                                    if (isDragging) dragOffsetY.roundToInt() else 0
                                                )
                                            }
                                            .pointerInput(state.activeTasks.size) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        draggedIndex = index
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragEnd = {
                                                        draggedIndex = -1
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragCancel = {
                                                        draggedIndex = -1
                                                        dragOffsetY = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffsetY += dragAmount.y
                                                        val thresholdPx = 110f
                                                        if (dragOffsetY > thresholdPx && draggedIndex < state.activeTasks.lastIndex) {
                                                            val nextIdx = draggedIndex + 1
                                                            viewModel.moveActiveTask(draggedIndex, nextIdx)
                                                            draggedIndex = nextIdx
                                                            dragOffsetY -= thresholdPx
                                                        } else if (dragOffsetY < -thresholdPx && draggedIndex > 0) {
                                                            val prevIdx = draggedIndex - 1
                                                            viewModel.moveActiveTask(draggedIndex, prevIdx)
                                                            draggedIndex = prevIdx
                                                            dragOffsetY += thresholdPx
                                                        }
                                                    }
                                                )
                                            },
                                        onToggleComplete = { viewModel.toggleTaskCompletion(task.id) },
                                        onToggleSubtask = { subId ->
                                            viewModel.toggleSubtaskCompletion(task.id, subId)
                                        },
                                        onClick = {
                                            editorTask = task
                                            showEditorForNew = false
                                        }
                                    )
                                }

                                // Collapsible Completed Section
                                if (state.completedTasks.isNotEmpty()) {
                                    item(key = "completed_header") {
                                        LiquidGlassSurface(
                                            shape = RoundedCornerShape(18.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                                .testTag("completed_section_header")
                                                .clickable { viewModel.toggleCompletedExpanded() }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${stringResource(R.string.completed_section_title)} (${state.completedTasks.size})",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Icon(
                                                    imageVector = if (state.completedExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                                    contentDescription = "Toggle completed tasks",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    item(key = "completed_items") {
                                        AnimatedVisibility(
                                            visible = state.completedExpanded,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut()
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                state.completedTasks.forEach { task ->
                                                    TaskGlassCard(
                                                        task = task,
                                                        elevation = 2.dp,
                                                        showDragHandle = false,
                                                        onToggleComplete = { viewModel.toggleTaskCompletion(task.id) },
                                                        onToggleSubtask = { subId ->
                                                            viewModel.toggleSubtaskCompletion(task.id, subId)
                                                        },
                                                        onClick = {
                                                            editorTask = task
                                                            showEditorForNew = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditorForNew || editorTask != null) {
        val defaultListId = state.selectedListId
            ?: state.lists.firstOrNull()?.id
            ?: AnyTaskDatabase.DEFAULT_LIST_ID

        TaskEditorSheet(
            existingTask = editorTask,
            lists = state.lists,
            defaultListId = defaultListId,
            onDismiss = {
                showEditorForNew = false
                editorTask = null
            },
            onSave = { listId, title, notes, priority, dueDateMillis, hasDueTime, reminderOffset, subtasks ->
                viewModel.saveTask(
                    existingTaskId = editorTask?.id,
                    listId = listId,
                    title = title,
                    notes = notes,
                    priority = priority,
                    dueDateMillis = dueDateMillis,
                    hasDueTime = hasDueTime,
                    reminderOffset = reminderOffset,
                    subtasks = subtasks
                )
            },
            onDelete = { taskId -> viewModel.deleteTask(taskId) }
        )
    }

    if (showCreateListDialog) {
        var listName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateListDialog = false },
            title = { Text(stringResource(R.string.new_list_title)) },
            text = {
                OutlinedTextField(
                    value = listName,
                    onValueChange = { listName = it },
                    label = { Text("List name") },
                    singleLine = true,
                    modifier = Modifier.testTag("new_list_name_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (listName.trim().isNotEmpty()) {
                            viewModel.createList(listName)
                            showCreateListDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_list_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateListDialog = false }) { Text("Cancel") }
            }
        )
    }

    listToRename?.let { target ->
        var newName by remember(target) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { listToRename = null },
            title = { Text(stringResource(R.string.rename_list_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true
                    )
                    if (!target.defaultList) {
                        TextButton(
                            onClick = {
                                listToRename = null
                                listToDelete = target
                            }
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(" Delete this list", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.trim().isNotEmpty()) {
                            viewModel.renameList(target.id, newName)
                            listToRename = null
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { listToRename = null }) { Text("Cancel") }
            }
        )
    }

    listToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { listToDelete = null },
            title = { Text(stringResource(R.string.delete_list_title)) },
            text = { Text("Delete \"${target.name}\" and all tasks inside it?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteList(target.id)
                        listToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { listToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ListChipRowItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onRename != null) {
                IconButton(onClick = onRename, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Rename list", modifier = Modifier.size(16.dp))
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete list", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TaskGlassCard(
    task: TaskItem,
    elevation: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    showDragHandle: Boolean = true,
    onToggleComplete: () -> Unit,
    onToggleSubtask: (String) -> Unit,
    onClick: () -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    val priorityColor = task.priority.accentColor()
    val isOverdue = !task.completed && task.dueDateMillis != null && task.dueDateMillis < System.currentTimeMillis()
    var subtasksExpanded by remember { mutableStateOf(false) }

    LiquidGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        elevation = elevation,
        tintColor = if (!task.completed) task.priority.glassTintColor(palette.isDark).copy(alpha = 0.14f) else null,
        borderHighlightColor = if (!task.completed) priorityColor.copy(alpha = 0.55f) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Priority Vertical Accent Bar
            Box(
                modifier = Modifier
                    .padding(top = 6.dp, start = 2.dp, end = 4.dp)
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(priorityColor)
            )

            LiquidGlassCheckCircle(
                checked = task.completed,
                priority = task.priority,
                onToggle = onToggleComplete,
                modifier = Modifier.testTag("task_check_${task.id}")
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 6.dp, end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (task.completed) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    PriorityBadge(priority = task.priority)
                }

                if (task.notes.isNotBlank()) {
                    Text(
                        text = task.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    task.dueDateMillis?.let { millis ->
                        val formatted = if (task.hasDueTime) {
                            SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(millis))
                        } else {
                            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isOverdue) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                            } else {
                                palette.glassSurfaceBottom
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    tint = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = formatted,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (task.subtasks.isNotEmpty()) {
                        val doneCount = task.subtasks.count { it.completed }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = palette.glassSurfaceBottom,
                            modifier = Modifier.clickable { subtasksExpanded = !subtasksExpanded }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "$doneCount/${task.subtasks.size} subtasks",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = if (subtasksExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = "Expand subtasks",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = subtasksExpanded && task.subtasks.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        task.subtasks.forEach { sub ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleSubtask(sub.id) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = sub.completed,
                                    onCheckedChange = { onToggleSubtask(sub.id) }
                                )
                                Text(
                                    text = sub.title,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        textDecoration = if (sub.completed) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    color = if (sub.completed) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (showDragHandle && !task.completed) {
                Icon(
                    imageVector = Icons.Rounded.DragIndicator,
                    contentDescription = "Long press and drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .padding(top = 12.dp, end = 4.dp)
                        .size(20.dp)
                )
            }
        }
    }
}
