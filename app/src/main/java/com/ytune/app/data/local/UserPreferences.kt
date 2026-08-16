package com.ytune.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore("ytune_preferences")

data class PlaybackPreferences(
    val quality: String = "best",
    val currentVideoId: String? = null,
    val positionMs: Long = 0,
    val currentIndex: Int = 0,
    val repeatMode: Int = 0,
    val shuffle: Boolean = false,
    val downloadWifiOnly: Boolean = true
)

class UserPreferences(private val context: Context) {
    private object Keys {
        val deviceId = stringPreferencesKey("device_id")
        val quality = stringPreferencesKey("quality")
        val currentVideoId = stringPreferencesKey("current_video_id")
        val positionMs = longPreferencesKey("position_ms")
        val currentIndex = intPreferencesKey("current_index")
        val repeatMode = intPreferencesKey("repeat_mode")
        val shuffle = booleanPreferencesKey("shuffle")
        val wifiOnly = booleanPreferencesKey("download_wifi_only")
    }

    val playback: Flow<PlaybackPreferences> = context.dataStore.data.map { values ->
        PlaybackPreferences(
            quality = values[Keys.quality] ?: "best",
            currentVideoId = values[Keys.currentVideoId],
            positionMs = values[Keys.positionMs] ?: 0,
            currentIndex = values[Keys.currentIndex] ?: 0,
            repeatMode = values[Keys.repeatMode] ?: 0,
            shuffle = values[Keys.shuffle] ?: false,
            downloadWifiOnly = values[Keys.wifiOnly] ?: true
        )
    }

    suspend fun deviceId(): String {
        var result: String? = null
        context.dataStore.edit { values ->
            result = values[Keys.deviceId] ?: UUID.randomUUID().toString().also { values[Keys.deviceId] = it }
        }
        return requireNotNull(result)
    }

    suspend fun savePlayback(videoId: String?, positionMs: Long, index: Int, repeatMode: Int, shuffle: Boolean) {
        context.dataStore.edit { values ->
            if (videoId == null) values.remove(Keys.currentVideoId) else values[Keys.currentVideoId] = videoId
            values[Keys.positionMs] = positionMs
            values[Keys.currentIndex] = index
            values[Keys.repeatMode] = repeatMode
            values[Keys.shuffle] = shuffle
        }
    }

    suspend fun setQuality(value: String) { context.dataStore.edit { it[Keys.quality] = value } }
    suspend fun setWifiOnly(value: Boolean) { context.dataStore.edit { it[Keys.wifiOnly] = value } }
}
