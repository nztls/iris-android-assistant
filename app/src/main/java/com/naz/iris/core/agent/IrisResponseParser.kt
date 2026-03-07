package com.naz.iris.core.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

sealed class IrisParsed {
    data class Final(val text: String) : IrisParsed()
    data class CallTool(val name: String, val args: JsonElement) : IrisParsed()
    data class Invalid(val reason: String, val safeText: String) : IrisParsed()
}

object IrisResponseParser {

    private val json = Json { ignoreUnknownKeys = true }

    private val finalRegex =
        Regex("""<<FINAL>>\s*(.*?)\s*<<END_FINAL>>""", RegexOption.DOT_MATCHES_ALL)

    private val toolRegex =
        Regex(
            """<<CALL_TOOL>>\s*name:\s*([a-zA-Z0-9_]+)\s*args:\s*(\{.*?\})\s*<<END_TOOL>>""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )

    fun parse(raw: String): IrisParsed {
        val trimmed = raw.trim()

        finalRegex.find(trimmed)?.let { m ->
            val text = m.groupValues[1].trim()
            return if (text.isNotBlank()) IrisParsed.Final(text)
            else IrisParsed.Invalid("FINAL boş", safeText = "Bir şey diyemedim, tekrarlar mısın?")
        }

        toolRegex.find(trimmed)?.let { m ->
            val name = m.groupValues[1].trim()
            val argsStr = m.groupValues[2].trim()

            val args = try {
                json.parseToJsonElement(argsStr)
            } catch (e: Exception) {
                return IrisParsed.Invalid("Args JSON parse hatası: ${e.message}", safeText = "İsteği anlayamadım, tekrarlar mısın?")
            }

            // args mutlaka object olsun
            if (args !is kotlinx.serialization.json.JsonObject) {
                return IrisParsed.Invalid("Args object değil", safeText = "İsteği anlayamadım, tekrarlar mısın?")
            }

            return IrisParsed.CallTool(name, args)
        }

        // Format dışına çıktı: güvenli fallback → chat gibi davran
        // (Kuralın: “format bozulursa chat gibi davran / hata mesajı”)
        val safe = trimmed.take(300).ifBlank { "Bir şey diyemedim, tekrarlar mısın?" }
        return IrisParsed.Invalid("Format dışı çıktı", safeText = safe)
    }
}