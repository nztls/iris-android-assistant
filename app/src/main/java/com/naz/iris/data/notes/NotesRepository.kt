package com.naz.iris.data.notes

class NotesRepository(private val dao: NotesDao) {

    suspend fun add(content: String, title: String?): Long {
        val note = NoteEntity(
            title = title?.takeIf { it.isNotBlank() },
            content = content,
            createdAt = System.currentTimeMillis()
        )
        return dao.insert(note)
    }

    suspend fun search(query: String): List<NoteEntity> {
        return dao.search(query.trim())
    }

    suspend fun recent(limit: Int): List<NoteEntity> {
        val safe = limit.coerceIn(1, 50)
        return dao.listRecent(safe)
    }
}