package com.dopagaki.detox.domain.lottery

import com.dopagaki.detox.domain.Rarity
import com.dopagaki.detox.domain.TOKYO
import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.model.Settings
import com.dopagaki.detox.domain.model.TimeTag
import com.dopagaki.detox.domain.reward
import com.dopagaki.detox.domain.task
import com.dopagaki.detox.domain.tokyo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import kotlin.random.Random

class LotteryTest {
    private val noon = tokyo("2026-11-08T12:00")
    private val lottery = Lottery(Random(42))
    private fun ctx(now: java.time.Instant = noon, previous: PreviousMission? = null, counts: Map<String, Int> = emptyMap()) =
        DrawContext(now, TOKYO, Settings(), previous, counts)

    @Test fun `重みはp×(1+0_1×min(d,7))×c`() {
        val m = task("a", weight = 4, lastDoneAt = noon.minus(Duration.ofDays(10)))
        assertEquals(6.8, lottery.weight(m, noon, null), 1e-9)
        val m3 = task("b", weight = 4, lastDoneAt = noon.minus(Duration.ofDays(3)))
        assertEquals(5.2, lottery.weight(m3, noon, null), 1e-9)
    }

    @Test fun `一度も実施していなければdは7`() {
        assertEquals(3 * 1.7, lottery.weight(task("a", weight = 3), noon, null), 1e-9)
    }

    @Test fun `直前と同じは0_2倍、ご褒美の直後のご褒美は0`() {
        val m = task("a", weight = 4, lastDoneAt = noon)
        assertEquals(0.8, lottery.weight(m, noon, PreviousMission("a", MissionKind.TASK)), 1e-9)
        assertEquals(0.0, lottery.weight(reward("g"), noon, PreviousMission("x", MissionKind.REWARD)), 0.0)
    }

    @Test fun `レア度の出現率はおおよそ50・35・12・3`() {
        val l = Lottery(Random(1))
        val n = 200_000
        val counts = (1..n).groupingBy { l.drawRarity() }.eachCount()
        assertEquals(0.50, counts.getValue(Rarity.N) / n.toDouble(), 0.01)
        assertEquals(0.35, counts.getValue(Rarity.R) / n.toDouble(), 0.01)
        assertEquals(0.12, counts.getValue(Rarity.SR) / n.toDouble(), 0.01)
        assertEquals(0.03, counts.getValue(Rarity.SSR) / n.toDouble(), 0.005)
    }

    @Test fun `枠が空なら最も近いレア度、同じ近さなら下`() {
        assertEquals(Rarity.R, lottery.nearestRarity(Rarity.SR, setOf(Rarity.R, Rarity.SSR)))
        assertEquals(Rarity.SSR, lottery.nearestRarity(Rarity.SR, setOf(Rarity.N, Rarity.SSR)))
        assertEquals(Rarity.N, lottery.nearestRarity(Rarity.SSR, setOf(Rarity.N)))
        assertNull(lottery.nearestRarity(Rarity.N, emptySet()))
    }

    @Test fun `ご褒美の次の1回はやることだけ`() {
        val missions = listOf(task("t", rarity = Rarity.N), reward("g", rarity = Rarity.SSR))
        repeat(500) {
            val d = lottery.drawGachaSingle(missions, ctx(previous = PreviousMission("g", MissionKind.REWARD)), pityReady = false)!!
            assertFalse(d.missions.single().isReward)
        }
    }

    @Test fun `1日上限に達したご褒美はガチャに出ない`() {
        val missions = listOf(task("t", rarity = Rarity.N), reward("g", dailyLimit = 2))
        assertEquals(RewardAvailability.DAILY_LIMIT, lottery.rewardAvailability(missions, ctx(counts = mapOf("g" to 2)), forGacha = true))
        repeat(500) {
            val d = lottery.drawGachaSingle(missions, ctx(counts = mapOf("g" to 2)), pityReady = true)!!
            assertFalse(d.missions.single().isReward)
            assertFalse(d.usedPity)
        }
    }

    @Test fun `静かな時間帯の境界は23時を含み6時を含まない`() {
        val missions = listOf(task("t"), reward("g"))
        assertEquals(RewardAvailability.AVAILABLE, lottery.rewardAvailability(missions, ctx(tokyo("2026-11-08T22:59")), true))
        assertEquals(RewardAvailability.QUIET_HOURS, lottery.rewardAvailability(missions, ctx(tokyo("2026-11-08T23:00")), true))
        assertEquals(RewardAvailability.QUIET_HOURS, lottery.rewardAvailability(missions, ctx(tokyo("2026-11-09T05:59")), true))
        assertEquals(RewardAvailability.AVAILABLE, lottery.rewardAvailability(missions, ctx(tokyo("2026-11-09T06:00")), true))
    }

    @Test fun `天井が立っていればご褒美確定、ご褒美のある枠から選ぶ`() {
        val missions = listOf(task("t", rarity = Rarity.N), task("t2", rarity = Rarity.SR), reward("g", rarity = Rarity.SSR))
        repeat(200) {
            val d = lottery.drawGachaSingle(missions, ctx(), pityReady = true)!!
            assertEquals("g", d.missions.single().id)
            assertTrue(d.usedPity)
        }
    }

    @Test fun `静かな時間帯は天井を使わず持ち越す`() {
        val missions = listOf(task("t"), reward("g"))
        val d = lottery.drawGachaSingle(missions, ctx(tokyo("2026-11-08T23:30")), pityReady = true)!!
        assertFalse(d.usedPity)
        assertFalse(d.missions.single().isReward)
    }

    @Test fun `10連は10件で、天井なら少なくとも1件がご褒美`() {
        val missions = listOf(task("t", rarity = Rarity.N), task("r", rarity = Rarity.R), reward("g", rarity = Rarity.SSR))
        repeat(200) {
            val d = lottery.drawGachaTen(missions, ctx(), pityReady = true)!!
            assertEquals(10, d.missions.size)
            assertTrue(d.missions.any { it.isReward })
        }
    }

    @Test fun `フィードは重複なしで最大20件、候補が少なければ候補数まで`() {
        val many = (1..30).map { task("t$it", rarity = Rarity.entries[it % 3]) }
        val feed = lottery.drawFeed(many, ctx())
        assertEquals(20, feed.size)
        assertEquals(20, feed.map { it.id }.toSet().size)
        val few = (1..5).map { task("t$it") }
        assertEquals(5, lottery.drawFeed(few, ctx()).size)
    }

    @Test fun `フィードは時間帯タグが合うものが先`() {
        val missions = listOf(task("night", tags = setOf(TimeTag.NIGHT)), task("day", tags = setOf(TimeTag.DAY)), task("none"))
        repeat(50) {
            assertEquals("day", lottery.drawFeed(missions, ctx()).first().id)
        }
    }

    @Test fun `フィードのご褒美はガチャのおよそ10分の1`() {
        // N・R のやることと SSR のご褒美。ガチャでは SSR が 3%、フィードでは 0.3% 前後
        val l = Lottery(Random(7))
        val missions = listOf(task("n", rarity = Rarity.N), task("r", rarity = Rarity.R), reward("g", rarity = Rarity.SSR))
        var rewardFirst = 0
        val n = 100_000
        repeat(n) {
            // 1件目だけを見る（候補が減る前）
            val first = l.drawFeed(missions, ctx()).let { feed -> feed.firstOrNull { true } }
            if (first?.isReward == true) rewardFirst++
        }
        // 1件目にご褒美が来る確率 ≒ 0.3%（並べ替えで時間帯タグはないので順は引いた順）
        assertEquals(0.003, rewardFirst / n.toDouble(), 0.0015)
    }

    @Test fun `ホームはあとでを先頭に、重複を除く`() {
        val a = task("a"); val b = task("b"); val c = task("c")
        assertEquals(listOf("c", "a", "b"), Lottery.homeTimeline(listOf(c), listOf(a, c, b)).map { it.id })
    }

    @Test fun `候補が20件未満でもご褒美が入るのは確率0_1`() {
        val l = Lottery(Random(3))
        val missions = listOf(task("a"), task("b"), reward("g"))
        val n = 20_000
        val withReward = (1..n).count { l.drawFeed(missions, ctx()).any { it.isReward } }
        assertEquals(0.1, withReward / n.toDouble(), 0.01)
    }

    @Test fun `天井のときのSRとSSRは12対3`() {
        val l = Lottery(Random(5))
        val missions = listOf(task("t"), reward("sr", rarity = Rarity.SR), reward("ssr", rarity = Rarity.SSR))
        val n = 50_000
        val ssr = (1..n).count { l.drawGachaSingle(missions, ctx(), pityReady = true)!!.missions.single().id == "ssr" }
        assertEquals(0.2, ssr / n.toDouble(), 0.01)
    }

    @Test fun `10連でもご褒美を出せないときは天井を持ち越す`() {
        val missions = listOf(task("t"), reward("g"))
        val d = lottery.drawGachaTen(missions, ctx(previous = PreviousMission("g", MissionKind.REWARD)), pityReady = true)!!
        assertFalse(d.usedPity)
        assertTrue(d.missions.none { it.isReward })
    }

    @Test fun `ホームのあとでから無効のものを除く`() {
        val a = task("a", enabled = false)
        val b = task("b")
        assertEquals(listOf("b"), Lottery.homeTimeline(listOf(a), listOf(b)).map { it.id })
    }
}
