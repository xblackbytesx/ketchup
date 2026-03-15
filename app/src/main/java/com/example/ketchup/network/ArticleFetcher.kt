package com.example.ketchup.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ArticleFetcher {
    companion object {
        private const val MAX_BODY_BYTES = 5 * 1024 * 1024L  // 5 MB cap
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchFullContent(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                // Reject by Content-Length if server declares it's too large
                val contentLength = body.contentLength()
                if (contentLength > MAX_BODY_BYTES) return@withContext null
                // Buffer up to MAX_BODY_BYTES from the stream, then read whatever arrived
                val source = body.source()
                source.request(MAX_BODY_BYTES)
                val size = minOf(source.buffer.size, MAX_BODY_BYTES)
                source.readUtf8(size)
            }
        } catch (e: Exception) {
            null
        }
    }
}
