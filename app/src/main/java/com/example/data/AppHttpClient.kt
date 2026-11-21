package com.example.data

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Single shared lightweight OkHttpClient pool across AI API, Exchange Rates,
 * GitHub Release updates, and WebDAV sync.
 * Sharing one ConnectionPool & Dispatcher avoids spawning 4 idle thread pools in background RAM.
 */
object AppHttpClient {
    private val sharedConnectionPool = ConnectionPool(2, 20, TimeUnit.SECONDS)
    private val sharedDispatcher = Dispatcher().apply {
        maxRequests = 8
        maxRequestsPerHost = 4
    }

    val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(sharedConnectionPool)
            .dispatcher(sharedDispatcher)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun derived(
        connectTimeoutSec: Long = 12,
        readTimeoutSec: Long = 20,
        writeTimeoutSec: Long = 20,
        followRedirects: Boolean = true
    ): OkHttpClient = baseClient.newBuilder()
        .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
        .writeTimeout(writeTimeoutSec, TimeUnit.SECONDS)
        .followRedirects(followRedirects)
        .build()

    fun trimIdleResources() {
        runCatching { sharedConnectionPool.evictAll() }
    }
}
