package com.utub.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utub.takeout.TakeoutImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 유튜브 데이터 가져오기 화면 (4차 — Takeout).
 * 사용자 요구: 아무것도 모르는 사람도 따라 할 수 있는 단계별 안내 + 필요한 링크 버튼 필수.
 */
@HiltViewModel
class TakeoutViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importer: TakeoutImporter,
) : ViewModel() {

    private val _progress = MutableStateFlow<TakeoutImporter.Progress?>(null)
    val progress: StateFlow<TakeoutImporter.Progress?> = _progress

    private var job: Job? = null

    fun startImport(uri: Uri) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("파일을 열 수 없어요")
                stream.use { importer.import(it) { p -> _progress.value = p } }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _progress.value = TakeoutImporter.Progress.Failed(
                    e.message ?: "가져오기에 실패했어요 — 파일을 확인해 주세요",
                )
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _progress.value = null
    }
}

@Composable
fun TakeoutScreen(
    onBack: () -> Unit,
    viewModel: TakeoutViewModel = hiltViewModel(),
) {
    val progress by viewModel.progress.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    BackHandler { onBack() }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::startImport) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
            Text("유튜브 데이터 가져오기", style = MaterialTheme.typography.titleMedium)
        }

        when (val p = progress) {
            null -> GuideContent(
                onOpenTakeout = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://takeout.google.com")),
                    )
                },
                onPickFile = { filePicker.launch(arrayOf("application/zip", "application/octet-stream")) },
            )

            is TakeoutImporter.Progress.Done -> ResultContent(p.summary, onBack)

            is TakeoutImporter.Progress.Failed -> {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("가져오지 못했어요", style = MaterialTheme.typography.titleSmall)
                    Text(p.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = viewModel::cancel) { Text("다시 시도") }
                }
            }

            else -> ProgressContent(p, onCancel = viewModel::cancel)
        }
    }
}

@Composable
private fun GuideContent(onOpenTakeout: () -> Unit, onPickFile: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "유튜브 계정의 재생목록 · 좋아요 · 시청기록을 UTub으로 옮겨요.\n구글이 공식 제공하는 \"내 데이터 내려받기(Takeout)\"를 사용하며, 한 번만 하면 됩니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StepTitle("1단계 · 구글에서 내 데이터 신청하기")
        StepBody(
            "아래 버튼으로 구글 Takeout을 열고 이렇게 하세요:\n" +
                "  ① \"모두 선택 해제\"를 누르기\n" +
                "  ② 목록에서 YouTube 및 YouTube Music만 체크 ✓\n" +
                "  ③ (시청기록도 원하면) \"여러 형식\" 버튼 → 기록 형식을 HTML에서 JSON으로 변경\n" +
                "  ④ \"다음 단계\" → \"내보내기 생성\" 누르기",
        )
        Button(onClick = onOpenTakeout, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
            Text("  구글 Takeout 열기 (takeout.google.com)")
        }

        StepTitle("2단계 · 파일 다운로드")
        StepBody(
            "몇 분~몇 시간 뒤 구글에서 메일이 와요.\n" +
                "메일의 \"파일 다운로드\" 버튼을 눌러 ZIP 파일을 이 휴대폰에 저장하세요.",
        )

        StepTitle("3단계 · 받은 파일 선택")
        StepBody("다운로드한 ZIP 파일을 그대로 선택하면 자동으로 가져와요. (압축을 풀 필요 없음)")
        Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FolderZip, null, modifier = Modifier.size(18.dp))
            Text("  ZIP 파일 선택하기")
        }

        Text(
            "가져오는 것: 재생목록 · 좋아요 · 시청기록(최근 500건)\n" +
                "이미 UTub에 있는 항목은 그대로 두고 건너뜁니다.\n" +
                "\"나중에 볼 동영상\"은 구글이 내보내기에서 제외해 가져올 수 없어요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProgressContent(p: TakeoutImporter.Progress?, onCancel: () -> Unit) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val (label, done, total) = when (p) {
            is TakeoutImporter.Progress.Importing -> Triple("목록 가져오는 중", p.done, p.total)
            is TakeoutImporter.Progress.FillingTitles -> Triple("영상 제목 불러오는 중 (오래 걸릴 수 있어요)", p.done, p.total)
            else -> Triple("파일 읽는 중…", 0, 0)
        }
        Text(label, style = MaterialTheme.typography.titleSmall)
        if (total > 0) {
            LinearProgressIndicator(
                progress = { done.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("$done / $total", style = MaterialTheme.typography.bodySmall)
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(
            "화면을 벗어나도 가져온 항목은 유지돼요. 제목이 비어 있는 항목은 재생하면 자동으로 채워져요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onCancel) { Text("중단") }
    }
}

@Composable
private fun ResultContent(summary: TakeoutImporter.Summary, onBack: () -> Unit) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("가져오기 완료 🎉", style = MaterialTheme.typography.titleMedium)
        Text(
            buildString {
                appendLine("· 재생목록 ${summary.playlistCount}개 (영상 ${summary.playlistItemCount}개)")
                appendLine("· 좋아요 ${summary.likedCount}개")
                appendLine("· 시청기록 ${summary.historyCount}건")
                if (summary.htmlHistoryFound) {
                    appendLine()
                    appendLine("⚠ 시청기록이 HTML 형식이라 가져오지 못했어요 — 다시 내보낼 땐 1단계 ③처럼 기록 형식을 JSON으로 바꿔주세요.")
                }
            }.trim(),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "보관함에서 확인해 보세요. (이미 있던 항목은 중복 없이 건너뛴 숫자예요)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("확인") }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun StepBody(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
