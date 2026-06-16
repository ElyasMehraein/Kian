package com.ely.kian.services

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ely.kian.KianApp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

class ChatWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? KianApp ?: return Result.failure()
        val container = app.container
        val syncManager = container.nostrSyncManager
        
        val myPubkey = container.chatRepository.getOwnPubkey()

        if (myPubkey == null) {
            Log.d("ChatWorker", "No pubkey found, skipping sync")
            return Result.success()
        }

        // Check if already syncing (likely in foreground)
        if (syncManager.isSyncing()) {
            Log.d("ChatWorker", "App is likely in foreground and already syncing, skipping")
            return Result.success()
        }

        Log.d("ChatWorker", "Starting background sync for $myPubkey")
        
        // Start syncing
        syncManager.startSyncing(myPubkey)
        
        // Let it run for 1 minute to catch new messages
        delay(60.seconds) 
        
        // Stop syncing to save battery
        syncManager.stopSyncing()
        
        Log.d("ChatWorker", "Background sync completed")
        
        return Result.success()
    }
}
