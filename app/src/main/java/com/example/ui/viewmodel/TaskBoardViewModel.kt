package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AnyTaskRepository
import com.example.data.local.PreferencesManager
import com.example.data.remote.FirestoreTaskRepository
import com.example.data.remote.SyncEngine
import com.example.domain.model.DueDateFilter
import com.example.domain.model.ReminderOffset
import com.example.domain.model.Subtask
import com.example.domain.model.SyncState
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskList
import com.example.domain.model.TaskPriority
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class TaskBoardUiState(
    val lists: List<TaskList> = emptyList(),
    val selectedListId: String? = null,
    val activeTasks: List<TaskItem> = emptyList(),
    val completedTasks: List<TaskItem> = emptyList(),
    val searchQuery: String = "",
    val priorityFilter: TaskPriority? = null,
    val dueDateFilter: DueDateFilter = DueDateFilter.ALL,
    val completedExpanded: Boolean = false,
    val syncState: SyncState = SyncState.Idle
)

class TaskBoardViewModel(
    private val repository: AnyTaskRepository,
    private val prefs: PreferencesManager,
    private val firestoreRepo: FirestoreTaskRepository?,
    private val syncEngine: SyncEngine?,
    private val currentUserId: String?
) : ViewModel() {

    private val _selectedListId = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _priorityFilter = MutableStateFlow<TaskPriority?>(null)
    private val _dueDateFilter = MutableStateFlow(DueDateFilter.ALL)
    private val _completedExpanded = MutableStateFlow(false)
    private val _syncState = MutableStateFlow<SyncState>(
        if (currentUserId == null) SyncState.Offline else SyncState.Idle
    )
    private val _pendingOpenTaskId = MutableStateFlow<String?>(null)
    val pendingOpenTaskId: StateFlow<String?> = _pendingOpenTaskId.asStateFlow()

    private val _pendingQuickAdd = MutableStateFlow(false)
    val pendingQuickAdd: StateFlow<Boolean> = _pendingQuickAdd.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private data class FilterInputs(
        val selectedListId: String?,
        val searchQuery: String,
        val priorityFilter: TaskPriority?,
        val dueDateFilter: DueDateFilter,
        val completedExpanded: Boolean
    )

    private val filterFlow = combine(
        _selectedListId,
        _searchQuery,
        _priorityFilter,
        _dueDateFilter,
        _completedExpanded
    ) { listId, query, prio, dueFilter, compExpanded ->
        FilterInputs(listId, query, prio, dueFilter, compExpanded)
    }

    val uiState: StateFlow<TaskBoardUiState> = combine(
        repository.taskListsFlow,
        repository.tasksFlow,
        filterFlow,
        _syncState
    ) { lists, tasks, filters, sync ->
        val (active, completed) = filterAndSplitTasks(
            tasks = tasks,
            selectedListId = filters.selectedListId,
            searchQuery = filters.searchQuery,
            priorityFilter = filters.priorityFilter,
            dueDateFilter = filters.dueDateFilter
        )
        TaskBoardUiState(
            lists = lists,
            selectedListId = filters.selectedListId,
            activeTasks = active,
            completedTasks = completed,
            searchQuery = filters.searchQuery,
            priorityFilter = filters.priorityFilter,
            dueDateFilter = filters.dueDateFilter,
            completedExpanded = filters.completedExpanded,
            syncState = sync
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = TaskBoardUiState()
    )

    init {
        viewModelScope.launch {
            repository.initializeDefaults()
        }
        if (!currentUserId.isNullOrBlank() && firestoreRepo != null && syncEngine != null) {
            startAuthenticatedCloudSync(currentUserId, firestoreRepo, syncEngine)
        }
    }

    private fun startAuthenticatedCloudSync(
        uid: String,
        fsRepo: FirestoreTaskRepository,
        engine: SyncEngine
    ) {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            engine.syncWithCloud(uid)
                .onSuccess { report ->
                    _syncState.value = SyncState.Synced(report.timestampMillis, report.conflicts.size)
                }
                .onFailure { error ->
                    _syncState.value = SyncState.Error(error.localizedMessage ?: "Cloud sync paused")
                }
        }

        // Two-tier Flow error handling for real-time Cloud Firestore snapshots
        viewModelScope.launch {
            combine(
                fsRepo.observeTaskLists(uid),
                fsRepo.observeTasks(uid)
            ) { remoteLists, remoteTasks ->
                remoteLists to remoteTasks
            }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing Firestore sync stream", rootError)
                    _syncState.value = SyncState.Error(rootError.message ?: "Sync stream error")
                }
                .collect { (remoteLists, remoteTasks) ->
                    engine.applyRemoteSnapshot(remoteLists, remoteTasks)
                    _syncState.value = SyncState.Synced(System.currentTimeMillis())
                }
        }
    }

    fun selectList(listId: String?) {
        _selectedListId.value = listId
        viewModelScope.launch { prefs.setSelectedListId(listId) }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setPriorityFilter(priority: TaskPriority?) {
        _priorityFilter.value = if (priority == null || _priorityFilter.value == priority) null else priority
    }

    fun setDueDateFilter(filter: DueDateFilter) {
        _dueDateFilter.value = filter
    }

    fun toggleCompletedExpanded() {
        _completedExpanded.value = !_completedExpanded.value
    }

    fun createList(name: String, colorHex: String = "#38BDF8") {
        viewModelScope.launch {
            val created = repository.createTaskList(name, colorHex)
            _selectedListId.value = created.id
        }
    }

    fun renameList(listId: String, newName: String) {
        viewModelScope.launch {
            repository.renameTaskList(listId, newName)
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch {
            repository.deleteTaskList(listId)
            if (_selectedListId.value == listId) {
                _selectedListId.value = null
            }
        }
    }

    fun saveTask(
        existingTaskId: String?,
        listId: String,
        title: String,
        notes: String,
        priority: TaskPriority,
        dueDateMillis: Long?,
        hasDueTime: Boolean,
        reminderOffset: ReminderOffset,
        subtasks: List<Subtask>
    ) {
        viewModelScope.launch {
            repository.saveTask(
                existingTaskId = existingTaskId,
                listId = listId,
                title = title,
                notes = notes,
                priority = priority,
                dueDateMillis = dueDateMillis,
                hasDueTime = hasDueTime,
                reminderOffset = reminderOffset,
                subtasks = subtasks
            )
        }
    }

    fun toggleTaskCompletion(taskId: String) {
        viewModelScope.launch {
            repository.toggleTaskCompletion(taskId)
        }
    }

    fun toggleSubtaskCompletion(taskId: String, subtaskId: String) {
        viewModelScope.launch {
            repository.toggleSubtaskCompletion(taskId, subtaskId)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
        }
    }

    fun deleteAllCompletedTasks() {
        val completedIds = uiState.value.completedTasks.map { it.id }
        if (completedIds.isEmpty()) return
        viewModelScope.launch {
            val count = repository.deleteCompletedTasks(completedIds)
            if (count > 0) {
                _completedExpanded.value = false
                _statusMessage.value = if (count == 1) {
                    "Deleted 1 completed task"
                } else {
                    "Deleted $count completed tasks"
                }
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun moveActiveTask(fromIndex: Int, toIndex: Int) {
        val currentActive = uiState.value.activeTasks
        if (fromIndex !in currentActive.indices || toIndex !in currentActive.indices || fromIndex == toIndex) {
            return
        }
        val mutable = currentActive.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        viewModelScope.launch {
            repository.reorderTasks(mutable.map { it.id })
        }
    }

    fun triggerManualSync() {
        if (currentUserId.isNullOrBlank()) return
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            repository.syncNow()
                .onSuccess { report ->
                    _syncState.value = SyncState.Synced(report.timestampMillis, report.conflicts.size)
                }
                .onFailure { error ->
                    _syncState.value = SyncState.Error(error.localizedMessage ?: "Sync failed")
                }
        }
    }

    fun setPendingDeepLink(openTaskId: String?, quickAdd: Boolean, listId: String?) {
        if (!listId.isNullOrBlank()) _selectedListId.value = listId
        if (!openTaskId.isNullOrBlank()) _pendingOpenTaskId.value = openTaskId
        if (quickAdd) _pendingQuickAdd.value = true
    }

    fun consumePendingOpenTask() {
        _pendingOpenTaskId.value = null
    }

    fun consumePendingQuickAdd() {
        _pendingQuickAdd.value = false
    }

    companion object {
        private const val TAG = "TaskBoardVM"

        fun filterAndSplitTasks(
            tasks: List<TaskItem>,
            selectedListId: String?,
            searchQuery: String,
            priorityFilter: TaskPriority?,
            dueDateFilter: DueDateFilter,
            nowMillis: Long = System.currentTimeMillis()
        ): Pair<List<TaskItem>, List<TaskItem>> {
            val query = searchQuery.trim().lowercase()

            val dayBounds = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfToday = dayBounds.timeInMillis
            dayBounds.add(Calendar.DAY_OF_YEAR, 1)
            val endOfToday = dayBounds.timeInMillis

            val filtered = tasks.filter { task ->
                val matchesList = selectedListId == null || task.listId == selectedListId
                val matchesPriority = priorityFilter == null || task.priority == priorityFilter
                val matchesQuery = query.isEmpty() ||
                    task.title.lowercase().contains(query) ||
                    task.notes.lowercase().contains(query) ||
                    task.subtasks.any { it.title.lowercase().contains(query) }

                val matchesDue = when (dueDateFilter) {
                    DueDateFilter.ALL -> true
                    DueDateFilter.TODAY -> {
                        val due = task.dueDateMillis
                        due != null && due in startOfToday until endOfToday
                    }
                    DueDateFilter.UPCOMING -> {
                        val due = task.dueDateMillis
                        due != null && due >= startOfToday
                    }
                    DueDateFilter.OVERDUE -> {
                        val due = task.dueDateMillis
                        !task.completed && due != null && due < nowMillis
                    }
                    DueDateFilter.NO_DATE -> task.dueDateMillis == null
                }

                matchesList && matchesPriority && matchesQuery && matchesDue
            }

            val active = filtered.filter { !it.completed }.sortedBy { it.sortOrder }
            val completed = filtered.filter { it.completed }
                .sortedByDescending { it.completedAtMillis ?: it.updatedAtMillis }
            return active to completed
        }
    }
}
