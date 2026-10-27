package com.flerty.wikiflerty.ui

import androidx.compose.animation.AnimatedVisibility
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

/** Connecting AI providers: keys, checking them, choosing a model; plus answer options. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSettingsContent(ai: AiManager, modifier: Modifier = Modifier) {
    val settings by ai.settings.collectAsStateWithLifecycle()
    val keys by ai.vault.keys.collectAsStateWithLifecycle()
    val checks by ai.checks.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf<String?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Вставь API-ключ хотя бы одной нейросети. Ключ хранится только на этом телефоне в зашифрованном виде. " +
                "Запросы оплачиваются по тарифам выбранной компании.",
            color = AppColors.Muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )

        AiProvider.entries.forEach { provider ->
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

        Spacer(Modifier.height(4.dp))
        SettingsGroup("Ответы") {
            SettingSegmented(
                label = "Подробность",
                options = listOf("Кратко", "Подробно"),
                selected = if (settings.style == "detailed") 1 else 0,
            ) { i -> ai.store.update { it.copy(style = if (i == 1) "detailed" else "short") } }
            PickerRow(
                label = "Переводить на",
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
            Box(
                Modifier.size(38.dp).clip(appCorner(11)).background(AppColors.AccentSoft)
                    .border(1.dp, AppColors.Accent.copy(alpha = 0.2f), appCorner(11)),
                contentAlignment = Alignment.Center,
            ) {
                Text(provider.title.take(1), color = AppColors.Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
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
                // Key field
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
                                        if (hasKey) "Ключ сохранён · вставь новый, чтобы заменить" else "Вставь API-ключ",
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
                            .height(44.dp)
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
                        )
                    }
                    if (hasKey) {
                        Box(
                            Modifier
                                .height(44.dp)
                                .liveClick(buttonShape, sound = FeedbackSound.Close) { scope.launch { ai.saveKey(provider, "") } }
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
                    is KeyCheck.Ok -> Text("Ключ работает · доступно моделей: ${check.models}", color = Good, fontSize = 13.sp)
                    else -> Unit
                }

                Text(
                    "Получить ключ на сайте ${provider.company} ›",
                    color = AppColors.Accent,
                    fontSize = 13.sp,
                    modifier = Modifier.liveClick(appCorner(6), pressedScale = 0.98f) {
                        runCatching { uri.openUri(provider.keyUrl) }
                    }.padding(vertical = 4.dp),
                )

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
                    Text(
                        if (loadedModels) "Модель" else "Модель (нажми «Проверить ключ», чтобы загрузить полный список)",
                        color = AppColors.Muted,
                        fontSize = 13.sp,
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .clip(appCorner(12))
                            .background(Color(0xFF121316))
                            .verticalScroll(rememberScrollState()),
                    ) {
                        models.forEach { model ->
                            val selected = model.id == selectedModel
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .liveClick(appCorner(0), sound = FeedbackSound.Toggle, pressedScale = 0.99f) {
                                        ai.store.update { it.copy(models = it.models + (provider.id to model.id)) }
                                    }
                                    .background(if (selected) AppColors.AccentTint else Color.Transparent)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(model.name, color = AppColors.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (model.name != model.id) {
                                        Text(model.id, color = AppColors.Faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (selected) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(AppIcons.Check, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
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
