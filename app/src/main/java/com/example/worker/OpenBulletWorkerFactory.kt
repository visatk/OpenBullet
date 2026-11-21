package com.example.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.data.source.LocalWorkspaceDataSource

/**
 * Custom [WorkerFactory] providing constructor-injected [LocalWorkspaceDataSource]
 * to WorkManager workers in accordance with Android Dependency Injection & Data Layer guidelines.
 */
class OpenBulletWorkerFactory(
    private val localDataSource: LocalWorkspaceDataSource
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            RunnerJobWorker::class.java.name -> RunnerJobWorker(
                appContext = appContext,
                params = workerParameters,
                localDataSource = localDataSource
            )
            ProxyCheckWorker::class.java.name -> ProxyCheckWorker(
                appContext = appContext,
                params = workerParameters,
                localDataSource = localDataSource
            )
            else -> null
        }
    }
}
