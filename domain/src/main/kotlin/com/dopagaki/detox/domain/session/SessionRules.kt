package com.dopagaki.detox.domain.session

import com.dopagaki.detox.domain.Rules
import com.dopagaki.detox.domain.coin.Coins
import com.dopagaki.detox.domain.day.DayKey
import com.dopagaki.detox.domain.model.Mission
import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.model.Session
import com.dopagaki.detox.domain.model.SessionResult
import com.dopagaki.detox.domain.model.SessionSource
import com.dopagaki.detox.domain.model.Settings
import com.dopagaki.detox.domain.model.Wallet
import com.dopagaki.detox.domain.pity.Pity
import com.dopagaki.detox.domain.quiet.QuietHours
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 「始める」を押せるか（5.1 ミッション詳細、5.2）。 */
sealed interface StartCheck {
    data class Ok(val cost: Int) : StartCheck
    data class NotEnoughCoins(val missing: Int) : StartCheck
    data object FeedSlotUsed : StartCheck
    data object QuietHours : StartCheck
    data object AfterReward : StartCheck
    data object AlreadyRunning : StartCheck
    data object Disabled : StartCheck
}

/** ホーム・スワイプ枠（7.4）。ホーム・スワイプ・あとでから始めるご褒美は1日1回まで。 */
object FeedSlot {
    fun used(wallet: Wallet, today: DayKey): Int =
        if (wallet.feedRewardUsedDay == today) wallet.feedRewardUsedCount else 0

    fun remaining(wallet: Wallet, today: DayKey, settings: Settings): Int =
        (settings.feedRewardDailyLimit - used(wallet, today)).coerceAtLeast(0)

    fun use(wallet: Wallet, today: DayKey): Wallet =
        wallet.copy(feedRewardUsedDay = today, feedRewardUsedCount = used(wallet, today) + 1)
}

/** ガチャの支払い（5.3、7.5）。 */
object GachaCost {
    fun cost(ten: Boolean): Int = if (ten) Rules.GACHA_TEN_COST else Rules.GACHA_SINGLE_COST

    fun missing(wallet: Wallet, ten: Boolean): Int = (cost(ten) - wallet.coins).coerceAtLeast(0)

    fun pay(wallet: Wallet, ten: Boolean): Wallet {
        require(missing(wallet, ten) == 0) { "コインが足りない" }
        return wallet.copy(coins = wallet.coins - cost(ten))
    }
}

/** 完了報告で付いたコインの内訳（S06）。 */
data class Settlement(val wallet: Wallet, val session: Session, val earned: Int, val streakBonus: Int)

/**
 * ミッションの確定・実行中・終了（設計書 5.4、6.1、6.3、7.5）。時刻はすべて信頼時刻。
 */
object SessionRules {

    fun checkStart(
        mission: Mission,
        source: SessionSource,
        wallet: Wallet,
        settings: Settings,
        now: Instant,
        zone: ZoneId,
        today: DayKey,
        running: Session?,
        previousKind: MissionKind?,
    ): StartCheck {
        if (running != null && running.isRunning) return StartCheck.AlreadyRunning
        if (!mission.enabled) return StartCheck.Disabled
        if (!mission.isReward) return StartCheck.Ok(0)
        // 静かな時間帯は、ガチャ（10連の結果から選ぶとき）も含めて始められない（7.3）
        if (QuietHours.isQuiet(settings, now, zone)) return StartCheck.QuietHours
        if (source.isGacha) return StartCheck.Ok(0)
        // 直前がご褒美のあいだは、ホーム・スワイプ・あとでのご褒美も始められない（7.3）
        if (previousKind == MissionKind.REWARD) return StartCheck.AfterReward
        if (FeedSlot.remaining(wallet, today, settings) == 0) return StartCheck.FeedSlotUsed
        val cost = Coins.startCost(mission.kind, mission.durationMin, mission.rarity, source)
        if (wallet.coins < cost) return StartCheck.NotEnoughCoins(cost - wallet.coins)
        return StartCheck.Ok(cost)
    }

    /** 確定する。checkStart が Ok のときだけ呼ぶ。ご褒美はここでコインを引き、ホーム・スワイプ枠を使う。 */
    fun start(
        id: String,
        mission: Mission,
        source: SessionSource,
        wallet: Wallet,
        now: Instant,
        today: DayKey,
    ): Pair<Session, Wallet> {
        val cost = Coins.startCost(mission.kind, mission.durationMin, mission.rarity, source)
        require(wallet.coins >= cost) { "コインが足りない" }
        val duration = mission.duration
        val session = Session(
            id = id,
            missionId = mission.id,
            missionTitle = mission.title,
            kind = mission.kind,
            rarity = mission.rarity,
            durationMin = mission.durationMin,
            source = source,
            startedAt = now,
            durationMs = duration.toMillis(),
            pausedMs = 0,
            endsAt = now.plus(duration),
            result = SessionResult.RUNNING,
            coinsEarned = 0,
            coinsSpent = cost,
            dayKey = today,
        )
        var w = wallet.copy(coins = wallet.coins - cost)
        if (mission.isReward && !source.isGacha) w = FeedSlot.use(w, today)
        return session to w
    }

    /** 一時停止が5分を過ぎていたら、5分の時点で自動で再開した状態にする。 */
    fun normalize(session: Session, now: Instant): Session {
        val pausedAt = session.pauseStartedAt ?: return session
        val autoResumeAt = pausedAt.plus(Rules.PAUSE_MAX)
        return if (now >= autoResumeAt) resume(session, autoResumeAt) else session
    }

    /** 残り時間。一時停止中は止まる。 */
    fun remaining(session: Session, now: Instant): Duration {
        val s = normalize(session, now)
        val ref = s.pauseStartedAt ?: now
        return Duration.between(ref, s.endsAt).coerceAtLeast(Duration.ZERO)
    }

    /** 時間が終わったか（完了報告へ進む）。 */
    fun isOver(session: Session, now: Instant): Boolean = remaining(session, now).isZero

    /** 一時停止の残り（一時停止中だけ）。 */
    fun pauseRemaining(session: Session, now: Instant): Duration? {
        val s = normalize(session, now)
        val pausedAt = s.pauseStartedAt ?: return null
        return Duration.between(now, pausedAt.plus(Rules.PAUSE_MAX)).coerceAtLeast(Duration.ZERO)
    }

    /** 一時停止できるか：設定で許可、1回まで、強制解除の待機中は不可。5.4 */
    fun canPause(session: Session, now: Instant, settings: Settings, unlockPending: Boolean): Boolean {
        val s = normalize(session, now)
        return settings.allowPause && s.isRunning && !s.isPaused && !unlockPending &&
            s.pauseCount < Rules.PAUSE_MAX_COUNT && !isOver(s, now)
    }

    fun pause(session: Session, now: Instant): Session =
        normalize(session, now).copy(pauseStartedAt = now, pauseCount = session.pauseCount + 1)

    /** 再開する。止めた分だけ endsAt を後ろへずらす（最大5分）。 */
    fun resume(session: Session, now: Instant): Session {
        val pausedAt = session.pauseStartedAt ?: return session
        val paused = Duration.between(pausedAt, now).coerceIn(Duration.ZERO, Rules.PAUSE_MAX)
        return session.copy(
            pauseStartedAt = null,
            pausedMs = session.pausedMs + paused.toMillis(),
            endsAt = session.endsAt.plus(paused),
        )
    }

    /** 強制解除のボタンを出すか：設定で許可、残り10分以上のときだけ。5.4、6.3 */
    fun canShowForceUnlock(session: Session, now: Instant, settings: Settings): Boolean =
        settings.allowForceUnlock && session.isRunning && remaining(session, now) >= Rules.FORCE_UNLOCK_MIN_REMAINING

    /**
     * 終了する（完了報告、または強制解除）。獲得コインを決める。
     * 完了報告（できた・一部・できなかった）は時間が終わってからだけ。強制解除（ABORTED）は10分待ちが満了したときに呼ぶ。
     */
    fun finish(session: Session, result: SessionResult, now: Instant): Session {
        require(result != SessionResult.RUNNING)
        require(result == SessionResult.ABORTED || isOver(session, now)) { "時間が終わる前には完了報告できない" }
        val s = normalize(session, now).let { if (it.isPaused) resume(it, now) else it }
        return s.copy(
            result = result,
            coinsEarned = Coins.earned(s.kind, s.durationMin, s.rarity, result),
            finishedAt = now,
        )
    }

    /**
     * 終了したセッションをコイン・連続日数・天井に反映する。
     * 連続日数はやることを「できた」「一部できた」で終えた日を数え、その日最初の完了でボーナスを付ける。
     * 強制解除は連続記録を変えない（維持）。
     */
    fun settle(wallet: Wallet, finished: Session, finishDay: DayKey): Settlement {
        require(finished.result != SessionResult.RUNNING)
        var w = wallet.copy(coins = wallet.coins + finished.coinsEarned)
        var bonus = 0
        val completed = finished.result == SessionResult.DONE || finished.result == SessionResult.PARTIAL
        if (finished.kind == MissionKind.TASK && completed) {
            val (next, b) = Coins.applyCompletionDay(w, finishDay)
            w = next
            bonus = b
        }
        w = Pity.afterSession(w, finished.kind, finished.durationMin, finished.result)
        return Settlement(w, finished, finished.coinsEarned, bonus)
    }

    /** 「これまでの回数」「最後に実施した日」に数える結果。8章 */
    fun countsAsDone(result: SessionResult): Boolean =
        result == SessionResult.DONE || result == SessionResult.PARTIAL
}
