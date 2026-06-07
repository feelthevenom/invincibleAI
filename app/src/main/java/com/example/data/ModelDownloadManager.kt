package com.example.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads Gemma 4 .litertlm files the same way as Google AI Edge Gallery:
 * - Pinned commit SHA in the resolve URL
 * - ?download=true query param
 * - HttpURLConnection with Range resume for partial files
 * - Optional Bearer token (public litert-community models work without it)
 */
class ModelDownloadManager(
    context: Context,
    private val secureStorageManager: SecureStorageManager
) {
    private val appContext = context.applicationContext

    private val modelsDir: File
        get() {
            val dir = File(appContext.filesDir, "models")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun getTotalRamGb(): Double {
        val actManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
    }

    fun isSystemCompatible(modelType: String): Boolean {
        val spec = OfflineModelConfig.specFor(modelType) ?: return false
        return getTotalRamGb() >= spec.minRamGb && Build.VERSION.SDK_INT >= 24
    }

    fun modelFile(modelType: String): File? = resolveModelFile(modelType)

    fun resolveModelFile(modelId: String): File? {
        OfflineModelConfig.specFor(modelId)?.let { spec ->
            val file = File(modelsDir, spec.fileName)
            return file.takeIf { it.exists() }
        }
        if (modelId.startsWith("imported:")) {
            val file = File(modelsDir, modelId.removePrefix("imported:"))
            return file.takeIf { it.exists() && OfflineModelConfig.isValidImportedFile(it.length()) }
        }
        return null
    }

    data class InstalledOfflineModel(
        val id: String,
        val displayName: String,
        val fileName: String,
        val isBuiltIn: Boolean,
        val minRamGb: Double,
        val supportsVision: Boolean = false,
        val supportsText: Boolean = true,
        val capabilityLabel: String = "Text"
    )

    fun listInstalledModels(): List<InstalledOfflineModel> {
        val knownNames = OfflineModelConfig.ALL.map { it.fileName }.toSet()
        val builtIn = OfflineModelConfig.ALL.mapNotNull { spec ->
            if (!isModelDownloaded(spec.id)) return@mapNotNull null
            InstalledOfflineModel(
                id = spec.id,
                displayName = spec.displayName,
                fileName = spec.fileName,
                isBuiltIn = true,
                minRamGb = spec.minRamGb,
                supportsVision = spec.supportsVision,
                supportsText = true,
                capabilityLabel = OfflineModelValidator.capabilityLabel(
                    OfflineModelValidator.ModelCapabilities(
                        supportsVision = spec.supportsVision,
                        supportsText = true,
                        displayName = spec.displayName,
                        minRamGb = spec.minRamGb,
                        isBuiltIn = true
                    )
                )
            )
        }
        val imported = modelsDir.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.endsWith(".litertlm", ignoreCase = true) &&
                    file.name !in knownNames &&
                    OfflineModelConfig.isValidImportedFile(file.length())
            }
            ?.map { file ->
                val cap = OfflineModelValidator.inferCapabilities(file.name, file.length())
                InstalledOfflineModel(
                    id = OfflineModelConfig.importedId(file.name),
                    displayName = cap.displayName,
                    fileName = file.name,
                    isBuiltIn = false,
                    minRamGb = cap.minRamGb,
                    supportsVision = cap.supportsVision,
                    supportsText = cap.supportsText,
                    capabilityLabel = OfflineModelValidator.capabilityLabel(cap)
                )
            }
            .orEmpty()
        return builtIn + imported
    }

    fun isModelInstalled(modelId: String): Boolean =
        listInstalledModels().any { it.id == modelId }

    fun isModelDownloaded(modelType: String): Boolean {
        val spec = OfflineModelConfig.specFor(modelType) ?: return false
        val file = File(modelsDir, spec.fileName)
        return file.exists() && OfflineModelConfig.isValidModelFile(spec, file.length())
    }

    fun downloadModel(modelType: String): Flow<DownloadStatus> = callbackFlow {
        val spec = OfflineModelConfig.specFor(modelType)
        if (spec == null) {
            trySend(DownloadStatus.Error("Unknown model type"))
            close()
            return@callbackFlow
        }

        if (!isSystemCompatible(modelType)) {
            trySend(
                DownloadStatus.Error(
                    "Device needs at least ${spec.minRamGb} GB RAM for ${spec.displayName}. " +
                        "Your device: ${String.format("%.1f", getTotalRamGb())} GB."
                )
            )
            close()
            return@callbackFlow
        }

        val dest = File(modelsDir, spec.fileName)
        val temp = File(modelsDir, "${spec.fileName}.download")
        val url = OfflineModelConfig.downloadUrl(spec)
        val token = secureStorageManager.getHuggingFaceToken()?.trim().orEmpty()

        var cancelled = false
        invokeOnClose { cancelled = true }

        trySend(DownloadStatus.Downloading(if (temp.exists()) estimateProgress(temp.length(), spec) else 0f))

        try {
            withContext(Dispatchers.IO) {
                downloadWithResume(
                    url = url,
                    temp = temp,
                    dest = dest,
                    spec = spec,
                    token = token.takeIf { it.isNotBlank() },
                    isCancelled = { cancelled || !isActive },
                    onProgress = { progress ->
                        trySend(DownloadStatus.Downloading(progress))
                    }
                )
            }
            trySend(DownloadStatus.Success)
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed: $url", e)
            trySend(DownloadStatus.Error(e.message ?: "Download failed"))
        }
        close()
    }

    private fun downloadWithResume(
        url: String,
        temp: File,
        dest: File,
        spec: OfflineModelConfig.ModelSpec,
        token: String?,
        isCancelled: () -> Boolean,
        onProgress: (Float) -> Unit
    ) {
        var partialSize = if (temp.exists()) temp.length() else 0L
        if (partialSize > 0 && partialSize >= spec.expectedSizeBytes) {
            temp.delete()
            partialSize = 0L
        }

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 300_000
            setRequestProperty("User-Agent", "GymAI/1.0 (Android; LiteRT-LM)")
            if (partialSize > 0) {
                setRequestProperty("Range", "bytes=$partialSize-")
                setRequestProperty("Accept-Encoding", "identity")
            }
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }

        try {
            connection.connect()
            val code = connection.responseCode
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    if (partialSize > 0) temp.delete()
                    partialSize = 0L
                }
                HttpURLConnection.HTTP_PARTIAL -> { /* resume OK */ }
                else -> throw IllegalStateException(
                    when (code) {
                        401, 403 -> "Download blocked (HTTP $code). Try Import, or add optional HF token in Settings."
                        404 -> "Model not found. URL: $url"
                        else -> "Download failed (HTTP $code)"
                    }
                )
            }

            val totalHeader = connection.getHeaderField("Content-Length")?.toLongOrNull()
            val totalBytes = when {
                totalHeader != null && totalHeader > 0 -> partialSize + totalHeader
                else -> spec.expectedSizeBytes
            }

            connection.inputStream.use { input ->
                FileOutputStream(temp, partialSize > 0).use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = partialSize
                    var lastReport = 0L
                    while (true) {
                        if (isCancelled()) throw IllegalStateException("Download cancelled")
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 200) {
                            onProgress((downloaded.toFloat() / totalBytes).coerceIn(0f, 0.99f))
                            lastReport = now
                        }
                    }
                    output.flush()
                }
            }

            if (!OfflineModelConfig.isValidModelFile(spec, temp.length())) {
                temp.delete()
                throw IllegalStateException(
                    "Download incomplete (${temp.length()} bytes). Expected ~${spec.expectedSizeBytes / 1_000_000_000} GB."
                )
            }

            if (dest.exists()) dest.delete()
            if (!temp.renameTo(dest)) {
                temp.copyTo(dest, overwrite = true)
                temp.delete()
            }
            onProgress(1f)
        } finally {
            connection.disconnect()
        }
    }

    private fun estimateProgress(currentBytes: Long, spec: OfflineModelConfig.ModelSpec): Float =
        (currentBytes.toFloat() / spec.expectedSizeBytes).coerceIn(0f, 0.99f)

    suspend fun importAnyModel(uri: Uri): DownloadStatus = withContext(Dispatchers.IO) {
        try {
            val rawName = appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            } ?: "imported-model.litertlm"

            if (!rawName.endsWith(".litertlm", ignoreCase = true)) {
                return@withContext DownloadStatus.Error(
                    "Unsupported file. Import a .litertlm LiteRT-LM model only (not .bin or other formats)."
                )
            }

            val fileName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val dest = File(modelsDir, fileName)
            val temp = File(modelsDir, "$fileName.import")

            appContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            } ?: return@withContext DownloadStatus.Error("Cannot read selected file")

            when (val validation = OfflineModelValidator.validateImportedFile(appContext, temp)) {
                is OfflineModelValidator.ValidationResult.Invalid -> {
                    temp.delete()
                    return@withContext DownloadStatus.Error(validation.message)
                }
                is OfflineModelValidator.ValidationResult.Valid -> {
                    if (validation.capabilities.minRamGb > getTotalRamGb()) {
                        temp.delete()
                        return@withContext DownloadStatus.Error(
                            "Device needs at least ${validation.capabilities.minRamGb} GB RAM for this model."
                        )
                    }
                }
            }

            if (dest.exists()) dest.delete()
            if (!temp.renameTo(dest)) {
                temp.copyTo(dest, overwrite = true)
                temp.delete()
            }
            DownloadStatus.Success
        } catch (e: Exception) {
            DownloadStatus.Error(e.message ?: "Import failed")
        }
    }

    suspend fun importModelFromUri(modelType: String, uri: Uri): DownloadStatus = withContext(Dispatchers.IO) {
        val spec = OfflineModelConfig.specFor(modelType)
            ?: return@withContext DownloadStatus.Error("Unknown model type")

        val dest = File(modelsDir, spec.fileName)
        val temp = File(modelsDir, "${spec.fileName}.import")

        try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            } ?: return@withContext DownloadStatus.Error("Cannot read selected file")

            if (!OfflineModelConfig.isValidModelFile(spec, temp.length())) {
                temp.delete()
                return@withContext DownloadStatus.Error(
                    "Invalid model file (${temp.length() / 1_000_000} MB). Expected ${spec.fileName} (~${spec.expectedSizeBytes / 1_000_000_000} GB)."
                )
            }

            if (dest.exists()) dest.delete()
            if (!temp.renameTo(dest)) {
                temp.copyTo(dest, overwrite = true)
                temp.delete()
            }
            DownloadStatus.Success
        } catch (e: Exception) {
            temp.delete()
            DownloadStatus.Error(e.message ?: "Import failed")
        }
    }

    fun deleteModel(modelType: String) {
        OfflineModelConfig.specFor(modelType)?.let { spec ->
            File(modelsDir, spec.fileName).delete()
            File(modelsDir, "${spec.fileName}.download").delete()
        }
        if (modelType.startsWith("imported:")) {
            resolveModelFile(modelType)?.delete()
        }
        File(modelsDir, "gemma-2b.bin").delete()
        File(modelsDir, "gemma-4b.bin").delete()
        File(modelsDir, "gemma-3n-E2B-it-int4.litertlm").delete()
        File(modelsDir, "gemma-3n-E4B-it-int4.litertlm").delete()
    }

    suspend fun validateInstalledModel(modelId: String): OfflineModelValidator.ValidationResult {
        val file = resolveModelFile(modelId)
            ?: return OfflineModelValidator.ValidationResult.Invalid("Model file missing.")
        return OfflineModelValidator.validateImportedFile(appContext, file)
    }

    companion object {
        private const val TAG = "ModelDownload"
    }
}

sealed class DownloadStatus {
    data class Downloading(val progress: Float) : DownloadStatus()
    data object Success : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}
