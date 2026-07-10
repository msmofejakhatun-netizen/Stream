package com.example.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.LocalPlaylistEntity
import com.example.database.OfflineVideoEntity
import com.example.database.SavedVideoEntity
import com.example.database.WatchHistoryEntity
import com.example.models.Channel
import com.example.models.Comment
import com.example.models.Video
import com.example.repository.VideoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class VideoViewModel(private val repository: VideoRepository) : ViewModel() {

    // --- Core Video Feed ---
    private val _selectedCategory = MutableStateFlow("Recommended")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    val videoFeed: StateFlow<List<Video>> = _selectedCategory
        .flatMapLatest { category -> repository.getVideos(category) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shortsFeed: StateFlow<List<Video>> = repository.getShorts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Search ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val suggestions: StateFlow<List<String>> = _searchQuery
        .map { query -> repository.getSearchSuggestions(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchResults: StateFlow<List<Video>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query -> repository.searchVideos(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Active Playback State ---
    private val _activeVideo = MutableStateFlow<Video?>(null)
    val activeVideo: StateFlow<Video?> = _activeVideo.asStateFlow()

    val currentChannel: StateFlow<Channel?> = _activeVideo
        .flatMapLatest { video ->
            flow {
                val channel = video?.let { repository.getCreatorChannel(it.creatorId) }
                emit(channel)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentVideoComments: StateFlow<List<Comment>> = _activeVideo
        .flatMapLatest { video ->
            video?.let { repository.getComments(it.id) } ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isCurrentCreatorSubscribed: StateFlow<Boolean> = _activeVideo
        .flatMapLatest { video ->
            video?.let { repository.isSubscribed(it.creatorId) } ?: flowOf(false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isCurrentVideoLiked: StateFlow<Boolean?> = _activeVideo
        .flatMapLatest { video ->
            video?.let { repository.getVideoLikesState(it.id) } ?: flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _videoQuality = MutableStateFlow("Auto")
    val videoQuality: StateFlow<String> = _videoQuality.asStateFlow()

    // --- Local Persistence ---
    val watchHistory: StateFlow<List<WatchHistoryEntity>> = repository.watchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedVideos: StateFlow<List<SavedVideoEntity>> = repository.savedVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchLaterVideos: StateFlow<List<SavedVideoEntity>> = repository.watchLaterVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offlineVideos: StateFlow<List<OfflineVideoEntity>> = repository.offlineVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<LocalPlaylistEntity>> = repository.localPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Gemini AI Features (Smart Search Recommendations and Co-Watching Chat) ---
    private val _aiRecommendationQuery = MutableStateFlow("")
    val aiRecommendationQuery: StateFlow<String> = _aiRecommendationQuery.asStateFlow()

    private val _isAiRecommendationLoading = MutableStateFlow(false)
    val isAiRecommendationLoading: StateFlow<Boolean> = _isAiRecommendationLoading.asStateFlow()

    val aiRecommendations: StateFlow<List<Video>> = _aiRecommendationQuery
        .debounce(500)
        .filter { it.isNotBlank() }
        .flatMapLatest { prompt ->
            _isAiRecommendationLoading.value = true
            repository.getAiSmartRecommendations(prompt)
                .onEach { _isAiRecommendationLoading.value = false }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _aiCopilotResponse = MutableStateFlow("")
    val aiCopilotResponse: StateFlow<String> = _aiCopilotResponse.asStateFlow()

    private val _isAiCopilotLoading = MutableStateFlow(false)
    val isAiCopilotLoading: StateFlow<Boolean> = _isAiCopilotLoading.asStateFlow()

    // --- Playback Actions ---
    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun setActiveVideo(video: Video?) {
        _activeVideo.value = video
        if (video != null) {
            viewModelScope.launch {
                repository.addToHistory(video)
            }
        }
        // Clear copilot chat on changing video
        _aiCopilotResponse.value = ""
    }

    fun getVideoById(id: String): Video? {
        return repository.getVideoById(id)
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
    }

    fun setVideoQuality(quality: String) {
        _videoQuality.value = quality
    }

    fun toggleSubscription(creatorId: String) {
        viewModelScope.launch {
            repository.toggleSubscription(creatorId)
        }
    }

    fun toggleLike(videoId: String, isLike: Boolean) {
        viewModelScope.launch {
            repository.toggleLike(videoId, isLike)
        }
    }

    fun addComment(videoId: String, userName: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.postComment(videoId, userName, text)
        }
    }

    fun toggleWatchLater(video: Video) {
        viewModelScope.launch {
            repository.toggleWatchLater(video)
        }
    }

    // --- Playlists Management ---
    fun createPlaylist(name: String, isPrivate: Boolean) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name, isPrivate)
        }
    }

    fun addVideoToPlaylist(playlistId: String, videoId: String) {
        viewModelScope.launch {
            repository.addVideoToPlaylist(playlistId, videoId)
        }
    }

    fun removeVideoFromPlaylist(playlistId: String, videoId: String) {
        viewModelScope.launch {
            repository.removeVideoFromPlaylist(playlistId, videoId)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    // --- Offline Download Actions ---
    fun downloadVideo(video: Video) {
        viewModelScope.launch {
            repository.downloadVideo(video)
        }
    }

    fun removeDownload(videoId: String) {
        viewModelScope.launch {
            repository.removeOfflineDownload(videoId)
        }
    }

    // --- AI Smart recommendations search query ---
    fun triggerAiRecommendationsSearch(query: String) {
        _aiRecommendationQuery.value = query
    }

    // --- AI Co-watching Analytical Assistant query ---
    fun askAiCopilot(userQuery: String) {
        val video = _activeVideo.value ?: return
        if (userQuery.isBlank()) return
        viewModelScope.launch {
            _isAiCopilotLoading.value = true
            _aiCopilotResponse.value = "AI thinking (ThinkingLevel.HIGH is active)..."
            val response = repository.getAiResponseForVideo(video.id, userQuery)
            _aiCopilotResponse.value = response
            _isAiCopilotLoading.value = false
        }
    }

    // --- Creator Studio Features (Mock/State tracking) ---
    private val _uploadedVideos = MutableStateFlow<List<Video>>(emptyList())
    val uploadedVideos: StateFlow<List<Video>> = _uploadedVideos.asStateFlow()

    private val _studioUploadingState = MutableStateFlow<String?>(null) // null, "uploading", "transcoding", "done"
    val studioUploadingState: StateFlow<String?> = _studioUploadingState.asStateFlow()

    private val _uploadProgress = MutableStateFlow(0)
    val uploadProgress: StateFlow<Int> = _uploadProgress.asStateFlow()

    fun uploadSimulatedVideo(title: String, description: String, category: String, duration: String) {
        viewModelScope.launch {
            _studioUploadingState.value = "uploading"
            _uploadProgress.value = 0
            try {
                val newVideo = repository.uploadDummyVideo(
                    title = title,
                    description = description,
                    category = category,
                    onProgress = { progress ->
                        _uploadProgress.value = progress
                    }
                )
                _uploadedVideos.value = _uploadedVideos.value + newVideo
                _studioUploadingState.value = "done"
                kotlinx.coroutines.delay(1000)
                _studioUploadingState.value = null
            } catch (e: Exception) {
                android.util.Log.e("VideoViewModel", "Network upload failed: ${e.message}")
                _studioUploadingState.value = "error"
                kotlinx.coroutines.delay(2000)
                _studioUploadingState.value = null
            }
        }
    }

    // --- Admin Moderation Features ---
    private val _reportedVideos = MutableStateFlow<List<Video>>(
        listOf(
            Video(
                id = "vid_report_1",
                title = "Phishing Scams Unveiled: Get rich quick scheme details",
                description = "This video contains dubious links promising financial returns. Marked as potentially harmful by moderators.",
                videoUrl = "https://pub-streamplay.r2.dev/sample/ElephantsDream.mp4",
                thumbnailUrl = "https://images.unsplash.com/photo-1589829545856-d10d557cf95f?auto=format&fit=crop&w=640&q=80",
                creatorId = "creator_scam",
                creatorName = "QuickCryptoWealth",
                creatorAvatar = "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&w=120&q=80",
                views = 150,
                likes = 1,
                dislikes = 120,
                duration = "02:40",
                uploadDate = "10 hours ago",
                category = "News"
            )
        )
    )
    val reportedVideos: StateFlow<List<Video>> = _reportedVideos.asStateFlow()

    fun approveReportedVideo(videoId: String) {
        _reportedVideos.value = _reportedVideos.value.filter { it.id != videoId }
    }

    fun removeReportedVideo(videoId: String) {
        _reportedVideos.value = _reportedVideos.value.filter { it.id != videoId }
    }
}
