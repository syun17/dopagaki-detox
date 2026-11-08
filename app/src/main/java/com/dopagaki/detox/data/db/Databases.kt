package com.dopagaki.detox.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface MainDao {
    @Query("SELECT * FROM missions ORDER BY sortOrder") suspend fun missions(): List<MissionEntity>
    @Upsert suspend fun upsertMissions(items: List<MissionEntity>)
    @Query("DELETE FROM missions WHERE id NOT IN (:keep)") suspend fun deleteMissionsExcept(keep: List<String>)
    @Query("DELETE FROM missions") suspend fun deleteAllMissions()

    @Transaction
    suspend fun replaceMissions(items: List<MissionEntity>) {
        if (items.isEmpty()) deleteAllMissions() else deleteMissionsExcept(items.map { it.id })
        upsertMissions(items)
    }

    @Query("SELECT * FROM sessions ORDER BY s_startedAt") suspend fun sessions(): List<SessionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSession(item: SessionEntity)
    @Query("DELETE FROM sessions") suspend fun deleteAllSessions()

    @Query("SELECT * FROM later_items ORDER BY savedAt DESC") suspend fun later(): List<LaterEntity>
    @Query("DELETE FROM later_items") suspend fun deleteAllLater()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLater(items: List<LaterEntity>)

    @Transaction
    suspend fun replaceLater(items: List<LaterEntity>) { deleteAllLater(); insertLater(items) }

    @Query("SELECT * FROM feed_items ORDER BY position") suspend fun feed(): List<FeedEntity>
    @Query("DELETE FROM feed_items") suspend fun deleteFeed()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFeed(items: List<FeedEntity>)

    @Transaction
    suspend fun replaceFeed(items: List<FeedEntity>) { deleteFeed(); insertFeed(items) }

    @Query("SELECT * FROM wallet WHERE slot = 0") suspend fun wallet(): WalletEntity?
    @Upsert suspend fun upsertWallet(item: WalletEntity)

    @Query("SELECT * FROM accessibility_off ORDER BY startedAt") suspend fun accessibilityOff(): List<AccessibilityOffEntity>
    @Upsert suspend fun upsertAccessibilityOff(item: AccessibilityOffEntity)
}

@Dao
interface RuntimeDao {
    @Query("SELECT * FROM running_session WHERE slot = 0") suspend fun running(): RunningSessionEntity?
    @Upsert suspend fun upsertRunning(item: RunningSessionEntity)
    @Query("DELETE FROM running_session") suspend fun deleteRunning()

    @Query("SELECT * FROM pending_changes ORDER BY requestedAt") suspend fun pending(): List<PendingEntity>
    @Upsert suspend fun upsertPending(items: List<PendingEntity>)

    @Query("SELECT * FROM clock_anchor WHERE slot = 0") suspend fun anchor(): ClockAnchorEntity?
    @Upsert suspend fun upsertAnchor(item: ClockAnchorEntity)

    @Query("SELECT * FROM gacha_ten WHERE slot = 0") suspend fun gachaTen(): GachaTenEntity?
    @Upsert suspend fun upsertGachaTen(item: GachaTenEntity)
    @Query("DELETE FROM gacha_ten") suspend fun deleteGachaTen()
}

@Database(
    entities = [
        MissionEntity::class, SessionEntity::class, LaterEntity::class, FeedEntity::class,
        WalletEntity::class, AccessibilityOffEntity::class,
    ],
    version = 1,
)
abstract class MainDatabase : RoomDatabase() {
    abstract fun dao(): MainDao

    companion object {
        const val NAME = "detox.db"
        fun create(context: Context): MainDatabase =
            Room.databaseBuilder(context, MainDatabase::class.java, NAME).build()
    }
}

/** 実行中の状態。バックアップから外す（data_extraction_rules.xml・backup_rules.xml。設計書 9.4）。 */
@Database(
    entities = [RunningSessionEntity::class, PendingEntity::class, ClockAnchorEntity::class, GachaTenEntity::class],
    version = 1,
)
abstract class RuntimeDatabase : RoomDatabase() {
    abstract fun dao(): RuntimeDao

    companion object {
        /** backup_rules.xml・data_extraction_rules.xml の除外と同じ名前にする。 */
        const val NAME = "runtime_state.db"
        fun create(context: Context): RuntimeDatabase =
            Room.databaseBuilder(context, RuntimeDatabase::class.java, NAME).build()
    }
}
