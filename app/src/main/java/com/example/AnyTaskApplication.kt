package com.example

import android.app.Application
import com.example.data.AnyTaskRepository
import com.example.data.local.AnyTaskDatabase
import com.example.data.local.PreferencesManager
import com.example.data.remote.FirestoreTaskRepository
import com.example.data.remote.SyncEngine
import com.example.notifications.DeadlineAlarmScheduler

class AnyTaskApplication : Application() {

    val database: AnyTaskDatabase by lazy { AnyTaskDatabase.getInstance(this) }
    val preferences: PreferencesManager by lazy { PreferencesManager(this) }
    val firestoreRepository: FirestoreTaskRepository by lazy { FirestoreTaskRepository(this) }
    val syncEngine: SyncEngine by lazy {
        SyncEngine(database, preferences, firestoreRepository)
    }
    val repository: AnyTaskRepository by lazy {
        AnyTaskRepository(this, database, preferences, firestoreRepository, syncEngine)
    }

    override fun onCreate() {
        super.onCreate()
        DeadlineAlarmScheduler.ensureNotificationChannel(this)
    }
}
