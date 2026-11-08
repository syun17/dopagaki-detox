package com.dopagaki.detox.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.dopagaki.detox.R
import com.dopagaki.detox.domain.Category
import com.dopagaki.detox.domain.Rarity
import com.dopagaki.detox.domain.Rules
import com.dopagaki.detox.domain.model.Mission
import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.model.MissionValidation
import com.dopagaki.detox.domain.model.TimeTag
import com.dopagaki.detox.domain.preset.Presets
import com.dopagaki.detox.lock.InstalledApp
import com.dopagaki.detox.ui.components.LoadingState
import com.dopagaki.detox.ui.components.REWARD_CATEGORIES
import com.dopagaki.detox.ui.components.TASK_CATEGORIES
import com.dopagaki.detox.ui.components.editorLabelRes
import com.dopagaki.detox.ui.components.iconRes
import com.dopagaki.detox.ui.theme.DetoxTheme

private const val DURATION_STEP = Rules.DURATION_STEP_MIN

/** ミッションの追加・編集（S04 の下。5.5）。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MissionEditScreen(
    vm: SettingsViewModel,
    missionId: String?,
    kind: MissionKind,
    onClose: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val original = remember(missionId) { missionId?.let { vm.mission(it) } }
    val initial = remember(missionId) {
        original ?: run {
            val min = if (kind == MissionKind.TASK) 15 else 15
            Mission(
                id = vm.newMissionId(), title = "", kind = kind,
                category = if (kind == MissionKind.TASK) Category.READING else Category.GAME,
                durationMin = min, rarity = Presets.defaultRarity(kind, min), weight = Rules.PRESET_WEIGHT,
                dailyLimit = if (kind == MissionKind.REWARD) Rules.REWARD_DAILY_LIMIT_DEFAULT else null,
                linkedApps = emptySet(), timeTags = emptySet(), enabled = true, lastDoneAt = null,
            )
        }
    }
    var draft by remember(missionId) { mutableStateOf(initial) }
    var rarityTouched by remember(missionId) { mutableStateOf(original != null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var pickingApps by remember { mutableStateOf(false) }
    val changed = draft != initial
    val savedMsg = stringResource(R.string.edit_saved)
    val savedWaitMsg = stringResource(R.string.edit_saved_wait)

    if (pickingApps) {
        AppPicker(vm, draft.linkedApps, onDone = { draft = draft.copy(linkedApps = it); pickingApps = false })
        return
    }
    BackHandler(enabled = changed) { dialog = "discard" }

    val title = stringResource(
        when {
            kind == MissionKind.TASK && original == null -> R.string.edit_add_task
            kind == MissionKind.TASK -> R.string.edit_edit_task
            original == null -> R.string.edit_add_reward
            else -> R.string.edit_edit_reward
        },
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = { if (changed) dialog = "discard" else onClose() }) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close))
                    }
                },
                actions = {
                    if (original != null) {
                        IconButton(onClick = { dialog = "delete" }) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete)) }
                    }
                    TextButton(
                        onClick = {
                            vm.saveMission(draft.copy(title = draft.title.trim())) { ok, waits ->
                                if (ok) { onMessage(if (waits) savedWaitMsg else savedMsg); onClose() }
                            }
                        },
                        enabled = MissionValidation.isValid(draft.copy(title = draft.title.trim())),
                    ) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = DetoxTheme.spacing.screenEdgeList, vertical = DetoxTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.xl),
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it) },
                label = { Text(stringResource(R.string.edit_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Field(stringResource(R.string.edit_category)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.sm)) {
                    (if (kind == MissionKind.TASK) TASK_CATEGORIES else REWARD_CATEGORIES).forEach { c ->
                        FilterChip(
                            selected = draft.category == c,
                            onClick = { draft = draft.copy(category = c) },
                            label = { Text(stringResource(c.editorLabelRes)) },
                            leadingIcon = { Icon(painterResource(c.iconRes), null, modifier = Modifier.size(DetoxTheme.sizes.iconMedium)) },
                        )
                    }
                }
            }
            val range = if (kind == MissionKind.TASK) Rules.TASK_DURATION_MIN_RANGE else Rules.REWARD_DURATION_MIN_RANGE
            Field(stringResource(R.string.edit_duration, draft.durationMin)) {
                Slider(
                    value = draft.durationMin.toFloat(),
                    onValueChange = { v ->
                        val min = (v / DURATION_STEP).toInt() * DURATION_STEP
                        draft = draft.copy(durationMin = min, rarity = if (rarityTouched) draft.rarity else Presets.defaultRarity(kind, min))
                    },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    steps = (range.last - range.first) / DURATION_STEP - 1,
                )
            }
            Field(stringResource(R.string.edit_rarity)) {
                val rarities = (if (kind == MissionKind.TASK) Rules.TASK_RARITIES else Rules.REWARD_RARITIES).sorted()
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    rarities.forEachIndexed { i, r ->
                        SegmentedButton(
                            selected = draft.rarity == r,
                            onClick = { draft = draft.copy(rarity = r); rarityTouched = true },
                            shape = SegmentedButtonDefaults.itemShape(i, rarities.size),
                        ) { Text(r.name) }
                    }
                }
                val byTime = Presets.defaultRarity(kind, draft.durationMin)
                if (kind == MissionKind.TASK && draft.rarity > byTime) {
                    Text(stringResource(R.string.edit_rarity_wait, byTime.name), style = MaterialTheme.typography.bodySmall, color = DetoxTheme.colors.warning)
                }
            }
            Field(stringResource(R.string.edit_weight)) {
                val weights = Rules.WEIGHT_RANGE.toList()
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    weights.forEachIndexed { i, w ->
                        SegmentedButton(
                            selected = draft.weight == w,
                            onClick = { draft = draft.copy(weight = w) },
                            shape = SegmentedButtonDefaults.itemShape(i, weights.size),
                        ) { Text(w.toString()) }
                    }
                }
                Hint(stringResource(R.string.edit_weight_hint))
            }
            OutlinedTextField(
                value = draft.note,
                onValueChange = { draft = draft.copy(note = it) },
                label = { Text(stringResource(R.string.edit_note)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Field(stringResource(R.string.edit_time_tags)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.sm)) {
                    listOf(TimeTag.MORNING to R.string.tag_morning, TimeTag.DAY to R.string.tag_day, TimeTag.NIGHT to R.string.tag_night).forEach { (t, label) ->
                        FilterChip(
                            selected = t in draft.timeTags,
                            onClick = { draft = draft.copy(timeTags = if (t in draft.timeTags) draft.timeTags - t else draft.timeTags + t) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                Hint(stringResource(R.string.edit_time_tags_hint))
            }
            if (kind == MissionKind.REWARD) {
                val limit = draft.dailyLimit ?: Rules.REWARD_DAILY_LIMIT_DEFAULT
                val lr = Rules.REWARD_DAILY_LIMIT_RANGE
                Field(stringResource(R.string.edit_daily_limit, limit)) {
                    Slider(
                        value = limit.toFloat(),
                        onValueChange = { draft = draft.copy(dailyLimit = it.toInt()) },
                        valueRange = lr.first.toFloat()..lr.last.toFloat(),
                        steps = lr.last - lr.first - 1,
                    )
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.edit_apps)) },
                    supportingContent = {
                        Text(
                            if (draft.linkedApps.isEmpty()) stringResource(R.string.edit_apps_none)
                            else draft.linkedApps.map { vm.appLabel(it) }.sorted().joinToString("、"),
                            color = DetoxTheme.colors.textSecondary,
                        )
                    },
                    leadingContent = { Icon(painterResource(R.drawable.ic_apps), null) },
                    trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null) },
                    modifier = Modifier.clickable { pickingApps = true },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.edit_enabled)) },
                trailingContent = { Switch(checked = draft.enabled, onCheckedChange = { draft = draft.copy(enabled = it) }) },
            )
        }
    }

    when (dialog) {
        "discard" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.edit_discard_title)) },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = { TextButton(onClick = { dialog = null; onClose() }) { Text(stringResource(R.string.edit_discard)) } },
        )
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null },
            text = { Text(stringResource(if (kind == MissionKind.TASK) R.string.edit_delete_task else R.string.edit_delete_reward)) },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = {
                TextButton(onClick = { dialog = null; original?.let { vm.deleteMission(it.id) }; onClose() }) { Text(stringResource(R.string.delete)) }
            },
        )
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.sm)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = DetoxTheme.colors.textSecondary)
}

/** 対象アプリの選択。除外アプリは出さない。連絡アプリは警告する（6.2）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPicker(vm: SettingsViewModel, selected: Set<String>, onDone: (Set<String>) -> Unit) {
    LaunchedEffect(Unit) { vm.loadApps() }
    val apps by vm.apps.collectAsState()
    var chosen by remember { mutableStateOf(selected) }
    var warn by remember { mutableStateOf<InstalledApp?>(null) }
    BackHandler { onDone(chosen) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.apps_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = { onDone(chosen) }) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) }
                },
            )
        },
    ) { inner ->
        val list = apps
        when {
            list == null -> LoadingState(Modifier.padding(inner))
            list.isEmpty() -> com.dopagaki.detox.ui.components.EmptyState(stringResource(R.string.apps_empty), stringResource(R.string.apps_empty_body), Modifier.padding(inner))
            else -> LazyColumn(Modifier.padding(inner)) {
                items(list, key = { it.packageName }) { app ->
                    val on = app.packageName in chosen
                    ListItem(
                        headlineContent = { Text(app.label) },
                        leadingContent = { AppIcon(app.packageName) },
                        trailingContent = { Checkbox(checked = on, onCheckedChange = null) },
                        modifier = Modifier.clickable {
                            if (on) chosen = chosen - app.packageName
                            else if (app.isMessaging) warn = app
                            else chosen = chosen + app.packageName
                        },
                    )
                }
            }
        }
    }
    warn?.let { app ->
        AlertDialog(
            onDismissRequest = { warn = null },
            text = { Text(stringResource(R.string.apps_messaging_warning)) },
            dismissButton = { TextButton(onClick = { warn = null }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = { TextButton(onClick = { chosen = chosen + app.packageName; warn = null }) { Text(stringResource(R.string.apps_choose)) } },
        )
    }
}

@Composable
fun AppIcon(packageName: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap = remember(packageName) { com.dopagaki.detox.lock.Apps.icon(context, packageName)?.toBitmap()?.asImageBitmap() }
    if (bitmap != null) {
        androidx.compose.foundation.Image(bitmap, null, modifier = Modifier.size(DetoxTheme.sizes.avatarTimeline))
    } else {
        Icon(painterResource(R.drawable.ic_apps), null, modifier = Modifier.size(DetoxTheme.sizes.avatarTimeline))
    }
}
