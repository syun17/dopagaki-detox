package com.dopagaki.detox.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 部品の寸法（docs/design-system.md 4〜8章）。画面に dp を直書きせず、ここを経由する。 */
@Immutable
data class DetoxSizes(
    /** カテゴリの丸：タイムライン、ボトムシート、ガチャ結果。 */
    val avatarTimeline: Dp = 40.dp,
    val avatarSheet: Dp = 48.dp,
    val avatarResult: Dp = 36.dp,
    /** ボタンの高さ：通常、ダイアログの中。 */
    val buttonHeight: Dp = 48.dp,
    val buttonHeightDialog: Dp = 40.dp,
    /** 情報のチップの高さ。 */
    val chipHeight: Dp = 32.dp,
    /** 直線の進捗の高さ。 */
    val linearProgressHeight: Dp = 4.dp,
    /** 円形の進捗（実行中）の直径と線の太さ。 */
    val circularProgress: Dp = 260.dp,
    val circularProgressStroke: Dp = 8.dp,
    /** アイコン：基本の 16/20/24、カテゴリの丸の中、ボトムシート、スワイプの大きなアイコン。 */
    val iconSmall: Dp = 16.dp,
    val iconMedium: Dp = 20.dp,
    val iconLarge: Dp = 24.dp,
    val iconInAvatar: Dp = 22.dp,
    val iconSheet: Dp = 26.dp,
    val iconSwipeHero: Dp = 112.dp,
    /** スワイプの右側アクション：「これにする」（FAB）と「あとで」の丸。 */
    val swipeFab: Dp = 56.dp,
    val swipeSecondaryAction: Dp = 48.dp,
    /** 区切り線の太さ。 */
    val divider: Dp = 1.dp,
)
