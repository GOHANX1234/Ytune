package com.ytune.app.player

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object ArtworkCache {
    fun localUri(context: Context, videoId: String): Uri? = artworkFile(context, videoId)
        .takeIf { it.isFile && it.length() > 0L }
        ?.let(Uri::fromFile)

    fun displayUri(context: Context, videoId: String, remoteUrl: String?): Uri? =
        localUri(context, videoId) ?: remoteUrl?.let(Uri::parse)

    suspend fun ensureCached(context: Context, videoId: String, remoteUrl: String?, client: OkHttpClient): Uri? = withContext(Dispatchers.IO) {
        localUri(context, videoId)?.let { return@withContext it }
        if (remoteUrl.isNullOrBlank()) return@withContext null

        val destination = artworkFile(context, videoId)
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        val request = Request.Builder().url(remoteUrl).header("Accept", "image/*").build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val body = response.body ?: return@use
                temporary.outputStream().buffered().use { output -> body.byteStream().use { it.copyTo(output) } }
                if (temporary.length() > 0L) {
                    if (!temporary.renameTo(destination)) {
                        temporary.copyTo(destination, overwrite = true)
                        temporary.delete()
                    }
                }
            }
        }

        localUri(context, videoId)
    }

    private fun artworkFile(context: Context, videoId: String): File =
        File(File(context.filesDir, "artwork"), videoId)
}
