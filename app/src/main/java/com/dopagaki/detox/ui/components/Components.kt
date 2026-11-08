package com.dopagaki.detox.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.dopagaki.detox.R
import com.dopagaki.detox.domain.Category
import com.dopagaki.detox.domain.Rarity
import com.dopagaki.detox.domain.Rules
import com.dopagaki.detox.ui.theme.DetoxTheme

/** 区切り線。色は必ず divider（docs/design-system.md 2章）。 */
@Composable
fun Divider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = DetoxTheme.sizes.divider, color = DetoxTheme.colors.divider)
}

enum class AvatarSize { Timeline, Sheet }

/** カテゴリの丸。 */
@Composable
fun CategoryAvatar(category: Category, size: AvatarSize = AvatarSize.Timeline, modifier: Modifier = Modifier) {
    val colors = DetoxTheme.colors.category(category)
    val (box, icon) = when (size) {
        AvatarSize.Timeline -> DetoxTheme.sizes.avatarTimeline to DetoxTheme.sizes.iconInAvatar
        AvatarSize.Sheet -> DetoxTheme.sizes.avatarSheet to DetoxTheme.sizes.iconSheet
    }
    Box(
        modifier.size(box).background(colors.container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(category.iconRes), contentDescription = null, tint = colors.content, modifier = Modifier.size(icon))
    }
}

/** 設定の行の丸（中は1文字）。 */
@Composable
fun LetterAvatar(category: Category) {
    val colors = DetoxTheme.colors.category(category)
    Box(
        Modifier.size(DetoxTheme.sizes.avatarTimeline).background(colors.container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(category.letterRes),
            style = DetoxTheme.textStyles.timelineBody.copy(fontWeight = FontWeight.Bold),
            color = colors.content,
        )
    }
}

/** レア度の丸（ガチャ結果）。 */
@Composable
fun RarityAvatar(rarity: Rarity) {
    val c = DetoxTheme.colors.rarity(rarity)
    Box(
        Modifier.size(DetoxTheme.sizes.avatarResult).background(c.container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(rarity.name, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = c.onContainer)
    }
}

/** レア度の文字（色だけで伝えないよう、必ず文字で出す）。 */
@Composable
fun RarityText(rarity: Rarity, style: androidx.compose.ui.text.TextStyle = DetoxTheme.textStyles.timelineMeta) {
    Text(rarity.name, style = style.copy(fontWeight = FontWeight.Bold), color = DetoxTheme.colors.rarity(rarity).text)
}

/** 押せない表示用のチップ（仮決定 U5）。 */
@Composable
fun InfoChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    @DrawableRes icon: Int? = null,
    iconTint: Color = color,
    numeric: Boolean = false,
) {
    Surface(
        modifier = modifier.height(DetoxTheme.sizes.chipHeight),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(DetoxTheme.sizes.divider, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.padding(horizontal = DetoxTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.sm),
        ) {
            if (icon != null) Icon(painterResource(icon), null, tint = iconTint, modifier = Modifier.size(DetoxTheme.sizes.iconMedium))
            Text(text, style = if (numeric) DetoxTheme.textStyles.coinLabel else MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

/** 注意書きの箱。onClick があれば箱全体を押せる。 */
@Composable
fun NoticeBox(@DrawableRes icon: Int, text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val fg = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = DetoxTheme.spacing.md, horizontal = DetoxTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(painterResource(icon), null, tint = fg, modifier = Modifier.size(DetoxTheme.sizes.iconMedium))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = fg, modifier = Modifier.weight(1f))
            if (onClick != null) Icon(painterResource(R.drawable.ic_chevron_right), null, tint = fg, modifier = Modifier.size(DetoxTheme.sizes.iconMedium))
        }
    }
}

/** 設定の区分見出し。 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(
            start = DetoxTheme.spacing.screenEdgeList,
            end = DetoxTheme.spacing.screenEdgeList,
            top = DetoxTheme.spacing.lg,
            bottom = DetoxTheme.spacing.sm,
        ),
    )
}

enum class ButtonStyle { Filled, Tonal, Outlined, Text }

/** 全幅のボタン（高さ 48dp）。 */
@Composable
fun FullWidthButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Filled,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val m = modifier.fillMaxWidth().height(DetoxTheme.sizes.buttonHeight)
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(painterResource(icon), null, modifier = Modifier.size(DetoxTheme.sizes.iconMedium))
            Spacer(Modifier.size(DetoxTheme.spacing.sm))
        }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    when (style) {
        ButtonStyle.Filled -> Button(onClick, m, enabled) { content() }
        ButtonStyle.Tonal -> FilledTonalButton(onClick, m, enabled) { content() }
        ButtonStyle.Outlined -> OutlinedButton(
            onClick, m, enabled,
            border = BorderStroke(DetoxTheme.sizes.divider, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant),
        ) { content() }
        ButtonStyle.Text -> TextButton(onClick, m, enabled) { content() }
    }
}

/** ボタンの下の補足（中央揃え）。reason は押せない理由（onSurface で強める）。 */
@Composable
fun ButtonCaption(text: String, modifier: Modifier = Modifier, reason: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (reason) MaterialTheme.colorScheme.onSurface else DetoxTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

/** 天井の進み（7.3）。 */
@Composable
fun PityProgress(count: Int, ready: Boolean, modifier: Modifier = Modifier) {
    val shown = if (ready) Rules.PITY_THRESHOLD else count
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (ready) stringResource(R.string.pity_ready)
                else stringResource(R.string.pity_remaining, Rules.PITY_THRESHOLD - count),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.pity_fraction, shown, Rules.PITY_THRESHOLD),
                style = MaterialTheme.typography.bodyMedium,
                color = DetoxTheme.colors.textSecondary,
            )
        }
        ProgressLine(shown / Rules.PITY_THRESHOLD.toFloat())
        Text(
            stringResource(if (ready) R.string.pity_ready_note else R.string.pity_note),
            style = MaterialTheme.typography.bodySmall,
            color = DetoxTheme.colors.textSecondary,
        )
    }
}

/** 直線の進み（高さ 4dp、溝 surfaceContainerHighest、値 primary）。 */
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(DetoxTheme.sizes.linearProgressHeight),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = StrokeCap.Round,
        gapSize = DetoxTheme.spacing.none,
        drawStopIndicator = {},
    )
}

/** 行全体で選べるラジオの行。選択中は selectedRow の面。 */
@Composable
fun ChoiceRow(
    selected: Boolean,
    onSelect: () -> Unit,
    title: String,
    support: String,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) DetoxTheme.colors.selectedRow else MaterialTheme.colorScheme.surface)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
                .padding(vertical = DetoxTheme.spacing.md, horizontal = DetoxTheme.spacing.screenEdge),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.md),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = DetoxTheme.textStyles.timelineBody,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    support,
                    style = DetoxTheme.textStyles.coinMeta,
                    color = if (selected || !enabled) MaterialTheme.colorScheme.onSurfaceVariant else DetoxTheme.colors.textSecondary,
                )
            }
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
                colors = RadioButtonDefaults.colors(unselectedColor = MaterialTheme.colorScheme.outline),
            )
        }
        Divider()
    }
}

/** 空の状態・エラー（中央揃え）。 */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = DetoxTheme.spacing.screenEdge, vertical = DetoxTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = DetoxTheme.colors.textSecondary, textAlign = TextAlign.Center)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** ロック中の画面：戻る操作を止める（targetSdk 36 の予測型「戻る」。onBackPressed は使わない。9.4）。 */
@Composable
fun BlockBack() {
    BackHandler(enabled = true) {}
}

/** 上部の見出しの組（段階の表示・大見出し・補足）。S03-2、S06、S08、単発の結果で使う。 */
@Composable
fun ScreenHeading(eyebrow: String, title: String, support: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(PaddingValues(start = DetoxTheme.spacing.screenEdge, end = DetoxTheme.spacing.screenEdge, top = DetoxTheme.spacing.xxl, bottom = DetoxTheme.spacing.lg)),
        verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.sm),
    ) {
        Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (support != null) Text(support, style = MaterialTheme.typography.bodyMedium, color = DetoxTheme.colors.textSecondary)
    }
}
