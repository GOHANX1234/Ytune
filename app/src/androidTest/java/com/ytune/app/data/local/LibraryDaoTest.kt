package com.ytune.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryDaoTest {
    private lateinit var database: YtuneDatabase
    private lateinit var dao: LibraryDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, YtuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.libraryDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun updatingTrackMetadataKeepsFavorite() = runBlocking {
        val videoId = "5NV6Rdv1a3I"
        dao.upsertTrack(TrackEntity(videoId, "Get Lucky", "Daft Punk", "Daft Punk", null, null, null))
        dao.favorite(FavoriteEntity(videoId))

        dao.upsertTrack(TrackEntity(videoId, "Get Lucky (Updated)", "Daft Punk", "Daft Punk", "Random Access Memories", null, 369_000))

        assertTrue(dao.isFavorite(videoId))
        assertEquals("Get Lucky (Updated)", dao.favorites().first().single().track.title)
    }
}
