package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray

enum class GuideSource { BUNDLED, AI, DATASET }

data class ExerciseGuideDetail(
    val displayName: String,
    val apiName: String,
    val exerciseId: String,
    val instructions: List<String>,
    val targetMuscles: List<String>,
    val equipments: List<String>,
    val bodyParts: List<String>,
    val source: GuideSource,
    val fromCache: Boolean
)

sealed class ExerciseGuideResult {
    data class Success(val guide: ExerciseGuideDetail) : ExerciseGuideResult()
    data object OfflineNoCache : ExerciseGuideResult()
    data object NoOnlineMatch : ExerciseGuideResult()
    data class Error(val message: String) : ExerciseGuideResult()
}

class ExerciseGuideRepository(
    context: Context,
    private val gymDao: GymDao,
    private val exerciseRepository: ExerciseRepository = ExerciseRepository(context),
    private val bundledGuides: BundledExerciseGuideRepository = BundledExerciseGuideRepository(context),
    private val localExercises: LocalExerciseRepository = LocalExerciseRepository(context)
) {
    private val appContext = context.applicationContext

    fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun loadGuide(exerciseName: String, forceOnline: Boolean = false): ExerciseGuideResult =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(15_000L) {
                    loadGuideInternal(exerciseName, forceOnline)
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                ExerciseGuideResult.Error("Tutorial load timed out. Try again.")
            } catch (_: CancellationException) {
                ExerciseGuideResult.Error("Tutorial load was interrupted. Try again.")
            }
        }

    private suspend fun loadGuideInternal(exerciseName: String, forceOnline: Boolean): ExerciseGuideResult {
        val lookupKey = ExerciseNameMatcher.normalize(exerciseName)
        if (lookupKey.isBlank()) {
            return ExerciseGuideResult.Error("Invalid exercise name.")
        }

        val dataset = exerciseRepository.findBestMatch(exerciseName, minScore = 120)
            ?: exerciseRepository.findBestMatch(exerciseName, minScore = 80)
        val bundled = bundledGuides.resolve(exerciseName)
        val inAppCatalog = localExercises.findByNameOrAlias(exerciseName) != null

        if (dataset == null && bundled == null && !inAppCatalog) {
            return ExerciseGuideResult.NoOnlineMatch
        }

        if (!forceOnline) {
            gymDao.getCachedExerciseGuide(lookupKey)?.let { cached ->
                if (ExerciseNameMatcher.score(exerciseName, cached.apiName) >= 80) {
                    val detail = mergeAllSources(
                        cached.toDetail(exerciseName),
                        dataset,
                        bundled
                    )
                    return ExerciseGuideResult.Success(detail)
                }
            }
        }

        val detail = buildGuide(exerciseName, dataset, bundled)
        persistGuide(
            lookupKey,
            exerciseName,
            detail,
            source = when (detail.source) {
                GuideSource.AI -> "ai"
                GuideSource.DATASET -> "dataset"
                GuideSource.BUNDLED -> "bundled"
            }
        )
        return ExerciseGuideResult.Success(detail)
    }

    suspend fun saveAiGuide(
        exerciseName: String,
        steps: List<String>,
        targetMuscles: List<String> = emptyList(),
        equipments: List<String> = emptyList(),
        bodyParts: List<String> = emptyList(),
        datasetExerciseId: String? = null
    ): ExerciseGuideDetail = withContext(Dispatchers.IO) {
        val lookupKey = ExerciseNameMatcher.normalize(exerciseName)
        val formatted = ExerciseStepFormatter.toApiFormat(steps)
        val dataset = datasetExerciseId?.let { exerciseRepository.getExerciseById(it) }
            ?: exerciseRepository.findBestMatch(exerciseName)
        val bundled = bundledGuides.resolve(exerciseName)
        val detail = ExerciseGuideDetail(
            displayName = exerciseName,
            apiName = dataset?.name ?: exerciseName,
            exerciseId = dataset?.id ?: bundled?.exerciseId ?: "custom_${lookupKey.hashCode()}",
            instructions = formatted,
            targetMuscles = targetMuscles,
            equipments = equipments,
            bodyParts = bodyParts,
            source = GuideSource.AI,
            fromCache = false
        )
        val merged = mergeAllSources(detail, dataset, bundled)
        persistGuide(lookupKey, exerciseName, merged, source = "ai")
        merged
    }

    private fun buildGuide(
        exerciseName: String,
        dataset: Exercise?,
        bundled: ResolvedExerciseGuide?
    ): ExerciseGuideDetail {
        val fromDataset = dataset?.let { guideFromDataset(exerciseName, it) }
        val fromBundled = bundled?.let { guideFromBundled(exerciseName, it) }

        return mergeAllSources(
            fromDataset ?: fromBundled ?: ExerciseGuideDetail(
                displayName = exerciseName,
                apiName = exerciseName,
                exerciseId = "unknown_${ExerciseNameMatcher.normalize(exerciseName).hashCode()}",
                instructions = emptyList(),
                targetMuscles = emptyList(),
                equipments = emptyList(),
                bodyParts = emptyList(),
                source = GuideSource.BUNDLED,
                fromCache = false
            ),
            dataset,
            bundled
        )
    }

    private fun guideFromDataset(exerciseName: String, dataset: Exercise): ExerciseGuideDetail =
        ExerciseGuideDetail(
            displayName = exerciseName,
            apiName = dataset.name,
            exerciseId = dataset.id,
            instructions = ExerciseStepFormatter.toApiFormat(dataset.instructions),
            targetMuscles = buildList {
                dataset.target.takeIf { it.isNotBlank() }?.let { add(it) }
                addAll(dataset.secondaryMuscles)
            }.distinct(),
            equipments = listOfNotNull(dataset.equipment.takeIf { it.isNotBlank() }),
            bodyParts = listOfNotNull(dataset.bodyPart.takeIf { it.isNotBlank() }),
            source = GuideSource.DATASET,
            fromCache = false
        )

    private fun guideFromBundled(exerciseName: String, bundled: ResolvedExerciseGuide): ExerciseGuideDetail =
        ExerciseGuideDetail(
            displayName = exerciseName,
            apiName = exerciseName,
            exerciseId = bundled.exerciseId,
            instructions = bundled.steps,
            targetMuscles = bundled.targetMuscles,
            equipments = bundled.equipments,
            bodyParts = bundled.bodyParts,
            source = GuideSource.BUNDLED,
            fromCache = false
        )

    private fun mergeAllSources(
        base: ExerciseGuideDetail,
        dataset: Exercise?,
        bundled: ResolvedExerciseGuide?
    ): ExerciseGuideDetail {
        val curatedSteps = bundled?.steps.orEmpty()
        val datasetSteps = dataset?.instructions?.let { ExerciseStepFormatter.toApiFormat(it) }.orEmpty()
        val steps = when {
            curatedSteps.isNotEmpty() -> curatedSteps
            datasetSteps.isNotEmpty() -> datasetSteps
            else -> base.instructions
        }

        val targetMuscles = when {
            bundled?.targetMuscles?.isNotEmpty() == true -> bundled.targetMuscles
            dataset != null -> buildList {
                dataset.target.takeIf { it.isNotBlank() }?.let { add(it) }
                addAll(dataset.secondaryMuscles)
            }.distinct()
            else -> base.targetMuscles
        }

        val equipments = when {
            bundled?.equipments?.isNotEmpty() == true -> bundled.equipments
            dataset?.equipment?.isNotBlank() == true -> listOf(dataset.equipment)
            else -> base.equipments
        }

        val bodyParts = when {
            bundled?.bodyParts?.isNotEmpty() == true -> bundled.bodyParts
            dataset?.bodyPart?.isNotBlank() == true -> listOf(dataset.bodyPart)
            else -> base.bodyParts
        }

        val source = when {
            base.source == GuideSource.AI && steps.isNotEmpty() -> GuideSource.AI
            curatedSteps.isNotEmpty() -> GuideSource.BUNDLED
            dataset != null && steps.isNotEmpty() -> GuideSource.DATASET
            else -> base.source
        }

        return base.copy(
            exerciseId = dataset?.id ?: bundled?.exerciseId ?: base.exerciseId,
            apiName = dataset?.name ?: base.apiName,
            instructions = steps,
            targetMuscles = targetMuscles,
            equipments = equipments,
            bodyParts = bodyParts,
            source = source
        )
    }

    private suspend fun persistGuide(
        lookupKey: String,
        exerciseName: String,
        detail: ExerciseGuideDetail,
        source: String
    ) {
        gymDao.upsertCachedExerciseGuide(
            CachedExerciseGuide(
                lookupKey = lookupKey,
                exerciseId = detail.exerciseId,
                apiName = exerciseName,
                gifUrl = "",
                localGifPath = null,
                instructionsJson = BundledExerciseGuideRepository.stepsToJson(detail.instructions),
                targetMusclesJson = BundledExerciseGuideRepository.listToJson(detail.targetMuscles),
                equipmentsJson = BundledExerciseGuideRepository.listToJson(detail.equipments),
                bodyPartsJson = BundledExerciseGuideRepository.listToJson(detail.bodyParts),
                source = source
            )
        )
    }

    private fun CachedExerciseGuide.toDetail(displayName: String, fromCache: Boolean = true) = ExerciseGuideDetail(
        displayName = displayName,
        apiName = apiName,
        exerciseId = exerciseId,
        instructions = parseJsonArray(instructionsJson),
        targetMuscles = parseJsonArray(targetMusclesJson),
        equipments = parseJsonArray(equipmentsJson),
        bodyParts = parseJsonArray(bodyPartsJson),
        source = when (source) {
            "ai" -> GuideSource.AI
            "dataset" -> GuideSource.DATASET
            else -> GuideSource.BUNDLED
        },
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
