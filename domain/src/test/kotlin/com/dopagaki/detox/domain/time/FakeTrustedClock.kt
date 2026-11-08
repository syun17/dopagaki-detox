package com.dopagaki.detox.domain.time

import java.time.Duration
import java.time.Instant

/** テスト用の信頼時刻。advance で時間を進める。 */
class FakeTrustedClock(
    private var current: Instant = Instant.parse("2026-11-08T12:00:00Z"),
) : TrustedClock {
    override fun now(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }

    fun set(instant: Instant) {
        current = instant
    }
}
