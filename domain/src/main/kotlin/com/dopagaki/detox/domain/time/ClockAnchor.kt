package com.dopagaki.detox.domain.time

import java.time.Duration
import java.time.Instant

/** ClockAnchor.source（8章）。 */
enum class AnchorSource { NETWORK, DEVICE, CARRIED }

/** 信頼時刻の基準（8章 ClockAnchor）。elapsed は SystemClock.elapsedRealtime() のミリ秒。 */
data class ClockAnchor(
    val bootCount: Int,
    val elapsedAtAnchor: Long,
    val trustedAtAnchor: Instant,
    val source: AnchorSource,
    val lastSavedTrusted: Instant,
    val lastSavedElapsed: Long,
)

/**
 * 信頼時刻の基準の取り方（6.4）。Android の値（起動回数、elapsedRealtime、ネットワーク時刻、端末の時計）は引数で受ける。
 * 信頼時刻 = 基準時刻 + (今の elapsedRealtime − 基準を取ったときの elapsedRealtime)
 */
object ClockAnchors {

    fun trustedNow(anchor: ClockAnchor, elapsedNow: Long): Instant =
        anchor.trustedAtAnchor.plus(Duration.ofMillis(elapsedNow - anchor.elapsedAtAnchor))

    /**
     * 起動時・ネットワーク時刻が取れたときに基準を決め直す。
     * - 初回：ネットワーク時刻、なければ端末の時計
     * - 同じ起動：取り直さない。ただしネットワーク時刻が初めて取れたら取り直す（今の信頼時刻より過去には戻さない）
     * - 再起動後：ネットワーク時刻（最後に保存した信頼時刻より過去なら保存した値）、なければ最後に保存した信頼時刻
     */
    fun resolve(
        saved: ClockAnchor?,
        bootCount: Int,
        elapsedNow: Long,
        networkNow: Instant?,
        deviceNow: Instant,
    ): ClockAnchor {
        fun anchorAt(trusted: Instant, source: AnchorSource) =
            ClockAnchor(bootCount, elapsedNow, trusted, source, trusted, elapsedNow)

        if (saved == null) {
            return if (networkNow != null) anchorAt(networkNow, AnchorSource.NETWORK)
            else anchorAt(deviceNow, AnchorSource.DEVICE)
        }
        if (saved.bootCount == bootCount && elapsedNow >= saved.elapsedAtAnchor) {
            if (networkNow != null && saved.source != AnchorSource.NETWORK) {
                val current = trustedNow(saved, elapsedNow)
                return anchorAt(maxOf(networkNow, current), AnchorSource.NETWORK)
            }
            return saved
        }
        // 再起動した。ネットワーク時刻がなければ、電源が切れていた間は数えない
        val carried = saved.lastSavedTrusted
        return if (networkNow != null) anchorAt(maxOf(networkNow, carried), AnchorSource.NETWORK)
        else anchorAt(carried, AnchorSource.CARRIED)
    }

    /** 今の信頼時刻を保存用に記録する（1分ごと・操作のたび）。 */
    fun checkpoint(anchor: ClockAnchor, elapsedNow: Long): ClockAnchor =
        anchor.copy(lastSavedTrusted = trustedNow(anchor, elapsedNow), lastSavedElapsed = elapsedNow)
}
