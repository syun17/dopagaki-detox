package com.dopagaki.detox.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dopagaki.detox.domain.Category
import com.dopagaki.detox.domain.Rarity
import com.dopagaki.detox.domain.day.DayKey
import com.dopagaki.detox.domain.day.DayState
import com.dopagaki.detox.domain.model.LaterItem
import com.dopagaki.detox.domain.model.Mission
import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.model.Session
import com.dopagaki.detox.domain.model.SessionResult
import com.dopagaki.detox.domain.model.SessionSource
import com.dopagaki.detox.domain.model.TimeTag
import com.dopagaki.detox.domain.model.Wallet
import com.dopagaki.detox.domain.pending.PendingChange
import com.dopagaki.detox.domain.pending.PendingPayload
import com.dopagaki.detox.domain.pending.PendingStatus
import com.dopagaki.detox.domain.time.AnchorSource
import com.dopagaki.detox.domain.time.ClockAnchor
import java.time.Instant

// 通常の DB（detox.db）：Mission、終わった Session、LaterItem、Wallet、フィード、ユーザー補助がオフだった時間
// 実行中の DB（runtime_state.db）：実行中の Session、PendingChange、ClockAnchor、10連の結果（バックアップから外す。9.4）

@Entity(tableName = "missions")
data class MissionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: String,
    val category: String,
    val durationMin: Int,
    val rarity: String,
    val weight: Int,
    val dailyLimit: Int?,
    /** パッケージ名を改行でつないだもの。 */
    val linkedApps: String,
    /** TimeTag をカンマでつないだもの。 */
    val timeTags: String,
    val enabled: Boolean,
    val lastDoneAt: Long?,
    val note: String,
    val sortOrder: Int,
) {
    fun toDomain() = Mission(
        id = id,
        title = title,
        kind = MissionKind.valueOf(kind),
        category = Category.valueOf(category),
        durationMin = durationMin,
        rarity = Rarity.valueOf(rarity),
        weight = weight,
        dailyLimit = dailyLimit,
        linkedApps = linkedApps.split('\n').filter { it.isNotEmpty() }.toSet(),
        timeTags = timeTags.split(',').filter { it.isNotEmpty() }.map { TimeTag.valueOf(it) }.toSet(),
        enabled = enabled,
        lastDoneAt = lastDoneAt?.let(Instant::ofEpochMilli),
        note = note,
    )

    companion object {
        fun from(m: Mission, order: Int) = MissionEntity(
            id = m.id,
            title = m.title,
            kind = m.kind.name,
            category = m.category.name,
            durationMin = m.durationMin,
            rarity = m.rarity.name,
            weight = m.weight,
            dailyLimit = m.dailyLimit,
            linkedApps = m.linkedApps.sorted().joinToString("\n"),
            timeTags = m.timeTags.sorted().joinToString(",") { it.name },
            enabled = m.enabled,
            lastDoneAt = m.lastDoneAt?.toEpochMilli(),
            note = m.note,
            sortOrder = order,
        )
    }
}

/** Session の列。終わった Session（detox.db の sessions）と実行中の Session（runtime_state.db の running_session）で同じ形を使う。 */
data class SessionColumns(
    val id: String,
    val missionId: String,
    val missionTitle: String,
    val kind: String,
    val rarity: String,
    val durationMin: Int,
    val source: String,
    val startedAt: Long,
    val durationMs: Long,
    val pausedMs: Long,
    val endsAt: Long,
    val result: String,
    val coinsEarned: Int,
    val coinsSpent: Int,
    val dayKey: String,
    val pauseStartedAt: Long?,
    val pauseCount: Int,
    val finishedAt: Long?,
) {
    fun toDomain() = Session(
        id = id,
        missionId = missionId,
        missionTitle = missionTitle,
        kind = MissionKind.valueOf(kind),
        rarity = Rarity.valueOf(rarity),
        durationMin = durationMin,
        source = SessionSource.valueOf(source),
        startedAt = Instant.ofEpochMilli(startedAt),
        durationMs = durationMs,
        pausedMs = pausedMs,
        endsAt = Instant.ofEpochMilli(endsAt),
        result = SessionResult.valueOf(result),
        coinsEarned = coinsEarned,
        coinsSpent = coinsSpent,
        dayKey = DayKey.parse(dayKey),
        pauseStartedAt = pauseStartedAt?.let(Instant::ofEpochMilli),
        pauseCount = pauseCount,
        finishedAt = finishedAt?.let(Instant::ofEpochMilli),
    )

    companion object {
        fun from(s: Session) = SessionColumns(
            s.id, s.missionId, s.missionTitle, s.kind.name, s.rarity.name, s.durationMin, s.source.name,
            s.startedAt.toEpochMilli(), s.durationMs, s.pausedMs, s.endsAt.toEpochMilli(), s.result.name,
            s.coinsEarned, s.coinsSpent, s.dayKey.toString(), s.pauseStartedAt?.toEpochMilli(), s.pauseCount,
            s.finishedAt?.toEpochMilli(),
        )
    }
}

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    @androidx.room.Embedded(prefix = "s_") val columns: SessionColumns,
)

@Entity(tableName = "running_session")
data class RunningSessionEntity(
    @PrimaryKey val slot: Int = 0,
    @androidx.room.Embedded(prefix = "s_") val columns: SessionColumns,
)

@Entity(tableName = "later_items")
data class LaterEntity(@PrimaryKey val missionId: String, val savedAt: Long) {
    fun toDomain() = LaterItem(missionId, Instant.ofEpochMilli(savedAt))
}

@Entity(tableName = "feed_items")
data class FeedEntity(@PrimaryKey val position: Int, val missionId: String)

@Entity(tableName = "wallet")
data class WalletEntity(
    @PrimaryKey val slot: Int = 0,
    val coins: Int,
    val streakDays: Int,
    val lastActiveDay: String?,
    val pityCount: Int,
    val pityReady: Boolean,
    /** 更新時刻（エポックミリ秒）をカンマでつないだもの。 */
    val refreshTimes: String,
    val feedRewardUsedDay: String?,
    val feedRewardUsedCount: Int,
    val currentDay: String?,
    val currentDaySwitchedAt: Long?,
) {
    fun toWallet() = Wallet(
        coins = coins,
        streakDays = streakDays,
        lastActiveDay = lastActiveDay?.let(DayKey::parse),
        pityCount = pityCount,
        pityReady = pityReady,
        refreshTimes = refreshTimes.split(',').filter { it.isNotEmpty() }.map { Instant.ofEpochMilli(it.toLong()) },
        feedRewardUsedDay = feedRewardUsedDay?.let(DayKey::parse),
        feedRewardUsedCount = feedRewardUsedCount,
    )

    fun toDayState(): DayState? =
        if (currentDay != null && currentDaySwitchedAt != null) DayState(DayKey.parse(currentDay), Instant.ofEpochMilli(currentDaySwitchedAt)) else null

    companion object {
        fun from(w: Wallet, day: DayState?) = WalletEntity(
            coins = w.coins,
            streakDays = w.streakDays,
            lastActiveDay = w.lastActiveDay?.toString(),
            pityCount = w.pityCount,
            pityReady = w.pityReady,
            refreshTimes = w.refreshTimes.joinToString(",") { it.toEpochMilli().toString() },
            feedRewardUsedDay = w.feedRewardUsedDay?.toString(),
            feedRewardUsedCount = w.feedRewardUsedCount,
            currentDay = day?.current?.toString(),
            currentDaySwitchedAt = day?.switchedAt?.toEpochMilli(),
        )
    }
}

/** ユーザー補助がオフになっていた時間（6.4「防げない抜け道」）。endedAt が null ならまだオフ。 */
@Entity(tableName = "accessibility_off")
data class AccessibilityOffEntity(@PrimaryKey val startedAt: Long, val endedAt: Long?)

@Entity(tableName = "pending_changes")
data class PendingEntity(
    @PrimaryKey val id: String,
    val target: String,
    val payload: String,
    val requestedAt: Long,
    val appliesAt: Long,
    val status: String,
) {
    fun toDomain() = PendingChange(
        id = id,
        payload = PendingPayload.decode(payload),
        requestedAt = Instant.ofEpochMilli(requestedAt),
        appliesAt = Instant.ofEpochMilli(appliesAt),
        status = PendingStatus.valueOf(status),
    )

    companion object {
        fun from(p: PendingChange) = PendingEntity(
            p.id, p.target.name, PendingPayload.encode(p.payload), p.requestedAt.toEpochMilli(),
            p.appliesAt.toEpochMilli(), p.status.name,
        )
    }
}

@Entity(tableName = "clock_anchor")
data class ClockAnchorEntity(
    @PrimaryKey val slot: Int = 0,
    val bootCount: Int,
    val elapsedAtAnchor: Long,
    val trustedAtAnchor: Long,
    val source: String,
    val lastSavedTrusted: Long,
    val lastSavedElapsed: Long,
) {
    fun toDomain() = ClockAnchor(
        bootCount, elapsedAtAnchor, Instant.ofEpochMilli(trustedAtAnchor), AnchorSource.valueOf(source),
        Instant.ofEpochMilli(lastSavedTrusted), lastSavedElapsed,
    )

    companion object {
        fun from(a: ClockAnchor) = ClockAnchorEntity(
            0, a.bootCount, a.elapsedAtAnchor, a.trustedAtAnchor.toEpochMilli(), a.source.name,
            a.lastSavedTrusted.toEpochMilli(), a.lastSavedElapsed,
        )
    }
}

/** 10連の結果（選ぶまで残す。5.3）。missionIds は改行でつないだもの。 */
@Entity(tableName = "gacha_ten")
data class GachaTenEntity(@PrimaryKey val slot: Int = 0, val missionIds: String, val drawnAt: Long)
