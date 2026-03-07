package com.naz.iris.data.notes

import kotlinx.serialization.Serializable

@Serializable
data class NoteDto(
    val id: Long,
    val title: String? = null,
    val content: String,
    val createdAt: Long
)