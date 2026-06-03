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
import kotlinx.coroutines.flow.collect
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
