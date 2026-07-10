package com.example

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.AppDatabase
import com.example.models.Video
import com.example.presentation.AuthViewModel
import com.example.presentation.VideoViewModel
import com.example.presentation.screens.*
import com.example.repository.AuthRepository
import com.example.repository.VideoRepository
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Core Database & Repo Setup
        val database = AppDatabase.getDatabase(applicationContext)
        val authRepository = AuthRepository(applicationContext)
        val videoRepository = VideoRepository(
            context = applicationContext,
            watchHistoryDao = database.watchHistoryDao(),
            savedVideoDao = database.savedVideoDao(),
            offlineVideoDao = database.offlineVideoDao(),
            playlistDao = database.playlistDao()
        )

        // ViewModel instances
        val authViewModel = AuthViewModel(authRepository)
        val videoViewModel = VideoViewModel(videoRepository)

        setContent {
            MyApplicationTheme {
                val currentUser by authViewModel.currentUser.collectAsState()
                val activeVideo by videoViewModel.activeVideo.collectAsState()

                if (currentUser == null) {
                    AuthScreen(authViewModel = authViewModel)
                } else {
                    MainAppShell(
                        authViewModel = authViewModel,
                        videoViewModel = videoViewModel,
                        activeVideo = activeVideo,
                        onToggleLandscape = { toggleOrientation() }
                    )
                }
            }
        }
    }

    private fun toggleOrientation() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}

@Composable
fun MainAppShell(
    authViewModel: AuthViewModel,
    videoViewModel: VideoViewModel,
    activeVideo: Video?,
    onToggleLandscape: () -> Unit
) {
    var selectedTab by remember { mutableStateOf("home") }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Full screen player layout for landscape orientation
    if (isLandscape && activeVideo != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            VideoDetailScreen(
                authViewModel = authViewModel,
                videoViewModel = videoViewModel,
                modifier = Modifier.fillMaxSize()
            )

            // Landscape close button overlay
            IconButton(
                onClick = { videoViewModel.setActiveVideo(null) },
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }
        return
    }

    Scaffold(
        bottomBar = {
            // Only show bottom navigation when NOT in full-screen detail mode
            if (activeVideo == null) {
                NavigationBar(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("bottom_nav_bar")
                ) {
                    NavigationBarItem(
                        selected = selectedTab == "home",
                        onClick = { selectedTab = "home" },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_home")
                    )
                    NavigationBarItem(
                        selected = selectedTab == "shorts",
                        onClick = { selectedTab = "shorts" },
                        icon = { Icon(Icons.Default.PlayCircle, contentDescription = "Shorts") },
                        label = { Text("Shorts", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_shorts")
                    )
                    NavigationBarItem(
                        selected = selectedTab == "studio",
                        onClick = { selectedTab = "studio" },
                        icon = { Icon(Icons.Default.VideoCall, contentDescription = "Studio") },
                        label = { Text("Studio", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_studio")
                    )
                    NavigationBarItem(
                        selected = selectedTab == "admin",
                        onClick = { selectedTab = "admin" },
                        icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin") },
                        label = { Text("Admin", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_admin")
                    )
                    NavigationBarItem(
                        selected = selectedTab == "profile",
                        onClick = { selectedTab = "profile" },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                        label = { Text("Profile", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_profile")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main navigation contents
            when (selectedTab) {
                "home" -> HomeScreen(
                    viewModel = videoViewModel,
                    onNavigateToVideo = { videoViewModel.setActiveVideo(it) }
                )
                "shorts" -> ShortsScreen(
                    viewModel = videoViewModel
                )
                "studio" -> CreatorStudioScreen(
                    viewModel = videoViewModel
                )
                "admin" -> AdminScreen(
                    viewModel = videoViewModel
                )
                "profile" -> ProfileScreen(
                    authViewModel = authViewModel,
                    videoViewModel = videoViewModel,
                    onNavigateToVideo = { videoViewModel.setActiveVideo(it) }
                )
            }

            // Animated slide-up detail screen player overlay
            AnimatedVisibility(
                visible = activeVideo != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                if (activeVideo != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        // Toolbar inside detail screen overlay
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { videoViewModel.setActiveVideo(null) },
                                modifier = Modifier.testTag("close_detail_button")
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }

                            Text(
                                text = "Streaming Now",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            IconButton(onClick = onToggleLandscape) {
                                Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate")
                            }
                        }

                        // Detail screen content
                        VideoDetailScreen(
                            authViewModel = authViewModel,
                            videoViewModel = videoViewModel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
