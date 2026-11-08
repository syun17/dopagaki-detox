package com.dopagaki.detox.domain.pending

import com.dopagaki.detox.domain.Rarity
import com.dopagaki.detox.domain.model.Settings
import com.dopagaki.detox.domain.reward
import com.dopagaki.detox.domain.task
import com.dopagaki.detox.domain.time.FakeTrustedClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalTime

class PendingChangeTest {
    private val base = Settings(rewardAppLock = true)

    @Test fun `常時ロックをオフにするのは待ち、オンはすぐ`() {
        val off = ChangeClassifier.splitSettings(base, base.copy(rewardAppLock = false))
        assertTrue(off.applyNow.rewardAppLock)
        assertEquals(false, off.pending!!.rewardAppLock)
        val on = ChangeClassifier.splitSettings(base.copy(rewardAppLock = false), base)
        assertTrue(on.applyNow.rewardAppLock)
        assertNull(on.pending)
    }

    @Test fun `強制解除・一時停止を許可にするのは待ち、許可しないのはすぐ`() {
        val strict = base.copy(allowForceUnlock = false, allowPause = false)
        val loosen = ChangeClassifier.splitSettings(strict, base)
        assertFalse(loosen.applyNow.allowForceUnlock)
        assertEquals(true, loosen.pending!!.allowForceUnlock)
        assertEquals(true, loosen.pending!!.allowPause)
        val tighten = ChangeClassifier.splitSettings(base, strict)
        assertNull(tighten.pending)
        assertFalse(tighten.applyNow.allowPause)
    }

    @Test fun `静かな時間帯は長くするのはすぐ、短くする・ずらす・オフは待ち`() {
        val longer = ChangeClassifier.splitSettings(base, base.copy(quietStart = LocalTime.of(22, 0)))
        assertNull(longer.pending)
        assertEquals(LocalTime.of(22, 0), longer.applyNow.quietStart)
        assertNotNull(ChangeClassifier.splitSettings(base, base.copy(quietStart = LocalTime.of(0, 0))).pending)
        assertNotNull(ChangeClassifier.splitSettings(base, base.copy(quietStart = LocalTime.of(22, 0), quietEnd = LocalTime.of(5, 0))).pending)
        assertNotNull(ChangeClassifier.splitSettings(base, base.copy(quietEnabled = false)).pending)
        assertNull(ChangeClassifier.splitSettings(base.copy(quietEnabled = false), base).pending)
    }

    @Test fun `ホーム・スワイプ枠を増やすのは待ち、減らすのはすぐ`() {
        assertEquals(2, ChangeClassifier.splitSettings(base, base.copy(feedRewardDailyLimit = 2)).pending!!.feedRewardDailyLimit)
        assertNull(ChangeClassifier.splitSettings(base, base.copy(feedRewardDailyLimit = 0)).pending)
    }

    @Test fun `ご褒美の有利な項目だけ待ち、厳しい項目はすぐ`() {
        val g = reward("g", min = 20, rarity = Rarity.SSR, weight = 3, dailyLimit = 2, apps = setOf("a", "b"))
        val edited = g.copy(durationMin = 30, weight = 2, dailyLimit = 3, rarity = Rarity.SR, linkedApps = setOf("b", "c"), title = "新")
        val split = ChangeClassifier.splitEdit(g, edited)
        assertEquals(20, split.applyNow.durationMin)
        assertEquals(2, split.applyNow.weight)
        assertEquals("新", split.applyNow.title)
        assertEquals(setOf("a", "b", "c"), split.applyNow.linkedApps)
        val p = split.pending as PendingPayload.MissionPatch
        assertEquals(30, p.durationMin)
        assertEquals(3, p.dailyLimit)
        assertEquals(Rarity.SR, p.rarity)
        assertEquals(setOf("a"), p.removeLinkedApps)
        assertNull(p.weight)
        // 反映のときはその項目だけを書き換える
        val applied = p.applyTo(split.applyNow.copy(weight = 1))
        assertEquals(1, applied.weight)
        assertEquals(30, applied.durationMin)
        assertEquals(setOf("b", "c"), applied.linkedApps)
    }

    @Test fun `やることはレア度を上げる・無効化・出やすさを下げるのが待ち`() {
        val t = task("t", rarity = Rarity.N, weight = 3)
        val p = ChangeClassifier.splitEdit(t, t.copy(rarity = Rarity.R, enabled = false, weight = 1)).pending as PendingPayload.MissionPatch
        assertEquals(Rarity.R, p.rarity)
        assertEquals(false, p.enabled)
        assertEquals(1, p.weight)
        assertNull(ChangeClassifier.splitEdit(t, t.copy(weight = 5, durationMin = 30)).pending)
    }

    @Test fun `ご褒美を足すと無効で入り、有効にするのを待つ。やることはすぐ`() {
        val add = ChangeClassifier.splitAdd(reward("g"))
        assertFalse(add.applyNow.enabled)
        assertEquals(true, (add.pending as PendingPayload.MissionPatch).enabled)
        assertNull(ChangeClassifier.splitAdd(task("t")).pending)
        assertEquals(DeleteSplit(false, PendingPayload.DeleteMission("t")), ChangeClassifier.splitDelete(task("t")))
        assertEquals(DeleteSplit(true, PendingPayload.UnlockApps(setOf("x"))), ChangeClassifier.splitDelete(reward("g", apps = setOf("x"))))
        assertEquals(DeleteSplit(true, null), ChangeClassifier.splitDelete(reward("g", apps = emptySet())))
    }

    @Test fun `待ちは依頼から10分、別々に数え、取り消せる`() {
        val clock = FakeTrustedClock()
        val a = PendingChanges.request("a", PendingPayload.ForceUnlockAppLock, clock.now())
        clock.advance(Duration.ofMinutes(3))
        val b = PendingChanges.request("b", PendingPayload.ResetData, clock.now())
        clock.advance(Duration.ofMinutes(7))
        assertEquals(listOf("a"), PendingChanges.due(listOf(a, b), clock.now()).map { it.id })
        assertTrue(PendingChanges.due(listOf(a, PendingChanges.cancel(b)), clock.now().plus(Duration.ofMinutes(5))).none { it.id == "b" })
    }

    @Test fun `常時ロックの強制解除は反映から30分だけ通す`() {
        val clock = FakeTrustedClock()
        val done = PendingChanges.request("a", PendingPayload.ForceUnlockAppLock, clock.now()).copy(status = PendingStatus.DONE)
        clock.advance(Duration.ofMinutes(39))
        assertNotNull(PendingChanges.appLockGraceUntil(listOf(done), clock.now()))
        clock.advance(Duration.ofMinutes(1))
        assertNull(PendingChanges.appLockGraceUntil(listOf(done), clock.now()))
    }

    @Test fun `中身は文字列にして戻せる`() {
        val p = PendingPayload.MissionPatch("m", durationMin = 30, rarity = Rarity.SR, removeLinkedApps = setOf("a"))
        assertEquals(p, PendingPayload.decode(PendingPayload.encode(p)))
        assertEquals(PendingPayload.ResetData, PendingPayload.decode(PendingPayload.encode(PendingPayload.ResetData)))
    }

    @Test fun `やることを時間から決まるより高いレア度で足すと、差は待ち`() {
        val add = ChangeClassifier.splitAdd(task("t", min = 5, rarity = Rarity.SR))
        assertEquals(Rarity.N, add.applyNow.rarity)
        assertEquals(Rarity.SR, (add.pending as PendingPayload.MissionPatch).rarity)
    }

    @Test fun `やることの時間を縮めてレア度が時間より高く残るなら、差は待ち`() {
        val t = task("t", min = 30, rarity = Rarity.SR)
        val split = ChangeClassifier.splitEdit(t, t.copy(durationMin = 5))
        assertEquals(5, split.applyNow.durationMin)
        assertEquals(Rarity.N, split.applyNow.rarity)
        assertEquals(Rarity.SR, (split.pending as PendingPayload.MissionPatch).rarity)
    }

    @Test fun `1日上限のnullは既定の2として比べる`() {
        val g = reward("g", dailyLimit = 1)
        assertNotNull(ChangeClassifier.splitEdit(g, g.copy(dailyLimit = null)).pending)
    }

    @Test fun `消したご褒美の対象アプリは待ちのあいだロックに残る`() {
        val clock = FakeTrustedClock()
        val c = PendingChanges.request("u", PendingPayload.UnlockApps(setOf("com.game")), clock.now())
        assertEquals(setOf("com.game"), PendingChanges.stillLockedApps(listOf(c)))
        assertEquals(emptySet<String>(), PendingChanges.stillLockedApps(listOf(c.copy(status = PendingStatus.DONE))))
    }
}
