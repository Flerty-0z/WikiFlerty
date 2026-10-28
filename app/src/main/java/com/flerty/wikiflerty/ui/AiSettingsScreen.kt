package com.flerty.wikiflerty.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.ai.AiManager
import com.flerty.wikiflerty.ai.AiProvider
import com.flerty.wikiflerty.ai.KeyCheck
import kotlinx.coroutines.launch

private val Good = Color(0xFF7ED3A0)
private val Bad = Color(0xFFF08A8A)

private val TranslateLanguages = listOf(
    "русский", "английский", "немецкий", "французский", "испанский", "итальянский", "португальский",
    "польский", "чешский", "украинский", "белорусский", "казахский", "узбекский", "киргизский",
    "татарский", "азербайджанский", "армянский", "грузинский", "турецкий", "арабский", "персидский",
    "иврит", "хинди", "китайский", "японский", "корейский", "вьетнамский", "индонезийский",
    "нидерландский", "шведский", "норвежский", "финский", "греческий", "латинский",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSettingsContent(ai: AiManager, modifier: Modifier = Modifier) {
    val settings by ai.settings.collectAsStateWithLifecycle()
    val keys by ai.vault.keys.collectAsStateWithLifecycle()
    val checks by ai.checks.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Вставь ключ любой нейросети — приложение само поймёт, чья она. Ключ хранится зашифрованным только на телефоне.",
            color = AppColors.Muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )

        QuickConnectCard(ai) { expanded = it.id }

        val all = settings.allProviders
        val connected = all.filter { !keys[it.id].isNullOrBlank() }

        val card: @Composable (AiProvider) -> Unit = { provider ->
            ProviderCard(
            ai = ai,
            provider = provider,
            hasKey = !keys[provider.id].isNullOrBlank(),
            isDefault = ai.current() == provider && !keys[provider.id].isNullOrBlank(),
            check = checks[provider.id],
            selectedModel = settings.modelFor(provider),
            models = settings.modelsFor(provider),
            loadedModels = settings.modelLists[provider.id] != null,
            expanded = expanded == provider.id,
            onExpand = { expanded = if (expanded == provider.id) null else provider.id },
            )
        }

        if (connected.isNotEmpty()) {
            SectionLabel("Подключены")
            connected.forEach { card(it) }
        }
        AllProvidersRow(all, connected.size) { showAll = true }

        if (showAll) {
            AppSheet(onDismiss = { showAll = false }, title = "Нейросети") {
                all.forEach { card(it) }
                CustomCard(ai, expanded == "custom") { expanded = if (expanded == "custom") null else "custom" }
                Text(
                    "Нажми на нейросеть, вставь ключ и проверь",
                    color = AppColors.Faint,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        SettingsGroup("Ответы") {
            PickerRow(
                label = "Длина ответов",
                icon = AppIcons.Length,
                options = listOf(
                    PickerOption("short", "Кратко", "2–5 предложений, только суть"),
                    PickerOption("detailed", "Подробно", "Развёрнуто, с примерами"),
                ),
                selectedId = if (settings.style == "detailed") "detailed" else "short",
            ) { id -> ai.store.update { it.copy(style = id) } }
            PickerRow(
                label = "Переводить на",
                icon = AppIcons.Translate,
                options = TranslateLanguages.map { PickerOption(it, it.replaceFirstChar { c -> c.uppercase() }) },
                selectedId = settings.translateTo,
            ) { language -> ai.store.update { it.copy(translateTo = language) } }
        }
    }
}

@Composable
private fun ProviderCard(
    ai: AiManager,
    provider: AiProvider,
    hasKey: Boolean,
    isDefault: Boolean,
    check: KeyCheck?,
    selectedModel: String,
    models: List<com.flerty.wikiflerty.ai.AiModel>,
    loadedModels: Boolean,
    expanded: Boolean,
    onExpand: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var keyInput by remember(provider, hasKey) { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    val shape = appCorner(18)
    val border by animateColorAsState(if (isDefault) AppColors.Accent.copy(alpha = 0.4f) else AppColors.PanelBorder, label = "card")

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Field)
            .border(if (isDefault) 1.5.dp else 1.dp, border, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onExpand)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProviderIcon(provider, 38.dp)
            Column(Modifier.weight(1f)) {
                Text(provider.title, color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                val status = when {
                    check is KeyCheck.Loading -> "Проверяю ключ…"
                    check is KeyCheck.Failed -> "Ошибка ключа"
                    hasKey && isDefault -> "Подключено · основная · $selectedModel"
                    hasKey -> "Подключено · $selectedModel"
                    else -> "${provider.company} · нет ключа"
                }
                Text(
                    status,
                    color = when {
                        check is KeyCheck.Failed -> Bad
                        hasKey -> Good
                        else -> AppColors.Faint
                    },
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (check is KeyCheck.Loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AppColors.Accent)
            } else {
                Icon(
                    AppIcons.Chevron,
                    null,
                    tint = AppColors.Muted,
                    modifier = Modifier.size(20.dp).rotate(if (expanded) 90f else 0f),
                )
            }
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(
                Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(appCorner(12))
                        .background(Color(0xFF121316))
                        .border(1.dp, AppColors.Line, appCorner(12))
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it.trim() },
                        singleLine = true,
                        textStyle = TextStyle(color = AppColors.Text, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(AppColors.Accent),
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box {
                                if (keyInput.isEmpty()) {
                                    Text(
                                        if (hasKey) "Ключ сохранён" else "Вставь API-ключ",
                                        color = AppColors.Faint,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    Box(
                        Modifier.size(44.dp).liveClick(CircleShape, sound = FeedbackSound.Toggle) { showKey = !showKey },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (showKey) AppIcons.EyeOff else AppIcons.Eye,
                            contentDescription = if (showKey) "Скрыть ключ" else "Показать ключ",
                            tint = AppColors.Muted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val buttonShape = appCorner(12)
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .liveClick(buttonShape, sound = FeedbackSound.Toggle, enabled = keyInput.isNotBlank() || hasKey) {
                                scope.launch {
                                    if (keyInput.isNotBlank()) {
                                        ai.saveKey(provider, keyInput)
                                        keyInput = ""
                                    } else {
                                        ai.check(provider)
                                    }
                                }
                            }
                            .background(if (keyInput.isNotBlank() || hasKey) AppColors.Accent else AppColors.Line),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (keyInput.isNotBlank()) "Сохранить и проверить" else "Проверить ключ",
                            color = AppColors.OnAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                    if (hasKey || provider.custom) {
                        Box(
                            Modifier
                                .heightIn(min = 44.dp)
                                .liveClick(buttonShape, sound = FeedbackSound.Close) {
                                    if (provider.custom) ai.removeCustom(provider.id) else scope.launch { ai.saveKey(provider, "") }
                                }
                                .border(1.dp, Color(0x55F08A8A), buttonShape)
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Удалить", color = Bad, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                when (check) {
                    is KeyCheck.Failed -> Text(check.message, color = Bad, fontSize = 13.sp, lineHeight = 18.sp)
                    is KeyCheck.Ok -> Text("Ключ работает · моделей: ${check.models}", color = Good, fontSize = 13.sp)
                    else -> Unit
                }

                if (provider.custom) {
                    Text(
                        provider.baseUrl,
                        color = AppColors.Faint,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        "Получить ключ на сайте ${provider.company} ›",
                        color = AppColors.Accent,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.liveClick(appCorner(6), pressedScale = 0.98f) {
                            runCatching { uri.openUri(provider.keyUrl) }
                        }.padding(vertical = 4.dp),
                    )
                }

                if (hasKey) {
                    if (!isDefault) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .liveClick(appCorner(12), sound = FeedbackSound.Toggle) {
                                    ai.store.update { it.copy(provider = provider.id) }
                                }
                                .border(1.dp, AppColors.Accent.copy(alpha = 0.33f), appCorner(12)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Сделать основной", color = AppColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    PickerRow(
                        label = "Модель",
                        icon = AppIcons.Sparkle,
                        options = models.map { PickerOption(it.id, it.name, it.id.takeIf { id -> id != it.name }) },
                        selectedId = selectedModel,
                        searchable = models.size > 8,
                    ) { id -> ai.store.update { it.copy(models = it.models + (provider.id to id)) } }
                    if (!loadedModels && !provider.custom) {
                        Text(
                            "Полный список — после «Проверить ключ»",
                            color = AppColors.Faint,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderIcon(provider: AiProvider, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val res = remember(provider.id) {
        if (provider.custom) 0 else context.resources.getIdentifier("ai_${provider.id}", "drawable", context.packageName)
    }
    if (res != 0) {
        Image(
            painterResource(res),
            contentDescription = provider.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            Modifier.size(size).clip(CircleShape)
                .background(AppColors.AccentSoft)
                .border(1.dp, AppColors.Accent.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                provider.title.take(1).uppercase(),
                color = AppColors.Accent,
                fontSize = (size.value * 0.45f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AllProvidersRow(all: List<AiProvider>, connected: Int, onClick: () -> Unit) {
    val shape = appCorner(18)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Field)
            .border(1.dp, AppColors.PanelBorder, shape)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (connected == 0) "Все нейросети" else "Ещё нейросети",
                color = AppColors.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                all.take(7).forEach { ProviderIcon(it, 26.dp) }
            }
        }
        Text("${all.size + 1}", color = AppColors.Muted, fontSize = 14.sp, maxLines = 1)
        Icon(AppIcons.Chevron, null, tint = AppColors.Muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    SectionHeader(text, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    secret: Boolean = false,
    monospace: Boolean = false,
) {
    var show by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(appCorner(12))
            .background(Color(0xFF121316))
            .border(1.dp, AppColors.Line, appCorner(12))
            .padding(start = 12.dp, end = if (secret) 0.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = AppColors.Text,
                fontSize = 14.sp,
                fontFamily = if (monospace || secret) FontFamily.Monospace else FontFamily.Default,
            ),
            cursorBrush = SolidColor(AppColors.Accent),
            visualTransformation = if (secret && !show) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, color = AppColors.Faint, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
            },
        )
        if (secret) {
            Box(
                Modifier.size(44.dp).liveClick(CircleShape, sound = FeedbackSound.Toggle) { show = !show },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (show) AppIcons.EyeOff else AppIcons.Eye,
                    contentDescription = if (show) "Скрыть ключ" else "Показать ключ",
                    tint = AppColors.Muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionButton(text: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val shape = appCorner(12)
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .liveClick(shape, sound = FeedbackSound.Toggle, enabled = enabled && !busy, onClick = onClick)
            .background(if (enabled) AppColors.Accent else AppColors.Line),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AppColors.OnAccent)
        } else {
            Text(text, color = AppColors.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun QuickConnectCard(ai: AiManager, onConnected: (AiProvider) -> Unit) {
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val shape = appCorner(18)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.AccentSoft)
            .border(1.dp, AppColors.Accent.copy(alpha = 0.3f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(AppIcons.Sparkle, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
            Text("Подключить нейросеть", color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        InputField(
            value = key,
            onValueChange = { key = it.trim(); result = null },
            placeholder = "Вставь API-ключ",
            secret = true,
        )
        ActionButton("Подключить", enabled = key.isNotBlank(), busy = busy) {
            busy = true
            result = null
            scope.launch {
                ai.detect(key)
                    .onSuccess {
                        result = true to "Подключено: ${it.title}"
                        key = ""
                        onConnected(it)
                    }
                    .onFailure { result = false to (it.message ?: "Не получилось") }
                busy = false
            }
        }
        result?.let { (ok, message) ->
            Text(message, color = if (ok) Good else Bad, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun CustomCard(ai: AiManager, expanded: Boolean, onExpand: () -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val shape = appCorner(18)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Field)
            .border(1.dp, AppColors.PanelBorder, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onExpand)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(38.dp).clip(appCorner(11)).background(AppColors.AccentSoft)
                    .border(1.dp, AppColors.Accent.copy(alpha = 0.2f), appCorner(11)),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = AppColors.Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text("Другое", color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Своя нейросеть по адресу", color = AppColors.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                AppIcons.Chevron,
                null,
                tint = AppColors.Muted,
                modifier = Modifier.size(20.dp).rotate(if (expanded) 90f else 0f),
            )
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(
                Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InputField(url, { url = it.trim(); error = null }, "Адрес: api.example.com/v1", monospace = true)
                InputField(key, { key = it.trim(); error = null }, "API-ключ", secret = true)
                InputField(title, { title = it }, "Название (необязательно)")
                InputField(model, { model = it.trim() }, "Модель (необязательно)", monospace = true)
                ActionButton("Подключить", enabled = url.isNotBlank(), busy = busy) {
                    busy = true
                    error = null
                    scope.launch {
                        ai.connectCustom(title, url, model, key)
                            .onSuccess {
                                title = ""; url = ""; key = ""; model = ""
                                onExpand()
                            }
                            .onFailure { error = it.message ?: "Не получилось" }
                        busy = false
                    }
                }
                error?.let { Text(it, color = Bad, fontSize = 13.sp, lineHeight = 18.sp) }
                Text(
                    "Подойдёт любой сервис с OpenAI-совместимым API",
                    color = AppColors.Faint,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = appCorner(18)
    val bg by animateColorAsState(if (selected) AppColors.Accent else Color.Transparent, label = "chip")
    Box(
        Modifier
            .height(36.dp)
            .liveClick(shape, sound = FeedbackSound.Toggle, onClick = onClick)
            .background(bg)
            .border(1.dp, if (selected) AppColors.Accent else AppColors.Line, shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) AppColors.OnAccent else AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
