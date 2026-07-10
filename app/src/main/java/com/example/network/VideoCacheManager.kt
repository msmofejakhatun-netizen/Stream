package com.example.network

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
object VideoCacheManager {
    private var cache: SimpleCache? = null
    private val activePreloadJobs = mutableMapOf<String, Job>()
    private val preloadScope = CoroutineScope(Dispatchers.IO)

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (cache == null) {
            val cacheDir = File(context.cacheDir, "exoplayer_video_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(150 * 1024 * 1024) // 150 MB LRU Cache
            val databaseProvider = StandaloneDatabaseProvider(context)
            cache = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return cache!!
    }

    fun getCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        return CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Preloads the first 2MB of a video URL asynchronously to enable instantaneous start times.
     */
    fun preloadVideo(context: Context, videoUrl: String) {
        if (videoUrl.isBlank()) return

        // Strict URL Validation: Only play/preload videos from the official Railway backend
        if (!validateUrl(videoUrl)) {
            android.util.Log.w("VideoCacheManager", "Strict URL Validation Blocked Preload: $videoUrl")
            return
        }

        synchronized(activePreloadJobs) {
            if (activePreloadJobs.containsKey(videoUrl)) {
                return // Already preloaded or preloading
            }

            val job = preloadScope.launch {
                try {
                    val cacheDataSource = getCacheDataSourceFactory(context).createDataSource()
                    val dataSpec = DataSpec(Uri.parse(videoUrl), 0, 2 * 1024 * 1024) // Preload first 2MB
                    val cacheWriter = CacheWriter(
                        cacheDataSource,
                        dataSpec,
                        null,
                        null
                    )
                    android.util.Log.d("VideoCacheManager", "Preloading stream: $videoUrl")
                    cacheWriter.cache()
                    android.util.Log.d("VideoCacheManager", "Preload complete for: $videoUrl")
                } catch (e: Exception) {
                    android.util.Log.e("VideoCacheManager", "Preload failed for: $videoUrl: ${e.message}")
                } finally {
                    synchronized(activePreloadJobs) {
                        activePreloadJobs.remove(videoUrl)
                    }
                }
            }
            activePreloadJobs[videoUrl] = job
        }
    }

    fun cancelPreload(videoUrl: String) {
        synchronized(activePreloadJobs) {
            activePreloadJobs[videoUrl]?.cancel()
            activePreloadJobs.remove(videoUrl)
        }
    }

    /**
     * Strictly validates that the video URL comes from the trusted Railway backend.
     */
    fun validateUrl(videoUrl: String): Boolean {
        val baseRailway = "https://stream-streamplay.up.railway.app"
        val baseRailwayHttp = "http://stream-streamplay.up.railway.app"
        return videoUrl.startsWith(baseRailway) || videoUrl.startsWith(baseRailwayHttp)
    }
}
