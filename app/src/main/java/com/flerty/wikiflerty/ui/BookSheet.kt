package com.flerty.wikiflerty.ui

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.Book
import com.flerty.wikiflerty.data.LibraryPrefs
import com.flerty.wikiflerty.data.LibrarySort
import com.flerty.wikiflerty.data.LibraryView
import com.flerty.wikiflerty.data.ReadStatus
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch
import androidx.compose.animation.togetherWith

private val Danger get() = AppColors.Bad

val LocalSheetClose = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }
val LocalSheetCloseAll = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
fun sheetClose(fallback: () -> Unit): () -> Unit = LocalSheetClose.current ?: fallback

@Composable
fun KeepHostBars() {
    val view = androidx.compose.ui.platform.LocalView.current
    val context = LocalContext.current
    DisposableEffect(view) {
        var host: android.content.Context? = context
        while (host is android.content.ContextWrapper && host !is android.app.Activity) host = host.baseContext
        val hostWindow = (host as? android.app.Activity)?.window
        var parent: android.view.ViewParent? = view.parent
        var window: android.view.Window? = null
        while (parent != null) {
            if (parent is androidx.compose.ui.window.DialogWindowProvider) {
                window = parent.window
                break
            }
            parent = parent.parent
        }
        if (window != null && hostWindow != null) {
            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(hostWindow.decorView)
            if (insets != null && !insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars())) {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        onDispose { }
    }
}

@Composable
fun sheetHeight(): androidx.compose.ui.unit.Dp = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.57f).dp

@Composable
fun AppSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    aboveHeight: androidx.compose.ui.unit.Dp = 0.dp,
    above: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    aura: List<Color>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalSheetPages.current
    if (host != null) {
        SheetPageEntry(host, onDismiss, title, content)
        return
    }
    val auraShown = remember { arrayOf<List<Color>?>(null) }
    if (aura != null) auraShown[0] = aura
    val auraGlow by androidx.compose.animation.core.animateFloatAsState(if (aura != null) 1f else 0f, Motion.tween(620), label = "sheet-aura")
    val corner = (26 * AppStyle.current.corners).dp
    val sheetShape = RoundedCornerShape(topStart = corner, topEnd = corner)
    val pages = remember { SheetPages() }
    val dismissAll: () -> Unit = {
        pages.stack.toList().asReversed().forEach { if (!it.leaving) it.onDismiss() }
        onDismiss()
    }
    SheetWindow(dismissAll, onBack = { pages.back() }) { control ->
        val close = control.close
        val viewport = remember { RevealViewport() }
        androidx.compose.runtime.CompositionLocalProvider(LocalSheetClose provides close, LocalSheetCloseAll provides close, LocalRevealViewport provides viewport, LocalSheetPages provides pages) {
            val body: @Composable (Modifier) -> Unit = { mod ->
                Box(
                    mod.height(sheetHeight()).drawBehind {
                        val colors = auraShown[0]
                        if (colors != null && auraGlow > 0.01f) {
                            val a = auraGlow * (if (AppStyle.light) 0.24f else 0.42f)
                            drawRect(
                                Brush.radialGradient(listOf(colors[0].copy(alpha = a), Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width * 0.16f, 72.dp.toPx()), radius = size.width * 0.9f),
                            )
                            drawRect(
                                Brush.radialGradient(listOf(colors[1].copy(alpha = a * 0.62f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width, 0f), radius = size.width * 0.72f),
                            )
                        }
                    },
                ) {
                    Column(Modifier.fillMaxSize().pageDepth(pages, null)) {
                        if (title != null) Box(Modifier.padding(horizontal = 16.dp)) { SheetHeader(title, close) }
                        Column(
                            Modifier.weight(1f).revealViewport(viewport).padding(horizontal = 16.dp).navigationBarsPadding().revealScroll(viewport).padding(top = if (title == null) 16.dp else 14.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            content()
                        }
                    }
                    pages.stack.forEach { page ->
                        androidx.compose.runtime.key(page) { SheetPageLayer(pages, page, close) }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(sheetHeight() + aboveHeight)) {
                if (above != null) Box(Modifier.fillMaxWidth().height(aboveHeight + corner).padding(horizontal = 16.dp), content = above)
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(sheetHeight())
                        .clip(sheetShape)
                        .background(AppColors.Panel),
                ) { body(Modifier) }
            }
        }
    }
}

class SheetPage {
    var title by androidx.compose.runtime.mutableStateOf<String?>(null)
    var content by androidx.compose.runtime.mutableStateOf<(@Composable ColumnScope.() -> Unit)?>(null)
    var onDismiss: () -> Unit = {}
    var leaving by androidx.compose.runtime.mutableStateOf(false)
    val enter = androidx.compose.animation.core.Animatable(0f)
}

class SheetPages {
    val stack = androidx.compose.runtime.mutableStateListOf<SheetPage>()

    fun back(): Boolean {
        val top = stack.lastOrNull { !it.leaving } ?: return false
        top.onDismiss()
        return true
    }

    fun above(page: SheetPage?): SheetPage? {
        val index = if (page == null) -1 else stack.indexOf(page)
        return stack.getOrNull(index + 1)
    }
}

val LocalSheetPages = androidx.compose.runtime.staticCompositionLocalOf<SheetPages?> { null }

private val PageEase = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)

@Composable
private fun SheetPageEntry(host: SheetPages, onDismiss: () -> Unit, title: String?, content: @Composable ColumnScope.() -> Unit) {
    val page = remember { SheetPage() }
    androidx.compose.runtime.SideEffect {
        page.title = title
        page.content = content
        page.onDismiss = onDismiss
    }
    DisposableEffect(page) {
        page.title = title
        page.content = content
        page.onDismiss = onDismiss
        host.stack.add(page)
        onDispose { page.leaving = true }
    }
}

private fun Modifier.pageDepth(pages: SheetPages, page: SheetPage?): Modifier = this.graphicsLayer {
    val over = pages.above(page)
    val v = over?.enter?.value ?: 0f
    translationX = -size.width * 0.24f * v
    alpha = (1f - v * 1.1f).coerceIn(0f, 1f)
}

@Composable
private fun SheetPageLayer(pages: SheetPages, page: SheetPage, closeAll: () -> Unit) {
    val leaving = page.leaving
    LaunchedEffect(leaving) {
        if (!leaving) {
            page.enter.animateTo(1f, androidx.compose.animation.core.tween(Motion.ms(360), easing = PageEase))
        } else {
            page.enter.animateTo(0f, androidx.compose.animation.core.tween(Motion.ms(280), easing = PageEase))
            pages.stack.remove(page)
        }
    }
    val viewport = remember { RevealViewport() }
    val back: () -> Unit = { if (!page.leaving) page.onDismiss() }
    val title = page.title
    androidx.compose.runtime.CompositionLocalProvider(LocalSheetClose provides back, LocalRevealViewport provides viewport) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = size.width * (1f - page.enter.value)
                    shadowElevation = if (page.enter.value in 0.01f..0.99f) 18.dp.toPx() else 0f
                }
                .pageDepth(pages, page)
                .background(AppColors.Panel)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            if (title != null) Box(Modifier.padding(horizontal = 16.dp)) { PageHeader(title, back, closeAll) }
            Column(
                Modifier.weight(1f).revealViewport(viewport).padding(horizontal = 16.dp).navigationBarsPadding().revealScroll(viewport).padding(top = if (title == null) 16.dp else 14.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                page.content?.invoke(this)
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.align(Alignment.CenterStart).size(38.dp).liveClick(CircleShape, sound = FeedbackSound.Close, onClick = onBack).background(AppColors.Field),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Back, "Назад", tint = AppColors.Muted, modifier = Modifier.size(18.dp)) }
            HeaderLabel(title, Modifier.padding(horizontal = 46.dp))
            Box(
                Modifier.align(Alignment.CenterEnd).size(38.dp).liveClick(CircleShape, sound = FeedbackSound.Close, onClick = onClose).background(AppColors.Field),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Close, "Закрыть", tint = AppColors.Muted, modifier = Modifier.size(18.dp)) }
        }
        SheetDivider()
    }
}

@Composable
fun ConfirmSheet(
    title: String,
    text: String,
    confirm: String,
    danger: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppSheet(onDismiss = onDismiss, title = title) {
        val close = sheetClose(onDismiss)
        Text(
            text,
            color = AppColors.Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth().fieldSurface(appCorner(16)).padding(horizontal = 14.dp, vertical = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val shape = appCorner(12)
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .liveClick(shape, sound = if (danger) FeedbackSound.Trash else FeedbackSound.Toggle) {
                        onConfirm()
                        close()
                    }
                    .background(if (danger) Danger.copy(alpha = 0.16f) else AppColors.Accent)
                    .border(1.dp, if (danger) Danger.copy(alpha = 0.5f) else AppColors.Accent, shape),
                contentAlignment = Alignment.Center,
            ) {
                val icon = actionIcon(confirm)
                if (icon != null) {
                    ActionLabel(confirm, if (danger) Danger else AppColors.OnAccent, fontSize = 14.sp, iconSize = 21.dp, icon = icon)
                } else {
                    FitText(confirm, if (danger) Danger else AppColors.OnAccent, 15.sp, Modifier.padding(horizontal = 10.dp), weight = FontWeight.SemiBold)
                }
            }
            AppButton("Отмена", primary = false, modifier = Modifier.weight(1f), onClick = close)
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, tint: Color, sound: FeedbackSound, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).liveClick(CircleShape, sound = sound, onClick = onClick).background(AppColors.Field),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp)) }
}

val StarYellow: Color get() = if (EInk.on) Color.Black else Color(0xFFFFC94D)

@Composable
private fun FavoriteButton(on: Boolean, onClick: () -> Unit) {
    val feedback = LocalFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    var burst by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val pop = remember { androidx.compose.animation.core.Animatable(1f) }
    var last by remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        if (on == last) return@LaunchedEffect
        last = on
        if (!Motion.enabled) return@LaunchedEffect
        if (on) {
            burst++
            pop.snapTo(0.6f)
            pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = 420f))
        } else {
            pop.snapTo(0.85f)
            pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 600f))
        }
    }
    val tint by androidx.compose.animation.animateColorAsState(if (on) StarYellow else AppColors.Muted, Motion.fade(220), label = "fav")
    Box(
        Modifier
            .size(40.dp)
            .sparkBurst(burst, listOf(StarYellow, AppColors.Accent, Color.White), count = 14, reach = 0.9f)
            .liveClick(CircleShape, sound = FeedbackSound.Toggle) {
                if (!on) feedback?.play(FeedbackSound.Star, view, 1.25f)
                onClick()
            }
            .background(AppColors.Field),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (on) AppIcons.StarFilled else AppIcons.Star,
            "Избранное",
            tint = tint,
            modifier = Modifier.size(20.dp).graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                rotationZ = (1f - pop.value) * 70f
            },
        )
    }
}

@Composable
private fun SmallChip(title: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val shape = appCorner(18)
    Row(
        Modifier
            .heightIn(min = 38.dp)
            .liveClick(shape, sound = FeedbackSound.Toggle, onClick = onClick)
            .then(if (selected) Modifier.accentHighlight(shape) else Modifier.fieldSurface(shape))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (selected) accentContent else AppColors.Muted, modifier = Modifier.size(16.dp))
        Text(title, color = if (selected) accentContent else AppColors.Text, fontSize = 14.sp)
    }
}

@Composable
fun BookSheet(
    book: Book,
    coverFile: File?,
    onRead: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onOpenBook: ((Book) -> Unit)? = null,
) {
    val app = LocalContext.current.applicationContext as App
    val scope = rememberCoroutineScope()
    var showStats by remember(book.id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf(false) }
    var editPaper by remember { mutableStateOf(false) }
    var editInfo by remember { mutableStateOf(false) }
    val inPages = book.paper || app.libraryPrefs.prefs.value.progressInPages
    val aura by rememberCoverAura(coverFile, app.libraryPrefs.prefs.value.aura && !EInk.on)

    val readAfter = remember { booleanArrayOf(false) }
    AppSheet({
        onDismiss()
        if (readAfter[0]) onRead()
    }, aura = aura) {
        val close = sheetClose(onDismiss)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val halo = aura?.firstOrNull()
            Box(
                Modifier
                    .size(width = 84.dp, height = 126.dp)
                    .then(if (halo != null) Modifier.shadow(20.dp, appCorner(8), clip = false, ambientColor = halo, spotColor = halo) else Modifier),
            ) {
                SmallCover(coverFile, Modifier.matchParentSize(), title = book.title, author = book.author)
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .size(28.dp)
                        .liveClick(CircleShape, sound = FeedbackSound.Open, pressedScale = 0.85f) { if (book.paper) editPaper = true else editInfo = true }
                        .background(Color(0xD9100D0B))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Pencil, "Изменить книгу", tint = Color.White, modifier = Modifier.size(14.dp)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(book.title, color = AppColors.Text, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) Text(book.author, color = AppColors.Muted, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                book.rating?.takeIf { it > 0f }?.let { r ->
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StarRating(r, 14.dp, gap = 2.dp)
                        Text(ratingText(r), color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundButton(AppIcons.Close, "Закрыть", AppColors.Muted, FeedbackSound.Close, close)
                FavoriteButton(book.favorite) { scope.launch { app.library.setFavorite(book.id, !book.favorite) } }
            }
        }

        var statusOpen by remember(book.id) { mutableStateOf(false) }
        Column(Modifier.sheetReveal()) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoTile(AppIcons.Pages, if (book.progress > 0 || inPages) progressLabel(book, inPages) else "Не начата", Modifier.weight(1f))
                StatusTile(book.status, statusOpen, Modifier.weight(1f)) { statusOpen = !statusOpen }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = statusOpen,
                enter = androidx.compose.animation.expandVertically(Motion.tween(260), expandFrom = Alignment.Top) + androidx.compose.animation.fadeIn(Motion.fade(220)),
                exit = androidx.compose.animation.shrinkVertically(Motion.tween(220), shrinkTowards = Alignment.Top) + androidx.compose.animation.fadeOut(Motion.fade(160)),
            ) {
                StatusOptions(book.status, Modifier.padding(top = 8.dp)) { status ->
                    statusOpen = false
                    if (status != book.status) scope.launch { app.library.setStatus(book.id, status) }
                }
            }
        }

        androidx.compose.animation.AnimatedContent(
            targetState = showStats,
            transitionSpec = {
                val dir = if (targetState) 1 else -1
                (androidx.compose.animation.slideInHorizontally(Motion.tween(260)) { dir * it / 5 } + androidx.compose.animation.fadeIn(Motion.fade(220)))
                    .togetherWith(androidx.compose.animation.slideOutHorizontally(Motion.tween(220)) { -dir * it / 5 } + androidx.compose.animation.fadeOut(Motion.fade(160)))
            },
            label = "book-sheet",
        ) { stats ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (stats) {
                    BookStatsContent(app, book, onBack = { showStats = false })
                } else {
                    BookMainContent(app, book, onRead = {
                        readAfter[0] = true
                        close()
                    }, onStats = { showStats = true }, onRate = { rating = true }, onDelete = { confirmDelete = true }, onEdit = { editPaper = true }, onOpenBook = onOpenBook)
                }
            }
        }
        if (rating) RatingSheet(book.id, celebrate = false) { rating = false }
        if (editPaper) PaperBookSheet(book) { editPaper = false }
        if (editInfo) BookEditSheet(book) { editInfo = false }
        if (confirmDelete) {
            ConfirmSheet(
                title = "Удалить книгу?",
                text = if (book.paper) "Книга и её сеансы удалятся. Статистика останется." else "Книга и заметки к ней удалятся. Словарь и статистика останутся.",
                confirm = "Удалить",
                onConfirm = onDelete,
                onDismiss = { confirmDelete = false },
            )
        }
    }
}

@Composable
private fun StatusTile(status: ReadStatus, open: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = appCorner(16)
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, Motion.tween(240), label = "status-chevron")
    val edge by androidx.compose.animation.animateColorAsState(if (open) AppColors.Accent.copy(alpha = 0.5f) else Color.Transparent, Motion.fade(200), label = "status-edge")
    Row(
        modifier
            .fillMaxHeight()
            .liveClick(shape, sound = if (open) FeedbackSound.Close else FeedbackSound.Open, pressedScale = 0.97f, onClick = onClick)
            .fieldSurface(shape)
            .border(1.dp, edge, shape)
            .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val tint = AppColors.Accent
        Box(
            Modifier
                .size(32.dp)
                .clip(appCorner(10))
                .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.26f), tint.copy(alpha = 0.08f))))
                .border(1.dp, tint.copy(alpha = 0.18f), appCorner(10)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.Crossfade(status, animationSpec = Motion.fade(200), label = "status-icon") { s ->
                Icon(statusIcon(s), null, tint = tint, modifier = Modifier.size(17.dp))
            }
        }
        androidx.compose.animation.Crossfade(status, Modifier.weight(1f), animationSpec = Motion.fade(200), label = "status-title") { s ->
            Text(
                s.title,
                color = AppColors.Text,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 15.sp, stepSize = 0.5.sp),
            )
        }
        Icon(AppIcons.Chevron, null, tint = AppColors.Muted, modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = 90f + turn })
    }
}

@Composable
private fun StatusOptions(current: ReadStatus, modifier: Modifier, onPick: (ReadStatus) -> Unit) {
    val shape = appCorner(16)
    Column(
        modifier
            .fillMaxWidth()
            .fieldSurface(shape)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(ReadStatus.Reading, ReadStatus.Planned, ReadStatus.Finished, ReadStatus.New).forEach { s ->
            val selected = s == current
            val row = appCorner(12)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .liveClick(row, sound = if (selected) FeedbackSound.Close else FeedbackSound.Check, pressedScale = 0.98f) { onPick(s) }
                    .then(if (selected) Modifier.clip(row).background(AppColors.AccentSoft) else Modifier)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(statusIcon(s), null, tint = if (selected) AppColors.Accent else AppColors.Muted, modifier = Modifier.size(18.dp))
                Text(s.title, color = if (selected) AppColors.Accent else AppColors.Text, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, modifier = Modifier.weight(1f))
                if (selected) Icon(AppIcons.Check, null, tint = AppColors.Accent, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun InfoTile(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier, tint: Color = AppColors.Accent) {
    val shape = appCorner(16)
    Row(
        modifier.fillMaxHeight().fieldSurface(shape).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(appCorner(10))
                .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.26f), tint.copy(alpha = 0.08f))))
                .border(1.dp, tint.copy(alpha = 0.18f), appCorner(10)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp)) }
        Text(
            text,
            color = AppColors.Text,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 15.sp, stepSize = 0.5.sp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.BookMainContent(
    app: App,
    book: Book,
    onRead: () -> Unit,
    onStats: () -> Unit,
    onRate: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onOpenBook: ((Book) -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    if (book.paper) {
        PaperMain(book, onEdit)
        RateRow(book, onRate)
        BookAboutCard(app, book, onOpenBook)
        SettingsNavRow(AppIcons.Chart, "Статистика книги", "Время по дням, вклад в цели") { onStats() }
        DeleteRow("Удалить книгу", onDelete)
        return
    }
    val watching = app.readingTimer.watch.collectAsStateWithLifecycle().value?.let { it.bookId == book.id && it.running } == true
    val readShape = appCorner(18)
    Row(
        Modifier
            .fillMaxWidth()
            .sheetReveal()
            .heightIn(min = 56.dp)
            .liveClick(readShape, sound = FeedbackSound.Open, pressedScale = 0.97f) { onRead() }
            .accentHighlight(readShape),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Play, null, tint = accentContent, modifier = Modifier.size(20.dp))
        Text(
            if (book.progress > 0.0) "Продолжить" else "Читать",
            color = accentContent,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp),
        )
        if (watching) Icon(AppIcons.Clock, null, tint = accentContent, modifier = Modifier.padding(start = 8.dp).size(18.dp))
    }

    ReadingWatchCard(book)

    RateRow(book, onRate)
    BookAboutCard(app, book, onOpenBook)

    SettingsNavRow(AppIcons.Chart, "Статистика книги", "Время, скорость, остаток") { onStats() }

    DeleteRow("Удалить с телефона", onDelete)
}

@Composable
internal fun DeleteRow(label: String, onDelete: () -> Unit) {
    val shape = appCorner(16)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).sheetReveal()
            .liveClick(shape, sound = FeedbackSound.Trash) { onDelete() }
            .border(1.dp, Danger.copy(alpha = 0.45f), shape),
        contentAlignment = Alignment.Center,
    ) { ActionLabel(label, Danger, fontSize = 14.sp, iconSize = 21.dp, icon = AppIcons.Trash) }
}

@Composable
private fun RateRow(book: Book, onClick: () -> Unit) {
    val shape = appCorner(16)
    Row(
        Modifier
            .fillMaxWidth()
            .sheetReveal()
            .heightIn(min = 56.dp)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick)
            .fieldSurface(shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Оценка", color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                book.review.ifBlank { if (book.rating != null) "Без отзыва" else "Оценить и написать отзыв" },
                color = AppColors.Faint,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StarRating(book.rating ?: 0f, 18.dp, gap = 3.dp)
    }
}

@Composable
private fun BookStatsContent(app: App, book: Book, onBack: () -> Unit) {
    val days by app.stats.days.collectAsStateWithLifecycle()
    val notes by app.notes.notes.collectAsStateWithLifecycle()
    val words by app.dictionary.entries.collectAsStateWithLifecycle()
    val stats = remember(days, book.statsKey) { StatsCalc.book(days, book.statsKey) }

    PageBar("Статистика книги", onBack, icon = AppIcons.Chart)

    val speed = stats.pagesPerHour
    val remaining = if (speed != null && book.positions > 0 && book.status != ReadStatus.Finished) {
        val pagesLeft = (1.0 - book.progress).coerceAtLeast(0.0) * book.positions
        StatsCalc.duration((pagesLeft / speed * 3_600_000).toLong())
    } else {
        null
    }
    StatPanel(
        "Показатели",
        listOf(
            StatCell("Время чтения", StatsCalc.duration(stats.ms), icon = AppIcons.Clock),
            StatCell("Страниц", if (book.positions > 0) "${stats.pages}/${book.positions}" else stats.pages.toString(), icon = AppIcons.Pages),
            StatCell("Скорость", speed?.let { "${it.toInt()} стр/ч" } ?: "—", icon = AppIcons.Speed),
            StatCell(
                "Осталось",
                when {
                    book.status == ReadStatus.Finished -> "Прочитано"
                    remaining != null -> "≈ $remaining"
                    else -> "—"
                },
                icon = AppIcons.Calendar,
                content = if (book.status == ReadStatus.Finished) {
                    { StatusPill(ReadStatus.Finished, small = false, framed = true) }
                } else {
                    null
                },
            ),
            StatCell("Дней чтения", stats.days.toString(), icon = AppIcons.Fire),
            StatCell("Заметки и слова", "${notes.count { it.bookId == book.id }} / ${words.count { it.bookId == book.id }}", icon = AppIcons.Note),
        ),
        icon = AppIcons.Chart,
    )

    val perDay = remember(days, book.statsKey) {
        days.mapNotNull { (d, b) -> b[book.statsKey]?.let { e -> runCatching { LocalDate.parse(d) }.getOrNull()?.let { it to e } } }.toMap()
    }
    MinutesCard(perDay, LocalDate.now())

    fun day(time: Long) = StatsCalc.date(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate())
    val dates = listOfNotNull(
        "Добавлена: ${day(book.addedAt)}",
        stats.firstDay?.let { "Начата: ${StatsCalc.date(it)}" },
        book.finishedAt?.takeIf { book.status == ReadStatus.Finished }?.let { "Прочитана: ${day(it)}" },
    )
    Text(dates.joinToString("\n"), color = AppColors.Faint, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
fun LibraryViewSheet(prefs: LibraryPrefs, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    AppSheet(onDismiss, title = "Вид и сортировка") {
        SettingsGroup(null) {
            LibrarySort.entries.forEachIndexed { index, sort ->
                if (index > 0) ListDivider()
                val icon = when (sort) {
                    LibrarySort.Recent -> AppIcons.Clock
                    LibrarySort.Added -> AppIcons.Plus
                    LibrarySort.Title -> AppIcons.TextSize
                    LibrarySort.Author -> AppIcons.Pencil
                    LibrarySort.Series -> AppIcons.Series
                    LibrarySort.Progress -> AppIcons.Chart
                }
                SheetOptionRow(PickerOption(sort.id, sort.title, icon = icon), sort == prefs.sort) {
                    app.libraryPrefs.update {
                        val names = sort == LibrarySort.Title || sort == LibrarySort.Author || sort == LibrarySort.Series
                        it.copy(sort = sort, descending = if (it.sort == sort) it.descending else !names)
                    }
                }
            }
            val names = prefs.sort == LibrarySort.Title || prefs.sort == LibrarySort.Author || prefs.sort == LibrarySort.Series
            val options = when {
                names -> listOf("А → Я", "Я → А")
                prefs.sort == LibrarySort.Progress -> listOf("Больше", "Меньше")
                else -> listOf("Сначала новые", "Сначала старые")
            }
            val first = if (names) !prefs.descending else prefs.descending
            SettingSegmented(null, options, if (first) 0 else 1) { i ->
                val wantFirst = i == 0
                app.libraryPrefs.update { it.copy(descending = if (names) !wantFirst else wantFirst) }
            }
        }
        SettingsGroup(null) {
            PickerRow(
                label = "Прогресс",
                icon = AppIcons.Percent,
                options = listOf(
                    PickerOption("percent", "Проценты", "30%", icon = AppIcons.Percent),
                    PickerOption("pages", "Страницы", "110/366", icon = AppIcons.Pages),
                ),
                selectedId = if (prefs.progressInPages) "pages" else "percent",
            ) { id -> app.libraryPrefs.update { it.copy(progressInPages = id == "pages") } }
            SettingSegmented(null, listOf("Полки", "Сетка", "Список"), LibraryView.entries.indexOf(prefs.view)) { i ->
                app.libraryPrefs.update { it.copy(view = LibraryView.entries[i]) }
            }
            Appear(prefs.view == LibraryView.Shelves) {
                SettingSegmented(
                    "Полка",
                    listOf("Обычная", "С декором", "Живая"),
                    when {
                        !prefs.liveShelf -> 0
                        !prefs.shelfBehavior -> 1
                        else -> 2
                    },
                ) { i ->
                    app.libraryPrefs.update { it.copy(liveShelf = i > 0, shelfBehavior = i == 2) }
                }
            }
            Appear(prefs.view == LibraryView.Grid) {
                SettingSegmented("Размер обложек", listOf("Мелкие", "Средние", "Крупные"), prefs.coverSize) { i ->
                    app.libraryPrefs.update { it.copy(coverSize = i) }
                }
            }
        }
    }
}

sealed class ShelfDialog {
    data class Create(val bookId: String?) : ShelfDialog()
    data class Edit(val name: String) : ShelfDialog()
}

@Composable
fun ShelfDialogView(dialog: ShelfDialog, shelves: List<String>, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val books by app.library.books.collectAsStateWithLifecycle()
    val original = (dialog as? ShelfDialog.Edit)?.name.orEmpty()
    var name by remember(dialog) { mutableStateOf(original) }
    val initial = remember(dialog) {
        when (dialog) {
            is ShelfDialog.Edit -> books.filter { original in it.shelves }.map { it.id }.toSet()
            is ShelfDialog.Create -> setOfNotNull(dialog.bookId)
        }
    }
    var chosen by remember(dialog) { mutableStateOf(initial) }
    var confirmDelete by remember { mutableStateOf(false) }
    val clean = name.trim()
    val taken = clean != original && clean in shelves
    val ready = clean.isNotEmpty() && !taken

    fun save() {
        if (!ready) return
        app.appScope.launch {
            if (dialog is ShelfDialog.Edit && clean != original) app.library.renameShelf(original, clean)
            app.library.books.value.forEach { b ->
                val has = clean in b.shelves
                val want = b.id in chosen
                if (has != want) app.library.setShelves(b.id, if (want) b.shelves + clean else b.shelves - clean)
            }
        }
        app.libraryPrefs.update { p ->
            p.copy(
                shelves = (p.shelves.map { if (it == original && original.isNotEmpty()) clean else it } + clean).distinct(),
                filter = if (original.isNotEmpty() && p.filter == "shelf:$original") "shelf:$clean" else p.filter,
            )
        }
    }

    AppSheet(onDismiss, title = if (dialog is ShelfDialog.Create) "Новая полка" else "Полка") {
        val close = sheetClose(onDismiss)
        BasicTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            singleLine = true,
            textStyle = TextStyle(color = AppColors.Text, fontSize = 16.sp),
            cursorBrush = SolidColor(AppColors.Accent),
            modifier = Modifier.fillMaxWidth().height(50.dp).fieldSurface(appCorner(16)).padding(horizontal = 16.dp),
            decorationBox = { inner ->
                Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Shelf, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                    Box(Modifier.weight(1f).padding(start = 10.dp)) {
                        if (name.isEmpty()) Text("Название полки", color = AppColors.Faint, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    }
                }
            },
        )
        if (taken) Text("Такая полка уже есть", color = Danger, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
        SettingsGroup("Книги на полке · ${chosen.size}/${books.size}") {
            Column(Modifier.padding(vertical = 6.dp)) {
                books.sortedBy { it.title.lowercase() }.forEachIndexed { index, b ->
                    if (index > 0) ListDivider()
                    val on = b.id in chosen
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 58.dp)
                            .liveClick(appCorner(12), sound = FeedbackSound.Toggle, pressedScale = 0.98f) { chosen = if (on) chosen - b.id else chosen + b.id }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BookCover(b.id, 32.dp)
                        Text(b.title, color = if (on) AppColors.Accent else AppColors.Text, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(appCorner(7))
                                .background(if (on) AppColors.Accent else Color.Transparent)
                                .border(1.5.dp, if (on) AppColors.Accent else AppColors.Line, appCorner(7)),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(AppIcons.Check, null, tint = AppColors.OnAccent, modifier = Modifier.size(15.dp)) }
                    }
                }
                if (books.isEmpty()) Text("Сначала добавь книги", color = AppColors.Faint, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        AppButton(if (dialog is ShelfDialog.Create) "Создать полку" else "Применить", primary = true, modifier = Modifier.fillMaxWidth(), labeled = true) {
            if (ready) {
                save()
                close()
            }
        }
        if (dialog is ShelfDialog.Edit) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .liveClick(appCorner(12), sound = FeedbackSound.Close) { confirmDelete = true }
                    .border(1.dp, Danger.copy(alpha = 0.45f), appCorner(12)),
                contentAlignment = Alignment.Center,
            ) {
                ActionLabel("Удалить полку", Danger, fontSize = 14.sp, iconSize = 21.dp)
            }
        }
        if (confirmDelete) {
            ConfirmSheet(
                title = "Удалить полку?",
                text = "Книги останутся в библиотеке, удалится только полка «$original».",
                confirm = "Удалить",
                onConfirm = {
                    app.appScope.launch { app.library.renameShelf(original, null) }
                    app.libraryPrefs.update { p ->
                        p.copy(shelves = p.shelves - original, filter = if (p.filter == "shelf:$original") "all" else p.filter)
                    }
                    onDismiss()
                },
                onDismiss = { confirmDelete = false },
            )
        }
    }
}
