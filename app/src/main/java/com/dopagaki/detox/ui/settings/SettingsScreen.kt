package com.dopagaki.detox.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.dopagaki.detox.R
import com.dopagaki.detox.domain.Rules
import com.dopagaki.detox.domain.model.Mission
import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.model.Settings
import com.dopagaki.detox.domain.pending.PendingChange
import com.dopagaki.detox.domain.pending.PendingPayload
import com.dopagaki.detox.ui.common.clock
import com.dopagaki.detox.ui.common.hm
import com.dopagaki.detox.ui.components.Divider
import com.dopagaki.detox.ui.components.EmptyState
import com.dopagaki.detox.ui.components.LetterAvatar
import com.dopagaki.detox.ui.components.LoadingState
import com.dopagaki.detox.ui.components.SectionHeader
import com.dopagaki.detox.ui.components.labelRes
import com.dopagaki.detox.ui.theme.DetoxTheme
import java.time.Duration
import java.time.LocalTime

private val GOAL_RANGE = Rules.DAILY_GOAL_MIN_RANGE
private const val GOAL_STEP = Rules.DAILY_GOAL_STEP_MIN
private val FEED_LIMIT_CHOICES = Rules.FEED_REWARD_DAILY_LIMIT_RANGE

/** S04 設定（5.5、5.6）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    vm: SettingsViewModel,
    onEditMission: (id: String?, kind: MissionKind) -> Unit,
    onOpenDisclosure: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onMessage: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val kind = if (tab == 0) MissionKind.TASK else MissionKind.REWARD
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val waitMsg = stringResource(R.string.settings_wait_snackbar)
    val s = state.app.settings
    fun update(new: Settings) = vm.updateSettings(new) { waits -> if (waits) onMessage(waitMsg) }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_settings)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(onClick = { onEditMission(null, kind) }) {
                        Icon(painterResource(R.drawable.ic_add), stringResource(if (kind == MissionKind.TASK) R.string.settings_add_task else R.string.settings_add_reward))
                    }
                },
            )
        },
    ) { inner ->
        if (!state.loaded) {
            LoadingState(Modifier.padding(inner))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(inner).fillMaxSize()) {
            // 反映待ち（5.6：設定画面の上部に出し、いつでも取り消せる）
            val waiting = state.app.waiting
            if (waiting.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.settings_pending)) }
                items(waiting, key = { it.id }) { p ->
                    ListItem(
                        headlineContent = { Text(pendingLabel(p, state.app.missions, vm)) },
                        supportingContent = {
                            Text(
                                stringResource(R.string.settings_pending_time, Duration.between(state.now, p.appliesAt).clock()),
                                style = DetoxTheme.textStyles.timerInline,
                                color = DetoxTheme.colors.warning,
                            )
                        },
                        trailingContent = {
                            if (com.dopagaki.detox.domain.pending.PendingChanges.isCancellable(p)) {
                                TextButton(onClick = { vm.cancelPending(p.id) }) { Text(stringResource(R.string.settings_pending_cancel)) }
                            }
                        },
                    )
                }
                item { Divider() }
            }
            item {
                PrimaryTabRow(
                    selectedTabIndex = tab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(tab, matchContentSize = true)) },
                    divider = { Divider() },
                ) {
                    listOf(R.string.settings_tab_tasks, R.string.settings_tab_rewards).forEachIndexed { i, label ->
                        Tab(
                            selected = tab == i,
                            onClick = { tab = i },
                            text = { Text(stringResource(label)) },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val missions = state.app.missions.filter { it.kind == kind }
            if (missions.isEmpty()) {
                item {
                    if (kind == MissionKind.TASK) EmptyState(stringResource(R.string.settings_empty_tasks), stringResource(R.string.settings_empty_tasks_body))
                    else EmptyState(stringResource(R.string.settings_empty_rewards), stringResource(R.string.settings_empty_rewards_body))
                }
            }
            items(missions, key = { it.id }) { m ->
                MissionRow(m, state, vm, onClick = { onEditMission(m.id, m.kind) }, onToggle = { on ->
                    vm.saveMission(m.copy(enabled = on)) { _, waits -> if (waits) onMessage(waitMsg) }
                })
            }

            item { Divider(); SectionHeader(stringResource(R.string.settings_lock_section)) }
            item {
                val consented = state.app.meta.a11yConsent == true
                SwitchRow(
                    title = stringResource(R.string.settings_reward_lock),
                    support = when {
                        s.rewardAppLock && !state.accessibilityOn -> stringResource(R.string.settings_reward_lock_off)
                        else -> stringResource(R.string.settings_reward_lock_support)
                    },
                    supportIsError = s.rewardAppLock && !state.accessibilityOn,
                    checked = s.rewardAppLock,
                    onChange = { on ->
                        // 同意していない、またはサービスがオフなら開示の画面へ（9.2）
                        if (on && (!consented || !state.accessibilityOn)) onOpenDisclosure() else update(s.copy(rewardAppLock = on))
                    },
                )
            }
            item {
                SwitchRow(stringResource(R.string.settings_allow_unlock), stringResource(R.string.settings_allow_unlock_support), s.allowForceUnlock) { update(s.copy(allowForceUnlock = it)) }
            }
            item {
                SwitchRow(stringResource(R.string.settings_allow_pause), stringResource(R.string.settings_allow_pause_support), s.allowPause) { update(s.copy(allowPause = it)) }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_feed_limit)) },
                    supportingContent = {
                        Column {
                            Text(stringResource(R.string.settings_feed_limit_support), color = DetoxTheme.colors.textSecondary)
                            SingleChoiceSegmentedButtonRow(Modifier.padding(top = DetoxTheme.spacing.sm)) {
                                val choices = FEED_LIMIT_CHOICES.toList()
                                choices.forEachIndexed { i, n ->
                                    SegmentedButton(
                                        selected = s.feedRewardDailyLimit == n,
                                        onClick = { update(s.copy(feedRewardDailyLimit = n)) },
                                        shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                                    ) { Text(stringResource(R.string.times, n)) }
                                }
                            }
                        }
                    },
                )
            }

            item { Divider(); SectionHeader(stringResource(R.string.settings_quiet_section)) }
            item {
                SwitchRow(
                    stringResource(R.string.settings_quiet),
                    stringResource(R.string.settings_quiet_support),
                    s.quietEnabled,
                ) { update(s.copy(quietEnabled = it)) }
            }
            if (s.quietEnabled) {
                item { TimeRow(stringResource(R.string.settings_quiet_start), s.quietStart) { update(s.copy(quietStart = it)) } }
                item { TimeRow(stringResource(R.string.settings_quiet_end), s.quietEnd) { update(s.copy(quietEnd = it)) } }
            }

            item { Divider(); SectionHeader(stringResource(R.string.settings_goal_section)) }
            item {
                var value by remember(s.dailyGoalMin) { mutableFloatStateOf(s.dailyGoalMin.toFloat()) }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_goal, value.toInt())) },
                    supportingContent = {
                        Slider(
                            value = value,
                            onValueChange = { value = it },
                            onValueChangeFinished = { update(s.copy(dailyGoalMin = value.toInt())) },
                            valueRange = GOAL_RANGE.first.toFloat()..GOAL_RANGE.last.toFloat(),
                            steps = (GOAL_RANGE.last - GOAL_RANGE.first) / GOAL_STEP - 1,
                        )
                    },
                )
            }

            item { Divider(); SectionHeader(stringResource(R.string.settings_a11y_section)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_disclosure)) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_accessibility_new), null) },
                    trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null) },
                    modifier = Modifier.clickable(onClick = onOpenDisclosure),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_notifications)) },
                    supportingContent = {
                        if (!state.notificationsOn) Text(stringResource(R.string.settings_notifications_off), color = DetoxTheme.colors.textSecondary)
                    },
                    leadingContent = { Icon(painterResource(R.drawable.ic_notifications), null) },
                    trailingContent = { Icon(painterResource(R.drawable.ic_open_in_new), null) },
                    modifier = Modifier.clickable(onClick = onOpenNotificationSettings),
                )
            }

            item { Divider(); SectionHeader(stringResource(R.string.settings_data_section)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_backup)) },
                    supportingContent = { Text(stringResource(R.string.settings_backup_support), color = DetoxTheme.colors.textSecondary) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_reset)) },
                    supportingContent = { Text(stringResource(R.string.settings_reset_support), color = DetoxTheme.colors.textSecondary) },
                    modifier = Modifier.clickable { dialog = "reset" },
                )
            }
            item { Divider() }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_help)) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_help), null) },
                    modifier = Modifier.clickable { dialog = "help" },
                )
            }
        }
    }

    when (dialog) {
        "reset" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.reset_title)) },
            text = { Text(stringResource(R.string.reset_body)) },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = { TextButton(onClick = { dialog = null; vm.requestReset(); onMessage(waitMsg) }) { Text(stringResource(R.string.reset_confirm)) } },
        )
        "help" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.settings_help)) },
            text = { Text(stringResource(R.string.help_body)) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun MissionRow(m: Mission, state: SettingsUiState, vm: SettingsViewModel, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val pendingOff = state.app.waiting.firstOrNull { (it.payload as? PendingPayload.MissionPatch)?.let { p -> p.missionId == m.id && p.enabled == false } == true }
    val support = if (pendingOff != null) {
        stringResource(R.string.settings_row_pending_off, Duration.between(state.now, pendingOff.appliesAt).clock())
    } else if (m.isReward) {
        stringResource(R.string.settings_row_reward, m.durationMin, m.rarity.name, m.linkedApps.size, m.dailyLimit ?: Rules.REWARD_DAILY_LIMIT_DEFAULT)
    } else {
        stringResource(R.string.settings_row_task, stringResource(m.category.labelRes), m.durationMin, m.rarity.name, m.weight)
    }
    ListItem(
        headlineContent = { Text(m.title, color = if (m.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) },
        supportingContent = {
            Text(
                support,
                style = if (pendingOff != null) DetoxTheme.textStyles.timerInline else MaterialTheme.typography.bodyMedium,
                color = if (pendingOff != null) DetoxTheme.colors.warning else DetoxTheme.colors.textSecondary,
            )
        },
        leadingContent = { LetterAvatar(m.category) },
        trailingContent = { Switch(checked = m.enabled, onCheckedChange = onToggle) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SwitchRow(title: String, support: String, checked: Boolean, supportIsError: Boolean = false, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(support, color = if (supportIsError) MaterialTheme.colorScheme.error else DetoxTheme.colors.textSecondary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRow(label: String, time: LocalTime, onChange: (LocalTime) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(time.hm(), style = DetoxTheme.textStyles.timerInline, color = DetoxTheme.colors.textSecondary) },
        leadingContent = { Icon(painterResource(R.drawable.ic_schedule), null, modifier = Modifier.size(DetoxTheme.sizes.iconLarge)) },
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        val picker = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            text = { TimePicker(picker) },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = {
                TextButton(onClick = { open = false; onChange(LocalTime.of(picker.hour, picker.minute)) }) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

/** 反映待ちの内容の文言。 */
@Composable
private fun pendingLabel(p: PendingChange, missions: List<Mission>, vm: SettingsViewModel): String {
    fun title(id: String) = missions.firstOrNull { it.id == id }?.title ?: ""
    val on = stringResource(R.string.on)
    val off = stringResource(R.string.off)
    return when (val x = p.payload) {
        is PendingPayload.SettingsPatch -> buildList {
            x.rewardAppLock?.let { add(stringResource(R.string.pending_setting, stringResource(R.string.settings_reward_lock), if (it) on else off)) }
            x.allowForceUnlock?.let { add(stringResource(R.string.pending_setting, stringResource(R.string.settings_allow_unlock), if (it) on else off)) }
            x.allowPause?.let { add(stringResource(R.string.pending_setting, stringResource(R.string.settings_allow_pause), if (it) on else off)) }
            x.feedRewardDailyLimit?.let { add(stringResource(R.string.pending_setting, stringResource(R.string.settings_feed_limit), stringResource(R.string.times, it))) }
            x.quietEnabled?.let { enabled ->
                add(
                    stringResource(
                        R.string.pending_setting,
                        stringResource(R.string.settings_quiet),
                        if (!enabled) off else stringResource(R.string.time_range, LocalTime.parse(x.quietStart).hm(), LocalTime.parse(x.quietEnd).hm()),
                    ),
                )
            }
        }.joinToString("、")
        is PendingPayload.MissionPatch -> buildList {
            x.enabled?.let { add(stringResource(if (it) R.string.pending_enable else R.string.pending_disable)) }
            x.durationMin?.let { add(stringResource(R.string.pending_duration, it)) }
            x.weight?.let { add(stringResource(R.string.pending_weight, it)) }
            x.dailyLimit?.let { add(stringResource(R.string.pending_daily_limit, it)) }
            x.rarity?.let { add(stringResource(R.string.pending_rarity, it.name)) }
            if (x.removeLinkedApps.isNotEmpty()) add(stringResource(R.string.pending_remove_apps, x.removeLinkedApps.joinToString("、") { vm.appLabel(it) }))
        }.joinToString("、").let { stringResource(R.string.pending_mission, title(x.missionId), it) }
        is PendingPayload.DeleteMission -> stringResource(R.string.pending_delete, title(x.missionId))
        is PendingPayload.UnlockApps -> stringResource(R.string.pending_unlock_apps, x.packages.joinToString("、") { vm.appLabel(it) })
        PendingPayload.ResetData -> stringResource(R.string.pending_reset)
        PendingPayload.ForceUnlockAppLock -> stringResource(R.string.pending_app_lock_unlock)
        is PendingPayload.ForceUnlockSession -> stringResource(R.string.force_unlock)
    }
}
