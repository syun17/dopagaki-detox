package com.dopagaki.detox.ui.gacha

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.dopagaki.detox.R
import com.dopagaki.detox.domain.Category
import com.dopagaki.detox.domain.Rarity
import com.dopagaki.detox.domain.Rules
import com.dopagaki.detox.domain.coin.Coins
import com.dopagaki.detox.domain.lottery.RewardAvailability
import com.dopagaki.detox.domain.model.Mission
import com.dopagaki.detox.domain.model.SessionSource
import com.dopagaki.detox.domain.model.Settings
import com.dopagaki.detox.domain.session.GachaCost
import com.dopagaki.detox.domain.session.StartCheck
import com.dopagaki.detox.ui.common.grouped
import com.dopagaki.detox.ui.components.BlockBack
import com.dopagaki.detox.ui.components.ButtonCaption
import com.dopagaki.detox.ui.components.ChoiceRow
import com.dopagaki.detox.ui.components.Divider
import com.dopagaki.detox.ui.components.FullWidthButton
import com.dopagaki.detox.ui.components.InfoChip
import com.dopagaki.detox.ui.components.NoticeBox
import com.dopagaki.detox.ui.components.PityProgress
import com.dopagaki.detox.ui.components.RarityAvatar
import com.dopagaki.detox.ui.components.RarityText
import com.dopagaki.detox.ui.components.ScreenHeading
import com.dopagaki.detox.ui.components.labelRes
import com.dopagaki.detox.ui.format.coinText
import com.dopagaki.detox.ui.format.rewardUnavailableText
import com.dopagaki.detox.ui.home.MissionSummary
import com.dopagaki.detox.ui.theme.DetoxTheme

data class GachaUiState(
    val loaded: Boolean = false,
    val coins: Int = 0,
    val pityCount: Int = 0,
    val pityReady: Boolean = false,
    val availability: RewardAvailability = RewardAvailability.AVAILABLE,
    val hasCandidates: Boolean = true,
    val settings: Settings = Settings(),
)

/** S03 ガチャ（設計書 5.3）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GachaScreen(
    state: GachaUiState,
    onDraw: (ten: Boolean) -> Unit,
    onShowMissions: () -> Unit,
    contentPadding: PaddingValues,
) {
    Scaffold(
        modifier = Modifier.padding(contentPadding),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_gacha)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    if (state.loaded) {
                        InfoChip(
                            stringResource(R.string.coins, state.coins.grouped()),
                            icon = R.drawable.ic_toll,
                            iconTint = DetoxTheme.colors.coin,
                            numeric = true,
                            modifier = Modifier.padding(end = DetoxTheme.spacing.lg),
                        )
                    }
                },
            )
        },
    ) { inner ->
        if (!state.loaded) {
            com.dopagaki.detox.ui.components.LoadingState(Modifier.padding(inner))
            return@Scaffold
        }
        Column(
            Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = DetoxTheme.spacing.screenEdge),
        ) {
            GachaMachine(Modifier.align(Alignment.CenterHorizontally).padding(top = DetoxTheme.spacing.lg, bottom = DetoxTheme.spacing.sm))
            rewardUnavailableText(state.availability, state.settings, state.pityReady)?.let {
                NoticeBox(R.drawable.ic_info, it, Modifier.padding(top = DetoxTheme.spacing.sm))
            }
            PityProgress(state.pityCount, state.pityReady, Modifier.padding(top = DetoxTheme.spacing.sm))
            Row(Modifier.padding(top = DetoxTheme.spacing.xl), horizontalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.md)) {
                for (ten in listOf(false, true)) {
                    val missing = (GachaCost.cost(ten) - state.coins).coerceAtLeast(0)
                    val enabled = state.loaded && state.hasCandidates && missing == 0
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.xs)) {
                        val label = stringResource(
                            if (ten) R.string.gacha_ten else R.string.gacha_single,
                            GachaCost.cost(ten).grouped(),
                        )
                        val m = Modifier.fillMaxWidth().height(DetoxTheme.sizes.buttonHeight)
                        if (ten) Button(onClick = { onDraw(true) }, enabled = enabled, modifier = m) { Text(label) }
                        else OutlinedButton(
                            onClick = { onDraw(false) }, enabled = enabled, modifier = m,
                            border = BorderStroke(DetoxTheme.sizes.divider, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant),
                        ) { Text(label) }
                        if (missing > 0) ButtonCaption(stringResource(R.string.reason_coins_short, missing.grouped()), reason = true)
                        else ButtonCaption(stringResource(if (ten) R.string.gacha_ten_caption else R.string.gacha_single_caption))
                    }
                }
            }
            if (!state.hasCandidates) {
                Text(stringResource(R.string.gacha_no_candidates), style = MaterialTheme.typography.bodyMedium, color = DetoxTheme.colors.textSecondary, modifier = Modifier.padding(top = DetoxTheme.spacing.md))
            } else if (state.coins < Rules.GACHA_SINGLE_COST) {
                Text(stringResource(R.string.gacha_no_coins), style = MaterialTheme.typography.bodyMedium, color = DetoxTheme.colors.textSecondary, modifier = Modifier.padding(top = DetoxTheme.spacing.md))
            }
            Divider(Modifier.padding(top = DetoxTheme.spacing.xl))
            Row(Modifier.fillMaxWidth().padding(top = DetoxTheme.spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.gacha_rates), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onShowMissions) { Text(stringResource(R.string.gacha_show_missions)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.sm)) {
                for (r in Rarity.entries) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        RarityText(r)
                        Text(stringResource(R.string.percent, Rules.rarityRatePercent.getValue(r)), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Text(
                stringResource(R.string.gacha_rates_note),
                style = MaterialTheme.typography.bodySmall,
                color = DetoxTheme.colors.textSecondary,
                modifier = Modifier.padding(top = DetoxTheme.spacing.md, bottom = DetoxTheme.spacing.xl),
            )
        }
    }
}

/** ガチャマシンの絵。値は直書きせず、カテゴリの色トークンを使う。 */
@Composable
private fun GachaMachine(modifier: Modifier = Modifier, leverAngle: Float = 0f) {
    val colors = DetoxTheme.colors
    // カプセルは9個・3段階の大きさ（mockup 04）
    val capsuleCategories = listOf(
        Category.READING, Category.EXERCISE, Category.GAME, Category.STUDY, Category.CHORES,
        Category.MIND, Category.READING, Category.GAME, Category.EXERCISE,
    )
    val capsules = capsuleCategories.map { colors.category(it) }
    val outline = MaterialTheme.colorScheme.outlineVariant
    val sphere = MaterialTheme.colorScheme.surfaceContainerLow
    val base = MaterialTheme.colorScheme.surface
    val exit = MaterialTheme.colorScheme.surfaceContainer
    val leverFill = MaterialTheme.colorScheme.secondaryContainer
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(DetoxTheme.sizes.gachaMachineWidth, DetoxTheme.sizes.gachaMachineHeight)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.012f)
        // 球
        val r = w * 0.38f
        val center = Offset(w / 2, r + w * 0.04f)
        drawCircle(sphere, r, center)
        drawCircle(outline, r, center, style = stroke)
        val sizes = listOf(0.2f, 0.16f, 0.13f)
        capsules.forEachIndexed { i, c ->
            val angle = Math.toRadians(i * 40.0 + 10)
            val dist = if (i % 2 == 0) 0.55f else 0.3f
            val p = Offset(
                center.x + (r * dist * kotlin.math.cos(angle)).toFloat(),
                center.y + (r * dist * kotlin.math.sin(angle)).toFloat() + r * 0.12f,
            )
            val cr = r * sizes[i % sizes.size]
            drawCircle(c.container, cr, p)
            drawCircle(c.content, cr, p, style = stroke)
        }
        // 台
        val baseTop = center.y + r * 0.85f
        drawRoundRect(base, Offset(w * 0.12f, baseTop), Size(w * 0.76f, h - baseTop - h * 0.02f), androidx.compose.ui.geometry.CornerRadius(w * 0.06f))
        drawRoundRect(outline, Offset(w * 0.12f, baseTop), Size(w * 0.76f, h - baseTop - h * 0.02f), androidx.compose.ui.geometry.CornerRadius(w * 0.06f), style = stroke)
        // レバー
        val lever = Offset(w / 2, baseTop + (h - baseTop) * 0.42f)
        drawCircle(leverFill, w * 0.07f, lever)
        drawCircle(primary, w * 0.07f, lever, style = stroke)
        rotate(leverAngle, lever) {
            drawLine(primary, Offset(lever.x - w * 0.045f, lever.y), Offset(lever.x + w * 0.045f, lever.y), strokeWidth = w * 0.02f)
        }
        // 出口
        drawRoundRect(exit, Offset(w * 0.38f, h - h * 0.14f), Size(w * 0.24f, h * 0.07f), androidx.compose.ui.geometry.CornerRadius(w * 0.03f))
        drawRoundRect(outline, Offset(w * 0.38f, h - h * 0.14f), Size(w * 0.24f, h * 0.07f), androidx.compose.ui.geometry.CornerRadius(w * 0.03f), style = stroke)
    }
}

/**
 * ガチャの演出（仮決定 U10）。レバー → カプセル → レア度の色の光。どこを押しても飛ばせる。
 * 「アニメーションを削除」がオンなら動かさずに結果を出す。
 */
@Composable
fun GachaRevealScreen(missions: List<Mission>, onFinish: () -> Unit) {
    BlockBack()
    val best = missions.maxOf { it.rarity }
    val progress = remember { Animatable(0f) }
    val animate = ValueAnimator.areAnimatorsEnabled()
    LaunchedEffect(Unit) {
        if (animate) progress.animateTo(1f, tween(durationMillis = REVEAL_MS, easing = FastOutSlowInEasing))
        onFinish()
    }
    val colors = DetoxTheme.colors
    val glow = colors.rarity(best).container
    val rainbow = listOf(Category.READING, Category.EXERCISE, Category.STUDY, Category.CHORES, Category.MIND, Category.GAME)
        .map { colors.category(it).container } + colors.rarity(Rarity.SSR).container
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onFinish),
        contentAlignment = Alignment.Center,
    ) {
        val p = progress.value
        Canvas(Modifier.fillMaxSize()) {
            // 3段階：0〜0.35 レバー、0.35〜0.6 カプセルが落ちる、0.6〜1 光が広がる
            if (p > 0.6f) {
                val t = (p - 0.6f) / 0.4f
                val radius = size.minDimension * 0.6f * t
                if (best == Rarity.SSR) {
                    // ガチャの演出に限り、SSR の虹だけ多色のグラデーションを許す（docs/design-system.md 9章）
                    drawCircle(Brush.sweepGradient(rainbow + rainbow.first(), center), radius, center)
                } else drawCircle(glow, radius, center)
            }
        }
        val leverAngle = (p / 0.35f).coerceAtMost(1f) * 360f
        GachaMachine(leverAngle = leverAngle)
        Text(
            stringResource(R.string.gacha_skip),
            style = MaterialTheme.typography.bodySmall,
            color = DetoxTheme.colors.textSecondary,
            modifier = Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBars)
                .padding(bottom = DetoxTheme.spacing.xxl),
        )
    }
}

private const val REVEAL_MS = 2_400

/** 単発ガチャの結果（仮決定 N13）。結果が出た時点で確定しているので、どの操作でも実行画面へ移る。 */
@Composable
fun GachaSingleResultScreen(mission: Mission, onStart: () -> Unit) {
    // 結果が出た時点で確定しているので、戻る操作でも実行画面へ移る（5.3）
    androidx.activity.compose.BackHandler(enabled = true, onBack = onStart)
    Scaffold { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.gacha_single_result),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = DetoxTheme.spacing.screenEdge, end = DetoxTheme.spacing.screenEdge, top = DetoxTheme.spacing.xxl, bottom = DetoxTheme.spacing.lg),
                )
                Column(Modifier.padding(horizontal = DetoxTheme.spacing.screenEdge), verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.lg)) {
                    MissionSummary(mission, null, null, coinText(mission, SessionSource.GACHA_SINGLE), large = true)
                    NoticeBox(R.drawable.ic_lock, stringResource(R.string.gacha_start_notice, mission.durationMin))
                }
            }
            FullWidthButton(
                stringResource(R.string.gacha_start),
                onClick = onStart,
                modifier = Modifier.padding(horizontal = DetoxTheme.spacing.screenEdge, vertical = DetoxTheme.spacing.xxl),
            )
        }
    }
}

/** S03-2 10連の結果（5.3）。1件選ぶまで出られない。静かな時間帯のご褒美は選べない。 */
/** 10連の結果の1件。index は保存した結果の中の番号（選んだときに渡す）。gachaRemaining はガチャでの1日の上限の残り。 */
data class TenItem(val index: Int, val mission: Mission, val check: StartCheck, val gachaRemaining: Int)

@Composable
fun GachaTenResultScreen(
    items: List<TenItem>,
    onPick: (index: Int) -> Unit,
) {
    BlockBack()
    // レア度の高い順に並べる。選ぶときはもとの番号を渡す
    val ordered = remember(items) { items.sortedByDescending { it.mission.rarity } }
    val firstSelectable = ordered.firstOrNull { it.check is StartCheck.Ok }?.index ?: -1
    var selected by rememberSaveable { mutableIntStateOf(firstSelectable) }
    Scaffold { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            ScreenHeading(stringResource(R.string.gacha_ten_result), stringResource(R.string.gacha_ten_title), stringResource(R.string.gacha_ten_support))
            Divider()
            LazyColumn(Modifier.weight(1f).selectableGroup()) {
                itemsIndexed(ordered, key = { _, it -> it.index }) { _, item ->
                    val m = item.mission
                    val check = item.check
                    val enabled = check is StartCheck.Ok
                    val support = when {
                        check is StartCheck.QuietHours -> stringResource(R.string.gacha_ten_quiet)
                        !enabled -> stringResource(R.string.gacha_ten_disabled)
                        m.isReward -> stringResource(R.string.gacha_ten_reward_support, item.gachaRemaining)
                        else -> stringResource(R.string.dot_join, stringResource(m.category.labelRes), coinText(m, SessionSource.GACHA_TEN))
                    }
                    ChoiceRow(
                        selected = selected == item.index,
                        onSelect = { selected = item.index },
                        title = stringResource(R.string.title_minutes, m.title, m.durationMin),
                        support = support,
                        enabled = enabled,
                        leading = { RarityAvatar(m.rarity) },
                    )
                }
            }
            Column(
                Modifier.padding(start = DetoxTheme.spacing.screenEdge, end = DetoxTheme.spacing.screenEdge, top = DetoxTheme.spacing.lg, bottom = DetoxTheme.spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.lg),
            ) {
                val chosen = items.firstOrNull { it.index == selected }?.mission
                if (chosen != null) NoticeBox(R.drawable.ic_lock, stringResource(R.string.gacha_start_notice, chosen.durationMin))
                FullWidthButton(
                    if (chosen != null) stringResource(R.string.gacha_ten_start, chosen.title, chosen.durationMin) else stringResource(R.string.gacha_ten_choose),
                    onClick = { if (selected >= 0) onPick(selected) },
                    enabled = chosen != null,
                )
            }
        }
    }
}
