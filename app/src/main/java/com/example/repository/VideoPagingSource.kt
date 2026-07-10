package com.example.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.models.Video
import com.example.network.StreamPlayRetrofitClient

class VideoPagingSource(
    private val category: String?,
    private val query: String? = null
) : PagingSource<Int, Video>() {

    override fun getRefreshKey(state: PagingState<Int, Video>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Video> {
        return try {
            val position = params.key ?: 1
            val allVideos = StreamPlayRetrofitClient.service.getVideos(
                category = category,
                query = query,
                isShort = false
            )
            
            val pageSize = params.loadSize
            val startIndex = (position - 1) * pageSize
            val endIndex = minOf(startIndex + pageSize, allVideos.size)

            val pagedData = if (startIndex < allVideos.size) {
                allVideos.subList(startIndex, endIndex)
            } else {
                emptyList()
            }

            LoadResult.Page(
                data = pagedData,
                prevKey = if (position == 1) null else position - 1,
                nextKey = if (endIndex >= allVideos.size) null else position + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
