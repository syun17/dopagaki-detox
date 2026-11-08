package com.dopagaki.detox.domain.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ClockAnchorsTest {
    private val t0 = Instant.parse("2026-11-08T03:00:00Z")

    @Test fun `初回でネットワーク時刻がなければ端末の時計`() {
        val a = ClockAnchors.resolve(null, 1, 1_000, null, t0)
        assertEquals(AnchorSource.DEVICE, a.source)
        assertEquals(t0, ClockAnchors.trustedNow(a, 1_000))
    }

    @Test fun `同じ起動の間に端末の時計を進めても信頼時刻は進まない`() {
        val a = ClockAnchors.resolve(null, 1, 1_000, null, t0)
        val again = ClockAnchors.resolve(a, 1, 61_000, null, t0.plus(Duration.ofHours(1)))
        assertEquals(t0.plusSeconds(60), ClockAnchors.trustedNow(again, 61_000))
    }

    @Test fun `再起動でネットワーク時刻があればそれが基準`() {
        val a = ClockAnchors.resolve(null, 1, 1_000, null, t0)
        val net = t0.plus(Duration.ofHours(2))
        val b = ClockAnchors.resolve(a, 2, 500, net, t0.plus(Duration.ofDays(3)))
        assertEquals(AnchorSource.NETWORK, b.source)
        assertEquals(net, ClockAnchors.trustedNow(b, 500))
    }

    @Test fun `再起動でネットワーク時刻がなければ最後に保存した信頼時刻`() {
        var a = ClockAnchors.resolve(null, 1, 1_000, null, t0)
        a = ClockAnchors.checkpoint(a, 61_000) // t0 + 60秒
        val b = ClockAnchors.resolve(a, 2, 500, null, t0.plus(Duration.ofDays(3)))
        assertEquals(AnchorSource.CARRIED, b.source)
        assertEquals(t0.plusSeconds(60), ClockAnchors.trustedNow(b, 500))
    }

    @Test fun `ネットワーク時刻が今の信頼時刻より過去なら戻さない`() {
        val a = ClockAnchors.resolve(null, 1, 1_000, null, t0)
        val b = ClockAnchors.resolve(a, 1, 11_000, t0.minus(Duration.ofHours(1)), t0)
        assertEquals(AnchorSource.NETWORK, b.source)
        assertEquals(t0.plusSeconds(10), ClockAnchors.trustedNow(b, 11_000))
    }

    @Test fun `ネットワーク時刻が取れるようになったら取り直す`() {
        val a = ClockAnchors.resolve(null, 1, 1_000, null, t0)
        val net = t0.plus(Duration.ofMinutes(5))
        val b = ClockAnchors.resolve(a, 1, 2_000, net, t0)
        assertEquals(net, ClockAnchors.trustedNow(b, 2_000))
    }
}
