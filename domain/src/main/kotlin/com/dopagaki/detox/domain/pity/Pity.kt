package com.dopagaki.detox.domain.pity

import com.dopagaki.detox.domain.Rules
import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.model.SessionResult
import com.dopagaki.detox.domain.model.Wallet

/**
 * 天井（設計書 7.3）。15分以上のやることを「できた」で5回続けると、次のガチャはご褒美確定。
 * 一部・できなかった・強制解除で 0。ご褒美と15分未満のやることは数えず、0 にも戻さない。
 * 天井が立ったら（pityReady）、ご褒美を出せるガチャまで持ち越す。
 */
object Pity {
    fun afterSession(wallet: Wallet, kind: MissionKind, durationMin: Int, result: SessionResult): Wallet {
        if (kind == MissionKind.REWARD || durationMin < Rules.PITY_MIN_DURATION_MIN) return wallet
        return when (result) {
            SessionResult.DONE -> {
                val count = wallet.pityCount + 1
                if (count >= Rules.PITY_THRESHOLD) wallet.copy(pityCount = 0, pityReady = true)
                else wallet.copy(pityCount = count)
            }
            SessionResult.PARTIAL, SessionResult.FAILED, SessionResult.ABORTED -> wallet.copy(pityCount = 0)
            SessionResult.RUNNING -> wallet
        }
    }

    /** 天井でご褒美を出したら使い切る。 */
    fun consume(wallet: Wallet): Wallet = wallet.copy(pityReady = false)
}
