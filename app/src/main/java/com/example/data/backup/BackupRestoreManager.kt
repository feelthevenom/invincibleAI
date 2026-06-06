package com.example.data.backup

import android.content.Context
import android.net.Uri
import com.example.data.AppDatabase
import com.example.data.ProgressPhoto
import com.example.data.UserProfile
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupRestoreManager {

    const val BACKUP_VERSION = 1
    const val BACKUP_MIME = "application/zip"
    const val BACKUP_FILE_NAME = "gymai_backup.zip"
    const val MANIFEST_FILE = "manifest.json"
    const val SETTINGS_FILE = "settings.json"
    const val DB_ZIP_PATH = "database/gym_database"
    const val PHOTOS_ZIP_DIR = "progress_photos/"

    data class BackupManifest(
        val version: Int,
        val appVersion: String,
        val createdAt: Long,
        val hasDatabase: Boolean,
        val hasSettings: Boolean,
        val photoCount: Int
    )

    data class BackupSettings(
        val userProfile: UserProfile? = null
    )

    sealed class BackupResult {
        data object Success : BackupResult()
        data class Error(val message: String) : BackupResult()
    }

    fun progressPhotosDir(context: Context): File {
        val dir = File(context.filesDir, "progress_photos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun exportBackup(
        context: Context,
        outputUri: Uri,
        userProfile: UserProfile
    ): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            AppDatabase.checkpoint(context)
            val dbFile = context.getDatabasePath("gym_database")
            if (!dbFile.exists()) return@runCatching BackupResult.Error("Database not found")

            val photosDir = progressPhotosDir(context)
            val photoFiles = photosDir.listFiles()?.filter { it.isFile }.orEmpty()
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val settingsJson = moshi.adapter(BackupSettings::class.java)
                .toJson(BackupSettings(userProfile = userProfile))

            val manifest = JSONObject().apply {
                put("version", BACKUP_VERSION)
                put("appVersion", "1.0")
                put("createdAt", System.currentTimeMillis())
                put("hasDatabase", true)
                put("hasSettings", true)
                put("photoCount", photoFiles.size)
            }.toString()

            context.contentResolver.openOutputStream(outputUri)?.use { rawOut ->
                ZipOutputStream(BufferedOutputStream(rawOut)).use { zip ->
                    zip.putNextEntry(ZipEntry(MANIFEST_FILE))
                    zip.write(manifest.toByteArray())
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry(SETTINGS_FILE))
                    zip.write(settingsJson.toByteArray())
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry(DB_ZIP_PATH))
                    FileInputStream(dbFile).use { it.copyTo(zip) }
                    zip.closeEntry()

                    photoFiles.forEach { file ->
                        zip.putNextEntry(ZipEntry(PHOTOS_ZIP_DIR + file.name))
                        FileInputStream(file).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            } ?: return@runCatching BackupResult.Error("Cannot write backup file")

            BackupResult.Success
        }.getOrElse { BackupResult.Error(it.message ?: "Export failed") }
    }

    suspend fun validateBackup(context: Context, inputUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(inputUri)?.use { rawIn ->
                ZipInputStream(BufferedInputStream(rawIn)).use { zip ->
                    var hasManifest = false
                    var hasDb = false
                    var entryCount = 0
                    var entry = zip.nextEntry
                    while (entry != null) {
                        entryCount++
                        when {
                            entry.name == MANIFEST_FILE -> {
                                hasManifest = true
                                val text = zip.readBytes().decodeToString()
                                if (text.isBlank()) {
                                    return@runCatching BackupResult.Error("Invalid backup: manifest.json is empty")
                                }
                                val json = JSONObject(text)
                                if (json.optInt("version", 0) < 1) {
                                    return@runCatching BackupResult.Error("Unsupported backup version")
                                }
                            }
                            entry.name == DB_ZIP_PATH -> hasDb = true
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                    when {
                        entryCount == 0 -> BackupResult.Error("Empty file. Choose a gymai_backup.zip exported from Gym AI.")
                        !hasManifest -> BackupResult.Error("Not a Gym AI backup: manifest.json is missing")
                        !hasDb -> BackupResult.Error("Invalid backup: database file is missing")
                        else -> BackupResult.Success
                    }
                }
            } ?: BackupResult.Error("Cannot read the selected file")
        }.getOrElse { BackupResult.Error(it.message ?: "Validation failed") }
    }

    suspend fun importBackup(context: Context, inputUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val validation = validateBackup(context, inputUri)
        if (validation is BackupResult.Error) return@withContext validation

        runCatching {
            AppDatabase.closeDatabase()

            val dbFile = context.getDatabasePath("gym_database")
            dbFile.parentFile?.mkdirs()
            val photosDir = progressPhotosDir(context)
            photosDir.listFiles()?.forEach { it.delete() }

            context.contentResolver.openInputStream(inputUri)?.use { rawIn ->
                ZipInputStream(BufferedInputStream(rawIn)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == DB_ZIP_PATH -> {
                                FileOutputStream(dbFile).use { out -> zip.copyTo(out) }
                            }
                            entry.name.startsWith(PHOTOS_ZIP_DIR) && !entry.isDirectory -> {
                                val name = entry.name.removePrefix(PHOTOS_ZIP_DIR)
                                if (name.isNotBlank()) {
                                    FileOutputStream(File(photosDir, name)).use { out -> zip.copyTo(out) }
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: return@runCatching BackupResult.Error("Cannot read backup file")

            AppDatabase.getDatabase(context)
            (context.applicationContext as com.example.GymApplication).refreshRepositoryAfterRestore()
            runBlocking {
                val dao = AppDatabase.getDatabase(context).gymDao()
                val profile = dao.getUserProfile().first()
                if (profile != null) {
                    dao.insertOrUpdateProfile(com.example.data.WorkoutSetupProgress.profileAfterRestore(profile))
                }
            }
            BackupResult.Success
        }.getOrElse {
            AppDatabase.getDatabase(context)
            BackupResult.Error(it.message ?: "Import failed")
        }
    }
}
