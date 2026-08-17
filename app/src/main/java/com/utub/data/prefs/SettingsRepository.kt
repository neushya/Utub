package com.utub.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 백그라운드 재생 모드 (docs/02 4.1절 — 사용자 요청 반영, 기본 항상 켬) */
enum class BackgroundMode { ALWAYS, HEADSET_ONLY, OFF }

data class UTubSettings(
    val backgroundMode: BackgroundMode = BackgroundMode.ALWAYS,
    val autoplayRelated: Boolean = true,
    val defaultAudioMode: Boolean = false,
    val clipboardDetect: Boolean = true,
    val onboardingDone: Boolean = false,
    val sleepTimerMinutes: Int = 0, // 0 = 끔, -1 = 현재 영상 끝까지
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "utub_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BACKGROUND_MODE = stringPreferencesKey("background_mode")
        val AUTOPLAY_RELATED = booleanPreferencesKey("autoplay_related")
        val DEFAULT_AUDIO_MODE = booleanPreferencesKey("default_audio_mode")
        val CLIPBOARD_DETECT = booleanPreferencesKey("clipboard_detect")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val SLEEP_TIMER_MIN = intPreferencesKey("sleep_timer_minutes")
    }

    val settings: Flow<UTubSettings> = context.dataStore.data.map { p ->
        UTubSettings(
            backgroundMode = p[Keys.BACKGROUND_MODE]?.let { runCatching { BackgroundMode.valueOf(it) }.getOrNull() }
                ?: BackgroundMode.ALWAYS,
            autoplayRelated = p[Keys.AUTOPLAY_RELATED] ?: true,
            defaultAudioMode = p[Keys.DEFAULT_AUDIO_MODE] ?: false,
            clipboardDetect = p[Keys.CLIPBOARD_DETECT] ?: true,
            onboardingDone = p[Keys.ONBOARDING_DONE] ?: false,
            sleepTimerMinutes = p[Keys.SLEEP_TIMER_MIN] ?: 0,
        )
    }

    suspend fun setBackgroundMode(mode: BackgroundMode) =
        context.dataStore.edit { it[Keys.BACKGROUND_MODE] = mode.name }

    suspend fun setAutoplayRelated(enabled: Boolean) =
        context.dataStore.edit { it[Keys.AUTOPLAY_RELATED] = enabled }

    suspend fun setDefaultAudioMode(enabled: Boolean) =
        context.dataStore.edit { it[Keys.DEFAULT_AUDIO_MODE] = enabled }

    suspend fun setClipboardDetect(enabled: Boolean) =
        context.dataStore.edit { it[Keys.CLIPBOARD_DETECT] = enabled }

    suspend fun setOnboardingDone() =
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }

    suspend fun setSleepTimerMinutes(minutes: Int) =
        context.dataStore.edit { it[Keys.SLEEP_TIMER_MIN] = minutes }
}
