package com.dopagaki.detox.domain.model

import com.dopagaki.detox.domain.Category
import com.dopagaki.detox.domain.Rarity
import com.dopagaki.detox.domain.Rules
import com.dopagaki.detox.domain.SettingsDefaults
import com.dopagaki.detox.domain.day.DayKey
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/** Mission.kind（設計書 8章）。やること／ご褒美。 */
enum class MissionKind { TASK, REWARD }

/** 時間帯タグ（8章 Mission.timeTags）。朝 4:00〜10:59、昼 11:00〜17:59、夜 18:00〜3:59。 */
enum class TimeTag {
    MORNING, DAY, NIGHT;

    companion object {
        private val DAY_START: LocalTime = LocalTime.of(Rules.TIME_TAG_DAY_START_HOUR, 0)
        private val NIGHT_START: LocalTime = LocalTime.of(Rules.TIME_TAG_NIGHT_START_HOUR, 0)
        private val MORNING_START: LocalTime = LocalTime.of(SettingsDefaults.DAY_START_HOUR, 0)

        fun of(time: LocalTime): TimeTag = when {
            time >= MORNING_START && time < DAY_START -> MORNING
            time >= DAY_START && time < NIGHT_START -> DAY
            else -> NIGHT
        }
    }
}

/** やること・ご褒美のマスタ（8章 Mission）。 */
data class Mission(
    val id: String,
    val title: String,
    val kind: MissionKind,
    val category: Category,
    val durationMin: Int,
    val rarity: Rarity,
    val weight: Int,
    /** ご褒美のガチャでの1日上限。やることは null。 */
    val dailyLimit: Int?,
    /** ご褒美の対象アプリのパッケージ名。やることは空。 */
    val linkedApps: Set<String>,
    val timeTags: Set<TimeTag>,
    val enabled: Boolean,
    val lastDoneAt: Instant?,
    val note: String = "",
) {
    val isReward: Boolean get() = kind == MissionKind.REWARD
    val duration: Duration get() = Duration.ofMinutes(durationMin.toLong())
}

/** Session.source（8章）。 */
enum class SessionSource {
    HOME, SWIPE, LATER, GACHA_SINGLE, GACHA_TEN;

    val isGacha: Boolean get() = this == GACHA_SINGLE || this == GACHA_TEN
}

/** Session.result（8章）。DONE＝できた、PARTIAL＝一部、FAILED＝できなかった、ABORTED＝強制解除。 */
enum class SessionResult { RUNNING, DONE, PARTIAL, FAILED, ABORTED }

/**
 * 実行履歴（8章 Session）。
 * kind・rarity・durationMin・title はコインと天井の計算、履歴の表示のために確定時のミッションの値を写して持つ。
 * pauseStartedAt・pauseCount は実行中だけ使う（一時停止。5.4）。
 */
data class Session(
    val id: String,
    val missionId: String,
    val missionTitle: String,
    val kind: MissionKind,
    val rarity: Rarity,
    val durationMin: Int,
    val source: SessionSource,
    val startedAt: Instant,
    val durationMs: Long,
    val pausedMs: Long,
    val endsAt: Instant,
    val result: SessionResult,
    val coinsEarned: Int,
    val coinsSpent: Int,
    val dayKey: DayKey,
    val pauseStartedAt: Instant? = null,
    val pauseCount: Int = 0,
    val finishedAt: Instant? = null,
) {
    val isRunning: Boolean get() = result == SessionResult.RUNNING
    val isPaused: Boolean get() = pauseStartedAt != null
}

/** あとで（8章 LaterItem）。 */
data class LaterItem(val missionId: String, val savedAt: Instant)

/** コイン・記録（8章 Wallet）。 */
data class Wallet(
    val coins: Int = 0,
    val streakDays: Int = 0,
    val lastActiveDay: DayKey? = null,
    val pityCount: Int = 0,
    val pityReady: Boolean = false,
    /** 直近60分の更新時刻（最大3件）。 */
    val refreshTimes: List<Instant> = emptyList(),
    val feedRewardUsedDay: DayKey? = null,
    val feedRewardUsedCount: Int = 0,
)

/** 設定（8章 Settings）。MVP で固定する値は SettingsDefaults から直接読む（5.5）。 */
data class Settings(
    val rewardAppLock: Boolean = SettingsDefaults.REWARD_APP_LOCK,
    val allowForceUnlock: Boolean = SettingsDefaults.ALLOW_FORCE_UNLOCK,
    val allowPause: Boolean = SettingsDefaults.ALLOW_PAUSE,
    val quietEnabled: Boolean = SettingsDefaults.QUIET_ENABLED,
    val quietStart: LocalTime = SettingsDefaults.QUIET_START,
    val quietEnd: LocalTime = SettingsDefaults.QUIET_END,
    val dailyGoalMin: Int = SettingsDefaults.DAILY_GOAL_MIN,
    val feedRewardDailyLimit: Int = SettingsDefaults.FEED_REWARD_DAILY_LIMIT,
)
