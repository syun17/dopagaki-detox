package com.dopagaki.detox.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 値の正本は docs/design-system.md「3. タイポグラフィ」。
// フォントは端末標準（英数は Roboto、日本語は Noto Sans CJK）。M3 の既定の type scale を使う。
internal val DetoxTypography = Typography()

/** M3 の type scale にない、X のタイムラインに合わせたスタイル。 */
data class DetoxTextStyles(
    /** タイムラインの1・2行目、ガチャ結果の行（15sp／20sp）。 */
    val timelineBody: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    /** タイムラインの最下段（13sp／18sp・400）。スワイプのチップと上部ラベルは metaLabel。 */
    val timelineMeta: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    /** スワイプのチップと上部ラベル（13sp／18sp・500）。 */
    val metaLabel: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),

    // 等幅数字（tnum）のスタイル。残り時間とコインの表示に使う（docs/design-system.md 3章）
    /** 実行中の残り時間「12:34」、スワイプの「15分」（displayLarge＋tnum）。 */
    val timerLarge: TextStyle = DetoxTypography.displayLarge.copy(fontFeatureSettings = TABULAR_NUMS),
    /** 解除待ちの「9:42」（displayMedium＋tnum）。 */
    val timerMedium: TextStyle = DetoxTypography.displayMedium.copy(fontFeatureSettings = TABULAR_NUMS),
    /** ロック画面の解除待ち「9:42」（displaySmall＋tnum）。 */
    val timerSmall: TextStyle = DetoxTypography.displaySmall.copy(fontFeatureSettings = TABULAR_NUMS),
    /** チップ・トップバーのコイン「8,400コイン」（labelLarge＋tnum）。 */
    val coinLabel: TextStyle = DetoxTypography.labelLarge.copy(fontFeatureSettings = TABULAR_NUMS),
    /** タイムラインの最下段のコイン「12,000コイン」（timelineMeta＋tnum）。 */
    val coinMeta: TextStyle = timelineMeta.copy(fontFeatureSettings = TABULAR_NUMS),
)

private const val TABULAR_NUMS = "tnum"
