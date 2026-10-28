package com.flerty.wikiflerty.ui

import androidx.compose.foundation.layout.statusBarsPadding

import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.ui.graphics.RectangleShape

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
import androidx.compose.animation.togetherWith
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

    val bottomNav = AppStyle.bottomNav
    val statsDays by app.stats.days.collectAsStateWithLifecycle()
    val libraryPrefsState by app.libraryPrefs.prefs.collectAsStateWithLifecycle()
    val todayMinutes = (statsDays[java.time.LocalDate.now().toString()]?.values?.sumOf { it.ms } ?: 0L) / 60_000
    val greeting = if (libraryPrefsState.dailyGoalMinutes > 0) {
        "Привет! Сегодня $todayMinutes из ${libraryPrefsState.dailyGoalMinutes} мин"
    } else {
        "Привет! Сегодня $todayMinutes мин"
    }
    var addMenu by remember { mutableStateOf(false) }
    SideDrawer(
        open = drawerOpen && !bottomNav,
        enabled = !bottomNav,
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
                    Box {
                        MainHeader(
                            section = section,
                            greeting = if (section == Section.Library) greeting else null,
                            onBack = when (section) {
                                Section.Settings -> { { section = Section.Library } }
                                Section.About -> { { section = Section.Settings } }
                                else -> null
                            },
                        ) {
                            if (section == Section.Library) {
                                HeaderButton(if (libraryControls.searchOpen) AppIcons.Close else AppIcons.Search, "Поиск") {
                                    libraryControls.searchOpen = !libraryControls.searchOpen
                                }
                                HeaderButton(AppIcons.Options, "Настройки") { section = Section.Settings }
                            }
                        }
                        if (busy != null) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                                color = AppColors.Accent,
                                trackColor = AppColors.Line,
                            )
                        }
                    }
                },
                bottomBar = {
                    if (bottomNav) {
                        BottomNav(section, onSelect = { section = it }, onAdd = { addMenu = true })
                    }
                },
                floatingActionButton = {
                    if (section == Section.Library && !bottomNav) {
                        AddBookFab(
                            onFile = { pickBook.launch(arrayOf("*/*")) },
                            onScan = { libraryControls.scanFolder = true },
                        )
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                androidx.compose.animation.AnimatedContent(
                    targetState = section,
                    transitionSpec = {
                        androidx.compose.animation.fadeIn(Motion.fade(220))
                            .togetherWith(androidx.compose.animation.fadeOut(Motion.fade(120)))
                            .using(androidx.compose.animation.SizeTransform(clip = false) { _, _ -> androidx.compose.animation.core.snap() })
                    },
                    label = "section",
                ) { current ->
                if (current == Section.Library) {
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        onAddBook = { pickBook.launch(arrayOf("*/*")) },
                        controls = libraryControls,
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                } else {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                        val modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()
                        when (current) {
                            Section.Notes -> NotesScreen(libraryViewModel, modifier)
                            Section.Dictionary -> DictionaryScreen(libraryViewModel, modifier)
                            Section.Stats -> StatsScreen(modifier)
                            Section.Settings -> SettingsScreen(modifier, onAbout = if (bottomNav) { { section = Section.About } } else null)
                            Section.About -> AboutScreen(modifier)
                            Section.Library -> Unit
                        }
                    }
                }
                }
            }
            if (addMenu) {
                AddBookSheet(
                    onDismiss = { addMenu = false },
                    onFile = { pickBook.launch(arrayOf("*/*")) },
                    onScan = {
                        section = Section.Library
                        libraryControls.scanFolder = true
                    },
                )
            }
            StartupPrompts()
            AchievementToast()
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
        val tile = appCorner(11)
        Box(
            Modifier
                .size(34.dp)
                .clip(tile)
                .background(if (selected) AppColors.Accent else AppColors.Shade.copy(alpha = 0.07f))
                .border(1.dp, if (selected) Color.Transparent else AppColors.Shade.copy(alpha = 0.08f), tile),
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
            .background(AppColors.Shade.copy(alpha = 0.06f))
            .border(1.dp, AppColors.Shade.copy(alpha = 0.09f), shape)
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (goal > 0) "Сегодня ${todayMs / 60_000} из $goal мин" else "Сегодня ${StatsCalc.duration(todayMs)}",
                color = AppColors.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                if (streak > 0) "Серия: ${StatsCalc.days(streak)}" else "Серия ещё не начата",
                color = AppColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SideDrawer(
    open: Boolean,
    enabled: Boolean = true,
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
            anim.animateTo(if (open) 1f else 0f, Motion.tween(durationMillis = 230, easing = FastOutSlowInEasing))
        }
        val visible = anim.value

        content()

        if (!open && enabled) {
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
                                        scope.launch { anim.animateTo(1f, Motion.tween(180)) }
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
                        withStyle(SpanStyle(color = AppColors.Text)) { append("Wiki") }
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

@Composable
private fun BottomNav(section: Section, onSelect: (Section) -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(12.dp, RectangleShape, ambientColor = AppColors.Shade.copy(alpha = 0.25f), spotColor = AppColors.Shade.copy(alpha = 0.25f))
                .background(AppColors.Panel)
                .navigationBarsPadding()
                .height(66.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomItem(Section.Library, "Полка", section, onSelect, Modifier.weight(1f))
            BottomItem(Section.Notes, "Заметки", section, onSelect, Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
            BottomItem(Section.Dictionary, "Словарь", section, onSelect, Modifier.weight(1f))
            BottomItem(Section.Stats, "Итоги", section, onSelect, Modifier.weight(1f))
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-22).dp)
                .size(62.dp)
                .clip(CircleShape)
                .background(AppColors.Background)
                .padding(4.dp)
                .liveClick(CircleShape, sound = FeedbackSound.Open, pressedScale = 0.88f, onClick = onAdd)
                .background(AppColors.Accent),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.Plus, "Добавить книгу", tint = AppColors.OnAccent, modifier = Modifier.size(28.dp)) }
    }
}

@Composable
private fun BottomItem(item: Section, label: String, current: Section, onSelect: (Section) -> Unit, modifier: Modifier) {
    val on = item == current
    val color = if (on) AppColors.Accent else AppColors.Faint
    Column(
        modifier
            .fillMaxHeight()
            .liveClick(RectangleShape, sound = FeedbackSound.Tap, pressedScale = 0.92f) { onSelect(item) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Icon(item.icon, null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun AddBookSheet(onDismiss: () -> Unit, onFile: () -> Unit, onScan: () -> Unit) {
    AppSheet(onDismiss = onDismiss, title = "Добавить книгу") {
        val close = sheetClose(onDismiss)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AddChoice(AppIcons.Book, "Файл", Modifier.weight(1f)) {
                close()
                onFile()
            }
            AddChoice(AppIcons.FolderSearch, "Найти на телефоне", Modifier.weight(1f)) {
                close()
                onScan()
            }
        }
    }
}

@Composable
private fun AddChoice(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = appCorner(20)
    Column(
        modifier
            .height(112.dp)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.95f, onClick = onClick)
            .fieldSurface(shape)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(AppColors.AccentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(24.dp))
        }
        Text(
            title,
            color = AppColors.Text,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp, stepSize = 0.5.sp),
        )
    }
}

private fun sectionTitle(section: Section): String = when (section) {
    Section.Library -> "Моя полка"
    Section.Stats -> "Итоги"
    else -> section.title
}

@Composable
private fun MainHeader(section: Section, greeting: String?, onBack: (() -> Unit)?, actions: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(74.dp)
            .padding(start = if (onBack != null) 12.dp else 20.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) HeaderButton(AppIcons.Back, "Назад", onBack)
        Column(Modifier.weight(1f)) {
            if (greeting != null) {
                Text(
                    greeting,
                    color = AppColors.Muted,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 13.sp, stepSize = 0.5.sp),
                )
            }
            Text(
                sectionTitle(section),
                color = AppColors.Text,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 27.sp, stepSize = 0.5.sp),
            )
        }
        actions()
    }
}

@Composable
private fun HeaderButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .liveClick(CircleShape, sound = FeedbackSound.Open, pressedScale = 0.88f, onClick = onClick)
            .fieldSurface(CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = AppColors.Text, modifier = Modifier.size(20.dp)) }
}
