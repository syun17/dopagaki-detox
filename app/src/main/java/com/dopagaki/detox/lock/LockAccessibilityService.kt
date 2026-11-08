package com.dopagaki.detox.lock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.PowerManager
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.platform.ComposeView
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
 *   時間切れなら GLOBAL_ACTION_HOME で端末のホームに戻してロック画面を出す
 * - 画面が消えている間は判定しない。画面がついたら判定し直す
 */
class LockAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var foreground: String? = null
    private var overlay: ComposeView? = null
    private var overlayOwner: OverlayOwner? = null
    private var overlayPackage: String? = null
    private var watchJob: Job? = null
    private var excluded: Set<String> = emptySet()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> watchJob?.cancel()
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    // 既定のアプリやキーボードを変えたときのために取り直す
                    excluded = Apps.excluded(this@LockAccessibilityService)
                    imes = Apps.imePackages(this@LockAccessibilityService)
                    evaluate()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        excluded = Apps.excluded(this)
        imes = Apps.imePackages(this)
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
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
        // 時間切れでホームに戻したあとは、ホームなどの除外アプリではロック画面を消さない（次のアプリを開くまで出す）
        if (sticky && pkg in excluded && pkg != packageName) return
        sticky = false
        foreground = pkg
        evaluate()
    }

    private var imes: Set<String> = emptySet()

    /** ご褒美の時間切れでロック画面を出したあと、ホームに戻っても出し続けている。 */
    private var sticky = false
    private fun imePackages(): Set<String> = imes

    override fun onInterrupt() = Unit

    private fun isScreenOn(): Boolean = getSystemService(PowerManager::class.java)?.isInteractive == true

    private fun evaluate() {
        val pkg = foreground ?: return
        if (!container.ready.isCompleted) return
        if (pkg == packageName) {
            hideOverlay()
            return
        }
        when (container.store.decideApp(pkg, excluded)) {
            AppLockDecision.LOCKED -> {
                showOverlay(pkg)
                watchJob?.cancel()
            }
            AppLockDecision.ALLOWED -> {
                hideOverlay()
                watch(pkg)
            }
            AppLockDecision.NOT_TARGET -> {
                hideOverlay()
                watchJob?.cancel()
            }
        }
    }

    /** 対象アプリが前面で画面がついている間、1秒ごとに時間切れを判定する。 */
    private fun watch(pkg: String) {
        watchJob?.cancel()
        watchJob = scope.launch {
            while (isActive && foreground == pkg && isScreenOn()) {
                delay(Rules.REWARD_EXPIRY_CHECK_INTERVAL.toMillis())
                if (container.store.decideApp(pkg, excluded) == AppLockDecision.LOCKED) {
                    // ロック画面を先に出してから端末のホームに戻す（6.2）。ホームに戻っても消さない
                    showOverlay(pkg)
                    sticky = true
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    break
                }
            }
        }
    }

    private fun showOverlay(pkg: String) {
        if (overlay != null && overlayPackage == pkg) return
        hideOverlay()
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
                            sticky = false
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
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        getSystemService(WindowManager::class.java)?.removeView(v)
        overlayOwner?.stop()
        overlay = null
        overlayOwner = null
        overlayPackage = null
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
