package com.xinotes.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class NoteGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    // Hex-цвет метки группы (например "#8BC34A"), null — без цвета.
    val color: String? = null
)
