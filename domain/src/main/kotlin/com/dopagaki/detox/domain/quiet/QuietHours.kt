package com.dopagaki.detox.domain.quiet

import com.dopagaki.detox.domain.model.Settings
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * 静かな時間帯（設計書 7.3）。開始を含み、終了を含まない。日をまたぐ範囲として扱う。
 * 判定は抽選のときと、ご褒美を「始める」ときの両方で行う。
 */
object QuietHours {
    fun isQuiet(time: LocalTime, start: LocalTime, end: LocalTime): Boolean = when {
        start == end -> false
        start < end -> time >= start && time < end
        else -> time >= start || time < end
    }

    fun isQuiet(settings: Settings, now: Instant, zone: ZoneId): Boolean =
        settings.quietEnabled && isQuiet(now.atZone(zone).toLocalTime(), settings.quietStart, settings.quietEnd)

    /** 新しい範囲が古い範囲をすべて含むか（含むなら厳しくする変更。5.6）。1分刻みで確かめる。 */
    fun covers(newStart: LocalTime, newEnd: LocalTime, oldStart: LocalTime, oldEnd: LocalTime): Boolean {
        var t = LocalTime.MIDNIGHT
        repeat(24 * 60) {
            if (isQuiet(t, oldStart, oldEnd) && !isQuiet(t, newStart, newEnd)) return false
            t = t.plusMinutes(1)
        }
        return true
    }
}
