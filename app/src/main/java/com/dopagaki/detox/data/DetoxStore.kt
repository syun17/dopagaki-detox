package com.dopagaki.detox.data

import com.dopagaki.detox.data.db.AccessibilityOffEntity
import com.dopagaki.detox.data.db.FeedEntity
import com.dopagaki.detox.data.db.GachaTenEntity
import com.dopagaki.detox.data.db.LaterEntity
import com.dopagaki.detox.data.db.MainDao
import com.dopagaki.detox.data.db.MissionEntity
import com.dopagaki.detox.data.db.PendingEntity
import com.dopagaki.detox.data.db.RunningSessionEntity
import com.dopagaki.detox.data.db.RuntimeDao
import com.dopagaki.detox.data.db.SessionColumns
import com.dopagaki.detox.data.db.SessionEntity
import com.dopagaki.detox.data.db.WalletEntity
import com.dopagaki.detox.data.prefs.Meta
import com.dopagaki.detox.data.prefs.PrefsStore
import com.dopagaki.detox.domain.coin.Coins
import com.dopagaki.detox.domain.day.DayClock
import com.dopagaki.detox.domain.day.DayKey
import com.dopagaki.detox.domain.day.DayState
import com.dopagaki.detox.domain.lock.AppLockDecision
import com.dopagaki.detox.domain.lock.AppLockPolicy
import com.dopagaki.detox.domain.lottery.DrawContext
import com.dopagaki.detox.domain.lottery.Lottery
import com.dopagaki.detox.domain.lottery.PreviousMission
import com.dopagaki.detox.domain.lottery.RewardAvailability
import com.dopagaki.detox.domain.model.LaterItem
import com.dopagaki.detox.domain.model.Mission
import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.model.MissionValidation
import com.dopagaki.detox.domain.model.Session
import com.dopagaki.detox.domain.model.SessionResult
import com.dopagaki.detox.domain.model.SessionSource
import com.dopagaki.detox.domain.model.Settings
import com.dopagaki.detox.domain.model.Wallet
import com.dopagaki.detox.domain.pending.ChangeClassifier
import com.dopagaki.detox.domain.pending.PendingChange
import com.dopagaki.detox.domain.pending.PendingChanges
import com.dopagaki.detox.domain.pending.PendingPayload
import com.dopagaki.detox.domain.pending.PendingStatus
import com.dopagaki.detox.domain.pity.Pity
import com.dopagaki.detox.domain.preset.Presets
import com.dopagaki.detox.domain.refresh.RefreshLimiter
import com.dopagaki.detox.domain.session.GachaCost
import com.dopagaki.detox.domain.session.SessionRules
import com.dopagaki.detox.domain.session.Settlement
import com.dopagaki.detox.domain.session.StartCheck
import com.dopagaki.detox.domain.time.TrustedClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

/** 10連の結果（選ぶまで残す。5.3）。 */
data class GachaTenResult(val missionIds: List<String>, val drawnAt: Instant)

/** ユーザー補助がオフだった時間（6.4）。 */
data class OffInterval(val startedAt: Instant, val endedAt: Instant?)

/** アプリ全体の状態。DB の内容をメモリに持ち、変更は DB にも書く。 */
data class AppState(
    val loaded: Boolean = false,
    val missions: List<Mission> = emptyList(),
    val wallet: Wallet = Wallet(),
    val day: DayState? = null,
    val settings: Settings = Settings(),
    val meta: Meta = Meta(),
    val running: Session? = null,
    val history: List<Session> = emptyList(),
    val later: List<LaterItem> = emptyList(),
    val feed: List<String> = emptyList(),
    val pending: List<PendingChange> = emptyList(),
    val gachaTen: GachaTenResult? = null,
    val accessibilityOff: List<OffInterval> = emptyList(),
    /** 直前に終わったセッションの内訳（完了報告の結果表示。S06）。表示したら消す。 */
    val settlement: Settlement? = null,
) {
    fun mission(id: String): Mission? = missions.firstOrNull { it.id == id }

    val waiting: List<PendingChange> get() = pending.filter { it.status == PendingStatus.WAITING }

    /** 直前に始めたミッション（7.2、7.3）。 */
    val previous: PreviousMission?
        get() = (running ?: history.maxByOrNull { it.startedAt })?.let { PreviousMission(it.missionId, it.kind) }

    /** 実行中のセッションの強制解除の待ち。 */
    val sessionUnlock: PendingChange?
        get() = running?.let { r ->
            waiting.firstOrNull { (it.payload as? PendingPayload.ForceUnlockSession)?.sessionId == r.id }
        }

    val appLockUnlock: PendingChange?
        get() = waiting.firstOrNull { it.payload is PendingPayload.ForceUnlockAppLock }

    /** 「これまでの回数」（できた・一部できた）。8章 */
    fun doneCount(missionId: String): Int =
        history.count { it.missionId == missionId && SessionRules.countsAsDone(it.result) }

    fun gachaRewardCounts(today: DayKey): Map<String, Int> =
        (history + listOfNotNull(running))
            .filter { it.source.isGacha && it.kind == MissionKind.REWARD && it.dayKey == today }
            .groupingBy { it.missionId }.eachCount()
}

/** ガチャの結果。 */
sealed interface GachaOutcome {
    data class NotEnoughCoins(val missing: Int) : GachaOutcome
    data object NoCandidates : GachaOutcome
    data object Busy : GachaOutcome
    data class Single(val mission: Mission, val usedPity: Boolean) : GachaOutcome
    data class Ten(val missions: List<Mission>, val usedPity: Boolean) : GachaOutcome
}

/**
 * アプリの状態と操作。画面・ユーザー補助サービス・通知はここを通して読み書きする。
 * 時間の判定はすべて TrustedClock。
 */
class DetoxStore(
    private val main: MainDao,
    private val runtime: RuntimeDao,
    private val prefs: PrefsStore,
    val clock: TrustedClock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val random: Random = Random.Default,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val lottery get() = Lottery(random)

    fun zone(): ZoneId = zone.invoke()

    fun today(s: AppState = _state.value): DayKey = DayClock.advance(s.day, clock.now(), zone()).current

    private fun drawContext(s: AppState) =
        DrawContext(clock.now(), zone(), s.settings, s.previous, s.gachaRewardCounts(today(s)))

    // ---------- 読み込みと定期処理 ----------

    suspend fun load() = mutex.withLock {
        var meta = prefs.loadMeta()
        var missions = main.missions().map { it.toDomain() }
        val walletEntity = main.wallet()
        var wallet = walletEntity?.toWallet() ?: Wallet()
        var day = walletEntity?.toDayState()
        val now = clock.now()
        if (!meta.firstLaunchBonusGiven) {
            // 初回起動：プリセットと初回ボーナス（7.5、8章）
            if (missions.isEmpty()) {
                missions = Presets.missions(newId)
                main.replaceMissions(missions.mapIndexed { i, m -> MissionEntity.from(m, i) })
            }
            wallet = Coins.applyFirstLaunchBonus(wallet, alreadyGiven = false)
            meta = meta.copy(firstLaunchBonusGiven = true)
            prefs.saveMeta(meta)
        }
        day = DayClock.advance(day, now, zone())
        main.upsertWallet(WalletEntity.from(wallet, day))

        val ten = runtime.gachaTen()
        var s = AppState(
            loaded = true,
            missions = missions,
            wallet = wallet,
            day = day,
            settings = prefs.loadSettings(),
            meta = meta,
            running = runtime.running()?.columns?.toDomain(),
            history = main.sessions().map { it.columns.toDomain() },
            later = main.later().map { it.toDomain() },
            feed = main.feed().map { it.missionId },
            pending = runtime.pending().map { it.toDomain() },
            gachaTen = ten?.let { GachaTenResult(it.missionIds.split('\n').filter(String::isNotEmpty), Instant.ofEpochMilli(it.drawnAt)) },
            accessibilityOff = main.accessibilityOff().map {
                OffInterval(Instant.ofEpochMilli(it.startedAt), it.endedAt?.let(Instant::ofEpochMilli))
            },
        )
        s = ensureFeed(s)
        _state.value = s
        tickLocked()
    }

    /** フィードが空（初回、または中身がすべて消えた）なら、更新回数を使わずに作る。 */
    private suspend fun ensureFeed(s: AppState): AppState {
        val valid = s.feed.filter { id -> s.mission(id)?.enabled == true }
        if (valid.isNotEmpty()) return s
        val feed = lottery.drawFeed(s.missions, drawContext(s)).map { it.id }
        main.replaceFeed(feed.mapIndexed { i, id -> FeedEntity(i, id) })
        return s.copy(feed = feed)
    }

    /** 1秒ごと（画面・サービスが動いている間）に呼ぶ。1日の切り替え、一時停止の自動再開、10分待ちの反映。 */
    suspend fun tick() = mutex.withLock { tickLocked() }

    private suspend fun tickLocked() {
        var s = _state.value
        if (!s.loaded) return
        val now = clock.now()

        val day = DayClock.advance(s.day, now, zone())
        if (day != s.day) {
            s = s.copy(day = day)
            main.upsertWallet(WalletEntity.from(s.wallet, day))
        }

        s.running?.let { r ->
            val n = SessionRules.normalize(r, now)
            if (n != r) {
                s = s.copy(running = n)
                saveRunning(n)
            }
        }

        // 強制解除の待ちより先にミッションの時間が終わったら、解除は取り消す（5.4）
        val r = s.running
        if (r != null) {
            val unlock = s.sessionUnlock
            if (unlock != null && SessionRules.isOver(r, now) && r.endsAt <= unlock.appliesAt) {
                s = s.replacePending(PendingChanges.cancel(unlock))
                savePending(s.pending)
            }
        }

        for (change in PendingChanges.due(s.pending, now)) {
            s = applyPending(s, change, now)
        }
        _state.value = s
    }

    private fun AppState.replacePending(c: PendingChange) =
        copy(pending = pending.map { if (it.id == c.id) c else it })

    private suspend fun applyPending(state: AppState, change: PendingChange, now: Instant): AppState {
        var s = state
        val done = change.copy(status = PendingStatus.DONE)
        when (val p = change.payload) {
            is PendingPayload.ForceUnlockSession -> {
                val r = s.running
                if (r == null || r.id != p.sessionId) {
                    s = s.replacePending(PendingChanges.cancel(change))
                } else {
                    s = s.replacePending(done)
                    s = finishLocked(s, SessionResult.ABORTED, now)
                }
            }
            PendingPayload.ForceUnlockAppLock, is PendingPayload.UnlockApps -> s = s.replacePending(done)
            is PendingPayload.SettingsPatch -> {
                s = s.copy(settings = p.applyTo(s.settings)).replacePending(done)
                prefs.saveSettings(s.settings)
            }
            is PendingPayload.MissionPatch -> {
                s = s.copy(missions = s.missions.map { if (it.id == p.missionId) p.applyTo(it) else it }).replacePending(done)
                saveMissions(s.missions)
            }
            is PendingPayload.DeleteMission -> {
                s = removeMission(s, p.missionId).replacePending(done)
            }
            PendingPayload.ResetData -> {
                // 実行中はリセットしない（ロックを抜ける道にしない）。終わるまで待つ
                if (s.running != null) return s
                s = resetLocked(s.replacePending(done))
            }
        }
        savePending(s.pending)
        return s
    }

    private suspend fun removeMission(s: AppState, id: String): AppState {
        val next = s.copy(
            missions = s.missions.filterNot { it.id == id },
            later = s.later.filterNot { it.missionId == id },
            feed = s.feed.filterNot { it == id },
        )
        saveMissions(next.missions)
        main.replaceLater(next.later.map { LaterEntity(it.missionId, it.savedAt.toEpochMilli()) })
        main.replaceFeed(next.feed.mapIndexed { i, f -> FeedEntity(i, f) })
        return ensureFeed(next)
    }

    private suspend fun resetLocked(s: AppState): AppState {
        val missions = Presets.missions(newId)
        val pending = s.pending.map {
            if (it.status == PendingStatus.WAITING) PendingChanges.cancel(it) else it
        }
        var next = s.copy(
            missions = missions,
            wallet = Wallet(),
            settings = Settings(),
            history = emptyList(),
            later = emptyList(),
            feed = emptyList(),
            pending = pending,
            gachaTen = null,
            settlement = null,
        )
        saveMissions(missions)
        main.deleteAllSessions()
        main.replaceLater(emptyList())
        main.upsertWallet(WalletEntity.from(next.wallet, next.day))
        prefs.saveSettings(next.settings)
        runtime.deleteGachaTen()
        next = ensureFeed(next)
        return next
    }

    // ---------- フィード・あとで ----------

    /** 更新（ホームの引っ張って更新、スワイプの次の20枚）。上限なら false。7.4 */
    suspend fun refreshFeed(): Boolean = mutex.withLock {
        val s = _state.value
        val now = clock.now()
        if (!RefreshLimiter.canRefresh(s.wallet.refreshTimes, now)) return false
        val feed = lottery.drawFeed(s.missions, drawContext(s)).map { it.id }
        val wallet = s.wallet.copy(refreshTimes = RefreshLimiter.record(s.wallet.refreshTimes, now))
        main.replaceFeed(feed.mapIndexed { i, id -> FeedEntity(i, id) })
        main.upsertWallet(WalletEntity.from(wallet, s.day))
        _state.value = s.copy(feed = feed, wallet = wallet)
        true
    }

    suspend fun toggleLater(missionId: String) = mutex.withLock {
        val s = _state.value
        val later = if (s.later.any { it.missionId == missionId }) s.later.filterNot { it.missionId == missionId }
        else listOf(LaterItem(missionId, clock.now())) + s.later
        main.replaceLater(later.map { LaterEntity(it.missionId, it.savedAt.toEpochMilli()) })
        _state.value = s.copy(later = later)
    }

    // ---------- 確定・実行中 ----------

    fun checkStart(missionId: String, source: SessionSource): StartCheck {
        val s = _state.value
        val m = s.mission(missionId) ?: return StartCheck.Disabled
        return SessionRules.checkStart(m, source, s.wallet, s.settings, clock.now(), zone(), today(s), s.running, s.previous?.kind)
    }

    /** ホーム・スワイプ・あとでから始める。ご褒美はここでコインを引く（6.1）。 */
    suspend fun start(missionId: String, source: SessionSource): StartCheck = mutex.withLock {
        val s = _state.value
        val m = s.mission(missionId) ?: return StartCheck.Disabled
        val check = SessionRules.checkStart(m, source, s.wallet, s.settings, clock.now(), zone(), today(s), s.running, s.previous?.kind)
        if (check !is StartCheck.Ok) return check
        val next = startLocked(s, m, source)
        _state.value = next
        check
    }

    private suspend fun startLocked(s: AppState, m: Mission, source: SessionSource): AppState {
        val now = clock.now()
        val today = today(s)
        val (session, wallet) = SessionRules.start(newId(), m, source, s.wallet, now, today)
        val later = s.later.filterNot { it.missionId == m.id }
        saveRunning(session)
        main.upsertWallet(WalletEntity.from(wallet, s.day))
        if (later.size != s.later.size) main.replaceLater(later.map { LaterEntity(it.missionId, it.savedAt.toEpochMilli()) })
        return s.copy(running = session, wallet = wallet, later = later)
    }

    fun rewardAvailability(forGacha: Boolean): RewardAvailability {
        val s = _state.value
        return lottery.rewardAvailability(s.missions, drawContext(s), forGacha)
    }

    /** ガチャ（5.3）。単発は結果が出た時点で確定。10連は結果を保存し、1件選ぶまで待つ。 */
    suspend fun drawGacha(ten: Boolean): GachaOutcome = mutex.withLock {
        val s = _state.value
        if (s.running != null || s.gachaTen != null) return GachaOutcome.Busy
        val missing = GachaCost.missing(s.wallet, ten)
        if (missing > 0) return GachaOutcome.NotEnoughCoins(missing)
        val ctx = drawContext(s)
        val draw = (if (ten) lottery.drawGachaTen(s.missions, ctx, s.wallet.pityReady)
        else lottery.drawGachaSingle(s.missions, ctx, s.wallet.pityReady)) ?: return GachaOutcome.NoCandidates
        var wallet = GachaCost.pay(s.wallet, ten)
        if (draw.usedPity) wallet = Pity.consume(wallet)
        var next = s.copy(wallet = wallet)
        main.upsertWallet(WalletEntity.from(wallet, s.day))
        if (ten) {
            val result = GachaTenResult(draw.missions.map { it.id }, clock.now())
            runtime.upsertGachaTen(GachaTenEntity(0, result.missionIds.joinToString("\n"), result.drawnAt.toEpochMilli()))
            _state.value = next.copy(gachaTen = result)
            GachaOutcome.Ten(draw.missions, draw.usedPity)
        } else {
            val m = draw.missions.single()
            next = startLocked(next, m, SessionSource.GACHA_SINGLE)
            _state.value = next
            GachaOutcome.Single(m, draw.usedPity)
        }
    }

    /** 10連の結果から1件を選んで確定する（6.1）。選ばなかった分は消える。 */
    suspend fun pickGachaTen(index: Int): Boolean = mutex.withLock {
        val s = _state.value
        val ten = s.gachaTen ?: return false
        if (s.running != null) return false
        val m = ten.missionIds.getOrNull(index)?.let { s.mission(it) } ?: return false
        // 静かな時間帯は、10連の結果のご褒美も選べない（7.3）
        if (pickCheck(s, m) !is StartCheck.Ok) return false
        runtime.deleteGachaTen()
        _state.value = startLocked(s.copy(gachaTen = null), m, SessionSource.GACHA_TEN)
        true
    }

    /** 10連の結果から選べるか。 */
    fun pickCheck(s: AppState, m: Mission): StartCheck =
        SessionRules.checkStart(m, SessionSource.GACHA_TEN, s.wallet, s.settings, clock.now(), zone(), today(s), null, s.previous?.kind)

    suspend fun pause() = mutex.withLock {
        val s = _state.value
        val r = s.running ?: return
        val now = clock.now()
        if (!SessionRules.canPause(r, now, s.settings, s.sessionUnlock != null)) return
        val p = SessionRules.pause(r, now)
        saveRunning(p)
        _state.value = s.copy(running = p)
    }

    suspend fun resume() = mutex.withLock {
        val s = _state.value
        val r = s.running ?: return
        val p = SessionRules.resume(SessionRules.normalize(r, clock.now()), clock.now())
        saveRunning(p)
        _state.value = s.copy(running = p)
    }

    /** 強制解除を押す（残り10分以上のときだけ。一時停止中なら一時停止を終えてから待つ。5.4、6.3）。 */
    suspend fun requestSessionUnlock() = mutex.withLock {
        var s = _state.value
        var r = s.running ?: return
        val now = clock.now()
        if (s.sessionUnlock != null || !SessionRules.canShowForceUnlock(r, now, s.settings)) return
        if (SessionRules.normalize(r, now).isPaused) {
            r = SessionRules.resume(SessionRules.normalize(r, now), now)
            saveRunning(r)
            s = s.copy(running = r)
        }
        val change = PendingChanges.request(newId(), PendingPayload.ForceUnlockSession(r.id), now)
        s = s.copy(pending = s.pending + change)
        savePending(s.pending)
        _state.value = s
    }

    /** 常時ロックの強制解除を押す（6.3）。 */
    suspend fun requestAppLockUnlock() = mutex.withLock {
        val s = _state.value
        if (s.appLockUnlock != null) return
        val change = PendingChanges.request(newId(), PendingPayload.ForceUnlockAppLock, clock.now())
        val next = s.copy(pending = s.pending + change)
        savePending(next.pending)
        _state.value = next
    }

    suspend fun cancelPending(id: String) = mutex.withLock {
        val s = _state.value
        val c = s.pending.firstOrNull { it.id == id && it.status == PendingStatus.WAITING } ?: return
        val next = s.replacePending(PendingChanges.cancel(c))
        savePending(next.pending)
        _state.value = next
    }

    /** 完了報告（S06）。時間が終わってからだけ。 */
    suspend fun report(result: SessionResult): Settlement? = mutex.withLock {
        val s = _state.value
        val r = s.running ?: return null
        val now = clock.now()
        if (!SessionRules.isOver(r, now)) return null
        // 時間が終わったので、強制解除の待ちは取り消す
        var next = s.sessionUnlock?.let { s.replacePending(PendingChanges.cancel(it)) } ?: s
        if (next !== s) savePending(next.pending)
        next = finishLocked(next, result, now)
        _state.value = next
        next.settlement
    }

    private suspend fun finishLocked(s: AppState, result: SessionResult, now: Instant): AppState {
        val r = s.running ?: return s
        val finished = SessionRules.finish(r, result, now)
        val finishDay = DayClock.advance(s.day, now, zone()).current
        val settlement = SessionRules.settle(s.wallet, finished, finishDay)
        val missions = if (SessionRules.countsAsDone(result)) {
            s.missions.map { if (it.id == r.missionId) it.copy(lastDoneAt = now) else it }
        } else s.missions
        main.insertSession(SessionEntity(finished.id, SessionColumns.from(finished)))
        runtime.deleteRunning()
        main.upsertWallet(WalletEntity.from(settlement.wallet, s.day))
        if (missions !== s.missions) saveMissions(missions)
        return s.copy(
            running = null,
            history = s.history + finished,
            wallet = settlement.wallet,
            missions = missions,
            settlement = settlement,
        )
    }

    fun acknowledgeSettlement() {
        _state.value = _state.value.copy(settlement = null)
    }

    // ---------- 設定（5.5、5.6） ----------

    /** 設定を変える。厳しくする項目はすぐ、有利な項目は10分待つ。ロック中は変えない。 */
    suspend fun updateSettings(new: Settings) = mutex.withLock {
        val s = _state.value
        if (s.running != null) return
        val split = ChangeClassifier.splitSettings(s.settings, new)
        var next = s.copy(settings = split.applyNow)
        prefs.saveSettings(split.applyNow)
        split.pending?.let {
            next = next.copy(pending = next.pending + PendingChanges.request(newId(), it, clock.now()))
            savePending(next.pending)
        }
        _state.value = next
    }

    /** ミッションを足す・直す。入力に誤りがあれば保存しない（false）。 */
    suspend fun saveMission(mission: Mission): Boolean = mutex.withLock {
        val s = _state.value
        if (s.running != null || !MissionValidation.isValid(mission)) return false
        val old = s.mission(mission.id)
        val split = if (old == null) ChangeClassifier.splitAdd(mission) else ChangeClassifier.splitEdit(old, mission)
        val missions = if (old == null) s.missions + split.applyNow
        else s.missions.map { if (it.id == mission.id) split.applyNow else it }
        var next = s.copy(missions = missions)
        saveMissions(missions)
        split.pending?.let {
            next = next.copy(pending = next.pending + PendingChanges.request(newId(), it, clock.now()))
            savePending(next.pending)
        }
        _state.value = next
        true
    }

    /** ミッションを消す。ご褒美はすぐ、やることは10分待つ（5.6）。 */
    suspend fun deleteMission(id: String) = mutex.withLock {
        val s = _state.value
        if (s.running != null) return
        val m = s.mission(id) ?: return
        val split = ChangeClassifier.splitDelete(m)
        var next = if (split.deleteNow) removeMission(s, id) else s
        split.pending?.let {
            next = next.copy(pending = next.pending + PendingChanges.request(newId(), it, clock.now()))
            savePending(next.pending)
        }
        _state.value = next
    }

    /** データのリセット（10分待ち。5.6）。 */
    suspend fun requestReset() = mutex.withLock {
        val s = _state.value
        if (s.waiting.any { it.payload == PendingPayload.ResetData }) return
        val next = s.copy(pending = s.pending + PendingChanges.request(newId(), PendingPayload.ResetData, clock.now()))
        savePending(next.pending)
        _state.value = next
    }

    // ---------- 初回設定・ユーザー補助 ----------

    suspend fun completeOnboarding(consent: Boolean?) = mutex.withLock {
        val s = _state.value
        val meta = s.meta.copy(onboardingDone = true, a11yConsent = consent ?: s.meta.a11yConsent)
        prefs.saveMeta(meta)
        _state.value = s.copy(meta = meta)
    }

    suspend fun setA11yConsent(consent: Boolean) = mutex.withLock {
        val s = _state.value
        val meta = s.meta.copy(a11yConsent = consent)
        prefs.saveMeta(meta)
        _state.value = s.copy(meta = meta)
    }

    /** ユーザー補助がオフになっていた時間を記録する（6.4）。常時ロックがオンのときだけ数える。 */
    suspend fun recordAccessibility(enabled: Boolean) = mutex.withLock {
        val s = _state.value
        val open = s.accessibilityOff.lastOrNull()?.takeIf { it.endedAt == null }
        val now = clock.now()
        val next = when {
            !enabled && s.settings.rewardAppLock && open == null -> {
                val i = OffInterval(now, null)
                main.upsertAccessibilityOff(AccessibilityOffEntity(now.toEpochMilli(), null))
                s.accessibilityOff + i
            }
            (enabled || !s.settings.rewardAppLock) && open != null -> {
                main.upsertAccessibilityOff(AccessibilityOffEntity(open.startedAt.toEpochMilli(), now.toEpochMilli()))
                s.accessibilityOff.dropLast(1) + open.copy(endedAt = now)
            }
            else -> return
        }
        _state.value = s.copy(accessibilityOff = next)
    }

    // ---------- 常時ロック（6.2、9.3） ----------

    fun decideApp(packageName: String, excluded: Set<String>): AppLockDecision {
        val s = _state.value
        if (!s.loaded) return AppLockDecision.NOT_TARGET
        val now = clock.now()
        return AppLockPolicy.decide(
            packageName, now, s.settings, s.missions, excluded, s.running,
            PendingChanges.appLockGraceUntil(s.pending, now),
            PendingChanges.stillLockedApps(s.pending),
        )
    }

    // ---------- 保存 ----------

    private suspend fun saveMissions(missions: List<Mission>) =
        main.replaceMissions(missions.mapIndexed { i, m -> MissionEntity.from(m, i) })

    private suspend fun saveRunning(s: Session) =
        runtime.upsertRunning(RunningSessionEntity(0, SessionColumns.from(s)))

    private suspend fun savePending(list: List<PendingChange>) =
        runtime.upsertPending(list.map { PendingEntity.from(it) })
}
