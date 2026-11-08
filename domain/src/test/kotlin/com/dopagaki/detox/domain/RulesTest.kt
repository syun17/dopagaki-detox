package com.dopagaki.detox.domain

import com.dopagaki.detox.domain.time.FakeTrustedClock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalTime

/** Rules・SettingsDefaults の値が設計書（5.4、5.5、7.1〜7.5、8章）と合っているかを1つずつ確かめる。 */
class RulesTest {

    @Test
    fun `レア度の出現率は設計書7_1のとおりで合計100`() {
        assertEquals(
            mapOf(Rarity.N to 50, Rarity.R to 35, Rarity.SR to 12, Rarity.SSR to 3),
            Rules.rarityRatePercent,
        )
        assertEquals(100, Rules.rarityRatePercent.values.sum())
    }

    @Test
    fun `コインの倍率は設計書7_1のとおり`() {
        assertEquals(
            mapOf(Rarity.N to 100, Rarity.R to 125, Rarity.SR to 150, Rarity.SSR to 200),
            Rules.rarityMultiplierPercent,
        )
    }

    @Test
    fun `コインとガチャと天井の定数は設計書7_3と7_5と8章のとおり`() {
        assertEquals(100, Rules.TASK_COIN_PER_MIN)
        assertEquals(300, Rules.REWARD_COIN_PER_MIN)
        assertEquals(600, Rules.GACHA_SINGLE_COST)
        assertEquals(4_500, Rules.GACHA_TEN_COST)
        assertEquals(10, Rules.GACHA_TEN_COUNT)
        assertEquals(5, Rules.PITY_THRESHOLD)
        assertEquals(15, Rules.PITY_MIN_DURATION_MIN)
        assertEquals(100, Rules.STREAK_BONUS_PER_DAY)
        assertEquals(1_000, Rules.STREAK_BONUS_MAX)
        assertEquals(3_000, Rules.FIRST_LAUNCH_BONUS)
        assertEquals(20, Rules.FEED_SIZE)
        assertEquals(2, Rules.REWARD_DAILY_LIMIT_DEFAULT)
    }

    @Test
    fun `設計書7_5の例表とコインの単価と倍率が合う`() {
        fun taskCoins(min: Int, rarity: Rarity) =
            Rules.TASK_COIN_PER_MIN * min * Rules.rarityMultiplierPercent.getValue(rarity) / 100
        fun rewardCost(min: Int, rarity: Rarity) =
            Rules.REWARD_COIN_PER_MIN * min * Rules.rarityMultiplierPercent.getValue(rarity) / 100

        assertEquals(500, taskCoins(5, Rarity.N))
        assertEquals(1_000, taskCoins(10, Rarity.N))
        assertEquals(2_500, taskCoins(20, Rarity.R))
        assertEquals(6_000, taskCoins(40, Rarity.SR))
        assertEquals(4_500, rewardCost(10, Rarity.SR))
        assertEquals(12_000, rewardCost(20, Rarity.SSR))
    }

    @Test
    fun `重みと実行中の数値は設計書5_4と7_2のとおり`() {
        assertEquals(0.1, Rules.WEIGHT_DAY_FACTOR, 0.0)
        assertEquals(7, Rules.WEIGHT_DAYS_CAP)
        assertEquals(0.2, Rules.WEIGHT_SAME_AS_PREVIOUS, 0.0)
        assertEquals(0.0, Rules.WEIGHT_REWARD_AFTER_REWARD, 0.0)
        assertEquals(1, Rules.PAUSE_MAX_COUNT)
        assertEquals(Duration.ofMinutes(5), Rules.PAUSE_MAX)
        assertEquals(Duration.ofMinutes(10), Rules.FORCE_UNLOCK_MIN_REMAINING)
    }

    @Test
    fun `種類ごとのレア度と時間の範囲と自動判定の境界は設計書5_5のとおり`() {
        assertEquals(setOf(Rarity.N, Rarity.R, Rarity.SR), Rules.TASK_RARITIES)
        assertEquals(setOf(Rarity.SR, Rarity.SSR), Rules.REWARD_RARITIES)
        assertEquals(5..120, Rules.TASK_DURATION_MIN_RANGE)
        assertEquals(5..60, Rules.REWARD_DURATION_MIN_RANGE)
        assertEquals(1..5, Rules.WEIGHT_RANGE)
        assertEquals(15, Rules.TASK_R_MIN_DURATION_MIN)
        assertEquals(30, Rules.TASK_SR_MIN_DURATION_MIN)
        assertEquals(16, Rules.REWARD_SSR_MIN_DURATION_MIN)
    }

    @Test
    fun `設定の初期値は設計書8章Settingsのとおり`() {
        assertEquals(Duration.ofMinutes(10), SettingsDefaults.UNLOCK_WAIT)
        assertEquals(Duration.ofMinutes(30), SettingsDefaults.UNLOCK_GRACE)
        assertEquals(LocalTime.of(23, 0), SettingsDefaults.QUIET_START)
        assertEquals(LocalTime.of(6, 0), SettingsDefaults.QUIET_END)
        assertEquals(0.1, SettingsDefaults.HOME_REWARD_RATIO, 0.0)
        assertEquals(1, SettingsDefaults.FEED_REWARD_DAILY_LIMIT)
        assertEquals(3, SettingsDefaults.REFRESH_LIMIT)
        assertEquals(Duration.ofMinutes(60), SettingsDefaults.REFRESH_WINDOW)
        assertEquals(4, SettingsDefaults.DAY_START_HOUR)
    }

    @Test
    fun `FakeTrustedClockは進めた分だけ進む`() {
        val clock = FakeTrustedClock()
        val start = clock.now()
        clock.advance(Duration.ofMinutes(10))
        assertEquals(Duration.ofMinutes(10), Duration.between(start, clock.now()))
    }
}
