package com.example

import com.example.domain.model.DueDateFilter
import com.example.domain.model.Subtask
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskPriority
import com.example.ui.viewmodel.TaskBoardViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskFilterAndSortTest {

    private val now = 1_800_000_000_000L

    private val sampleTasks = listOf(
        TaskItem(
            id = "t1",
            listId = "work",
            title = "Prepare Q4 Deck",
            notes = "Include revenue chart",
            priority = TaskPriority.HIGH,
            completed = false,
            dueDateMillis = now - 3_600_000L, // overdue
            sortOrder = 2,
            subtasks = listOf(Subtask("s1", "t1", "Export slides", false, 0))
        ),
        TaskItem(
            id = "t2",
            listId = "personal",
            title = "Buy coffee beans",
            notes = "Ethiopian roast",
            priority = TaskPriority.LOW,
            completed = false,
            dueDateMillis = null,
            sortOrder = 0
        ),
        TaskItem(
            id = "t3",
            listId = "work",
            title = "Code review PR #42",
            notes = "",
            priority = TaskPriority.MEDIUM,
            completed = true,
            completedAtMillis = now - 1000L,
            sortOrder = 1
        )
    )

    @Test
    fun `filterAndSplitTasks filters by priority, list, search query, and splits completed`() {
        val (activeAll, completedAll) = TaskBoardViewModel.filterAndSplitTasks(
            tasks = sampleTasks,
            selectedListId = null,
            searchQuery = "",
            priorityFilter = null,
            dueDateFilter = DueDateFilter.ALL,
            nowMillis = now
        )
        assertEquals(listOf("t2", "t1"), activeAll.map { it.id }) // sorted by sortOrder
        assertEquals(listOf("t3"), completedAll.map { it.id })

        val (highOnly, _) = TaskBoardViewModel.filterAndSplitTasks(
            tasks = sampleTasks,
            selectedListId = null,
            searchQuery = "",
            priorityFilter = TaskPriority.HIGH,
            dueDateFilter = DueDateFilter.OVERDUE,
            nowMillis = now
        )
        assertEquals(listOf("t1"), highOnly.map { it.id })

        val (subtaskSearch, _) = TaskBoardViewModel.filterAndSplitTasks(
            tasks = sampleTasks,
            selectedListId = null,
            searchQuery = "export slides",
            priorityFilter = null,
            dueDateFilter = DueDateFilter.ALL,
            nowMillis = now
        )
        assertEquals(listOf("t1"), subtaskSearch.map { it.id })
    }
}
