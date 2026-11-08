package com.dopagaki.detox.domain.lottery

import com.dopagaki.detox.domain.Rarity
import com.dopagaki.detox.domain.Rules
import com.dopagaki.detox.domain.SettingsDefaults
import com.dopagaki.detox.domain.model.Mission
import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.model.Settings
import com.dopagaki.detox.domain.model.TimeTag
import com.dopagaki.detox.domain.quiet.QuietHours
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** 直前に始めたミッション（重みの直前補正と「ご褒美の次はやること」に使う。7.2、7.3）。 */
data class PreviousMission(val missionId: String, val kind: MissionKind)

/** 抽選の条件。 */
data class DrawContext(
    val now: Instant,
    val zone: ZoneId,
    val settings: Settings,
    val previous: PreviousMission?,
    /** その日にガチャから始めた回数（ミッション ID ごと）。7.3 の1日上限に使う。 */
    val gachaRewardCountsToday: Map<String, Int> = emptyMap(),
)

/** ご褒美が出せるか（5.3「今はご褒美が出ません」）。 */
enum class RewardAvailability { AVAILABLE, QUIET_HOURS, AFTER_REWARD, DAILY_LIMIT, NO_REWARDS }

data class GachaDraw(val missions: List<Mission>, val usedPity: Boolean)

/**
 * 抽選（設計書 7.1〜7.4）。レア度を先に決め、その中から重み付きで選ぶ2段階方式。
 * 乱数は引数の Random を使う。
 */
class Lottery(private val random: Random) {

    /** 重み w = p × (1 + 0.1 × min(d, 7)) × c。一度も実施していなければ d = 7。7.2 */
    fun weight(mission: Mission, now: Instant, previous: PreviousMission?): Double {
        val days = mission.lastDoneAt
            ?.let { Duration.between(it, now).toDays().coerceIn(0, Rules.WEIGHT_DAYS_CAP.toLong()).toInt() }
            ?: Rules.WEIGHT_DAYS_CAP
        val c = when {
            previous == null -> 1.0
            previous.kind == MissionKind.REWARD && mission.isReward -> Rules.WEIGHT_REWARD_AFTER_REWARD
            previous.missionId == mission.id -> Rules.WEIGHT_SAME_AS_PREVIOUS
            else -> 1.0
        }
        return mission.weight * (1 + Rules.WEIGHT_DAY_FACTOR * days) * c
    }

    /** ガチャの出現率でレア度を引く。7.1 */
    fun drawRarity(allowed: Set<Rarity> = Rarity.entries.toSet()): Rarity {
        val rates = Rules.rarityRatePercent.filterKeys { it in allowed }
        val total = rates.values.sum()
        var r = random.nextInt(total)
        for ((rarity, rate) in rates) {
            if (r < rate) return rarity
            r -= rate
        }
        error("unreachable")
    }

    /** ご褒美を出せない理由。出せるなら AVAILABLE。 */
    fun rewardAvailability(missions: List<Mission>, ctx: DrawContext, forGacha: Boolean): RewardAvailability {
        val rewards = missions.filter { it.enabled && it.isReward }
        return when {
            rewards.isEmpty() -> RewardAvailability.NO_REWARDS
            QuietHours.isQuiet(ctx.settings, ctx.now, ctx.zone) -> RewardAvailability.QUIET_HOURS
            ctx.previous?.kind == MissionKind.REWARD -> RewardAvailability.AFTER_REWARD
            forGacha && rewards.none { underDailyLimit(it, ctx) } -> RewardAvailability.DAILY_LIMIT
            else -> RewardAvailability.AVAILABLE
        }
    }

    private fun underDailyLimit(mission: Mission, ctx: DrawContext): Boolean {
        val limit = mission.dailyLimit ?: Rules.REWARD_DAILY_LIMIT_DEFAULT
        return (ctx.gachaRewardCountsToday[mission.id] ?: 0) < limit
    }

    /** 抽選の候補（有効、重み > 0、ご褒美は出せるときだけ）。 */
    private fun candidates(missions: List<Mission>, ctx: DrawContext, forGacha: Boolean): List<Pair<Mission, Double>> {
        val rewardsOk = rewardAvailability(missions, ctx, forGacha) == RewardAvailability.AVAILABLE
        return missions
            .filter { it.enabled }
            .filter { !it.isReward || (rewardsOk && (!forGacha || underDailyLimit(it, ctx))) }
            .map { it to weight(it, ctx.now, ctx.previous) }
            .filter { it.second > 0.0 }
    }

    /**
     * 抽選したレア度に候補がないとき、候補のある最も近いレア度に移す（同じ近さなら下）。7.3
     * 候補がどこにもなければ null。
     */
    fun nearestRarity(drawn: Rarity, available: Set<Rarity>): Rarity? {
        if (drawn in available) return drawn
        val order = Rarity.entries
        val i = drawn.ordinal
        for (distance in 1 until order.size) {
            order.getOrNull(i - distance)?.takeIf { it in available }?.let { return it }
            order.getOrNull(i + distance)?.takeIf { it in available }?.let { return it }
        }
        return null
    }

    private fun weightedPick(items: List<Pair<Mission, Double>>): Mission {
        val total = items.sumOf { it.second }
        var r = random.nextDouble() * total
        for ((mission, w) in items) {
            if (r < w) return mission
            r -= w
        }
        return items.last().first
    }

    /** 2段階で1件を引く。候補がなければ null。 */
    private fun drawFrom(pool: List<Pair<Mission, Double>>, allowedRarities: Set<Rarity> = Rarity.entries.toSet()): Mission? {
        if (pool.isEmpty()) return null
        val drawn = drawRarity(allowedRarities)
        val rarity = nearestRarity(drawn, pool.map { it.first.rarity }.toSet()) ?: return null
        return weightedPick(pool.filter { it.first.rarity == rarity })
    }

    /** ガチャ単発（5.3）。天井が立っていて、ご褒美を出せるときはご褒美確定。出せなければ持ち越す。 */
    fun drawGachaSingle(missions: List<Mission>, ctx: DrawContext, pityReady: Boolean): GachaDraw? {
        val pool = candidates(missions, ctx, forGacha = true)
        val rewards = pool.filter { it.first.isReward }
        if (pityReady && rewards.isNotEmpty()) {
            val m = drawFrom(rewards, Rules.REWARD_RARITIES) ?: return null
            return GachaDraw(listOf(m), usedPity = true)
        }
        val m = drawFrom(pool) ?: return null
        return GachaDraw(listOf(m), usedPity = false)
    }

    /** ガチャ10連（5.3）。天井が立っていてご褒美を出せるときは、10件のうち少なくとも1件をご褒美にする。 */
    fun drawGachaTen(missions: List<Mission>, ctx: DrawContext, pityReady: Boolean): GachaDraw? {
        val pool = candidates(missions, ctx, forGacha = true)
        if (pool.isEmpty()) return null
        val results = MutableList(Rules.GACHA_TEN_COUNT) { drawFrom(pool)!! }
        val rewards = pool.filter { it.first.isReward }
        var usedPity = false
        if (pityReady && rewards.isNotEmpty()) {
            usedPity = true
            if (results.none { it.isReward }) {
                results[random.nextInt(results.size)] = drawFrom(rewards, Rules.REWARD_RARITIES)!!
            }
        }
        return GachaDraw(results, usedPity)
    }

    /**
     * おすすめフィード（7.3、7.4）。重複なしで最大20件。
     * ガチャと同じ方法で引き、ご褒美が出たら確率 0.9（1 − homeRewardRatio）でやることだけから引き直す。
     * 時間帯タグが今の時間帯に合うものを先に並べる（同じ組の中は引いた順）。
     */
    fun drawFeed(missions: List<Mission>, ctx: DrawContext): List<Mission> {
        val remaining = candidates(missions, ctx, forGacha = false).toMutableList()
        val picked = mutableListOf<Mission>()
        while (picked.size < Rules.FEED_SIZE && remaining.isNotEmpty()) {
            val m = drawFrom(remaining) ?: break
            if (m.isReward && random.nextDouble() >= SettingsDefaults.HOME_REWARD_RATIO) {
                // 引き直したご褒美は今回のフィードに入れない（候補が少ないときも、ご褒美が入るのは確率 0.1）
                remaining.removeAll { it.first.id == m.id }
                val tasks = remaining.filter { !it.first.isReward }
                if (tasks.isEmpty()) continue
                val t = drawFrom(tasks)!!
                picked += t
                remaining.removeAll { it.first.id == t.id }
                continue
            }
            picked += m
            remaining.removeAll { it.first.id == m.id }
        }
        val tag = TimeTag.of(ctx.now.atZone(ctx.zone).toLocalTime())
        val (matching, others) = picked.partition { tag in it.timeTags }
        return matching + others
    }

    companion object {
        /** ホームの並び：「あとで」を先頭に、そのあとにフィード（重複と無効のものは除く）。5.1 */
        fun homeTimeline(later: List<Mission>, feed: List<Mission>): List<Mission> {
            val shown = later.filter { it.enabled }
            val laterIds = shown.map { it.id }.toSet()
            return shown + feed.filter { it.enabled && it.id !in laterIds }
        }
    }
}
