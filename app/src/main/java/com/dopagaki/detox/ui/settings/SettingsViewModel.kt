package com.dopagaki.detox.ui.settings

import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dopagaki.detox.AppContainer
import com.dopagaki.detox.data.AppState
import com.dopagaki.detox.domain.model.Mission
import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.model.Settings
import com.dopagaki.detox.domain.pending.ChangeClassifier
import com.dopagaki.detox.domain.pending.PendingChange
import com.dopagaki.detox.lock.Apps
import com.dopagaki.detox.lock.InstalledApp
import com.dopagaki.detox.ui.common.ticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

data class SettingsUiState(
    val loaded: Boolean = false,
    val app: AppState = AppState(),
    val now: Instant = Instant.EPOCH,
    val accessibilityOn: Boolean = false,
    val notificationsOn: Boolean = true,
)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(c.store.state, c.ticker()) { s, now ->
        SettingsUiState(
            loaded = s.loaded,
            app = s,
            now = now,
            accessibilityOn = Apps.isAccessibilityEnabled(c.appContext),
            notificationsOn = NotificationManagerCompat.from(c.appContext).areNotificationsEnabled(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _apps = MutableStateFlow<List<InstalledApp>?>(null)
    val apps: StateFlow<List<InstalledApp>?> = _apps

    fun loadApps() {
        if (_apps.value != null) return
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) { Apps.launchable(c.appContext) }
        }
    }

    /** 設定を変える。10分待ちになる項目があれば true を onDone に渡す（5.6）。 */
    fun updateSettings(new: Settings, onDone: (waits: Boolean) -> Unit = {}) {
        val waits = ChangeClassifier.splitSettings(c.store.state.value.settings, new).pending != null
        viewModelScope.launch {
            c.store.updateSettings(new)
            c.afterAction()
            onDone(waits)
        }
    }

    fun mission(id: String): Mission? = c.store.state.value.mission(id)

    /** 保存する。保存できたかと、10分待ちの項目があるかを返す。 */
    fun saveMission(m: Mission, onDone: (saved: Boolean, waits: Boolean) -> Unit) {
        val old = mission(m.id)
        val waits = (if (old == null) ChangeClassifier.splitAdd(m) else ChangeClassifier.splitEdit(old, m)).pending != null
        viewModelScope.launch {
            val ok = c.store.saveMission(m)
            c.afterAction()
            onDone(ok, waits)
        }
    }

    fun deleteMission(id: String) {
        viewModelScope.launch { c.store.deleteMission(id); c.afterAction() }
    }

    fun cancelPending(id: String) {
        viewModelScope.launch { c.store.cancelPending(id) }
    }

    fun requestReset() {
        viewModelScope.launch { c.store.requestReset(); c.afterAction() }
    }

    fun appLabel(pkg: String): String = Apps.label(c.appContext, pkg)

    fun newMissionId(): String = java.util.UUID.randomUUID().toString()

    fun canPostNotificationsRuntime(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val waiting: List<PendingChange> get() = c.store.state.value.waiting

    fun missionsOf(kind: MissionKind): List<Mission> = c.store.state.value.missions.filter { it.kind == kind }
}
