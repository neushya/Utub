package com.utub.ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational

/**
 * PIP(화면 속 화면) 파라미터 구성 (F-40, docs/09 후속 개선).
 * - X(시스템 닫기) = 창만 닫힘 → 서비스가 살아 있어 백그라운드 재생 유지 (앱 정체성)
 * - 커스텀 액션 3종: 재생/일시정지 · 다음 · 완전 종료 — X 알럿은 시스템 제약으로
 *   불가하므로 "종료" 버튼을 창 안에 상시 제공해 선택 의도를 충족한다.
 * - API 31+: setAutoEnterEnabled로 홈 제스처 시 부드러운 자동 진입.
 *   API 28~30: MainActivity.onUserLeaveHint에서 enterPictureInPictureMode 폴백.
 */
object PipHelper {

    const val ACTION_PIP_CONTROL = "com.utub.PIP_CONTROL"
    const val EXTRA_CONTROL = "control"
    const val CONTROL_PLAY_PAUSE = 1
    const val CONTROL_NEXT = 2
    const val CONTROL_STOP = 3
    const val CONTROL_PREV = 4

    // 구성(A안, 사용자 확정 — 기기 커스텀 액션 최대 3개 실측):
    // - 하단 3버튼: ⏮ 이전 · ⏯ 재생/일시정지 · ⏭ 다음
    // - 우상단 X: setCloseAction(API 33+)으로 "앱 완전 종료" 재정의 (아이콘/위치는 시스템 고정)
    // - 백그라운드 청취 경로: 전원버튼(화면 끄기) / PIP 엣지 밀어넣기 / 오디오 모드→홈
    // - API 33 미만 폴백: X 재정의 불가 → 하단 [⏯·⏭·⏻앱종료], X=창닫기(백그라운드 유지)
    fun params(context: Context, isPlaying: Boolean, autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        // 완전 종료는 서비스 직행 인텐트 — 창 정리 중에도 유실 없이 전달된다
        val stopPending = PendingIntent.getService(
            context, 3,
            Intent(context, com.utub.playback.PlaybackService::class.java)
                .setAction(com.utub.playback.PlaybackService.ACTION_STOP_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = RemoteAction(
            Icon.createWithResource(context, com.utub.R.drawable.ic_power),
            "앱 완전 종료", "앱 완전 종료", stopPending,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.setActions(
                listOf(
                    remoteAction(context, 4, android.R.drawable.ic_media_previous, "이전", CONTROL_PREV),
                    playPauseAction(context, isPlaying),
                    remoteAction(context, 2, android.R.drawable.ic_media_next, "다음", CONTROL_NEXT),
                ),
            )
            builder.setCloseAction(stop)
        } else {
            builder.setActions(listOf(playPauseAction(context, isPlaying), remoteAction(context, 2, android.R.drawable.ic_media_next, "다음", CONTROL_NEXT), stop))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
        }
        return builder.build()
    }

    private fun playPauseAction(context: Context, isPlaying: Boolean): RemoteAction = remoteAction(
        context, 1,
        if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        if (isPlaying) "일시정지" else "재생",
        CONTROL_PLAY_PAUSE,
    )

    private fun remoteAction(
        context: Context,
        requestCode: Int,
        iconRes: Int,
        title: String,
        control: Int,
    ): RemoteAction {
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(ACTION_PIP_CONTROL).setPackage(context.packageName).putExtra(EXTRA_CONTROL, control),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteAction(Icon.createWithResource(context, iconRes), title, title, pending)
    }
}
