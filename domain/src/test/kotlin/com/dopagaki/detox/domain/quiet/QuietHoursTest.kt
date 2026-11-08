package com.dopagaki.detox.domain.quiet

import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.preset.Presets
import com.dopagaki.detox.domain.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class QuietHoursTest {
    private val s = LocalTime.of(23, 0)
    private val e = LocalTime.of(6, 0)

    @Test fun `日をまたぐ範囲で23時を含み6時を含まない`() {
        assertFalse(QuietHours.isQuiet(LocalTime.of(22, 59), s, e))
        assertTrue(QuietHours.isQuiet(LocalTime.of(23, 0), s, e))
        assertTrue(QuietHours.isQuiet(LocalTime.of(3, 0), s, e))
        assertFalse(QuietHours.isQuiet(LocalTime.of(6, 0), s, e))
    }

    @Test fun `プリセットは7件で、時間からのレア度の初期値は5_5のとおり`() {
        var i = 0
        assertEquals(7, Presets.missions { "id${i++}" }.size)
        assertEquals(Rarity.N, Presets.defaultRarity(MissionKind.TASK, 14))
        assertEquals(Rarity.R, Presets.defaultRarity(MissionKind.TASK, 15))
        assertEquals(Rarity.SR, Presets.defaultRarity(MissionKind.TASK, 30))
        assertEquals(Rarity.SR, Presets.defaultRarity(MissionKind.REWARD, 15))
        assertEquals(Rarity.SSR, Presets.defaultRarity(MissionKind.REWARD, 16))
    }

    @Test fun `開始と終了が同じなら静かな時間帯はない`() {
        assertFalse(QuietHours.isQuiet(LocalTime.of(23, 0), s, s))
    }

    @Test fun `ミッションの入力を検証する`() {
        val ok = com.dopagaki.detox.domain.reward("g")
        val v = com.dopagaki.detox.domain.model.MissionValidation
        assertTrue(v.isValid(ok))
        assertFalse(v.isValid(ok.copy(rarity = Rarity.N)))
        assertFalse(v.isValid(ok.copy(durationMin = 61)))
        assertFalse(v.isValid(ok.copy(dailyLimit = null)))
        assertFalse(v.isValid(com.dopagaki.detox.domain.task("t").copy(linkedApps = setOf("x"))))
    }
}
