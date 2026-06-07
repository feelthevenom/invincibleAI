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

/**
 * Wraps LiteRT-LM (same stack as Google AI Edge Gallery) for on-device inference.
 */
class OfflineLlmEngine(private val context: Context) {

    private var engine: Engine? = null
    private var loadedModelPath: String? = null

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

    /** Streams tokens as they are generated — used for live Coach chat UI. */
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
        if (engine != null && loadedModelPath == modelPath) return

        release()

        val cacheDir = context.cacheDir.absolutePath
        val config = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            cacheDir = cacheDir,
            visionBackend = if (enableVision) Backend.CPU() else null
        )

        Log.d(TAG, "Initializing LiteRT-LM: $modelPath (vision=$enableVision)")
        val newEngine = Engine(config)
        newEngine.initialize()
        engine = newEngine
        loadedModelPath = modelPath
        Log.d(TAG, "LiteRT-LM ready")
    }

    fun release() {
        try {
            engine?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Engine close error", e)
        }
        engine = null
        loadedModelPath = null
    }

    /** Quick init test — returns true if LiteRT-LM can load the model file. */
    suspend fun probeModel(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        if (!java.io.File(modelPath).exists()) return@withContext false
        val probeEngine = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath,
                visionBackend = null
            )
        )
        try {
            probeEngine.initialize()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Probe failed for $modelPath", e)
            false
        } finally {
            try {
                probeEngine.close()
            } catch (_: Exception) {
            }
        }
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
