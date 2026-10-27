package com.flerty.wikiflerty.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.liveClick
import com.flerty.wikiflerty.ui.panelSurface
import org.readium.r2.shared.publication.Locator

data class SearchHit(
    val locator: Locator,
    val chapter: String?,
    val before: String,
    val match: String,
    val after: String,
)

class SearchState {
    var query by mutableStateOf("")
    val results = mutableStateListOf<SearchHit>()
    var running by mutableStateOf(false)
    var searched by mutableStateOf(false)
    var limited by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var current by mutableIntStateOf(-1)
}

class SearchActions(
    val state: SearchState,
    val onSearch: (String) -> Unit,
    val onOpen: (Int) -> Unit,
    val onClear: () -> Unit,
)

data class FootnoteInfo(val text: String, val url: String)

@Composable
internal fun SearchContent(search: SearchActions, modifier: Modifier) {
    val state = search.state
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        if (state.query.isBlank()) runCatching { focus.requestFocus() }
    }
    val submit = {
        if (state.query.isNotBlank()) {
            keyboard?.hide()
            search.onSearch(state.query.trim())
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(appCorner(14))
                .background(AppColors.Field)
                .border(1.dp, AppColors.Line, appCorner(14))
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(AppIcons.Search, null, tint = AppColors.Muted, modifier = Modifier.size(20.dp))
            BasicTextField(
                value = state.query,
                onValueChange = { state.query = it },
                singleLine = true,
                textStyle = TextStyle(color = AppColors.Text, fontSize = 16.sp),
                cursorBrush = SolidColor(AppColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box {
                        if (state.query.isEmpty()) Text("Слово или фраза", color = AppColors.Faint, fontSize = 16.sp)
                        inner()
                    }
                },
            )
            if (state.query.isNotEmpty()) {
                Box(
                    Modifier.size(42.dp).liveClick(CircleShape, sound = FeedbackSound.Close) {
                        state.query = ""
                        search.onClear()
                        runCatching { focus.requestFocus() }
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Close, "Очистить", tint = AppColors.Muted, modifier = Modifier.size(18.dp)) }
            }
            Box(
                Modifier
                    .size(42.dp)
                    .liveClick(CircleShape, sound = FeedbackSound.Open) { submit() }
                    .background(if (state.query.isNotBlank()) AppColors.Accent else AppColors.Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Send, "Искать", tint = AppColors.OnAccent, modifier = Modifier.size(18.dp)) }
        }

        val status = when {
            state.error != null -> state.error
            state.running -> "Ищу… найдено ${state.results.size}"
            state.searched && state.results.isEmpty() -> "Ничего не найдено"
            state.searched && state.limited -> "Показаны первые ${state.results.size} совпадений"
            state.searched -> "Найдено: ${state.results.size}"
            else -> null
        }
        if (status != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.running) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = AppColors.Accent)
                Text(status, color = AppColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }

        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(state.results) { index, hit ->
                val shape = appCorner(12)
                val active = index == state.current
                Column(
                    Modifier
                        .fillMaxWidth()
                        .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f) { search.onOpen(index) }
                        .then(if (active) Modifier.background(AppColors.AccentSoft, shape) else Modifier)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (!hit.chapter.isNullOrBlank()) {
                        Text(hit.chapter, color = AppColors.Faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        snippet(hit),
                        color = AppColors.Text,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun snippet(hit: SearchHit) = buildAnnotatedString {
    val before = hit.before.replace(Regex("\\s+"), " ").takeLast(70)
    val after = hit.after.replace(Regex("\\s+"), " ").take(110)
    if (before.isNotEmpty()) {
        append("…")
        append(before)
    }
    withStyle(SpanStyle(color = AppColors.Accent, fontWeight = FontWeight.Bold, background = AppColors.AccentSoft)) {
        append(hit.match)
    }
    append(after)
    if (after.isNotEmpty()) append("…")
}

@Composable
internal fun SearchNavBar(search: SearchActions, onReopen: () -> Unit, modifier: Modifier) {
    val state = search.state
    val total = state.results.size
    Row(
        modifier
            .panelSurface(appCorner(24), elevation = 12.dp)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(AppIcons.Back, "Предыдущее", sound = FeedbackSound.Tap) {
            if (total > 0) search.onOpen((state.current - 1 + total) % total)
        }
        Text(
            "${state.current + 1} из $total",
            color = AppColors.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .liveClick(appCorner(12), sound = FeedbackSound.Open, onClick = onReopen)
                .padding(horizontal = 10.dp, vertical = 10.dp),
        )
        RoundIconButton(AppIcons.Chevron, "Следующее", sound = FeedbackSound.Tap) {
            if (total > 0) search.onOpen((state.current + 1) % total)
        }
        RoundIconButton(AppIcons.Close, "Закрыть поиск", sound = FeedbackSound.Close) { search.onClear() }
    }
}

@Composable
internal fun FootnotePopup(note: FootnoteInfo, onGo: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .padding(horizontal = 12.dp)
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .panelSurface(appCorner(22), elevation = 18.dp)
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Note, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
            Text(
                "Сноска",
                color = AppColors.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            RoundIconButton(AppIcons.Close, "Закрыть", sound = FeedbackSound.Close, onClick = onClose)
        }
        Text(
            note.text,
            color = AppColors.Text,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier
                .padding(end = 10.dp)
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState()),
        )
        Text(
            "Открыть в книге ›",
            color = AppColors.Accent,
            fontSize = 13.sp,
            modifier = Modifier
                .liveClick(appCorner(8), sound = FeedbackSound.Open, onClick = onGo)
                .padding(vertical = 6.dp),
        )
    }
}
