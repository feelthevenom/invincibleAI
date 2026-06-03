package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/* ---------- Response models ---------- */

@JsonClass(generateAdapter = true)
data class OffSearchResponse(
    val count: Int = 0,
    val page: Int = 1,
    @Json(name = "page_size") val pageSize: Int = 20,
    val products: List<OffProduct> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OffProduct(
    val code: String = "",
    @Json(name = "product_name") val productName: String? = null,
    val brands: String? = null,
    val nutriments: OffNutriments? = null
)

@JsonClass(generateAdapter = true)
data class OffNutriments(
    @Json(name = "energy-kcal_100g") val energyKcal100g: Double? = null,
    @Json(name = "proteins_100g") val proteins100g: Double? = null,
    @Json(name = "carbohydrates_100g") val carbohydrates100g: Double? = null,
    @Json(name = "fat_100g") val fat100g: Double? = null,
    @Json(name = "fiber_100g") val fiber100g: Double? = null
)

/* ---------- Retrofit service ---------- */

interface OpenFoodFactsApi {

    @GET("api/v2/search")
    suspend fun search(
        @Query("search_terms") searchTerms: String,
        @Query("fields") fields: String = FIELDS,
        @Query("page_size") pageSize: Int = 20,
        @Query("page") page: Int = 1,
        @Query("json") json: Int = 1
    ): OffSearchResponse

    companion object {
        private const val BASE_URL = "https://world.openfoodfacts.org/"
        private const val FIELDS = "code,product_name,brands,nutriments"
        private const val USER_AGENT = "GymAI - Android - Version 1.0 - github.com/gym-ai"

        fun create(): OpenFoodFactsApi {
            val userAgentInterceptor = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(userAgentInterceptor)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(OpenFoodFactsApi::class.java)
        }
    }
}
