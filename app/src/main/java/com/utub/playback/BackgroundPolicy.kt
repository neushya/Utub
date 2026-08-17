package com.utub.playback

import com.utub.data.prefs.BackgroundMode

/**
 * 백그라운드/화면꺼짐 재생 정책 (docs/02 4.1절, TC-PB-16).
 * 화면이 꺼지거나 앱이 백그라운드로 갈 때 재생을 계속할지 결정한다.
 */
object BackgroundPolicy {

    /**
     * @param mode 사용자 설정 모드
     * @param isExternalOutput 이어폰/블루투스/외부 스피커 연결 여부
     * @return true면 재생 유지, false면 일시정지
     */
    fun shouldContinueInBackground(mode: BackgroundMode, isExternalOutput: Boolean): Boolean =
        when (mode) {
            BackgroundMode.ALWAYS -> true
            BackgroundMode.HEADSET_ONLY -> isExternalOutput
            BackgroundMode.OFF -> false
        }
}
