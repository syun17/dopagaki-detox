package com.dopagaki.detox.data.db

import com.dopagaki.detox.domain.Category
import com.dopagaki.detox.domain.Rarity
import com.dopagaki.detox.domain.day.DayKey
import com.dopagaki.detox.domain.day.DayState
import com.dopagaki.detox.domain.model.Mission
import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.model.Session
import com.dopagaki.detox.domain.model.SessionResult
import com.dopagaki.detox.domain.model.SessionSource
import com.dopagaki.detox.domain.model.TimeTag
import com.dopagaki.detox.domain.model.Wallet
import com.dopagaki.detox.domain.pending.PendingChanges
import com.dopagaki.detox.domain.pending.PendingPayload
import com.dopagaki.detox.domain.time.AnchorSource
import com.dopagaki.detox.domain.time.ClockAnchor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class EntitiesTest {
    private val t = Instant.parse("2026-11-08T03:00:00Z")

    @Test fun `Mission は保存して戻せる`() {
        val m = Mission("id", "好きなゲーム", MissionKind.REWARD, Category.GAME, 20, Rarity.SSR, 3, 2,
            setOf("com.a", "com.b"), setOf(TimeTag.NIGHT, TimeTag.DAY), true, t, "ひとこと")
        assertEquals(m, MissionEntity.from(m, 0).toDomain())
        val empty = m.copy(linkedApps = emptySet(), timeTags = emptySet(), lastDoneAt = null, dailyLimit = null)
        assertEquals(empty, MissionEntity.from(empty, 0).toDomain())
    }

    @Test fun `Session は保存して戻せる`() {
        val s = Session("s", "m", "読書", MissionKind.TASK, Rarity.R, 20, SessionSource.GACHA_TEN, t, 1_200_000, 0,
            t.plusSeconds(1200), SessionResult.RUNNING, 0, 0, DayKey(LocalDate.of(2026, 11, 8)), t, 1, null)
        assertEquals(s, SessionColumns.from(s).toDomain())
    }

    @Test fun `Wallet と1日の状態は保存して戻せる`() {
        val d = DayKey(LocalDate.of(2026, 11, 8))
        val w = Wallet(1234, 3, d, 2, true, listOf(t, t.plusSeconds(60)), d, 1)
        val day = DayState(d, t)
        val e = WalletEntity.from(w, day)
        assertEquals(w, e.toWallet())
        assertEquals(day, e.toDayState())
    }

    @Test fun `PendingChange と ClockAnchor は保存して戻せる`() {
        val p = PendingChanges.request("p", PendingPayload.SettingsPatch(quietEnabled = false), t)
        assertEquals(p, PendingEntity.from(p).toDomain())
        val a = ClockAnchor(3, 100, t, AnchorSource.CARRIED, t, 100)
        assertEquals(a, ClockAnchorEntity.from(a).toDomain())
    }
}
