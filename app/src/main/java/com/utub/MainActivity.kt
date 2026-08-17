package com.utub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
                    onOnboardingDone = {
                        runBlocking { settingsRepository.setOnboardingDone() }
                    },
                )
            }
        }
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
    onOnboardingDone: () -> Unit,
) {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val currentItem by playerViewModel.currentItem.collectAsState()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val start = when {
        startOnboarding -> "onboarding"
        openPlayerOnStart -> "player"
        else -> "home"
    }

    val tabRoutes = listOf("home", "subscriptions", "library")

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
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
                composable("home") {
                    HomeScreen(
                        onSearchClick = { navController.navigate("search") },
                        onSettingsClick = { navController.navigate("settings") },
                    )
                }
                composable("subscriptions") { SubscriptionsPlaceholder() }
                composable("library") { com.utub.ui.library.LibraryScreen() }
                composable("search") {
                    SearchScreen(
                        onBack = { navController.popBackStack() },
                        onVideoPlayed = { navController.navigate("player") },
                    )
                }
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

            // 미니플레이어: 플레이어 화면이 아닐 때 + 재생 항목 있을 때 (SCR-310)
            if (currentItem != null && currentRoute != "player" && currentRoute != "onboarding") {
                MiniPlayerBar(
                    viewModel = playerViewModel,
                    onExpand = { navController.navigate("player") },
                )
            }

            // 하단 3탭 (유튜브 앱과 동일 구성 — docs/02 1절)
            if (currentRoute in tabRoutes) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == "home",
                        onClick = { navController.navigate("home") { popUpTo("home"); launchSingleTop = true } },
                        icon = { Icon(Icons.Default.Home, "홈") },
                        label = { Text("홈") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == "subscriptions",
                        onClick = { navController.navigate("subscriptions") { popUpTo("home"); launchSingleTop = true } },
                        icon = { Icon(Icons.Default.Subscriptions, "구독") },
                        label = { Text("구독") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == "library",
                        onClick = { navController.navigate("library") { popUpTo("home"); launchSingleTop = true } },
                        icon = { Icon(Icons.Default.VideoLibrary, "보관함") },
                        label = { Text("보관함") },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionsPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "구독 기능은 2차 업데이트에서 제공돼요\n(채널을 앱에 등록하면 새 영상을 모아 보여줍니다)",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
