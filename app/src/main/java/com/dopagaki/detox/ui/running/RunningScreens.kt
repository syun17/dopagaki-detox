package com.dopagaki.detox.ui.running

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import com.dopagaki.detox.R
import com.dopagaki.detox.domain.Category
import com.dopagaki.detox.domain.coin.Coins
import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.model.Session
import com.dopagaki.detox.domain.model.SessionResult
import com.dopagaki.detox.domain.session.Settlement
import com.dopagaki.detox.ui.common.clock
import com.dopagaki.detox.ui.common.grouped
import com.dopagaki.detox.ui.components.BlockBack
import com.dopagaki.detox.ui.components.ButtonCaption
import com.dopagaki.detox.ui.components.ButtonStyle
import com.dopagaki.detox.ui.components.ChoiceRow
import com.dopagaki.detox.ui.components.Divider
import com.dopagaki.detox.ui.components.FullWidthButton
import com.dopagaki.detox.ui.components.NoticeBox
import com.dopagaki.detox.ui.components.PityProgress
import com.dopagaki.detox.ui.components.ProgressLine
import com.dopagaki.detox.ui.components.ScreenHeading
import com.dopagaki.detox.ui.components.labelRes
import com.dopagaki.detox.ui.theme.DetoxTheme
import java.time.Duration

/** 対象アプリ（ご褒美ミッションで開けるアプリ）。 */
data class LinkedApp(val packageName: String, val label: String)

data class RunningUiState(
    val session: Session,
    val category: Category,
    val note: String,
    val remaining: Duration,
    val endLabel: String,
    val paused: Boolean,
    val pauseRemaining: Duration?,
    val canPause: Boolean,
    val pauseUsed: Boolean,
    val pauseAllowed: Boolean,
    val canForceUnlock: Boolean,
    val unlockRemaining: Duration?,
    val linkedApps: List<LinkedApp>,
    val notificationsAllowed: Boolean,
)

/** S05 ミッション実行中（5.4、6.1）。終わるまでほかの画面へ進めない。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningScreen(
    state: RunningUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onForceUnlock: () -> Unit,
    onCancelUnlock: () -> Unit,
    onOpenApp: (String) -> Unit,
) {
    BlockBack()
    val s = state.session
    val isReward = s.kind == MissionKind.REWARD
    var pickApp by remember { mutableStateOf(false) }
    Scaffold { inner ->
        Column(Modifier.padding(inner).fillMaxSize().padding(horizontal = DetoxTheme.spacing.screenEdge), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(DetoxTheme.spacing.xxl))
                LockedLabel()
                Spacer(Modifier.height(DetoxTheme.spacing.xxl))
                Text(
                    stringResource(R.string.dot_join, stringResource(state.category.labelRes), s.rarity.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DetoxTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(DetoxTheme.spacing.xs))
                Text(s.missionTitle, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(DetoxTheme.spacing.xxl))
                val total = s.durationMs.toFloat()
                val progress = if (total > 0) 1f - state.remaining.toMillis() / total else 1f
                val desc = stringResource(R.string.running_remaining_desc, state.remaining.toMinutes(), state.remaining.seconds % 60)
                Box(Modifier.size(DetoxTheme.sizes.circularProgress).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = DetoxTheme.sizes.circularProgressStroke,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round,
                        gapSize = DetoxTheme.spacing.none,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.xs)) {
                        Text(
                            stringResource(if (state.paused) R.string.running_paused else R.string.running_remaining),
                            style = MaterialTheme.typography.bodyMedium,
                            color = DetoxTheme.colors.textSecondary,
                        )
                        Text(state.remaining.clock(), style = DetoxTheme.textStyles.timerLarge)
                        Text(
                            if (state.paused && state.pauseRemaining != null) stringResource(R.string.running_resume_in, state.pauseRemaining.clock())
                            else stringResource(R.string.running_ends_at, state.endLabel),
                            style = if (state.paused) DetoxTheme.textStyles.timerInline else MaterialTheme.typography.bodyMedium,
                            color = DetoxTheme.colors.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(DetoxTheme.spacing.xxl))
                Column(Modifier.padding(horizontal = DetoxTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.xs)) {
                    if (state.note.isNotEmpty()) {
                        Text(state.note, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    val cheer = if (isReward) {
                        if (state.linkedApps.isEmpty()) stringResource(R.string.running_reward_no_apps)
                        else stringResource(R.string.running_reward_apps, s.durationMin, state.linkedApps.joinToString("、") { it.label })
                    } else stringResource(R.string.running_task_coins, Coins.taskReward(s.durationMin, s.rarity).grouped())
                    Text(cheer, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                if (state.category == Category.EXERCISE) {
                    Spacer(Modifier.height(DetoxTheme.spacing.lg))
                    NoticeBox(R.drawable.ic_info, stringResource(R.string.running_exercise_note))
                }
                Spacer(Modifier.height(DetoxTheme.spacing.lg))
            }
            Column(Modifier.padding(bottom = DetoxTheme.spacing.xxl), verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.sm)) {
                if (isReward && state.linkedApps.isNotEmpty()) {
                    val single = state.linkedApps.singleOrNull()
                    FullWidthButton(
                        if (single != null) stringResource(R.string.running_open_app, single.label) else stringResource(R.string.running_open_apps),
                        onClick = { if (single != null) onOpenApp(single.packageName) else pickApp = true },
                        enabled = !state.paused,
                        icon = R.drawable.ic_open_in_new,
                    )
                    if (state.paused) ButtonCaption(stringResource(R.string.running_paused_apps), reason = true)
                }
                if (state.pauseAllowed) {
                    FilledTonalButton(
                        onClick = if (state.paused) onResume else onPause,
                        enabled = state.paused || state.canPause,
                        modifier = Modifier.fillMaxWidth().height(DetoxTheme.sizes.buttonHeight),
                    ) {
                        Icon(painterResource(if (state.paused) R.drawable.ic_play_arrow else R.drawable.ic_pause), null, modifier = Modifier.size(DetoxTheme.sizes.iconMedium))
                        Spacer(Modifier.size(DetoxTheme.spacing.sm))
                        Text(
                            when {
                                state.paused -> stringResource(R.string.running_resume)
                                state.pauseUsed -> stringResource(R.string.running_pause_used)
                                else -> stringResource(R.string.running_pause)
                            },
                        )
                    }
                }
                if (state.canForceUnlock && state.unlockRemaining == null) {
                    FullWidthButton(stringResource(R.string.force_unlock), onClick = onForceUnlock, style = ButtonStyle.Text)
                    ButtonCaption(stringResource(if (isReward) R.string.force_unlock_caption_reward else R.string.force_unlock_caption_task))
                }
                if (isReward && !state.notificationsAllowed) {
                    ButtonCaption(stringResource(R.string.running_no_notifications))
                }
            }
        }
    }

    if (state.unlockRemaining != null) {
        UnlockWaitDialog(state, onCancelUnlock)
    }
    if (pickApp) {
        ModalBottomSheet(
            onDismissRequest = { pickApp = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) },
        ) {
            state.linkedApps.forEach { app ->
                ListItem(
                    headlineContent = { Text(app.label) },
                    leadingContent = { com.dopagaki.detox.ui.settings.AppIcon(app.packageName) },
                    trailingContent = { Icon(painterResource(R.drawable.ic_open_in_new), null) },
                    modifier = Modifier.clickable { pickApp = false; onOpenApp(app.packageName) },
                )
            }
            Spacer(Modifier.height(DetoxTheme.spacing.xl))
        }
    }
}

@Composable
private fun LockedLabel() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.height(DetoxTheme.sizes.chipHeight)) {
        Row(
            Modifier.padding(start = DetoxTheme.spacing.md, end = DetoxTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.sm),
        ) {
            Icon(painterResource(R.drawable.ic_lock), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(DetoxTheme.sizes.iconMedium))
            Text(stringResource(R.string.running_locked), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 解除待ちの中身（S05-2 と S07 で共通。仮決定 U3）。 */
@Composable
fun UnlockWaitContent(remaining: Duration, body: String, large: Boolean, withHeading: Boolean = false) {
    val wait = Duration.ofMinutes(com.dopagaki.detox.domain.SettingsDefaults.UNLOCK_WAIT.toMinutes())
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (withHeading) {
            // S07 では AlertDialog を使わないので、S05-2 と同じ見出しの形をここで描く（仮決定 U3）
            Icon(painterResource(R.drawable.ic_lock_open), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(DetoxTheme.sizes.iconLarge))
            Spacer(Modifier.height(DetoxTheme.spacing.lg))
            Text(stringResource(R.string.unlock_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(DetoxTheme.spacing.lg))
        }
        Text(stringResource(R.string.unlock_until), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(remaining.clock(), style = if (large) DetoxTheme.textStyles.timerMedium else DetoxTheme.textStyles.timerSmall)
        Spacer(Modifier.height(DetoxTheme.spacing.lg))
        ProgressLine(1f - remaining.toMillis() / wait.toMillis().toFloat())
        Spacer(Modifier.height(DetoxTheme.spacing.lg))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** S05-2 強制解除の待機（6.3）。待っている間ずっと出し続け、取り消せる。 */
@Composable
private fun UnlockWaitDialog(state: RunningUiState, onCancel: () -> Unit) {
    val s = state.session
    val body = if (s.kind == MissionKind.REWARD) {
        val apps = state.linkedApps.joinToString("、") { it.label }
        stringResource(R.string.unlock_body_reward, s.missionTitle) + if (apps.isNotEmpty()) stringResource(R.string.unlock_body_reward_apps, apps) else ""
    } else stringResource(R.string.unlock_body_task, s.missionTitle)
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        icon = { Icon(painterResource(R.drawable.ic_lock_open), null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.unlock_title)) },
        text = { UnlockWaitContent(state.unlockRemaining ?: Duration.ZERO, body + stringResource(R.string.unlock_body_timer), large = true) },
        confirmButton = { Button(onClick = onCancel) { Text(stringResource(R.string.unlock_back_to_mission)) } },
    )
}

/** S06 完了報告（6.1「終了後」、7.5）。やることは3択→結果、ご褒美は報告を省く（仮決定 N12）。 */
@Composable
fun ReportScreen(session: Session, onReport: (SessionResult) -> Unit) {
    BlockBack()
    if (session.kind == MissionKind.REWARD) {
        // ご褒美はどれを選んでもコイン0で天井にも数えないので、選ぶ段階を省いて「できた」で記録する
        androidx.compose.runtime.LaunchedEffect(session.id) { onReport(SessionResult.DONE) }
        com.dopagaki.detox.ui.components.LoadingState()
        return
    }
    var choice by rememberSaveable { mutableStateOf<SessionResult?>(null) }
    val full = Coins.taskReward(session.durationMin, session.rarity)
    Scaffold { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            ScreenHeading(
                stringResource(R.string.report_eyebrow, session.durationMin),
                session.missionTitle,
                stringResource(R.string.report_question),
            )
            Divider()
            Column(Modifier.weight(1f)) {
                listOf(
                    SessionResult.DONE to (stringResource(R.string.result_done) to stringResource(R.string.coins, full.grouped())),
                    SessionResult.PARTIAL to (stringResource(R.string.result_partial) to stringResource(R.string.report_partial_support, (full / 2).grouped())),
                    SessionResult.FAILED to (stringResource(R.string.result_failed) to stringResource(R.string.report_failed_support)),
                ).forEach { (r, texts) ->
                    ChoiceRow(selected = choice == r, onSelect = { choice = r }, title = texts.first, support = texts.second)
                }
            }
            FullWidthButton(
                stringResource(R.string.report_submit),
                onClick = { choice?.let(onReport) },
                enabled = choice != null,
                modifier = Modifier.padding(horizontal = DetoxTheme.spacing.screenEdge, vertical = DetoxTheme.spacing.xxl),
            )
        }
    }
}

/** S06 の結果（もらったコイン、連続日数ボーナス、天井）。強制解除・ご褒美のときもここに来る。 */
@Composable
fun SettlementScreen(
    settlement: Settlement,
    linkedAppLabels: List<String>,
    onHome: () -> Unit,
) {
    BlockBack()
    val s = settlement.session
    val total = settlement.earned + settlement.streakBonus
    Scaffold { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                when {
                    s.result == SessionResult.ABORTED -> ScreenHeading(stringResource(R.string.settle_aborted), s.missionTitle, stringResource(R.string.settle_aborted_body))
                    s.kind == MissionKind.REWARD -> ScreenHeading(
                        stringResource(R.string.settle_reward),
                        s.missionTitle,
                        stringResource(R.string.settle_reward_body) +
                            if (linkedAppLabels.isNotEmpty()) stringResource(R.string.settle_reward_apps, linkedAppLabels.joinToString("、")) else "",
                    )
                    else -> ScreenHeading(
                        stringResource(R.string.settle_recorded),
                        stringResource(R.string.settle_plus_coins, total.grouped()),
                        stringResource(
                            when (s.result) {
                                SessionResult.DONE -> R.string.settle_cheer_done
                                SessionResult.PARTIAL -> R.string.settle_cheer_partial
                                else -> R.string.settle_cheer_failed
                            },
                        ),
                    )
                }
                Column(Modifier.padding(horizontal = DetoxTheme.spacing.screenEdge)) {
                    Spacer(Modifier.height(DetoxTheme.spacing.sm))
                    Divider()
                    if (s.kind == MissionKind.TASK && s.result != SessionResult.ABORTED) {
                        LineItem(stringResource(R.string.settle_line_mission, s.missionTitle, stringResource(resultLabel(s.result))), "+" + stringResource(R.string.coins, settlement.earned.grouped()))
                        if (settlement.streakBonus > 0) {
                            LineItem(stringResource(R.string.settle_line_streak, settlement.wallet.streakDays), "+" + stringResource(R.string.coins, settlement.streakBonus.grouped()))
                        }
                        Divider()
                    }
                    LineItem(stringResource(R.string.settle_line_balance), stringResource(R.string.coins, settlement.wallet.coins.grouped()))
                    Divider()
                    if (s.kind == MissionKind.TASK) {
                        Spacer(Modifier.height(DetoxTheme.spacing.xl))
                        PityProgress(settlement.wallet.pityCount, settlement.wallet.pityReady)
                        if (s.durationMin >= com.dopagaki.detox.domain.Rules.PITY_MIN_DURATION_MIN && s.result != SessionResult.DONE && settlement.wallet.pityCount == 0) {
                            Text(stringResource(R.string.settle_pity_reset), style = MaterialTheme.typography.bodySmall, color = DetoxTheme.colors.textSecondary, modifier = Modifier.padding(top = DetoxTheme.spacing.sm))
                        }
                    }
                }
            }
            FullWidthButton(
                stringResource(R.string.settle_home),
                onClick = onHome,
                modifier = Modifier.padding(horizontal = DetoxTheme.spacing.screenEdge, vertical = DetoxTheme.spacing.xxl),
            )
        }
    }
}

private fun resultLabel(r: SessionResult): Int = when (r) {
    SessionResult.DONE -> R.string.result_done
    SessionResult.PARTIAL -> R.string.result_partial
    else -> R.string.result_failed
}

@Composable
private fun LineItem(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = DetoxTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = DetoxTheme.textStyles.coinLabel)
    }
}
