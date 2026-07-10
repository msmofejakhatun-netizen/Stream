package com.example.presentation.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.network.VideoCacheManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    videoUrl: String,
    playbackSpeed: Float = 1.0f,
    modifier: Modifier = Modifier,
    useController: Boolean = true,
    isMuted: Boolean = false,
    isPlaying: Boolean = true,
    onVideoFinished: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryAttempt by remember { mutableStateOf(0) }
    val maxRetries = 3

    var isPlayingState by remember { mutableStateOf(true) }
    var showPlayPauseIndicator by remember { mutableStateOf(false) }

    // Strict URL security validation check
    val isUrlValid = remember(videoUrl) { VideoCacheManager.validateUrl(videoUrl) }

    // Track selector for Adaptive Bitrate Streaming
    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setPreferredVideoMimeType("video/avc"))
        }
    }

    // MediaSource.Factory integrating our custom SimpleCache
    val mediaSourceFactory = remember {
        DefaultMediaSourceFactory(context)
            .setDataSourceFactory(VideoCacheManager.getCacheDataSourceFactory(context))
    }

    // Initialize ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .build().apply {
                playWhenReady = isPlaying
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    // Handle isPlaying changes reactively
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // Handle Speed Changes
    LaunchedEffect(playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    // Handle Mute Changes
    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    // Load media source with validation & automatic retry structure
    fun loadMedia() {
        if (!isUrlValid) {
            errorMessage = "Security Block: Video URL must come from official Railway backend server."
            isLoading = false
            return
        }

        isLoading = true
        errorMessage = null
        val mediaItem = MediaItem.fromUri(videoUrl)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (isPlaying) {
            exoPlayer.play()
        }
    }

    LaunchedEffect(videoUrl, isUrlValid) {
        retryAttempt = 0
        loadMedia()
    }

    // Track Player Events
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isLoading = playbackState == Player.STATE_BUFFERING
                isPlayingState = exoPlayer.isPlaying
                if (playbackState == Player.STATE_ENDED) {
                    onVideoFinished()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("VideoPlayer", "ExoPlayer Error: ${error.message}", error)
                if (retryAttempt < maxRetries) {
                    retryAttempt++
                    coroutineScope.launch {
                        errorMessage = "Playback failed. Retrying (Attempt $retryAttempt/$maxRetries)..."
                        delay(2000L * retryAttempt) // Exponential backoff
                        loadMedia()
                    }
                } else {
                    isLoading = false
                    errorMessage = "Failed to load video: ${error.localizedMessage ?: "Network error"}"
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Interactive Play/Pause on single tap
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                } else {
                    exoPlayer.play()
                }
                showPlayPauseIndicator = true
                coroutineScope.launch {
                    delay(800)
                    showPlayPauseIndicator = false
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    this.useController = useController
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM // Full bleed for Shorts
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.useController = useController
            },
            modifier = Modifier.fillMaxSize()
        )

        // Custom Sleek M3 Loading Spinner Overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(54.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
            }
        }

        // Custom Gorgeous Error Overlay with Security validation warnings & Retry action
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Playback Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = errorMessage ?: "Unknown Error",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.85f)
                    )
                    if (isUrlValid) {
                        Button(
                            onClick = {
                                retryAttempt = 0
                                loadMedia()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry Playback")
                        }
                    }
                }
            }
        }

        // Temporary Play/Pause Large Status Indicator (like YouTube Shorts / IG Reels)
        AnimatedVisibility(
            visible = showPlayPauseIndicator,
            enter = fadeIn() + scaleIn(initialScale = 0.5f),
            exit = fadeOut() + scaleOut(targetScale = 1.5f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlayingState) Icons.Default.PlayArrow else Icons.Default.PlayArrow, // Can show custom pause/play graphics
                    contentDescription = if (isPlayingState) "Playing" else "Paused",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}
