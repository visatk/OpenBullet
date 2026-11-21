package com.example.data.source

import com.example.di.DispatcherProvider
import com.example.network.OpenBulletApiService
import kotlinx.coroutines.withContext

/**
 * Remote network data source contract for fetching remote proxy/config payloads and running HTTP probes.
 */
interface RemoteTextDataSource {
    suspend fun fetchRemoteText(url: String): Result<String>
    suspend fun runHttpProbe(url: String): Result<String>
}

class RetrofitRemoteTextDataSource(
    private val apiService: OpenBulletApiService,
    private val dispatchers: DispatcherProvider
) : RemoteTextDataSource {

    override suspend fun fetchRemoteText(url: String): Result<String> = withContext(dispatchers.io) {
        runCatching {
            apiService.fetchRemoteText(url).use { body ->
                body.string()
            }
        }
    }

    override suspend fun runHttpProbe(url: String): Result<String> = withContext(dispatchers.io) {
        runCatching {
            val start = System.currentTimeMillis()
            val response = apiService.probeHttpGet(url)
            val elapsed = System.currentTimeMillis() - start
            "HTTP 200 (${elapsed}ms) | origin=${response.origin ?: "unknown"} | url=${response.url ?: url}"
        }
    }
}
