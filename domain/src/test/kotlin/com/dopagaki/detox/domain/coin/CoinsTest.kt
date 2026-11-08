package com.dopagaki.detox.domain.coin

import com.dopagaki.detox.domain.Rarity
import com.dopagaki.detox.domain.day.DayKey
import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.model.SessionResult
import com.dopagaki.detox.domain.model.SessionSource
import com.dopagaki.detox.domain.model.Wallet
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CoinsTest {
    private val t = MissionKind.TASK
    private val r = MissionKind.REWARD

    @Test fun `できたは100×分×倍率`() {
        assertEquals(500, Coins.earned(t, 5, Rarity.N, SessionResult.DONE))
        assertEquals(1_000, Coins.earned(t, 10, Rarity.N, SessionResult.DONE))
        assertEquals(2_500, Coins.earned(t, 20, Rarity.R, SessionResult.DONE))
        assertEquals(6_000, Coins.earned(t, 40, Rarity.SR, SessionResult.DONE))
        assertEquals(1_875, Coins.earned(t, 15, Rarity.R, SessionResult.DONE))
    }

    @Test fun `一部できたは半分を切り捨て`() {
        assertEquals(937, Coins.earned(t, 15, Rarity.R, SessionResult.PARTIAL))
    }

    @Test fun `できなかった・強制解除・ご褒美は0`() {
        assertEquals(0, Coins.earned(t, 20, Rarity.R, SessionResult.FAILED))
        assertEquals(0, Coins.earned(t, 20, Rarity.R, SessionResult.ABORTED))
        assertEquals(0, Coins.earned(r, 20, Rarity.SSR, SessionResult.DONE))
    }

    @Test fun `ご褒美の必要コインはホーム・スワイプ・あとでだけ`() {
        assertEquals(4_500, Coins.startCost(r, 10, Rarity.SR, SessionSource.HOME))
        assertEquals(12_000, Coins.startCost(r, 20, Rarity.SSR, SessionSource.SWIPE))
        assertEquals(12_000, Coins.startCost(r, 20, Rarity.SSR, SessionSource.LATER))
        assertEquals(0, Coins.startCost(r, 20, Rarity.SSR, SessionSource.GACHA_SINGLE))
        assertEquals(0, Coins.startCost(r, 20, Rarity.SSR, SessionSource.GACHA_TEN))
        assertEquals(0, Coins.startCost(t, 20, Rarity.R, SessionSource.HOME))
    }

    @Test fun `連続日数ボーナスは日数×100で上限1000`() {
        assertEquals(300, Coins.streakBonus(3))
        assertEquals(1_000, Coins.streakBonus(10))
        assertEquals(1_000, Coins.streakBonus(15))
    }

    @Test fun `連続日数ボーナスはその日最初の完了でだけ付く`() {
        val d1 = DayKey(LocalDate.of(2026, 11, 8))
        val w0 = Wallet(coins = 0, streakDays = 2, lastActiveDay = d1.previous())
        val (w1, b1) = Coins.applyCompletionDay(w0, d1)
        assertEquals(3, w1.streakDays)
        assertEquals(300, b1)
        val (w2, b2) = Coins.applyCompletionDay(w1, d1)
        assertEquals(0, b2)
        assertEquals(w1, w2)
    }

    @Test fun `1日空いたら連続日数は1から`() {
        val d = DayKey(LocalDate.of(2026, 11, 8))
        val w = Wallet(streakDays = 5, lastActiveDay = DayKey(LocalDate.of(2026, 11, 6)))
        val (next, bonus) = Coins.applyCompletionDay(w, d)
        assertEquals(1, next.streakDays)
        assertEquals(100, bonus)
    }

    @Test fun `初回起動ボーナスは1回だけ`() {
        assertEquals(3_000, Coins.applyFirstLaunchBonus(Wallet(), alreadyGiven = false).coins)
        assertEquals(0, Coins.applyFirstLaunchBonus(Wallet(), alreadyGiven = true).coins)
    }
}
