package com.flerty.wikiflerty.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flerty.wikiflerty.App
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SettingsNav {
    var open by mutableStateOf<String?>(null)
    var guideQuery by mutableStateOf("")
    var news by mutableStateOf(false)
}

object Tips {
    private fun prefs(context: Context) = context.getSharedPreferences("tips", Context.MODE_PRIVATE)

    fun tourDone(context: Context) = prefs(context).getBoolean("tour", false)
    fun markTour(context: Context) = prefs(context).edit().putBoolean("tour", true).apply()

    fun readerCoachDone(context: Context) = prefs(context).getBoolean("reader_coach", false)
    fun markReaderCoach(context: Context) = prefs(context).edit().putBoolean("reader_coach", true).apply()

    fun seenVersion(context: Context) = prefs(context).getInt("seen_version", -1)
    fun markVersion(context: Context, code: Int) = prefs(context).edit().putInt("seen_version", code).apply()

    fun seenRelease(context: Context) = prefs(context).getInt("seen_release", 0)
    fun markRelease(context: Context, code: Int) = prefs(context).edit().putInt("seen_release", code).apply()

    fun versionCode(context: Context): Int = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    }.getOrDefault(0)
}

enum class NewsTarget { None, Stats, Custom, Library, LibraryView, Guide, Ai }

class NewsItem(val group: String, val icon: ImageVector, val title: String, val text: String, val target: NewsTarget = NewsTarget.None, val query: String = "")

const val NewsRelease = 248

val WhatsNewItems = listOf(
    NewsItem("Библиотека", AppIcons.Play, "Продолжить чтение", "Последняя книга под фильтром полок. Нажми на кольцо прогресса — и сразу читать", NewsTarget.Library),
    NewsItem("Чтение", AppIcons.Person, "Имена персонажей", "Перед именем — флажок с вопросом. Нажми на имя, чтобы открыть карточку", NewsTarget.Guide, "Персонажи"),
    NewsItem("Чтение", AppIcons.Note, "Длинные заметки", "Сворачиваются в карточке, «Развернуть» показывает весь текст"),
    NewsItem("Оформление", AppIcons.Sun, "Листья на полках", "Слетают на полки в разные места и со временем исчезают", NewsTarget.Library),
    NewsItem("Оформление", AppIcons.Sparkle, "«Что нового» в настройках", "Карусель в стиле твоего шаблона — листай карточки вбок"),
    NewsItem("Исправления", AppIcons.Cloud, "Синхронизация закладок", "Закладки, заметки и персонажи теперь переносятся между устройствами"),
    NewsItem("Исправления", AppIcons.Snow, "Эффекты без перезапуска", "Снег, листья и светлячки не начинаются заново при смене раздела"),
    NewsItem("Исправления", AppIcons.Shelf, "Перетаскивание книг", "Без рывков и скачка обложки, когда отпускаешь книгу"),
    NewsItem("Исправления", AppIcons.Fire, "Полки без перезагрузки", "Полки не появляются заново при возврате в библиотеку, а при прокрутке снова плавно выезжают"),
    NewsItem("Исправления", AppIcons.Theme, "Листья в «Дзен»", "Осенью и летом — такие же листья, как у сезона, а весной сакура"),
)

@Composable
fun OnboardingHost(libraryEmpty: Boolean, onAddBook: () -> Unit, onTarget: (NewsItem) -> Unit) {
    val context = LocalContext.current
    var tour by remember { mutableStateOf(false) }
    var news by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        val code = Tips.versionCode(context)
        val seen = Tips.seenVersion(context)
        when {
            !Tips.tourDone(context) && libraryEmpty && seen < 0 -> tour = true
            Tips.seenRelease(context) < NewsRelease -> news = WhatsNewItems
        }
        if (!libraryEmpty) Tips.markTour(context)
        Tips.markVersion(context, code)
        Tips.markRelease(context, NewsRelease)
    }
    if (tour) {
        WelcomeTour(
            onDone = { add ->
                Tips.markTour(context)
                tour = false
                if (add) onAddBook()
            },
        )
    }
    LaunchedEffect(SettingsNav.news) {
        if (SettingsNav.news) {
            news = WhatsNewItems
            SettingsNav.news = false
        }
    }
    if (news.isNotEmpty()) {
        WhatsNewSheet(news, onDismiss = { news = emptyList() }) { item ->
            news = emptyList()
            onTarget(item)
        }
    }
}

@Composable
fun WhatsNewSheet(items: List<NewsItem>, onDismiss: () -> Unit, onTarget: (NewsItem) -> Unit) {
    AppSheet(onDismiss, title = "Что нового") {
        val close = sheetClose(onDismiss)
        items.groupBy { it.group }.forEach { (group, list) ->
            val groupIcon = when (group) {
                "Библиотека" -> AppIcons.Shelf
                "Чтение" -> AppIcons.Book
                "Оформление" -> AppIcons.Palette
                else -> AppIcons.Sparkle
            }
            TabbedFrame(group, icon = groupIcon, inset = false) {
                list.forEachIndexed { i, item ->
                    if (i > 0) ListDivider(Modifier.padding(horizontal = 14.dp))
                    val shape = appCorner(18)
                    val active = item.target != NewsTarget.None
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (active) Modifier.liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f) { onTarget(item) } else Modifier)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingIcon(item.icon, 34.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.title, color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(item.text, color = AppColors.Muted, fontSize = 13.sp, lineHeight = 17.sp)
                        }
                        if (active) Icon(AppIcons.Chevron, null, tint = AppColors.Faint, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        AppButton("Отлично", primary = true, modifier = Modifier.fillMaxWidth()) { close() }
    }
}

@Composable
fun WelcomeTour(onDone: (Boolean) -> Unit) {
    val pager = rememberPagerState { 5 }
    val scope = rememberCoroutineScope()
    Dialog({ onDone(false) }, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false)) {
        KeepHostBars()
        Box(Modifier.fillMaxSize().background(AppColors.Background)) {
            KitBackdrop(AppStyle.decor, AppStyle.base, AppColors.Accent, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AppColors.Background.copy(alpha = 0.2f), AppColors.Background.copy(alpha = 0.82f)))))
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(5) { i ->
                            val active = i == pager.currentPage
                            val color by animateColorAsState(if (i <= pager.currentPage) AppColors.Accent else AppColors.Text.copy(alpha = 0.2f), Motion.tween(260), label = "dot")
                            val width by androidx.compose.animation.core.animateDpAsState(if (active) 22.dp else 8.dp, Motion.tween(260), label = "dotw")
                            Box(Modifier.width(width).height(8.dp).clip(CircleShape).background(color))
                        }
                    }
                    AnimatedVisibility(pager.currentPage < 4, enter = fadeIn(Motion.fade(200)), exit = fadeOut(Motion.fade(160))) {
                        Text(
                            "Пропустить",
                            color = AppColors.Muted,
                            fontSize = 14.sp,
                            modifier = Modifier.liveClick(appCorner(10), sound = FeedbackSound.Close) { onDone(false) }.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                    val visible = pager.currentPage == page
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                            if (visible) {
                                when (page) {
                                    0 -> KitPicker()
                                    1 -> ShelfScene()
                                    2 -> TapScene()
                                    3 -> SelectScene()
                                    else -> PowerScene()
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        val (title, text) = TourTexts[page]
                        Text(title, color = AppColors.Text, fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Text(text, color = AppColors.Muted, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 380.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp)) {
                    AnimatedContent(pager.currentPage == 4, transitionSpec = { fadeIn(Motion.fade(220)) togetherWith fadeOut(Motion.fade(140)) }, label = "tour-btn") { last ->
                        if (last) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                AppButton("Добавить первую книгу", primary = true, modifier = Modifier.fillMaxWidth()) { onDone(true) }
                                AppButton("Начать", primary = false, modifier = Modifier.fillMaxWidth()) { onDone(false) }
                            }
                        } else {
                            AppButton("Далее", primary = true, modifier = Modifier.fillMaxWidth()) {
                                scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val TourTexts = listOf(
    "Добро пожаловать" to "Выбери, как будет выглядеть твоя библиотека. Поменять можно в любой момент в Кастомизации.",
    "Живые полки" to "Предметы на полках растут, когда ты читаешь каждый день, и радуются каждой прочитанной книге.",
    "Как читать" to "Края экрана листают страницы, центр открывает меню: главы, заметки, шрифт и всё остальное.",
    "Зажми слово" to "Появится меню: заметка, маркер, перевод, толкование, персонаж и ИИ. Порядок кнопок настраивается.",
    "И ещё много всего" to "Чтение вслух офлайн-голосами, ИИ-пересказ, синхронизация без регистрации, итоги года. Все подсказки — в Настройки → Подсказки.",
)

@Composable
private fun KitPicker() {
    val app = LocalContext.current.applicationContext as App
    val style = AppStyle.current
    val current = Kit.of(style.kit)
    val light = AppStyle.light
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
        items(Kit.entries.size) { i ->
            val kit = Kit.entries[i]
            val selected = kit == current
            val thumb by rememberKitThumb(kit, light, 110.dp, 160.dp)
            val shape = appCorner(16)
            val base = if (light) kit.light else kit.dark
            val accent = Color(if (light) kit.accentLight else kit.accent)
            Reveal {
                Box(
                    Modifier
                        .size(110.dp, 160.dp)
                        .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.94f) {
                            app.style.update { st ->
                                val mode = if (st.kit != kit.id && ThemeMode.of(st.mode) != ThemeMode.System) (if (kit.lightFirst) ThemeMode.Light else ThemeMode.Dark).id else st.mode
                                val lightNow = when (ThemeMode.of(mode)) { ThemeMode.Light -> true; ThemeMode.Dark -> false; ThemeMode.System -> !AppStyle.systemDark }
                                kit.applyTo(st, lightNow).copy(mode = mode)
                            }
                        }
                        .clip(shape)
                        .border(if (selected) 2.5.dp else 1.dp, if (selected) accent else AppColors.PanelBorder, shape),
                ) {
                    thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                        ?: Box(Modifier.fillMaxSize().background(Color(base.background)))
                    Text(
                        kit.title,
                        color = Color(base.text),
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    )
                    if (selected) {
                        Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Check, null, tint = if (accent.red * 0.3f + accent.green * 0.59f + accent.blue * 0.11f > 0.5f) Color(0xFF111111) else Color.White, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
        }
    }
    val modes = ThemeMode.entries
    Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        SettingSegmented(null, listOf("☀ Светлая", "☾ Тёмная", "◐ Система"), modes.indexOf(ThemeMode.of(style.mode))) { idx ->
            app.style.update { it.copy(mode = modes[idx].id) }
        }
    }
    }
}

@Composable
private fun ShelfScene() {
    val decor = AppStyle.decor
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            grow.snapTo(0f)
            delay(500)
            grow.animateTo(1f, tween(Motion.ms(1400).coerceAtLeast(1), easing = FastOutSlowInEasing))
            delay(1800)
        }
    }
    val life = ShelfLife(today = true, streak = if (grow.value > 0.5f) 30 else 3, frozen = false, books = if (grow.value > 0.5f) 25 else 1, sad = false, evening = false)
    CompositionLocalProvider(LocalShelfState provides ShelfState(life, null)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ShelfProp(decor, 0, Modifier.graphicsLayer { val s = 1f + 0.15f * kotlin.math.sin(Math.PI.toFloat() * grow.value); scaleX = s; scaleY = s; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) })
                Box(Modifier.size(64.dp, 96.dp).clip(appCorner(5))) { GeneratedCover("Моя первая книга", "WikiFlerty", Modifier.fillMaxSize()) }
                Box(Modifier.size(58.dp, 88.dp).clip(appCorner(5))) { GeneratedCover("Сказки на ночь", "", Modifier.fillMaxSize()) }
                ShelfProp(decor, 2, Modifier.graphicsLayer { val s = 1f + 0.15f * kotlin.math.sin(Math.PI.toFloat() * grow.value); scaleX = s; scaleY = s; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) })
            }
            Box(Modifier.width(300.dp).height(14.dp).clip(appCorner(4)).background(Brush.verticalGradient(listOf(Color(0xFFA8743F), Color(0xFF6B4322)))))
        }
    }
}

@Composable
private fun TapScene() {
    val loop = rememberInfiniteTransition(label = "tap")
    val t by loop.animateFloat(0f, 3f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "tap-t")
    val zone = t.toInt().coerceIn(0, 2)
    val phase = t - zone
    val accent = AppColors.Accent
    val text = AppColors.Text
    val panel = AppColors.Panel
    val border = AppColors.PanelBorder
    Box(Modifier.size(180.dp, 290.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val r = CornerRadius(22.dp.toPx())
            drawRoundRect(panel, cornerRadius = r)
            drawRoundRect(border, cornerRadius = r, style = Stroke(2.dp.toPx()))
            val pad = 18.dp.toPx()
            var y = 34.dp.toPx()
            var k = 0
            while (y < size.height - 30.dp.toPx()) {
                val w = (size.width - pad * 2) * (if (k % 5 == 4) 0.6f else 1f)
                drawRoundRect(text.copy(alpha = 0.16f), Offset(pad, y), Size(w, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                y += 12.dp.toPx()
                k++
            }
            val zw = size.width / 3
            val hl = (if (phase < 0.8f) 1f else (1f - (phase - 0.8f) * 5f)).coerceIn(0f, 1f)
            drawRect(accent.copy(alpha = 0.16f * hl), Offset(zw * zone, 0f), Size(zw, size.height))
            val c = Offset(zw * zone + zw / 2, size.height * 0.55f)
            val press = if (phase < 0.25f) phase / 0.25f else 1f
            drawCircle(accent.copy(alpha = 0.35f * (1f - press) * hl), 10.dp.toPx() + 22.dp.toPx() * press, c)
            drawCircle(accent.copy(alpha = 0.9f * hl), 9.dp.toPx(), c)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            listOf("Назад", "Меню", "Вперёд").forEachIndexed { i, label ->
                Text(label, color = if (i == zone) AppColors.Accent else AppColors.Faint, fontSize = 11.sp, fontWeight = if (i == zone) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SelectScene() {
    val loop = rememberInfiniteTransition(label = "sel")
    val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "sel-t")
    val icons = listOf(AppIcons.Note, AppIcons.Translate, AppIcons.TextSize, AppIcons.Person, AppIcons.Sparkle, AppIcons.More)
    val active = ((t * 1.3f - 0.2f) * icons.size).toInt().coerceIn(-1, icons.size - 1)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.panelSurface(appCorner(26), elevation = 14.dp).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            icons.forEachIndexed { i, icon ->
                val on = i == active
                val bg by animateColorAsState(if (on) AppColors.AccentTintStrong else Color.Transparent, Motion.tween(200), label = "sel-bg")
                Box(Modifier.size(44.dp).clip(appCorner(14)).background(bg), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = if (on) AppColors.Accent else AppColors.Text, modifier = Modifier.size(21.dp))
                }
            }
        }
        val accent = AppColors.Accent
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Он ", color = AppColors.Text, fontSize = 20.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
            Box(Modifier.clip(appCorner(4)).background(accent.copy(alpha = 0.3f)).padding(horizontal = 3.dp)) {
                Text("улыбнулся", color = AppColors.Text, fontSize = 20.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
            }
            Text(" в ответ", color = AppColors.Text, fontSize = 20.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
        }
    }
}

@Composable
private fun PowerScene() {
    val items = listOf(
        AppIcons.Headphones to "Слушать",
        AppIcons.Sparkle to "ИИ",
        AppIcons.Cloud to "Синхронизация",
        AppIcons.Chart to "Итоги",
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { c, (icon, label) ->
                    val a = remember { Animatable(0f) }
                    LaunchedEffect(Unit) {
                        delay(Motion.ms(120 + (r * 2 + c) * 140).toLong())
                        a.animateTo(1f, tween(Motion.ms(520).coerceAtLeast(1), easing = FastOutSlowInEasing))
                    }
                    Column(
                        Modifier
                            .size(130.dp, 110.dp)
                            .graphicsLayer { alpha = a.value; scaleX = 0.8f + 0.2f * a.value; scaleY = 0.8f + 0.2f * a.value }
                            .fieldSurface(appCorner(20))
                            .kitFrame(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        SettingIcon(icon, 44.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            label,
                            color = AppColors.Text,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

class GuideEntry(val icon: ImageVector, val title: String, val how: String, val keys: String = "")

class GuideGroup(val title: String, val icon: ImageVector, val entries: List<GuideEntry>)

val GuideGroups = listOf(
    GuideGroup(
        "Библиотека", AppIcons.Book,
        listOf(
            GuideEntry(AppIcons.Plus, "Добавить книги", "«+» внизу → Файл, Папка или Бумажная книга. Поддерживаются EPUB, FB2, TXT, PDF", "импорт загрузить открыть файл"),
            GuideEntry(AppIcons.Image, "Обложка и описание", "Карточка книги → карандаш на обложке → Из галереи, Сфотографировать или Найти в интернете", "обложка картинка фото название автор"),
            GuideEntry(AppIcons.Fire, "Живые предметы", "Предметы на полках растут от серии чтения и прочитанных книг. Иней — когда действует заморозка", "полка декор растение"),
            GuideEntry(AppIcons.Bell, "Напоминание", "Колокольчик в шапке библиотеки", "уведомление время"),
            GuideEntry(AppIcons.Shelf, "Перетащить книгу на другую полку", "Зажми книгу на полке и перетащи на нужную. На «Без полки» — убрать с полки", "перетащить перенести полка зажать"),
            GuideEntry(AppIcons.Sort, "Вид полки", "Вид и сортировка → Полка: обычная, с декором или живая", "декор живые украшения"),
            GuideEntry(AppIcons.Pages, "PDF", "Добавь PDF как обычную книгу — он откроется в читалке со всеми функциями. Сканы без текста показываются страницами", "pdf скан документ"),
        ),
    ),
    GuideGroup(
        "Чтение", AppIcons.Pages,
        listOf(
            GuideEntry(AppIcons.Options, "Меню чтения", "Тап по центру экрана: главы, заметки, закладки, персонажи и Опции", "панель настройки"),
            GuideEntry(AppIcons.Scroll, "Страницы или лента", "Опции → Листание. В ленте потяни в конце главы — откроется следующая", "прокрутка скролл свайп"),
            GuideEntry(AppIcons.TextSize, "Шрифт и отступы", "Опции → Текст", "размер интервал"),
            GuideEntry(AppIcons.Eye, "Бионическое чтение", "Опции → Текст", "жирный быстрое"),
            GuideEntry(AppIcons.Palette, "Тема страницы и стиль шаблона", "Опции → Тема: фон, ночь по расписанию, буквица и орнамент", "ночь тёмная буквица"),
            GuideEntry(AppIcons.Fire, "Атмосфера", "Опции → Атмосфера: дождь, камин и другие звуки", "звуки фон дождь"),
            GuideEntry(AppIcons.Panel, "Кнопки внизу", "Опции → Панель: до пяти кнопок, например Авто, Поиск, Слушать", "автопрокрутка поиск"),
            GuideEntry(AppIcons.Flag, "Флажок внизу", "Нажми на флажок: страница главы и книги, проценты, время до конца главы, часы, заряд, положение и размер", "флажок страницы прогресс время часы батарея"),
            GuideEntry(AppIcons.Bookmark, "Закладки", "Значок закладки в верхней панели. После добавления можно назвать, переименовать — карандаш в списке", "закладка название"),
            GuideEntry(AppIcons.Toc, "Оглавление", "Меню чтения → Главы. Открывается на текущей главе", "главы содержание оглавление"),
            GuideEntry(AppIcons.Note, "Сноски", "Нажми на сноску — откроется окно. «Открыть в книге» — потом кнопка «К тексту» вернёт назад", "примечание ссылка"),
        ),
    ),
    GuideGroup(
        "Выделение", AppIcons.Select,
        listOf(
            GuideEntry(AppIcons.Select, "Меню выделения", "Зажми слово и потяни. Порядок кнопок — Опции → Выделение", "выделить зажать"),
            GuideEntry(AppIcons.Translate, "Перевод", "Выдели текст → Перевод. Язык — плашка в окне перевода", "переводчик язык"),
            GuideEntry(AppIcons.TextSize, "Толкование и карточки", "Выдели слово → Словарь → «В словарь». Повторяй в разделе Словарь", "значение слово флешкарты"),
            GuideEntry(AppIcons.Person, "Персонажи", "Выдели имя → ⋯ → Персонаж. Перед именем в тексте появится флажок, нажми на имя — откроется карточка. Список — в меню чтения на вкладке с человечком", "герой имя"),
            GuideEntry(AppIcons.Quote, "Цитата-карточка", "Выдели текст → ⋯ → Цитата", "поделиться картинка"),
        ),
    ),
    GuideGroup(
        "Слушать и ИИ", AppIcons.Headphones,
        listOf(
            GuideEntry(AppIcons.Headphones, "Чтение вслух", "Опции → Чтение вслух: голос, скорость, таймер сна. Офлайн-голоса скачиваются там же", "озвучка голос tts"),
            GuideEntry(AppIcons.Sparkle, "ИИ-помощник", "Нужен ключ: Настройки → ИИ-помощник. В книге: выдели текст → ИИ", "нейросеть ключ"),
            GuideEntry(AppIcons.Sparkle, "Пересказ, тест, герои", "Опции → Панель → «ИИ в панели»", "пересказ тест герой учитель"),
        ),
    ),
    GuideGroup(
        "Приложение", AppIcons.Options,
        listOf(
            GuideEntry(AppIcons.Palette, "Шаблоны оформления", "Настройки → Кастомизация", "тема цвет стиль"),
            GuideEntry(AppIcons.Sun, "Время суток и сезоны", "Настройки → Кастомизация, под шаблонами", "снег осень ночь"),
            GuideEntry(AppIcons.Cloud, "Синхронизация", "Настройки → Синхронизация → Создать код, на втором устройстве — Ввести код", "телефон планшет перенос"),
            GuideEntry(AppIcons.Backup, "Резервная копия", "Настройки → Резервная копия", "экспорт файл"),
            GuideEntry(AppIcons.Calendar, "Итоги года", "Статистика → «Итоги»", "год статистика"),
            GuideEntry(AppIcons.Grid, "Виджеты", "Долгое нажатие на рабочем столе → Виджеты → WikiFlerty: Чтение, Цитата дня, Слово дня", "рабочий стол"),
        ),
    ),
)

private fun markQuery(text: String, q: String, accent: Color): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    append(text)
    if (q.isEmpty()) return@buildAnnotatedString
    val lower = text.lowercase()
    var at = lower.indexOf(q)
    while (at >= 0) {
        addStyle(androidx.compose.ui.text.SpanStyle(color = accent, background = accent.copy(alpha = 0.18f), fontWeight = FontWeight.Bold), at, at + q.length)
        at = lower.indexOf(q, at + q.length)
    }
}

@Composable
fun GuideContent(onTour: () -> Unit) {
    var query by remember { mutableStateOf(SettingsNav.guideQuery.also { SettingsNav.guideQuery = "" }) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SearchField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Что ищешь? Например, «перевод»")
        val q = query.trim().lowercase()
        GuideGroups.forEach { group ->
            val shown = if (q.isEmpty()) group.entries else group.entries.filter { (it.title + " " + it.how + " " + it.keys).lowercase().contains(q) }
            AnimatedVisibility(shown.isNotEmpty(), enter = fadeIn(Motion.fade(200)) + expandVertically(Motion.tween(240)), exit = fadeOut(Motion.fade(140)) + shrinkVertically(Motion.tween(200))) {
                TabbedFrame(group.title, icon = group.icon, inset = false) {
                    shown.forEachIndexed { i, e ->
                        if (i > 0) ListDivider(Modifier.padding(horizontal = 14.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingIcon(e.icon, 32.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val mark = AppColors.Accent
                                Text(markQuery(e.title, q, mark), color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(markQuery(e.how, q, mark), color = AppColors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
                                if (q.isNotEmpty() && !e.title.lowercase().contains(q) && !e.how.lowercase().contains(q)) {
                                    e.keys.split(' ').firstOrNull { it.lowercase().contains(q) }?.let { key ->
                                        Text(markQuery(key, q, mark), color = AppColors.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        AppButton("Пройти знакомство заново", primary = false, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = onTour)
    }
}
