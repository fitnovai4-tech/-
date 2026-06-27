package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val tags: String = "", // Comma-separated tags
    val imagePath: String? = null, // Local storage file path for attached image
    val pdfPath: String? = null,   // Local storage file path for imported PDF
    val drawingPath: String? = null, // Local storage file path for drawing image
    val voicePath: String? = null,   // Local storage file path for voice recording
    val voiceDuration: Long = 0,     // Voice note duration in milliseconds
    val reminderTime: Long? = null,  // Alarm reminder epoch milliseconds
    val isFavorite: Boolean = false,
    val isLocked: Boolean = false,
    val notePassword: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
