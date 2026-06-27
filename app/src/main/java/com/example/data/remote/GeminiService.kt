package com.example.data.remote

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

// --- Data Classes for Moshi ---

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>,
    val role: String? = null
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null,
    val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null
)

// --- Retrofit API Service ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

// --- Service Implementation ---

object GeminiService {
    private const val TAG = "GeminiService"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val api: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    private fun getApiKey(): String {
        return BuildConfig.GEMINI_API_KEY
    }

    /**
     * Auto generate tags for a note based on its title and content.
     * Returns a list of strings (tags).
     */
    suspend fun autoTagNote(title: String, content: String): List<String> {
        val prompt = """
            بصفتك مساعداً ذكياً لتصنيف الملاحظات، قم بتحليل الملاحظة التالية واقترح من 2 إلى 4 وسوم (tags) قصيرة ومناسبة جداً باللغة العربية.
            أرجع الوسوم فقط مفصولة بفاصلة عادية، مثال: "عمل, أفكار, برمجة". لا تكتب أي كلام آخر غير الوسوم.
            
            عنوان الملاحظة: $title
            محتوى الملاحظة: $content
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        return try {
            val response = api.generateContent(getApiKey(), request)
            val resultText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            Log.d(TAG, "AutoTag result: $resultText")
            if (!resultText.isNullOrBlank()) {
                resultText.split(",")
                    .map { it.replace("#", "").trim() }
                    .filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to auto tag", e)
            emptyList()
        }
    }

    /**
     * Summarizes note text.
     */
    suspend fun summarizeNote(title: String, content: String): String {
        val prompt = """
            قم بتلخيص الملاحظة التالية بأسلوب ذكي ومنظم ومختصر باللغة العربية. استخدم نقاطاً واضحة إذا كان ذلك مناسباً.
            
            العنوان: $title
            المحتوى: $content
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        return try {
            val response = api.generateContent(getApiKey(), request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "تعذر توليد ملخص لهذه الملاحظة."
        } catch (e: Exception) {
            Log.e(TAG, "Failed to summarize note", e)
            "حدث خطأ أثناء محاولة تلخيص الملاحظة: ${e.localizedMessage}"
        }
    }

    /**
     * Analyzes an image and generates a note/description.
     */
    suspend fun analyzeImage(bitmap: Bitmap, prompt: String): String {
        val base64Image = bitmap.toBase64()
        val defaultPrompt = "قم بتحليل هذه الصورة واكتب وصفاً مفصلاً ومفيداً لمحتواها باللغة العربية لاستخدامه كملاحظة."
        val finalPrompt = if (prompt.isBlank()) defaultPrompt else prompt

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = finalPrompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            )
        )

        return try {
            val response = api.generateContent(getApiKey(), request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "تعذر تحليل الصورة."
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze image", e)
            "حدث خطأ أثناء محاولة تحليل الصورة: ${e.localizedMessage}"
        }
    }

    // Helper extension
    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
