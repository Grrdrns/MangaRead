package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.Chapter
import com.example.ui.MangaViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: MangaViewModel,
    chapterId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeChapter by viewModel.activeChapter.collectAsState()
    val chapterPages by viewModel.chapterPages.collectAsState()
    val loading by viewModel.pagesLoading.collectAsState()
    val currentPageIndex by viewModel.readerCurrentPage.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var readVerticalMode by remember { mutableStateOf(false) } // False: Horizontal Pager, True: Continuous Webtoon Vertical Scroll
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Trigger loading chapter pages
    LaunchedEffect(chapterId) {
        // If chapter is not already matching active loaded chapter
        if (activeChapter?.id != chapterId) {
            val dummyChapter = Chapter(
                id = chapterId,
                mangaId = "",
                title = "Loading...",
                chapterNumber = ""
            )
            viewModel.loadChapter(dummyChapter)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                // Clicking anywhere on image toggles immersive hud control overlay!
                detectTapGestures(
                    onTap = { showControls = !showControls }
                )
            }
    ) {
        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Buffering high quality feeds...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
        } else if (chapterPages == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "Error icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Unable to fetch chapter pages",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "The page index is restricted or offline.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { activeChapter?.let { viewModel.loadChapter(it) } },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Reload Chapter")
                    }
                }
            }
        } else {
            val pages = chapterPages!!
            val urls = pages.getPageUrls()

            if (urls.isNotEmpty()) {
                if (readVerticalMode) {
                    // Vertical continuous list layout (Webtoon format)
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPageIndex)
                    
                    // Track visible screen elements to update SQLite history/progress in real-time
                    LaunchedEffect(listState.firstVisibleItemIndex) {
                        viewModel.updateProgress(listState.firstVisibleItemIndex)
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        itemsIndexed(urls) { index, url ->
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(url)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Page ${index + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .testTag("manga_page_$index")
                            )
                        }
                    }
                } else {
                    // Horizontal layout mapping
                    val pagerState = rememberPagerState(
                        initialPage = currentPageIndex.coerceIn(0, urls.size - 1),
                        pageCount = { urls.size }
                    )

                    LaunchedEffect(pagerState.currentPage) {
                        viewModel.updateProgress(pagerState.currentPage)
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 4.dp
                    ) { page ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(urls[page])
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Page ${page + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("manga_page_$page")
                            )
                        }
                    }

                    // Simple horizontal overlay page progress pill
                    if (!showControls) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${urls.size}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // If chapter has no pages on MangaDex (hosted externally or restricted)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        val isExternal = activeChapter?.externalUrl?.isNotEmpty() == true
                        
                        Icon(
                            imageVector = if (isExternal) Icons.Default.Launch else Icons.Default.MenuBook,
                            contentDescription = "External chapter icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = if (isExternal) "Externally Hosted Chapter" else "No Pages Available",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val msg = if (isExternal) {
                            "This chapter is officially published on an external platform (like MangaPlus or Viz Media) and does not host direct image files on MangaDex."
                        } else {
                            "This chapter does not have direct page streams uploaded on MangaDex, or the data has been restricted."
                        }
                        
                        Text(
                            text = msg,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        if (isExternal) {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(activeChapter!!.externalUrl))
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Fallback if launcher fails
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .testTag("open_external_button")
                            ) {
                                Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Read on Publisher Website")
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW, 
                                    Uri.parse("https://mangadex.org/chapter/${activeChapter?.id ?: chapterId}")
                                )
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {}
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .testTag("open_mangadex_button")
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View on MangaDex Website")
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("no_pages_back_button")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Go Back to Details", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Controls overlay (HUD) with fade animations
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                TopAppBarHud(
                    title = activeChapter?.title ?: "Manga Player",
                    chapterNum = activeChapter?.chapterNumber ?: "",
                    readVertical = readVerticalMode,
                    onBack = onBack,
                    onToggleMode = { readVerticalMode = !readVerticalMode }
                )
            }

            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                BottomBarHud(
                    currentPage = currentPageIndex,
                    totalPages = urls.size,
                    onPageChanged = { index ->
                        viewModel.updateProgress(index)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarHud(
    title: String,
    chapterNum: String,
    readVertical: Boolean,
    onBack: () -> Unit,
    onToggleMode: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black.copy(alpha = 0.85f),
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        ),
        title = {
            Column {
                Text(
                    text = if (chapterNum.isNotEmpty()) "Chapter $chapterNum" else "Manga Read Mode",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("reader_back_button")) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close reader")
            }
        },
        actions = {
            // Style switcher button description info
            IconButton(
                onClick = onToggleMode,
                modifier = Modifier.testTag("reading_mode_switcher")
            ) {
                Icon(
                    imageVector = if (readVertical) Icons.Default.SwapHoriz else Icons.Default.SwapVert,
                    contentDescription = if (readVertical) "Switch to Horizontal Pager" else "Switch to Webtoon Scroll"
                )
            }
        }
    )
}

@Composable
fun BottomBarHud(
    currentPage: Int,
    totalPages: Int,
    onPageChanged: (Int) -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.85f),
        contentColor = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Page ${currentPage + 1}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Text(
                    text = "$totalPages pages",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (totalPages > 1) {
                Slider(
                    value = currentPage.toFloat(),
                    onValueChange = { onPageChanged(it.toInt()) },
                    valueRange = 0f..(totalPages - 1).toFloat(),
                    steps = totalPages - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reader_page_slider")
                )
            }
        }
    }
}
