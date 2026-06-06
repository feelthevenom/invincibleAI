package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ExerciseDbListResponse(
    val success: Boolean = false,
    val data: List<ExerciseDbExerciseDto>? = null,
    @Json(name = "meta") val meta: ExerciseDbMetaDto? = null
)

@JsonClass(generateAdapter = true)
data class ExerciseDbSingleResponse(
    val success: Boolean = false,
    val data: ExerciseDbExerciseDto? = null
)

@JsonClass(generateAdapter = true)
data class ExerciseDbMetaDto(
    val total: Int? = null,
    val hasNextPage: Boolean? = null,
    val nextCursor: String? = null
)

@JsonClass(generateAdapter = true)
data class ExerciseDbExerciseDto(
    val exerciseId: String,
    val name: String,
    val gifUrl: String,
    val targetMuscles: List<String>? = null,
    val bodyParts: List<String>? = null,
    val equipments: List<String>? = null,
    val secondaryMuscles: List<String>? = null,
    val instructions: List<String>? = null
)

interface ExerciseDbService {
    @GET("api/v1/exercises")
    suspend fun listExercises(
        @Query("limit") limit: Int = 50,
        @Query("cursor") cursor: String? = null
    ): ExerciseDbListResponse

    @GET("api/v1/exercises")
    suspend fun searchByName(
        @Query("name") name: String,
        @Query("limit") limit: Int = 25
    ): ExerciseDbListResponse

    @GET("api/v1/exercises/{exerciseId}")
    suspend fun getById(@Path("exerciseId") exerciseId: String): ExerciseDbSingleResponse
}

class ExerciseDbApiClient {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val service: ExerciseDbService = Retrofit.Builder()
        .baseUrl("https://oss.exercisedb.dev/")
        .client(
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(ExerciseDbService::class.java)

    suspend fun searchByName(name: String, limit: Int = 25): List<ExerciseDbExerciseDto> {
        val response = service.searchByName(name, limit)
        if (!response.success) return emptyList()
        return response.data.orEmpty()
    }

    suspend fun fetchCatalogPage(limit: Int = 50, cursor: String? = null): ExerciseDbListResponse =
        service.listExercises(limit = limit, cursor = cursor)

    suspend fun getById(exerciseId: String): ExerciseDbExerciseDto? {
        val response = service.getById(exerciseId)
        if (!response.success) return null
        return response.data
    }
}
