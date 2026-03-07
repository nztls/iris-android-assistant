package com.naz.iris.data.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class GeminiResult {
    data class Success(val rawText: String) : GeminiResult()
    data class Error(val kind: Kind, val message: String) : GeminiResult()

    enum class Kind { NO_KEY, NETWORK, TIMEOUT, HTTP, PARSE, UNKNOWN }
}

class GeminiClient(
    private val apiKeyProvider: () -> String?,
    private val model: String = GeminiModels.DEFAULT_MODEL
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    suspend fun generateRaw(
        systemPrompt: String,
        userMessage: String
    ): GeminiResult = withContext(Dispatchers.IO) {
        val key = apiKeyProvider()?.trim()
        if (key.isNullOrEmpty()) {
            return@withContext GeminiResult.Error(GeminiResult.Kind.NO_KEY, "API key yok")
        }

        val endpoint =
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"

        val bodyObj = GeminiGenerateRequest(
            systemInstruction = GeminiContent(
                role = "system",
                parts = listOf(GeminiPart(systemPrompt))
            ),
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(userMessage))
                )
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.2,
                maxOutputTokens = 512
            )
        )

        val bodyStr = json.encodeToString(GeminiGenerateRequest.serializer(), bodyObj)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val req = Request.Builder()
            .url(endpoint)
            .post(bodyStr.toRequestBody(mediaType))
            .build()

        try {
            http.newCall(req).execute().use { resp ->
                val code = resp.code
                val respBody = resp.body?.string().orEmpty()

                if (!resp.isSuccessful) {
                    return@withContext GeminiResult.Error(
                        GeminiResult.Kind.HTTP,
                        "HTTP $code: ${respBody.take(300)}"
                    )
                }

                val parsed = try {
                    json.decodeFromString(GeminiGenerateResponse.serializer(), respBody)
                } catch (e: Exception) {
                    return@withContext GeminiResult.Error(
                        GeminiResult.Kind.PARSE,
                        "Response parse edilemedi: ${e.message}"
                    )
                }

                val text = parsed.candidates
                    .firstOrNull()
                    ?.content
                    ?.parts
                    ?.joinToString(separator = "") { it.text }
                    ?.trim()
                    .orEmpty()

                if (text.isBlank()) {
                    return@withContext GeminiResult.Error(
                        GeminiResult.Kind.PARSE,
                        "Boş cevap döndü"
                    )
                }

                GeminiResult.Success(text)
            }
        } catch (e: java.net.SocketTimeoutException) {
            GeminiResult.Error(GeminiResult.Kind.TIMEOUT, "Timeout: ${e.message}")
        } catch (e: IOException) {
            GeminiResult.Error(GeminiResult.Kind.NETWORK, "Network: ${e.message}")
        } catch (e: Exception) {
            GeminiResult.Error(GeminiResult.Kind.UNKNOWN, "Unknown: ${e.message}")
        }
    }
}