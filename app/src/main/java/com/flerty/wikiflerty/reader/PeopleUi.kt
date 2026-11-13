package com.flerty.wikiflerty.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.data.BookCharacter
import com.flerty.wikiflerty.ui.ActionLabel
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.AppStyle
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.KeepHostBars
import com.flerty.wikiflerty.ui.Motion
import com.flerty.wikiflerty.ui.Reveal
import com.flerty.wikiflerty.ui.SheetHeader
import com.flerty.wikiflerty.ui.accentContent
import com.flerty.wikiflerty.ui.accentHighlight
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.fieldSurface
import com.flerty.wikiflerty.ui.liveClick
import com.flerty.wikiflerty.ui.panelSurface
import com.flerty.wikiflerty.ui.revealScroll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PersonDraft(val id: String?, val name: String, val note: String)

class PeopleActions(
    val onSave: (PersonDraft) -> Unit,
    val onDelete: (String) -> Unit,
    val onUpdate: (BookCharacter) -> Unit = {},
    val onAvatar: (String, android.net.Uri?) -> Unit = { _, _ -> },
    val onAi: (String) -> Unit = {},
)

@Composable
internal fun PeopleList(people: List<BookCharacter>, focus: String?, onAdd: () -> Unit, onEdit: (BookCharacter) -> Unit, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    LaunchedEffect(focus, people.size) {
        val index = people.indexOfFirst { it.id == focus }
        if (index >= 0) list.animateScrollToItem(index + 1)
    }
    LazyColumn(modifier, state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "add") {
            val shape = appCorner(16)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onAdd)
                    .accentHighlight(shape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(AppIcons.Plus, null, tint = accentContent, modifier = Modifier.size(20.dp))
                Text("Добавить персонажа", color = accentContent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(start = 10.dp))
            }
        }
        if (people.isEmpty()) {
            item(key = "empty") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(AppIcons.Person, null, tint = AppColors.Faint, modifier = Modifier.size(20.dp))
                    Text("Выдели имя в тексте → «Персонаж»", color = AppColors.Muted, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
        items(people, key = { it.id }) { person ->
            Reveal { PersonCard(person, person.id == focus) { onEdit(person) } }
        }
    }
}

@Composable
private fun PersonCard(person: BookCharacter, focused: Boolean, onClick: () -> Unit) {
    val shape = appCorner(16)
    val edge by animateColorAsState(if (focused) AppColors.Accent else AppColors.PanelBorder.copy(alpha = 0f), Motion.tween(320), label = "person-edge")
    Row(
        Modifier
            .fillMaxWidth()
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick)
            .fieldSurface(shape, selected = focused)
            .border(1.5.dp, edge, shape)
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PersonAvatar(person, 42.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(person.name, color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            RoleBadge(person.role)
            if (person.note.isNotBlank()) {
                com.flerty.wikiflerty.ui.ExpandingText(person.note, expanded = focused, lines = 4, color = AppColors.Text.copy(alpha = 0.82f), fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.fillMaxWidth())
            }
        }
        Icon(AppIcons.Chevron, null, tint = AppColors.Faint, modifier = Modifier.padding(top = 3.dp).size(16.dp))
    }
}

@Composable
internal fun PersonPopup(person: BookCharacter, onEdit: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .padding(horizontal = 12.dp)
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .panelSurface(appCorner(22), elevation = 18.dp)
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(person, 40.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    person.name,
                    color = AppColors.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                RoleBadge(person.role)
            }
            RoundIconButton(AppIcons.Person, "Профиль", sound = FeedbackSound.Open, onClick = onEdit)
            RoundIconButton(AppIcons.Close, "Закрыть", sound = FeedbackSound.Close, onClick = onClose)
        }
        if (person.note.isNotBlank()) {
            Text(
                person.note,
                color = AppColors.Text,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(end = 10.dp).heightIn(max = 260.dp).revealScroll(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonSheet(draft: PersonDraft, onDismiss: () -> Unit, actions: PeopleActions) {
    var name by remember { mutableStateOf(TextFieldValue(draft.name, TextRange(draft.name.length))) }
    var note by remember { mutableStateOf(TextFieldValue(draft.note, TextRange(draft.note.length))) }
    var confirmDelete by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    val noteFocus = remember { FocusRequester() }
    val hideInput = remember { arrayOfNulls<() -> Unit>(1) }
    val corner = (26 * AppStyle.corners).dp
    com.flerty.wikiflerty.ui.SheetWindow(onDismiss, onClosing = { hideInput[0]?.invoke() }) { control ->
        val close = control.close
        val innerFocus = LocalFocusManager.current
        val innerKeyboard = LocalSoftwareKeyboardController.current
        hideInput[0] = {
            innerFocus.clearFocus(force = true)
            innerKeyboard?.hide()
        }
        LaunchedEffect(Unit) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching {
                if (draft.name.isBlank() || draft.id != null && draft.note.isNotBlank()) nameFocus.requestFocus() else noteFocus.requestFocus()
                innerKeyboard?.show()
            }
        }
        val ready = name.text.isNotBlank()
        val save = {
            if (ready) {
                actions.onSave(PersonDraft(draft.id, name.text.trim(), note.text.trim()))
                close()
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = corner, topEnd = corner))
                .background(AppColors.Panel)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(if (draft.id == null) "Новый персонаж" else "Персонаж", close)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).fieldSurface(appCorner(18)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Person, null, tint = AppColors.Muted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (name.text.isEmpty()) Text("Имя", color = AppColors.Faint, fontSize = 19.sp, maxLines = 1)
                    BasicTextField(
                        value = name,
                        onValueChange = { v -> name = v.copy(text = v.text.replace("\n", " ").take(80)) },
                        singleLine = true,
                        textStyle = TextStyle(color = AppColors.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(AppColors.Accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onNext = { runCatching { noteFocus.requestFocus() } }),
                        modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                    )
                }
            }
            Box(
                Modifier.fillMaxWidth().fieldSurface(appCorner(18)).padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                if (note.text.isEmpty()) Text("Кто это", color = AppColors.Faint, fontSize = 16.sp, maxLines = 1)
                BasicTextField(
                    value = note,
                    onValueChange = { v -> note = v.copy(text = v.text.take(3000)) },
                    minLines = 3,
                    maxLines = 7,
                    textStyle = TextStyle(color = AppColors.Text, fontSize = 16.sp, lineHeight = 22.sp),
                    cursorBrush = SolidColor(AppColors.Accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().focusRequester(noteFocus),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (draft.id != null) {
                    val shape = appCorner(16)
                    Box(
                        Modifier
                            .height(52.dp)
                            .then(if (confirmDelete) Modifier.weight(1f) else Modifier.width(52.dp))
                            .liveClick(shape, sound = FeedbackSound.Toggle) {
                                if (confirmDelete) {
                                    actions.onDelete(draft.id)
                                    close()
                                } else {
                                    confirmDelete = true
                                }
                            }
                            .fieldSurface(shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(confirmDelete, transitionSpec = { fadeIn(Motion.fade(200)) togetherWith fadeOut(Motion.fade(140)) }, label = "person-delete") { sure ->
                            if (sure) ActionLabel("Удалить", AppColors.Bad) else Icon(AppIcons.Trash, "Удалить", tint = AppColors.Muted, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                val shape = appCorner(16)
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .liveClick(shape, sound = FeedbackSound.Toggle) { save() }
                        .accentHighlight(shape),
                    contentAlignment = Alignment.Center,
                ) { ActionLabel("Сохранить", accentContent) }
            }
        }
    }
}
