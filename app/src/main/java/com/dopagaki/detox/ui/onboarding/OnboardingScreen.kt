package com.dopagaki.detox.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.dopagaki.detox.R
import com.dopagaki.detox.ui.components.ButtonStyle
import com.dopagaki.detox.ui.components.Divider
import com.dopagaki.detox.ui.components.FullWidthButton
import com.dopagaki.detox.ui.components.ScreenHeading
import com.dopagaki.detox.ui.components.SectionHeader
import com.dopagaki.detox.ui.theme.DetoxTheme

private enum class Step { Disclosure, OpenSettings, Notifications }

/**
 * S08 初回設定（設計書 9.2）。ユーザー補助の開示と同意を独立した画面で行い、同意してから端末の設定へ案内する。
 * 同意しなくても常時ロックなしで使える。reshow は設定から出し直したとき（開示だけを出す）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    accessibilityOn: Boolean,
    consented: Boolean,
    reshow: Boolean,
    onConsent: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit = {},
) {
    val needsNotif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !reshow
    var step by rememberSaveable { mutableStateOf(Step.Disclosure) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onFinish() }
    fun afterA11y() {
        if (needsNotif) step = Step.Notifications else onFinish()
    }
    val totalSteps = if (needsNotif) 2 else 1
    Scaffold(
        topBar = {
            if (reshow) {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_disclosure)) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) } },
                )
            }
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                val eyebrow = if (reshow || totalSteps == 1) stringResource(R.string.onboarding_eyebrow_single)
                else stringResource(R.string.onboarding_step, if (step == Step.Notifications) 2 else 1, totalSteps)
                when (step) {
                    Step.Disclosure -> {
                        ScreenHeading(eyebrow, stringResource(R.string.disclosure_title))
                        Body(stringResource(R.string.disclosure_body))
                        SectionHeader(stringResource(R.string.disclosure_data))
                        listOf(
                            R.string.disclosure_item_package, R.string.disclosure_item_no_content, R.string.disclosure_item_no_send,
                            R.string.disclosure_item_only_targets, R.string.disclosure_item_off,
                        ).forEach { id ->
                            ListItem(headlineContent = { Text(stringResource(id)) })
                            Divider()
                        }
                        Text(
                            stringResource(R.string.disclosure_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = DetoxTheme.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = DetoxTheme.spacing.screenEdge, vertical = DetoxTheme.spacing.lg),
                        )
                    }
                    Step.OpenSettings -> {
                        ScreenHeading(eyebrow, stringResource(R.string.a11y_settings_title))
                        if (accessibilityOn) Body(stringResource(R.string.a11y_settings_on))
                        else Body(stringResource(R.string.a11y_settings_body))
                    }
                    Step.Notifications -> {
                        ScreenHeading(eyebrow, stringResource(R.string.notif_title))
                        Body(stringResource(R.string.notif_body))
                    }
                }
            }
            Column(
                Modifier.padding(horizontal = DetoxTheme.spacing.screenEdge, vertical = DetoxTheme.spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.sm),
            ) {
                when (step) {
                    Step.Disclosure -> {
                        if (reshow && accessibilityOn && consented) {
                            FullWidthButton(stringResource(R.string.a11y_open_device_settings), onClick = onOpenAccessibilitySettings, icon = R.drawable.ic_open_in_new)
                        } else {
                            FullWidthButton(stringResource(R.string.disclosure_agree), onClick = { onConsent(true); step = Step.OpenSettings })
                            FullWidthButton(stringResource(R.string.disclosure_disagree), onClick = { onConsent(false); if (reshow) onBack() else afterA11y() }, style = ButtonStyle.Outlined)
                        }
                    }
                    Step.OpenSettings -> {
                        if (accessibilityOn) {
                            FullWidthButton(stringResource(R.string.next), onClick = { if (reshow) onBack() else afterA11y() })
                        } else {
                            FullWidthButton(stringResource(R.string.a11y_open_settings), onClick = onOpenAccessibilitySettings, icon = R.drawable.ic_open_in_new)
                            FullWidthButton(stringResource(R.string.not_now), onClick = { if (reshow) onBack() else afterA11y() }, style = ButtonStyle.Text)
                        }
                    }
                    Step.Notifications -> {
                        FullWidthButton(stringResource(R.string.notif_allow), onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) })
                        FullWidthButton(stringResource(R.string.not_now), onClick = onFinish, style = ButtonStyle.Text)
                    }
                }
            }
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = DetoxTheme.spacing.screenEdge, vertical = DetoxTheme.spacing.sm),
    )
}
