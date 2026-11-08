package com.dopagaki.detox.domain.pending

import com.dopagaki.detox.domain.Rarity
import com.dopagaki.detox.domain.Rules
import com.dopagaki.detox.domain.SettingsDefaults
import com.dopagaki.detox.domain.preset.Presets
import com.dopagaki.detox.domain.model.Mission
import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.model.Settings
import com.dopagaki.detox.domain.quiet.QuietHours
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalTime

/** PendingChange.target（8章）。 */
enum class PendingTarget { MISSION, APP_LOCK, SETTINGS }

/** PendingChange.status（8章）。 */
enum class PendingStatus { WAITING, CANCELLED, DONE }

/** 10分待ってから反映する変更の中身（8章 PendingChange.payload）。 */
@Serializable
sealed interface PendingPayload {
    val target: PendingTarget

    /** ミッション中のロックの強制解除（6.3）。 */
    @Serializable
    @SerialName("force_unlock_session")
    data class ForceUnlockSession(val sessionId: String) : PendingPayload {
        override val target get() = PendingTarget.MISSION
    }

    /** 報酬アプリの常時ロックの強制解除。反映したあと30分だけ対象アプリを通す（6.3）。 */
    @Serializable
    @SerialName("force_unlock_app_lock")
    data object ForceUnlockAppLock : PendingPayload {
        override val target get() = PendingTarget.APP_LOCK
    }

    /** 設定の有利な変更。null の項目は変えない。 */
    @Serializable
    @SerialName("settings")
    data class SettingsPatch(
        val rewardAppLock: Boolean? = null,
        val allowForceUnlock: Boolean? = null,
        val allowPause: Boolean? = null,
        val quietEnabled: Boolean? = null,
        /** "HH:mm" */
        val quietStart: String? = null,
        val quietEnd: String? = null,
        val feedRewardDailyLimit: Int? = null,
    ) : PendingPayload {
        override val target get() = if (rewardAppLock != null) PendingTarget.APP_LOCK else PendingTarget.SETTINGS

        fun applyTo(s: Settings): Settings = s.copy(
            rewardAppLock = rewardAppLock ?: s.rewardAppLock,
            allowForceUnlock = allowForceUnlock ?: s.allowForceUnlock,
            allowPause = allowPause ?: s.allowPause,
            quietEnabled = quietEnabled ?: s.quietEnabled,
            quietStart = quietStart?.let(LocalTime::parse) ?: s.quietStart,
            quietEnd = quietEnd?.let(LocalTime::parse) ?: s.quietEnd,
            feedRewardDailyLimit = feedRewardDailyLimit ?: s.feedRewardDailyLimit,
        )
    }

    /** ミッションの有利な項目の変更。反映のときにその項目だけを書き換える（5.6）。 */
    @Serializable
    @SerialName("mission")
    data class MissionPatch(
        val missionId: String,
        val durationMin: Int? = null,
        val weight: Int? = null,
        val dailyLimit: Int? = null,
        val rarity: Rarity? = null,
        val enabled: Boolean? = null,
        val removeLinkedApps: Set<String> = emptySet(),
    ) : PendingPayload {
        override val target get() = if (removeLinkedApps.isNotEmpty()) PendingTarget.APP_LOCK else PendingTarget.MISSION

        fun isEmpty(): Boolean = durationMin == null && weight == null && dailyLimit == null &&
            rarity == null && enabled == null && removeLinkedApps.isEmpty()

        fun applyTo(m: Mission): Mission = m.copy(
            durationMin = durationMin ?: m.durationMin,
            weight = weight ?: m.weight,
            dailyLimit = dailyLimit ?: m.dailyLimit,
            rarity = rarity ?: m.rarity,
            enabled = enabled ?: m.enabled,
            linkedApps = m.linkedApps - removeLinkedApps,
        )
    }

    /** やることの削除（5.6：ご褒美が出やすくなるので待つ）。 */
    @Serializable
    @SerialName("delete_mission")
    data class DeleteMission(val missionId: String) : PendingPayload {
        override val target get() = PendingTarget.MISSION
    }

    /** 消したご褒美の対象アプリのロックを外す（5.6：ご褒美の削除はすぐ、対象アプリのロックは10分後に外れる）。 */
    @Serializable
    @SerialName("unlock_apps")
    data class UnlockApps(val packages: Set<String>) : PendingPayload {
        override val target get() = PendingTarget.APP_LOCK
    }

    /** データのリセット（5.6）。 */
    @Serializable
    @SerialName("reset")
    data object ResetData : PendingPayload {
        override val target get() = PendingTarget.SETTINGS
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun encode(p: PendingPayload): String = json.encodeToString(serializer(), p)
        fun decode(text: String): PendingPayload = json.decodeFromString(serializer(), text)
    }
}

/** 10分待ちの変更（8章 PendingChange）。強制解除と有利な設定変更の両方を扱う。 */
data class PendingChange(
    val id: String,
    val payload: PendingPayload,
    val requestedAt: Instant,
    val appliesAt: Instant,
    val status: PendingStatus,
) {
    val target: PendingTarget get() = payload.target
}

object PendingChanges {
    /** 新しい待ちを作る。appliesAt = 依頼時刻 + 10分（信頼時刻）。 */
    fun request(id: String, payload: PendingPayload, now: Instant): PendingChange =
        PendingChange(id, payload, now, now.plus(SettingsDefaults.UNLOCK_WAIT), PendingStatus.WAITING)

    fun cancel(change: PendingChange): PendingChange = change.copy(status = PendingStatus.CANCELLED)

    /** 反映する時刻になった待ち（古い順）。 */
    fun due(changes: List<PendingChange>, now: Instant): List<PendingChange> =
        changes.filter { it.status == PendingStatus.WAITING && it.appliesAt <= now }.sortedBy { it.appliesAt }

    /** 常時ロックの強制解除のあと、対象アプリを通す期限。期限内でなければ null。6.3 */
    fun appLockGraceUntil(changes: List<PendingChange>, now: Instant): Instant? =
        changes.filter { it.status == PendingStatus.DONE && it.payload is PendingPayload.ForceUnlockAppLock }
            .map { it.appliesAt.plus(SettingsDefaults.UNLOCK_GRACE) }
            .filter { now < it }
            .maxOrNull()

    /** 消したご褒美の対象アプリのうち、まだロックを外していないもの。 */
    fun stillLockedApps(changes: List<PendingChange>): Set<String> =
        changes.filter { it.status == PendingStatus.WAITING }
            .mapNotNull { it.payload as? PendingPayload.UnlockApps }
            .flatMap { it.packages }.toSet()
}

/** ミッションの削除：すぐ消すか、10分待つ変更。 */
data class DeleteSplit(val deleteNow: Boolean, val pending: PendingPayload?)

/** 設定の変更を、すぐ反映する部分と10分待つ部分に分ける（5.6）。 */
data class SettingsSplit(val applyNow: Settings, val pending: PendingPayload.SettingsPatch?)

/** ミッションの編集を、すぐ反映する部分と10分待つ部分に分ける（5.6）。 */
data class MissionSplit(val applyNow: Mission, val pending: PendingPayload?)

object ChangeClassifier {

    fun splitSettings(old: Settings, new: Settings): SettingsSplit {
        var now = old.copy(dailyGoalMin = new.dailyGoalMin)
        var patch = PendingPayload.SettingsPatch()

        // 常時ロック：オフにするのは待ち、オンはすぐ
        if (new.rewardAppLock != old.rewardAppLock) {
            if (new.rewardAppLock) now = now.copy(rewardAppLock = true)
            else patch = patch.copy(rewardAppLock = false)
        }
        // 強制解除・一時停止：許可にするのは待ち
        if (new.allowForceUnlock != old.allowForceUnlock) {
            if (!new.allowForceUnlock) now = now.copy(allowForceUnlock = false)
            else patch = patch.copy(allowForceUnlock = true)
        }
        if (new.allowPause != old.allowPause) {
            if (!new.allowPause) now = now.copy(allowPause = false)
            else patch = patch.copy(allowPause = true)
        }
        // ホーム・スワイプ枠：増やすのは待ち
        if (new.feedRewardDailyLimit != old.feedRewardDailyLimit) {
            if (new.feedRewardDailyLimit < old.feedRewardDailyLimit) now = now.copy(feedRewardDailyLimit = new.feedRewardDailyLimit)
            else patch = patch.copy(feedRewardDailyLimit = new.feedRewardDailyLimit)
        }
        // 静かな時間帯：新しい範囲が古い範囲を含むならすぐ、それ以外（短くする・ずらす・オフ）は待ち
        val quietChanged = new.quietEnabled != old.quietEnabled || new.quietStart != old.quietStart || new.quietEnd != old.quietEnd
        if (quietChanged) {
            val stricter = when {
                !old.quietEnabled -> true
                !new.quietEnabled -> false
                else -> QuietHours.covers(new.quietStart, new.quietEnd, old.quietStart, old.quietEnd)
            }
            if (stricter) now = now.copy(quietEnabled = new.quietEnabled, quietStart = new.quietStart, quietEnd = new.quietEnd)
            else patch = patch.copy(
                quietEnabled = new.quietEnabled,
                quietStart = new.quietStart.toString(),
                quietEnd = new.quietEnd.toString(),
            )
        }
        return SettingsSplit(now, patch.takeIf { it != PendingPayload.SettingsPatch() })
    }

    /**
     * ミッションを足す。やることはすぐ（時間から決まるより高いレア度は、その差を10分待つ。5.5）。
     * ご褒美は無効の状態ですぐ足し、有効にするのを10分待つ。
     */
    fun splitAdd(new: Mission): MissionSplit {
        if (new.kind == MissionKind.TASK) {
            val cap = Presets.defaultRarity(MissionKind.TASK, new.durationMin)
            return if (new.rarity > cap) MissionSplit(new.copy(rarity = cap), PendingPayload.MissionPatch(new.id, rarity = new.rarity))
            else MissionSplit(new, null)
        }
        return if (!new.enabled) MissionSplit(new, null)
        else MissionSplit(new.copy(enabled = false), PendingPayload.MissionPatch(new.id, enabled = true))
    }

    /**
     * ミッションを消す。やることは10分待つ。ご褒美はすぐ消すが、対象アプリのロックが外れるのは10分後。
     */
    fun splitDelete(mission: Mission): DeleteSplit = when {
        mission.kind == MissionKind.TASK -> DeleteSplit(false, PendingPayload.DeleteMission(mission.id))
        mission.linkedApps.isNotEmpty() -> DeleteSplit(true, PendingPayload.UnlockApps(mission.linkedApps))
        else -> DeleteSplit(true, null)
    }

    /** ミッションの編集を項目ごとに分ける。種類（kind）は変えられない。 */
    fun splitEdit(old: Mission, new: Mission): MissionSplit {
        require(old.id == new.id && old.kind == new.kind)
        // 有利にならない項目はすぐ
        var now = old.copy(
            title = new.title,
            category = new.category,
            timeTags = new.timeTags,
            note = new.note,
        )
        var patch = PendingPayload.MissionPatch(old.id)
        if (old.kind == MissionKind.REWARD) {
            if (new.enabled != old.enabled) {
                if (new.enabled) patch = patch.copy(enabled = true) else now = now.copy(enabled = false)
            }
            if (new.durationMin != old.durationMin) {
                if (new.durationMin > old.durationMin) patch = patch.copy(durationMin = new.durationMin)
                else now = now.copy(durationMin = new.durationMin)
            }
            if (new.weight != old.weight) {
                if (new.weight > old.weight) patch = patch.copy(weight = new.weight) else now = now.copy(weight = new.weight)
            }
            if (new.dailyLimit != old.dailyLimit) {
                // null は抽選と同じく既定の2として比べる（Lottery の1日上限と同じ解釈）
                val o = old.dailyLimit ?: Rules.REWARD_DAILY_LIMIT_DEFAULT
                val n = new.dailyLimit ?: Rules.REWARD_DAILY_LIMIT_DEFAULT
                if (n > o) patch = patch.copy(dailyLimit = n) else now = now.copy(dailyLimit = n)
            }
            if (new.rarity != old.rarity) {
                if (new.rarity < old.rarity) patch = patch.copy(rarity = new.rarity) else now = now.copy(rarity = new.rarity)
            }
            val added = new.linkedApps - old.linkedApps
            val removed = old.linkedApps - new.linkedApps
            now = now.copy(linkedApps = now.linkedApps + added)
            if (removed.isNotEmpty()) patch = patch.copy(removeLinkedApps = removed)
        } else {
            if (new.enabled != old.enabled) {
                if (new.enabled) now = now.copy(enabled = true) else patch = patch.copy(enabled = false)
            }
            if (new.weight != old.weight) {
                if (new.weight < old.weight) patch = patch.copy(weight = new.weight) else now = now.copy(weight = new.weight)
            }
            if (new.rarity != old.rarity) {
                if (new.rarity > old.rarity) patch = patch.copy(rarity = new.rarity) else now = now.copy(rarity = new.rarity)
            }
            now = now.copy(durationMin = new.durationMin)
            // 時間を変えたとき、時間から決まるより高いレア度は、その差を10分待つ（5.5）
            if (new.durationMin != old.durationMin) {
                val cap = Presets.defaultRarity(MissionKind.TASK, new.durationMin)
                if (now.rarity > cap) {
                    patch = patch.copy(rarity = patch.rarity ?: now.rarity)
                    now = now.copy(rarity = cap)
                }
            }
        }
        return MissionSplit(now, patch.takeUnless { it.isEmpty() })
    }
}
