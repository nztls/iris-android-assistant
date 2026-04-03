package com.naz.iris.core.agent

import com.naz.iris.core.tools.IrisToolDispatcher
import com.naz.iris.core.tools.ToolExecutionResult
import com.naz.iris.data.llm.GeminiClient
import com.naz.iris.data.llm.GeminiResult
import kotlinx.serialization.json.Json

class IrisAgentOrchestrator(
    private val geminiClient: GeminiClient,
    private val toolDispatcher: IrisToolDispatcher,
    private val memory: ConversationMemory,
    private val json: Json = Json { prettyPrint = false }
) {

    fun clearMemory() {
        memory.clear()
    }
    suspend fun processUserInput(userText: String): String {
        val cleanUserText = userText.trim()
        if (cleanUserText.isBlank()) {
            return "Seni duyamadım, tekrar söyler misin?"
        }

        memory.add(ConversationRole.USER, cleanUserText)

        val recentContext = ConversationContextBuilder.buildRecentConversationBlock(
            messages = memory.recent(),
            maxItems = 8
        )

        val firstTurnResult = geminiClient.generateRaw(
            systemPrompt = IrisPrompts.SYSTEM_PROMPT,
            userMessage = IrisPrompts.buildFirstTurnUserMessage(
                userText = cleanUserText,
                recentConversationBlock = recentContext
            )
        )

        val finalText = when (firstTurnResult) {
            is GeminiResult.Success -> {
                handleFirstTurnSuccess(
                    originalUserText = cleanUserText,
                    rawModelText = firstTurnResult.rawText
                )
            }

            is GeminiResult.Error -> {
                mapGeminiErrorToSafeText(firstTurnResult)
            }
        }

        memory.add(ConversationRole.ASSISTANT, finalText)
        return finalText
    }

    private suspend fun handleFirstTurnSuccess(
        originalUserText: String,
        rawModelText: String
    ): String {
        return when (val parsed = IrisResponseParser.parse(rawModelText)) {
            is IrisParsed.Final -> {
                parsed.text
            }

            is IrisParsed.CallTool -> {
                val argsJson = parsed.args.toString()
                val toolResult = toolDispatcher.dispatch(
                    toolName = parsed.name,
                    argsJson = argsJson
                )

                memory.add(
                    ConversationRole.TOOL,
                    ConversationContextBuilder.buildToolResultLine(
                        toolName = toolResult.toolName,
                        rawJson = toolResult.rawJson
                    )
                )

                when (toolResult) {
                    is ToolExecutionResult.WithLocalFinal -> {
                        toolResult.localFinalText
                    }

                    is ToolExecutionResult.RequiresSecondTurn -> {
                        runSecondTurn(
                            originalUserText = originalUserText,
                            toolName = toolResult.toolName,
                            toolRawJson = toolResult.rawJson
                        )
                    }
                }
            }

            is IrisParsed.Invalid -> {
                parsed.safeText
            }
        }
    }

    private suspend fun runSecondTurn(
        originalUserText: String,
        toolName: String,
        toolRawJson: String
    ): String {
        val recentContext = ConversationContextBuilder.buildRecentConversationBlock(
            messages = memory.recent(),
            maxItems = 8
        )

        return when (
            val secondTurnResult = geminiClient.generateRaw(
                systemPrompt = IrisPrompts.SYSTEM_PROMPT,
                userMessage = IrisPrompts.buildSecondTurnUserMessage(
                    originalUserText = originalUserText,
                    toolName = toolName,
                    toolRawJson = toolRawJson,
                    recentConversationBlock = recentContext
                )
            )
        ) {
            is GeminiResult.Success -> {
                when (val parsed = IrisResponseParser.parse(secondTurnResult.rawText)) {
                    is IrisParsed.Final -> parsed.text
                    is IrisParsed.CallTool -> "İsteği işledim ama beklenmeyen ikinci araç çağrısı geldi."
                    is IrisParsed.Invalid -> parsed.safeText
                }
            }

            is GeminiResult.Error -> {
                mapGeminiErrorToSafeText(secondTurnResult)
            }
        }
    }

    private fun mapGeminiErrorToSafeText(error: GeminiResult.Error): String {
        return when (error.kind) {
            GeminiResult.Kind.NO_KEY -> "Gemini API anahtarı bulunamadı."
            GeminiResult.Kind.NETWORK -> "İnternet bağlantısında bir sorun var gibi görünüyor."
            GeminiResult.Kind.TIMEOUT -> "Yanıt biraz uzun sürdü. Lütfen tekrar dener misin?"
            GeminiResult.Kind.HTTP -> "Sunucu tarafında bir hata oluştu."
            GeminiResult.Kind.PARSE -> "Yanıtı işlerken bir sorun oluştu."
            GeminiResult.Kind.UNKNOWN -> "Beklenmeyen bir hata oluştu."
        }
    }
}