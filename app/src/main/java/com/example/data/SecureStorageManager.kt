package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorageManager(context: Context) {

    private val sharedPreferences: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "secret_ai_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getGeminiApiKey(): String? {
        return sharedPreferences.getString(KEY_GEMINI_API, null)
    }

    fun saveGeminiApiKey(apiKey: String) {
        sharedPreferences.edit().putString(KEY_GEMINI_API, apiKey).apply()
    }

    fun clearGeminiApiKey() {
        sharedPreferences.edit().remove(KEY_GEMINI_API).apply()
    }

    fun getHuggingFaceToken(): String? {
        return sharedPreferences.getString(KEY_HF_TOKEN, null)
    }

    fun saveHuggingFaceToken(token: String) {
        sharedPreferences.edit().putString(KEY_HF_TOKEN, token).apply()
    }

    fun clearHuggingFaceToken() {
        sharedPreferences.edit().remove(KEY_HF_TOKEN).apply()
    }

    fun getGroqApiKey(): String? {
        return sharedPreferences.getString(KEY_GROQ_API, null)
    }

    fun saveGroqApiKey(apiKey: String) {
        sharedPreferences.edit().putString(KEY_GROQ_API, apiKey).apply()
    }

    fun clearGroqApiKey() {
        sharedPreferences.edit().remove(KEY_GROQ_API).apply()
    }

    fun getOpenRouterApiKey(): String? {
        return sharedPreferences.getString(KEY_OPENROUTER_API, null)
    }

    fun saveOpenRouterApiKey(apiKey: String) {
        sharedPreferences.edit().putString(KEY_OPENROUTER_API, apiKey).apply()
    }

    fun clearOpenRouterApiKey() {
        sharedPreferences.edit().remove(KEY_OPENROUTER_API).apply()
    }

    companion object {
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_HF_TOKEN = "huggingface_token"
        private const val KEY_GROQ_API = "groq_api_key"
        private const val KEY_OPENROUTER_API = "openrouter_api_key"
    }
}
