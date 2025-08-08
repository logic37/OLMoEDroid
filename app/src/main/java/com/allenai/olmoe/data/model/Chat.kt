package com.allenai.olmoe.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import android.os.Parcelable

@Entity(tableName = "chat_messages")
@Parcelize
data class Chat(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable

enum class Role {
    USER,
    BOT
} 