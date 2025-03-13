package com.pqsolutions.hdd_monitor.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.data.UserRepository
import javax.inject.Inject

class EventReminderWorkerFactory @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userRepository: UserRepository
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            EventNotificationScheduler.EventReminderWorker::class.java.name -> {
                EventNotificationScheduler.EventReminderWorker(
                    appContext,
                    workerParameters,
                    firestore,
                    userRepository
                )
            }
            else -> null
        }
    }
}