package com.flerty.wikiflerty.reader

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.Book
import com.flerty.wikiflerty.data.BookCharacter
import com.flerty.wikiflerty.data.CharacterRole
import com.flerty.wikiflerty.ui.ActionLabel
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.AppSheet
import com.flerty.wikiflerty.ui.ConfirmSheet
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.FitText
import com.flerty.wikiflerty.ui.HintNote
import com.flerty.wikiflerty.ui.KitSpinner
import com.flerty.wikiflerty.ui.Motion
import com.flerty.wikiflerty.ui.SmallCover
import com.flerty.wikiflerty.ui.TabbedFrame
import com.flerty.wikiflerty.ui.TextInputSheet
import com.flerty.wikiflerty.ui.accentContent
import com.flerty.wikiflerty.ui.accentHighlight
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.fieldSurface
import com.flerty.wikiflerty.ui.liveClick
import com.flerty.wikiflerty.ui.sheetClose
import com.flerty.wikiflerty.ui.sheetReveal
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

fun roleIcon(role: String): ImageVector = when (CharacterRole.of(role)) {
    CharacterRole.Main -> AppIcons.StarFilled
    CharacterRole.Love -> AppIcons.HeartFilled
    CharacterRole.Villain -> AppIcons.Fire
    CharacterRole.Support -> AppIcons.Person
    else -> AppIcons.Person
}

@Composable
internal fun PersonAvatar(person: BookCharacter, size: Dp, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as App
    val image by produceState<ImageBitmap?>(null, person.id, person.avatarAt) {
        value = withContext(Dispatchers.IO) {
            val file = app.characters.avatar(person.id)
            if (file.exists()) runCatching { android.graphics.BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull() else null
        }
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(AppColors.Accent.copy(alpha = 0.32f), AppColors.Accent.copy(alpha = 0.12f))))
            .border(1.5.dp, AppColors.Accent.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = image
        if (bmp != null) {
            Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                person.name.trim().take(1).uppercase(),
                color = AppColors.Accent,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun RoleBadge(role: String, modifier: Modifier = Modifier) {
    if (role.isBlank()) return
    Row(
        modifier.clip(appCorner(9)).background(AppColors.AccentSoft).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(roleIcon(role), null, tint = AppColors.Accent, modifier = Modifier.size(11.dp))
        Text(CharacterRole.title(role), color = AppColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RoleChip(title: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val shape = appCorner(13)
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.94f, onClick = onClick)
            .then(if (selected) Modifier.accentHighlight(shape) else Modifier.fieldSurface(shape))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = if (selected) accentContent else AppColors.Muted, modifier = Modifier.size(14.dp))
        Text(title, color = if (selected) accentContent else AppColors.Text, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun SmallAction(icon: ImageVector, description: String, sound: FeedbackSound = FeedbackSound.Toggle, active: Boolean = false, onClick: () -> Unit) {
    val shape = appCorner(11)
    Box(
        Modifier
            .size(34.dp)
            .liveClick(shape, sound = sound, pressedScale = 0.86f, onClick = onClick)
            .then(if (active) Modifier.clip(shape).background(AppColors.AccentSoft) else Modifier)
            .border(1.dp, if (active) AppColors.Accent.copy(alpha = 0.4f) else AppColors.PanelBorder, shape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = if (active) AppColors.Accent else AppColors.Muted, modifier = Modifier.size(16.dp)) }
}

@Composable
private fun ProfileSection(title: String, icon: ImageVector, text: String, hint: String, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    TabbedFrame(title, icon = icon, trailing = trailing) {
        Box(
            Modifier
                .fillMaxWidth()
                .liveClick(appCorner(10), sound = FeedbackSound.Open, pressedScale = 0.99f, onClick = onClick)
                .padding(vertical = 10.dp),
        ) {
            if (text.isBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(AppIcons.Pencil, null, tint = AppColors.Faint, modifier = Modifier.size(14.dp))
                    Text(hint, color = AppColors.Faint, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text(text, color = AppColors.Text, fontSize = 14.5.sp, lineHeight = 21.sp)
            }
        }
    }
}

private class ProfileField(val title: String, val hint: String, val value: String, val max: Int, val multiline: Boolean, val apply: (BookCharacter, String) -> BookCharacter)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonProfileSheet(
    person: BookCharacter,
    homeBookId: String,
    aiBusy: Boolean,
    aiError: String?,
    actions: PeopleActions,
    onDismiss: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as App
    val books by app.library.books.collectAsStateWithLifecycle()
    var field by remember { mutableStateOf<ProfileField?>(null) }
    var fateShown by remember(person.id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }
    var customRole by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> if (uri != null) actions.onAvatar(person.id, uri) }
    val hasAvatar = remember(person.id, person.avatarAt) { app.characters.avatar(person.id).exists() }
    val linked = remember(books, person.books) { person.books.mapNotNull { key -> books.firstOrNull { it.statsKey == key } } }
    val home = remember(books, person.bookId) { books.firstOrNull { it.id == person.bookId } }
    val people by app.characters.characters.collectAsStateWithLifecycle()
    val ties = remember(person.ties, people) { parseTies(person.ties, people.filter { it.id != person.id }) }
    var tab by remember(person.id) { mutableIntStateOf(0) }
    AppSheet(onDismiss) {
        val close = sheetClose(onDismiss)
        val editName = {
            field = ProfileField("Имя", "Имя персонажа", person.name, 80, false) { c, v -> if (v.isBlank()) c else c.copy(name = v) }
        }
        val editNote = {
            field = ProfileField("Кто это", "Пара слов о персонаже", person.note, 3000, true) { c, v -> c.copy(note = v) }
        }
        ProfileCover(person, onClose = close)
        Row(Modifier.fillMaxWidth().offset(y = (-58).dp), verticalAlignment = Alignment.Top) {
            Box {
                Box(Modifier.size(92.dp).clip(CircleShape).background(AppColors.Panel), contentAlignment = Alignment.Center) {
                    PersonAvatar(person, 84.dp, Modifier.liveClick(CircleShape, sound = FeedbackSound.Open, pressedScale = 0.94f) { pick.launch("image/*") })
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .liveClick(CircleShape, sound = if (hasAvatar) FeedbackSound.Trash else FeedbackSound.Open, pressedScale = 0.85f) {
                            if (hasAvatar) actions.onAvatar(person.id, null) else pick.launch("image/*")
                        }
                        .background(AppColors.Panel)
                        .border(1.dp, AppColors.PanelBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (hasAvatar) AppIcons.Close else AppIcons.Camera, if (hasAvatar) "Убрать фото" else "Выбрать фото", tint = AppColors.Muted, modifier = Modifier.size(14.dp)) }
            }
            Box(Modifier.weight(1f))
            Row(Modifier.padding(top = 70.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallAction(AppIcons.Pencil, "Изменить имя", FeedbackSound.Open, onClick = editName)
                val aiShape = appCorner(12)
                Row(
                    Modifier
                        .height(34.dp)
                        .liveClick(aiShape, sound = FeedbackSound.Open, pressedScale = 0.94f) { if (!aiBusy) actions.onAi(person.id) }
                        .accentHighlight(aiShape)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AnimatedContent(aiBusy, transitionSpec = { fadeIn(Motion.fade(200)) togetherWith fadeOut(Motion.fade(140)) }, label = "person-ai") { busy ->
                        if (busy) KitSpinner(Modifier.size(15.dp), accentContent) else Icon(AppIcons.Sparkle, null, tint = accentContent, modifier = Modifier.size(15.dp))
                    }
                    Text("ИИ", color = accentContent, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        Column(Modifier.offset(y = (-58).dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.liveClick(appCorner(10), sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = editName), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(person.name, color = AppColors.Text, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (person.role.isNotBlank()) Icon(roleIcon(person.role), null, tint = AppColors.Accent, modifier = Modifier.size(15.dp))
            }
            val sub = listOfNotNull(person.role.takeIf { it.isNotBlank() }?.let { "@" + CharacterRole.title(it).lowercase().replace(' ', '_') }, home?.title).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, color = AppColors.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.fillMaxWidth().liveClick(appCorner(10), sound = FeedbackSound.Open, pressedScale = 0.99f, onClick = editNote).padding(vertical = 2.dp)) {
                if (person.note.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(AppIcons.Plus, null, tint = AppColors.Faint, modifier = Modifier.size(14.dp))
                        Text("Кто это", color = AppColors.Faint, fontSize = 14.sp, maxLines = 1)
                    }
                } else {
                    Text(person.note, color = AppColors.Text, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 2.dp)) {
                ProfileCount(person.quotes.size, "цитаты")
                ProfileCount(ties.size, "связи")
                ProfileCount(linked.size + 1, "книги")
            }
            if (aiError != null) HintNote(aiError, AppIcons.Alert)
        }
        Column(Modifier.offset(y = (-58).dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileTabs(listOf("О нём", "Связи", "Цитаты"), tab) { tab = it }
            AnimatedContent(
                tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(Motion.fade(220)) + androidx.compose.animation.slideInHorizontally(Motion.tween(300)) { it / 6 * dir }) togetherWith
                        (fadeOut(Motion.fade(140)) + androidx.compose.animation.slideOutHorizontally(Motion.tween(260)) { -it / 6 * dir }) using
                        androidx.compose.animation.SizeTransform(clip = false) { _, _ -> Motion.tween(300) }
                },
                label = "person-tab",
            ) { page ->
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (page) {
                        0 -> {
                            ProfileLabel("Роль")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                CharacterRole.entries.forEach { r ->
                                    RoleChip(r.title, roleIcon(r.id), person.role == r.id) { actions.onUpdate(person.copy(role = if (person.role == r.id) "" else r.id)) }
                                }
                                val own = person.role.isNotBlank() && CharacterRole.of(person.role) == null
                                RoleChip(if (own) person.role else "Своя", AppIcons.Pencil, own) { customRole = true }
                            }
                            ProfileText("Внешность", person.looks, "Как он выглядит") {
                                field = ProfileField("Внешность", "Как он выглядит", person.looks, 2000, true) { c, v -> c.copy(looks = v) }
                            }
                            val fateLocked = person.fateHidden && !fateShown && person.fate.isNotBlank()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProfileLabel("Судьба", Modifier.weight(1f))
                                SmallAction(if (person.fateHidden) AppIcons.EyeOff else AppIcons.Eye, "Скрывать как спойлер", if (person.fateHidden) FeedbackSound.Uncheck else FeedbackSound.Check, active = person.fateHidden) {
                                    actions.onUpdate(person.copy(fateHidden = !person.fateHidden))
                                }
                            }
                            FateText(person.fate, fateLocked) {
                                if (fateLocked) fateShown = true
                                else field = ProfileField("Судьба", "Что с ним стало", person.fate, 3000, true) { c, v -> c.copy(fate = v) }
                            }
                            ProfileLabel("Встречается в")
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                linked.forEach { b ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SmallCover(app.library.coverFile(b), Modifier.size(width = 30.dp, height = 45.dp), title = b.title, author = b.author)
                                        Text(b.title, color = AppColors.Text, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        SmallAction(AppIcons.Close, "Отвязать книгу", FeedbackSound.Uncheck) { actions.onUpdate(person.copy(books = person.books - b.statsKey)) }
                                    }
                                }
                                ProfileButton("Привязать книгу", AppIcons.Plus) { linking = true }
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4.dp).liveClick(appCorner(12), sound = FeedbackSound.Trash, pressedScale = 0.97f) { confirmDelete = true }.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) { ActionLabel("Удалить персонажа", AppColors.Bad, fontSize = 13.sp, iconSize = 18.dp, icon = AppIcons.Trash) }
                        }
                        1 -> {
                            if (ties.isEmpty()) {
                                Text("Связей пока нет", color = AppColors.Faint, fontSize = 14.sp)
                            } else {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ties.forEach { t -> TiePill(t) }
                                }
                            }
                            ProfileButton(if (ties.isEmpty()) "Добавить связи" else "Изменить связи", if (ties.isEmpty()) AppIcons.Plus else AppIcons.Pencil) {
                                field = ProfileField("Связи", "Имя — кем приходится, по строке", person.ties, 3000, true) { c, v -> c.copy(ties = v) }
                            }
                        }
                        else -> {
                            person.quotes.forEach { q ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(Modifier.weight(1f).height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(Modifier.width(3.dp).fillMaxSize().clip(appCorner(2)).background(AppColors.Accent))
                                        Text("«" + q.trim().trim('«', '»', '"') + "»", color = AppColors.Text, fontSize = 14.sp, lineHeight = 20.sp, fontStyle = FontStyle.Italic, modifier = Modifier.weight(1f).padding(vertical = 2.dp))
                                    }
                                    SmallAction(AppIcons.Trash, "Убрать цитату", FeedbackSound.Trash) { actions.onUpdate(person.copy(quotes = person.quotes - q)) }
                                }
                            }
                            ProfileButton("Добавить цитату", AppIcons.Plus) {
                                field = ProfileField("Цитата", "Реплика персонажа", "", 600, true) { c, v -> if (v.isBlank()) c else c.copy(quotes = (c.quotes + v).distinct()) }
                            }
                        }
                    }
                }
            }
        }
        field?.let { f ->
            TextInputSheet(f.title, f.value, f.hint, onDismiss = { field = null }, maxLength = f.max, multiline = f.multiline) { text ->
                actions.onUpdate(f.apply(person, text.trim()))
            }
        }
        if (customRole) {
            val own = person.role.takeIf { CharacterRole.of(it) == null }.orEmpty()
            TextInputSheet("Своя роль", own, "Например, наставник", onDismiss = { customRole = false }, icon = AppIcons.Pencil, maxLength = 40) { text ->
                actions.onUpdate(person.copy(role = text.trim()))
            }
        }
        if (linking) {
            BookLinkSheet(
                books = books.filter { it.id != person.bookId && it.statsKey !in person.books },
                series = (home ?: books.firstOrNull { it.id == homeBookId })?.series?.map { it.name.lowercase() }.orEmpty(),
                cover = { app.library.coverFile(it) },
                onPick = { b -> actions.onUpdate(person.copy(books = (person.books + b.statsKey).distinct())) },
                onDismiss = { linking = false },
            )
        }
        if (confirmDelete) {
            ConfirmSheet(
                title = "Удалить персонажа?",
                text = "Профиль и флажки в тексте исчезнут.",
                confirm = "Удалить",
                onConfirm = {
                    actions.onDelete(person.id)
                    onDismiss()
                },
                onDismiss = { confirmDelete = false },
            )
        }
    }
}

private class Tie(val name: String, val what: String, val person: BookCharacter?)

private fun parseTies(text: String, people: List<BookCharacter>): List<Tie> =
    text.split('\n', ';').map { it.trim().trimStart('-', '•', '—').trim() }.filter { it.isNotEmpty() }.take(24).map { line ->
        val parts = line.split(Regex("\\s+[—–-]\\s+|:\\s*"), limit = 2)
        val name = parts[0].trim().take(40)
        val what = parts.getOrNull(1)?.trim().orEmpty()
        Tie(name, what, people.firstOrNull { it.name.equals(name, true) || it.name.split(' ').firstOrNull()?.equals(name, true) == true })
    }

@Composable
private fun ProfileCover(person: BookCharacter, onClose: () -> Unit) {
    val accent = AppColors.Accent
    val panel = AppColors.Panel
    val quote = person.quotes.firstOrNull()?.trim()?.trim('«', '»', '"')
    Box(
        Modifier
            .layout { m, c ->
                val extra = 32.dp.roundToPx()
                val p = m.measure(c.copy(minWidth = c.maxWidth + extra, maxWidth = c.maxWidth + extra))
                layout(c.maxWidth, p.height - 16.dp.roundToPx()) { p.place(-extra / 2, -16.dp.roundToPx()) }
            }
            .fillMaxWidth()
            .height(124.dp)
            .background(
                Brush.linearGradient(
                    listOf(accent, androidx.compose.ui.graphics.lerp(accent, Color(0xFF6A3A6E), 0.6f), androidx.compose.ui.graphics.lerp(panel, accent, 0.18f)),
                    start = androidx.compose.ui.geometry.Offset.Zero,
                    end = androidx.compose.ui.geometry.Offset.Infinite,
                ),
            ),
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, panel.copy(alpha = 0.35f)))))
        if (!quote.isNullOrBlank()) {
            Text(
                "«" + quote + "»",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.align(Alignment.BottomEnd).padding(start = 130.dp, end = 16.dp, bottom = 10.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 14.dp)
                .size(34.dp)
                .liveClick(CircleShape, sound = FeedbackSound.Close, pressedScale = 0.86f, onClick = onClose)
                .background(Color.Black.copy(alpha = 0.28f)),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.Close, "Закрыть", tint = Color.White, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun ProfileCount(value: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("$value", color = AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = AppColors.Faint, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun ProfileTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val pos by androidx.compose.animation.core.animateFloatAsState(selected.toFloat(), Motion.tween(320), label = "person-tabs")
    val accent = AppColors.Accent
    val line = AppColors.PanelBorder
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            titles.forEachIndexed { i, t ->
                val on = i == selected
                val color by androidx.compose.animation.animateColorAsState(if (on) AppColors.Text else AppColors.Faint, Motion.fade(220), label = "person-tab-color")
                Box(
                    Modifier.weight(1f).height(40.dp).liveClick(appCorner(10), sound = FeedbackSound.Tap, pressedScale = 0.96f) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) { Text(t, color = color, fontSize = 13.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1) }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(2.dp).drawBehind {
                drawRect(line, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
                val w = size.width / titles.size
                drawRoundRect(accent, topLeft = androidx.compose.ui.geometry.Offset(w * pos + w * 0.18f, 0f), size = androidx.compose.ui.geometry.Size(w * 0.64f, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
            },
        )
    }
}

@Composable
private fun ProfileLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = AppColors.Faint, fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = modifier)
}

@Composable
private fun ProfileText(label: String, text: String, hint: String, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ProfileLabel(label)
        Box(Modifier.fillMaxWidth().liveClick(appCorner(10), sound = FeedbackSound.Open, pressedScale = 0.99f, onClick = onClick).padding(vertical = 2.dp)) {
            if (text.isBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(AppIcons.Plus, null, tint = AppColors.Muted, modifier = Modifier.size(14.dp))
                    Text(hint, color = AppColors.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text(text, color = AppColors.Text, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun FateText(fate: String, locked: Boolean, onClick: () -> Unit) {
    val blur by androidx.compose.animation.core.animateFloatAsState(if (locked) 1f else 0f, Motion.tween(420), label = "fate-blur")
    Box(Modifier.fillMaxWidth().liveClick(appCorner(10), sound = FeedbackSound.Open, pressedScale = 0.99f, onClick = onClick).padding(vertical = 2.dp)) {
        when {
            fate.isBlank() -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(AppIcons.Plus, null, tint = AppColors.Muted, modifier = Modifier.size(14.dp))
                Text("Что с ним стало", color = AppColors.Muted, fontSize = 14.sp, maxLines = 1)
            }
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> Text(
                fate,
                color = AppColors.Text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = if (locked) 2 else Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
                modifier = Modifier.graphicsLayer {
                    if (blur > 0.01f) {
                        val r = 9.dp.toPx() * blur
                        renderEffect = androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Decal)
                    }
                    alpha = 1f - 0.3f * blur
                },
            )
            locked -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(AppIcons.EyeOff, null, tint = AppColors.Accent, modifier = Modifier.size(16.dp))
                Text("Спойлер — нажми, чтобы открыть", color = AppColors.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            else -> Text(fate, color = AppColors.Text, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun TiePill(tie: Tie) {
    val shape = appCorner(20)
    Row(
        Modifier.clip(shape).fieldSurface(shape).padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (tie.person != null) {
            PersonAvatar(tie.person, 28.dp)
        } else {
            Box(Modifier.size(28.dp).clip(CircleShape).background(AppColors.AccentSoft), contentAlignment = Alignment.Center) {
                Text(tie.name.take(1).uppercase(), color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column {
            Text(tie.name, color = AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (tie.what.isNotBlank()) Text(tie.what, color = AppColors.Faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProfileButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    val shape = appCorner(12)
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.97f, onClick = onClick)
            .border(1.dp, AppColors.Accent.copy(alpha = 0.35f), shape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(17.dp))
        FitText(text, AppColors.Accent, 13.sp, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun BookLinkSheet(books: List<Book>, series: List<String>, cover: (Book) -> File?, onPick: (Book) -> Unit, onDismiss: () -> Unit) {
    val sorted = remember(books, series) {
        books.sortedWith(compareBy<Book> { b -> if (b.series.any { it.name.lowercase() in series }) 0 else 1 }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }
    AppSheet(onDismiss, title = "Привязать книгу") {
        val close = sheetClose(onDismiss)
        if (sorted.isEmpty()) {
            HintNote("Других книг в библиотеке нет", AppIcons.Info)
        }
        sorted.take(120).forEach { b ->
            val shape = appCorner(14)
            Row(
                Modifier
                    .fillMaxWidth()
                    .sheetReveal()
                    .liveClick(shape, sound = FeedbackSound.Check, pressedScale = 0.98f) {
                        onPick(b)
                        close()
                    }
                    .fieldSurface(shape)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallCover(cover(b), Modifier.size(width = 34.dp, height = 51.dp), title = b.title, author = b.author)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(b.title, color = AppColors.Text, fontSize = 14.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = b.series.firstOrNull()?.label ?: b.author
                    if (sub.isNotBlank()) Text(sub, color = AppColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

class PersonFound(val role: String, val who: String, val looks: String, val ties: String, val fate: String, val quotes: List<String>)

object PersonPassages {
    private fun stems(name: String): List<String> = name.lowercase().replace('ё', 'е').split(Regex("[^\\p{L}]+"))
        .filter { it.length >= 3 }
        .map { if (it.length > 5) it.dropLast(2) else if (it.length > 3) it.dropLast(1) else it }

    fun pick(text: String, name: String, budget: Int = 26_000): String {
        val keys = stems(name)
        if (keys.isEmpty()) return ""
        val paragraphs = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val hits = paragraphs.indices.filter { i ->
            val low = paragraphs[i].lowercase().replace('ё', 'е')
            keys.any { low.contains(it) }
        }
        if (hits.isEmpty()) return ""
        val order = hits.take(8) + hits.drop(8).reversed()
        val chosen = java.util.TreeSet<Int>()
        var used = 0
        for (i in order) {
            for (j in (i - 1)..(i + 1)) {
                if (j in paragraphs.indices && chosen.add(j)) used += minOf(paragraphs[j].length, 1400) + 1
            }
            if (used > budget) break
        }
        return chosen.joinToString("\n") { paragraphs[it].take(1400) }
    }

    fun parse(raw: String): PersonFound? = runCatching {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return@runCatching null
        val j = JSONObject(raw.substring(start, end + 1))
        fun text(key: String): String = when (val v = j.opt(key)) {
            null, JSONObject.NULL -> ""
            is org.json.JSONArray -> (0 until v.length()).joinToString("\n") { v.optString(it).trim() }
            else -> v.toString().trim()
        }
        val quotes = j.optJSONArray("quotes")?.let { a -> (0 until a.length()).map { a.optString(it).trim().trim('«', '»', '"') }.filter { it.length in 3..500 } }.orEmpty()
        val role = text("role").lowercase().let { r -> CharacterRole.entries.firstOrNull { r.contains(it.id) }?.id.orEmpty() }
        PersonFound(role, text("who"), text("looks"), text("ties"), text("fate"), quotes.take(3))
    }.getOrNull()
}
