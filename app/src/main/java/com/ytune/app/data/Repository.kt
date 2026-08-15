package com.ytune.app.data

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

object YtuneRepository { const val baseUrl = "http://103.30.211.180:8000/"; private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }; private val api: YtuneApi by lazy { val log = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }; val client = OkHttpClient.Builder().addInterceptor(log).build(); Retrofit.Builder().baseUrl(baseUrl).client(client).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(YtuneApi::class.java) }; suspend fun search(q: String) = api.search(q); suspend fun track(id: String) = api.track(id); suspend fun stream(id: String) = api.stream(id); suspend fun lyrics(id: String) = api.lyrics(id); suspend fun refreshLyrics(id: String) = api.refreshLyrics(id); suspend fun playlist(id: String) = api.playlist(id); suspend fun health() = api.health(); suspend fun ytdlp() = api.ytdlp() }
