package com.dopagaki.detox.domain.pity

import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.model.SessionResult
import com.dopagaki.detox.domain.model.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PityTest {
    private val t = MissionKind.TASK

    @Test fun `15分以上のやることを5回できたで天井`() {
        var w = Wallet()
        repeat(4) { w = Pity.afterSession(w, t, 15, SessionResult.DONE) }
        assertEquals(4, w.pityCount)
        assertFalse(w.pityReady)
        w = Pity.afterSession(w, t, 15, SessionResult.DONE)
        assertTrue(w.pityReady)
    }

    @Test fun `一部・できなかった・強制解除で0`() {
        for (r in listOf(SessionResult.PARTIAL, SessionResult.FAILED, SessionResult.ABORTED)) {
            assertEquals(0, Pity.afterSession(Wallet(pityCount = 3), t, 20, r).pityCount)
        }
    }

    @Test fun `ご褒美と15分未満のやることは数えず戻さない`() {
        val w = Wallet(pityCount = 3)
        assertEquals(3, Pity.afterSession(w, MissionKind.REWARD, 20, SessionResult.DONE).pityCount)
        assertEquals(3, Pity.afterSession(w, t, 14, SessionResult.DONE).pityCount)
        assertEquals(3, Pity.afterSession(w, t, 14, SessionResult.FAILED).pityCount)
    }

    @Test fun `天井は使うまで持ち越す`() {
        val w = Wallet(pityReady = true)
        assertTrue(Pity.afterSession(w, t, 20, SessionResult.FAILED).pityReady)
        assertFalse(Pity.consume(w).pityReady)
    }
}
