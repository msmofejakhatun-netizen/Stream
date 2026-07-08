package com.example.models

import androidx.annotation.Keep

@Keep
data class Video(
    val id: String,
    val title: String,
    val description: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val creatorId: String,
    val creatorName: String,
    val creatorAvatar: String,
    val views: Long,
    val likes: Long,
    val dislikes: Long,
    val duration: String,
    val uploadDate: String,
    val category: String,
    val isLive: Boolean = false,
    val isShort: Boolean = false,
    val commentsCount: Int = 0,
    val shareUrl: String = "https://streamplay.aistudio.com/v/$id"
)

@Keep
data class Channel(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val bannerUrl: String,
    val subscriberCount: Int,
    val videoCount: Int,
    val isSubscribed: Boolean = false,
    val description: String = "",
    val totalEarnings: Double = 0.0,
    val monthlyViews: Long = 0
)

@Keep
data class Comment(
    val id: String,
    val videoId: String,
    val userName: String,
    val userAvatar: String,
    val content: String,
    val timestamp: String,
    val likes: Int = 0,
    val hasLiked: Boolean = false
)

@Keep
data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String,
    val isGuest: Boolean = false,
    val joinedDate: String = "Jul 2026",
    val subscribersCount: Int = 0
)

@Keep
data class Playlist(
    val id: String,
    val name: String,
    val videoCount: Int,
    val isPrivate: Boolean = true,
    val createdDate: String = "Jul 2026",
    val videoIds: List<String> = emptyList()
)

@Keep
data class NotificationItem(
    val id: String,
    val title: String,
    val description: String,
    val timestamp: String,
    val videoId: String?,
    val isLive: Boolean = false,
    val isRead: Boolean = false
)
