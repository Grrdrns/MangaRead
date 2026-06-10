package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.ui.MangaViewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.MangaDetailScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.theme.MyApplicationTheme

sealed class Screen {
    object Dashboard : Screen()
    data class MangaDetail(val mangaId: String) : Screen()
    data class Reader(val chapterId: String, val mangaId: String) : Screen()
}

class MainActivity : ComponentActivity() {
    private val viewModel: MangaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

                // Enable edge-to-edge system navigation & back button routing
                BackHandler(enabled = currentScreen != Screen.Dashboard) {
                    when (val screen = currentScreen) {
                        is Screen.Reader -> currentScreen = Screen.MangaDetail(screen.mangaId)
                        is Screen.MangaDetail -> currentScreen = Screen.Dashboard
                        else -> { /* No-op */ }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "screen_navigation_transition"
                    ) { screen ->
                        when (screen) {
                            is Screen.Dashboard -> {
                                DashboardScreen(
                                    viewModel = viewModel,
                                    onMangaSelected = { mangaId ->
                                        currentScreen = Screen.MangaDetail(mangaId)
                                    },
                                    onChapterSelected = { chapterId, mangaId ->
                                        currentScreen = Screen.Reader(chapterId, mangaId)
                                    }
                                )
                            }
                            is Screen.MangaDetail -> {
                                MangaDetailScreen(
                                    viewModel = viewModel,
                                    mangaId = screen.mangaId,
                                    onBack = { currentScreen = Screen.Dashboard },
                                    onChapterSelected = { chapter ->
                                        currentScreen = Screen.Reader(chapter.id, screen.mangaId)
                                    }
                                )
                            }
                            is Screen.Reader -> {
                                ReaderScreen(
                                    viewModel = viewModel,
                                    chapterId = screen.chapterId,
                                    onBack = { currentScreen = Screen.MangaDetail(screen.mangaId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
