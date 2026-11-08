package com.dopagaki.detox.domain.coin

import com.dopagaki.detox.domain.Rarity
import com.dopagaki.detox.domain.Rules
import com.dopagaki.detox.domain.day.DayKey
import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.model.SessionResult
import com.dopagaki.detox.domain.model.SessionSource
import com.dopagaki.detox.domain.model.Wallet

/** コインの計算（設計書 7.5）。コインは失敗で減らさない。増えるのはやることの完了・連続日数・初回ボーナスだけ。 */
object Coins {
    private fun multiplied(perMin: Int, durationMin: Int, rarity: Rarity): Int =
        perMin * durationMin * Rules.rarityMultiplierPercent.getValue(rarity) / 100

    /** やることを「できた」で完了したときの獲得コイン（100 × 分 × 倍率）。 */
    fun taskReward(durationMin: Int, rarity: Rarity): Int =
        multiplied(Rules.TASK_COIN_PER_MIN, durationMin, rarity)

    /** 完了報告の結果に応じた獲得コイン。一部は半分（切り捨て）。できなかった・強制解除・ご褒美は 0。 */
    fun earned(kind: MissionKind, durationMin: Int, rarity: Rarity, result: SessionResult): Int {
        if (kind == MissionKind.REWARD) return 0
        return when (result) {
            SessionResult.DONE -> taskReward(durationMin, rarity)
            SessionResult.PARTIAL -> taskReward(durationMin, rarity) / 2
            else -> 0
        }
    }

    /** ご褒美の価値（300 × 分 × 倍率）。ホーム・スワイプ・あとでから始めるときの必要コイン。 */
    fun rewardValue(durationMin: Int, rarity: Rarity): Int =
        multiplied(Rules.REWARD_COIN_PER_MIN, durationMin, rarity)

    /** 始めるのに必要なコイン。やることとガチャ経由のご褒美は 0。 */
    fun startCost(kind: MissionKind, durationMin: Int, rarity: Rarity, source: SessionSource): Int =
        if (kind == MissionKind.REWARD && !source.isGacha) rewardValue(durationMin, rarity) else 0

    /** 連続日数ボーナス（日数 × 100、上限 1,000）。 */
    fun streakBonus(streakDays: Int): Int =
        minOf(streakDays * Rules.STREAK_BONUS_PER_DAY, Rules.STREAK_BONUS_MAX)

    /**
     * やることを「できた」「一部できた」で終えたときの連続日数とボーナス。
     * その日最初の完了でだけボーナスが付く。前日に完了していれば +1、空いていれば 1 から。
     */
    fun applyCompletionDay(wallet: Wallet, today: DayKey): Pair<Wallet, Int> {
        if (wallet.lastActiveDay == today) return wallet to 0
        val streak = if (wallet.lastActiveDay == today.previous()) wallet.streakDays + 1 else 1
        val bonus = streakBonus(streak)
        return wallet.copy(streakDays = streak, lastActiveDay = today, coins = wallet.coins + bonus) to bonus
    }

    /** 初回起動ボーナス。すでに付けていれば何もしない。 */
    fun applyFirstLaunchBonus(wallet: Wallet, alreadyGiven: Boolean): Wallet =
        if (alreadyGiven) wallet else wallet.copy(coins = wallet.coins + Rules.FIRST_LAUNCH_BONUS)
}
