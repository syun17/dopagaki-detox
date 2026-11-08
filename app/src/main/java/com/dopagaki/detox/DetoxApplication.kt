package com.dopagaki.detox

import android.app.Application
import android.content.Context
import com.dopagaki.detox.data.DetoxStore
import com.dopagaki.detox.data.db.MainDatabase
import com.dopagaki.detox.data.db.RuntimeDatabase
import com.dopagaki.detox.data.prefs.PrefsStore
import com.dopagaki.detox.domain.Rules
import com.dopagaki.detox.notification.Notifier
import com.dopagaki.detox.time.AndroidTrustedClock
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DetoxApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}

val Context.container: AppContainer get() = (applicationContext as DetoxApplication).container

/** 依存の受け渡し（Hilt は使わない）。 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainDb = MainDatabase.create(appContext)
    private val runtimeDb = RuntimeDatabase.create(appContext)
    val clock = AndroidTrustedClock(appContext, runtimeDb.dao())
    val store = DetoxStore(mainDb.dao(), runtimeDb.dao(), PrefsStore(appContext), clock)

    val notifier = Notifier(appContext)

    /** 信頼時刻と状態の読み込みが終わったら完了する。 */
    val ready = CompletableDeferred<Unit>()

    fun refreshNotifications() {
        notifier.update(store.state.value, clock.now())
    }

    fun start() {
        scope.launch {
            clock.initialize()
            store.load()
            ready.complete(Unit)
            // 実行中・10分待ちが変わったら通知とアラームを更新する
            launch {
                store.state
                    .map { Triple(it.running, it.waiting, it.settings) }
                    .distinctUntilChanged()
                    .collect { refreshNotifications() }
            }
            var lastCheckpoint = 0L
            var ticks = 0L
            while (isActive) {
                store.tick()
                // 信頼時刻は1分ごとに保存する（6.4）
                if (ticks - lastCheckpoint >= Rules.TRUSTED_CLOCK_SAVE_INTERVAL.seconds) {
                    clock.checkpoint()
                    lastCheckpoint = ticks
                }
                ticks++
                delay(1_000)
            }
        }
    }

    /** 操作のあとに呼ぶ。信頼時刻を保存する（6.4「操作のたびに保存」）。 */
    fun afterAction() {
        scope.launch { clock.checkpoint() }
    }
}
