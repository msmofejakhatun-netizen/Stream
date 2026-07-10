package com.example.presentation.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.models.Comment
import com.example.models.Video
import com.example.presentation.VideoViewModel
import com.example.presentation.components.VideoPlayer
import com.example.network.VideoCacheManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Data state tracking double-tap heart bursts
data class HeartTapState(val id: Long, val x: Float, val y: Float)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShortsScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val shortsFeed by viewModel.shortsFeed.collectAsState()

    // 1. Loading and Empty state
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

    // 2. Vertical Pager setup matching Instagram Reels / YouTube Shorts exactly
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { shortsFeed.size }
    )

    // 3. Asynchronous Preloading of the Next 3 short videos (Optimizes scrolling to 60 FPS without lag)
    LaunchedEffect(pagerState.currentPage, shortsFeed) {
        for (offset in 1..3) {
            val nextIndex = pagerState.currentPage + offset
            if (nextIndex < shortsFeed.size) {
                val nextShort = shortsFeed[nextIndex]
                android.util.Log.d("ShortsScreen", "Preloader scheduling next short: ${nextShort.title}")
                VideoCacheManager.preloadVideo(context, nextShort.videoUrl)
            }
        }
    }

    // Comment Modal State
    var showCommentsSheet by remember { mutableStateOf<Video?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val video = shortsFeed[pageIndex]
            val isCurrentVisiblePage = pagerState.currentPage == pageIndex

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Tracking coordinates of double-tap to spawn beautiful heart bursts
                val heartTaps = remember { mutableStateListOf<HeartTapState>() }
                var isLikedByMe by remember(video.id) { mutableStateOf(false) }

                // Full screen video player (HLS & MP4 cache-enabled adaptive player)
                VideoPlayer(
                    videoUrl = video.videoUrl,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(video.id) {
                            detectTapGestures(
                                onDoubleTap = { offset ->
                                    isLikedByMe = true
                                    viewModel.toggleLike(video.id, true)
                                    heartTaps.add(
                                        HeartTapState(
                                            id = System.currentTimeMillis(),
                                            x = offset.x,
                                            y = offset.y
                                        )
                                    )
                                }
                            )
                        },
                    useController = false, // Pure immersive full screen player
                    isPlaying = isCurrentVisiblePage, // Only play if fully visible! Auto-pauses all others.
                    onVideoFinished = {
                        // Auto Play Next Video
                        coroutineScope.launch {
                            if (pagerState.currentPage < shortsFeed.size - 1) {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                )

                // Double tap Heart bursts renderer
                val density = LocalDensity.current
                heartTaps.forEach { tap ->
                    key(tap.id) {
                        HeartBurst(
                            xDp = with(density) { tap.x.toDp() },
                            yDp = with(density) { tap.y.toDp() },
                            onFinish = { heartTaps.remove(tap) }
                        )
                    }
                }

                // Bottom gradient for captions overlay readability
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

                // Left-side overlay for details (Creator name, description, follows)
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(video.creatorAvatar),
                            contentDescription = "Creator Avatar",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Text(
                            text = "@${video.creatorName.replace(" ", "").lowercase()}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        val isSubscribed = viewModel.isCurrentCreatorSubscribed.collectAsState().value
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSubscribed) Color.Gray else Color.Red)
                                .clickable { viewModel.toggleSubscription(video.creatorId) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (isSubscribed) "FOLLOWED" else "FOLLOW",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = video.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = video.description,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(60.dp)) // Safe spacing for navigation overlap prevention
                }

                // Infinite Rotating Sound Wheel Disk simulation
                val infiniteTransition = rememberInfiniteTransition(label = "Disk Rotation")
                val diskRotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(4000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "Rotation"
                )

                // Right-side premium actions panel (Like, Comment, Share, Sound disk)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Like Button with responsive tap animation
                    var likeButtonScale by remember { mutableStateOf(1.0f) }
                    val animatedLikeScale by animateFloatAsState(targetValue = likeButtonScale, label = "Like Button Scale")

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                isLikedByMe = !isLikedByMe
                                viewModel.toggleLike(video.id, isLikedByMe)
                                likeButtonScale = 1.3f
                                coroutineScope.launch {
                                    delay(150)
                                    likeButtonScale = 1.0f
                                }
                            },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                .size(50.dp)
                                .scale(animatedLikeScale)
                                .testTag("shorts_like_button")
                        ) {
                            Icon(
                                imageVector = if (isLikedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (isLikedByMe) Color.Red else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = if (isLikedByMe) "${(video.likes + 1) / 1000}K" else "${video.likes / 1000}K",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // Comment Button opening sliding bottom-sheet
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { showCommentsSheet = video },
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
                            text = "${video.commentsCount}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // Share Button
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

                    // Disk Rotation Wheel
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .rotate(diskRotation)
                            .background(Color.DarkGray)
                            .padding(4.dp)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(video.creatorAvatar),
                            contentDescription = "Rotating Sound Disk",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(60.dp)) // Prevent system bar overlapping
                }
            }
        }

        // 4. Desktop-friendly Fallback Scrolling Buttons (top-right side)
        Column(
            modifier = Modifier
                .padding(16.dp)
                .statusBarsPadding()
                .align(Alignment.TopEnd),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        if (pagerState.currentPage > 0) {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }
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
                    coroutineScope.launch {
                        if (pagerState.currentPage < shortsFeed.size - 1) {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .size(40.dp)
                    .testTag("shorts_next_button")
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Next Short", tint = Color.White)
            }
        }

        // 5. Sliding Modal Bottom Sheet for Production-Grade Comments Integration
        if (showCommentsSheet != null) {
            val videoForComments = showCommentsSheet!!
            ModalBottomSheet(
                onDismissRequest = { showCommentsSheet = null },
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                CommentsSheetContent(
                    video = videoForComments,
                    viewModel = viewModel,
                    onClose = { showCommentsSheet = null }
                )
            }
        }
    }
}

@Composable
fun HeartBurst(
    xDp: androidx.compose.ui.unit.Dp,
    yDp: androidx.compose.ui.unit.Dp,
    onFinish: () -> Unit
) {
    var isAnimated by remember { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(
        targetValue = if (isAnimated) 1.5f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "Heart Scale"
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isAnimated) 0f else 1f,
        animationSpec = tween(600, easing = LinearEasing),
        label = "Heart Alpha"
    )

    LaunchedEffect(Unit) {
        isAnimated = true
        delay(600)
        onFinish()
    }

    Icon(
        imageVector = Icons.Default.Favorite,
        contentDescription = null,
        tint = Color.Red,
        modifier = Modifier
            .absoluteOffset(x = xDp - 30.dp, y = yDp - 30.dp) // Offset to align tap center
            .size(60.dp)
            .scale(animatedScale)
            .alpha(animatedAlpha)
    )
}

@Composable
fun CommentsSheetContent(
    video: Video,
    viewModel: VideoViewModel,
    onClose: () -> Unit
) {
    val comments by viewModel.currentVideoComments.collectAsState()
    var commentText by remember { mutableStateOf("") }

    // Fetch comments for active video
    LaunchedEffect(video.id) {
        viewModel.setActiveVideo(video)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.6f)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Comments (${comments.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close Comments")
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

        // Comments List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (comments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No comments yet. Be the first to join the conversation!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(comments) { comment ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(comment.userAvatar.ifBlank { "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=120" }),
                            contentDescription = "User Avatar",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Column {
                            Text(
                                text = comment.userName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = comment.content,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

        // Write Comment Panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextField(
                value = commentText,
                onValueChange = { commentText = it },
                placeholder = { Text("Add a comment...", fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )
            IconButton(
                onClick = {
                    if (commentText.isNotBlank()) {
                        viewModel.addComment(video.id, "Me", commentText)
                        commentText = ""
                    }
                },
                enabled = commentText.isNotBlank(),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send Comment", modifier = Modifier.size(18.dp))
            }
        }
    }
}
