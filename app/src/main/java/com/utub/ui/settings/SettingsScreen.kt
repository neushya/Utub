package com.utub.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utub.data.prefs.BackgroundMode
import com.utub.data.prefs.SettingsRepository
import com.utub.data.prefs.UTubSettings
import com.utub.ui.onboarding.isBatteryOptimizationIgnored
import com.utub.ui.onboarding.openAppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {
    val settings = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UTubSettings())

    fun setBackgroundMode(mode: BackgroundMode) = viewModelScope.launch { repository.setBackgroundMode(mode) }
    fun setAutoplayRelated(v: Boolean) = viewModelScope.launch { repository.setAutoplayRelated(v) }
    fun setDefaultAudioMode(v: Boolean) = viewModelScope.launch { repository.setDefaultAudioMode(v) }
    fun setClipboardDetect(v: Boolean) = viewModelScope.launch { repository.setClipboardDetect(v) }
    fun setContentCountry(code: String) = viewModelScope.launch { repository.setContentCountry(code) }
}

/** 콘텐츠 국가 선택지 (사용자 요청, 2026-08-17) */
val CONTENT_COUNTRIES = listOf(
    "AUTO" to "자동 (기기 설정)",
    "KR" to "한국",
    "US" to "미국",
    "JP" to "일본",
    "TW" to "대만",
    "GB" to "영국",
    "DE" to "독일",
    "FR" to "프랑스",
    "VN" to "베트남",
    "ID" to "인도네시아",
)

/** SCR-700 설정 최소판 (docs/03): 백그라운드 재생 모드·자동재생·기본 모드·권한 상태 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    // Takeout 가져오기 화면 (4차) — 설정 내부 전환, back으로 복귀
    var showTakeout by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showTakeout) {
        TakeoutScreen(onBack = { showTakeout = false })
        return
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
            Text("설정", style = MaterialTheme.typography.titleLarge)
        }

        SectionTitle("데이터")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showTakeout = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("유튜브 데이터 가져오기", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "재생목록·좋아요·시청기록을 UTub으로 (구글 Takeout)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionTitle("재생")

        Text(
            "백그라운드 재생",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        BackgroundModeOption(
            "항상 켬 (기본)", "화면이 꺼지거나 다른 앱을 써도 계속 재생",
            selected = settings.backgroundMode == BackgroundMode.ALWAYS,
        ) { viewModel.setBackgroundMode(BackgroundMode.ALWAYS) }
        BackgroundModeOption(
            "이어폰 연결 시에만", "이어폰·블루투스가 연결된 경우에만 백그라운드 재생",
            selected = settings.backgroundMode == BackgroundMode.HEADSET_ONLY,
        ) { viewModel.setBackgroundMode(BackgroundMode.HEADSET_ONLY) }
        BackgroundModeOption(
            "끔", "화면이 꺼지거나 앱을 벗어나면 일시정지",
            selected = settings.backgroundMode == BackgroundMode.OFF,
        ) { viewModel.setBackgroundMode(BackgroundMode.OFF) }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SwitchRow(
            "연관 영상 자동재생", "대기열이 끝나면 연관 영상을 이어서 재생",
            checked = settings.autoplayRelated, onChange = viewModel::setAutoplayRelated,
        )
        SwitchRow(
            "기본 오디오 모드", "재생을 항상 오디오 전용으로 시작 (데이터 절약)",
            checked = settings.defaultAudioMode, onChange = viewModel::setDefaultAudioMode,
        )
        SwitchRow(
            "클립보드 링크 감지", "복사한 유튜브 링크를 홈에서 바로 재생 제안",
            checked = settings.clipboardDetect, onChange = viewModel::setClipboardDetect,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 콘텐츠 국가 (홈 피드·검색 노출 기준)
        CountryPickerRow(
            current = settings.contentCountry,
            onSelect = viewModel::setContentCountry,
        )

        SectionTitle("권한")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { openAppSettings(context) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("배터리 최적화 예외", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (isBatteryOptimizationIgnored(context)) "설정됨 — 화면 꺼짐 재생 안정" else "미설정 — 재생이 끊길 수 있어요 (탭해서 설정)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionTitle("정보")
        Text(
            "UTub 0.4.0 · 개인용 빌드\nNewPipeExtractor 기반 · 유튜브 규격 변경 시 업데이트 필요할 수 있음",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun CountryPickerRow(current: String, onSelect: (String) -> Unit) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val currentLabel = CONTENT_COUNTRIES.firstOrNull { it.first == current }?.second ?: current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("콘텐츠 국가", style = MaterialTheme.typography.bodyMedium)
            Text(
                "홈 인기 피드와 검색 노출 기준: $currentLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.foundation.layout.Box {
            Text(currentLabel, color = MaterialTheme.colorScheme.primary)
            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                CONTENT_COUNTRIES.forEach { (code, label) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { onSelect(code); open = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun BackgroundModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.padding(4.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
