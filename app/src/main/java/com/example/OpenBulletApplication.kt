package com.example

import android.app.Application
import androidx.work.Configuration
import com.example.di.AppContainer
import com.example.di.DefaultAppContainer
import com.example.worker.BackgroundKeepAliveHelper

class OpenBulletApplication : Application(), Configuration.Provider {

    val container: AppContainer by lazy {
        DefaultAppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(container.workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        BackgroundKeepAliveHelper.ensureChannel(this)
    }
}
