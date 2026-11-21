package com.example.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.local.AnyTaskDatabase
import com.example.domain.model.ReminderOffset
import com.example.domain.model.Subtask
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskList
import com.example.domain.model.TaskPriority
import com.example.notifications.DeadlineAlarmScheduler
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.theme.LocalLiquidGlassPalette
import com.example.ui.theme.accentColor
import com.example.ui.theme.glassTintColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(
    existingTask: TaskItem?,
    lists: List<TaskList>,
    defaultListId: String,
    onDismiss: () -> Unit,
    onSave: (
        listId: String,
        title: String,
        notes: String,
        priority: TaskPriority,
        dueDateMillis: Long?,
        hasDueTime: Boolean,
        reminderOffset: ReminderOffset,
        subtasks: List<Subtask>
    ) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val palette = LocalLiquidGlassPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember(existingTask) { mutableStateOf(existingTask?.title.orEmpty()) }
    var notes by remember(existingTask) { mutableStateOf(existingTask?.notes.orEmpty()) }
    var selectedListId by remember(existingTask, defaultListId) {
        mutableStateOf(existingTask?.listId ?: defaultListId)
    }
    var priority by remember(existingTask) {
        mutableStateOf(existingTask?.priority ?: TaskPriority.LOW)
    }
    var dueDateMillis by remember(existingTask) { mutableStateOf(existingTask?.dueDateMillis) }
    var hasDueTime by remember(existingTask) { mutableStateOf(existingTask?.hasDueTime ?: false) }
    var reminderOffset by remember(existingTask) {
        mutableStateOf(existingTask?.reminderOffset ?: ReminderOffset.AT_TIME)
    }
    val subtasks = remember(existingTask) {
        mutableStateListOf<Subtask>().apply {
            addAll(existingTask?.subtasks.orEmpty())
        }
    }
    var newSubtaskTitle by remember { mutableStateOf("") }
    var showNotifRationale by remember { mutableStateOf(false) }
    var showExactAlarmRationale by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && !DeadlineAlarmScheduler.canScheduleExactAlarms(context)) {
            showExactAlarmRationale = true
        }
    }

    fun checkReminderPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!notifGranted) {
                showNotifRationale = true
                return
            }
        }
        if (!DeadlineAlarmScheduler.canScheduleExactAlarms(context)) {
            showExactAlarmRationale = true
        }
    }

    fun openDatePicker() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dueDateMillis ?: System.currentTimeMillis()
        }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val updated = Calendar.getInstance().apply {
                    timeInMillis = dueDateMillis ?: System.currentTimeMillis()
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    if (!hasDueTime) {
                        set(Calendar.HOUR_OF_DAY, 9)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }
                }
                dueDateMillis = updated.timeInMillis
                checkReminderPermissionsIfNeeded()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun openTimePicker() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dueDateMillis ?: System.currentTimeMillis()
        }
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val updated = Calendar.getInstance().apply {
                    timeInMillis = dueDateMillis ?: System.currentTimeMillis()
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }
                dueDateMillis = updated.timeInMillis
                hasDueTime = true
                checkReminderPermissionsIfNeeded()
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            false
        ).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (palette.isDark) palette.canvasSecondary else palette.canvasBase,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existingTask == null) {
                        stringResource(R.string.new_task_title)
                    } else {
                        stringResource(R.string.edit_task_title)
                    },
                    style = MaterialTheme.typography.headlineSmall
                )
                Row {
                    if (existingTask != null && onDelete != null) {
                        IconButton(
                            onClick = {
                                onDelete(existingTask.id)
                                onDismiss()
                            },
                            modifier = Modifier.testTag("delete_task_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete task",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close editor")
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.task_title_label)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_title_input")
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.task_notes_label)) },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_notes_input")
            )

            // Quick one-tap Priority Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Priority Level",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TaskPriority.entries.forEach { option ->
                        val selected = priority == option
                        val accent = option.accentColor()
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("editor_priority_${option.name.lowercase()}")
                                .clickable { priority = option },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) option.glassTintColor(palette.isDark) else palette.glassSurfaceBottom,
                            border = BorderStroke(
                                width = if (selected) 1.6.dp else 1.dp,
                                color = if (selected) accent else palette.rimEnd
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                )
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Task List Picker
            if (lists.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Task List",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        lists.forEach { list ->
                            FilterChip(
                                selected = selectedListId == list.id,
                                onClick = { selectedListId = list.id },
                                label = { Text(list.name) }
                            )
                        }
                    }
                }
            }

            // Due Date, Due Time & Early Reminder Offset
            LiquidGlassSurface(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Deadline & Reminder",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = dueDateMillis != null,
                            onClick = { openDatePicker() },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                val label = dueDateMillis?.let {
                                    SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(it))
                                } ?: "Set Date"
                                Text(label)
                            }
                        )
                        FilterChip(
                            selected = hasDueTime && dueDateMillis != null,
                            onClick = { openTimePicker() },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                val label = if (hasDueTime && dueDateMillis != null) {
                                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(dueDateMillis!!))
                                } else {
                                    "Set Time"
                                }
                                Text(label)
                            }
                        )
                        if (dueDateMillis != null) {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    dueDateMillis = null
                                    hasDueTime = false
                                },
                                label = { Text("Clear") }
                            )
                        }
                    }

                    if (dueDateMillis != null) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReminderOffset.entries.forEach { offset ->
                                FilterChip(
                                    selected = reminderOffset == offset,
                                    onClick = {
                                        reminderOffset = offset
                                        if (offset != ReminderOffset.NONE) {
                                            checkReminderPermissionsIfNeeded()
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (offset == ReminderOffset.NONE) Icons.Rounded.NotificationsOff else Icons.Rounded.Alarm,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    },
                                    label = { Text(offset.label) }
                                )
                            }
                        }
                    }
                }
            }

            // Subtasks Management
            LiquidGlassSurface(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.subtasks_header),
                        style = MaterialTheme.typography.titleSmall
                    )

                    subtasks.forEachIndexed { index, sub ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = sub.completed,
                                onCheckedChange = { checked ->
                                    subtasks[index] = sub.copy(completed = checked)
                                }
                            )
                            Text(
                                text = sub.title,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { subtasks.removeAt(index) }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove subtask",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newSubtaskTitle,
                            onValueChange = { newSubtaskTitle = it },
                            placeholder = { Text(stringResource(R.string.add_subtask_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("new_subtask_input")
                        )
                        IconButton(
                            onClick = {
                                val cleaned = newSubtaskTitle.trim()
                                if (cleaned.isNotEmpty()) {
                                    subtasks += Subtask(
                                        id = AnyTaskDatabase.newValidId(),
                                        taskId = existingTask?.id.orEmpty(),
                                        title = cleaned,
                                        completed = false,
                                        sortOrder = subtasks.size
                                    )
                                    newSubtaskTitle = ""
                                }
                            },
                            modifier = Modifier.testTag("add_subtask_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add subtask")
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val trimmed = title.trim()
                    if (trimmed.isNotEmpty()) {
                        onSave(
                            selectedListId,
                            trimmed,
                            notes,
                            priority,
                            dueDateMillis,
                            hasDueTime,
                            reminderOffset,
                            subtasks.toList()
                        )
                        onDismiss()
                    }
                },
                enabled = title.trim().isNotEmpty(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_task_button")
            ) {
                Text(
                    text = if (existingTask == null) "Create Task" else "Save Changes",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showNotifRationale) {
        AlertDialog(
            onDismissRequest = { showNotifRationale = false },
            title = { Text("Deadline Notifications") },
            text = { Text(stringResource(R.string.notification_permission_rationale)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotifRationale = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                ) {
                    Text("Allow Notifications")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotifRationale = false }) {
                    Text("Not Now")
                }
            }
        )
    }

    if (showExactAlarmRationale) {
        AlertDialog(
            onDismissRequest = { showExactAlarmRationale = false },
            title = { Text("Exact Deadline Timing") },
            text = { Text(stringResource(R.string.exact_alarm_rationale)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExactAlarmRationale = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            runCatching { context.startActivity(intent) }
                        }
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExactAlarmRationale = false }) {
                    Text("Use Approximate")
                }
            }
        )
    }
}
