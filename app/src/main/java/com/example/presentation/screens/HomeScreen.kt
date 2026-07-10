package com.example.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlinx.coroutines.launch
import com.example.database.WatchHistoryEntity
import com.example.models.Video
import com.example.presentation.VideoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: VideoViewModel,
    onNavigateToVideo: (Video) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val videos by viewModel.videoFeed.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()

    val categories = listOf("Recommended", "Trending", "Gaming", "Music", "News", "Education", "Live")
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            coroutineScope.launch {
                isRefreshing = true
                viewModel.selectCategory(selectedCategory)
                kotlinx.coroutines.delay(1200)
                isRefreshing = false
            }
        },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        // Hero Banner at the top
        item {
            HeroRecommendationBanner(
                video = videos.firstOrNull(),
                onClick = { videos.firstOrNull()?.let(onNavigateToVideo) }
            )
        }

        // Horizontal Category Row
        item {
            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
                edgePadding = 16.dp,
                divider = {},
                indicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                categories.forEachIndexed { index, categoryName ->
                    val isSelected = categoryName == selectedCategory
                    Tab(
                        selected = isSelected,
                        onClick = { viewModel.selectCategory(categoryName) },
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("category_tab_$categoryName")
                    ) {
                        Text(
                            text = categoryName,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Continue Watching Row if history is populated
        if (watchHistory.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "Continue Watching",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(watchHistory) { historyItem ->
                            ContinueWatchingCard(
                                historyItem = historyItem,
                                onClick = {
                                    val video = viewModel.getVideoById(historyItem.videoId)
                                    if (video != null) {
                                        onNavigateToVideo(video)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // General Video Grid / List
        item {
            Text(
                text = if (selectedCategory == "Recommended") "For You" else selectedCategory,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
            )
        }

        if (videos.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No videos available in this category.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            // Display regular feeds skipping first item if recommended (since it is already on the hero banner)
            val feedList = if (selectedCategory == "Recommended" && videos.size > 1) videos.drop(1) else videos
            items(feedList) { video ->
                VideoFeedCard(
                    video = video,
                    onClick = { onNavigateToVideo(video) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
    }
}

@Composable
fun HeroRecommendationBanner(
    video: Video?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (video == null) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = rememberAsyncImagePainter(video.thumbnailUrl),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Vignette Gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
        )

        // Live/Ad badge
        if (video.isLive) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Red)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .align(Alignment.TopStart)
            ) {
                Text("LIVE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                "FEATURED VIDEO",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${video.creatorName} • ${video.views / 1000}K views",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun VideoFeedCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Image(
                    painter = rememberAsyncImagePainter(video.thumbnailUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Bottom badge for duration or live stream
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (video.isLive) Color.Red else Color.Black.copy(alpha = 0.8f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (video.isLive) "LIVE" else video.duration,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(video.creatorAvatar),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${video.creatorName} • ${video.views / 1000}K views • ${video.uploadDate}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    historyItem: WatchHistoryEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(200.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(historyItem.thumbnailUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Simulated Red playback progress line
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = 0.65f)
                        .height(3.dp)
                        .background(Color.Red)
                        .align(Alignment.BottomStart)
                )
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = historyItem.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = historyItem.creatorName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
