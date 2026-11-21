package com.example

import com.example.data.remote.LastWriteWinsResolver
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskList
import com.example.domain.model.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineTest {

    @Test
    fun `mergeTasks resolves concurrent edits via last-write-wins`() {
        val localNewer = TaskItem(
            id = "task-1",
            listId = "list-1",
            title = "Local Edited Title",
            priority = TaskPriority.HIGH,
            updatedAtMillis = 2000L,
            dirty = true
        )
        val remoteOlder = TaskItem(
            id = "task-1",
            listId = "list-1",
            title = "Cloud Older Title",
            priority = TaskPriority.LOW,
            updatedAtMillis = 1000L,
            dirty = false
        )

        val remoteNewer = TaskItem(
            id = "task-2",
            listId = "list-1",
            title = "Cloud Newer Title",
            priority = TaskPriority.MEDIUM,
            updatedAtMillis = 5000L,
            dirty = false
        )
        val localOlderDirty = TaskItem(
            id = "task-2",
            listId = "list-1",
            title = "Local Stale Title",
            priority = TaskPriority.LOW,
            updatedAtMillis = 3000L,
            dirty = true
        )

        val localOnly = TaskItem(
            id = "task-3",
            listId = "list-1",
            title = "Created Offline",
            updatedAtMillis = 4000L,
            dirty = true
        )

        val result = LastWriteWinsResolver.mergeTasks(
            local = listOf(localNewer, localOlderDirty, localOnly),
            remote = listOf(remoteOlder, remoteNewer)
        )

        assertEquals(listOf("task-2"), result.winnersToSaveLocally.map { it.id })
        assertEquals(listOf("task-1", "task-3"), result.winnersToPushRemotely.map { it.id })
        assertEquals(2, result.conflicts.size)
        assertTrue(result.conflicts.any { it.itemTitle == "Local Edited Title" && it.winner == "Local (newer)" })
        assertTrue(result.conflicts.any { it.itemTitle == "Cloud Newer Title" && it.winner == "Cloud (newer)" })
    }

    @Test
    fun `mergeTaskLists pulls remote-only lists cleanly`() {
        val remoteOnly = TaskList(
            id = "work-list",
            name = "Work",
            updatedAtMillis = 1000L,
            dirty = false
        )

        val result = LastWriteWinsResolver.mergeTaskLists(
            local = emptyList(),
            remote = listOf(remoteOnly)
        )

        assertEquals(1, result.winnersToSaveLocally.size)
        assertEquals("Work", result.winnersToSaveLocally.first().name)
        assertTrue(result.winnersToPushRemotely.isEmpty())
    }
}
