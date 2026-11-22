package com.dopagaki.detox.navigation

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.core.app.NotificationManagerCompat
import com.dopagaki.detox.AppContainer
import com.dopagaki.detox.R
import com.dopagaki.detox.data.AppState
import com.dopagaki.detox.domain.lottery.RewardAvailability
import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.model.SessionResult
import com.dopagaki.detox.domain.model.SessionSource
import com.dopagaki.detox.domain.session.SessionRules
import com.dopagaki.detox.lock.Apps
import com.dopagaki.detox.ui.common.containerFactory
import com.dopagaki.detox.ui.common.ticker
import com.dopagaki.detox.ui.common.wallClockLabel
import com.dopagaki.detox.ui.components.LoadingState
import com.dopagaki.detox.ui.gacha.GachaRevealScreen
import com.dopagaki.detox.ui.gacha.GachaScreen
import com.dopagaki.detox.ui.gacha.GachaSingleResultScreen
import com.dopagaki.detox.ui.gacha.GachaTenResultScreen
import com.dopagaki.detox.ui.gacha.GachaUiState
import com.dopagaki.detox.ui.gacha.TenItem
import com.dopagaki.detox.ui.home.FeedRow
import com.dopagaki.detox.ui.home.FeedViewModel
import com.dopagaki.detox.ui.home.HomeScreen
import com.dopagaki.detox.ui.home.MissionDetailSheet
import com.dopagaki.detox.ui.onboarding.OnboardingScreen
import com.dopagaki.detox.ui.running.LinkedApp
import com.dopagaki.detox.ui.running.ReportScreen
import com.dopagaki.detox.ui.running.RunningScreen
import com.dopagaki.detox.ui.running.RunningUiState
import com.dopagaki.detox.ui.running.SettlementScreen
import com.dopagaki.detox.ui.settings.MissionEditScreen
import com.dopagaki.detox.ui.settings.SettingsScreen
import com.dopagaki.detox.ui.settings.SettingsViewModel
import com.dopagaki.detox.ui.swipe.SwipeScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** アプリ全体の状態と、ロック中の画面の操作。 */
class AppViewModel(val c: AppContainer) : ViewModel() {
    data class Ui(val s: AppState = AppState(), val now: Instant = Instant.EPOCH)

    val ui = combine(c.store.state, c.ticker()) { s, now -> Ui(s, now) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ui())

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch { block(); c.afterAction() }
    }

    fun pause() = act { c.store.pause() }
    fun resume() = act { c.store.resume() }
    fun forceUnlock() = act { c.store.requestSessionUnlock() }
    fun cancelUnlock() = act { c.store.sessionUnlock()?.let { c.store.cancelPending(it.id) } }
    fun report(r: SessionResult) = act { c.store.report(r) }
    fun pickTen(i: Int) = act { c.store.pickGachaTen(i) }
    fun draw(ten: Boolean) = act { c.store.drawGacha(ten) }
    fun acknowledgeReveal() = c.store.acknowledgeReveal()
    fun acknowledgeSettlement() = c.store.acknowledgeSettlement()
    fun consent(v: Boolean) = act { c.store.setA11yConsent(v) }
    fun finishOnboarding() = act {
        c.store.completeOnboarding(null)
        // ユーザー補助がオンになっていれば常時ロックをオンにする（厳しくする変更なのですぐ反映。仮決定 N18）
        if (Apps.isAccessibilityEnabled(c.appContext) && c.store.state.value.meta.a11yConsent == true) {
            c.store.updateSettings(c.store.state.value.settings.copy(rewardAppLock = true))
        }
    }

    fun enableLockIfReady() = act {
        val s = c.store.state.value
        if (s.meta.a11yConsent == true && Apps.isAccessibilityEnabled(c.appContext) && !s.settings.rewardAppLock) {
            c.store.updateSettings(s.settings.copy(rewardAppLock = true))
        }
    }

    fun runningUi(u: Ui): RunningUiState? {
        val r = u.s.running ?: return null
        val now = u.now
        val n = SessionRules.normalize(r, now)
        val m = u.s.mission(r.missionId)
        val unlock = u.s.sessionUnlock
        return RunningUiState(
            session = n,
            category = m?.category ?: com.dopagaki.detox.domain.Category.READING,
            note = m?.note ?: "",
            remaining = SessionRules.remaining(n, now),
            endLabel = wallClockLabel(n.endsAt, now, c.store.zone()),
            paused = n.isPaused,
            pauseRemaining = SessionRules.pauseRemaining(n, now),
            canPause = SessionRules.canPause(n, now, u.s.settings, unlock != null),
            pauseUsed = n.pauseCount >= com.dopagaki.detox.domain.Rules.PAUSE_MAX_COUNT,
            pauseAllowed = u.s.settings.allowPause,
            canForceUnlock = SessionRules.canShowForceUnlock(n, now, u.s.settings),
            unlockRemaining = unlock?.let { Duration.between(now, it.appliesAt).coerceAtLeast(Duration.ZERO) },
            linkedApps = if (r.kind == MissionKind.REWARD) m?.linkedApps.orEmpty().map { LinkedApp(it, Apps.label(c.appContext, it)) }.sortedBy { it.label } else emptyList(),
            notificationsAllowed = NotificationManagerCompat.from(c.appContext).areNotificationsEnabled(),
        )
    }

    fun gachaUi(u: Ui): GachaUiState = GachaUiState(
        loaded = u.s.loaded,
        coins = u.s.wallet.coins,
        pityCount = u.s.wallet.pityCount,
        pityReady = u.s.wallet.pityReady,
        availability = if (u.s.loaded) c.store.rewardAvailability(forGacha = true) else RewardAvailability.AVAILABLE,
        hasCandidates = u.s.missions.any { it.enabled },
        settings = u.s.settings,
    )

    fun appLabels(packages: Set<String>) = packages.map { Apps.label(c.appContext, it) }.sorted()
}

private fun com.dopagaki.detox.data.DetoxStore.sessionUnlock() = state.value.sessionUnlock

private enum class Tab(val route: String, @StringRes val label: Int, @DrawableRes val icon: Int, @DrawableRes val selectedIcon: Int) {
    Home("home", R.string.tab_home, R.drawable.ic_home, R.drawable.ic_home_fill),
    Swipe("swipe", R.string.tab_swipe, R.drawable.ic_swipe_vertical, R.drawable.ic_swipe_vertical_fill),
    Gacha("gacha", R.string.tab_gacha, R.drawable.ic_casino, R.drawable.ic_casino_fill),
    Settings("settings", R.string.tab_settings, R.drawable.ic_tune, R.drawable.ic_tune_fill),
}

/**
 * 画面の行き先（設計書 4章、6.1、8章末尾）。ロックがかかっている間は実行画面などに固定し、
 * ナビゲーションバーを出さず、戻る操作を止め、設定には入れない。
 */
@Composable
fun DetoxApp(container: AppContainer) {
    val vm: AppViewModel = viewModel(factory = containerFactory { AppViewModel(container) })
    val u by vm.ui.collectAsStateWithLifecycle()
    val s = u.s
    val context = LocalContext.current
    when {
        !s.loaded -> LoadingState()
        !s.meta.onboardingDone -> OnboardingScreen(
            accessibilityOn = Apps.isAccessibilityEnabled(context),
            consented = s.meta.a11yConsent == true,
            reshow = false,
            onConsent = vm::consent,
            onOpenAccessibilitySettings = { context.startActivity(Apps.accessibilitySettingsIntent()) },
            onFinish = vm::finishOnboarding,
        )
        s.reveal != null -> GachaRevealScreen(s.reveal.missions, onFinish = vm::acknowledgeReveal)
        s.settlement != null -> SettlementScreen(
            s.settlement,
            linkedAppLabels = if (s.settlement.session.kind == MissionKind.REWARD) vm.appLabels(s.mission(s.settlement.session.missionId)?.linkedApps.orEmpty()) else emptyList(),
            onHome = vm::acknowledgeSettlement,
        )
        s.running != null -> {
            val running = vm.runningUi(u)!!
            if (running.remaining.isZero && !running.paused) {
                ReportScreen(running.session, onReport = vm::report)
            } else {
                SingleResultThenRunning(vm, running, u)
            }
        }
        s.gachaTen != null -> {
            val today = container.store.today(s)
            val counts = s.gachaRewardCounts(today)
            val items = s.gachaTen.missionIds.mapIndexedNotNull { i, id ->
                s.mission(id)?.let { m ->
                    val limit = m.dailyLimit ?: com.dopagaki.detox.domain.Rules.REWARD_DAILY_LIMIT_DEFAULT
                    TenItem(i, m, container.store.pickCheck(s, m), (limit - (counts[m.id] ?: 0)).coerceAtLeast(0))
                }
            }
            GachaTenResultScreen(items, onPick = vm::pickTen)
        }
        else -> MainTabs(container, vm)
    }
}

/** 単発ガチャで確定した直後は結果の画面を出し、「スタート」で実行画面へ（仮決定 N13）。 */
@Composable
private fun SingleResultThenRunning(vm: AppViewModel, running: RunningUiState, u: AppViewModel.Ui) {
    // 開き直したときは結果を出さずに実行画面へ（確定はすでに済んでいる）
    var seen by androidx.compose.runtime.saveable.rememberSaveable(running.session.id) {
        mutableStateOf(running.session.source != SessionSource.GACHA_SINGLE || Duration.between(running.session.startedAt, u.now) > SINGLE_RESULT_WINDOW)
    }
    val mission = u.s.mission(running.session.missionId)
    if (!seen && mission != null) {
        GachaSingleResultScreen(mission, onStart = { seen = true })
        return
    }
    val context = LocalContext.current
    RunningScreen(
        state = running,
        onPause = vm::pause,
        onResume = vm::resume,
        onForceUnlock = vm::forceUnlock,
        onCancelUnlock = vm::cancelUnlock,
        onOpenApp = { pkg ->
            context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
    )
}

@Composable
private fun MainTabs(container: AppContainer, appVm: AppViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val feedVm: FeedViewModel = viewModel(factory = containerFactory { FeedViewModel(container) })
    val settingsVm: SettingsViewModel = viewModel(factory = containerFactory { SettingsViewModel(container) })
    val showBar = Tab.entries.any { it.route == route }
    fun message(text: String) { scope.launch { snackbar.showSnackbar(text) } }
    val policyUrl = stringResource(R.string.privacy_policy_url)
    val policyFailed = stringResource(R.string.privacy_policy_open_failed)
    val copyUrl = stringResource(R.string.copy_url)
    val urlCopied = stringResource(R.string.url_copied)
    val versionName = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }

    // プライバシーポリシーは端末のブラウザで開く（アプリは INTERNET を持たない）。
    // resolveActivity は <queries> がないと null を返すので使わず、開けなかったら Snackbar で URL のコピーを勧める
    fun openPrivacyPolicy() {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, policyUrl.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            scope.launch {
                val r = snackbar.showSnackbar(policyFailed, actionLabel = copyUrl, duration = SnackbarDuration.Long)
                if (r == SnackbarResult.ActionPerformed) {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText(copyUrl, policyUrl))
                    // Android 13 以降は端末がコピーを知らせるので、それより前だけ知らせる
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snackbar.showSnackbar(urlCopied)
                }
            }
        }
    }


    Scaffold(
        // 上のインセットは各画面（トップアプリバー、スワイプの背景）が処理する。下はナビゲーションバーが処理する
        contentWindowInsets = WindowInsets(0),
        // アクションつき（今はプライバシーポリシーの「URLをコピー」だけ）は、文が長いのでアクションを下の行に出す
        snackbarHost = { SnackbarHost(snackbar) { data -> Snackbar(data, actionOnNewLine = data.visuals.actionLabel != null) } },
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { tab ->
                        val selected = route == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(Tab.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(painterResource(if (selected) tab.selectedIcon else tab.icon), null) },
                            label = {
                                Text(
                                    stringResource(tab.label),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = Tab.Home.route) {
            composable(Tab.Home.route) {
                val state by feedVm.state.collectAsStateWithLifecycle()
                var open by remember { mutableStateOf<FeedRow?>(null) }
                HomeScreen(
                    state = state,
                    onRefresh = { done -> feedVm.refresh { done() } },
                    onOpenRow = { open = it },
                    onOpenAccessibility = { nav.navigate("disclosure") },
                    onOpenSettings = { nav.navigate(Tab.Settings.route) },
                    contentPadding = inner,
                )
                open?.let { row ->
                    val current = state.rows.firstOrNull { it.mission.id == row.mission.id } ?: row
                    MissionDetailSheet(
                        row = current,
                        check = feedVm.check(current, SessionSource.HOME),
                        state = state,
                        appLabels = feedVm.appLabels(current.mission),
                        onStart = { open = null; feedVm.start(current, SessionSource.HOME) },
                        onDismiss = { open = null },
                    )
                }
            }
            composable(Tab.Swipe.route) {
                val state by feedVm.state.collectAsStateWithLifecycle()
                SwipeScreen(feedVm, state, snackbar, onOpenSettings = { nav.navigate(Tab.Settings.route) }, contentPadding = inner)
            }
            composable(Tab.Gacha.route) {
                val u by appVm.ui.collectAsStateWithLifecycle()
                GachaScreen(appVm.gachaUi(u), onDraw = appVm::draw, onShowMissions = { nav.navigate(Tab.Settings.route) }, contentPadding = inner)
            }
            composable(Tab.Settings.route) {
                val state by settingsVm.state.collectAsStateWithLifecycle()
                SettingsScreen(
                    state = state,
                    vm = settingsVm,
                    onEditMission = { id, kind -> nav.navigate("edit/${kind.name}?id=${id ?: ""}") },
                    onOpenDisclosure = { nav.navigate("disclosure") },
                    onOpenNotificationSettings = {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    onOpenPrivacyPolicy = ::openPrivacyPolicy,
                    versionName = versionName,
                    onMessage = ::message,
                    contentPadding = inner,
                )
            }
            composable(
                "edit/{kind}?id={id}",
                arguments = listOf(navArgument("kind") { type = NavType.StringType }, navArgument("id") { type = NavType.StringType; defaultValue = "" }),
            ) { e ->
                val kind = MissionKind.valueOf(e.arguments?.getString("kind") ?: MissionKind.TASK.name)
                val id = e.arguments?.getString("id")?.takeIf { it.isNotEmpty() }
                MissionEditScreen(settingsVm, id, kind, onClose = { nav.popBackStack() }, onMessage = ::message)
            }
            composable("disclosure") {
                val u by appVm.ui.collectAsStateWithLifecycle()
                // この画面で同意し、ユーザー補助をオンにして離れたときだけ、常時ロックをオンにする（厳しくする変更なのですぐ反映）
                var agreedHere by remember { mutableStateOf(false) }
                androidx.compose.runtime.DisposableEffect(Unit) {
                    onDispose { if (agreedHere) appVm.enableLockIfReady() }
                }
                OnboardingScreen(
                    accessibilityOn = Apps.isAccessibilityEnabled(context),
                    consented = u.s.meta.a11yConsent == true,
                    reshow = true,
                    onConsent = { agreed -> agreedHere = agreed; appVm.consent(agreed) },
                    onOpenAccessibilitySettings = { context.startActivity(Apps.accessibilitySettingsIntent()) },
                    onFinish = { nav.popBackStack() },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

/** 単発ガチャの結果を出すのは、確定してからこの時間の間だけ（演出のすぐあと）。 */
private val SINGLE_RESULT_WINDOW: Duration = Duration.ofMinutes(1)
