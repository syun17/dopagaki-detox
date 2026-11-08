package com.dopagaki.detox.domain.time

import java.time.Instant

/**
 * 信頼時刻（設計書 6.4）。
 *
 * 信頼時刻 = 基準時刻 + (今の elapsedRealtime − 基準を取ったときの elapsedRealtime)
 *
 * 約束：
 * - 過去に戻らない（呼ぶたびに前回以上の値を返す）。ネットワーク時刻で基準を取り直しても戻さない
 * - 端末の時計を変えても動かない
 *
 * ミッションの残り時間、一時停止、強制解除・有利な設定変更の10分、常時ロック解除後の30分、
 * ご褒美で対象アプリを開ける時間、更新回数、1日の切り替え、静かな時間帯は、すべてこの時刻で判定する。
 * 端末の時計（System.currentTimeMillis()、Instant.now() など）を判定に使ってはいけない。
 *
 * Android での実装は :app の time パッケージに置く（ClockAnchor を保存・復元する）。
 */
fun interface TrustedClock {
    fun now(): Instant
}
