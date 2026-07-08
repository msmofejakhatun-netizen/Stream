package com.example.presentation.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.models.Video
import com.example.presentation.VideoViewModel
import com.example.presentation.components.VideoPlayer

@Composable
fun ShortsScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val shortsFeed by viewModel.shortsFeed.collectAsState()
    var currentIndex by remember { mutableStateOf(0) }

    if (shortsFeed.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val activeShort = shortsFeed.getOrNull(currentIndex) ?: return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Full screen video player
        VideoPlayer(
            videoUrl = activeShort.videoUrl,
            modifier = Modifier.fillMaxSize(),
            onVideoFinished = {
                // Auto play next video
                if (currentIndex < shortsFeed.size - 1) {
                    currentIndex++
                } else {
                    currentIndex = 0
                }
            }
        )

        // Bottom gradient for captions overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.35f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .align(Alignment.BottomCenter)
        )

        // Next/Prev Buttons overlay for sandbox desktop interaction
        Column(
            modifier = Modifier
                .padding(16.dp)
                .statusBarsPadding()
                .align(Alignment.TopEnd),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    if (currentIndex > 0) currentIndex-- else currentIndex = shortsFeed.size - 1
                },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .size(40.dp)
                    .testTag("shorts_prev_button")
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Previous Short", tint = Color.White)
            }

            IconButton(
                onClick = {
                    if (currentIndex < shortsFeed.size - 1) currentIndex++ else currentIndex = 0
                },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .size(40.dp)
                    .testTag("shorts_next_button")
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Next Short", tint = Color.White)
            }
        }

        // Left-side overlays for captions and creator detail
        Column(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            // Channel Name + Follow action
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(activeShort.creatorAvatar),
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = "@${activeShort.creatorName.replace(" ", "").lowercase()}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Red)
                        .clickable { viewModel.toggleSubscription(activeShort.creatorId) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "FOLLOW",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Short caption
            Text(
                text = activeShort.title,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = activeShort.description,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(60.dp)) // Avoid navigation overlapping
        }

        // Right-side columns for standard Shorts actions
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            var isLiked by remember(activeShort.id) { mutableStateOf(false) }

            // Like Action
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { isLiked = !isLiked },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .size(50.dp)
                        .testTag("shorts_like_button")
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color.Red else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = if (isLiked) "${(activeShort.likes + 1) / 1000}K" else "${activeShort.likes / 1000}K",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Comment Action
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { /* Display bottomsheet or pop toast */ },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .size(50.dp)
                        .testTag("shorts_comment_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Comment,
                        contentDescription = "Comment",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    text = "${activeShort.commentsCount}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Share Action
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .size(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    "Share",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Sound Wheel Disk Rotation simulation
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
                    .padding(4.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(activeShort.creatorAvatar),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(60.dp)) // Avoid navigation overlapping
        }
    }
}
