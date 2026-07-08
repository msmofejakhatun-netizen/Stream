package com.example.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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

@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    videoViewModel: VideoViewModel,
    onNavigateToVideo: (Video) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentUser by authViewModel.currentUser.collectAsState()
    val watchHistory by videoViewModel.watchHistory.collectAsState()
    val watchLaterVideos by videoViewModel.watchLaterVideos.collectAsState()
    val offlineVideos by videoViewModel.offlineVideos.collectAsState()
    val playlists by videoViewModel.playlists.collectAsState()

    var activeTabIdx by remember { mutableStateOf(0) }
    val tabLabels = listOf("Playlists", "Watch Later", "History", "Downloads")

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }
    var playlistIsPrivateInput by remember { mutableStateOf(true) }

    currentUser?.let { user ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // User Header Profile Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(user.avatarUrl),
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.displayName, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                            Text(user.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(if (user.isGuest) "Guest Mode" else "Premium Sync", fontSize = 10.sp) }
                                )
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("Joined: ${user.joinedDate}", fontSize = 10.sp) }
                                )
                            }
                        }

                        IconButton(
                            onClick = { authViewModel.logout() },
                            modifier = Modifier.testTag("logout_button")
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = "Log Out", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Tabs header
            item {
                TabRow(
                    selectedTabIndex = activeTabIdx,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[activeTabIdx]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    tabLabels.forEachIndexed { idx, label ->
                        Tab(
                            selected = idx == activeTabIdx,
                            onClick = { activeTabIdx = idx },
                            modifier = Modifier.testTag("profile_tab_$label")
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

            // Playlists Tab
            if (activeTabIdx == 0) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("My Custom Playlists", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        TextButton(
                            onClick = { showPlaylistDialog = true },
                            modifier = Modifier.testTag("create_playlist_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Create")
                        }
                    }
                }

                if (playlists.isEmpty()) {
                    item {
                        EmptyStatePlaceholder(text = "Create playlists to group reference videos together.")
                    }
                } else {
                    items(playlists) { playlist ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.FeaturedPlayList, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text(playlist.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(
                                            text = "${playlist.videoIds.size} videos • ${if (playlist.isPrivate) "Private" else "Public"}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                Row {
                                    IconButton(onClick = { videoViewModel.deletePlaylist(playlist.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Watch Later Tab
            if (activeTabIdx == 1) {
                if (watchLaterVideos.isEmpty()) {
                    item {
                        EmptyStatePlaceholder(text = "Save videos to watch later. Access them anytime offline or online.")
                    }
                } else {
                    items(watchLaterVideos) { saved ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val video = videoViewModel.getVideoById(saved.videoId)
                                    if (video != null) onNavigateToVideo(video)
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 80.dp, height = 48.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black)
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(saved.thumbnailUrl),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(saved.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${saved.creatorName} • ${saved.duration}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                }

                                IconButton(
                                    onClick = {
                                        val mockVid = Video(
                                            id = saved.videoId, title = saved.title, description = "",
                                            videoUrl = "", thumbnailUrl = saved.thumbnailUrl, creatorId = "",
                                            creatorName = saved.creatorName, creatorAvatar = "", views = 0, likes = 0,
                                            dislikes = 0, duration = saved.duration, uploadDate = "", category = saved.category
                                        )
                                        videoViewModel.toggleWatchLater(mockVid)
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // History Tab
            if (activeTabIdx == 2) {
                if (watchHistory.isEmpty()) {
                    item {
                        EmptyStatePlaceholder(text = "Your watch history is currently empty.")
                    }
                } else {
                    items(watchHistory) { history ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val video = videoViewModel.getVideoById(history.videoId)
                                    if (video != null) onNavigateToVideo(video)
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 80.dp, height = 48.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black)
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(history.thumbnailUrl),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(history.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${history.creatorName} • ${history.duration}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
            }

            // Downloads Tab
            if (activeTabIdx == 3) {
                if (offlineVideos.isEmpty()) {
                    item {
                        EmptyStatePlaceholder(text = "No offline videos saved. Use the download action inside any video detail.")
                    }
                } else {
                    items(offlineVideos) { downloaded ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 80.dp, height = 48.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black)
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(downloaded.thumbnailUrl),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(downloaded.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = if (downloaded.isCompleted) "Offline • ${downloaded.fileSize}"
                                        else "Downloading: ${downloaded.downloadProgress}%",
                                        fontSize = 11.sp,
                                        color = if (downloaded.isCompleted) Color.Green else MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(onClick = { videoViewModel.removeDownload(downloaded.videoId) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete File", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Playlist Dialog Creator
    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("Create Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = playlistNameInput,
                        onValueChange = { playlistNameInput = it },
                        label = { Text("Playlist Name") },
                        modifier = Modifier.fillMaxWidth().testTag("playlist_name_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Private Playlist", fontSize = 14.sp)
                        Switch(
                            checked = playlistIsPrivateInput,
                            onCheckedChange = { playlistIsPrivateInput = it },
                            modifier = Modifier.testTag("playlist_privacy_switch")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playlistNameInput.isNotBlank()) {
                            videoViewModel.createPlaylist(playlistNameInput, playlistIsPrivateInput)
                            showPlaylistDialog = false
                            playlistNameInput = ""
                        }
                    },
                    modifier = Modifier.testTag("playlist_submit_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EmptyStatePlaceholder(
    text: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Inbox,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = text,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}
