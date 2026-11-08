package com.dopagaki.detox.domain.refresh

import com.dopagaki.detox.domain.SettingsDefaults
import java.time.Duration
import java.time.Instant

/**
 * 更新回数（設計書 7.4）。ホームの引っ張って更新とスワイプの次の20枚を合わせて、直近60分に3回まで。
 * 上限に達したら、いちばん古い更新から60分たつまで更新できない。時刻は信頼時刻。
 */
object RefreshLimiter {
    private val window: Duration = SettingsDefaults.REFRESH_WINDOW
    private const val LIMIT = SettingsDefaults.REFRESH_LIMIT

    fun recent(times: List<Instant>, now: Instant): List<Instant> =
        times.filter { Duration.between(it, now) < window }.sorted()

    fun remaining(times: List<Instant>, now: Instant): Int = (LIMIT - recent(times, now).size).coerceAtLeast(0)

    fun canRefresh(times: List<Instant>, now: Instant): Boolean = remaining(times, now) > 0

    /** 次に更新できる時刻。今できるなら null。 */
    fun nextAvailableAt(times: List<Instant>, now: Instant): Instant? {
        val r = recent(times, now)
        if (r.size < LIMIT) return null
        return r[r.size - LIMIT].plus(window)
    }

    /** 更新したあとの記録（直近60分、最大3件）。 */
    fun record(times: List<Instant>, now: Instant): List<Instant> = (recent(times, now) + now).takeLast(LIMIT)
}
