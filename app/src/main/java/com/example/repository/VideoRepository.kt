package com.example.repository

import android.content.Context
import com.example.BuildConfig
import com.example.database.*
import com.example.models.*
import com.example.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.util.UUID

class VideoRepository(
    private val context: Context,
    private val watchHistoryDao: WatchHistoryDao,
    private val savedVideoDao: SavedVideoDao,
    private val offlineVideoDao: OfflineVideoDao,
    private val playlistDao: PlaylistDao
) {
    // Highly realistic production-grade mock datasets
    private val sampleVideos = listOf(
        Video(
            id = "vid_1",
            title = "Introduction to Jetpack Compose: Building Beautiful Interfaces",
            description = "Learn how to build modern Android UIs with Jetpack Compose. This video covers layouts, state management, modifiers, and standard material design 3 UI components with best practices.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            thumbnailUrl = "https://images.unsplash.com/photo-1607799279861-4dd421887fb3?auto=format&fit=crop&w=640&q=80",
            creatorId = "creator_android_dev",
            creatorName = "Android Developer Academy",
            creatorAvatar = "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&w=120&q=80",
            views = 124500,
            likes = 8900,
            dislikes = 120,
            duration = "09:56",
            uploadDate = "2 days ago",
            category = "Education",
            commentsCount = 234
        ),
        Video(
            id = "vid_2",
            title = "Top 10 High-Performance Rust Frameworks in 2026",
            description = "Explore the cutting-edge of system development with the top Rust web and desktop frameworks. We measure request latency, memory footprint, and compile times in high-concurrency environments.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            thumbnailUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?auto=format&fit=crop&w=640&q=80",
            creatorId = "creator_rustacean",
            creatorName = "The Coding Sanctuary",
            creatorAvatar = "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?auto=format&fit=crop&w=120&q=80",
            views = 85900,
            likes = 4120,
            dislikes = 45,
            duration = "15:30",
            uploadDate = "5 days ago",
            category = "Education",
            commentsCount = 112
        ),
        Video(
            id = "vid_3",
            title = "Epic Live Chillstep Session for Late-Night Coding 🎧",
            description = "Sit back, focus, and stream these high-quality lofi and chillstep beats designed specifically for deep flow state programming sessions. Active 24/7 with a welcoming chat community.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            thumbnailUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=640&q=80",
            creatorId = "creator_lofi_beats",
            creatorName = "Sonic Horizon",
            creatorAvatar = "https://images.unsplash.com/photo-1487412720507-e7ab37603c6f?auto=format&fit=crop&w=120&q=80",
            views = 450000,
            likes = 34500,
            dislikes = 80,
            duration = "24:00",
            uploadDate = "Live",
            category = "Music",
            isLive = true,
            commentsCount = 1200
        ),
        Video(
            id = "vid_4",
            title = "Unboxing & First Impressions: The Foldable Tablet of Tomorrow",
            description = "We finally got our hands on the ultimate foldable display device of the decade! Check out the flexible OLED panel, processing speeds, multi-window performance, and custom software overlay.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            thumbnailUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=640&q=80",
            creatorId = "creator_tech_radar",
            creatorName = "TechPulse",
            creatorAvatar = "https://images.unsplash.com/photo-1531427186611-ecfd6d936c79?auto=format&fit=crop&w=120&q=80",
            views = 98000,
            likes = 5400,
            dislikes = 230,
            duration = "12:15",
            uploadDate = "1 week ago",
            category = "Latest",
            commentsCount = 450
        ),
        Video(
            id = "vid_5",
            title = "Global Market Update: Tech Industry Trends in Q3 2026",
            description = "A deep dive financial analysis of market caps, computing chip shortages, and startup investments driving the tech economy this quarter. Hosted by senior market analysts.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
            thumbnailUrl = "https://images.unsplash.com/photo-1590283603385-17ffb3a7f29f?auto=format&fit=crop&w=640&q=80",
            creatorId = "creator_finance_times",
            creatorName = "Finance Insights",
            creatorAvatar = "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&w=120&q=80",
            views = 34200,
            likes = 1200,
            dislikes = 12,
            duration = "08:45",
            uploadDate = "Yesterday",
            category = "News",
            commentsCount = 95
        ),
        Video(
            id = "vid_6",
            title = "Spaceflight Launch Event: Mission to Jupiter's Moons",
            description = "Join us live for the historic deep space probe launch event. This mission plans to orbit Europa and analyze underwater hydrothermal activity over the next seven years.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
            thumbnailUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=640&q=80",
            creatorId = "creator_nasa_space",
            creatorName = "AstroVenture",
            creatorAvatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=120&q=80",
            views = 1204000,
            likes = 112000,
            dislikes = 450,
            duration = "01:45:00",
            uploadDate = "Live",
            category = "Live",
            isLive = true,
            commentsCount = 3800
        ),
        Video(
            id = "vid_7",
            title = "Speedrunning Elden Ring with a Custom Dance Pad Controller",
            description = "Yes, you read that right. Attempting to beat the ultimate fantasy action RPG boss roster using a customized dance mat inputs. Absolute chaos from start to finish!",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            thumbnailUrl = "https://images.unsplash.com/photo-1538481199705-c710c4e965fc?auto=format&fit=crop&w=640&q=80",
            creatorId = "creator_gamer_hq",
            creatorName = "Retro Gaming Lab",
            creatorAvatar = "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?auto=format&fit=crop&w=120&q=80",
            views = 230400,
            likes = 18900,
            dislikes = 90,
            duration = "22:40",
            uploadDate = "3 days ago",
            category = "Gaming",
            commentsCount = 612
        ),
        Video(
            id = "vid_shorts_1",
            title = "How does database indexing work in 60 seconds?",
            description = "The absolute fastest way to understand B-Trees, lookup complexity, and why indexing makes select queries lightning fast.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
            thumbnailUrl = "https://images.unsplash.com/photo-1544383835-bda2bc66a55d?auto=format&fit=crop&w=640&q=80",
            creatorId = "creator_android_dev",
            creatorName = "Android Developer Academy",
            creatorAvatar = "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&w=120&q=80",
            views = 890300,
            likes = 78300,
            dislikes = 400,
            duration = "00:58",
            uploadDate = "4 days ago",
            category = "Education",
            isShort = true,
            commentsCount = 920
        ),
        Video(
            id = "vid_shorts_2",
            title = "Clean Architecture package layout walkthrough!",
            description = "Separating Presentation, Domain, Data, and Network boundaries with modern Kotlin structures.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
            thumbnailUrl = "https://images.unsplash.com/photo-1507238691740-187a5b1d37b8?auto=format&fit=crop&w=640&q=80",
            creatorId = "creator_rustacean",
            creatorName = "The Coding Sanctuary",
            creatorAvatar = "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?auto=format&fit=crop&w=120&q=80",
            views = 540200,
            likes = 42100,
            dislikes = 190,
            duration = "00:45",
            uploadDate = "1 week ago",
            category = "Education",
            isShort = true,
            commentsCount = 312
        ),
        Video(
            id = "vid_shorts_3",
            title = "Satisfying Keyboard Customization Sound Test ⌨️",
            description = "Custom linear switches lubed with Krytox 205g0. Hand-wired ortholinear sound showcase.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
            thumbnailUrl = "https://images.unsplash.com/photo-1618384887929-16ec33fab9ef?auto=format&fit=crop&w=640&q=80",
            creatorId = "creator_lofi_beats",
            creatorName = "Sonic Horizon",
            creatorAvatar = "https://images.unsplash.com/photo-1487412720507-e7ab37603c6f?auto=format&fit=crop&w=120&q=80",
            views = 1240000,
            likes = 135000,
            dislikes = 720,
            duration = "00:30",
            uploadDate = "2 weeks ago",
            category = "Music",
            isShort = true,
            commentsCount = 1450
        )
    )

    // In-memory creator states
    private val subscribedCreatorIds = MutableStateFlow<Set<String>>(emptySet())
    private val videoLikes = MutableStateFlow<Map<String, Boolean>>(emptyMap()) // videoId to isLiked (true=like, false=dislike)
    private val videoComments = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())

    init {
        // Initialize sample comments
        val defaultComments = sampleVideos.associate { video ->
            video.id to listOf(
                Comment(
                    id = "comment_${video.id}_1",
                    videoId = video.id,
                    userName = "Jordan Sparks",
                    userAvatar = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=120&q=80",
                    content = "Wow, the production quality here is incredible! Thanks for sharing this detailed review.",
                    timestamp = "1 day ago",
                    likes = 42
                ),
                Comment(
                    id = "comment_${video.id}_2",
                    videoId = video.id,
                    userName = "Clara Bennett",
                    userAvatar = "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?auto=format&fit=crop&w=120&q=80",
                    content = "Been trying to master compose layout structure for three months now, and this clarified everything.",
                    timestamp = "15 hours ago",
                    likes = 12
                )
            )
        }
        videoComments.value = defaultComments
    }

    // --- Video Fetching ---
    fun getVideos(category: String): Flow<List<Video>> = flow {
        try {
            val list = StreamPlayRetrofitClient.service.getVideos(category = category, isShort = false)
            emit(list)
        } catch (e: Exception) {
            android.util.Log.e("VideoRepository", "Failed to fetch remote videos: ${e.message}")
            val result = if (category == "Trending") {
                sampleVideos.filter { !it.isShort }.sortedByDescending { it.views }
            } else if (category == "Recommended") {
                sampleVideos.filter { !it.isShort }
            } else {
                sampleVideos.filter { !it.isShort && it.category.equals(category, ignoreCase = true) }
            }
            emit(result)
        }
    }

    fun getShorts(): Flow<List<Video>> = flow {
        try {
            val list = StreamPlayRetrofitClient.service.getVideos(isShort = true)
            emit(list)
        } catch (e: Exception) {
            android.util.Log.e("VideoRepository", "Failed to fetch remote shorts: ${e.message}")
            emit(sampleVideos.filter { it.isShort })
        }
    }

    fun getVideoById(id: String): Video? {
        return sampleVideos.find { it.id == id }
    }

    // --- Search with Suggestions ---
    fun getSearchSuggestions(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        val allQueries = listOf(
            "Jetpack Compose layout tutorial",
            "Rust web framework benchmarks",
            "Deep house study session beats",
            "Best foldable tablet reviews 2026",
            "Tech stock analysis and news",
            "Jupiter Europa mission live",
            "Elden Ring dance pad world record",
            "Database indexes explained simply",
            "Clean architecture dependency injection",
            "ExoPlayer HLS adaptive stream setup",
            "Jetpack Compose Canvas animations"
        )
        return allQueries.filter { it.contains(query, ignoreCase = true) }
    }

    fun searchVideos(query: String): Flow<List<Video>> = flow {
        try {
            val list = StreamPlayRetrofitClient.service.getVideos(query = query)
            emit(list)
        } catch (e: Exception) {
            android.util.Log.e("VideoRepository", "Failed to search remote videos: ${e.message}")
            if (query.isBlank()) {
                emit(sampleVideos)
            } else {
                emit(sampleVideos.filter {
                    it.title.contains(query, ignoreCase = true) ||
                            it.description.contains(query, ignoreCase = true) ||
                            it.creatorName.contains(query, ignoreCase = true)
                })
            }
        }
    }

    // --- Subscription & Creator Follow ---
    suspend fun getCreatorChannel(creatorId: String): Channel {
        return try {
            StreamPlayRetrofitClient.service.getCreatorChannel(creatorId)
        } catch (e: Exception) {
            android.util.Log.e("VideoRepository", "Failed to fetch remote creator channel: ${e.message}")
            val videoCount = sampleVideos.count { it.creatorId == creatorId }
            val name = sampleVideos.find { it.creatorId == creatorId }?.creatorName ?: "Unknown Channel"
            val avatar = sampleVideos.find { it.creatorId == creatorId }?.creatorAvatar ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=120&q=80"
            
            Channel(
                id = creatorId,
                name = name,
                avatarUrl = avatar,
                bannerUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=1280&h=300&q=80",
                subscriberCount = 24500 + (if (subscribedCreatorIds.value.contains(creatorId)) 1 else 0),
                videoCount = videoCount,
                isSubscribed = subscribedCreatorIds.value.contains(creatorId),
                description = "Welcome to the official $name channel! Dedicated to high fidelity tutorial videos, live streams, and developer insights.",
                totalEarnings = 1450.50,
                monthlyViews = 48900
            )
        }
    }

    fun isSubscribed(creatorId: String): Flow<Boolean> {
        return subscribedCreatorIds.map { it.contains(creatorId) }
    }

    suspend fun toggleSubscription(creatorId: String) {
        try {
            StreamPlayRetrofitClient.service.toggleSubscription(creatorId)
        } catch (e: Exception) {
            android.util.Log.e("VideoRepository", "Failed to post remote subscription: ${e.message}")
        }
        val current = subscribedCreatorIds.value.toMutableSet()
        if (current.contains(creatorId)) {
            current.remove(creatorId)
        } else {
            current.add(creatorId)
        }
        subscribedCreatorIds.value = current
    }

    // --- Like / Dislike ---
    fun getVideoLikesState(videoId: String): Flow<Boolean?> {
        return videoLikes.map { it[videoId] }
    }

    suspend fun toggleLike(videoId: String, isLike: Boolean) {
        try {
            StreamPlayRetrofitClient.service.likeVideo(videoId, LikeRequest(isLike))
        } catch (e: Exception) {
            android.util.Log.e("VideoRepository", "Failed to post remote like: ${e.message}")
        }
        val current = videoLikes.value.toMutableMap()
        if (current[videoId] == isLike) {
            current.remove(videoId)
        } else {
            current[videoId] = isLike
        }
        videoLikes.value = current
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
        try {
            val newComment = StreamPlayRetrofitClient.service.postComment(videoId, CommentRequest(content))
            val list = videoComments.value[videoId]?.toMutableList() ?: mutableListOf()
            list.add(0, newComment)
            val current = videoComments.value.toMutableMap()
            current[videoId] = list
            videoComments.value = current
        } catch (e: Exception) {
            android.util.Log.e("VideoRepository", "Failed to post remote comment: ${e.message}")
            val list = videoComments.value[videoId]?.toMutableList() ?: mutableListOf()
            val comment = Comment(
                id = "comment_" + UUID.randomUUID().toString().take(6),
                videoId = videoId,
                userName = userName,
                userAvatar = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=120&q=80",
                content = content,
                timestamp = "Just now"
            )
            list.add(0, comment)
            val current = videoComments.value.toMutableMap()
            current[videoId] = list
            videoComments.value = current
        }
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
                // Keep it in database but remove Watch Later flag
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

    // --- Offline Downloading Mock Stream ---
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
        
        // Simulating the background download work
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

    // --- Gemini-Powered Smart Recommendation Engine with HIGH Thinking ---
    suspend fun getAiSmartRecommendations(prompt: String): Flow<List<Video>> = flow {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey.contains("dummy")) {
            // Graceful fallback to simple recommendation algorithm
            val matched = sampleVideos.filter {
                it.title.contains(prompt, ignoreCase = true) ||
                        it.description.contains(prompt, ignoreCase = true) ||
                        it.category.contains(prompt, ignoreCase = true)
            }
            emit(matched.ifEmpty { sampleVideos.shuffled().take(3) })
            return@flow
        }

        // Build a robust reasoning prompt to find the perfect video
        val contextPrompt = """
            You are the smart AI Copilot inside StreamPlay, an advanced video streaming application.
            Here is the current database of available videos in the catalog:
            ${sampleVideos.joinToString("\n") { "- ID: ${it.id}, Title: ${it.title}, Category: ${it.category}, Desc: ${it.description}" }}
            
            The user is asking: "$prompt"
            
            Analyze the query and use reasoning to select the top 2-3 most relevant videos from this database.
            Return your answer in plain text with a list of matching video IDs, with a brief explanation of why you selected each. Format it cleanly for the UI.
            If no videos match directly, recommend the most relevant category or general programming videos.
        """.trimIndent()

        val request = GeminiGenerateContentRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = contextPrompt)))),
            generationConfig = GeminiGenerationConfig(
                thinkingConfig = ThinkingConfig("HIGH") // HIGH thinking requested
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = "You are a highly analytical video curator. Always recommend real IDs from the list provided."))
            )
        )

        try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            
            // Extract IDs from the response text
            val matchedIds = sampleVideos.filter { responseText.contains(it.id) }.map { it.id }
            val finalVideos = sampleVideos.filter { matchedIds.contains(it.id) }
            emit(finalVideos.ifEmpty { sampleVideos.shuffled().take(3) })
        } catch (e: Exception) {
            // Safe fallback
            emit(sampleVideos.shuffled().take(3))
        }
    }

    // Interactive AI chat inside streaming player (Video summary, quiz, habbits)
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
