package com.dopagaki.detox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.dopagaki.detox.ui.theme.DetoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35 以降は edge-to-edge が必須（設計書 9.4）。余白は Scaffold のインセットで処理する
        enableEdgeToEdge()
        setContent {
            DetoxTheme {
                DetoxApp()
            }
        }
    }
}

/**
 * アプリの骨組み。画面と遷移は navigation/ と ui/<画面>/ に作る（/implement-screen）。
 * 中央揃えのトップバー＋区切り線はホーム（S01）だけの形。ホームを作るときに ui/home/ へ移し、
 * ガチャ・設定は左揃え・区切り線なし、ロック中はナビゲーションバーを出さない（docs/design-system.md 8章）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetoxApp() {
    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                )
                HorizontalDivider(color = DetoxTheme.colors.divider)
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DetoxAppPreview() {
    DetoxTheme {
        DetoxApp()
    }
}
