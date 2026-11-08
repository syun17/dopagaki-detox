package com.dopagaki.detox.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dopagaki.detox.R
import com.dopagaki.detox.domain.coin.Coins
import com.dopagaki.detox.domain.day.DayKey
import com.dopagaki.detox.domain.lottery.RewardAvailability
import com.dopagaki.detox.domain.model.Mission
import com.dopagaki.detox.domain.model.SessionSource
import com.dopagaki.detox.domain.model.Settings
import com.dopagaki.detox.domain.session.StartCheck
import com.dopagaki.detox.ui.common.grouped
import com.dopagaki.detox.ui.common.hm
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 「今日」「昨日」「3日前」「1週間前」「2週間前」（4時区切りの日で数える）。 */
@Composable
fun relativeDay(at: Instant, now: Instant, zone: ZoneId): String {
    val days = ChronoUnit.DAYS.between(DayKey.of(at, zone).date, DayKey.of(now, zone).date).coerceAtLeast(0)
    return when {
        days == 0L -> stringResource(R.string.day_today)
        days == 1L -> stringResource(R.string.day_yesterday)
        days < 7 -> stringResource(R.string.day_n_ago, days)
        days < 14 -> stringResource(R.string.week_one_ago)
        else -> stringResource(R.string.week_n_ago, days / 7)
    }
}

/** やることは「完了で2,500コイン」、ご褒美は「始めるのに12,000コイン」（ガチャ経由は「追加コインなし」）。 */
@Composable
fun coinText(m: Mission, source: SessionSource = SessionSource.HOME): String = when {
    !m.isReward -> stringResource(R.string.coin_on_done, Coins.taskReward(m.durationMin, m.rarity).grouped())
    source.isGacha -> stringResource(R.string.coin_no_extra)
    else -> stringResource(R.string.coin_to_start, Coins.rewardValue(m.durationMin, m.rarity).grouped())
}

/** 始められない理由（S01-2、S02）。Ok なら null。 */
@Composable
fun startReason(check: StartCheck, settings: Settings): String? = when (check) {
    is StartCheck.Ok, StartCheck.AlreadyRunning -> null
    is StartCheck.NotEnoughCoins -> stringResource(R.string.reason_coins, check.missing.grouped())
    StartCheck.FeedSlotUsed -> stringResource(R.string.reason_feed_slot)
    StartCheck.QuietHours -> stringResource(R.string.reason_quiet, settings.quietStart.hm(), settings.quietEnd.hm())
    StartCheck.AfterReward -> stringResource(R.string.reason_after_reward)
    StartCheck.Disabled -> stringResource(R.string.reason_disabled)
}

/** 始められない短い理由（S02 のチップ。全文は Snackbar。仮決定）。Ok なら null。 */
@Composable
fun startReasonShort(check: StartCheck): String? = when (check) {
    is StartCheck.Ok, StartCheck.AlreadyRunning -> null
    is StartCheck.NotEnoughCoins -> stringResource(R.string.reason_coins_short, check.missing.grouped())
    StartCheck.FeedSlotUsed -> stringResource(R.string.reason_short_feed_slot)
    StartCheck.QuietHours -> stringResource(R.string.reason_short_quiet)
    StartCheck.AfterReward -> stringResource(R.string.reason_short_after_reward)
    StartCheck.Disabled -> stringResource(R.string.reason_short_disabled)
}

/** 「今はご褒美が出ません」の理由（S03）。出せるなら null。 */
@Composable
fun rewardUnavailableText(a: RewardAvailability, settings: Settings, pityReady: Boolean): String? {
    val base = when (a) {
        RewardAvailability.AVAILABLE -> return null
        RewardAvailability.QUIET_HOURS -> stringResource(R.string.no_reward_quiet, settings.quietStart.hm(), settings.quietEnd.hm())
        RewardAvailability.AFTER_REWARD -> stringResource(R.string.no_reward_after_reward)
        RewardAvailability.DAILY_LIMIT -> stringResource(R.string.no_reward_daily_limit)
        RewardAvailability.NO_REWARDS -> stringResource(R.string.no_reward_none)
    }
    return if (pityReady) base + stringResource(R.string.no_reward_pity_carry) else base
}
