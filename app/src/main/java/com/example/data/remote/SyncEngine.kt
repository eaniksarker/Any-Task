package com.example.data.remote

import com.example.data.local.AnyTaskDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.domain.model.SyncConflictSummary
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskList

/**
 * Pure conflict-resolution & offline-first bidirectional sync engine.
 *
 * Uses **Last-Write-Wins (LWW)** based on [updatedAtMillis] so concurrent edits
 * across devices resolve deterministically with explicit conflict telemetry.
 */
object LastWriteWinsResolver {

    data class MergeResult<T>(
        val winnersToSaveLocally: List<T>,
        val winnersToPushRemotely: List<T>,
        val conflicts: List<SyncConflictSummary>
    )

    fun mergeTaskLists(
        local: List<TaskList>,
        remote: List<TaskList>
    ): MergeResult<TaskList> {
        val localMap = local.associateBy { it.id }
        val remoteMap = remote.associateBy { it.id }
        val allIds = (localMap.keys + remoteMap.keys).distinct()

        val saveLocally = mutableListOf<TaskList>()
        val pushRemotely = mutableListOf<TaskList>()
        val conflicts = mutableListOf<SyncConflictSummary>()
        val now = System.currentTimeMillis()

        for (id in allIds) {
            val l = localMap[id]
            val r = remoteMap[id]
            when {
                l != null && r == null -> pushRemotely += l
                l == null && r != null -> saveLocally += r.copy(dirty = false)
                l != null && r != null -> {
                    if (l.dirty && l.updatedAtMillis >= r.updatedAtMillis) {
                        pushRemotely += l
                        if (r.updatedAtMillis > 0L && l.name != r.name) {
                            conflicts += SyncConflictSummary(l.name, "Local (newer)", now)
                        }
                    } else if (r.updatedAtMillis > l.updatedAtMillis) {
                        saveLocally += r.copy(dirty = false)
                        if (l.dirty) {
                            conflicts += SyncConflictSummary(r.name, "Cloud (newer)", now)
                        }
                    } else if (l.dirty) {
                        pushRemotely += l
                    }
                }
            }
        }
        return MergeResult(saveLocally, pushRemotely, conflicts)
    }

    fun mergeTasks(
        local: List<TaskItem>,
        remote: List<TaskItem>
    ): MergeResult<TaskItem> {
        val localMap = local.associateBy { it.id }
        val remoteMap = remote.associateBy { it.id }
        val allIds = (localMap.keys + remoteMap.keys).distinct()

        val saveLocally = mutableListOf<TaskItem>()
        val pushRemotely = mutableListOf<TaskItem>()
        val conflicts = mutableListOf<SyncConflictSummary>()
        val now = System.currentTimeMillis()

        for (id in allIds) {
            val l = localMap[id]
            val r = remoteMap[id]
            when {
                l != null && r == null -> pushRemotely += l
                l == null && r != null -> saveLocally += r.copy(dirty = false)
                l != null && r != null -> {
                    val contentDiffers = l.title != r.title ||
                        l.completed != r.completed ||
                        l.priority != r.priority ||
                        l.notes != r.notes ||
                        l.deleted != r.deleted ||
                        l.dueDateMillis != r.dueDateMillis
                    if (l.dirty && l.updatedAtMillis >= r.updatedAtMillis) {
                        pushRemotely += l
                        if (contentDiffers && r.updatedAtMillis > 0L) {
                            conflicts += SyncConflictSummary(l.title, "Local (newer)", now)
                        }
                    } else if (r.updatedAtMillis > l.updatedAtMillis) {
                        saveLocally += r.copy(dirty = false)
                        if (l.dirty && contentDiffers) {
                            conflicts += SyncConflictSummary(r.title, "Cloud (newer)", now)
                        }
                    } else if (l.dirty) {
                        pushRemotely += l
                    }
                }
            }
        }
        return MergeResult(saveLocally, pushRemotely, conflicts)
    }
}

class SyncEngine(
    private val localDb: AnyTaskDatabase,
    private val prefs: PreferencesManager,
    private val firestoreRepo: FirestoreTaskRepository
) {
    data class SyncReport(
        val timestampMillis: Long,
        val conflicts: List<SyncConflictSummary>
    )

    /**
     * Performs a full bidirectional sync against Cloud Firestore for the signed-in Google user.
     */
    suspend fun syncWithCloud(userId: String): Result<SyncReport> {
        return runCatching {
            val remoteLists = firestoreRepo.fetchTaskListsOnce(userId).getOrThrow()
            val remoteTasks = firestoreRepo.fetchTasksOnce(userId).getOrThrow()

            val localLists = localDb.taskListDao().getAllIncludingDeleted().map { it.toDomain() }
            val localTasks = localDb.taskDao().getAllIncludingDeleted().map { it.toDomain() }

            val listMerge = LastWriteWinsResolver.mergeTaskLists(localLists, remoteLists)
            if (listMerge.winnersToSaveLocally.isNotEmpty()) {
                localDb.taskListDao().upsertAll(listMerge.winnersToSaveLocally.map { it.toEntity(dirty = false) })
            }
            for (list in listMerge.winnersToPushRemotely) {
                firestoreRepo.upsertTaskList(list).getOrThrow()
                localDb.taskListDao().markSynced(list.id)
            }

            val taskMerge = LastWriteWinsResolver.mergeTasks(localTasks, remoteTasks)
            for (task in taskMerge.winnersToSaveLocally) {
                localDb.taskDao().upsertTask(task.toEntity(dirty = false))
                localDb.subtaskDao().replaceForTask(task.id, task.subtasks.map { it.toEntity() })
            }
            for (task in taskMerge.winnersToPushRemotely) {
                firestoreRepo.upsertTask(task).getOrThrow()
                localDb.taskDao().markSynced(task.id)
            }

            AnyTaskDatabase.ensureDefaultListExists(localDb)
            val now = System.currentTimeMillis()
            prefs.setLastSyncedMillis(now)
            SyncReport(
                timestampMillis = now,
                conflicts = listMerge.conflicts + taskMerge.conflicts
            )
        }
    }

    /**
     * Merges an incoming real-time Firestore snapshot into Room using last-write-wins.
     */
    suspend fun applyRemoteSnapshot(remoteLists: List<TaskList>, remoteTasks: List<TaskItem>) {
        val localLists = localDb.taskListDao().getAllIncludingDeleted().map { it.toDomain() }
        val localTasks = localDb.taskDao().getAllIncludingDeleted().map { it.toDomain() }

        val listMerge = LastWriteWinsResolver.mergeTaskLists(localLists, remoteLists)
        if (listMerge.winnersToSaveLocally.isNotEmpty()) {
            localDb.taskListDao().upsertAll(listMerge.winnersToSaveLocally.map { it.toEntity(dirty = false) })
        }

        val taskMerge = LastWriteWinsResolver.mergeTasks(localTasks, remoteTasks)
        for (task in taskMerge.winnersToSaveLocally) {
            localDb.taskDao().upsertTask(task.toEntity(dirty = false))
            localDb.subtaskDao().replaceForTask(task.id, task.subtasks.map { it.toEntity() })
        }
        AnyTaskDatabase.ensureDefaultListExists(localDb)
    }
}
