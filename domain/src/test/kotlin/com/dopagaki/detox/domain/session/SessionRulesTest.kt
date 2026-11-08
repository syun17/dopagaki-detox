package com.dopagaki.detox.domain.session

import com.dopagaki.detox.domain.Rarity
import com.dopagaki.detox.domain.TOKYO
import com.dopagaki.detox.domain.day.DayKey
import com.dopagaki.detox.domain.model.SessionResult
import com.dopagaki.detox.domain.model.SessionSource
import com.dopagaki.detox.domain.model.Settings
import com.dopagaki.detox.domain.model.Wallet
import com.dopagaki.detox.domain.reward
import com.dopagaki.detox.domain.task
import com.dopagaki.detox.domain.tokyo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class SessionRulesTest {
    private val now = tokyo("2026-11-08T12:00")
    private val today = DayKey.of(now, TOKYO)
    private val settings = Settings()

    @Test fun `コインが足りないご褒美は始められない`() {
        val g = reward("g", min = 20, rarity = Rarity.SSR)
        val check = SessionRules.checkStart(g, SessionSource.HOME, Wallet(coins = 11_999), settings, now, TOKYO, today, null, null)
        assertEquals(StartCheck.NotEnoughCoins(1), check)
    }

    @Test fun `ご褒美をホームから始めるとコインを引き、枠を使う`() {
        val g = reward("g", min = 10, rarity = Rarity.SR)
        val w = Wallet(coins = 5_000)
        assertEquals(StartCheck.Ok(4_500), SessionRules.checkStart(g, SessionSource.HOME, w, settings, now, TOKYO, today, null, null))
        val (s, w2) = SessionRules.start("s", g, SessionSource.HOME, w, now, today)
        assertEquals(500, w2.coins)
        assertEquals(4_500, s.coinsSpent)
        assertEquals(StartCheck.FeedSlotUsed, SessionRules.checkStart(g, SessionSource.SWIPE, w2.copy(coins = 99_999), settings, now, TOKYO, today, null, null))
    }

    @Test fun `ガチャのご褒美はコインも枠もいらない`() {
        val g = reward("g")
        val w = Wallet(coins = 0, feedRewardUsedDay = today, feedRewardUsedCount = 1)
        assertEquals(StartCheck.Ok(0), SessionRules.checkStart(g, SessionSource.GACHA_SINGLE, w, settings, now, TOKYO, today, null, null))
    }

    @Test fun `静かな時間帯はあとでのご褒美も始められない`() {
        val night = tokyo("2026-11-08T23:00")
        val check = SessionRules.checkStart(reward("g"), SessionSource.LATER, Wallet(coins = 99_999), settings, night, TOKYO, DayKey.of(night, TOKYO), null, null)
        assertEquals(StartCheck.QuietHours, check)
    }

    @Test fun `強制解除は残り10分ちょうどなら出し、9分59秒なら出さない`() {
        val (s, _) = SessionRules.start("s", task("t", min = 20), SessionSource.HOME, Wallet(), now, today)
        assertTrue(SessionRules.canShowForceUnlock(s, now.plus(Duration.ofMinutes(10)), settings))
        assertFalse(SessionRules.canShowForceUnlock(s, now.plus(Duration.ofMinutes(10)).plusSeconds(1), settings))
        assertFalse(SessionRules.canShowForceUnlock(s, now, settings.copy(allowForceUnlock = false)))
    }

    @Test fun `一時停止は1回・最大5分で、止めた分だけ終了が後ろへずれる`() {
        val (s0, _) = SessionRules.start("s", task("t", min = 20), SessionSource.HOME, Wallet(), now, today)
        assertTrue(SessionRules.canPause(s0, now, settings, unlockPending = false))
        val paused = SessionRules.pause(s0, now)
        // 3分後の残りは止まったまま
        assertEquals(Duration.ofMinutes(20), SessionRules.remaining(paused, now.plus(Duration.ofMinutes(3))))
        val resumed = SessionRules.resume(paused, now.plus(Duration.ofMinutes(3)))
        assertEquals(now.plus(Duration.ofMinutes(23)), resumed.endsAt)
        assertFalse(SessionRules.canPause(resumed, now.plus(Duration.ofMinutes(4)), settings, unlockPending = false))
    }

    @Test fun `一時停止は5分で自動で再開する`() {
        val (s0, _) = SessionRules.start("s", task("t", min = 20), SessionSource.HOME, Wallet(), now, today)
        val paused = SessionRules.pause(s0, now)
        val later = now.plus(Duration.ofMinutes(8))
        val n = SessionRules.normalize(paused, later)
        assertFalse(n.isPaused)
        assertEquals(now.plus(Duration.ofMinutes(25)), n.endsAt)
        assertEquals(Duration.ofMinutes(17), SessionRules.remaining(paused, later))
    }

    @Test fun `解除の待機中と設定で許可しないときは一時停止できない`() {
        val (s0, _) = SessionRules.start("s", task("t"), SessionSource.HOME, Wallet(), now, today)
        assertFalse(SessionRules.canPause(s0, now, settings, unlockPending = true))
        assertFalse(SessionRules.canPause(s0, now, settings.copy(allowPause = false), unlockPending = false))
    }

    @Test fun `強制解除はコイン0、連続記録は維持、天井は0`() {
        val (s0, _) = SessionRules.start("s", task("t", min = 20), SessionSource.HOME, Wallet(), now, today)
        val aborted = SessionRules.finish(s0, SessionResult.ABORTED, now.plus(Duration.ofMinutes(10)))
        val w = Wallet(coins = 100, streakDays = 4, lastActiveDay = today.previous(), pityCount = 3)
        val st = SessionRules.settle(w, aborted, today)
        assertEquals(0, st.earned)
        assertEquals(100, st.wallet.coins)
        assertEquals(4, st.wallet.streakDays)
        assertEquals(0, st.wallet.pityCount)
    }

    @Test fun `できたでコインと連続日数ボーナスと天井が進む`() {
        val (s0, _) = SessionRules.start("s", task("t", min = 20, rarity = Rarity.R), SessionSource.HOME, Wallet(), now, today)
        val done = SessionRules.finish(s0, SessionResult.DONE, s0.endsAt)
        val st = SessionRules.settle(Wallet(coins = 0, streakDays = 2, lastActiveDay = today.previous()), done, today)
        assertEquals(2_500, st.earned)
        assertEquals(300, st.streakBonus)
        assertEquals(2_800, st.wallet.coins)
        assertEquals(1, st.wallet.pityCount)
    }

    @Test fun `時間が終わったかどうか`() {
        val (s0, _) = SessionRules.start("s", task("t", min = 20), SessionSource.HOME, Wallet(), now, today)
        assertFalse(SessionRules.isOver(s0, now.plus(Duration.ofMinutes(19))))
        assertTrue(SessionRules.isOver(s0, now.plus(Duration.ofMinutes(20))))
    }

    @Test fun `直前がご褒美ならホーム・スワイプ・あとでのご褒美は始められない、ガチャは始められる`() {
        val g = reward("g")
        val w = Wallet(coins = 99_999)
        val prev = com.dopagaki.detox.domain.model.MissionKind.REWARD
        assertEquals(StartCheck.AfterReward, SessionRules.checkStart(g, SessionSource.LATER, w, settings, now, TOKYO, today, null, prev))
        assertEquals(StartCheck.Ok(0), SessionRules.checkStart(g, SessionSource.GACHA_TEN, w, settings, now, TOKYO, today, null, prev))
    }

    @Test fun `10連の結果から静かな時間帯にご褒美は選べない`() {
        val night = tokyo("2026-11-08T23:01")
        val check = SessionRules.checkStart(reward("g"), SessionSource.GACHA_TEN, Wallet(), settings, night, TOKYO, DayKey.of(night, TOKYO), null, null)
        assertEquals(StartCheck.QuietHours, check)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `時間が終わる前には完了報告できない`() {
        val (s0, _) = SessionRules.start("s", task("t", min = 20), SessionSource.HOME, Wallet(), now, today)
        SessionRules.finish(s0, SessionResult.DONE, now.plus(Duration.ofMinutes(19)))
    }

    @Test fun `一時停止中も残り10分以上なら強制解除を出す`() {
        val (s0, _) = SessionRules.start("s", task("t", min = 20), SessionSource.HOME, Wallet(), now, today)
        assertTrue(SessionRules.canShowForceUnlock(SessionRules.pause(s0, now), now.plus(Duration.ofMinutes(4)), settings))
    }
}
