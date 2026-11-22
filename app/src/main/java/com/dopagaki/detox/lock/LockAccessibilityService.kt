package com.dopagaki.detox.lock

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dopagaki.detox.MainActivity
import com.dopagaki.detox.container
import com.dopagaki.detox.domain.Rules
import com.dopagaki.detox.domain.lock.AppLockDecision
import com.dopagaki.detox.domain.lock.HomeEventAction
import com.dopagaki.detox.domain.lock.HomeEventRule
import com.dopagaki.detox.ui.theme.DetoxTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 報酬アプリの常時ロック（設計書 6.2、9.3）。
 * - 受け取るのは TYPE_WINDOW_STATE_CHANGED だけ。画面の内容は読まない（canRetrieveWindowContent=false）
 * - 前面のアプリのパッケージ名だけを使い、端末の外に送らない
 * - 対象アプリならロック画面を TYPE_ACCESSIBILITY_OVERLAY で重ねる（SYSTEM_ALERT_WINDOW は使わない）
 * - ご褒美の時間切れは、対象アプリが前面にあり画面がついている間、1秒ごとに信頼時刻で判定し、
 *   時間切れならロック画面を出して GLOBAL_ACTION_HOME で端末のホームに戻し、3秒見せてから消す
 * - ホームのイベントは遅れて届くことがある（6.2）。扱いは HomeEventRule：ロック画面を出している間は
 *   ホーム操作を送ってから消す。時間切れを判定している間と、時間切れのロック画面を見せている間は無視する
 * - ロック画面を出している間も1秒ごとに判定し直す（強制解除の待ちが終わったときなどに消す）
 * - 画面が消えたらロック画面を消し、判定もしない。キーガードの上には重ねない。画面がついたら判定し直す
 */
class LockAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var foreground: String? = null
    private var overlay: ComposeView? = null
    private var overlayOwner: OverlayOwner? = null
    private var overlayPackage: String? = null
    private var excluded: Set<String> = emptySet()

    /** ご褒美中に時間切れを判定している（9.3）。 */
    private var watchJob: Job? = null

    /** 最後に通した（ご褒美中の）対象アプリ。画面が消えている間に時間切れになったかの判定に使う。 */
    private var allowedPackage: String? = null

    /** 時間切れのロック画面を見せていて、決めた時間のあとに消す（6.2）。 */
    private var expiredJob: Job? = null

    /** ロック画面を出している間の判定し直し。 */
    private var recheckJob: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    watchJob?.cancel()
                    hideOverlay()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    // キーガードが出ている間は判定しない（解除したときの ACTION_USER_PRESENT で判定する）
                    if (isKeyguardLocked()) return
                    refreshPackages()
                    evaluate()
                }
            }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshPackages()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        refreshPackages()
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // ホームアプリやキーボードを入れ替えたときにも取り直す
        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(AudioManager::class.java)
                ?.addOnModeChangedListener(mainExecutor, modeListener as AudioManager.OnModeChangedListener)
        }
        scope.launch {
            container.ready.await()
            container.store.recordAccessibility(enabled = true)
            evaluate()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // キーボード・システムUI・システムのダイアログのイベントは無視する（9.3）
        if (pkg == SYSTEM_UI || pkg == ANDROID || pkg in imePackages()) return
        // ロック画面を出している間は、自分の重ね表示のイベントでは消さない（自分のアプリの画面に来たときだけ消す）
        if (pkg == packageName && overlay != null && event.className?.toString() != MainActivity::class.java.name) return
        if (pkg in homes) {
            // ホームのイベントは対象アプリより遅れて届き、実際には対象アプリが前面のままのことがある（6.2、9.3）
            val action = HomeEventRule.decide(
                overlayShown = overlay != null,
                showingExpired = expiredJob?.isActive == true,
                watchingExpiry = watchJob?.isActive == true,
                pinned = isPinned(),
            )
            when (action) {
                HomeEventAction.IGNORE -> return
                HomeEventAction.SEND_HOME_AND_HIDE -> {
                    // 対象アプリが前面に残っていても、ホーム操作で確実に裏へ回してから消す
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    foreground = pkg
                    allowedPackage = null
                    hideOverlay()
                    return
                }
                HomeEventAction.NORMAL -> Unit
            }
        }
        // 時間切れのロック画面を見せている間に、時間切れになったアプリ自身のイベントが来た（遅れて届いた、
        // またはホームに戻る前に来た）。3秒たったらもう一度ホーム操作を送って確実に裏へ回してから消す
        if (expiredJob?.isActive == true && pkg == overlayPackage) {
            foreground = pkg
            return
        }
        // ほかのアプリが前面に来たら、時間切れのロック画面を見せる時間は打ち切って判定し直す
        expiredJob?.cancel()
        expiredJob = null
        foreground = pkg
        evaluate()
    }

    private var imes: Set<String> = emptySet()
    private var homes: Set<String> = emptySet()
    private fun imePackages(): Set<String> = imes

    override fun onInterrupt() = Unit

    /** 既定のアプリやキーボードを変えたときのために、画面がつくたびにも取り直す。 */
    private fun refreshPackages() {
        excluded = Apps.excluded(this)
        imes = Apps.imePackages(this)
        homes = Apps.homePackages(this)
    }

    private fun isScreenOn(): Boolean = getSystemService(PowerManager::class.java)?.isInteractive == true

    private fun isKeyguardLocked(): Boolean = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    /** 画面の固定（アプリ固定）中か。固定中はホーム操作が効かない（6.2、9.3）。 */
    private fun isPinned(): Boolean =
        getSystemService(ActivityManager::class.java)?.lockTaskModeState.let { it != null && it != ActivityManager.LOCK_TASK_MODE_NONE }

    /**
     * 電話の着信中・通話中か（着信の通知や通話の操作を隠さない。9.3）。
     * 通話アプリ（VoIP）の通話中（MODE_IN_COMMUNICATION）は含めない。対象アプリ自身の通話でロックが外れないようにするため。
     */
    private fun isInCall(): Boolean = getSystemService(AudioManager::class.java)?.mode.let {
        it == AudioManager.MODE_RINGTONE || it == AudioManager.MODE_IN_CALL
    }

    /** 着信・通話が終わったときに判定し直す（Android 12 以降）。 */
    private val modeListener: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        AudioManager.OnModeChangedListener { evaluate() }
    } else {
        null
    }

    private fun evaluate() {
        val pkg = foreground ?: return
        if (!container.ready.isCompleted) return
        // キーガードの上には重ねない（緊急通報を隠さない。9.3）。解除したときの ACTION_USER_PRESENT で判定し直す
        if (isKeyguardLocked()) {
            hideOverlay()
            return
        }
        // 着信中・通話中は重ねない（9.3）。終わったら判定し直す
        if (isInCall()) {
            hideOverlay()
            return
        }
        if (pkg == packageName) {
            allowedPackage = null
            watchJob?.cancel()
            hideOverlay()
            return
        }
        when (container.store.decideApp(pkg, excluded)) {
            AppLockDecision.LOCKED -> {
                watchJob?.cancel()
                // 通していたアプリがロックに変わった＝画面が消えている間などに時間切れになった（9.3）
                if (allowedPackage == pkg) expire(pkg) else showOverlay(pkg)
                allowedPackage = null
            }
            AppLockDecision.ALLOWED -> {
                hideOverlay()
                allowedPackage = pkg
                watch(pkg)
            }
            AppLockDecision.NOT_TARGET -> {
                hideOverlay()
                allowedPackage = null
                watchJob?.cancel()
            }
        }
    }

    /**
     * 対象アプリが前面で画面がついている間、1秒ごとに時間切れを判定する。
     * ホームのイベントでは止めない（遅れて届くことがあるため。6.2）。ほかのアプリが前面に来たら止まる。
     */
    private fun watch(pkg: String) {
        watchJob?.cancel()
        watchJob = scope.launch {
            while (isActive && foreground == pkg && isScreenOn()) {
                delay(Rules.REWARD_EXPIRY_CHECK_INTERVAL.toMillis())
                // 待っている間にほかのアプリ（ドパガキデトックスを含む）が前面に来ていたら止める
                if (foreground != pkg) break
                // キーガードの上ではホーム操作もロック画面も出さない（解除したときに判定し直す）
                if (isKeyguardLocked()) continue
                if (container.store.decideApp(pkg, excluded) == AppLockDecision.LOCKED) {
                    allowedPackage = null
                    expire(pkg)
                    break
                }
            }
        }
    }

    /** 時間切れ：ロック画面を先に出してから端末のホームに戻し、決めた時間だけ見せて消す（6.2）。 */
    private fun expire(pkg: String) {
        showOverlay(pkg)
        performGlobalAction(GLOBAL_ACTION_HOME)
        // ホームに戻したので、前面は対象アプリではない（画面を消してつけ直してもロック画面をホームに出さない）
        foreground = null
        expiredJob?.cancel()
        expiredJob = scope.launch {
            delay(Rules.EXPIRED_LOCK_SHOW.toMillis())
            // 画面の固定中はホーム操作が効かず対象アプリが前面に残るので、ロック画面を出し続ける（6.2）
            if (isPinned()) {
                foreground = pkg
                return@launch
            }
            if (foreground == pkg) performGlobalAction(GLOBAL_ACTION_HOME)
            foreground = null
            hideOverlay()
        }
    }

    private fun showOverlay(pkg: String) {
        if (overlay != null && overlayPackage == pkg) return
        hideOverlay()
        // キーガードの上には重ねない（緊急通報を隠さない。9.3）
        if (isKeyguardLocked() || isInCall()) return
        val wm = getSystemService(WindowManager::class.java) ?: return
        val owner = OverlayOwner().also { it.start() }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                DetoxTheme {
                    AppLockedOverlay(
                        packageName = pkg,
                        onOpenApp = {
                            hideOverlay()
                            startActivity(Intent(this@LockAccessibilityService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // フォーカスを取らない（自分のウィンドウのイベントで消えないように）。タッチは受け取る
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(view, params)
        overlay = view
        overlayOwner = owner
        overlayPackage = pkg
        // 出している間も判定し直す（強制解除の待ちが終わった、常時ロックをオフにした、など。9.3）
        recheckJob = scope.launch {
            while (isActive) {
                delay(Rules.REWARD_EXPIRY_CHECK_INTERVAL.toMillis())
                if (expiredJob?.isActive != true) evaluate()
            }
        }
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        getSystemService(WindowManager::class.java)?.removeView(v)
        overlayOwner?.stop()
        overlay = null
        overlayOwner = null
        overlayPackage = null
        recheckJob?.cancel()
        recheckJob = null
        expiredJob?.cancel()
        expiredJob = null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanUp()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanUp()
        super.onDestroy()
    }

    private var cleaned = false
    private fun cleanUp() {
        if (cleaned) return
        cleaned = true
        hideOverlay()
        try { unregisterReceiver(screenReceiver) } catch (e: IllegalArgumentException) { }
        try { unregisterReceiver(packageReceiver) } catch (e: IllegalArgumentException) { }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(AudioManager::class.java)?.removeOnModeChangedListener(modeListener as AudioManager.OnModeChangedListener)
        }
        val c = container
        c.scope.launch { c.store.recordAccessibility(enabled = false) }
        scope.cancel()
    }

    companion object {
        private const val SYSTEM_UI = "com.android.systemui"
        private const val ANDROID = "android"
    }
}

/** 重ね表示の ComposeView に渡す Lifecycle・SavedState・ViewModelStore の持ち主。 */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    fun start() {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
