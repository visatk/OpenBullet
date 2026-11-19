package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.OpenBulletDatabase
import com.example.network.HttpExecutionEngine
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
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
        val keepAwake = settings?.keepAwakeEnabled ?: true
        val probeUrl = inputData.getString(KEY_PROBE_URL)
            ?.takeIf { it.isNotBlank() }
            ?: settings?.proxyJudgeUrl?.takeIf { it.isNotBlank() }
            ?: "https://httpbin.org/ip"

        val proxies = proxyDao.getAllSnapshot().map { it.toModel() }
        if (proxies.isEmpty()) {
            return@withContext Result.success(workDataOf(KEY_CHECKED to 0, KEY_TOTAL to 0))
        }

        try {
            setForeground(
                BackgroundKeepAliveHelper.buildForegroundInfo(
                    context = applicationContext,
                    workId = id,
                    notificationId = BackgroundKeepAliveHelper.PROXY_CHECK_NOTIFICATION_ID,
                    title = "OpenBullet • Proxy Checker",
                    contentText = "Checking ${proxies.size} proxies...",
                    progressCurrent = 0,
                    progressTotal = proxies.size
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // Foreground promotion may be restricted by OEM background limits; continue execution
        }

        BackgroundKeepAliveHelper.withKeepAwakeLocks(applicationContext, enabled = keepAwake) {
            val checked = AtomicInteger(0)
            val lastProgressMs = AtomicLong(0L)
            val semaphore = Semaphore(8)

            coroutineScope {
                proxies.map { proxy ->
                    async {
                        semaphore.withPermit {
                            if (!isActive || isStopped) return@withPermit
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
                            val now = System.currentTimeMillis()
                            val prev = lastProgressMs.get()
                            if (done == proxies.size || (done % 2 == 0 && now - prev >= 200L && lastProgressMs.compareAndSet(prev, now))) {
                                setProgress(workDataOf(KEY_CHECKED to done, KEY_TOTAL to proxies.size))
                            }
                            if (done % 5 == 0 || done == proxies.size) {
                                try {
                                    setForeground(
                                        BackgroundKeepAliveHelper.buildForegroundInfo(
                                            context = applicationContext,
                                            workId = id,
                                            notificationId = BackgroundKeepAliveHelper.PROXY_CHECK_NOTIFICATION_ID,
                                            title = "OpenBullet • Proxy Checker",
                                            contentText = "Checked $done / ${proxies.size} proxies",
                                            progressCurrent = done,
                                            progressTotal = proxies.size
                                        )
                                    )
                                } catch (ce: CancellationException) {
                                    throw ce
                                } catch (_: Throwable) {
                                    // Non-fatal notification update failure
                                }
                            }
                        }
                    }
                }.awaitAll()
            }

            Result.success(workDataOf(KEY_CHECKED to checked.get(), KEY_TOTAL to proxies.size))
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "openbullet_proxy_check"
        const val WORK_TAG = "proxy_check_tag"
        const val KEY_PROBE_URL = "probe_url"
        const val KEY_CHECKED = "checked"
        const val KEY_TOTAL = "total"
    }
}
