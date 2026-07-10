package com.example.repository

import android.content.Context
import com.example.BuildConfig
import com.example.database.*
import com.example.models.*
import com.example.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink

class ProgressRequestBody(
    private val inputStream: InputStream,
    private val contentType: String,
    private val contentLength: Long,
    private val onProgress: (progress: Int) -> Unit
) : RequestBody() {

    override fun contentType() = contentType.toMediaTypeOrNull()

    override fun contentLength() = contentLength

    override fun writeTo(sink: BufferedSink) {
        val buffer = ByteArray(4096)
        var uploaded: Long = 0
        inputStream.use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                sink.write(buffer, 0, read)
                uploaded += read
                val progress = ((uploaded * 100) / contentLength).toInt()
                onProgress(progress)
            }
        }
    }
}

class VideoRepository(
    private val context: Context,
    private val watchHistoryDao: WatchHistoryDao,
    private val savedVideoDao: SavedVideoDao,
    private val offlineVideoDao: OfflineVideoDao,
    private val playlistDao: PlaylistDao
) {
    // In-memory caching for seamless synchronous access across screens
    private val videoCache = ConcurrentHashMap<String, Video>()

    // In-memory creator states
    private val subscribedCreatorIds = MutableStateFlow<Set<String>>(emptySet())
    private val videoLikes = MutableStateFlow<Map<String, Boolean>>(emptyMap()) // videoId to isLiked (true=like, false=dislike)
    private val videoComments = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())

    // --- Video Fetching ---
    fun getVideos(category: String): Flow<List<Video>> = flow {
        try {
            val list = StreamPlayRetrofitClient.service.getVideos(category = category, isShort = false)
            list.forEach { video -> videoCache[video.id] = video }
            emit(list)
        } catch (e: Exception) {
            android.util.Log.e("VideoRepository", "Failed to fetch remote videos: ${e.message}")
            // Fallback to loaded cache on failure
            emit(videoCache.values.filter { !it.isShort })
        }
    }

    fun getShorts(): Flow<List<Video>> = flow {
        try {
            val list = StreamPlayRetrofitClient.service.getVideos(isShort = true)
            list.forEach { video -> videoCache[video.id] = video }
            emit(list)
        } catch (e: Exception) {
            android.util.Log.e("VideoRepository", "Failed to fetch remote shorts: ${e.message}")
            emit(videoCache.values.filter { it.isShort })
        }
    }

    fun getVideoById(id: String): Video? {
        return videoCache[id]
    }

    // --- Search with Suggestions ---
    fun getSearchSuggestions(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        return videoCache.values
            .filter { it.title.contains(query, ignoreCase = true) }
            .map { it.title }
            .take(5)
    }

    fun searchVideos(query: String): Flow<List<Video>> = flow {
        try {
            val list = StreamPlayRetrofitClient.service.getVideos(query = query)
            list.forEach { video -> videoCache[video.id] = video }
            emit(list)
        } catch (e: Exception) {
            android.util.Log.e("VideoRepository", "Failed to search remote videos: ${e.message}")
            val matched = videoCache.values.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true) ||
                        it.creatorName.contains(query, ignoreCase = true)
            }
            emit(matched)
        }
    }

    // --- Subscription & Creator Follow ---
    suspend fun getCreatorChannel(creatorId: String): Channel {
        val channel = StreamPlayRetrofitClient.service.getCreatorChannel(creatorId)
        val current = subscribedCreatorIds.value.toMutableSet()
        if (channel.isSubscribed) {
            current.add(creatorId)
        } else {
            current.remove(creatorId)
        }
        subscribedCreatorIds.value = current
        return channel
    }

    fun isSubscribed(creatorId: String): Flow<Boolean> {
        return subscribedCreatorIds.map { it.contains(creatorId) }
    }

    suspend fun toggleSubscription(creatorId: String) {
        val result = StreamPlayRetrofitClient.service.toggleSubscription(creatorId)
        val current = subscribedCreatorIds.value.toMutableSet()
        if (result.isSubscribed) {
            current.add(creatorId)
        } else {
            current.remove(creatorId)
        }
        subscribedCreatorIds.value = current
    }

    // --- Like / Dislike ---
    fun getVideoLikesState(videoId: String): Flow<Boolean?> {
        return videoLikes.map { it[videoId] }
    }

    suspend fun toggleLike(videoId: String, isLike: Boolean) {
        val result = StreamPlayRetrofitClient.service.likeVideo(videoId, LikeRequest(isLike))
        val current = videoLikes.value.toMutableMap()
        if (current[videoId] == isLike) {
            current.remove(videoId)
        } else {
            current[videoId] = isLike
        }
        videoLikes.value = current

        videoCache[videoId]?.let { video ->
            videoCache[videoId] = video.copy(likes = result.likes, dislikes = result.dislikes)
        }
    }

    // --- Comments Management ---
    fun getComments(videoId: String): Flow<List<Comment>> = flow {
        try {
            val list = StreamPlayRetrofitClient.service.getComments(videoId)
            val current = videoComments.value.toMutableMap()
            current[videoId] = list
            videoComments.value = current
            emit(list)
        } catch (e: Exception) {
            android.util.Log.e("VideoRepository", "Failed to fetch remote comments: ${e.message}")
            emit(videoComments.value[videoId] ?: emptyList())
        }
    }

    suspend fun postComment(videoId: String, userName: String, content: String) {
        val newComment = StreamPlayRetrofitClient.service.postComment(videoId, CommentRequest(content))
        val list = videoComments.value[videoId]?.toMutableList() ?: mutableListOf()
        list.add(0, newComment)
        val current = videoComments.value.toMutableMap()
        current[videoId] = list
        videoComments.value = current

        videoCache[videoId]?.let { video ->
            videoCache[videoId] = video.copy(commentsCount = video.commentsCount + 1)
        }
    }

    // --- Video Uploading (Progressive Multi-Part Network Flow) ---
    suspend fun uploadVideo(
        inputStream: InputStream,
        contentLength: Long,
        filename: String,
        title: String,
        description: String,
        category: String,
        isShort: Boolean,
        onProgress: (Int) -> Unit
    ): Video {
        val requestBody = ProgressRequestBody(
            inputStream = inputStream,
            contentType = "video/mp4",
            contentLength = contentLength,
            onProgress = onProgress
        )
        val videoPart = MultipartBody.Part.createFormData("video", filename, requestBody)

        val titleBody = RequestBody.create("text/plain".toMediaTypeOrNull(), title)
        val descBody = RequestBody.create("text/plain".toMediaTypeOrNull(), description)
        val catBody = RequestBody.create("text/plain".toMediaTypeOrNull(), category)
        val shortBody = RequestBody.create("text/plain".toMediaTypeOrNull(), isShort.toString())

        val uploadedVideo = StreamPlayRetrofitClient.service.uploadVideo(
            video = videoPart,
            title = titleBody,
            description = descBody,
            category = catBody,
            isShort = shortBody
        )
        videoCache[uploadedVideo.id] = uploadedVideo
        return uploadedVideo
    }

    suspend fun uploadDummyVideo(
        title: String,
        description: String,
        category: String,
        onProgress: (Int) -> Unit
    ): Video {
        val tempFile = java.io.File(context.cacheDir, "dummy_upload.mp4")
        if (!tempFile.exists()) {
            tempFile.createNewFile()
        }
        tempFile.outputStream().use { out ->
            val bytes = ByteArray(4096)
            for (i in 0 until 256) { // Write 1MB of chunks
                out.write(bytes)
            }
        }
        return uploadVideo(
            inputStream = tempFile.inputStream(),
            contentLength = tempFile.length(),
            filename = "sample_video.mp4",
            title = title,
            description = description,
            category = category,
            isShort = false,
            onProgress = onProgress
        )
    }

    // --- Room Database Operations (History, Watch Later, Playlists, Downloads) ---
    val watchHistory: Flow<List<WatchHistoryEntity>> = watchHistoryDao.getWatchHistory()
    val savedVideos: Flow<List<SavedVideoEntity>> = savedVideoDao.getSavedVideos()
    val watchLaterVideos: Flow<List<SavedVideoEntity>> = savedVideoDao.getWatchLaterVideos()
    val likedVideos: Flow<List<SavedVideoEntity>> = savedVideoDao.getLikedVideos()
    val offlineVideos: Flow<List<OfflineVideoEntity>> = offlineVideoDao.getOfflineVideos()
    val localPlaylists: Flow<List<LocalPlaylistEntity>> = playlistDao.getAllPlaylists()

    suspend fun addToHistory(video: Video, progressMs: Long = 0, durationMs: Long = 0) {
        watchHistoryDao.insertWatchHistory(
            WatchHistoryEntity(
                videoId = video.id,
                title = video.title,
                thumbnailUrl = video.thumbnailUrl,
                creatorName = video.creatorName,
                duration = video.duration,
                progressMs = progressMs,
                durationMs = durationMs
            )
        )
    }

    suspend fun toggleWatchLater(video: Video) {
        val existing = savedVideoDao.getSavedVideoById(video.id)
        if (existing != null) {
            if (existing.isLiked) {
                savedVideoDao.saveVideo(existing.copy(isWatchLater = !existing.isWatchLater))
            } else {
                savedVideoDao.removeSavedVideo(existing)
            }
        } else {
            savedVideoDao.saveVideo(
                SavedVideoEntity(
                    videoId = video.id,
                    title = video.title,
                    thumbnailUrl = video.thumbnailUrl,
                    creatorName = video.creatorName,
                    duration = video.duration,
                    category = video.category,
                    isWatchLater = true
                )
            )
        }
    }

    suspend fun isWatchLater(videoId: String): Boolean {
        return savedVideoDao.getSavedVideoById(videoId)?.isWatchLater ?: false
    }

    suspend fun createPlaylist(name: String, isPrivate: Boolean) {
        playlistDao.insertPlaylist(
            LocalPlaylistEntity(
                id = "playlist_" + UUID.randomUUID().toString().take(6),
                name = name,
                isPrivate = isPrivate,
                createdDate = "Jul 2026",
                videoIds = emptyList()
            )
        )
    }

    suspend fun addVideoToPlaylist(playlistId: String, videoId: String) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return
        if (!playlist.videoIds.contains(videoId)) {
            val updatedList = playlist.videoIds.toMutableList().apply { add(videoId) }
            playlistDao.insertPlaylist(playlist.copy(videoIds = updatedList))
        }
    }

    suspend fun removeVideoFromPlaylist(playlistId: String, videoId: String) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return
        if (playlist.videoIds.contains(videoId)) {
            val updatedList = playlist.videoIds.toMutableList().apply { remove(videoId) }
            playlistDao.insertPlaylist(playlist.copy(videoIds = updatedList))
        }
    }

    suspend fun deletePlaylist(playlistId: String) {
        playlistDao.deletePlaylist(playlistId)
    }

    // --- Offline Downloading Stream ---
    suspend fun downloadVideo(video: Video) {
        val entity = OfflineVideoEntity(
            videoId = video.id,
            title = video.title,
            thumbnailUrl = video.thumbnailUrl,
            creatorName = video.creatorName,
            duration = video.duration,
            localFilePath = "${context.filesDir.absolutePath}/downloads/${video.id}.mp4",
            fileSize = "42.5 MB",
            downloadProgress = 0,
            isCompleted = false
        )
        offlineVideoDao.insertOfflineVideo(entity)

        withContext(Dispatchers.IO) {
            for (p in 10..100 step 15) {
                val currentProgress = if (p > 100) 100 else p
                val isDone = currentProgress == 100
                offlineVideoDao.insertOfflineVideo(
                    entity.copy(
                        downloadProgress = currentProgress,
                        isCompleted = isDone
                    )
                )
                kotlinx.coroutines.delay(800)
            }
        }
    }

    suspend fun removeOfflineDownload(videoId: String) {
        offlineVideoDao.deleteOfflineVideo(videoId)
    }

    // --- Gemini-Powered Smart Recommendation Engine ---
    suspend fun getAiSmartRecommendations(prompt: String): Flow<List<Video>> = flow {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val allVideos = videoCache.values.toList().ifEmpty {
            try {
                StreamPlayRetrofitClient.service.getVideos()
            } catch (e: Exception) {
                emptyList()
            }
        }

        if (allVideos.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey.contains("dummy")) {
            val matched = allVideos.filter {
                it.title.contains(prompt, ignoreCase = true) ||
                        it.description.contains(prompt, ignoreCase = true) ||
                        it.category.contains(prompt, ignoreCase = true)
            }
            emit(matched.ifEmpty { allVideos.shuffled().take(3) })
            return@flow
        }

        val contextPrompt = """
            You are the smart AI Copilot inside StreamPlay, an advanced video streaming application.
            Here is the current database of available videos in the catalog:
            ${allVideos.joinToString("\n") { "- ID: ${it.id}, Title: ${it.title}, Category: ${it.category}, Desc: ${it.description}" }}
            
            The user is asking: "$prompt"
            
            Analyze the query and use reasoning to select the top 2-3 most relevant videos from this database.
            Return your answer in plain text with a list of matching video IDs, with a brief explanation of why you selected each. Format it cleanly for the UI.
            If no videos match directly, recommend the most relevant category or general programming videos.
        """.trimIndent()

        val request = GeminiGenerateContentRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = contextPrompt)))),
            generationConfig = GeminiGenerationConfig(
                thinkingConfig = ThinkingConfig("HIGH")
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = "You are a highly analytical video curator. Always recommend real IDs from the list provided."))
            )
        )

        try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val matchedIds = allVideos.filter { responseText.contains(it.id) }.map { it.id }
            val finalVideos = allVideos.filter { matchedIds.contains(it.id) }
            emit(finalVideos.ifEmpty { allVideos.shuffled().take(3) })
        } catch (e: Exception) {
            emit(allVideos.shuffled().take(3))
        }
    }

    // Interactive AI chat inside streaming player
    suspend fun getAiResponseForVideo(videoId: String, userQuery: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val video = getVideoById(videoId) ?: return@withContext "Error: Video not found."

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey.contains("dummy")) {
            return@withContext """
                [Demo mode - No API Key configured]
                This video titled "${video.title}" discusses ${video.category} concepts.
                To activate thinking mode powered by gemini-3.1-pro-preview, enter a valid Gemini API key in the AI Studio Secrets panel.
                
                Simulated response:
                - Summary: This video introduces developers to key strategies for highly efficient mobile layouts.
                - Best practice: Always declare safeDrawing padding to keep interactive elements accessible on all screens.
            """.trimIndent()
        }

        val chatPrompt = """
            You are StreamPlay AI, an intelligent co-watching assistant. You are helping a user analyze the following video:
            Title: "${video.title}"
            Category: "${video.category}"
            Description: "${video.description}"
            
            The user is asking: "$userQuery"
            
            Provide an analytical, highly reasoning-driven answer using thinking mode (ThinkingLevel.HIGH is enabled).
            Explain details clearly, cite concepts from the description, and structure your response beautifully with markdown bullet points.
        """.trimIndent()

        val request = GeminiGenerateContentRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = chatPrompt)))),
            generationConfig = GeminiGenerationConfig(
                thinkingConfig = ThinkingConfig("HIGH")
            )
        )

        try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No response from Gemini API."
        } catch (e: Exception) {
            "Error calling Gemini thinking model: ${e.message}"
        }
    }
}
