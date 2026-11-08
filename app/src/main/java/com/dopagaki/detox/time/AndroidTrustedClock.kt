package com.dopagaki.detox.time

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.dopagaki.detox.data.db.ClockAnchorEntity
import com.dopagaki.detox.data.db.RuntimeDao
import com.dopagaki.detox.domain.time.ClockAnchor
import com.dopagaki.detox.domain.time.ClockAnchors
import com.dopagaki.detox.domain.time.TrustedClock
import java.time.Instant

/**
 * 信頼時刻の Android 実装（設計書 6.4）。
 * 信頼時刻 = 基準時刻 + (今の elapsedRealtime − 基準を取ったときの elapsedRealtime)
 * 基準（ClockAnchor）は runtime_state.db に保存する。端末の時計は初回にネットワーク時刻が取れないときだけ使う。
 */
class AndroidTrustedClock(
    private val context: Context,
    private val dao: RuntimeDao,
) : TrustedClock {
    @Volatile private var anchor: ClockAnchor? = null
    @Volatile private var lastReturned: Instant = Instant.EPOCH

    /** 起動時に一度呼ぶ。保存した基準を読み、起動回数とネットワーク時刻で決め直して保存する。 */
    suspend fun initialize() {
        val saved = dao.anchor()?.toDomain()
        val resolved = ClockAnchors.resolve(
            saved = saved,
            bootCount = bootCount(),
            elapsedNow = SystemClock.elapsedRealtime(),
            networkNow = networkNow(),
            // 初回にネットワーク時刻が取れないときの基準にだけ使う（6.4）
            deviceNow = Instant.ofEpochMilli(System.currentTimeMillis()),
        )
        anchor = resolved
        dao.upsertAnchor(ClockAnchorEntity.from(resolved))
    }

    override fun now(): Instant {
        val a = checkNotNull(anchor) { "TrustedClock が初期化されていない" }
        val t = ClockAnchors.trustedNow(a, SystemClock.elapsedRealtime())
        // 過去に戻らない
        synchronized(this) {
            if (t > lastReturned) lastReturned = t
            return lastReturned
        }
    }

    /**
     * 1分ごとと操作のたびに呼ぶ。今の信頼時刻を保存し、ネットワーク時刻が初めて取れたら基準を取り直す。
     */
    suspend fun checkpoint() {
        val a = anchor ?: return
        val elapsed = SystemClock.elapsedRealtime()
        val net = networkNow()
        val next = if (net != null) {
            ClockAnchors.resolve(a, a.bootCount, elapsed, net, Instant.EPOCH)
        } else a
        val saved = ClockAnchors.checkpoint(next, elapsed)
        anchor = saved
        dao.upsertAnchor(ClockAnchorEntity.from(saved))
    }

    private fun bootCount(): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    /** ネットワーク時刻（Android 13 以降）。取れなければ null。 */
    private fun networkNow(): Instant? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return try {
            SystemClock.currentNetworkTimeClock().instant()
        } catch (e: java.time.DateTimeException) {
            null
        }
    }
}
