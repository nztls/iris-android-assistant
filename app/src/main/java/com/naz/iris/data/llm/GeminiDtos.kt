package com.naz.iris.data.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    @SerialName("system_instruction") val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val role: String? = null, // "user" / "model"
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(val text: String)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double? = 0.2,
    val maxOutputTokens: Int? = 512
)

@Serializable
data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate> = emptyList()
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null
)