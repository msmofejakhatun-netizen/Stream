package com.example.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.models.Video
import com.example.presentation.VideoViewModel

@Composable
fun CreatorStudioScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val uploadedVideos by viewModel.uploadedVideos.collectAsState()
    val uploadState by viewModel.studioUploadingState.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()

    var showUploadDialog by remember { mutableStateOf(false) }
    var inputTitle by remember { mutableStateOf("") }
    var inputDesc by remember { mutableStateOf("") }
    var inputCategory by remember { mutableStateOf("Education") }
    var inputDuration by remember { mutableStateOf("05:30") }

    val categories = listOf("Education", "Gaming", "Music", "News", "Live")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Welcome Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Creator Studio",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Manage your channel and track earnings",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                Button(
                    onClick = { showUploadDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("upload_video_button")
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload")
                }
            }
        }

        // Uploading Progress Indicators
        if (uploadState != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = if (uploadState == "uploading") "Uploading video chunks ($uploadProgress%)..." else if (uploadState == "done") "Upload completed successfully!" else if (uploadState == "error") "Upload failed!" else "Transcoding HLS adaptive bitrate streams...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (uploadState == "uploading") "Sending binary payload via HTTP multipart stream" else "FFmpeg multi-resolution video transcoding queue active",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        // Channel Dashboard metrics Grid
        item {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Channel Analytics",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnalyticsMetricCard(
                        title = "Subscribers",
                        value = "1,292",
                        increment = "+42 this month",
                        icon = Icons.Default.Group,
                        modifier = Modifier.weight(1f)
                    )
                    AnalyticsMetricCard(
                        title = "Est. Earnings",
                        value = "$1,450.50",
                        increment = "+$120.45 this week",
                        icon = Icons.Default.MonetizationOn,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnalyticsMetricCard(
                        title = "Monthly Views",
                        value = "48.9K",
                        increment = "+12% vs last month",
                        icon = Icons.Default.ShowChart,
                        modifier = Modifier.weight(1f)
                    )
                    AnalyticsMetricCard(
                        title = "Watch Time (Hrs)",
                        value = "1.2K",
                        increment = "85% retention score",
                        icon = Icons.Default.Timer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Uploaded videos listing
        item {
            Text(
                "My Uploads & Scheduled Videos",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        if (uploadedVideos.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.VideoCall,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "You haven't uploaded any videos yet.",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            items(uploadedVideos) { video ->
                StudioVideoListItem(video = video)
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Modal dialog to trigger upload simulation
    if (showUploadDialog) {
        AlertDialog(
            onDismissRequest = { showUploadDialog = false },
            title = { Text("Upload New Video") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = inputTitle,
                        onValueChange = { inputTitle = it },
                        label = { Text("Video Title") },
                        modifier = Modifier.fillMaxWidth().testTag("upload_title_field")
                    )

                    OutlinedTextField(
                        value = inputDesc,
                        onValueChange = { inputDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = inputDuration,
                            onValueChange = { inputDuration = it },
                            label = { Text("Duration (MM:SS)") },
                            modifier = Modifier.weight(1f)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Category", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            ScrollableTabRow(
                                selectedTabIndex = categories.indexOf(inputCategory).coerceAtLeast(0),
                                divider = {},
                                indicator = {}
                            ) {
                                categories.forEach { cat ->
                                    Tab(
                                        selected = cat == inputCategory,
                                        onClick = { inputCategory = cat }
                                    ) {
                                        Text(cat, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputTitle.isNotBlank()) {
                            viewModel.uploadSimulatedVideo(inputTitle, inputDesc, inputCategory, inputDuration)
                            showUploadDialog = false
                            inputTitle = ""
                            inputDesc = ""
                        }
                    },
                    modifier = Modifier.testTag("upload_submit_button")
                ) {
                    Text("Start Upload")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUploadDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AnalyticsMetricCard(
    title: String,
    value: String,
    increment: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(increment, fontSize = 10.sp, color = Color.Green, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StudioVideoListItem(video: Video) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 100.dp, height = 60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(video.thumbnailUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(video.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("Category: ${video.category} • Scheduled: 2026-07-10", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text("DRAFT", fontSize = 9.sp) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = Color.Yellow)
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text("HLS-1080p", fontSize = 9.sp) }
                    )
                }
            }

            Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
