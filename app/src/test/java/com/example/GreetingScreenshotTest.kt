package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.models.Video
import com.example.presentation.screens.MiniVideoRowItem
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun video_item_screenshot() {
    val sampleVideo = Video(
        id = "vid_1",
        title = "Introduction to Jetpack Compose: Building Beautiful Interfaces",
        description = "Learn how to build modern Android UIs with Jetpack Compose.",
        videoUrl = "https://example.com/video.mp4",
        thumbnailUrl = "https://images.unsplash.com/photo-1607799279861-4dd421887fb3?auto=format&fit=crop&w=640&q=80",
        creatorId = "creator_android_dev",
        creatorName = "Android Developer Academy",
        creatorAvatar = "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&w=120&q=80",
        views = 124500,
        likes = 8900,
        dislikes = 120,
        duration = "09:56",
        uploadDate = "2 days ago",
        category = "Education"
    )

    composeTestRule.setContent { 
        MyApplicationTheme { 
            MiniVideoRowItem(
                video = sampleVideo,
                onClick = {}
            )
        } 
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/video_item.png")
  }
}
