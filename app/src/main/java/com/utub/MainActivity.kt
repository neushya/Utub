package com.utub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.core.content.ContextCompat
import com.utub.data.prefs.SettingsRepository
import com.utub.playback.PlayerConnection
import androidx.lifecycle.lifecycleScope
import com.utub.playback.PlayerStateHolder
import kotlinx.coroutines.launch
import com.utub.ui.player.PipHelper
import com.utub.ui.player.findActivity
import com.utub.ui.home.HomeScreen
import com.utub.ui.onboarding.OnboardingScreen
import com.utub.ui.player.MiniPlayerBar
import com.utub.ui.player.PlayerScreen
import com.utub.ui.player.PlayerViewModel
import com.utub.ui.search.SearchScreen
import com.utub.ui.settings.SettingsScreen
import com.utub.ui.theme.UTubTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var playerConnection: PlayerConnection
    @Inject lateinit var stateHolder: PlayerStateHolder

    // launchMode=singleTask라 앱 실행 중 공유 수신은 onCreate가 아닌 onNewIntent로 온다.
    // 세대 카운터로 신호해 연속 공유도 매번 플레이어 화면을 연다.
    private var openPlayerTick by mutableIntStateOf(0)

    // ── PIP (F-40) ─────────────────────────────────────────────────────────
    /** PIP 창 표시 중 여부 — PlayerScreen이 영상만 렌더하도록 전달 */
    private var isInPipState by mutableStateOf(false)

    /** 영상 재생 중(+오디오 전용 아님)일 때만 홈 이동 시 PIP 진입 */
    private var pipEligible = false

    /** PIP 창의 커스텀 버튼(재생/일시정지·다음·완전종료) + 서비스 종료 신호 수신 */
    private val pipControlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // 알림바 종료 등으로 재생이 완전 종료됨 — PIP 창이면 죽은 빈 창이 남지 않게 닫는다
            if (intent.action == com.utub.playback.PlaybackService.ACTION_PLAYBACK_STOPPED) {
                if (isInPipState) finish()
                return
            }
            when (intent.getIntExtra(PipHelper.EXTRA_CONTROL, 0)) {
                PipHelper.CONTROL_PLAY_PAUSE -> {
                    playerConnection.playPause()
                    // 재생 상태에 맞춰 버튼 아이콘 갱신 (약간의 상태 반영 지연은 수용)
                    updatePipState(pipEligible, !playerConnection.isPlaying.value)
                }
                PipHelper.CONTROL_NEXT -> stateHolder.queue.next()
                PipHelper.CONTROL_PREV -> stateHolder.queue.previous()
                PipHelper.CONTROL_STOP -> {
                    // 완전 종료: 재생·서비스·알림 정리 후 PIP 창(액티비티)도 닫는다
                    playerConnection.stopService()
                    stateHolder.queue.clear()
                    stateHolder.setError(null)
                    finish()
                }
            }
        }
    }

    /** 재생 상태 변화 시 호출 — PIP 자격·자동진입 파라미터 갱신 */
    fun updatePipState(eligible: Boolean, isPlaying: Boolean) {
        pipEligible = eligible
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { setPictureInPictureParams(PipHelper.params(this, isPlaying, eligible)) }
        }
    }

    /** API 28~30: 홈 버튼 감지 폴백 (31+는 autoEnter가 처리) */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && pipEligible) {
            runCatching {
                enterPictureInPictureMode(
                    PipHelper.params(this, playerConnection.isPlaying.value, false),
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipState = isInPictureInPictureMode
        // PIP 창에는 영상만 보여야 하므로 진입 시 플레이어 화면으로 전환
        // (확대 복귀 시 시청 화면이 떠 있는 유튜브 동작과 동일)
        if (isInPictureInPictureMode) openPlayerTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ContextCompat.registerReceiver(
            this, pipControlReceiver,
            IntentFilter(PipHelper.ACTION_PIP_CONTROL).apply {
                addAction(com.utub.playback.PlaybackService.ACTION_PLAYBACK_STOPPED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // 보관함·재생목록·다운로드에서 재생 시 플레이어 화면 자동 열기 (사용자 요청)
        lifecycleScope.launch {
            var last = stateHolder.openPlayerRequest.value
            stateHolder.openPlayerRequest.collect { v ->
                if (v != last) { last = v; openPlayerTick++ }
            }
        }

        val onboardingDone = runBlocking { settingsRepository.settings.first().onboardingDone }
        val openPlayer = intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)

        setContent {
            UTubTheme {
                UTubApp(
                    startOnboarding = !onboardingDone,
                    openPlayerOnStart = openPlayer,
                    openPlayerTick = openPlayerTick,
                    isInPip = isInPipState,
                    onOnboardingDone = {
                        runBlocking { settingsRepository.setOnboardingDone() }
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(pipControlReceiver)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) openPlayerTick++
    }

    override fun onStart() {
        super.onStart()
        stateHolder.setAppInForeground(true)
    }

    override fun onStop() {
        super.onStop()
        stateHolder.setAppInForeground(false)
        // 앱이 백그라운드로 → 백그라운드 재생 정책 적용 (docs/02 4.1절)
        playerConnection.notifyAppBackground()
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
    }
}

@Composable
private fun UTubApp(
    startOnboarding: Boolean,
    openPlayerOnStart: Boolean,
    openPlayerTick: Int,
    isInPip: Boolean,
    onOnboardingDone: () -> Unit,
) {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val currentItem by playerViewModel.currentItem.collectAsState()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // PIP 자격 갱신: 영상 재생 중 + 오디오 전용 아님 → 홈 이동 시 PIP 자동 진입 (F-40)
    val isPlayingNow by playerViewModel.isPlaying.collectAsState()
    val audioOnlyNow by playerViewModel.audioOnlyMode.collectAsState()
    val mainActivity = androidx.compose.ui.platform.LocalContext.current.findActivity() as? MainActivity
    androidx.compose.runtime.LaunchedEffect(isPlayingNow, audioOnlyNow) {
        mainActivity?.updatePipState(eligible = isPlayingNow && !audioOnlyNow, isPlaying = isPlayingNow)
    }

    // 앱 실행 중 공유 수신(onNewIntent) → 플레이어 화면으로 전환.
    // launchSingleTop: 이미 플레이어가 최상단이면 중복 적재하지 않음
    LaunchedEffect(openPlayerTick) {
        if (openPlayerTick > 0) {
            navController.navigate("player") { launchSingleTop = true }
        }
    }

    // 홈 탭 웹뷰가 이동할 목적지 (홈/Shorts). 재클릭도 반영되도록 카운터를 접미
    var webTarget by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.utub.webview.YT_HOME) }
    var webNavTick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }

    // 유튜브 WebView 보존 홀더 (A안) — NavHost 밖에서 수명을 관리해 플레이어를
    // 다녀와도 보던 페이지(채널·검색결과)가 유지된다. 컴포지션 종료 시 파괴.
    val webViewHolder = androidx.compose.runtime.remember { com.utub.webview.WebViewHolder() }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { webViewHolder.destroy() }
    }

    // 영상 시청 중 화면 자동 꺼짐 방지 (결함: 재생 중 딤오프 → 소리만 재생).
    // 오디오 전용은 "화면 끄고 듣기" 목적이라 제외, PIP는 시스템 관례대로 제외.
    // 쇼츠 탭은 WebView 재생이라 네이티브 재생 상태와 무관하게 표시 중 유지.
    val shortsTabVisible = currentRoute == "home" && webTarget == com.utub.webview.YT_SHORTS
    com.utub.ui.shared.KeepScreenOnWhile(
        active = (isPlayingNow && !audioOnlyNow && !isInPip) || shortsTabVisible,
    )

    val start = when {
        startOnboarding -> "onboarding"
        openPlayerOnStart -> "player"
        else -> "home"
    }

    val tabRoutes = listOf("home", "library", "settings_tab")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { innerPadding ->
        // 상단은 상태바 인셋을 직접 적용(모든 화면 공통). 하단 인셋은 NavigationBar가 자체 흡수 → 탭 바 아래 빈 공간 제거
        @Suppress("UNUSED_EXPRESSION") innerPadding
        Column(
            modifier = Modifier
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                .fillMaxSize(),
        ) {
            NavHost(
                navController = navController,
                startDestination = start,
                modifier = Modifier.weight(1f),
            ) {
                composable("onboarding") {
                    OnboardingScreen(
                        onDone = {
                            onOnboardingDone()
                            navController.navigate(if (openPlayerOnStart) "player" else "home") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        },
                    )
                }
                // 홈 = 유튜브 웹 탐색만. 영상 클릭 시 네이티브 플레이어 화면으로 전환 (방식 B)
                composable("home") {
                    com.utub.webview.HybridScreen(
                        playerViewModel = playerViewModel,
                        onVideoSelected = { navController.navigate("player") },
                        webTarget = webTarget,
                        webNavTick = webNavTick,
                        webViewHolder = webViewHolder,
                        // 로고 탭 = 유튜브 홈 (하단 홈 탭과 동일 경로 → 탭 하이라이트 동기화)
                        onLogoClick = { webTarget = com.utub.webview.YT_HOME; webNavTick++ },
                        onSearchClick = { navController.navigate("search") { launchSingleTop = true } },
                    )
                }
                composable("library") { com.utub.ui.library.LibraryScreen() }
                composable("search") {
                    SearchScreen(
                        onBack = { navController.navigate("home") { popUpTo("home") { inclusive = false } } },
                        onVideoPlayed = { navController.navigate("player") { launchSingleTop = true } },
                    )
                }
                composable("settings_tab") { SettingsScreen(onBack = { navController.navigate("home") }) }
                composable("player") {
                    PlayerScreen(
                        onCollapse = {
                            if (!navController.popBackStack()) {
                                navController.navigate("home") { popUpTo("player") { inclusive = true } }
                            }
                        },
                        // 채널 행 탭 → 유튜브 웹 채널 페이지 (로고 탭과 같은 webTarget 경로 재사용)
                        onOpenChannel = { url ->
                            webTarget = url
                            webNavTick++
                            navController.navigate("home") { popUpTo("player") { inclusive = true } }
                        },
                        viewModel = playerViewModel,
                        isInPip = isInPip,
                    )
                }
                composable("settings") {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
            }

            // 쇼츠 탭에서 back: 항상 홈 탭으로 전환 — back은 앱 홈을 거쳐 종료된다는 원칙.
            // (유튜브가 쇼츠 진입을 히스토리 대체로 처리해 웹 뒤로가기가 없을 수 있고,
            //  웹 이력이 있어도 탭 하이라이트 desync가 생기므로 탭 전환으로 일원화)
            // NavHost 뒤에 등록해 HybridScreen의 웹 뒤로가기 핸들러보다 우선한다.
            androidx.activity.compose.BackHandler(
                enabled = currentRoute == "home" && webTarget == com.utub.webview.YT_SHORTS,
            ) {
                webTarget = com.utub.webview.YT_HOME
                webNavTick++
            }

            // 미니플레이어: 플레이어 화면·온보딩이 아닐 때 + 재생 항목 있을 때 (SCR-310)
            // 쇼츠 탭에서는 숨김 — 쇼츠 진행바(하단 수 px)와 미니플레이어가 인접해
            // 손가락 시킹 드래그를 미니플레이어가 가로채는 문제 방지 (docs/09 ⑤)
            val onShortsTab = currentRoute == "home" && webTarget == com.utub.webview.YT_SHORTS
            if (currentItem != null && currentRoute != "player" && currentRoute != "onboarding" && !onShortsTab) {
                // 영상 미니플레이어 (docs/09 ⑥) — 문제 시 MiniPlayerBar로 1줄 롤백 가능
                com.utub.ui.player.VideoMiniPlayerBar(
                    viewModel = playerViewModel,
                    onExpand = { navController.navigate("player") },
                )
            }

            // 하단 4탭: 홈(유튜브 웹) / Shorts(유튜브 웹) / 보관함(로컬) / 설정
            // 유튜브 웹 자체 탭은 hybrid.js가 숨김 → 우리 탭만 노출
            if (currentRoute in tabRoutes) {
                val onHome = currentRoute == "home" && webTarget == com.utub.webview.YT_HOME
                val onShorts = currentRoute == "home" && webTarget == com.utub.webview.YT_SHORTS
                // 컴팩트 탭 바 (기본 NavigationBar 80dp → 52dp, 아이콘+라벨 유지)
                com.utub.ui.shared.CompactNavBar(
                    items = listOf(
                        com.utub.ui.shared.CompactNavItem(Icons.Default.Home, "홈", onHome) {
                            webTarget = com.utub.webview.YT_HOME; webNavTick++
                            navController.navigate("home") { popUpTo("home"); launchSingleTop = true }
                        },
                        com.utub.ui.shared.CompactNavItem(Icons.Default.PlayCircleOutline, "Shorts", onShorts) {
                            webTarget = com.utub.webview.YT_SHORTS; webNavTick++
                            navController.navigate("home") { popUpTo("home"); launchSingleTop = true }
                        },
                        com.utub.ui.shared.CompactNavItem(Icons.Default.VideoLibrary, "보관함", currentRoute == "library") {
                            navController.navigate("library") { popUpTo("home"); launchSingleTop = true }
                        },
                        com.utub.ui.shared.CompactNavItem(Icons.Default.Settings, "설정", currentRoute == "settings_tab") {
                            navController.navigate("settings_tab") { popUpTo("home"); launchSingleTop = true }
                        },
                    ),
                )
            }
        }
    }
}
