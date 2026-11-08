package com.dopagaki.detox.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dopagaki.detox.MainActivity
import com.dopagaki.detox.R
import com.dopagaki.detox.container
import com.dopagaki.detox.data.AppState
import com.dopagaki.detox.domain.Rules
import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.pending.PendingChange
import com.dopagaki.detox.domain.session.SessionRules
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.launch

/**
 * 通知（設計書 6.2、6.3、9.4）。残り時間は継続通知のカウントダウン（setUsesChronometer＋setChronometerCountDown）で出し、毎秒更新しない。
 * 終了・終了1分前・10分待ちの反映は AlarmManager（起動からの経過時間の基準）で起こす。
 * 通知が許可されていなくてもロックは動く（通知が出ないだけ。9.2）。
 */
class Notifier(private val context: Context) {
    private val nm = NotificationManagerCompat.from(context)

    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CH_ONGOING, context.getString(R.string.channel_ongoing), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CH_ALERT, context.getString(R.string.channel_alert), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** 状態が変わるたびに呼ぶ。now は信頼時刻。 */
    fun update(s: AppState, now: Instant) {
        val running = s.running?.let { SessionRules.normalize(it, now) }
        // 実行中のミッション
        if (running != null && !SessionRules.isOver(running, now)) {
            val remaining = SessionRules.remaining(running, now)
            val title = context.getString(R.string.notif_running_title, running.missionTitle)
            val b = base(CH_ONGOING, title).setOngoing(true).setOnlyAlertOnce(true)
            if (running.isPaused) {
                b.setContentText(context.getString(R.string.notif_paused))
            } else {
                countdown(b, remaining)
                b.setContentText(context.getString(R.string.notif_running_text))
            }
            post(ID_RUNNING, b)
            if (!running.isPaused) {
                schedule(REQ_END, remaining)
                if (running.kind == MissionKind.REWARD && remaining > Rules.REWARD_END_NOTICE_BEFORE) {
                    schedule(REQ_REWARD_SOON, remaining.minus(Rules.REWARD_END_NOTICE_BEFORE))
                } else cancelAlarm(REQ_REWARD_SOON)
            } else {
                cancelAlarm(REQ_END); cancelAlarm(REQ_REWARD_SOON)
                SessionRules.pauseRemaining(running, now)?.let { schedule(REQ_TICK, it) }
            }
        } else {
            nm.cancel(ID_RUNNING)
            cancelAlarm(REQ_END); cancelAlarm(REQ_REWARD_SOON)
        }

        // 10分待ち（強制解除・有利な設定変更）
        val waits = s.waiting
        for ((id, unlock) in listOf(ID_UNLOCK to s.sessionUnlock, ID_UNLOCK_APP to s.appLockUnlock)) {
            if (unlock != null) {
                val b = base(CH_ONGOING, context.getString(R.string.notif_unlock_title))
                    .setOngoing(true).setOnlyAlertOnce(true)
                    .setContentText(context.getString(R.string.notif_unlock_text))
                countdown(b, Duration.between(now, unlock.appliesAt))
                post(id, b)
            } else nm.cancel(id)
        }

        val next: PendingChange? = waits.minByOrNull { it.appliesAt }
        if (next != null) schedule(REQ_PENDING, Duration.between(now, next.appliesAt).coerceAtLeast(Duration.ZERO))
        else cancelAlarm(REQ_PENDING)
    }

    private fun base(channel: String, title: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_timer)
        .setContentTitle(title)
        .setContentIntent(openApp())
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)

    /** 端末の時計は表示（通知の「残り」）だけに使う。 */
    private fun countdown(b: NotificationCompat.Builder, remaining: Duration) {
        b.setUsesChronometer(true).setChronometerCountDown(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis() + remaining.toMillis())
            // 終了の通知が遅れても、マイナスの残り時間を出し続けない
            .setTimeoutAfter(remaining.toMillis().coerceAtLeast(1))
    }

    private fun post(id: Int, b: NotificationCompat.Builder) {
        if (!canNotify()) return
        try {
            nm.notify(id, b.build())
        } catch (e: SecurityException) {
            // 通知が許可されていない
        }
    }

    fun alert(kind: String) {
        val (title, text) = when (kind) {
            KIND_END -> context.getString(R.string.notif_end_title) to context.getString(R.string.notif_end_text)
            KIND_REWARD_SOON -> context.getString(R.string.notif_reward_soon_title) to context.getString(R.string.notif_reward_soon_text)
            else -> return
        }
        post(ID_ALERT, base(CH_ALERT, title).setContentText(text).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER))
    }

    private fun alarmIntent(req: Int): PendingIntent = PendingIntent.getBroadcast(
        context, req, Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_KIND, kindOf(req)),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun kindOf(req: Int) = when (req) {
        REQ_END -> KIND_END
        REQ_REWARD_SOON -> KIND_REWARD_SOON
        else -> KIND_TICK
    }

    /** 起動からの経過時間を基準にアラームを置く（端末の時計を変えてもずれない）。正確なアラームが許可されていなければ通常のもの。 */
    private fun schedule(req: Int, after: Duration) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = SystemClock.elapsedRealtime() + after.toMillis()
        val pi = alarmIntent(req)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
    }

    private fun cancelAlarm(req: Int) {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(req))
    }

    companion object {
        const val CH_ONGOING = "ongoing"
        const val CH_ALERT = "alert"
        private const val ID_RUNNING = 1
        private const val ID_UNLOCK = 2
        private const val ID_UNLOCK_APP = 4
        private const val ID_ALERT = 3
        private const val REQ_END = 10
        private const val REQ_REWARD_SOON = 11
        private const val REQ_PENDING = 12
        private const val REQ_TICK = 13
        const val EXTRA_KIND = "kind"
        const val KIND_END = "end"
        const val KIND_REWARD_SOON = "reward_soon"
        const val KIND_TICK = "tick"
    }
}

/** アラームで起こされたら、状態を進めて（10分待ちの反映など）通知を出す。 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val c = context.container
        c.scope.launch {
            try {
                c.ready.await()
                c.store.tick()
                when (val kind = intent.getStringExtra(Notifier.EXTRA_KIND)) {
                    Notifier.KIND_END, Notifier.KIND_REWARD_SOON -> c.notifier.alert(kind)
                }
                c.refreshNotifications()
            } finally {
                pending.finish()
            }
        }
    }
}

/** 再起動のあと、信頼時刻と通知を戻す（9.2）。Application の起動で信頼時刻の基準を取り直す。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val c = context.container
        c.scope.launch {
            try {
                c.ready.await()
                c.refreshNotifications()
            } finally {
                pending.finish()
            }
        }
    }
}
