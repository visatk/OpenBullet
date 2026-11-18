package com.example.network

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

class InMemoryCookieJar : CookieJar {
    private val store = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val list = store.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { newCookie ->
            list.removeAll { it.name == newCookie.name }
            list.add(newCookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val list = store[url.host] ?: return emptyList()
        list.removeAll { it.expiresAt < now }
        return list.toList()
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
            level = HttpLoggingInterceptor.Level.BASIC
        }
    }

    val baseOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    val apiService: OpenBulletApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://httpbin.org/")
            .client(baseOkHttpClient)
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
        val builder = baseOkHttpClient.newBuilder()
            .connectTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
            .writeTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
            .followRedirects(followRedirects)
            .followSslRedirects(followRedirects)

        if (cookieJar != null) {
            builder.cookieJar(cookieJar)
        }

        if (proxyItem != null) {
            val type = when (proxyItem.protocol) {
                ProxyProtocol.HTTP -> Proxy.Type.HTTP
                ProxyProtocol.SOCKS4, ProxyProtocol.SOCKS5 -> Proxy.Type.SOCKS
            }
            builder.proxy(Proxy(type, InetSocketAddress(proxyItem.host, proxyItem.port)))
        }
        return builder.build()
    }
}
