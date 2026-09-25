package com.example.data.remote

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object GeminiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Model name as specified in the user request
    private const val MODEL_NAME = "gemini-2.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private const val SYSTEM_INSTRUCTION = """
You are an executive Senior AI Business Consultant and Strategy Advisor. 
Provide clear, actionable, structured, and insightful business recommendations.
Structure your answers with:
1. Executive Summary / Direct Answer
2. Strategic Recommendations (Numbered & Actionable)
3. Financial / Operational Considerations
4. Next Steps
Keep advice professional, realistic, and tailored to the business context provided.
"""

    fun getResolvedApiKey(userCustomKey: String?): String {
        val trimmedCustom = userCustomKey?.trim() ?: ""
        if (trimmedCustom.isNotBlank() && !trimmedCustom.equals("YOUR_API_KEY_HERE", ignoreCase = true)) {
            return trimmedCustom
        }
        val buildKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }
        if (buildKey.isNotBlank() && !buildKey.contains("MY_GEMINI_API_KEY") && !buildKey.contains("PLACEHOLDER")) {
            return buildKey
        }
        return ""
    }

    suspend fun generateTextResponse(
        prompt: String,
        businessContext: String? = null,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Gemini API key is not configured. Please enter your API key in the top banner or AI Studio Secrets.")
                )
            }

            val url = "$BASE_URL/$MODEL_NAME:generateContent?key=$apiKey"

            val fullPrompt = if (!businessContext.isNullOrBlank()) {
                "Client Business Context:\n$businessContext\n\nClient Question:\n$prompt"
            } else {
                prompt
            }

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().put("text", fullPrompt))
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)

                val sysInstructionObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().put("text", SYSTEM_INSTRUCTION))
                    }
                    put("parts", parts)
                }
                put("systemInstruction", sysInstructionObj)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val errObj = JSONObject(bodyStr).getJSONObject("error")
                        errObj.optString("message", "API Error HTTP ${response.code}")
                    } catch (e: Exception) {
                        "API Request Failed: HTTP ${response.code} ($bodyStr)"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val responseJson = JSONObject(bodyStr)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val text = parts.getJSONObject(0).optString("text")
                        return@withContext Result.success(text)
                    }
                }
                Result.failure(Exception("No content returned from Gemini model."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeImage(
        bitmap: Bitmap,
        userPrompt: String,
        businessContext: String? = null,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Gemini API key is not configured. Please enter your API key in the top banner or AI Studio Secrets.")
                )
            }

            val url = "$BASE_URL/$MODEL_NAME:generateContent?key=$apiKey"

            // Convert bitmap to Base64 JPEG
            val outputStream = ByteArrayOutputStream()
            // Resize if too large to prevent out-of-memory or high payload
            val scaledBitmap = scaleBitmapIfNeeded(bitmap, 1280)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val base64Data = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            val promptText = buildString {
                append("Please inspect and analyze this business-related image (e.g. shop, product, chart, invoice, storefront, or marketing asset).\n")
                if (!businessContext.isNullOrBlank()) {
                    append("Business Profile Context:\n$businessContext\n\n")
                }
                if (userPrompt.isNotBlank()) {
                    append("Specific Question/Instruction from user: $userPrompt\n")
                } else {
                    append("Provide an executive breakdown:\n1. Identify what is depicted\n2. Key Business Insights & Observations\n3. Strategic Recommendations & Opportunities\n4. Potential Risks or Actionable Improvements\n")
                }
            }

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().put("text", promptText))
                            put(JSONObject().apply {
                                val inlineData = JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Data)
                                }
                                put("inlineData", inlineData)
                            })
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)

                val sysInstructionObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().put("text", SYSTEM_INSTRUCTION))
                    }
                    put("parts", parts)
                }
                put("systemInstruction", sysInstructionObj)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val errObj = JSONObject(bodyStr).getJSONObject("error")
                        errObj.optString("message", "API Error HTTP ${response.code}")
                    } catch (e: Exception) {
                        "API Request Failed: HTTP ${response.code} ($bodyStr)"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val responseJson = JSONObject(bodyStr)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val text = parts.getJSONObject(0).optString("text")
                        return@withContext Result.success(text)
                    }
                }
                Result.failure(Exception("No content returned from Gemini model."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (width > height) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / ratio).toInt()
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
