package com.naz.iris.core.agent

object ConversationContextBuilder {

    fun buildRecentConversationBlock(
        messages: List<ConversationMessage>,
        maxItems: Int = 8
    ): String {
        if (messages.isEmpty()) {
            return "Önceki konuşma yok."
        }

        return messages
            .takeLast(maxItems)
            .joinToString(separator = "\n") { msg ->
                val roleLabel = when (msg.role) {
                    ConversationRole.USER -> "KULLANICI"
                    ConversationRole.ASSISTANT -> "ASISTAN"
                    ConversationRole.TOOL -> "TOOL"
                }
                "$roleLabel: ${msg.text}"
            }
    }

    fun buildToolResultLine(
        toolName: String,
        rawJson: String
    ): String {
        return "TOOL[$toolName]: $rawJson"
    }
}