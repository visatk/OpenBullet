package com.example

import android.app.Application
import com.example.di.AppContainer
import com.example.di.DefaultAppContainer
import com.example.worker.BackgroundKeepAliveHelper

class OpenBulletApplication : Application() {

    val container: AppContainer by lazy {
        DefaultAppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        BackgroundKeepAliveHelper.ensureChannel(this)
    }
}
