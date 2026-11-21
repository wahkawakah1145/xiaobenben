package com.example.data

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Single shared lightweight OkHttpClient pool across AI API, Exchange Rates,
 * GitHub Release updates, and WebDAV sync.
 * Uses zero-core short-lived daemon threads (15s idle expiry) and a minimal connection pool
 * to minimize resident background RAM while keeping background notification parsing responsive.
 */
object AppHttpClient {
    private val sharedConnectionPool = ConnectionPool(1, 15, TimeUnit.SECONDS)
    private val sharedExecutor = ThreadPoolExecutor(
        0,
        4,
        15L,
        TimeUnit.SECONDS,
        SynchronousQueue()
    ) { runnable ->
        Thread(runnable, "xbb-http").apply {
            isDaemon = true
        }
    }
    private val sharedDispatcher = Dispatcher(sharedExecutor).apply {
        maxRequests = 6
        maxRequestsPerHost = 3
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
