package com.utub.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * SCR-000 권한 온보딩 (docs/03):
 * 1장 알림 권한 → 2장 배터리 최적화 예외. 영구 거부 시 앱 설정으로 이동 (agy 검토 P1-3 반영)
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableIntStateOf(0) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { page = 1 }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (page) {
            0 -> {
                Icon(
                    Icons.Default.Notifications, contentDescription = null,
                    modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(24.dp))
                Text("알림 권한", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "잠금화면과 알림에서 재생을 제어하려면 알림 권한이 필요해요.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            page = 1
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("허용하기") }
                TextButton(onClick = { page = 1 }) { Text("나중에") }
            }
            else -> {
                Icon(
                    Icons.Default.BatteryChargingFull, contentDescription = null,
                    modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(24.dp))
                Text("배터리 설정", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "화면을 꺼도 재생이 끊기지 않으려면 배터리 최적화 예외가 필요해요.\n(갤럭시 절전 기능이 재생을 중단시킬 수 있어요)",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = {
                        requestIgnoreBatteryOptimization(context)
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("허용하기") }
                TextButton(onClick = onDone) { Text("나중에") }
            }
        }
    }
}

private fun requestIgnoreBatteryOptimization(context: Context) {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
    val intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
    runCatching { context.startActivity(intent) }.onFailure {
        // 일부 기기는 다이렉트 요청 미지원 → 설정 목록 화면으로 (영구 거부 대응 포함)
        runCatching {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}

fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )
}
