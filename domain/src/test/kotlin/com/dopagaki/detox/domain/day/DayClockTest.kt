package com.dopagaki.detox.domain.day

import com.dopagaki.detox.domain.TOKYO
import com.dopagaki.detox.domain.tokyo
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class DayClockTest {
    @Test fun `3時59分は前日、4時から当日`() {
        assertEquals(LocalDate.of(2026, 11, 7), DayKey.of(tokyo("2026-11-08T03:59"), TOKYO).date)
        assertEquals(LocalDate.of(2026, 11, 8), DayKey.of(tokyo("2026-11-08T04:00"), TOKYO).date)
    }

    @Test fun `普段は毎朝4時に切り替わる`() {
        var s = DayClock.advance(null, tokyo("2026-11-08T09:00"), TOKYO)
        s = DayClock.advance(s, tokyo("2026-11-09T04:00"), TOKYO)
        assertEquals(LocalDate.of(2026, 11, 9), s.current.date)
        s = DayClock.advance(s, tokyo("2026-11-10T04:00"), TOKYO)
        assertEquals(LocalDate.of(2026, 11, 10), s.current.date)
    }

    @Test fun `タイムゾーンを西に変えても後戻りしない`() {
        val s = DayClock.advance(null, tokyo("2026-11-08T05:00"), TOKYO)
        val west = DayClock.advance(s, tokyo("2026-11-08T05:00"), ZoneId.of("Pacific/Honolulu"))
        assertEquals(LocalDate.of(2026, 11, 8), west.current.date)
    }

    @Test fun `タイムゾーンを東に変えても切り替えは20時間あける`() {
        val start = tokyo("2026-11-08T04:00")
        val s = DayClock.advance(null, start, TOKYO)
        val east = ZoneId.of("Pacific/Kiritimati") // UTC+14（東京より5時間進む）
        // 東京 23:00 は +14 で翌 4:00。まだ20時間たっていないので切り替えない
        val blocked = DayClock.advance(s, tokyo("2026-11-08T23:00"), east)
        assertEquals(LocalDate.of(2026, 11, 8), blocked.current.date)
        // 20時間たてば切り替える
        val after = DayClock.advance(blocked, start.plus(Duration.ofHours(20)), east)
        assertEquals(LocalDate.of(2026, 11, 9), after.current.date)
    }

    @Test fun `東へずれた切り替えの時刻は数日で現地の4時に戻る`() {
        val east = ZoneId.of("Pacific/Kiritimati")
        var s = DayClock.advance(null, tokyo("2026-11-08T04:00"), TOKYO)
        var t = tokyo("2026-11-09T00:00")
        s = DayClock.advance(s, t, east)
        repeat(24 * 5) {
            t = t.plus(Duration.ofHours(1))
            s = DayClock.advance(s, t, east)
        }
        assertEquals(java.time.LocalTime.of(4, 0), s.switchedAt.atZone(east).toLocalTime())
    }
}
