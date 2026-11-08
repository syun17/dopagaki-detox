package com.dopagaki.detox.domain.lock

import com.dopagaki.detox.domain.TOKYO
import com.dopagaki.detox.domain.day.DayKey
import com.dopagaki.detox.domain.model.SessionSource
import com.dopagaki.detox.domain.model.Settings
import com.dopagaki.detox.domain.model.Wallet
import com.dopagaki.detox.domain.reward
import com.dopagaki.detox.domain.session.SessionRules
import com.dopagaki.detox.domain.task
import com.dopagaki.detox.domain.tokyo
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class AppLockPolicyTest {
    private val now = tokyo("2026-11-08T12:00")
    private val game = reward("g", apps = setOf("com.game"))
    private val missions = listOf(game, task("t"))
    private val on = Settings(rewardAppLock = true)

    private fun decide(pkg: String, settings: Settings = on, running: com.dopagaki.detox.domain.model.Session? = null, at: java.time.Instant = now, excluded: Set<String> = emptySet(), grace: java.time.Instant? = null) =
        AppLockPolicy.decide(pkg, at, settings, missions, excluded, running, grace)

    @Test fun `普段はロック、常時ロックがオフなら対象外`() {
        assertEquals(AppLockDecision.LOCKED, decide("com.game"))
        assertEquals(AppLockDecision.NOT_TARGET, decide("com.game", settings = Settings(rewardAppLock = false)))
        assertEquals(AppLockDecision.NOT_TARGET, decide("com.other"))
    }

    @Test fun `ご褒美ミッション中だけ通し、時間切れと一時停止中はロック`() {
        val (s, _) = SessionRules.start("s", game, SessionSource.GACHA_SINGLE, Wallet(), now, DayKey.of(now, TOKYO))
        assertEquals(AppLockDecision.ALLOWED, decide("com.game", running = s))
        assertEquals(AppLockDecision.LOCKED, decide("com.game", running = s, at = s.endsAt))
        assertEquals(AppLockDecision.LOCKED, decide("com.game", running = SessionRules.pause(s, now)))
    }

    @Test fun `やることのミッション中はロック`() {
        val (s, _) = SessionRules.start("s", task("t"), SessionSource.HOME, Wallet(), now, DayKey.of(now, TOKYO))
        assertEquals(AppLockDecision.LOCKED, decide("com.game", running = s))
    }

    @Test fun `強制解除後の30分は通す`() {
        assertEquals(AppLockDecision.ALLOWED, decide("com.game", grace = now.plus(Duration.ofMinutes(1))))
    }

    @Test fun `除外アプリは対象にならない`() {
        assertEquals(AppLockDecision.NOT_TARGET, decide("com.game", excluded = setOf("com.game")))
    }

    @Test fun `消したご褒美の対象アプリは待ちのあいだロック`() {
        val d = AppLockPolicy.decide("com.old", now, on, missions, emptySet(), null, null, stillLocked = setOf("com.old"))
        assertEquals(AppLockDecision.LOCKED, d)
    }
}
