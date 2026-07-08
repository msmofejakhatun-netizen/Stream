package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC")
    fun getWatchHistory(): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchHistory(history: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE videoId = :videoId")
    suspend fun deleteFromHistory(videoId: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearHistory()
}

@Dao
interface SavedVideoDao {
    @Query("SELECT * FROM saved_videos ORDER BY timestamp DESC")
    fun getSavedVideos(): Flow<List<SavedVideoEntity>>

    @Query("SELECT * FROM saved_videos WHERE isWatchLater = 1 ORDER BY timestamp DESC")
    fun getWatchLaterVideos(): Flow<List<SavedVideoEntity>>

    @Query("SELECT * FROM saved_videos WHERE isLiked = 1 ORDER BY timestamp DESC")
    fun getLikedVideos(): Flow<List<SavedVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveVideo(video: SavedVideoEntity)

    @Query("SELECT * FROM saved_videos WHERE videoId = :videoId LIMIT 1")
    suspend fun getSavedVideoById(videoId: String): SavedVideoEntity?

    @Delete
    suspend fun removeSavedVideo(video: SavedVideoEntity)

    @Query("DELETE FROM saved_videos WHERE videoId = :videoId")
    suspend fun deleteSavedVideoById(videoId: String)
}

@Dao
interface OfflineVideoDao {
    @Query("SELECT * FROM offline_videos ORDER BY timestamp DESC")
    fun getOfflineVideos(): Flow<List<OfflineVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOfflineVideo(video: OfflineVideoEntity)

    @Query("SELECT * FROM offline_videos WHERE videoId = :videoId LIMIT 1")
    suspend fun getOfflineVideoById(videoId: String): OfflineVideoEntity?

    @Query("DELETE FROM offline_videos WHERE videoId = :videoId")
    suspend fun deleteOfflineVideo(videoId: String)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY id DESC")
    fun getAllPlaylists(): Flow<List<LocalPlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: String): LocalPlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: LocalPlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)
}
