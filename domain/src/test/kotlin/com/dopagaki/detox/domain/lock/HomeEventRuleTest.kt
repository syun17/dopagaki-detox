package com.dopagaki.detox.domain.lock

import org.junit.Test
import org.junit.Assert.assertEquals

/** ホームのイベントの扱い（6.2、9.3）。 */
class HomeEventRuleTest {
    @Test
    fun `時間切れのロック画面を見せている間は無視する`() {
        assertEquals(HomeEventAction.IGNORE, HomeEventRule.decide(overlayShown = true, showingExpired = true, watchingExpiry = false, pinned = false))
    }

    @Test
    fun `ロック画面を出している間はホーム操作を送ってから消す`() {
        assertEquals(HomeEventAction.SEND_HOME_AND_HIDE, HomeEventRule.decide(overlayShown = true, showingExpired = false, watchingExpiry = false, pinned = false))
    }

    @Test
    fun `ご褒美中に時間切れを判定している間は無視して判定を続ける`() {
        assertEquals(HomeEventAction.IGNORE, HomeEventRule.decide(overlayShown = false, showingExpired = false, watchingExpiry = true, pinned = false))
    }

    @Test
    fun `ロック画面も判定もないときはふつうに前面のアプリとして扱う`() {
        assertEquals(HomeEventAction.NORMAL, HomeEventRule.decide(overlayShown = false, showingExpired = false, watchingExpiry = false, pinned = false))
    }

    @Test
    fun `画面の固定中はホーム操作が効かないのでロック画面を消さない`() {
        assertEquals(HomeEventAction.IGNORE, HomeEventRule.decide(overlayShown = true, showingExpired = false, watchingExpiry = false, pinned = true))
    }

    @Test
    fun `ロック画面と時間切れの判定が同時に立ったらホーム操作を送って消す`() {
        assertEquals(HomeEventAction.SEND_HOME_AND_HIDE, HomeEventRule.decide(overlayShown = true, showingExpired = false, watchingExpiry = true, pinned = false))
    }

    @Test
    fun `時間切れの3秒はロック画面が出ていなくても無視する`() {
        assertEquals(HomeEventAction.IGNORE, HomeEventRule.decide(overlayShown = false, showingExpired = true, watchingExpiry = false, pinned = false))
    }

    @Test
    fun `ホームの判定から設定・電話・SMS・自分自身を除く`() {
        val homes = HomeEventRule.homes(
            homeCapable = setOf("launcher", "com.android.settings", "dialer"),
            nonHomeExcluded = setOf("com.android.settings", "dialer", "sms", "self"),
        )
        assertEquals(setOf("launcher"), homes)
    }
}
