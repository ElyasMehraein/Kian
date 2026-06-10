package com.ely.kian

import android.app.Application
import com.ely.kian.di.KianContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KianApp : Application() {
    lateinit var container: KianContainer
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        try {
            container = KianContainer(this)
            
            // Start syncing in background with delay
            appScope.launch {
                try {
                    kotlinx.coroutines.delay(5000)
                    container.nostrSyncManager.startSyncing()
                } catch (t: Throwable) {
                    android.util.Log.e("KianApp", "Nostr sync failed", t)
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("KianApp", "Critical init failure", t)
        }
    }
}
