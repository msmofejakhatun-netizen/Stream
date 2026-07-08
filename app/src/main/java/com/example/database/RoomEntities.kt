package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val creatorName: String,
    val duration: String,
    val timestamp: Long = System.currentTimeMillis(),
    val progressMs: Long = 0,
    val durationMs: Long = 0
)

@Entity(tableName = "saved_videos")
data class SavedVideoEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val creatorName: String,
    val duration: String,
    val category: String,
    val isWatchLater: Boolean = false,
    val isLiked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "offline_videos")
data class OfflineVideoEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val creatorName: String,
    val duration: String,
    val localFilePath: String,
    val fileSize: String,
    val downloadProgress: Int = 0,
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class LocalPlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isPrivate: Boolean = true,
    val createdDate: String,
    val videoIds: List<String> = emptyList()
)

class Converters {
    private val moshi = Moshi.Builder().build()
    private val listType = Types.newParameterizedType(List::class.java, String::class.java)
    private val adapter = moshi.adapter<List<String>>(listType)

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { adapter.toJson(it) }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let { adapter.fromJson(it) } ?: emptyList()
    }
}
