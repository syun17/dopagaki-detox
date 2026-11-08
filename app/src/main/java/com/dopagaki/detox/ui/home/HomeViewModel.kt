package com.dopagaki.detox.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dopagaki.detox.AppContainer
import com.dopagaki.detox.data.AppState
import com.dopagaki.detox.domain.lottery.Lottery
import com.dopagaki.detox.domain.model.Mission
import com.dopagaki.detox.domain.model.SessionSource
import com.dopagaki.detox.domain.model.Settings
import com.dopagaki.detox.domain.refresh.RefreshLimiter
import com.dopagaki.detox.domain.session.FeedSlot
import com.dopagaki.detox.domain.session.StartCheck
import com.dopagaki.detox.lock.Apps
import com.dopagaki.detox.ui.common.ticker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** タイムラインの1行（S01）。 */
data class FeedRow(val mission: Mission, val doneCount: Int, val isLater: Boolean)

data class HomeUiState(
    val loaded: Boolean = false,
    val rows: List<FeedRow> = emptyList(),
    val refreshRemaining: Int = 0,
    val nextRefreshIn: Duration? = null,
    /** 常時ロックがオンなのにユーザー補助サービスがオフ（6.4）。 */
    val accessibilityOff: Boolean = false,
    val feedRewardRemaining: Int = 0,
    val coins: Int = 0,
    val settings: Settings = Settings(),
    val now: Instant = Instant.EPOCH,
    val zone: ZoneId = ZoneId.systemDefault(),
)

/** ホームとスワイプで共通の、フィードの状態（ホームとスワイプは同じフィードを見る。5.1）。 */
class FeedViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(c.store.state, c.ticker()) { s, now -> build(s, now) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun build(s: AppState, now: Instant): HomeUiState {
        if (!s.loaded) return HomeUiState()
        val feed = s.feed.mapNotNull { s.mission(it) }
        val later = s.later.mapNotNull { s.mission(it.missionId) }
        val laterIds = later.map { it.id }.toSet()
        val rows = Lottery.homeTimeline(later, feed).map { FeedRow(it, s.doneCount(it.id), it.id in laterIds) }
        val today = c.store.today(s)
        return HomeUiState(
            loaded = true,
            rows = rows,
            refreshRemaining = RefreshLimiter.remaining(s.wallet.refreshTimes, now),
            nextRefreshIn = RefreshLimiter.nextAvailableAt(s.wallet.refreshTimes, now)?.let { Duration.between(now, it) },
            accessibilityOff = s.settings.rewardAppLock && !Apps.isAccessibilityEnabled(c.appContext),
            feedRewardRemaining = FeedSlot.remaining(s.wallet, today, s.settings),
            coins = s.wallet.coins,
            settings = s.settings,
            now = now,
            zone = c.store.zone(),
        )
    }

    /** スワイプだけの行（「あとで」を先頭にしない、フィードの20件）。 */
    fun swipeRows(s: HomeUiState): List<FeedRow> {
        val st = c.store.state.value
        return st.feed.mapNotNull { st.mission(it) }.filter { it.enabled }
            .map { m -> FeedRow(m, st.doneCount(m.id), st.later.any { it.missionId == m.id }) }
    }

    fun isLater(missionId: String): Boolean = c.store.state.value.later.any { it.missionId == missionId }

    fun check(row: FeedRow, source: SessionSource): StartCheck =
        c.store.checkStart(row.mission.id, if (row.isLater) SessionSource.LATER else source)

    fun start(row: FeedRow, source: SessionSource) {
        viewModelScope.launch {
            c.store.start(row.mission.id, if (row.isLater) SessionSource.LATER else source)
            c.afterAction()
        }
    }

    fun refresh(onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onDone(c.store.refreshFeed()) }
    }

    fun toggleLater(missionId: String) {
        viewModelScope.launch { c.store.toggleLater(missionId) }
    }

    fun appLabels(m: Mission): List<String> = m.linkedApps.map { Apps.label(c.appContext, it) }.sorted()
}
