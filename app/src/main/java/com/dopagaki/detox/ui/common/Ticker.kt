package com.dopagaki.detox.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dopagaki.detox.AppContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 1秒ごとに信頼時刻を流す（残り時間の表示用）。 */
fun AppContainer.ticker(): Flow<Instant> = flow {
    // 信頼時刻の基準を読み終わるまでは時刻を読まない
    ready.await()
    while (true) {
        emit(clock.now())
        delay(1_000)
    }
}

/** ViewModel を AppContainer から作る。 */
inline fun <reified VM : ViewModel> containerFactory(crossinline create: () -> VM): ViewModelProvider.Factory =
    viewModelFactory { initializer { create() } }

/** 「12:34」「1:02:03」。 */
fun Duration.clock(): String {
    val total = seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** 「3,000」。 */
fun Int.grouped(): String = String.format(java.util.Locale.US, "%,d", this)

/**
 * 「○時○分に終了」の表示。端末の時計は表示だけに使う（6.4）。
 * 信頼時刻と端末の時計の差を足して、端末の時計での時刻にする。
 */
fun wallClockLabel(target: Instant, trustedNow: Instant, zone: ZoneId): String {
    val wallNow = Instant.ofEpochMilli(System.currentTimeMillis())
    val wall = wallNow.plus(Duration.between(trustedNow, target))
    return wall.atZone(zone).format(DateTimeFormatter.ofPattern("H:mm"))
}

/** 「6:00」「23:00」。 */
fun java.time.LocalTime.hm(): String = format(DateTimeFormatter.ofPattern("H:mm"))
