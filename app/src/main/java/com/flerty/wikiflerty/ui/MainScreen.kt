package com.flerty.wikiflerty.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.reader.ReaderActivity
import kotlin.math.roundToInt

/** The main sections of the app, opened from the side panel. */
enum class Section(val title: String, val icon: ImageVector) {
    Library("Библиотека", AppIcons.Book),
    Notes("Заметки", AppIcons.Note),
    Dictionary("Словарь", AppIcons.TextSize),
    Stats("Статистика", AppIcons.Chart),
    Settings("Настройки", AppIcons.Options),
    About("О приложении", AppIcons.Info),
}

@Composable
fun MainScreen(libraryViewModel: LibraryViewModel) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var section by rememberSaveable { mutableStateOf(Section.Library) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    val busy by libraryViewModel.busy.collectAsStateWithLifecycle()
    val libraryBooks by libraryViewModel.books.collectAsStateWithLifecycle()
    val libraryControls = remember { LibraryControls() }
    val app = context.applicationContext as com.flerty.wikiflerty.App
    val notesCount by remember { app.notes.notes.map { it.size } }.collectAsStateWithLifecycle(0)
    val wordsCount by remember { app.dictionary.entries.map { it.size } }.collectAsStateWithLifecycle(0)
    val feedback = LocalFeedback.current
    val view = LocalView.current

    val pickBook = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) libraryViewModel.import(uri)
    }

    LaunchedEffect(Unit) {
        libraryViewModel.openReader.collect {
            context.startActivity(Intent(context, ReaderActivity::class.java))
        }
    }
    LaunchedEffect(Unit) {
        libraryViewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    BackHandler(enabled = drawerOpen || section != Section.Library) {
        if (drawerOpen) drawerOpen = false else section = Section.Library
    }

    SideDrawer(
        open = drawerOpen,
        onOpenChange = { drawerOpen = it },
        drawer = {
            DrawerLogo()
            Spacer(Modifier.height(10.dp))
            Section.entries.forEach { item ->
                if (item == Section.Settings) {
                    Box(Modifier.padding(horizontal = 22.dp, vertical = 8.dp).fillMaxWidth().height(1.dp).background(AppColors.Line))
                }
                val count = when (item) {
                    Section.Library -> libraryBooks.size
                    Section.Notes -> notesCount
                    Section.Dictionary -> wordsCount
                    else -> 0
                }
                DrawerItem(item, selected = item == section, count = count) {
                    section = item
                    drawerOpen = false
                }
            }
            Spacer(Modifier.weight(1f))
            DrawerTodayCard()
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            AppBackground()
            val hasBackground = hasAppBackground
            Scaffold(
                containerColor = if (hasBackground) Color.Transparent else AppColors.Background,
                topBar = {
                    Column {
                        TopAppBar(
                            title = { Text(section.title) },
                            actions = {
                                if (section == Section.Library && libraryBooks.isNotEmpty()) LibraryTopActions(libraryControls)
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                            navigationIcon = {
                                IconButton(onClick = {
                                    feedback?.play(FeedbackSound.Open, view)
                                    drawerOpen = true
                                }) {
                                    Icon(Icons.Filled.Menu, contentDescription = "Меню")
                                }
                            },
                        )
                        if (busy != null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                },
                floatingActionButton = {
                    if (section == Section.Library) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                feedback?.play(FeedbackSound.Open, view)
                                pickBook.launch(arrayOf("application/epub+zip", "application/octet-stream"))
                            },
                            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            text = { Text("Книга") },
                        )
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                if (section == Section.Library) {
                    // The library uses the whole width: tablets just get more books per row.
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        onAddBook = { pickBook.launch(arrayOf("application/epub+zip", "application/octet-stream")) },
                        controls = libraryControls,
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                } else {
                    // Other screens keep a comfortable reading width and stay centered on tablets.
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                        val modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()
                        when (section) {
                            Section.Notes -> NotesScreen(libraryViewModel, modifier)
                            Section.Dictionary -> DictionaryScreen(libraryViewModel, modifier)
                            Section.Stats -> StatsScreen(modifier)
                            Section.Settings -> SettingsScreen(modifier)
                            Section.About -> AboutScreen(modifier)
                            Section.Library -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(item: Section, selected: Boolean, count: Int, onClick: () -> Unit) {
    val shape = appCorner(18)
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .fillMaxWidth()
            .height(54.dp)
            .liveClick(shape, sound = FeedbackSound.Tap, pressedScale = 0.97f, onClick = onClick)
            .then(
                if (selected) {
                    Modifier.background(AppColors.Accent.copy(alpha = 0.16f)).border(1.dp, AppColors.Accent.copy(alpha = 0.24f), shape)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Each section's icon sits on its own small tile.
        val tile = appCorner(11)
        Box(
            Modifier
                .size(34.dp)
                .clip(tile)
                .background(if (selected) AppColors.Accent else Color.White.copy(alpha = 0.07f))
                .border(1.dp, if (selected) Color.Transparent else Color.White.copy(alpha = 0.08f), tile),
            contentAlignment = Alignment.Center,
        ) { Icon(item.icon, null, tint = if (selected) AppColors.OnAccent else AppColors.Accent, modifier = Modifier.size(19.dp)) }
        Text(
            item.title,
            color = AppColors.Text,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) Text(count.toString(), color = AppColors.Faint, fontSize = 12.sp)
    }
}

/** Bottom of the side panel: today's reading, the goal ring and the streak. */
@Composable
private fun DrawerTodayCard() {
    val app = LocalContext.current.applicationContext as com.flerty.wikiflerty.App
    val days by app.stats.days.collectAsStateWithLifecycle()
    val prefs by app.libraryPrefs.prefs.collectAsStateWithLifecycle()
    val perDay = remember(days) { StatsCalc.perDay(days) }
    val today = java.time.LocalDate.now()
    val todayMs = perDay[today]?.ms ?: 0L
    val goal = prefs.dailyGoalMinutes
    val (streak, _) = StatsCalc.streaks(perDay, today)
    val shape = appCorner(18)
    Row(
        Modifier
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (goal > 0) {
            GoalRing(todayMs, goal, 46.dp)
        } else {
            Box(Modifier.size(46.dp).clip(CircleShape).background(AppColors.Accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Clock, null, tint = AppColors.Accent, modifier = Modifier.size(22.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (goal > 0) "Сегодня ${todayMs / 60_000} из $goal мин" else "Сегодня ${StatsCalc.duration(todayMs)}",
                color = AppColors.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                if (streak > 0) "Серия: ${StatsCalc.days(streak)}" else "Почитай сегодня, чтобы начать серию",
                color = AppColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

/**
 * A light side panel. It slides in quickly and lets go of the screen the moment it starts closing,
 * so a tap right after closing always reaches the page underneath.
 */
@Composable
private fun SideDrawer(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    drawer: @Composable ColumnScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = minOf(maxWidth * 0.84f, 320.dp)
        val widthPx = with(density) { width.toPx() }
        val scope = rememberCoroutineScope()
        val anim = remember { Animatable(if (open) 1f else 0f) }
        LaunchedEffect(open) {
            anim.animateTo(if (open) 1f else 0f, tween(durationMillis = 230, easing = FastOutSlowInEasing))
        }
        val visible = anim.value

        content()

        // Swipe from the left edge to open.
        if (!open) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(18.dp)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta -> if (delta > 12f) onOpenChange(true) },
                    ),
            )
        }

        if (visible > 0f) {
            // Dark scrim; only catches taps while the panel is (being) open.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f * visible))
                    .then(
                        if (open) Modifier.pointerInput(Unit) { detectTapGestures { onOpenChange(false) } } else Modifier,
                    ),
            )
            Column(
                Modifier
                    .offset { IntOffset(((visible - 1f) * widthPx).roundToInt(), 0) }
                    .width(width)
                    .fillMaxHeight()
                    .shadow(24.dp)
                    .drawBehind {
                        // The panel's own background: a soft gradient and a glow in the look's colors.
                        drawRect(Brush.verticalGradient(listOf(AppColors.PanelTop, AppColors.Panel, AppColors.Background)))
                        val glow = AppStyle.base.ambience?.spots?.firstOrNull()?.color?.let { Color(it) } ?: AppColors.Accent
                        drawCircle(
                            Brush.radialGradient(
                                listOf(glow.copy(alpha = 0.32f), glow.copy(alpha = 0f)),
                                center = Offset(size.width * 0.15f, size.height * 0.05f),
                                radius = size.width * 1.1f,
                            ),
                            radius = size.width * 1.1f,
                            center = Offset(size.width * 0.15f, size.height * 0.05f),
                        )
                    }
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .then(
                        if (open) {
                            Modifier.draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta ->
                                    scope.launch { anim.snapTo((anim.value + delta / widthPx).coerceIn(0f, 1f)) }
                                },
                                onDragStopped = {
                                    if (anim.value < 0.75f) {
                                        onOpenChange(false)
                                    } else {
                                        scope.launch { anim.animateTo(1f, tween(180)) }
                                    }
                                },
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) { drawer() }
        }
    }
}

/** "WikiFlerty" in a framed flag: Wiki in white, Flerty in the accent color, version below. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Composable
private fun DrawerLogo() {
    val context = LocalContext.current
    val logoFont = remember {
        androidx.compose.ui.text.font.FontFamily(
            androidx.compose.ui.text.font.Font(
                path = "fonts/Nunito.ttf",
                assetManager = context.assets,
                weight = FontWeight.ExtraBold,
                variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(
                    androidx.compose.ui.text.font.FontVariation.weight(850),
                ),
            ),
        )
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val shape = appCorner(20)
    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 20.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(AppColors.Accent.copy(alpha = 0.16f).compositeOver(AppColors.Field), AppColors.Field)))
            .border(1.dp, AppColors.Accent.copy(alpha = 0.25f), shape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The app icon: the same open book as on the home screen.
            Box(
                Modifier
                    .size(48.dp)
                    .clip(appCorner(14))
                    .background(androidx.compose.ui.res.colorResource(com.flerty.wikiflerty.R.color.icon_background)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(com.flerty.wikiflerty.R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.requiredSize(60.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.White)) { append("Wiki") }
                        withStyle(SpanStyle(color = AppColors.Accent)) { append("Flerty") }
                    },
                    fontFamily = logoFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    maxLines = 1,
                )
                if (version.isNotBlank()) {
                    Text("Версия $version", color = AppColors.Faint, fontSize = 12.sp, fontWeight = FontWeight.Light)
                }
            }
        }
    }
}
