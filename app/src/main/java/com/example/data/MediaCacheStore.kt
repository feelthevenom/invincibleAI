package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Disk cache for **Lottie `.json` files only**.
 * Food and wger static images are cached by Coil's disk cache — not here.
 */
class MediaCacheStore(context: Context) {

    private val appContext = context.applicationContext
    private val cacheDir: File
        get() = File(appContext.filesDir, "media_cache").also { it.mkdirs() }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    fun cacheKey(raw: String): String {
        val normalized = raw.trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    fun cachedFile(key: String, extension: String): File? {
        val file = File(cacheDir, "${cacheKey(key)}.${extension.trim('.')}")
        return file.takeIf { it.exists() && it.length() > 0 }
    }

    suspend fun downloadUrl(key: String, url: String, extension: String = "json"): File? =
        withContext(Dispatchers.IO) {
            cachedFile(key, extension)?.let { return@withContext it }
            if (url.isBlank()) return@withContext null
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body?.bytes() ?: return@withContext null
                    if (body.isEmpty()) return@withContext null
                    File(cacheDir, "${cacheKey(key)}.${extension.trim('.')}").apply { writeBytes(body) }
                }
            } catch (_: Exception) {
                null
            }
        }

    companion object {
        private const val USER_AGENT = "InvincibleAI/1.0 (Android; Lottie media cache)"
    }
}
