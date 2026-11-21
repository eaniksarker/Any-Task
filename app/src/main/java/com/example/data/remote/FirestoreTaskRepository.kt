package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskList
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Cloud Firestore repository scoped strictly to the authenticated user's UID
 * under `/users/{uid}/taskLists` and `/users/{uid}/tasks`.
 */
class FirestoreTaskRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        check(!uid.isNullOrBlank()) {
            "User must be signed in with Google before accessing Firestore."
        }
        return uid
    }

    fun observeTaskLists(userId: String): Flow<List<TaskList>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user changed; refusing cross-user stream." }
        val path = "users/$uid/taskLists"
        emitAll(
            db.collection("users").document(uid).collection("taskLists")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreTaskList::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { it.toDomain() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeTasks(userId: String): Flow<List<TaskItem>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user changed; refusing cross-user stream." }
        val path = "users/$uid/tasks"
        emitAll(
            db.collection("users").document(uid).collection("tasks")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreTask::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { it.toDomain() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun fetchTaskListsOnce(userId: String): Result<List<TaskList>> {
        val path = "users/$userId/taskLists"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val snapshot = db.collection("users").document(uid).collection("taskLists").get().await()
            val items = snapshot.toObjects(
                FirestoreTaskList::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).map { it.toDomain() }
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun fetchTasksOnce(userId: String): Result<List<TaskItem>> {
        val path = "users/$userId/tasks"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val snapshot = db.collection("users").document(uid).collection("tasks").get().await()
            val items = snapshot.toObjects(
                FirestoreTask::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).map { it.toDomain() }
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun upsertTaskList(list: TaskList): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/taskLists/${list.id}"
        return try {
            val uid = requireUserId()
            require(list.id.isNotBlank()) { "TaskList ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("taskLists").document(list.id)
            docPath = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(FirestoreTaskList.toUpdateMap(list)).await()
            } else {
                docRef.set(FirestoreTaskList.toCreateMap(uid, list)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertTask(task: TaskItem): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/${task.id}"
        return try {
            val uid = requireUserId()
            require(task.id.isNotBlank()) { "Task ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("tasks").document(task.id)
            docPath = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(FirestoreTask.toUpdateMap(task)).await()
            } else {
                docRef.set(FirestoreTask.toCreateMap(uid, task)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }
}
