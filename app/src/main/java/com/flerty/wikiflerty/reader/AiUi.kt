package com.flerty.wikiflerty.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.ai.AiAction
import com.flerty.wikiflerty.ai.AiManager
import com.flerty.wikiflerty.ai.AiPrompts
import com.flerty.wikiflerty.ai.AiProvider
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.fieldSurface
import com.flerty.wikiflerty.ui.liveClick
import com.flerty.wikiflerty.ui.markdown
import kotlinx.coroutines.Job
import org.readium.r2.shared.publication.Locator

/** One message in the AI chat. [shown] is what the user sees, [sent] is what goes to the model. */
data class AiBubble(val fromUser: Boolean, val shown: String, val sent: String)

/** The conversation about the selected text. */
class AiChatState {
    var action by mutableStateOf(AiAction.Explain)
    var fragment by mutableStateOf("")
    var context: String = ""
    var locator: Locator? = null
    val bubbles = mutableStateListOf<AiBubble>()
    var streaming by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var provider by mutableStateOf<AiProvider?>(null)
    var model by mutableStateOf("")
    var savedToDictionary by mutableStateOf(false)
    var savedToNotes by mutableStateOf(false)
    var job: Job? = null

    val lastAnswer: String?
        get() = bubbles.lastOrNull { !it.fromUser && it.shown.isNotBlank() }?.shown
}

/** Everything the AI screens can ask the reader to do. */
class AiBridge(
    val manager: AiManager,
    val chat: AiChatState,
    val onStart: (AiAction, String?) -> Unit,
    val onFollowUp: (String) -> Unit,
    val onStop: () -> Unit,
    val onRetry: () -> Unit,
    val onModel: (AiProvider, String) -> Unit,
    val onSaveDictionary: () -> Unit,
    val onSaveNote: () -> Unit,
)

// ---------------------------------------------------------------- choose an action

@Composable
fun AiMenuContent(bridge: AiBridge, onSetup: () -> Unit, onStarted: () -> Unit, modifier: Modifier = Modifier) {
    val keys by bridge.manager.vault.keys.collectAsStateWithLifecycle()
    val settings by bridge.manager.settings.collectAsStateWithLifecycle()
    val provider = bridge.manager.current()
    var asking by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        QuoteBox(bridge.chat.fragment, maxLines = 4)

        if (provider == null || keys.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().fieldSurface(RoundedCornerShape(16.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Нейросеть не подключена", color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Вставь API-ключ Claude, ChatGPT, Gemini или DeepSeek — один раз, и ИИ будет объяснять слова прямо в книге.",
                    color = AppColors.Muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                AccentButton("Подключить нейросеть", onClick = onSetup)
            }
            return@Column
        }

        AiAction.entries.forEach { action ->
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f) {
                        if (action == AiAction.Ask) {
                            asking = !asking
                        } else {
                            bridge.onStart(action, null)
                            onStarted()
                        }
                    }
                    .fieldSurface(shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Brush.linearGradient(listOf(Color(0x478FB3FF), Color(0x1A8FB3FF)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(action.mark, color = AppColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(action.title, color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(action.hint, color = AppColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (action == AiAction.Ask) {
                AnimatedVisibility(visible = asking, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    MessageInput(
                        value = question,
                        onChange = { question = it },
                        placeholder = "Твой вопрос об этом отрывке…",
                        streaming = false,
                        onSend = {
                            if (question.isNotBlank()) {
                                bridge.onStart(AiAction.Ask, question)
                                question = ""
                                asking = false
                                onStarted()
                            }
                        },
                        onStop = {},
                    )
                }
            }
        }
        Text(
            "${provider.title} · ${settings.modelFor(provider)}",
            color = AppColors.Faint,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

// ---------------------------------------------------------------- the chat

@Composable
fun AiChatContent(bridge: AiBridge, modifier: Modifier = Modifier) {
    val chat = bridge.chat
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    val lastText = chat.bubbles.lastOrNull()?.shown

    // Keep the newest text in view while it is being written.
    LaunchedEffect(chat.bubbles.size, lastText?.length) {
        if (chat.bubbles.isNotEmpty()) listState.scrollToItem(chat.bubbles.size - 1, Int.MAX_VALUE / 2)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ModelChip(bridge)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(chat.bubbles) { index, bubble ->
                if (bubble.fromUser) {
                    UserBubble(bubble, first = index == 0, fragment = chat.fragment)
                } else {
                    AnswerBubble(bubble.shown, writing = chat.streaming && index == chat.bubbles.lastIndex)
                }
            }
            chat.error?.let { error ->
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x22F08A8A))
                            .border(1.dp, Color(0x55F08A8A), RoundedCornerShape(14.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(error, color = Color(0xFFF5B4B4), fontSize = 13.sp, lineHeight = 18.sp)
                        Text(
                            "Повторить",
                            color = AppColors.Accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.liveClick(RoundedCornerShape(8.dp), onClick = bridge.onRetry).padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }

        val hasAnswer = chat.lastAnswer != null && !chat.streaming
        AnimatedVisibility(visible = hasAnswer, enter = fadeIn(tween(220)) + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiPrompts.followUps.forEach { (label, prompt) ->
                        val shape = RoundedCornerShape(18.dp)
                        Box(
                            Modifier.height(34.dp)
                                .liveClick(shape, sound = FeedbackSound.Tap) { bridge.onFollowUp(prompt) }
                                .border(1.dp, AppColors.Line, shape)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, color = AppColors.Text, fontSize = 13.sp) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentButton(
                        if (chat.savedToDictionary) "✓ В словаре" else "В словарь",
                        enabled = !chat.savedToDictionary,
                        modifier = Modifier.weight(1f),
                        onClick = bridge.onSaveDictionary,
                    )
                    OutlineButton(
                        if (chat.savedToNotes) "✓ В заметках" else "В заметку",
                        enabled = !chat.savedToNotes,
                        modifier = Modifier.weight(1f),
                        onClick = bridge.onSaveNote,
                    )
                }
            }
        }

        MessageInput(
            value = input,
            onChange = { input = it },
            placeholder = "Спросить ещё…",
            streaming = chat.streaming,
            onSend = {
                if (input.isNotBlank()) {
                    bridge.onFollowUp(input.trim())
                    input = ""
                }
            },
            onStop = bridge.onStop,
        )
    }
}

@Composable
private fun ModelChip(bridge: AiBridge) {
    val settings by bridge.manager.settings.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val chat = bridge.chat
    Box {
        val shape = RoundedCornerShape(16.dp)
        Row(
            Modifier.height(32.dp)
                .liveClick(shape, sound = FeedbackSound.Open) { open = true }
                .background(AppColors.Field)
                .border(1.dp, AppColors.PanelBorder, shape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("✦", color = AppColors.Accent, fontSize = 12.sp)
            Text(
                "${chat.provider?.title ?: "ИИ"} · ${chat.model}",
                color = AppColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 240.dp),
            )
            Text("▾", color = AppColors.Muted, fontSize = 12.sp)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = 420.dp).background(AppColors.Panel),
        ) {
            bridge.manager.connected().forEachIndexed { index, provider ->
                if (index > 0) HorizontalDivider(color = AppColors.Line)
                Text(
                    provider.title,
                    color = AppColors.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                settings.modelsFor(provider).forEach { model ->
                    val selected = chat.provider == provider && chat.model == model.id
                    DropdownMenuItem(
                        text = {
                            Text(
                                model.name,
                                color = if (selected) AppColors.Accent else AppColors.Text,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingIcon = if (selected) {
                            { Icon(AppIcons.Check, null, tint = AppColors.Accent, modifier = Modifier.size(16.dp)) }
                        } else {
                            null
                        },
                        onClick = {
                            open = false
                            bridge.onModel(provider, model.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun UserBubble(bubble: AiBubble, first: Boolean, fragment: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                .background(Color(0xFF2A2E35))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (first) {
                Text("Из книги", color = AppColors.Faint, fontSize = 11.sp)
                Text(
                    fragment,
                    color = AppColors.Text,
                    fontFamily = FontFamily.Serif,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(bubble.shown, color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text(bubble.shown, color = AppColors.Text, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun AnswerBubble(text: String, writing: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
            .background(AppColors.Field)
            .border(1.dp, AppColors.PanelBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (text.isEmpty() && writing) {
            TypingDots()
        } else {
            SelectionContainer {
                Text(markdown(text), color = Color(0xFFE2E3E6), fontSize = 15.sp, lineHeight = 22.sp)
            }
        }
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(Modifier.height(20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(520, delayMillis = i * 160), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(Modifier.size(7.dp).alpha(alpha).clip(CircleShape).background(AppColors.Accent))
        }
    }
}

@Composable
private fun MessageInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    streaming: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AppColors.Field)
            .border(1.dp, AppColors.Line, RoundedCornerShape(24.dp))
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(color = AppColors.Text, fontSize = 15.sp),
            cursorBrush = SolidColor(AppColors.Accent),
            maxLines = 4,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, color = AppColors.Faint, fontSize = 15.sp)
                    inner()
                }
            },
        )
        Box(
            Modifier
                .size(40.dp)
                .liveClick(CircleShape, sound = FeedbackSound.Tap, pressedScale = 0.88f) { if (streaming) onStop() else onSend() }
                .background(if (streaming || value.isNotBlank()) AppColors.Accent else AppColors.Line)
                .semantics { contentDescription = if (streaming) "Остановить" else "Отправить" },
            contentAlignment = Alignment.Center,
        ) {
            if (streaming) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(AppColors.OnAccent))
            } else {
                Icon(AppIcons.Send, null, tint = AppColors.OnAccent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun QuoteBox(text: String, maxLines: Int) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp)).background(AppColors.Field),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(AppColors.Accent))
        Text(
            text,
            color = Color(0xFFD0CEC8),
            fontFamily = FontFamily.Serif,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AccentButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .liveClick(shape, sound = FeedbackSound.Toggle, enabled = enabled, onClick = onClick)
            .background(if (enabled) Brush.verticalGradient(listOf(Color(0xFFA3C1FF), AppColors.Accent)) else SolidColor(Color(0xFF26324A))),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) AppColors.OnAccent else AppColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OutlineButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .liveClick(shape, sound = FeedbackSound.Toggle, enabled = enabled, onClick = onClick)
            .border(1.dp, AppColors.Line, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) AppColors.Text else AppColors.Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
