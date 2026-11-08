package com.dopagaki.detox.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import com.dopagaki.detox.R
import com.dopagaki.detox.domain.Category
import com.dopagaki.detox.domain.Rarity
import com.dopagaki.detox.domain.coin.Coins
import com.dopagaki.detox.domain.model.Mission
import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.model.SessionSource
import com.dopagaki.detox.domain.session.StartCheck
import com.dopagaki.detox.ui.common.clock
import com.dopagaki.detox.ui.common.grouped
import com.dopagaki.detox.ui.components.AvatarSize
import com.dopagaki.detox.ui.components.ButtonCaption
import com.dopagaki.detox.ui.components.CategoryAvatar
import com.dopagaki.detox.ui.components.Divider
import com.dopagaki.detox.ui.components.EmptyState
import com.dopagaki.detox.ui.components.FullWidthButton
import com.dopagaki.detox.ui.components.InfoChip
import com.dopagaki.detox.ui.components.LoadingState
import com.dopagaki.detox.ui.components.NoticeBox
import com.dopagaki.detox.ui.components.labelRes
import com.dopagaki.detox.ui.format.coinText
import com.dopagaki.detox.ui.format.relativeDay
import com.dopagaki.detox.ui.format.startReason
import com.dopagaki.detox.ui.theme.DetoxTheme
import kotlinx.coroutines.launch

/** S01 ホーム（設計書 5.1）。X のタイムラインの形。行にボタンを置かない。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: (onDone: () -> Unit) -> Unit,
    onOpenRow: (FeedRow) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues,
) {
    var refreshing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val pull = rememberPullToRefreshState()
    val canRefresh = state.refreshRemaining > 0
    Scaffold(
        modifier = Modifier.padding(contentPadding),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                Divider()
            }
        },
    ) { inner ->
        if (!state.loaded) {
            LoadingState(Modifier.padding(inner))
            return@Scaffold
        }
        Box(
            Modifier
                .padding(inner)
                .fillMaxSize()
                // 更新の上限中は引っ張る操作を止める（仮決定 N2）
                .pullToRefresh(
                    isRefreshing = refreshing,
                    state = pull,
                    enabled = canRefresh,
                    onRefresh = {
                        refreshing = true
                        onRefresh {
                            refreshing = false
                            scope.launch { listState.scrollToItem(0) }
                        }
                    },
                ),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                if (state.accessibilityOff) {
                    item {
                        NoticeBox(
                            icon = R.drawable.ic_warning,
                            text = stringResource(R.string.home_a11y_off),
                            onClick = onOpenAccessibility,
                            modifier = Modifier.padding(horizontal = DetoxTheme.spacing.screenEdgeList, vertical = DetoxTheme.spacing.md),
                        )
                    }
                }
                if (state.rows.isEmpty()) {
                    item {
                        EmptyState(
                            title = stringResource(R.string.home_empty_title),
                            body = stringResource(R.string.home_empty_body),
                            action = stringResource(R.string.home_empty_action),
                            onAction = onOpenSettings,
                        )
                    }
                }
                items(state.rows, key = { it.mission.id }) { row ->
                    TimelineRow(row, state, onClick = { onOpenRow(row) })
                    Divider()
                }
                item {
                    val text = if (state.nextRefreshIn != null) stringResource(R.string.home_next_refresh, state.nextRefreshIn.clock())
                    else stringResource(R.string.home_pull_to_refresh, state.refreshRemaining)
                    Text(
                        text,
                        style = if (state.nextRefreshIn != null) DetoxTheme.textStyles.coinMeta else DetoxTheme.textStyles.timelineMeta,
                        color = DetoxTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = DetoxTheme.spacing.screenEdgeList, vertical = DetoxTheme.spacing.lg),
                    )
                }
            }
            PullToRefreshDefaults.Indicator(state = pull, isRefreshing = refreshing, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun TimelineRow(row: FeedRow, state: HomeUiState, onClick: () -> Unit) {
    val m = row.mission
    val secondary = DetoxTheme.colors.textSecondary
    val openLabel = stringResource(R.string.home_open_detail)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openLabel, onClick = onClick)
            .padding(horizontal = DetoxTheme.spacing.screenEdgeList, vertical = DetoxTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.md),
    ) {
        CategoryAvatar(m.category)
        Column(Modifier.weight(1f)) {
            val category = stringResource(m.category.labelRes)
            val minutes = stringResource(R.string.minutes, m.durationMin)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.title) }
                    withStyle(SpanStyle(color = secondary)) { append(stringResource(R.string.dot_join, category, minutes).let { " · $it" }) }
                },
                style = DetoxTheme.textStyles.timelineBody,
            )
            if (m.note.isNotEmpty()) Text(m.note, style = DetoxTheme.textStyles.timelineBody)
            Spacer(Modifier.height(DetoxTheme.spacing.sm))
            val rarityColor = DetoxTheme.colors.rarity(m.rarity).text
            val meta = if (m.isReward) {
                stringResource(
                    R.string.home_meta_reward,
                    stringResource(R.string.coins, Coins.rewardValue(m.durationMin, m.rarity).grouped()),
                    state.feedRewardRemaining,
                )
            } else {
                val count = stringResource(R.string.home_meta_count, row.doneCount)
                m.lastDoneAt?.let { stringResource(R.string.dot_join, count, relativeDay(it, state.now, state.zone)) } ?: count
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = rarityColor, fontWeight = FontWeight.Bold)) { append(m.rarity.name) }
                    append(" · ")
                    append(meta)
                },
                style = if (m.isReward) DetoxTheme.textStyles.coinMeta else DetoxTheme.textStyles.timelineMeta,
                color = secondary,
            )
        }
    }
}

/** S01-2 ミッション詳細のボトムシート（5.1）。置くボタンは「始める」の1つだけ。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MissionDetailSheet(
    row: FeedRow,
    check: StartCheck,
    state: HomeUiState,
    appLabels: List<String>,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val m = row.mission
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) },
    ) {
        Column(
            Modifier.padding(start = DetoxTheme.spacing.screenEdge, end = DetoxTheme.spacing.screenEdge, bottom = DetoxTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.lg),
        ) {
            MissionSummary(m, row.doneCount, state, coinText(m, if (row.isLater) SessionSource.LATER else SessionSource.HOME))
            NoticeBox(R.drawable.ic_lock, lockNotice(m, appLabels))
            val reason = startReason(check, state.settings)
            Column(Modifier.padding(top = DetoxTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.sm)) {
                FullWidthButton(stringResource(R.string.start), onClick = onStart, enabled = check is StartCheck.Ok)
                if (reason != null) ButtonCaption(reason, reason = true)
            }
        }
    }
}

/** 名前・回数・チップ・ひとこと（S01-2、単発ガチャの結果）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MissionSummary(m: Mission, doneCount: Int?, state: HomeUiState?, coin: String, large: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.lg)) {
        Row(horizontalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(m.category, AvatarSize.Sheet)
            Column {
                Text(m.title, style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall)
                if (doneCount != null) {
                    val count = stringResource(R.string.home_meta_count, doneCount)
                    val last = m.lastDoneAt?.let { at -> state?.let { stringResource(R.string.last_done, relativeDay(at, it.now, it.zone)) } }
                    Text(
                        if (last != null) stringResource(R.string.dot_join, count, last) else count,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DetoxTheme.colors.textSecondary,
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.sm)) {
            InfoChip(stringResource(m.category.labelRes))
            InfoChip(stringResource(R.string.minutes, m.durationMin), numeric = true)
            InfoChip(stringResource(R.string.rarity_label, m.rarity.name), color = DetoxTheme.colors.rarity(m.rarity).text)
            InfoChip(coin, numeric = true)
        }
        if (m.note.isNotEmpty()) Text(m.note, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun lockNotice(m: Mission, appLabels: List<String>): String = if (m.isReward) {
    val base = stringResource(R.string.lock_notice_reward, Coins.rewardValue(m.durationMin, m.rarity).grouped(), m.durationMin)
    if (appLabels.isEmpty()) base else base + stringResource(R.string.lock_notice_reward_apps, appLabels.joinToString("、"))
} else stringResource(R.string.lock_notice_task, m.durationMin)

private val previewMissions = listOf(
    Mission("1", "紙の本を読む", MissionKind.TASK, Category.READING, 20, Rarity.R, 3, null, emptySet(), emptySet(), true, null, "紙の本で目を休めよう。"),
    Mission("2", "好きなゲーム", MissionKind.REWARD, Category.GAME, 20, Rarity.SSR, 3, 2, emptySet(), emptySet(), true, null, "思い切り楽しもう。"),
)

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    DetoxTheme {
        HomeScreen(
            HomeUiState(loaded = true, rows = previewMissions.map { FeedRow(it, 3, false) }, refreshRemaining = 2, feedRewardRemaining = 1),
            {}, {}, {}, {}, PaddingValues(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeLimitPreview() {
    DetoxTheme {
        HomeScreen(
            HomeUiState(loaded = true, rows = emptyList(), nextRefreshIn = java.time.Duration.ofMinutes(47), accessibilityOff = true),
            {}, {}, {}, {}, PaddingValues(),
        )
    }
}
