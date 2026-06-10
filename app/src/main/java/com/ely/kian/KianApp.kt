package com.ely.kian

import android.app.Application
import com.ely.kian.di.KianContainer

class KianApp : Application() {
    lateinit var container: KianContainer

    override fun onCreate() {
        super.onCreate()
        container = KianContainer(this)
    }
}
