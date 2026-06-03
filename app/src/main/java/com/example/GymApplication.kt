package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.GymRepository
import com.example.data.LocalFoodRepository
import com.example.data.LocalExerciseRepository
import com.example.data.api.OpenFoodFactsRepository

class GymApplication : Application() {

    @Volatile
    private var repositoryRef: GymRepository? = null

    val repository: GymRepository
        get() = repositoryRef ?: synchronized(this) {
            repositoryRef ?: GymRepository(AppDatabase.getDatabase(this).gymDao())
                .also { repositoryRef = it }
        }

    val localFoodRepository by lazy { LocalFoodRepository(this) }
    val localExerciseRepository by lazy { LocalExerciseRepository(this) }
    val offRepository by lazy { OpenFoodFactsRepository() }
    val secureStorageManager by lazy { com.example.data.SecureStorageManager(this) }
    val modelDownloadManager by lazy { com.example.data.ModelDownloadManager(this, secureStorageManager) }
    val aiManager by lazy { com.example.data.AiManager(this, secureStorageManager, modelDownloadManager) }

    fun refreshRepositoryAfterRestore() {
        synchronized(this) {
            repositoryRef = null
        }
    }
}
