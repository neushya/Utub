package com.utub.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 플레이어 전용 볼륨 칩 (기기 볼륨과 별개, 상한 = 기기 볼륨 크기).
 * [volume]은 슬라이더 위치(0.0~1.0) — 게인 변환은 PlayerViewModel이 담당.
 * 음소거(0)일 때 아이콘을 강조해 "기기 볼륨을 올려도 안 들리는" 오인을 방지한다.
 */
@Composable
internal fun VolumeChip(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeCommit: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(30.dp)) {
            Icon(
                when {
                    volume <= 0.001f -> Icons.AutoMirrored.Filled.VolumeOff
                    volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                    else -> Icons.AutoMirrored.Filled.VolumeUp
                },
                "볼륨",
                modifier = Modifier.size(16.dp),
                tint = if (volume <= 0.001f) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .width(220.dp)
                    .padding(horizontal = 12.dp),
            ) {
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    onValueChangeFinished = onVolumeCommit,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
