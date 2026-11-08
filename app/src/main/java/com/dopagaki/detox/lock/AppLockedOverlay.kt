package com.dopagaki.detox.lock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.graphics.drawable.toBitmap
import com.dopagaki.detox.R
import com.dopagaki.detox.container
import com.dopagaki.detox.domain.session.SessionRules
import com.dopagaki.detox.ui.common.grouped
import com.dopagaki.detox.ui.common.ticker
import com.dopagaki.detox.ui.components.ButtonStyle
import com.dopagaki.detox.ui.components.FullWidthButton
import com.dopagaki.detox.ui.components.InfoChip
import com.dopagaki.detox.ui.running.UnlockWaitContent
import com.dopagaki.detox.ui.theme.DetoxTheme
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * S07 アプリのロック画面（設計書 6.2、6.3）。対象アプリの上に TYPE_ACCESSIBILITY_OVERLAY で重ねる。
 * 選べるのは「ドパガキデトックスを開く」と「強制解除」（10分待ち）の2つだけ。
 */
@Composable
fun AppLockedOverlay(packageName: String, onOpenApp: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val state by c.store.state.collectAsState()
    val now by remember { c.ticker() }.collectAsState(initial = c.clock.now())
    val label = remember(packageName) { Apps.label(context, packageName) }
    val icon = remember(packageName) { Apps.icon(context, packageName)?.toBitmap()?.asImageBitmap() }
    val wait = state.appLockUnlock
    val running = state.running?.let { SessionRules.normalize(it, now) }
    val runningMission = running?.let { state.mission(it.missionId) }
    val pausedReward = running != null && running.isPaused && runningMission?.isReward == true && packageName in runningMission.linkedApps
    val expired = running != null && runningMission?.isReward == true && packageName in runningMission.linkedApps && SessionRules.isOver(running, now)

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(DetoxTheme.sizes.lockTile)) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        if (icon != null) Image(icon, null, Modifier.size(DetoxTheme.sizes.lockTileIcon))
                        else Icon(painterResource(R.drawable.ic_apps), null, Modifier.size(DetoxTheme.sizes.lockTileIcon))
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    border = BorderStroke(DetoxTheme.sizes.lockBadgeBorder, MaterialTheme.colorScheme.background),
                    modifier = Modifier.size(DetoxTheme.sizes.lockBadge).align(Alignment.BottomEnd)
                        .offset(x = DetoxTheme.spacing.sm, y = DetoxTheme.spacing.sm),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_lock), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(DetoxTheme.sizes.iconSmall))
                    }
                }
            }
            Spacer(Modifier.height(DetoxTheme.spacing.xxl))
            Text(
                if (expired) stringResource(R.string.locked_expired_title, label) else stringResource(R.string.locked_title, label),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = DetoxTheme.spacing.screenEdge),
            )
            Spacer(Modifier.height(DetoxTheme.spacing.md))
            Text(
                when {
                    expired -> stringResource(R.string.locked_expired_body)
                    pausedReward -> stringResource(R.string.locked_paused_body, label)
                    else -> stringResource(R.string.locked_body)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = DetoxTheme.spacing.screenEdge + DetoxTheme.spacing.lg),
            )
            Spacer(Modifier.height(DetoxTheme.spacing.xl))
            InfoChip(stringResource(R.string.locked_coins, state.wallet.coins.grouped()), icon = R.drawable.ic_toll, iconTint = DetoxTheme.colors.coin, numeric = true)
            if (wait != null) {
                Spacer(Modifier.height(DetoxTheme.spacing.xxl))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = DetoxTheme.spacing.screenEdge),
                ) {
                    Column(Modifier.padding(DetoxTheme.spacing.xl)) {
                        UnlockWaitContent(
                            Duration.between(now, wait.appliesAt).coerceAtLeast(Duration.ZERO),
                            stringResource(R.string.locked_unlock_body, label),
                            large = false,
                            withHeading = true,
                        )
                        Row(Modifier.fillMaxWidth().padding(top = DetoxTheme.spacing.lg), horizontalArrangement = Arrangement.End) {
                            Button(
                                onClick = { c.scope.launch { c.store.cancelPending(wait.id) } },
                                modifier = Modifier.height(DetoxTheme.sizes.buttonHeightDialog),
                            ) { Text(stringResource(R.string.locked_unlock_cancel)) }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.padding(horizontal = DetoxTheme.spacing.screenEdge).padding(bottom = DetoxTheme.spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(DetoxTheme.spacing.sm),
            ) {
                FullWidthButton(stringResource(R.string.locked_open_app), onClick = onOpenApp)
                if (wait == null && state.settings.allowForceUnlock) {
                    FullWidthButton(
                        stringResource(R.string.locked_force_unlock),
                        onClick = { c.scope.launch { c.store.requestAppLockUnlock(); c.afterAction() } },
                        style = ButtonStyle.Text,
                    )
                }
            }
        }
    }
}
