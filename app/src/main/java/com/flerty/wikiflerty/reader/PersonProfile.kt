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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    AppSheet(onDismiss) {
        val close = sheetClose(onDismiss)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box {
                PersonAvatar(person, 84.dp, Modifier.liveClick(CircleShape, sound = FeedbackSound.Open, pressedScale = 0.94f) { pick.launch("image/*") })
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    Modifier.liveClick(appCorner(10), sound = FeedbackSound.Open, pressedScale = 0.98f) {
                        field = ProfileField("Имя", "Имя персонажа", person.name, 80, false) { c, v -> if (v.isBlank()) c else c.copy(name = v) }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(person.name, color = AppColors.Text, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(AppIcons.Pencil, null, tint = AppColors.Faint, modifier = Modifier.size(14.dp))
                }
                RoleBadge(person.role)
                home?.let { Text(it.title, color = AppColors.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            SmallAction(AppIcons.Close, "Закрыть", FeedbackSound.Close, onClick = close)
        }

        val aiShape = appCorner(16)
        Row(
            Modifier
                .fillMaxWidth()
                .sheetReveal()
                .height(50.dp)
                .liveClick(aiShape, sound = FeedbackSound.Open, pressedScale = 0.98f) { if (!aiBusy) actions.onAi(person.id) }
                .accentHighlight(aiShape)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            AnimatedContent(aiBusy, transitionSpec = { fadeIn(Motion.fade(200)) togetherWith fadeOut(Motion.fade(140)) }, label = "person-ai") { busy ->
                if (busy) KitSpinner(Modifier.size(20.dp), accentContent) else Icon(AppIcons.Sparkle, null, tint = accentContent, modifier = Modifier.size(20.dp))
            }
            FitText(if (aiBusy) "ИИ читает прочитанное…" else if (person.filled >= 4) "Обновить с ИИ" else "Заполнить с ИИ", accentContent, 15.sp, weight = FontWeight.SemiBold)
        }
        if (aiError != null) HintNote(aiError, AppIcons.Alert)

        TabbedFrame("Роль", icon = AppIcons.Star) {
            FlowRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CharacterRole.entries.forEach { r ->
                    RoleChip(r.title, roleIcon(r.id), person.role == r.id) { actions.onUpdate(person.copy(role = if (person.role == r.id) "" else r.id)) }
                }
                val own = person.role.isNotBlank() && CharacterRole.of(person.role) == null
                RoleChip(if (own) person.role else "Своя", AppIcons.Pencil, own) { customRole = true }
            }
        }

        ProfileSection("Кто это", AppIcons.Person, person.note, "Добавить описание") {
            field = ProfileField("Кто это", "Пара слов о персонаже", person.note, 3000, true) { c, v -> c.copy(note = v) }
        }
        ProfileSection("Внешность", AppIcons.Eye, person.looks, "Как он выглядит") {
            field = ProfileField("Внешность", "Как он выглядит", person.looks, 2000, true) { c, v -> c.copy(looks = v) }
        }
        ProfileSection("Отношения", AppIcons.Heart, person.ties, "С кем и как связан") {
            field = ProfileField("Отношения", "Имя — кем приходится", person.ties, 3000, true) { c, v -> c.copy(ties = v) }
        }
        val fateLocked = person.fateHidden && !fateShown && person.fate.isNotBlank()
        TabbedFrame(
            "Судьба",
            icon = AppIcons.Target,
            trailing = {
                SmallAction(if (person.fateHidden) AppIcons.EyeOff else AppIcons.Eye, "Скрывать как спойлер", if (person.fateHidden) FeedbackSound.Uncheck else FeedbackSound.Check, active = person.fateHidden) {
                    actions.onUpdate(person.copy(fateHidden = !person.fateHidden))
                }
            },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .liveClick(appCorner(10), sound = FeedbackSound.Open, pressedScale = 0.99f) {
                        if (fateLocked) fateShown = true
                        else field = ProfileField("Судьба", "Что с ним стало", person.fate, 3000, true) { c, v -> c.copy(fate = v) }
                    }
                    .padding(vertical = 10.dp),
            ) {
                AnimatedContent(fateLocked, transitionSpec = { fadeIn(Motion.fade(240)) togetherWith fadeOut(Motion.fade(140)) }, label = "fate") { locked ->
                    when {
                        locked -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(AppIcons.EyeOff, null, tint = AppColors.Accent, modifier = Modifier.size(16.dp))
                            Text("Спойлер — нажми, чтобы открыть", color = AppColors.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        person.fate.isBlank() -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(AppIcons.Pencil, null, tint = AppColors.Faint, modifier = Modifier.size(14.dp))
                            Text("Что с ним стало", color = AppColors.Faint, fontSize = 14.sp, maxLines = 1)
                        }
                        else -> Text(person.fate, color = AppColors.Text, fontSize = 14.5.sp, lineHeight = 21.sp)
                    }
                }
            }
        }

        TabbedFrame("Цитаты", icon = AppIcons.Quote) {
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

        TabbedFrame("Встречается в", icon = AppIcons.Series) {
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                linked.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SmallCover(app.library.coverFile(b), Modifier.size(width = 30.dp, height = 45.dp), title = b.title, author = b.author)
                        Text(b.title, color = AppColors.Text, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        SmallAction(AppIcons.Close, "Отвязать книгу", FeedbackSound.Uncheck) { actions.onUpdate(person.copy(books = person.books - b.statsKey)) }
                    }
                }
                ProfileButton("Привязать книгу", AppIcons.Plus) { linking = true }
            }
        }

        val dangerShape = appCorner(16)
        val danger = AppColors.Bad
        Box(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).sheetReveal()
                .liveClick(dangerShape, sound = FeedbackSound.Trash) { confirmDelete = true }
                .border(1.dp, danger.copy(alpha = 0.45f), dangerShape),
            contentAlignment = Alignment.Center,
        ) { ActionLabel("Удалить персонажа", danger, fontSize = 14.sp, iconSize = 21.dp, icon = AppIcons.Trash) }
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
