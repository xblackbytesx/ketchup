package com.example.ketchup.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ArticleFetcher(private val client: OkHttpClient) {
    companion object {
        private const val MAX_BODY_BYTES = 5 * 1024 * 1024L  // 5 MB cap
    }

    suspend fun fetchFullContent(url: String): String? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body
                    // Reject by Content-Length if server declares it's too large
                    val contentLength = body.contentLength()
                    if (contentLength > MAX_BODY_BYTES) return@withContext null
                    // Reject oversized bodies outright — truncating mid-document
                    // (possibly mid-UTF-8-sequence) renders garbage.
                    val source = body.source()
                    source.request(MAX_BODY_BYTES + 1)
                    if (source.buffer.size > MAX_BODY_BYTES) return@withContext null
                    source.readUtf8()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
