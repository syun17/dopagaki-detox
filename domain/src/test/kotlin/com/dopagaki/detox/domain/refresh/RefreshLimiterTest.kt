package com.dopagaki.detox.domain.refresh

import com.dopagaki.detox.domain.time.FakeTrustedClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class RefreshLimiterTest {
    @Test fun `直近60分に3回まで、いちばん古い更新から60分で戻る`() {
        val clock = FakeTrustedClock()
        val t0 = clock.now()
        var times = RefreshLimiter.record(emptyList(), t0)
        clock.advance(Duration.ofMinutes(10)); times = RefreshLimiter.record(times, clock.now())
        clock.advance(Duration.ofMinutes(10)); times = RefreshLimiter.record(times, clock.now())
        clock.advance(Duration.ofMinutes(10))
        assertFalse(RefreshLimiter.canRefresh(times, clock.now()))
        assertEquals(Duration.ofMinutes(30), Duration.between(clock.now(), RefreshLimiter.nextAvailableAt(times, clock.now())))
        clock.set(t0.plus(Duration.ofMinutes(60)).minusMillis(1))
        assertFalse(RefreshLimiter.canRefresh(times, clock.now()))
        clock.set(t0.plus(Duration.ofMinutes(60)))
        assertTrue(RefreshLimiter.canRefresh(times, clock.now()))
        assertNull(RefreshLimiter.nextAvailableAt(times, clock.now()))
        assertEquals(1, RefreshLimiter.remaining(times, clock.now()))
    }

    @Test fun `記録は最大3件`() {
        val clock = FakeTrustedClock()
        var times = emptyList<java.time.Instant>()
        repeat(5) { times = RefreshLimiter.record(times, clock.now()); clock.advance(Duration.ofMinutes(30)) }
        assertTrue(times.size <= 3)
    }
}
