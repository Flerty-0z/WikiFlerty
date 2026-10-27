package com.flerty.wikiflerty.ai

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class KeyCheck {
    data object Loading : KeyCheck()
    data class Ok(val models: Int) : KeyCheck()
    data class Failed(val message: String) : KeyCheck()
}

class AiManager(context: Context) {
    val vault = KeyVault(context)
    val store = AiSettingsStore(context)
    val settings: StateFlow<AiSettings> get() = store.settings

    private val _checks = MutableStateFlow<Map<String, KeyCheck>>(emptyMap())
    val checks: StateFlow<Map<String, KeyCheck>> = _checks.asStateFlow()

    fun connected(): List<AiProvider> = settings.value.allProviders.filter { vault.get(it) != null }

    fun provider(id: String?): AiProvider? = settings.value.providerOf(id)

    fun current(): AiProvider? {
        val chosen = settings.value.activeProvider
        return if (chosen != null && vault.get(chosen) != null) chosen else connected().firstOrNull()
    }

    suspend fun saveKey(provider: AiProvider, key: String) {
        vault.set(provider, key)
        if (key.isBlank()) {
            _checks.value = _checks.value - provider.id
            store.update { s ->
                s.copy(
                    modelLists = s.modelLists - provider.id,
                    provider = if (s.provider == provider.id) null else s.provider,
                )
            }
            return
        }
        check(provider)
    }

    suspend fun detect(rawKey: String): Result<AiProvider> {
        val key = rawKey.trim().removePrefix("Bearer ").trim()
        val candidates = AiProvider.candidates(key)
        if (candidates.isEmpty()) {
            return Result.failure(AiException("Не узнал этот ключ. Выбери нейросеть в списке или «Другое»"))
        }
        val errors = mutableListOf<String>()
        for (provider in candidates) {
            try {
                val models = AiClient.listModels(provider, key)
                vault.set(provider, key)
                remember(provider, models)
                _checks.value = _checks.value + (provider.id to KeyCheck.Ok(models.size))
                return Result.success(provider)
            } catch (e: Exception) {
                errors += e.message ?: "Ключ не подошёл"
            }
        }
        val message = when {
            errors.all { it.startsWith("Нет связи") } -> errors.first()
            candidates.size == AiProvider.builtIn.size -> "Ключ не подошёл ни к одной нейросети. Проверь его или выбери «Другое»"
            candidates.size > 1 -> "Ключ не подошёл. Проверены: ${candidates.joinToString { it.title }}"
            else -> "${candidates.first().title}: ${errors.first()}"
        }
        return Result.failure(AiException(message))
    }

    suspend fun connectCustom(title: String, baseUrl: String, model: String, key: String): Result<AiProvider> {
        val raw = baseUrl.trim()
        if (raw.isBlank()) return Result.failure(AiException("Укажи адрес сервера"))
        val url = normalizeUrl(raw)
        val item = CustomAi("custom_" + System.currentTimeMillis().toString(36), title.trim().ifBlank { "Другое" }, url, model.trim())
        val provider = item.provider()
        val cleanKey = key.trim().removePrefix("Bearer ").trim()
        return try {
            val models = AiClient.listModels(provider, cleanKey.ifBlank { "none" })
            store.update { it.copy(custom = it.custom + item) }
            vault.set(provider, cleanKey.ifBlank { "none" })
            remember(provider, models)
            _checks.value = _checks.value + (provider.id to KeyCheck.Ok(models.size))
            Result.success(provider)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun normalizeUrl(raw: String): String {
        val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        return withScheme.trimEnd('/').removeSuffix("/chat/completions").removeSuffix("/models").trimEnd('/')
    }

    fun removeCustom(id: String) {
        vault.set(id, null)
        _checks.value = _checks.value - id
        store.update { s ->
            s.copy(
                custom = s.custom.filterNot { it.id == id },
                modelLists = s.modelLists - id,
                models = s.models - id,
                provider = if (s.provider == id) null else s.provider,
            )
        }
    }

    private fun remember(provider: AiProvider, models: List<AiModel>) {
        store.update { s ->
            val chosen = s.models[provider.id]?.takeIf { id -> models.any { it.id == id } }
                ?: provider.fallbackModels.firstOrNull { id -> models.any { it.id == id } }
                ?: models.first().id
            s.copy(
                modelLists = s.modelLists + (provider.id to models),
                models = s.models + (provider.id to chosen),
                provider = s.provider?.takeIf { id -> vault.get(id) != null } ?: provider.id,
            )
        }
    }

    suspend fun check(provider: AiProvider) {
        val key = vault.get(provider) ?: return
        _checks.value = _checks.value + (provider.id to KeyCheck.Loading)
        try {
            val models = AiClient.listModels(provider, key)
            remember(provider, models)
            _checks.value = _checks.value + (provider.id to KeyCheck.Ok(models.size))
        } catch (e: Exception) {
            _checks.value = _checks.value + (provider.id to KeyCheck.Failed(e.message ?: "Не удалось проверить ключ"))
        }
    }

    fun chat(provider: AiProvider, model: String, system: String, messages: List<ChatMessage>): Flow<String> {
        val key = vault.get(provider) ?: throw AiException("Нет ключа для ${provider.title}")
        return AiClient.streamChat(provider, key, model, system, messages)
    }
}
