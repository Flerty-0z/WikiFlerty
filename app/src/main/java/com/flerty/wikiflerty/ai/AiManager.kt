package com.flerty.wikiflerty.ai

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Result of checking a key and loading its models. */
sealed class KeyCheck {
    data object Loading : KeyCheck()
    data class Ok(val models: Int) : KeyCheck()
    data class Failed(val message: String) : KeyCheck()
}

/** One place for keys, options and requests to all AI providers. */
class AiManager(context: Context) {
    val vault = KeyVault(context)
    val store = AiSettingsStore(context)
    val settings: StateFlow<AiSettings> get() = store.settings

    private val _checks = MutableStateFlow<Map<String, KeyCheck>>(emptyMap())
    val checks: StateFlow<Map<String, KeyCheck>> = _checks.asStateFlow()

    /** Providers with a saved key, in the order they are shown. */
    fun connected(): List<AiProvider> = AiProvider.entries.filter { vault.get(it) != null }

    /** The provider to use now: the chosen one if it has a key, else any provider with a key. */
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

    /** Checks the key by loading the list of models it can use. */
    suspend fun check(provider: AiProvider) {
        val key = vault.get(provider) ?: return
        _checks.value = _checks.value + (provider.id to KeyCheck.Loading)
        try {
            val models = AiClient.listModels(provider, key)
            store.update { s ->
                val chosen = s.models[provider.id]?.takeIf { id -> models.any { it.id == id } }
                    ?: provider.fallbackModels.firstOrNull { id -> models.any { it.id == id } }
                    ?: models.first().id
                s.copy(
                    modelLists = s.modelLists + (provider.id to models),
                    models = s.models + (provider.id to chosen),
                    provider = s.provider ?: provider.id,
                )
            }
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
