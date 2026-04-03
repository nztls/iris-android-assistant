package com.naz.iris.core.tools

import android.content.Context
import com.naz.iris.core.reminder.AndroidReminderScheduler
import com.naz.iris.core.reminder.ReminderRequest
import com.naz.iris.data.notes.NoteEntity
import com.naz.iris.data.notes.NotesRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class IrisToolDispatcherImpl(
    context: Context,
    private val notesRepository: NotesRepository,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : IrisToolDispatcher {

    private val reminderScheduler = AndroidReminderScheduler(context)

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

            "create_reminder" -> handleCreateReminder(argsJson)

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

    private fun handleCreateReminder(argsJson: String): ToolExecutionResult {
        val args = runCatching {
            json.decodeFromString(CreateReminderArgs.serializer(), argsJson)
        }.getOrElse {
            return ToolExecutionResult.WithLocalFinal(
                toolName = "create_reminder",
                rawJson = """{"ok":false,"reason":"bad_args"}""",
                localFinalText = "Hatırlatıcı komutunu anlayamadım."
            )
        }

        if (args.title.isBlank()) {
            return ToolExecutionResult.WithLocalFinal(
                toolName = "create_reminder",
                rawJson = """{"ok":false,"reason":"empty_title"}""",
                localFinalText = "Hatırlatıcı başlığı boş olamaz."
            )
        }

        val now = System.currentTimeMillis()
        val sourceText = args.body.ifBlank { args.title }
        val triggerTime = tryFixRelativeTime(sourceText, now)

        if (triggerTime == null || triggerTime <= now) {
            return ToolExecutionResult.WithLocalFinal(
                toolName = "create_reminder",
                rawJson = """{"ok":false,"reason":"no_time"}""",
                localFinalText = "Zamanı anlayamadım, biraz daha net söyler misin?"
            )
        }

        val reminderId = (100000..999999).random()

        val result = reminderScheduler.schedule(
            ReminderRequest(
                reminderId = reminderId,
                title = args.title.trim(),
                body = args.body.ifBlank { args.title.trim() },
                triggerAtMillis = triggerTime
            )
        )

        val payload = buildJsonObject {
            put("ok", result.success)
            put("reminderId", result.reminderId)
            put("exact", result.exact)
            put("message", result.message)
            put("title", args.title.trim())
            put("triggerAtMillis", triggerTime)
        }.toString()

        val finalText = when {
            !result.success -> "Hatırlatıcı oluşturamadım."
            result.exact -> "Tamam, hatırlatıcıyı ayarladım."
            else -> "Tamam, hatırlatıcıyı ayarladım. Sistem nedeniyle küçük gecikmeler olabilir."
        }

        return ToolExecutionResult.WithLocalFinal(
            toolName = "create_reminder",
            rawJson = payload,
            localFinalText = finalText
        )
    }

    private fun tryFixRelativeTime(
        originalText: String,
        currentMillis: Long
    ): Long? {
        val text = originalText.lowercase()

        val minuteWords = mapOf(
            "bir" to 1,
            "iki" to 2,
            "üç" to 3,
            "dört" to 4,
            "beş" to 5,
            "altı" to 6,
            "yedi" to 7,
            "sekiz" to 8,
            "dokuz" to 9,
            "on" to 10
        )

        val hourWords = minuteWords // aynı mapping

        // 🔹 1) Numeric dakika (5 dakika)
        Regex("(\\d+)\\s*dakika").find(text)?.let {
            val value = it.groupValues[1].toLong()
            return currentMillis + value * 60_000
        }

        // 🔹 2) Yazıyla dakika (iki dakika)
        minuteWords.forEach { (word, value) ->
            if (text.contains("$word dakika")) {
                return currentMillis + value * 60_000
            }
        }

        // 🔹 3) Numeric saat (2 saat)
        Regex("(\\d+)\\s*saat").find(text)?.let {
            val value = it.groupValues[1].toLong()
            return currentMillis + value * 60 * 60_000
        }

        // 🔹 4) Yazıyla saat (iki saat)
        hourWords.forEach { (word, value) ->
            if (text.contains("$word saat")) {
                return currentMillis + value * 60 * 60_000
            }
        }

        // 🔹 5) Yarım saat
        if (text.contains("yarım saat")) {
            return currentMillis + 30 * 60_000
        }

        // 🔹 6) Yarın saat X veya X:Y
        if (text.contains("yarın")) {
            val calendar = java.util.Calendar.getInstance().apply {
                timeInMillis = currentMillis
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }

            // saat: 15 veya 15:30
            val hourMinuteMatch = Regex("(\\d{1,2})(:(\\d{1,2}))?").find(text)

            if (hourMinuteMatch != null) {
                val hour = hourMinuteMatch.groupValues[1].toInt()
                val minute = hourMinuteMatch.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toInt() ?: 0

                calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
                calendar.set(java.util.Calendar.MINUTE, minute)
            } else {
                // default sabah 9
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 9)
                calendar.set(java.util.Calendar.MINUTE, 0)
            }

            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)

            return calendar.timeInMillis
        }

        // 🔹 7) Sabah / akşam
        if (text.contains("sabah")) {
            val calendar = java.util.Calendar.getInstance().apply {
                timeInMillis = currentMillis
                set(java.util.Calendar.HOUR_OF_DAY, 9)
                set(java.util.Calendar.MINUTE, 0)
            }
            return calendar.timeInMillis
        }

        if (text.contains("akşam")) {
            val calendar = java.util.Calendar.getInstance().apply {
                timeInMillis = currentMillis
                set(java.util.Calendar.HOUR_OF_DAY, 18)
                set(java.util.Calendar.MINUTE, 0)
            }
            return calendar.timeInMillis
        }

        return null
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

@Serializable
data class CreateReminderArgs(
    val title: String,
    val body: String = ""
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