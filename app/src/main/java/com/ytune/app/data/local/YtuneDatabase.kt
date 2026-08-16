package com.ytune.app.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val artists: String,
    val artist: String?,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Long?,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites", foreignKeys = [ForeignKey(TrackEntity::class, ["videoId"], ["videoId"], onDelete = ForeignKey.CASCADE)])
data class FavoriteEntity(@PrimaryKey val videoId: String, val addedAt: Long = System.currentTimeMillis())

@Entity(tableName = "history", indices = [Index("videoId")])
data class HistoryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val videoId: String, val playedAt: Long = System.currentTimeMillis())

@Entity(tableName = "recent_discoveries", foreignKeys = [ForeignKey(TrackEntity::class, ["videoId"], ["videoId"], onDelete = ForeignKey.CASCADE)])
data class RecentDiscoveryEntity(@PrimaryKey val videoId: String, val discoveredAt: Long = System.currentTimeMillis())

@Entity(tableName = "queue", primaryKeys = ["position"])
data class QueueEntity(val position: Int, val videoId: String)

@Entity(tableName = "playlists")
data class LocalPlaylistEntity(@PrimaryKey val id: String, val name: String, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis())

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "videoId"],
    indices = [Index("playlistId"), Index("videoId")],
    foreignKeys = [
        ForeignKey(LocalPlaylistEntity::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TrackEntity::class, ["videoId"], ["videoId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class LocalPlaylistTrackEntity(val playlistId: String, val videoId: String, val position: Int)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val videoId: String,
    val title: String = "",
    val artists: String = "",
    val artworkUrl: String? = null,
    val state: Int,
    val percent: Float = 0f,
    val bytesDownloaded: Long = 0,
    val error: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

data class FavoriteTrack(@Embedded val favorite: FavoriteEntity, @Relation(parentColumn = "videoId", entityColumn = "videoId") val track: TrackEntity)
data class HistoryTrack(@Embedded val history: HistoryEntity, @Relation(parentColumn = "videoId", entityColumn = "videoId") val track: TrackEntity?)
data class RecentDiscoveryTrack(@Embedded val discovery: RecentDiscoveryEntity, @Relation(parentColumn = "videoId", entityColumn = "videoId") val track: TrackEntity)
data class QueueTrack(@Embedded val queue: QueueEntity, @Relation(parentColumn = "videoId", entityColumn = "videoId") val track: TrackEntity?)

@Dao
interface LibraryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTrack(track: TrackEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTracks(tracks: List<TrackEntity>)
    @Query("SELECT * FROM tracks WHERE videoId = :id") suspend fun track(id: String): TrackEntity?

    @Transaction @Query("SELECT * FROM favorites ORDER BY addedAt DESC") fun favorites(): Flow<List<FavoriteTrack>>
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE videoId = :id)") suspend fun isFavorite(id: String): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun favorite(value: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE videoId = :id") suspend fun unfavorite(id: String)

    @Insert suspend fun addHistory(value: HistoryEntity)
    @Transaction @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT :limit") fun history(limit: Int = 100): Flow<List<HistoryTrack>>
    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY playedAt DESC LIMIT 500)") suspend fun trimHistory()

    @Transaction @Query("SELECT * FROM recent_discoveries ORDER BY discoveredAt DESC LIMIT 30") fun recentDiscoveries(): Flow<List<RecentDiscoveryTrack>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addRecentDiscoveries(values: List<RecentDiscoveryEntity>)
    @Query("DELETE FROM recent_discoveries WHERE videoId NOT IN (SELECT videoId FROM recent_discoveries ORDER BY discoveredAt DESC LIMIT 30)") suspend fun trimRecentDiscoveries()

    @Query("DELETE FROM queue") suspend fun clearQueue()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertQueue(values: List<QueueEntity>)
    @Transaction @Query("SELECT * FROM queue ORDER BY position") suspend fun queue(): List<QueueTrack>

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC") fun playlists(): Flow<List<LocalPlaylistEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPlaylist(value: LocalPlaylistEntity)
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylist(id: String)
    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId") suspend fun playlistSize(playlistId: String): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addPlaylistTrack(value: LocalPlaylistTrackEntity)
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND videoId = :videoId") suspend fun removePlaylistTrack(playlistId: String, videoId: String)
    @Query("SELECT tracks.* FROM tracks INNER JOIN playlist_tracks ON tracks.videoId = playlist_tracks.videoId WHERE playlist_tracks.playlistId = :playlistId ORDER BY playlist_tracks.position") fun playlistTracks(playlistId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC") fun downloads(): Flow<List<DownloadEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDownload(value: DownloadEntity)
    @Query("DELETE FROM downloads WHERE videoId = :id") suspend fun removeDownload(id: String)
}

@Database(
    entities = [TrackEntity::class, FavoriteEntity::class, HistoryEntity::class, RecentDiscoveryEntity::class, QueueEntity::class, LocalPlaylistEntity::class, LocalPlaylistTrackEntity::class, DownloadEntity::class],
    version = 2,
    exportSchema = true
)
abstract class YtuneDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `recent_discoveries` (`videoId` TEXT NOT NULL, `discoveredAt` INTEGER NOT NULL, PRIMARY KEY(`videoId`), FOREIGN KEY(`videoId`) REFERENCES `tracks`(`videoId`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }
        fun create(context: Context): YtuneDatabase = Room.databaseBuilder(context, YtuneDatabase::class.java, "ytune.db").addMigrations(MIGRATION_1_2).build()
    }
}
