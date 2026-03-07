package com.naz.iris.core.tools

import com.naz.iris.data.notes.NoteEntity
import com.naz.iris.data.notes.NotesRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class IrisToolDispatcherImpl(
    private val notesRepository: NotesRepository,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : IrisToolDispatcher {

    override suspend fun dispatch(toolName: String, argsJson: String): ToolExecutionResult {
        return when (toolName) {
            "add_note" -> handleAddNote(argsJson)
            "list_recent_notes" -> handleListRecentNotes(argsJson)
            "search_notes" -> handleSearchNotes(argsJson)

            "call_contact" -> ToolExecutionResult.WithLocalFinal(
                toolName = toolName,
                rawJson = """{"ok":false,"reason":"not_implemented"}""",
                localFinalText = "Arama özelliği henüz hazır değil."
            )

            "create_reminder" -> ToolExecutionResult.WithLocalFinal(
                toolName = toolName,
                rawJson = """{"ok":false,"reason":"not_implemented"}""",
                localFinalText = "Hatırlatıcı özelliği henüz hazır değil."
            )

            else -> ToolExecutionResult.WithLocalFinal(
                toolName = toolName,
                rawJson = """{"ok":false,"reason":"unknown_tool"}""",
                localFinalText = "Bu aracı tanımıyorum: $toolName"
            )
        }
    }

    private suspend fun handleAddNote(argsJson: String): ToolExecutionResult {
        val args = runCatching {
            json.decodeFromString(AddNoteArgs.serializer(), argsJson)
        }.getOrElse {
            return ToolExecutionResult.WithLocalFinal(
                toolName = "add_note",
                rawJson = """{"ok":false,"reason":"bad_args"}""",
                localFinalText = "Not ekleme komutunu anlayamadım."
            )
        }

        val content = args.content.trim()
        if (content.isBlank()) {
            return ToolExecutionResult.WithLocalFinal(
                toolName = "add_note",
                rawJson = """{"ok":false,"reason":"empty_content"}""",
                localFinalText = "Boş not ekleyemem."
            )
        }

        val insertedId = notesRepository.add(
            content = content,
            title = args.title?.trim()
        )

        val payload = buildJsonObject {
            put("ok", true)
            put("id", insertedId)
            put("title", args.title?.trim().orEmpty())
            put("contentSnippet", content.toSnippet(100))
        }.toString()

        return ToolExecutionResult.WithLocalFinal(
            toolName = "add_note",
            rawJson = payload,
            localFinalText = if (!args.title.isNullOrBlank()) {
                "Tamam, ${args.title.trim()} başlıklı notu aldım."
            } else {
                "Tamam, notunu aldım."
            }
        )
    }

    private suspend fun handleListRecentNotes(argsJson: String): ToolExecutionResult {
        val args = runCatching {
            json.decodeFromString(ListRecentNotesArgs.serializer(), argsJson)
        }.getOrDefault(ListRecentNotesArgs())

        val spokenLimit = args.limit?.coerceIn(1, 5) ?: 5
        val payloadLimit = args.limit?.coerceIn(1, 10) ?: 5

        val notes: List<NoteEntity> = notesRepository.recent(payloadLimit)

        val payload = buildJsonObject {
            put("ok", true)
            put("count", notes.size)
            put("items", buildJsonArray {
                notes.forEach { note ->
                    add(
                        buildJsonObject {
                            put("id", note.id)
                            put("title", note.title.orEmpty())
                            put("contentSnippet", note.content.toSnippet(100))
                            put("createdAt", note.createdAt)
                        }
                    )
                }
            })
        }.toString()

        val finalText = if (notes.isEmpty()) {
            "Henüz kayıtlı notun yok."
        } else {
            val spoken = notes.take(spokenLimit).mapIndexed { index, note ->
                val body = note.title?.takeIf { it.isNotBlank() }
                    ?: note.content.toSnippet(100)
                "${index + 1}. not: $body"
            }.joinToString(". ")

            "Son ${notes.size} notunu buldum. $spoken."
        }

        return ToolExecutionResult.WithLocalFinal(
            toolName = "list_recent_notes",
            rawJson = payload,
            localFinalText = finalText
        )
    }

    private suspend fun handleSearchNotes(argsJson: String): ToolExecutionResult {
        val args = runCatching {
            json.decodeFromString(SearchNotesArgs.serializer(), argsJson)
        }.getOrElse {
            return ToolExecutionResult.WithLocalFinal(
                toolName = "search_notes",
                rawJson = """{"ok":false,"reason":"bad_args"}""",
                localFinalText = "Not arama komutunu anlayamadım."
            )
        }

        val query = args.query.trim()
        if (query.isBlank()) {
            return ToolExecutionResult.WithLocalFinal(
                toolName = "search_notes",
                rawJson = """{"ok":false,"reason":"empty_query"}""",
                localFinalText = "Aramak için bir ifade söylemelisin."
            )
        }

        val spokenLimit = args.limit?.coerceIn(1, 5) ?: 5
        val payloadLimit = args.limit?.coerceIn(1, 10) ?: 5

        val notes: List<NoteEntity> = notesRepository.search(query)

        val limitedNotes = notes.take(payloadLimit)

        val payload = buildJsonObject {
            put("ok", true)
            put("query", query)
            put("count", limitedNotes.size)
            put("items", buildJsonArray {
                limitedNotes.forEach { note ->
                    add(
                        buildJsonObject {
                            put("id", note.id)
                            put("title", note.title.orEmpty())
                            put("contentSnippet", note.content.toSnippet(100))
                            put("createdAt", note.createdAt)
                        }
                    )
                }
            })
        }.toString()

        val finalText = if (limitedNotes.isEmpty()) {
            "$query ile ilgili not bulamadım."
        } else {
            val spoken = limitedNotes.take(spokenLimit).mapIndexed { index, note ->
                val body = note.title?.takeIf { it.isNotBlank() }
                    ?: note.content.toSnippet(100)
                "${index + 1}. sonuç: $body"
            }.joinToString(". ")

            "$query için ${limitedNotes.size} not buldum. $spoken."
        }

        return ToolExecutionResult.WithLocalFinal(
            toolName = "search_notes",
            rawJson = payload,
            localFinalText = finalText
        )
    }
}

@Serializable
data class AddNoteArgs(
    val title: String? = null,
    val content: String
)

@Serializable
data class SearchNotesArgs(
    val query: String,
    val limit: Int? = 5
)

@Serializable
data class ListRecentNotesArgs(
    val limit: Int? = 5
)

private fun String.toSnippet(maxLen: Int): String {
    val normalized = replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    return if (normalized.length <= maxLen) {
        normalized
    } else {
        normalized.take(maxLen).trimEnd() + "…"
    }
}