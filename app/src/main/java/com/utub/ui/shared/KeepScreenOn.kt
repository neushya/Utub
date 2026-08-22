package com.utub.ui.shared

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.utub.ui.player.findActivity

/**
 * [active]인 동안 화면 자동 꺼짐(딤오프)을 막는다 — 영상 시청 중 화면 유지
 * (결함 보고 2026-08-22: 재생 도중 딤오프되어 소리만 남음).
 *
 * 앱 어디에도 화면 유지 코드가 없던 것이 원인: PlaybackService의
 * WAKE_MODE_NETWORK는 화면이 꺼진 "뒤" CPU·네트워크만 잡고, media3 PlayerView(1.7.1)는
 * keepScreenOn을 관리하지 않는다. 오디오 전용 모드는 "화면 끄고 듣기"가 목적이므로
 * 호출부에서 제외한다.
 *
 * DisposableEffect(active)가 조건 변화·화면 이탈 시 플래그 해제를 보장 —
 * 잘못돼도 "화면이 좀 더 오래 켜짐" 방향으로만 열화되는 안전한 실패.
 */
@Composable
fun KeepScreenOnWhile(active: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(active) {
        val window = activity?.window
        android.util.Log.i("UTubKeepScreen", "effect active=$active window=${window != null}")
        if (active && window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            android.util.Log.i("UTubKeepScreen", "dispose active=$active")
            if (active && window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}
