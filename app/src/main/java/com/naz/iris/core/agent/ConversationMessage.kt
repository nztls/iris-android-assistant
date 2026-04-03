package com.naz.iris.core.agent

data class ConversationMessage(
    val role: ConversationRole,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis()
)