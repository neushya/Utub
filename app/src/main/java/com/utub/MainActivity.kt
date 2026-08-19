package com.utub

import android.content.Intent
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.utub.data.prefs.SettingsRepository
import com.utub.playback.PlayerConnection
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

    // launchMode=singleTask라 앱 실행 중 공유 수신은 onCreate가 아닌 onNewIntent로 온다.
    // 세대 카운터로 신호해 연속 공유도 매번 플레이어 화면을 연다.
    private var openPlayerTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val onboardingDone = runBlocking { settingsRepository.settings.first().onboardingDone }
        val openPlayer = intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)

        setContent {
            UTubTheme {
                UTubApp(
                    startOnboarding = !onboardingDone,
                    openPlayerOnStart = openPlayer,
                    openPlayerTick = openPlayerTick,
                    onOnboardingDone = {
                        runBlocking { settingsRepository.setOnboardingDone() }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) openPlayerTick++
    }

    override fun onStop() {
        super.onStop()
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
    onOnboardingDone: () -> Unit,
) {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val currentItem by playerViewModel.currentItem.collectAsState()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

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
                        // 로고 탭 = 유튜브 홈 (하단 홈 탭과 동일 경로 → 탭 하이라이트 동기화)
                        onLogoClick = { webTarget = com.utub.webview.YT_HOME; webNavTick++ },
                    )
                }
                composable("library") { com.utub.ui.library.LibraryScreen() }
                composable("settings_tab") { SettingsScreen(onBack = { navController.navigate("home") }) }
                composable("player") {
                    PlayerScreen(
                        onCollapse = {
                            if (!navController.popBackStack()) {
                                navController.navigate("home") { popUpTo("player") { inclusive = true } }
                            }
                        },
                        viewModel = playerViewModel,
                    )
                }
                composable("settings") {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
            }

            // 미니플레이어: 플레이어 화면·온보딩이 아닐 때 + 재생 항목 있을 때 (SCR-310)
            // 쇼츠 탭에서는 숨김 — 쇼츠 진행바(하단 수 px)와 미니플레이어가 인접해
            // 손가락 시킹 드래그를 미니플레이어가 가로채는 문제 방지 (docs/09 ⑤)
            val onShortsTab = currentRoute == "home" && webTarget == com.utub.webview.YT_SHORTS
            if (currentItem != null && currentRoute != "player" && currentRoute != "onboarding" && !onShortsTab) {
                MiniPlayerBar(
                    viewModel = playerViewModel,
                    onExpand = { navController.navigate("player") },
                )
            }

            // 하단 4탭: 홈(유튜브 웹) / Shorts(유튜브 웹) / 보관함(로컬) / 설정
            // 유튜브 웹 자체 탭은 hybrid.js가 숨김 → 우리 탭만 노출
            if (currentRoute in tabRoutes) {
                val onHome = currentRoute == "home" && webTarget == com.utub.webview.YT_HOME
                val onShorts = currentRoute == "home" && webTarget == com.utub.webview.YT_SHORTS
                NavigationBar {
                    NavigationBarItem(
                        selected = onHome,
                        onClick = {
                            webTarget = com.utub.webview.YT_HOME; webNavTick++
                            navController.navigate("home") { popUpTo("home"); launchSingleTop = true }
                        },
                        icon = { Icon(Icons.Default.Home, "홈") },
                        label = { Text("홈") },
                    )
                    NavigationBarItem(
                        selected = onShorts,
                        onClick = {
                            webTarget = com.utub.webview.YT_SHORTS; webNavTick++
                            navController.navigate("home") { popUpTo("home"); launchSingleTop = true }
                        },
                        icon = { Icon(Icons.Default.PlayCircleOutline, "Shorts") },
                        label = { Text("Shorts") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == "library",
                        onClick = { navController.navigate("library") { popUpTo("home"); launchSingleTop = true } },
                        icon = { Icon(Icons.Default.VideoLibrary, "보관함") },
                        label = { Text("보관함") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == "settings_tab",
                        onClick = { navController.navigate("settings_tab") { popUpTo("home"); launchSingleTop = true } },
                        icon = { Icon(Icons.Default.Settings, "설정") },
                        label = { Text("설정") },
                    )
                }
            }
        }
    }
}
