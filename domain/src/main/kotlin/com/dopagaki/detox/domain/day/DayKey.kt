package com.dopagaki.detox.domain.day

import com.dopagaki.detox.domain.Rules
import com.dopagaki.detox.domain.SettingsDefaults
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 4時区切りの1日（設計書 6.4）。 */
data class DayKey(val date: LocalDate) : Comparable<DayKey> {
    override fun compareTo(other: DayKey): Int = date.compareTo(other.date)

    fun previous(): DayKey = DayKey(date.minusDays(1))

    override fun toString(): String = date.toString()

    companion object {
        /** 時刻から、その時刻が属する1日を求める（4:00 から当日、3:59 は前日）。 */
        fun of(instant: Instant, zone: ZoneId): DayKey =
            DayKey(instant.atZone(zone).minusHours(SettingsDefaults.DAY_START_HOUR.toLong()).toLocalDate())

        fun parse(text: String): DayKey = DayKey(LocalDate.parse(text))
    }
}

/**
 * 今の1日と、その1日に切り替わった時刻。
 * 1日は後戻りせず、切り替えは前回から20時間以上あける（タイムゾーンを変えて同じ日が2回来ても数え直さない。
 * 東へ変えてずれた切り替えの時刻は、数日で現地の4時に戻る。6.4）。
 */
data class DayState(val current: DayKey, val switchedAt: Instant)

object DayClock {
    private val MIN_INTERVAL: Duration = Rules.DAY_SWITCH_MIN_INTERVAL

    /** 信頼時刻 now での1日を求める。state が null なら初回。 */
    fun advance(state: DayState?, now: Instant, zone: ZoneId): DayState {
        val candidate = DayKey.of(now, zone)
        if (state == null) return DayState(candidate, boundaryOf(candidate, zone))
        if (candidate <= state.current) return state
        val earliest = state.switchedAt.plus(MIN_INTERVAL)
        if (now < earliest) return state
        val boundary = boundaryOf(candidate, zone)
        return DayState(candidate, if (boundary < earliest) earliest else boundary)
    }

    private fun boundaryOf(day: DayKey, zone: ZoneId): Instant =
        day.date.atTime(SettingsDefaults.DAY_START_HOUR, 0).atZone(zone).toInstant()
}
