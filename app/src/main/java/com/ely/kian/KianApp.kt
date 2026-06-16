package com.ely.kian

import android.app.Application
import android.content.Context
import com.ely.kian.di.KianContainer
import com.ely.kian.services.WorkScheduler
import org.osmdroid.config.Configuration
import android.app.Activity
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KianApp : Application() {
    lateinit var container: KianContainer
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var isAppInForeground = false
        private set

    override fun onCreate() {
        super.onCreate()
        
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                isAppInForeground = true
            }
            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities == 0) {
                    isAppInForeground = false
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // Initialize Osmdroid (OpenStreetMap)
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        try {
            container = KianContainer(this)
            
            // Schedule background sync
            WorkScheduler.scheduleChatSync(this)
            
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
