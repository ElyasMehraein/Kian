package com.ely.kian

import android.app.Application
import android.content.Context
import com.ely.kian.di.KianContainer
import org.osmdroid.config.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KianApp : Application() {
    lateinit var container: KianContainer
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Osmdroid (OpenStreetMap)
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        try {
            container = KianContainer(this)
            
            // Start syncing in background with delay
            appScope.launch {
                try {
                    kotlinx.coroutines.delay(5000)
                    // Syncing is now triggered from MainViewModel when pubkey is available
                    // container.nostrSyncManager.startSyncing()
                } catch (t: Throwable) {
                    android.util.Log.e("KianApp", "Nostr sync failed", t)
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("KianApp", "Critical init failure", t)
        }
    }
}
