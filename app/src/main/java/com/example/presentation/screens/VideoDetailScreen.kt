package com.example.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.models.UserProfile
import com.example.models.Video
import com.example.presentation.AuthViewModel
import com.example.presentation.VideoViewModel
import com.example.presentation.components.VideoPlayer

@Composable
fun VideoDetailScreen(
    authViewModel: AuthViewModel,
    videoViewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val video by videoViewModel.activeVideo.collectAsState()
    val channel by videoViewModel.currentChannel.collectAsState()
    val comments by videoViewModel.currentVideoComments.collectAsState()
    val isSubscribed by videoViewModel.isCurrentCreatorSubscribed.collectAsState()
    val isLiked by videoViewModel.isCurrentVideoLiked.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    val playbackSpeed by videoViewModel.playbackSpeed.collectAsState()
    val videoQuality by videoViewModel.videoQuality.collectAsState()

    val aiResponse by videoViewModel.aiCopilotResponse.collectAsState()
    val isAiLoading by videoViewModel.isAiCopilotLoading.collectAsState()

    var activeTabIdx by remember { mutableStateOf(0) }
    val detailTabs = listOf("AI Co-Watcher", "Comments", "More Videos")

    var commentInputText by remember { mutableStateOf("") }
    var copilotInputText by remember { mutableStateOf("") }

    if (video == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text("Select a video to start streaming.")
        }
        return
    }

    val currentVideo = video!!

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Safe drawing header to avoid notch overlapping
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        // ExoPlayer Canvas
        VideoPlayer(
            videoUrl = currentVideo.videoUrl,
            playbackSpeed = playbackSpeed,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )

        // Title and metrics
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                item {
                    Text(
                        text = currentVideo.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                // Views and Upload Date
                item {
                    Text(
                        text = "${currentVideo.views / 1000}K views • ${currentVideo.uploadDate} • #${currentVideo.category}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                // Quick Action Bar (Like, Speed, Quality, Download, Save)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Like Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { videoViewModel.toggleLike(currentVideo.id, true) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("like_button")
                        ) {
                            Icon(
                                imageVector = if (isLiked == true) Icons.Default.ThumbUp else Icons.Default.ThumbUp,
                                contentDescription = "Like",
                                tint = if (isLiked == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isLiked == true) "${currentVideo.likes + 1}" else "${currentVideo.likes}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Speed Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    val nextSpeed = when (playbackSpeed) {
                                        1.0f -> 1.5f
                                        1.5f -> 2.0f
                                        else -> 1.0f
                                    }
                                    videoViewModel.setPlaybackSpeed(nextSpeed)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("speed_button")
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${playbackSpeed}x", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Quality selection
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    val nextQuality = when (videoQuality) {
                                        "Auto" -> "1080p"
                                        "1080p" -> "4K"
                                        else -> "Auto"
                                    }
                                    videoViewModel.setVideoQuality(nextQuality)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("quality_button")
                        ) {
                            Icon(Icons.Default.Hd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(videoQuality, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Download button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { videoViewModel.downloadVideo(currentVideo) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("download_button")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Save Watch Later button
                        IconButton(
                            onClick = { videoViewModel.toggleWatchLater(currentVideo) },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .testTag("watch_later_button")
                        ) {
                            Icon(Icons.Default.BookmarkBorder, contentDescription = "Watch Later", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Channel Header Profile Card
                item {
                    channel?.let { creatorChannel ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(creatorChannel.avatarUrl),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )

                                    Column {
                                        Text(creatorChannel.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("${creatorChannel.subscriberCount} subscribers", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                }

                                Button(
                                    onClick = { videoViewModel.toggleSubscription(currentVideo.creatorId) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else Color.Red
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.testTag("subscribe_button")
                                ) {
                                    Text(
                                        text = if (isSubscribed) "SUBSCRIBED" else "SUBSCRIBE",
                                        color = if (isSubscribed) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Tab Selectors
                item {
                    TabRow(
                        selectedTabIndex = activeTabIdx,
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        detailTabs.forEachIndexed { idx, label ->
                            Tab(
                                selected = idx == activeTabIdx,
                                onClick = { activeTabIdx = idx },
                                modifier = Modifier.testTag("tab_$label")
                            ) {
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                    }
                }

                // AI Co-Watcher Tab Content
                if (activeTabIdx == 0) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Gemini Co-Watching CoPilot", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            // Preset Prompt Chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable { videoViewModel.askAiCopilot("Summarize this video and provide 3 key learning bullet points.") }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .testTag("prompt_summarize")
                                ) {
                                    Text("Summarize Video", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable { videoViewModel.askAiCopilot("Provide a short interactive quiz with 2 questions based on this video's topic.") }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .testTag("prompt_quiz")
                                ) {
                                    Text("Quiz Me", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Response display area
                            if (isAiLoading || aiResponse.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        if (isAiLoading) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                                Text("Thinking Mode (gemini-3.1-pro-preview)...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Text(
                                                text = aiResponse,
                                                fontSize = 12.sp,
                                                lineHeight = 18.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // Custom analytical search query box
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = copilotInputText,
                                    onValueChange = { copilotInputText = it },
                                    placeholder = { Text("Ask the AI co-watcher...", fontSize = 12.sp) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("copilot_input"),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        if (copilotInputText.isNotBlank()) {
                                            videoViewModel.askAiCopilot(copilotInputText)
                                            copilotInputText = ""
                                        }
                                    },
                                    modifier = Modifier.testTag("ask_copilot_submit")
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Comments Tab Content
                if (activeTabIdx == 1) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Text Input
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(currentUser?.avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=120&q=80"),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                )
                                OutlinedTextField(
                                    value = commentInputText,
                                    onValueChange = { commentInputText = it },
                                    placeholder = { Text("Add a comment...", fontSize = 12.sp) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("comment_input"),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        if (commentInputText.isNotBlank()) {
                                            videoViewModel.addComment(
                                                currentVideo.id,
                                                currentUser?.displayName ?: "Viewer",
                                                commentInputText
                                            )
                                            commentInputText = ""
                                        }
                                    },
                                    modifier = Modifier.testTag("post_comment_button")
                                ) {
                                    Text("Post", fontSize = 12.sp)
                                }
                            }

                            // Comments List items
                            if (comments.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Be the first to comment!", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    comments.forEach { comment ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Image(
                                                painter = rememberAsyncImagePainter(comment.userAvatar),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(comment.userName, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                    Text(comment.timestamp, fontSize = 9.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                                }
                                                Text(comment.content, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Up Next Tab Content
                if (activeTabIdx == 2) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // We can trigger an AI recommendation prompt automatically here
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable { videoViewModel.triggerAiRecommendationsSearch(currentVideo.title) }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("Smart Recommendation System", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text("Find matching references for deep diving via Gemini", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Recommended Next", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                            // We can display custom list or simple recommendations
                            val matchingList by videoViewModel.aiRecommendations.collectAsState()
                            val isLoadingRecommendations by videoViewModel.isAiRecommendationLoading.collectAsState()

                            if (isLoadingRecommendations) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            } else if (matchingList.isNotEmpty()) {
                                matchingList.forEach { matched ->
                                    if (matched.id != currentVideo.id) {
                                        MiniVideoRowItem(
                                            video = matched,
                                            onClick = { videoViewModel.setActiveVideo(matched) }
                                        )
                                    }
                                }
                            } else {
                                // Fallback when AI search is unactivated
                                val allVideos = listOf(
                                    "vid_1", "vid_2", "vid_3", "vid_4", "vid_5", "vid_6", "vid_7"
                                )
                                for (vidId in allVideos) {
                                    if (vidId != currentVideo.id) {
                                        val nextVideo = videoViewModel.getVideoById(vidId)
                                        if (nextVideo != null) {
                                            MiniVideoRowItem(
                                                video = nextVideo,
                                                onClick = { videoViewModel.setActiveVideo(nextVideo) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

@Composable
fun MiniVideoRowItem(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 70.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(video.thumbnailUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(video.duration, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${video.creatorName} • ${video.views / 1000}K views",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}
