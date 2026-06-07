package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.api.ExerciseDbApiClient
import com.example.data.api.ExerciseDbExerciseDto
import com.example.data.api.ExerciseDbRateLimitException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

enum class GuideSource { API, AI }

data class ExerciseGuideDetail(
    val displayName: String,
    val apiName: String,
    val exerciseId: String,
    val gifUrl: String?,
    val localGifPath: String?,
    val instructions: List<String>,
    val targetMuscles: List<String>,
    val equipments: List<String>,
    val bodyParts: List<String>,
    val source: GuideSource,
    val fromCache: Boolean
) {
    val gifModel: Any?
        get() {
            if (source == GuideSource.AI) return null
            val local = localGifPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
            return local ?: gifUrl?.takeIf { it.isNotBlank() }
        }
}

sealed class ExerciseGuideResult {
    data class Success(val guide: ExerciseGuideDetail) : ExerciseGuideResult()
    data object OfflineNoCache : ExerciseGuideResult()
    data object NoOnlineMatch : ExerciseGuideResult()
    data object RateLimited : ExerciseGuideResult()
    data class Error(val message: String) : ExerciseGuideResult()
}

data class ExerciseCatalogEntry(
    val exerciseId: String,
    val name: String,
    val gifUrl: String
)

object ExerciseNameMatcher {
    private val stopWords = setOf("the", "a", "an", "with", "on", "in", "and", "or", "male", "female")
    private val abbreviations = mapOf(
        "db" to "dumbbell",
        "bb" to "barbell",
        "bw" to "bodyweight",
        "ohp" to "overhead press",
        "rdl" to "romanian deadlift",
        "cgbp" to "close grip bench press"
    )

    fun expandAbbreviations(name: String): String {
        val tokens = normalize(name).split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return name
        return tokens.joinToString(" ") { abbreviations[it] ?: it }
    }

    fun normalize(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    fun tokens(name: String): List<String> =
        normalize(name).split(" ").filter { it.length > 1 && it !in stopWords }

    fun score(query: String, candidate: String): Int {
        val qNorm = normalize(query)
        val cNorm = normalize(candidate)
        if (qNorm.isBlank() || cNorm.isBlank()) return 0
        if (qNorm == cNorm) return 10_000

        val qTokens = tokens(query)
        val cTokens = tokens(candidate).toSet()
        if (qTokens.isEmpty()) return 0

        // Every query token must appear in the candidate name (abbreviations expanded for matching).
        val expandedTokens = qTokens.map { abbreviations[it] ?: it }
        val allPresent = expandedTokens.all { qt ->
            cTokens.any { ct -> ct == qt || (qt.length >= 3 && (ct.contains(qt) || qt.contains(ct))) }
        }
        if (!allPresent) return 0

        if (cNorm.contains(qNorm)) return 5_000 + qNorm.length

        var score = 0
        for (token in qTokens) {
            score += when {
                cTokens.contains(token) -> 100
                cTokens.any { it.contains(token) || token.contains(it) } -> 60
                else -> 0
            }
        }

        val extraTokens = cTokens.count { ct ->
            qTokens.none { qt -> ct == qt || ct.contains(qt) || qt.contains(ct) }
        }
        score -= extraTokens * 25
        return score
    }

    fun bestMatch(query: String, candidates: List<ExerciseCatalogEntry>, minScore: Int = 80): ExerciseCatalogEntry? {
        if (candidates.isEmpty()) return null
        return candidates
            .map { it to score(query, it.name) }
            .filter { it.second >= minScore }
            .maxByOrNull { it.second }
            ?.first
    }
}

object ExerciseStepFormatter {
    private val stepPrefix = Regex("^Step:(\\d+)\\s*", RegexOption.IGNORE_CASE)

    fun toApiFormat(steps: List<String>): List<String> =
        steps.mapIndexed { index, raw ->
            val trimmed = raw.trim()
            if (stepPrefix.containsMatchIn(trimmed)) trimmed
            else {
                val withoutNumber = trimmed.removePrefix("${index + 1}.").removePrefix("${index + 1})").trim()
                "Step:${index + 1} $withoutNumber"
            }
        }

    fun parseAiSteps(raw: String): List<String> {
        val regex = Regex("\\[[\\s\\S]*\\]")
        val jsonArray = regex.find(raw)?.value?.let { JSONArray(it) }
        if (jsonArray != null) {
            return buildList {
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.get(i)
                    when (item) {
                        is JSONObject -> {
                            val step = item.optString("step", item.optString("instruction", "")).trim()
                            if (step.isNotBlank()) add(step)
                        }
                        is String -> if (item.isNotBlank()) add(item.trim())
                    }
                }
            }.let { toApiFormat(it) }.takeIf { it.isNotEmpty() }
                ?: emptyList()
        }

        val lines = raw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                line.removePrefix("-").removePrefix("•").trim()
            }
        return toApiFormat(lines)
    }

    data class StepParts(val tag: String, val body: String)

    fun parts(step: String, fallbackIndex: Int): StepParts {
        val match = Regex("^Step:(\\d+)\\s*(.*)", RegexOption.IGNORE_CASE).find(step.trim())
        return if (match != null) {
            StepParts("Step:${match.groupValues[1]}", match.groupValues[2].trim())
        } else {
            StepParts("Step:${fallbackIndex + 1}", step.trim())
        }
    }
}

class ExerciseGuideRepository(
    private val context: Context,
    private val gymDao: GymDao,
    private val api: ExerciseDbApiClient = ExerciseDbApiClient(),
    private val localExercises: LocalExerciseRepository = LocalExerciseRepository(context)
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val gifDir: File
        get() = File(context.filesDir, "exercise_guides").also { it.mkdirs() }

    private val catalogFile: File
        get() = File(context.filesDir, "exercise_catalog.json")

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun loadGuide(exerciseName: String, forceOnline: Boolean = false): ExerciseGuideResult =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(45_000L) {
                    loadGuideInternal(exerciseName, forceOnline)
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                ExerciseGuideResult.Error("Tutorial load timed out. Try again or use AI Fill.")
            } catch (_: CancellationException) {
                ExerciseGuideResult.Error("Tutorial load was interrupted. Try again.")
            }
        }

    private suspend fun loadGuideInternal(exerciseName: String, forceOnline: Boolean): ExerciseGuideResult {
        val lookupKey = ExerciseNameMatcher.normalize(exerciseName)
        if (lookupKey.isBlank()) {
            return ExerciseGuideResult.Error("Invalid exercise name.")
        }

        if (!forceOnline) {
            gymDao.getCachedExerciseGuide(lookupKey)?.let { cached ->
                val cacheValid = cached.source == "ai" ||
                    ExerciseNameMatcher.score(exerciseName, cached.apiName) >= 80
                if (cacheValid) {
                    return ExerciseGuideResult.Success(cached.toDetail(exerciseName))
                }
            }
        }

        if (!isOnline()) {
            return ExerciseGuideResult.OfflineNoCache
        }

        return try {
            val full = findExerciseOnline(exerciseName)
                ?: return ExerciseGuideResult.NoOnlineMatch

            val localPath = downloadGif(full.exerciseId, full.gifUrl)
            val entity = CachedExerciseGuide(
                lookupKey = lookupKey,
                exerciseId = full.exerciseId,
                apiName = full.name,
                gifUrl = full.gifUrl,
                localGifPath = localPath,
                instructionsJson = JSONArray(full.instructions.orEmpty()).toString(),
                targetMusclesJson = JSONArray(full.targetMuscles.orEmpty()).toString(),
                equipmentsJson = JSONArray(full.equipments.orEmpty()).toString(),
                bodyPartsJson = JSONArray(full.bodyParts.orEmpty()).toString(),
                source = "api"
            )
            gymDao.upsertCachedExerciseGuide(entity)
            ExerciseGuideResult.Success(entity.toDetail(exerciseName, fromCache = false))
        } catch (_: ExerciseDbRateLimitException) {
            ExerciseGuideResult.RateLimited
        } catch (_: CancellationException) {
            ExerciseGuideResult.Error("Tutorial load was interrupted. Try again.")
        } catch (e: Exception) {
            ExerciseGuideResult.Error(e.message ?: "Failed to load exercise tutorial.")
        }
    }

    suspend fun saveAiGuide(
        exerciseName: String,
        steps: List<String>,
        targetMuscles: List<String> = emptyList(),
        equipments: List<String> = emptyList(),
        bodyParts: List<String> = emptyList()
    ): ExerciseGuideDetail = withContext(Dispatchers.IO) {
        val lookupKey = ExerciseNameMatcher.normalize(exerciseName)
        val formatted = ExerciseStepFormatter.toApiFormat(steps)
        val existing = gymDao.getCachedExerciseGuide(lookupKey)
        val keepApiMedia = existing?.source == "api" &&
            ExerciseNameMatcher.score(exerciseName, existing.apiName) >= 80
        val entity = CachedExerciseGuide(
            lookupKey = lookupKey,
            exerciseId = existing?.takeIf { keepApiMedia }?.exerciseId ?: "ai_${lookupKey.hashCode()}",
            apiName = exerciseName,
            gifUrl = if (keepApiMedia) existing?.gifUrl.orEmpty() else "",
            localGifPath = if (keepApiMedia) existing?.localGifPath else null,
            instructionsJson = JSONArray(formatted).toString(),
            targetMusclesJson = JSONArray(
                targetMuscles.ifEmpty { parseJsonArray(existing?.targetMusclesJson.orEmpty()) }
            ).toString(),
            equipmentsJson = JSONArray(
                equipments.ifEmpty { parseJsonArray(existing?.equipmentsJson.orEmpty()) }
            ).toString(),
            bodyPartsJson = JSONArray(
                bodyParts.ifEmpty { parseJsonArray(existing?.bodyPartsJson.orEmpty()) }
            ).toString(),
            source = "ai"
        )
        gymDao.upsertCachedExerciseGuide(entity)
        entity.toDetail(exerciseName, fromCache = false)
    }

    private suspend fun findExerciseOnline(exerciseName: String): ExerciseDbExerciseDto? {
        for (query in buildSearchQueries(exerciseName)) {
            val results = api.searchByName(query)
            pickFromApiResults(exerciseName, results)?.let { return it }
            val altResults = api.searchByQuery(query)
            pickFromApiResults(exerciseName, altResults)?.let { return it }
        }

        val diskCatalog = loadCatalogFromDisk().orEmpty()
        if (diskCatalog.isNotEmpty()) {
            ExerciseNameMatcher.bestMatch(exerciseName, diskCatalog, minScore = 60)?.let { match ->
                api.getById(match.exerciseId)?.let { return it }
            }
        }
        return null
    }

    private fun buildSearchQueries(exerciseName: String): List<String> {
        val queries = linkedSetOf(exerciseName.trim(), ExerciseNameMatcher.expandAbbreviations(exerciseName))
        localExercises.findByNameOrAlias(exerciseName)?.let { bundled ->
            queries.add(bundled.name)
            bundled.aliases.forEach { queries.add(it) }
        }
        return queries.filter { it.isNotBlank() }
    }

    private fun pickFromApiResults(
        exerciseName: String,
        results: List<ExerciseDbExerciseDto>
    ): ExerciseDbExerciseDto? {
        if (results.isEmpty()) return null
        val candidates = results.map { ExerciseCatalogEntry(it.exerciseId, it.name, it.gifUrl) }
        val bestEntry = ExerciseNameMatcher.bestMatch(exerciseName, candidates, minScore = 60)
            ?: candidates.firstOrNull()
        return bestEntry?.let { entry -> results.find { it.exerciseId == entry.exerciseId } }
            ?: results.first()
    }

    private fun loadCatalogFromDisk(): List<ExerciseCatalogEntry>? {
        if (!catalogFile.exists()) return null
        return try {
            val array = JSONArray(catalogFile.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        ExerciseCatalogEntry(
                            exerciseId = obj.getString("exerciseId"),
                            name = obj.getString("name"),
                            gifUrl = obj.getString("gifUrl")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun downloadGif(exerciseId: String, url: String): String? {
        if (url.isBlank()) return null
        val target = File(gifDir, "$exerciseId.gif")
        if (target.exists() && target.length() > 0) return target.absolutePath
        return suspendCancellableCoroutine { cont ->
            val request = Request.Builder().url(url).get().build()
            val call = httpClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        cont.resume(null)
                        return@suspendCancellableCoroutine
                    }
                    val body = response.body?.bytes()
                    if (body == null) {
                        cont.resume(null)
                        return@suspendCancellableCoroutine
                    }
                    target.writeBytes(body)
                    cont.resume(target.absolutePath)
                }
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    private fun CachedExerciseGuide.toDetail(displayName: String, fromCache: Boolean = true) = ExerciseGuideDetail(
        displayName = displayName,
        apiName = apiName,
        exerciseId = exerciseId,
        gifUrl = if (source == "ai") null else gifUrl.takeIf { it.isNotBlank() },
        localGifPath = if (source == "ai") null else localGifPath,
        instructions = parseJsonArray(instructionsJson),
        targetMuscles = parseJsonArray(targetMusclesJson),
        equipments = parseJsonArray(equipmentsJson),
        bodyParts = parseJsonArray(bodyPartsJson),
        source = if (source == "ai") GuideSource.AI else GuideSource.API,
        fromCache = fromCache
    )

    private fun parseJsonArray(json: String): List<String> {
        return try {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    add(array.optString(i))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
