package com.dopagaki.detox.ui.theme

import androidx.compose.ui.graphics.Color

// 値の正本は docs/design-system.md「2. 色」。画面からはこのファイルを直接使わず、
// MaterialTheme.colorScheme と DetoxTheme.colors を経由する。

// M3 のカラーロール
internal val Primary = Color(0xFF1E6B45)
internal val OnPrimary = Color(0xFFFFFFFF)
internal val GreenContainer = Color(0xFFD5E8D9)
internal val OnGreenContainer = Color(0xFF0F1F15)
internal val Surface = Color(0xFFFFFFFF)
internal val SurfaceContainerLow = Color(0xFFF7F8F6)
internal val SurfaceContainer = Color(0xFFF2F4F1)
internal val SurfaceContainerHigh = Color(0xFFE6E8E4)
internal val SurfaceContainerHighest = Color(0xFFE1E3DF)
internal val OnSurface = Color(0xFF191C1A)
internal val OnSurfaceVariant = Color(0xFF42493F)
internal val Outline = Color(0xFF72796F)
internal val OutlineVariant = Color(0xFFC2C9BD)
internal val InverseSurface = Color(0xFF2E3330)
internal val Error = Color(0xFFC2362F)
internal val ErrorContainer = Color(0xFFFBEDEC)
internal val Scrim = Color(0xFF000000)

// 拡張トークン
internal val TextSecondary = Color(0xFF5C6159)
internal val TextBody = Color(0xFF2E3330)
internal val Divider = Color(0xFFE6E8E4)
internal val SelectedRow = Color(0xFFEEF6F0)
internal val OnTintedSurface = Color(0xFFFFFFFF)
internal val OnTintedSurfaceTranslucent = Color(0xCCFFFFFF)
internal val Amber = Color(0xFF7A5600)

// カテゴリ色（前景／丸の背景／スワイプ背景）
internal val ReadingFg = Color(0xFF2152B8)
internal val ReadingBg = Color(0xFFDCE6FB)
internal val ReadingSwipe = Color(0xFFEEF2FD)
internal val ExerciseFg = Color(0xFF146C2E)
internal val ExerciseBg = Color(0xFFD7F0DC)
internal val ExerciseSwipe = Color(0xFFEBF8EE)
internal val StudyFg = Color(0xFF7B3A9E)
internal val StudyBg = Color(0xFFEEDFF7)
internal val StudySwipe = Color(0xFFF6EFFB)
internal val ChoresFg = Color(0xFF9A4A0A)
internal val ChoresBg = Color(0xFFFDE5D2)
internal val ChoresSwipe = Color(0xFFFEF2E8)
internal val MindFg = Color(0xFF55680C)
internal val MindBg = Color(0xFFE8F0CD)
internal val MindSwipe = Color(0xFFF4F8E6)
internal val RewardFg = Color(0xFF7A5600)
internal val RewardBg = Color(0xFFFCEFC7)
internal val RewardSwipe = Color(0xFFFEF7E3)

// レア度の色
internal val RarityRFg = Color(0xFF2152B8)
internal val RarityRBg = Color(0xFFDCE6FB)
internal val RaritySrFg = Color(0xFF7A5600)
internal val RaritySrBg = Color(0xFFFCEFC7)
internal val RaritySsrFg = Color(0xFFA8326E)
internal val RaritySsrBg = Color(0xFFFBE1EC)
