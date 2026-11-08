package com.dopagaki.detox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.dopagaki.detox.lock.Apps
import com.dopagaki.detox.navigation.DetoxApp
import com.dopagaki.detox.ui.theme.DetoxTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35 以降は edge-to-edge が必須（設計書 9.4）。余白は Scaffold のインセットで処理する
        enableEdgeToEdge()
        setContent {
            DetoxTheme {
                DetoxApp(container)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // ユーザー補助がオフになっていた時間を記録する（6.4）。開くたびに状態を確かめる
        val c = container
        lifecycleScope.launch {
            c.ready.await()
            c.store.recordAccessibility(Apps.isAccessibilityEnabled(this@MainActivity))
            c.store.tick()
        }
    }
}
