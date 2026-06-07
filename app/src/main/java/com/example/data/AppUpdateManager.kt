package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String,
    val minVersionCode: Int = 1
)

sealed class UpdateCheckResult {
    data object UpToDate : UpdateCheckResult()
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

sealed class ApkDownloadResult {
    data class Success(val file: File) : ApkDownloadResult()
    data class Error(val message: String) : ApkDownloadResult()
}

class AppUpdateManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val manifestUrls: List<String> = buildList {
        val configured = BuildConfig.UPDATE_MANIFEST_URL.trim()
        if (configured.isNotBlank() && configured != "https://example.com/version.json") {
            add(configured)
        }
        add("https://raw.githubusercontent.com/feelthevenom/invincibleAI/main/app/version.json")
        add("https://raw.githubusercontent.com/feelthevenom/invincibleAI/master/app/version.json")
    }.distinct()

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val bundled = readBundledManifest()
        if (bundled != null && bundled !is UpdateCheckResult.Error) {
            return@withContext bundled
        }
        var lastError: String? = null
        for (url in manifestUrls) {
            when (val result = fetchManifest(url)) {
                is UpdateCheckResult.Error -> lastError = result.message
                else -> return@withContext result
            }
        }
        bundled ?: UpdateCheckResult.Error(lastError ?: "Could not check for updates.")
    }

    private fun fetchManifest(url: String): UpdateCheckResult {
        return try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return UpdateCheckResult.Error("Could not check for updates (HTTP ${response.code}).")
                }
                parseManifestBody(response.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Update check failed.")
        }
    }

    private fun readBundledManifest(): UpdateCheckResult? = try {
        val json = context.assets.open("version.json").bufferedReader().use { it.readText() }
        when (val parsed = parseManifestBody(json)) {
            is UpdateCheckResult.Error -> null
            else -> parsed
        }
    } catch (_: Exception) {
        null
    }

    private fun parseManifestBody(body: String): UpdateCheckResult {
        if (body.isBlank()) return UpdateCheckResult.Error("Update manifest was empty.")
        val json = JSONObject(body)
        val info = AppUpdateInfo(
            versionCode = json.getInt("versionCode"),
            versionName = json.optString("versionName", ""),
            apkUrl = json.getString("apkUrl"),
            releaseNotes = json.optString("releaseNotes", ""),
            minVersionCode = json.optInt("minVersionCode", 1)
        )
        return if (info.versionCode > BuildConfig.VERSION_CODE) {
            UpdateCheckResult.UpdateAvailable(info)
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    suspend fun downloadApk(
        url: String,
        onProgress: (Float) -> Unit
    ): ApkDownloadResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ApkDownloadResult.Error("Download failed (HTTP ${response.code}).")
                }
                val body = response.body ?: return@withContext ApkDownloadResult.Error("Empty download.")
                val total = body.contentLength().coerceAtLeast(1L)
                val dir = File(context.cacheDir, "updates").also { it.mkdirs() }
                val file = File(dir, "gymai-update.apk")
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        var downloaded = 0L
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                ApkDownloadResult.Success(file)
            }
        } catch (e: Exception) {
            ApkDownloadResult.Error(e.message ?: "Download failed.")
        }
    }

    fun createInstallIntent(apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun canInstallPackages(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    fun openInstallPermissionSettings(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
