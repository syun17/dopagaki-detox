package com.dopagaki.detox.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 余白の段階（docs/design-system.md「4. 余白とレイアウト」）。段階の外の値を使わない。 */
@Immutable
data class DetoxSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    /** 画面の左右の余白：一覧（タイムライン、設定の行）とトップアプリバー。 */
    val screenEdgeList: Dp = 16.dp,
    /** 画面の左右の余白：一覧以外（ボトムシート、ガチャ、実行中、ダイアログ、ロック画面）。 */
    val screenEdge: Dp = 24.dp,
)
