package com.ytune.app.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DownloadManager
import com.ytune.app.YtuneApplication
import java.io.File
import java.util.concurrent.Executors

@UnstableApi
object PlaybackManager {
    private var initialized = false
    lateinit var cache: SimpleCache
        private set
    lateinit var downloadManager: DownloadManager
        private set
    lateinit var upstreamFactory: OkHttpDataSource.Factory
        private set
    lateinit var cacheFactory: CacheDataSource.Factory
        private set

    fun initialize(context: Context) {
        if (initialized) return
        val app = context.applicationContext as YtuneApplication
        upstreamFactory = OkHttpDataSource.Factory(app.repository.httpClient())
            .setUserAgent("Ytune/${com.ytune.app.BuildConfig.VERSION_NAME}")
        cache = SimpleCache(File(context.filesDir, "media"), NoOpCacheEvictor(), StandaloneDatabaseProvider(context))
        cacheFactory = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstreamFactory)
        downloadManager = DownloadManager(context, StandaloneDatabaseProvider(context), cache, upstreamFactory, Executors.newFixedThreadPool(3)).apply {
            maxParallelDownloads = 2
        }
        initialized = true
    }
}
