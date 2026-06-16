package com.ely.kian.services

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val CHAT_WORK_NAME = "ChatSyncWork"

    fun scheduleChatSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val chatWorkRequest = PeriodicWorkRequestBuilder<ChatWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CHAT_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing to avoid resetting the 15m timer
            chatWorkRequest
        )
    }

    fun cancelChatSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(CHAT_WORK_NAME)
    }
}
