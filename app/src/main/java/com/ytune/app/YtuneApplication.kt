package com.ytune.app

import android.app.Application
import com.ytune.app.data.YtuneRepository
import com.ytune.app.data.local.UserPreferences
import com.ytune.app.data.local.YtuneDatabase
import com.ytune.app.player.PlaybackManager

class YtuneApplication : Application() {
    lateinit var database: YtuneDatabase
        private set
    lateinit var preferences: UserPreferences
        private set
    lateinit var repository: YtuneRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = YtuneDatabase.create(this)
        preferences = UserPreferences(this)
        repository = YtuneRepository(this, database.libraryDao(), preferences)
        PlaybackManager.initialize(this)
    }
}
