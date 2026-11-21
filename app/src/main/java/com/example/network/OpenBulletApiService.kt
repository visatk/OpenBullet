package com.example.network

import com.example.BuildConfig
import com.example.model.ProxyItem
import com.example.model.ProxyProtocol
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url

interface OpenBulletApiService {
    @GET
    suspend fun probeIp(@Url url: String): IpifyResponse

    @GET
    suspend fun probeHttpGet(@Url url: String): HttpProbeResponse

    @GET
    suspend fun fetchRemoteText(@Url url: String): ResponseBody
}

/**
 * Thread-safe per-session cookie jar supporting domain/path matching and expiration cleanup.
 */
class InMemoryCookieJar : CookieJar {
    private val lock = Any()
    private val cookies = linkedMapOf<String, Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            for (cookie in cookies) {
                val key = "${cookie.domain}|${cookie.path}|${cookie.name}"
                if (cookie.expiresAt <= now) {
                    this.cookies.remove(key)
                } else {
                    this.cookies[key] = cookie
                }
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val iter = cookies.values.iterator()
            val matching = mutableListOf<Cookie>()
            while (iter.hasNext()) {
                val cookie = iter.next()
                if (cookie.expiresAt <= now) {
                    iter.remove()
                } else if (cookie.matches(url)) {
                    matching.add(cookie)
                }
            }
            return matching
        }
    }
}

/**
 * Preserves unresolved domain names on [InetAddress] so [ProxiedTcpSocket] and [RawTcpProxyTunnel]
 * can forward the hostname directly over SOCKS5 (ATYP_DOMAIN), SOCKS4a, or HTTP CONNECT
 * without leaking or blocking on local DNS resolution.
 */
object ProxyRemoteDns : okhttp3.Dns {
    private val ipv4Literal = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    override fun lookup(hostname: String): List< java.net.InetAddress> {
        val trimmed = hostname.trim().removeSurrounding("[", "]")
        if (ipv4Literal.matches(trimmed) || trimmed.contains(':')) {
            return okhttp3.Dns.SYSTEM.lookup(trimmed)
        }
        return listOf(java.net.InetAddress.getByAddress(trimmed, byteArrayOf(0, 0, 0, 1)))
    }
}

object OpenBulletNetworkClient {

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    private val highThroughputDispatcher: okhttp3.Dispatcher by lazy {
        okhttp3.Dispatcher().apply {
            maxRequests = 256
            maxRequestsPerHost = 128
        }
    }

    private val sharedConnectionPool: okhttp3.ConnectionPool by lazy {
        okhttp3.ConnectionPool(64, 5, TimeUnit.MINUTES)
    }

    val baseOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(highThroughputDispatcher)
            .connectionPool(sharedConnectionPool)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val apiService: OpenBulletApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://httpbin.org/")
            .client(baseOkHttpClient.newBuilder().addInterceptor(loggingInterceptor).build())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenBulletApiService::class.java)
    }

    fun buildClient(
        timeoutSec: Int,
        proxyItem: ProxyItem?,
        followRedirects: Boolean = true,
        cookieJar: CookieJar? = null
    ): OkHttpClient {
        val safeTimeoutSec = timeoutSec.coerceIn(1, 120).toLong()
        val timeoutMs = (safeTimeoutSec * 1000L).toInt()
        val builder = baseOkHttpClient.newBuilder()
            .connectTimeout(safeTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(safeTimeoutSec, TimeUnit.SECONDS)
            .writeTimeout(safeTimeoutSec, TimeUnit.SECONDS)
            .followRedirects(followRedirects)
            .followSslRedirects(followRedirects)

        if (cookieJar != null) {
            builder.cookieJar(cookieJar)
        }

        if (proxyItem != null && proxyItem.host.isNotBlank() && proxyItem.port in 1..65535) {
            // Route HTTP (CONNECT), SOCKS4/4a, and SOCKS5 connections through our raw TCP tunnel SocketFactory
            builder.proxy(Proxy.NO_PROXY)
            builder.dns(ProxyRemoteDns)
            builder.socketFactory(
                ProxiedTcpSocketFactory(
                    proxy = proxyItem,
                    connectTimeoutMs = timeoutMs
                )
            )
            if (proxyItem.protocol == ProxyProtocol.HTTP && proxyItem.hasAuth) {
                val credential = okhttp3.Credentials.basic(proxyItem.username, proxyItem.password)
                builder.proxyAuthenticator { _, response ->
                    if (response.request.header("Proxy-Authorization") != null) {
                        null
                    } else {
                        response.request.newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    }
                }
            }
        }
        return builder.build()
    }
}
