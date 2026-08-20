package com.utub.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * SCR-300 전체화면 진입/해제 (docs/09 ⑦) — 회전·시스템바·컷아웃을 한 곳에서 관리.
 * 매니페스트는 세로 고정이지만 requestedOrientation이 런타임에 이를 덮어쓰고,
 * configChanges에 orientation이 선언돼 있어 회전 시 액티비티가 재생성되지 않는다
 * (= 재생이 끊기지 않는다).
 */
object Fullscreen {

    fun enter(activity: Activity) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        // 펀치홀 영역까지 확장 (몰입 모드에서 좌우 검은 띠 방지)
        activity.window.attributes = activity.window.attributes.also {
            it.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun exit(activity: Activity) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity.window.attributes = activity.window.attributes.also {
            it.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
        WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }
}

/** Compose LocalContext에서 Activity를 찾는다 (테마 래퍼 체인 대응) */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
