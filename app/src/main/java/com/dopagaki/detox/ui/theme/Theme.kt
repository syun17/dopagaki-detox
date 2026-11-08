package com.dopagaki.detox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.dopagaki.detox.domain.Category
import com.dopagaki.detox.domain.Rarity

// docs/design-system.md「2. 色」。M3 既定の紫系を残さないよう、すべてのロールを明示する。
// mockups にないロールは文書にある近い値で埋めている（10. 決定の記録）。
private val DetoxColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = GreenContainer,
    onPrimaryContainer = OnGreenContainer,
    inversePrimary = GreenContainer,
    secondary = Primary,
    onSecondary = OnPrimary,
    secondaryContainer = GreenContainer,
    onSecondaryContainer = OnGreenContainer,
    tertiary = Primary,
    onTertiary = OnPrimary,
    tertiaryContainer = GreenContainer,
    onTertiaryContainer = OnGreenContainer,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceContainerHigh,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceTint = Primary,
    inverseSurface = InverseSurface,
    inverseOnSurface = SurfaceContainer,
    error = Error,
    onError = OnPrimary,
    errorContainer = ErrorContainer,
    onErrorContainer = Error,
    outline = Outline,
    outlineVariant = OutlineVariant,
    scrim = Scrim,
    surfaceBright = Surface,
    surfaceDim = SurfaceContainerHighest,
    surfaceContainerLowest = Surface,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    primaryFixed = GreenContainer,
    primaryFixedDim = SurfaceContainerHighest,
    onPrimaryFixed = OnGreenContainer,
    onPrimaryFixedVariant = Primary,
    secondaryFixed = GreenContainer,
    secondaryFixedDim = SurfaceContainerHighest,
    onSecondaryFixed = OnGreenContainer,
    onSecondaryFixedVariant = Primary,
    tertiaryFixed = GreenContainer,
    tertiaryFixedDim = SurfaceContainerHighest,
    onTertiaryFixed = OnGreenContainer,
    onTertiaryFixedVariant = Primary,
)

/** カテゴリの色：前景（アイコン・文字）、丸の背景、スワイプ画面の背景。 */
@Immutable
data class CategoryColors(val content: Color, val container: Color, val swipeBackground: Color)

/** レア度の色：白の上の文字、丸の背景、丸の中の文字。 */
@Immutable
data class RarityColors(val text: Color, val container: Color, val onContainer: Color)

/** M3 の colorScheme にない色（docs/design-system.md「2. 色」の拡張トークン）。 */
@Immutable
class DetoxColors internal constructor() {
    /** 白の面の上の補足文字。色のある面の上では colorScheme.onSurfaceVariant を使う。 */
    val textSecondary: Color = TextSecondary
    val textBody: Color = TextBody

    /** 区切り線。HorizontalDivider には必ずこれを渡す。 */
    val divider: Color = Divider
    val selectedRow: Color = SelectedRow
    val onTintedSurface: Color = OnTintedSurface

    /** スワイプ画面の色付き背景の上に置く半透明（白 80%）の面。上部のページ表示など。 */
    val onTintedSurfaceTranslucent: Color = OnTintedSurfaceTranslucent
    val warning: Color = Amber
    val coin: Color = Amber

    private val reading = CategoryColors(ReadingFg, ReadingBg, ReadingSwipe)
    private val exercise = CategoryColors(ExerciseFg, ExerciseBg, ExerciseSwipe)
    private val study = CategoryColors(StudyFg, StudyBg, StudySwipe)
    private val chores = CategoryColors(ChoresFg, ChoresBg, ChoresSwipe)
    private val mind = CategoryColors(MindFg, MindBg, MindSwipe)
    private val reward = CategoryColors(RewardFg, RewardBg, RewardSwipe)

    fun category(category: Category): CategoryColors = when (category) {
        Category.READING -> reading
        Category.EXERCISE -> exercise
        Category.STUDY -> study
        Category.CHORES -> chores
        Category.MIND -> mind
        Category.GAME, Category.SNS -> reward
    }

    private val rarityN = RarityColors(TextSecondary, SurfaceContainerHigh, OnSurfaceVariant)
    private val rarityR = RarityColors(RarityRFg, RarityRBg, RarityRFg)
    private val raritySr = RarityColors(RaritySrFg, RaritySrBg, RaritySrFg)
    private val raritySsr = RarityColors(RaritySsrFg, RaritySsrBg, RaritySsrFg)

    fun rarity(rarity: Rarity): RarityColors = when (rarity) {
        Rarity.N -> rarityN
        Rarity.R -> rarityR
        Rarity.SR -> raritySr
        Rarity.SSR -> raritySsr
    }
}

private val LocalDetoxColors = staticCompositionLocalOf { DetoxColors() }
private val LocalDetoxTextStyles = staticCompositionLocalOf { DetoxTextStyles() }
private val LocalDetoxSpacing = staticCompositionLocalOf { DetoxSpacing() }
private val LocalDetoxSizes = staticCompositionLocalOf { DetoxSizes() }

/**
 * 拡張トークンの入口。
 * 例：DetoxTheme.colors.divider、DetoxTheme.textStyles.timerLarge、DetoxTheme.spacing.md、DetoxTheme.sizes.avatarTimeline
 */
object DetoxTheme {
    val sizes: DetoxSizes
        @Composable @ReadOnlyComposable get() = LocalDetoxSizes.current
    val colors: DetoxColors
        @Composable @ReadOnlyComposable get() = LocalDetoxColors.current
    val textStyles: DetoxTextStyles
        @Composable @ReadOnlyComposable get() = LocalDetoxTextStyles.current
    val spacing: DetoxSpacing
        @Composable @ReadOnlyComposable get() = LocalDetoxSpacing.current
}

/** アプリのテーマ。ライトのみ・dynamic color なし（docs/design-system.md 1章・10章）。 */
@Composable
fun DetoxTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalDetoxColors provides DetoxColors(),
        LocalDetoxTextStyles provides DetoxTextStyles(),
        LocalDetoxSpacing provides DetoxSpacing(),
        LocalDetoxSizes provides DetoxSizes(),
    ) {
        MaterialTheme(
            colorScheme = DetoxColorScheme,
            typography = DetoxTypography,
            shapes = DetoxShapes,
            content = content,
        )
    }
}
