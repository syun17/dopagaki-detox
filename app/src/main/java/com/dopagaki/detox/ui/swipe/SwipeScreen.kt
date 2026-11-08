package com.dopagaki.detox.ui.swipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.dopagaki.detox.R
import com.dopagaki.detox.domain.model.SessionSource
import com.dopagaki.detox.domain.session.StartCheck
import com.dopagaki.detox.ui.common.clock
import com.dopagaki.detox.ui.components.EmptyState
import com.dopagaki.detox.ui.components.LoadingState
import com.dopagaki.detox.ui.components.iconRes
import com.dopagaki.detox.ui.components.labelRes
import com.dopagaki.detox.ui.format.coinText
import com.dopagaki.detox.ui.format.relativeDay
import com.dopagaki.detox.ui.format.startReason
import com.dopagaki.detox.ui.home.FeedRow
import com.dopagaki.detox.ui.home.FeedViewModel
import com.dopagaki.detox.ui.home.HomeUiState
import com.dopagaki.detox.ui.theme.DetoxTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** S02 縦スワイプ（設計書 5.2）。1画面1ミッション。最後まで見ると次の20枚（1回の更新）。 */
@Composable
fun SwipeScreen(
    vm: FeedViewModel,
    state: HomeUiState,
    snackbar: SnackbarHostState,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues,
) {
    if (!state.loaded) {
        LoadingState(Modifier.padding(contentPadding))
        return
    }
    val rows = remember(state.rows) { vm.swipeRows(state) }
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(contentPadding).windowInsetsPadding(WindowInsets.statusBars)) {
            EmptyState(stringResource(R.string.home_empty_title), stringResource(R.string.home_empty_body), action = stringResource(R.string.home_empty_action), onAction = onOpenSettings)
        }
        return
    }
    val pager = rememberPagerState { rows.size + 1 }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    val limitMsg = stringResource(R.string.swipe_saved)
    val unsavedMsg = stringResource(R.string.swipe_unsaved)

    // 21ページ目に止まったら次の20件を読み込む（1回の更新として数える。5.2）
    LaunchedEffect(pager, rows.size) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            if (page == rows.size && state.refreshRemaining > 0 && !loading) {
                loading = true
                vm.refresh {
                    loading = false
                    scope.launch { pager.scrollToPage(0) }
                }
            }
        }
    }

    VerticalPager(state = pager, modifier = Modifier.fillMaxSize().padding(contentPadding)) { page ->
        if (page < rows.size) {
            val row = rows[page]
            val check = vm.check(row, SessionSource.SWIPE)
            val reason = startReason(check, state.settings)
            val short = com.dopagaki.detox.ui.format.startReasonShort(check)
            SwipePage(
                short = short,
                row = row,
                index = page,
                total = rows.size,
                state = state,
                reason = reason,
                isLater = vm.isLater(row.mission.id),
                onPick = {
                    if (check is StartCheck.Ok) vm.start(row, SessionSource.SWIPE)
                    else if (reason != null) scope.launch { snackbar.showSnackbar(reason) }
                },
                onLater = {
                    val wasLater = vm.isLater(row.mission.id)
                    vm.toggleLater(row.mission.id)
                    scope.launch { snackbar.showSnackbar(if (wasLater) unsavedMsg else limitMsg) }
                },
            )
        } else {
            EndPage(state, loading, rows.size)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwipePage(
    short: String?,
    row: FeedRow,
    index: Int,
    total: Int,
    state: HomeUiState,
    reason: String?,
    isLater: Boolean,
    onPick: () -> Unit,
    onLater: () -> Unit,
) {
    val m = row.mission
    val cat = DetoxTheme.colors.category(m.category)
    Box(Modifier.fillMaxSize().background(cat.swipeBackground)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            // 右上のページ表示
            Row(Modifier.fillMaxWidth().padding(top = DetoxTheme.spacing.lg, end = DetoxTheme.spacing.lg), horizontalArrangement = Arrangement.End) {
                Surface(color = DetoxTheme.colors.onTintedSurfaceTranslucent, shape = MaterialTheme.shapes.small) {
                    Text(
                        stringResource(R.string.swipe_page, index + 1, total, state.refreshRemaining),
                        style = DetoxTheme.textStyles.metaLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = DetoxTheme.spacing.xs, horizontal = DetoxTheme.spacing.md),
                    )
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                // 大きなアイコンと時間
                Column(
                    Modifier.weight(1f).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(DetoxTheme.sizes.swipeHeroCircle).background(DetoxTheme.colors.onTintedSurface, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(painterResource(m.category.iconRes), null, tint = cat.content, modifier = Modifier.size(DetoxTheme.sizes.iconSwipeHero))
                    }
                    Spacer(Modifier.height(DetoxTheme.spacing.lg))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(m.durationMin.toString(), style = DetoxTheme.textStyles.timerLarge, modifier = Modifier.alignByBaseline())
                        Spacer(Modifier.size(DetoxTheme.spacing.xs))
                        Text(stringResource(R.string.minute_unit), style = MaterialTheme.typography.titleLarge, modifier = Modifier.alignByBaseline())
                    }
                }
                // 右のアクション
                Column(
                    Modifier.fillMaxHeight().padding(end = DetoxTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.lg, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FloatingActionButton(
                            onClick = onPick,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                        ) {
                            Icon(painterResource(R.drawable.ic_check), stringResource(R.string.swipe_pick), modifier = Modifier.size(DetoxTheme.sizes.iconLarge))
                        }
                        Spacer(Modifier.height(DetoxTheme.spacing.xs))
                        Text(stringResource(R.string.swipe_pick), style = MaterialTheme.typography.labelMedium)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val savedDesc = stringResource(R.string.swipe_later_saved)
                        FilledIconToggleButton(
                            checked = isLater,
                            onCheckedChange = { onLater() },
                            modifier = Modifier.size(DetoxTheme.sizes.swipeSecondaryAction).semantics { if (isLater) stateDescription = savedDesc },
                            colors = IconButtonDefaults.filledIconToggleButtonColors(
                                containerColor = DetoxTheme.colors.onTintedSurface,
                                checkedContainerColor = DetoxTheme.colors.onTintedSurface,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                checkedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            Icon(
                                painterResource(if (isLater) R.drawable.ic_bookmark_fill else R.drawable.ic_bookmark),
                                stringResource(R.string.swipe_later),
                                modifier = Modifier.size(DetoxTheme.sizes.iconInAvatar),
                            )
                        }
                        Spacer(Modifier.height(DetoxTheme.spacing.xs))
                        Text(stringResource(R.string.swipe_later), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            // 下部の文
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = DetoxTheme.spacing.screenEdgeList, end = DetoxTheme.sizes.swipeFab + DetoxTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.sm),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.sm)) {
                    TintedChip(stringResource(m.category.labelRes), cat.content)
                    TintedChip(m.rarity.name, DetoxTheme.colors.rarity(m.rarity).text)
                    TintedChip(short ?: coinText(m, SessionSource.SWIPE), MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(m.title, style = MaterialTheme.typography.headlineSmall)
                if (m.note.isNotEmpty()) Text(m.note, style = DetoxTheme.textStyles.swipeBody, color = DetoxTheme.colors.textBody)
                val count = stringResource(R.string.home_meta_count, row.doneCount)
                val last = m.lastDoneAt?.let { stringResource(R.string.last_done, relativeDay(it, state.now, state.zone)) }
                Text(
                    if (last != null) stringResource(R.string.dot_join, count, last) else count,
                    style = DetoxTheme.textStyles.timelineMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = DetoxTheme.spacing.lg, bottom = DetoxTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.xs, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_keyboard_arrow_up), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(DetoxTheme.sizes.iconSmall))
                Text(stringResource(R.string.swipe_next), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TintedChip(text: String, color: Color) {
    Surface(color = DetoxTheme.colors.onTintedSurface, shape = MaterialTheme.shapes.small, modifier = Modifier.height(DetoxTheme.sizes.chipHeight)) {
        Box(Modifier.padding(horizontal = DetoxTheme.spacing.md), contentAlignment = Alignment.Center) {
            Text(text, style = DetoxTheme.textStyles.metaLabel, color = color)
        }
    }
}

/** 21ページ目：更新できるなら読み込み中、上限中は次の更新までの時間（7.4）。 */
@Composable
private fun EndPage(state: HomeUiState, loading: Boolean, total: Int) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.statusBars)) {
        if (state.nextRefreshIn == null || loading) {
            LoadingState()
        } else {
            Column(
                Modifier.fillMaxSize().padding(horizontal = DetoxTheme.spacing.screenEdge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.sm, Alignment.CenterVertically),
            ) {
                Icon(painterResource(R.drawable.ic_hourglass_top), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(DetoxTheme.sizes.iconLarge))
                Text(stringResource(R.string.swipe_next_refresh_label), style = MaterialTheme.typography.bodyLarge, color = DetoxTheme.colors.textSecondary)
                Text(state.nextRefreshIn.clock(), style = DetoxTheme.textStyles.timerSmall)
                Text(
                    stringResource(R.string.swipe_limit_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DetoxTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
