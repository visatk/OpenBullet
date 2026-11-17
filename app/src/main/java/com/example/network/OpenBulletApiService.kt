package com.example.network

import com.example.model.ProxyItem
import com.example.model.ProxyProtocol
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
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

    fun buildClient(timeoutSec: Int, proxyItem: ProxyItem?): OkHttpClient {
        val builder = baseOkHttpClient.newBuilder()
            .connectTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
            .writeTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)

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
