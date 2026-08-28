package com.xinotes.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = NoteGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("groupId")]
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    // Текст заметки. Картинки обозначены маркером [[img:filename.jpg]] прямо внутри текста,
    // это позволяет сохранять порядок текст/картинка так, как его расположил пользователь.
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val reminderAt: Long? = null,
    val pinned: Boolean = false,
    val groupId: Long? = null,
    // Ручной порядок внутри своей группы (или "Без группы"), меняется перетаскиванием.
    val sortOrder: Int = 0
)
