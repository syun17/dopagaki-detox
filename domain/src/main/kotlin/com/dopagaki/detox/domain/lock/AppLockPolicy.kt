package com.dopagaki.detox.domain.lock

import com.dopagaki.detox.domain.model.Mission
import com.dopagaki.detox.domain.model.Session
import com.dopagaki.detox.domain.model.Settings
import com.dopagaki.detox.domain.session.SessionRules
import java.time.Instant

/** 前面に来たアプリへの判定（6.2、6.3、9.3）。 */
enum class AppLockDecision {
    /** 対象外のアプリ、または常時ロックがオフ。 */
    NOT_TARGET,
    /** ご褒美ミッション中、または強制解除後の30分なので通す。 */
    ALLOWED,
    /** ロック画面を重ねる。 */
    LOCKED,
}

/**
 * 報酬アプリの常時ロック。ご褒美リストで対象アプリに選んだアプリ（無効のご褒美も含む）は普段ロックし、
 * ご褒美ミッション中（一時停止中を除く）と、常時ロックを強制解除したあとの30分だけ通す。
 * 除外アプリ（既定の電話・SMS・ホーム、端末の設定、システムUI、IME、自分自身）は対象にしない。
 */
object AppLockPolicy {
    /** stillLocked：消したご褒美の対象アプリのうち、10分待ちが終わっていないもの（5.6）。 */
    fun lockedApps(missions: List<Mission>, excluded: Set<String>, stillLocked: Set<String> = emptySet()): Set<String> =
        (missions.filter { it.isReward }.flatMap { it.linkedApps }.toSet() + stillLocked) - excluded

    fun decide(
        packageName: String,
        now: Instant,
        settings: Settings,
        missions: List<Mission>,
        excluded: Set<String>,
        running: Session?,
        graceUntil: Instant?,
        stillLocked: Set<String> = emptySet(),
    ): AppLockDecision {
        if (!settings.rewardAppLock) return AppLockDecision.NOT_TARGET
        if (packageName !in lockedApps(missions, excluded, stillLocked)) return AppLockDecision.NOT_TARGET
        if (graceUntil != null && now < graceUntil) return AppLockDecision.ALLOWED
        if (running != null && running.isRunning) {
            val s = SessionRules.normalize(running, now)
            val mission = missions.firstOrNull { it.id == s.missionId }
            if (mission != null && mission.isReward && packageName in mission.linkedApps &&
                !s.isPaused && now < s.endsAt
            ) return AppLockDecision.ALLOWED
        }
        return AppLockDecision.LOCKED
    }
}
