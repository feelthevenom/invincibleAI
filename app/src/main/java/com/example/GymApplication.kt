package com.example

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.example.notifications.WaterNotificationHelper
import com.example.notifications.WorkoutNotificationHelper
import com.example.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.example.data.GymRepository
import com.example.data.LocalFoodRepository
import com.example.data.LocalExerciseRepository
import com.example.data.api.OpenFoodFactsRepository

class GymApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        WaterNotificationHelper.createChannel(this)
        WorkoutNotificationHelper.createChannel(this)
        rescheduleRemindersIfNeeded()
    }

    private fun rescheduleRemindersIfNeeded() {
        val appContext = this
        Thread {
            try {
                val profile = runBlocking {
                    AppDatabase.getDatabase(appContext).gymDao().getUserProfile().first()
                } ?: return@Thread
                if (profile.waterReminderEnabled) {
                    WaterNotificationHelper.scheduleReminders(appContext, profile)
                }
                if (profile.workoutReminderEnabled) {
                    WorkoutNotificationHelper.scheduleReminder(appContext, profile)
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
    }

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
    val exerciseGuideRepository by lazy {
        com.example.data.ExerciseGuideRepository(this, AppDatabase.getDatabase(this).gymDao())
    }
    val coachHistoryRepository by lazy {
        com.example.data.CoachHistoryRepository(AppDatabase.getDatabase(this).gymDao())
    }

    fun refreshRepositoryAfterRestore() {
        synchronized(this) {
            repositoryRef = null
        }
    }
}
