package com.greatbody.phonepanel.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.greatbody.phonepanel.data.PanelButton
import com.greatbody.phonepanel.data.PanelClient
import com.greatbody.phonepanel.data.PanelStore
import com.greatbody.phonepanel.data.Pairing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelScreen(
    pairing: Pairing,
    store: PanelStore,
    onRescan: () -> Unit,
    onForget: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(pairing) { PanelClient(pairing) }

    var buttons by remember { mutableStateOf<List<PanelButton>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    /** id -> "pending|ok|err" 用于按钮短暂闪烁 */
    var pulse by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    var showSettings by remember { mutableStateOf(false) }
    val brightness by store.brightness.collectAsStateWithLifecycle(initialValue = -1f)

    suspend fun reload() {
        client.fetchConfig().onSuccess {
            buttons = it.buttons
            loadError = null
        }.onFailure {
            loadError = it.message ?: "load fail"
        }
    }
    LaunchedEffect(pairing) { reload() }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1800)
            toast = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B1020))) {
        if (loadError != null) {
            ErrorPane(message = loadError!!, onRetry = { scope.launch { reload() } }, onRescan = onRescan)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.fillMaxSize().padding(12.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(buttons, key = { it.id }) { btn ->
                    PanelButtonTile(
                        btn = btn,
                        iconUrl = if (btn.isImageIcon) client.iconUrl(btn.id) else null,
                        state = pulse[btn.id],
                        onClick = {
                            if (pulse[btn.id] == "pending") return@PanelButtonTile
                            vibrate(context, 25)
                            pulse = pulse + (btn.id to "pending")
                            scope.launch {
                                val r = client.execute(btn.id)
                                r.onSuccess { res ->
                                    val ok = res.ok
                                    pulse = pulse + (btn.id to if (ok) "ok" else "err")
                                    toast = (if (ok) "${btn.label} ✓ ${res.durationMs}ms"
                                             else "${btn.label} ✕ ${res.error ?: res.stderr ?: "fail"}") to ok
                                    vibrate(context, if (ok) 15 else 80)
                                }.onFailure { e ->
                                    pulse = pulse + (btn.id to "err")
                                    toast = "请求失败: ${e.message}" to false
                                    vibrate(context, 80)
                                }
                                delay(600)
                                pulse = pulse - btn.id
                            }
                        },
                    )
                }
            }
        }

        // 顶部右侧设置按钮
        IconButton(
            onClick = { showSettings = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            Icon(Icons.Default.Settings, contentDescription = "设置", tint = Color(0xFF94A3B8))
        }

        // 顶部居中 toast
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
        ) {
            val t = toast
            if (t != null) {
                Surface(
                    color = if (t.second) Color(0xFF065F46) else Color(0xFF7F1D1D),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = t.first,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
        ) {
            SettingsContent(
                pairing = pairing,
                brightness = brightness,
                onBrightnessChange = { v ->
                    scope.launch { store.saveBrightness(v) }
                },
                onRescan = {
                    showSettings = false
                    onRescan()
                },
                onForget = {
                    showSettings = false
                    onForget()
                },
                onReload = { scope.launch { reload() } },
            )
        }
    }
}

@Composable
private fun PanelButtonTile(
    btn: PanelButton,
    iconUrl: String?,
    state: String?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val baseColor = parseColorOr(btn.color, Color(0xFF334155))
    val targetAlpha = if (state == "pending") 0.55f else 1f
    val alpha by animateFloatAsState(targetAlpha, label = "alpha")

    val borderColor = when (state) {
        "ok" -> Color(0xFF10B981)
        "err" -> Color(0xFFEF4444)
        else -> Color.Transparent
    }
    val borderTargetWidth = if (state == "ok" || state == "err") 4.dp else 0.dp

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .alpha(alpha)
            .clip(RoundedCornerShape(20.dp))
            .background(baseColor)
            .then(
                if (state == "ok" || state == "err")
                    Modifier.background(borderColor.copy(alpha = 0.0f))
                else Modifier
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (iconUrl != null) {
                // 图片图标：占 tile 60%，给 label 留位置
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(iconUrl)
                        // OkHttp 默认 Coil 调度，弱 ETag 由后端发，磁盘缓存自动
                        .crossfade(true)
                        .build(),
                    contentDescription = btn.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(64.dp),
                )
            } else {
                Text(
                    text = btn.icon.ifEmpty { "·" },
                    fontSize = 44.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = btn.label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        // 状态边框（用一个 overlay 实现，不影响圆角剪裁）
        if (borderTargetWidth.value > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(borderColor.copy(alpha = 0.18f))
            )
        }
    }
}

@Composable
private fun ErrorPane(message: String, onRetry: () -> Unit, onRescan: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("加载按钮失败", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Color(0xFF94A3B8))
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry) { Text("重试") }
            OutlinedButton(onClick = onRescan) { Text("重新扫码") }
        }
    }
}

@Composable
private fun SettingsContent(
    pairing: Pairing,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    onRescan: () -> Unit,
    onForget: () -> Unit,
    onReload: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        Text("当前服务器", style = MaterialTheme.typography.labelMedium)
        Text(pairing.baseUrl, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BrightnessHigh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (brightness < 0f) "亮度：跟随系统"
                else "亮度：${(brightness * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Slider(
            value = if (brightness < 0f) 0.5f else brightness.coerceIn(0.01f, 1f),
            onValueChange = onBrightnessChange,
            valueRange = 0.01f..1f,
        )
        TextButton(onClick = { onBrightnessChange(-1f) }) {
            Text("跟随系统")
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onReload,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("重新加载按钮")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onRescan,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("重新扫码（换服务器/换 token）")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onForget,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626)),
        ) { Text("忘记当前配对") }

        Spacer(Modifier.height(20.dp))
    }
}

private fun parseColorOr(hex: String, fallback: Color): Color = try {
    val s = if (hex.startsWith("#")) hex else "#$hex"
    Color(android.graphics.Color.parseColor(s))
} catch (_: Throwable) {
    fallback
}

private fun vibrate(context: Context, durationMs: Long) {
    val v: Vibrator? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    v?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
}
