package com.utub.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utub.backup.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 설정 [데이터] — UTub 백업/복원 (5차). 재생목록·좋아요·나중에 보기·시청기록을
 * JSON 파일로 내보내고, 새 기기에서 중복 없이 병합 복원한다. 다운로드 영상 파일은 제외.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: BackupManager,
) : ViewModel() {

    fun export(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val out = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("파일을 만들 수 없어요")
                val s = out.use { manager.export(it) }
                toast("백업 완료 — 재생목록 ${s.playlists}개(영상 ${s.playlistItems}개) · 좋아요 ${s.liked} · 나중에보기 ${s.watchLater} · 기록 ${s.history}건")
            } catch (e: Exception) {
                toast(e.message ?: "백업에 실패했어요")
            }
        }
    }

    fun import(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("파일을 열 수 없어요")
                val s = input.use { manager.import(it) }
                toast("복원 완료 — 새로 추가: 재생목록 ${s.playlists}개(영상 ${s.playlistItems}개) · 좋아요 ${s.liked} · 나중에보기 ${s.watchLater} · 기록 ${s.history}건")
            } catch (e: Exception) {
                toast(e.message ?: "복원에 실패했어요 — UTub 백업 파일인지 확인해 주세요")
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}

@Composable
fun BackupSection(viewModel: BackupViewModel = hiltViewModel()) {
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::export) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::import) }

    SettingRow(
        title = "백업 파일 만들기",
        subtitle = "재생목록·좋아요·나중에보기·시청기록을 파일로 (다운로드 영상은 제외)",
    ) {
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        exportLauncher.launch("utub-backup-$stamp.json")
    }
    SettingRow(
        title = "백업 파일 복원",
        subtitle = "백업 JSON 선택 — 기존 데이터는 그대로, 없는 것만 추가돼요",
    ) {
        importLauncher.launch(arrayOf("application/json", "application/octet-stream"))
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
