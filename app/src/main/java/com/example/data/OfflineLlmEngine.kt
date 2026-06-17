package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Wraps LiteRT-LM (same stack as Google AI Edge Gallery) for on-device inference.
 */
class OfflineLlmEngine(private val context: Context) {

    data class ProbeResult(
        val success: Boolean,
        val supportsVision: Boolean,
        val supportsText: Boolean,
        val useVisionBackendAtInit: Boolean
    )

    private var engine: Engine? = null
    private var loadedModelPath: String? = null
    private var loadedWithVisionBackend: Boolean = false

    suspend fun generate(prompt: String, modelPath: String, image: Bitmap? = null): String =
        withContext(Dispatchers.IO) {
            ensureEngine(modelPath, enableVision = image != null)
            val eng = engine ?: throw IllegalStateException("Offline engine failed to initialize")

            val conversationConfig = ConversationConfig(
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3)
            )

            eng.createConversation(conversationConfig).use { conversation ->
                val contents = if (image != null) {
                    Contents.of(
                        Content.ImageBytes(bitmapToJpeg(image)),
                        Content.Text(prompt)
                    )
                } else {
                    Contents.of(Content.Text(prompt))
                }

                val builder = StringBuilder()
                conversation.sendMessageAsync(contents).collect { chunk ->
                    builder.append(chunk.toString())
                }
                builder.toString().trim()
            }
        }

    fun generateStream(prompt: String, modelPath: String, image: Bitmap? = null): Flow<String> = flow {
        ensureEngine(modelPath, enableVision = image != null)
        val eng = engine ?: throw IllegalStateException("Offline engine failed to initialize")

        val conversationConfig = ConversationConfig(
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3)
        )

        eng.createConversation(conversationConfig).use { conversation ->
            val contents = if (image != null) {
                Contents.of(
                    Content.ImageBytes(bitmapToJpeg(image)),
                    Content.Text(prompt)
                )
            } else {
                Contents.of(Content.Text(prompt))
            }
            conversation.sendMessageAsync(contents).collect { chunk ->
                val text = chunk.toString()
                if (text.isNotEmpty()) emit(text)
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun ensureEngine(modelPath: String, enableVision: Boolean) {
        val meta = OfflineModelMetadata.load(File(modelPath))
        val fileName = File(modelPath).name
        val likelyVision = OfflineModelValidator.likelyVisionModel(fileName)

        if (engine != null && loadedModelPath == modelPath) {
            val needsVisionReload = enableVision && !loadedWithVisionBackend
            if (!needsVisionReload) return
        }

        release()

        val cacheDir = context.cacheDir.absolutePath
        val visionAttempts = buildList {
            if (meta?.probeUsedVisionBackend == true || likelyVision || enableVision) add(true)
            add(false)
            if (!likelyVision && meta?.probeUsedVisionBackend != true) add(true)
        }.distinct()

        var lastError: Exception? = null
        for (needsVisionBackend in visionAttempts) {
            val config = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = cacheDir,
                visionBackend = if (needsVisionBackend) Backend.CPU() else null
            )
            try {
                Log.d(TAG, "Initializing LiteRT-LM: $modelPath (visionBackend=$needsVisionBackend)")
                val newEngine = Engine(config)
                newEngine.initialize()
                engine = newEngine
                loadedModelPath = modelPath
                loadedWithVisionBackend = needsVisionBackend
                Log.d(TAG, "LiteRT-LM ready")
                return
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Init failed (visionBackend=$needsVisionBackend)", e)
            }
        }
        throw lastError ?: IllegalStateException("Offline engine failed to initialize")
    }

    fun release() {
        try {
            engine?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Engine close error", e)
        }
        engine = null
        loadedModelPath = null
        loadedWithVisionBackend = false
    }

    /** Tries multiple LiteRT-LM init configs — VLMs (e.g. FastVLM) need visionBackend at init. */
    suspend fun probeModel(modelPath: String, fileName: String = File(modelPath).name): ProbeResult =
        withContext(Dispatchers.IO) {
            if (!File(modelPath).exists()) {
                return@withContext ProbeResult(false, false, false, false)
            }
            val likelyVision = OfflineModelValidator.likelyVisionModel(fileName)
            val likelyEmbedding = OfflineModelValidator.likelyEmbeddingModel(fileName)
            if (likelyEmbedding) {
                return@withContext ProbeResult(false, false, false, false)
            }

            val attempts = buildList {
                if (likelyVision) add(true)
                add(false)
                if (!likelyVision) add(true)
            }.distinct()

            for (withVisionBackend in attempts) {
                val probeEngine = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(),
                        cacheDir = context.cacheDir.absolutePath,
                        visionBackend = if (withVisionBackend) Backend.CPU() else null
                    )
                )
                try {
                    probeEngine.initialize()
                    val supportsVision = withVisionBackend || likelyVision
                    return@withContext ProbeResult(
                        success = true,
                        supportsVision = supportsVision,
                        supportsText = true,
                        useVisionBackendAtInit = withVisionBackend
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Probe failed (visionBackend=$withVisionBackend) for $fileName", e)
                } finally {
                    try {
                        probeEngine.close()
                    } catch (_: Exception) {
                    }
                }
            }
            ProbeResult(false, false, false, false)
        }

    private fun bitmapToJpeg(bitmap: Bitmap, quality: Int = 80): ByteArray {
        val safe = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } else {
            bitmap
        }
        val stream = ByteArrayOutputStream()
        safe.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        if (safe !== bitmap) safe.recycle()
        return stream.toByteArray()
    }

    companion object {
        private const val TAG = "OfflineLlmEngine"
    }
}
