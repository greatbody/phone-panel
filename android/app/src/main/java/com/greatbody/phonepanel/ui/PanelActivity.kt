package com.greatbody.phonepanel.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.greatbody.phonepanel.data.PanelStore
import com.greatbody.phonepanel.data.Pairing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PanelActivity : ComponentActivity() {

    private lateinit var store: PanelStore

    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val url = result.data?.getStringExtra(ScanActivity.EXTRA_RESULT_URL)
        if (url != null) {
            val pairing = Pairing.fromQrUrl(url)
            if (pairing != null) {
                lifecycleScope.launch { store.savePairing(pairing) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PanelStore(applicationContext)

        // 保持常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 边到边
        WindowCompat.setDecorFitsSystemWindows(window, true)

        // 应用持久化的亮度
        lifecycleScope.launch {
            val b = store.brightness.first()
            applyBrightness(b)
        }

        setContent {
            PhonePanelTheme {
                val pairing by store.pairing.collectAsStateWithLifecycle(initialValue = null)
                val brightness by store.brightness.collectAsStateWithLifecycle(initialValue = -1f)

                // 亮度变化时即时应用
                LaunchedEffect(brightness) { applyBrightness(brightness) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (pairing == null) {
                        PairingGuide(onScan = ::launchScan)
                    } else {
                        PanelScreen(
                            pairing = pairing!!,
                            store = store,
                            onRescan = ::launchScan,
                            onForget = {
                                lifecycleScope.launch { store.clearPairing() }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun launchScan() {
        scanLauncher.launch(Intent(this, ScanActivity::class.java))
    }

    /** -1 表示系统亮度；0..1 设置当前窗口的覆盖亮度 */
    private fun applyBrightness(v: Float) {
        val lp = window.attributes
        lp.screenBrightness = if (v < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            else v.coerceIn(0.01f, 1f)
        window.attributes = lp
    }
}

@Composable
private fun PairingGuide(onScan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1020))
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Phone Panel", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(
            "在电脑上启动 phone-panel 服务，然后扫描 admin 页面的二维码。",
            color = Color(0xFF94A3B8),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onScan) { Text("扫码配对") }
    }
}
