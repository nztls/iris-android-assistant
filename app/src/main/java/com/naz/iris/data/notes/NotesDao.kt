package com.naz.iris.data.notes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NotesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Query(
        """
        SELECT * FROM notes
        WHERE title LIKE '%' || :query || '%'
           OR content LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
        """
    )
    suspend fun search(query: String): List<NoteEntity>

    @Query(
        """
        SELECT * FROM notes
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun listRecent(limit: Int): List<NoteEntity>
}