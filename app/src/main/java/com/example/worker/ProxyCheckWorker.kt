package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.OpenBulletDatabase
import com.example.network.HttpExecutionEngine
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class ProxyCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = OpenBulletDatabase.getInstance(applicationContext)
        val proxyDao = db.proxyDao()
        val settings = db.settingsDao().getSnapshot()
        val timeoutSec = (settings?.requestTimeoutSec ?: 8).coerceIn(2, 15)
        val probeUrl = inputData.getString(KEY_PROBE_URL) ?: "https://httpbin.org/ip"

        val proxies = proxyDao.getAllSnapshot().map { it.toModel() }
        if (proxies.isEmpty()) {
            return@withContext Result.success(workDataOf(KEY_CHECKED to 0, KEY_TOTAL to 0))
        }

        val checked = AtomicInteger(0)
        val semaphore = Semaphore(8)

        coroutineScope {
            proxies.map { proxy ->
                async {
                    semaphore.withPermit {
                        if (isStopped) return@withPermit
                        val (status, pingMs, info) = HttpExecutionEngine.checkProxy(
                            proxy = proxy,
                            timeoutSec = timeoutSec,
                            probeUrl = probeUrl
                        )
                        proxyDao.updateCheckResult(
                            id = proxy.id,
                            status = status.name,
                            pingMs = pingMs,
                            country = info
                        )
                        val done = checked.incrementAndGet()
                        setProgress(workDataOf(KEY_CHECKED to done, KEY_TOTAL to proxies.size))
                    }
                }
            }.awaitAll()
        }

        Result.success(workDataOf(KEY_CHECKED to checked.get(), KEY_TOTAL to proxies.size))
    }

    companion object {
        const val UNIQUE_WORK_NAME = "openbullet_proxy_check"
        const val WORK_TAG = "proxy_check_tag"
        const val KEY_PROBE_URL = "probe_url"
        const val KEY_CHECKED = "checked"
        const val KEY_TOTAL = "total"
    }
}
