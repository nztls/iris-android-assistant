package com.naz.iris.core.agent

class ConversationMemory(
    private val maxMessages: Int = 12,
    private val maxCharsPerMessage: Int = 240
) {
    private val items = mutableListOf<ConversationMessage>()

    @Synchronized
    fun add(role: ConversationRole, text: String) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return

        val normalized = if (cleaned.length > maxCharsPerMessage) {
            cleaned.take(maxCharsPerMessage).trimEnd() + "…"
        } else {
            cleaned
        }

        items += ConversationMessage(
            role = role,
            text = normalized
        )

        trimToLimit()
    }

    @Synchronized
    fun recent(limit: Int = maxMessages): List<ConversationMessage> {
        return items.takeLast(limit)
    }

    @Synchronized
    fun all(): List<ConversationMessage> = items.toList()

    @Synchronized
    fun clear() {
        items.clear()
    }

    @Synchronized
    private fun trimToLimit() {
        val overflow = items.size - maxMessages
        repeat(overflow.coerceAtLeast(0)) {
            items.removeAt(0)
        }
    }
}