package com.flerty.wikiflerty.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * API keys, encrypted with a key that lives in the phone's secure hardware (Android Keystore).
 * They never leave the phone except in requests to the matching AI company,
 * and they are never part of export or sync.
 */
class KeyVault(context: Context) {
    private val file = File(context.filesDir, "ai_keys.bin")
    private val _keys = MutableStateFlow(load())

    /** Which providers have a key. The keys themselves are read with [get]. */
    val keys: StateFlow<Map<String, String>> = _keys.asStateFlow()

    fun get(provider: AiProvider): String? = _keys.value[provider.id]?.takeIf { it.isNotBlank() }

    fun set(provider: AiProvider, key: String?) {
        val updated = _keys.value.toMutableMap()
        if (key.isNullOrBlank()) updated.remove(provider.id) else updated[provider.id] = key.trim()
        _keys.value = updated
        save(updated)
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun save(keys: Map<String, String>) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val data = cipher.doFinal(JSONObject(keys).toString().toByteArray(Charsets.UTF_8))
            val out = JSONObject()
                .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .put("data", Base64.encodeToString(data, Base64.NO_WRAP))
            file.writeText(out.toString())
        }
    }

    private fun load(): Map<String, String> = runCatching {
        if (!file.exists()) return emptyMap()
        val json = JSONObject(file.readText())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, Base64.decode(json.getString("iv"), Base64.NO_WRAP)),
        )
        val plain = String(cipher.doFinal(Base64.decode(json.getString("data"), Base64.NO_WRAP)), Charsets.UTF_8)
        val obj = JSONObject(plain)
        obj.keys().asSequence().associateWith { obj.getString(it) }
    }.getOrElse { emptyMap() }

    private companion object {
        const val ALIAS = "wikiflerty_ai_keys"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/** Non-secret AI options. */
data class AiSettings(
    /** Provider used for new questions. */
    val provider: String? = null,
    /** Chosen model per provider. */
    val models: Map<String, String> = emptyMap(),
    /** Models loaded from each provider with the key. */
    val modelLists: Map<String, List<AiModel>> = emptyMap(),
    val translateTo: String = "русский",
    /** "short" or "detailed". */
    val style: String = "short",
) {
    val activeProvider: AiProvider? get() = AiProvider.of(provider)

    fun modelsFor(provider: AiProvider): List<AiModel> =
        modelLists[provider.id]?.takeIf { it.isNotEmpty() } ?: provider.fallbackModels.map { AiModel(it, it) }

    fun modelFor(provider: AiProvider): String =
        models[provider.id] ?: modelsFor(provider).first().id

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("provider", provider)
        put("models", JSONObject(models))
        put("modelLists", JSONObject().apply {
            modelLists.forEach { (id, list) ->
                put(id, JSONArray().apply { list.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } })
            }
        })
        put("translateTo", translateTo)
        put("style", style)
    }

    companion object {
        fun fromJson(json: JSONObject): AiSettings {
            val models = json.optJSONObject("models")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }
            val lists = json.optJSONObject("modelLists")?.let { o ->
                o.keys().asSequence().associateWith { id ->
                    val arr = o.getJSONArray(id)
                    (0 until arr.length()).map { i ->
                        val m = arr.getJSONObject(i)
                        AiModel(m.getString("id"), m.optString("name", m.getString("id")))
                    }
                }
            }
            return AiSettings(
                provider = if (json.isNull("provider")) null else json.optString("provider"),
                models = models.orEmpty(),
                modelLists = lists.orEmpty(),
                translateTo = json.optString("translateTo", "русский"),
                style = json.optString("style", "short"),
            )
        }
    }
}

class AiSettingsStore(context: Context) {
    private val file = File(context.filesDir, "ai_settings.json")
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AiSettings> = _settings.asStateFlow()

    fun update(transform: (AiSettings) -> AiSettings) {
        val updated = transform(_settings.value)
        if (updated == _settings.value) return
        _settings.value = updated
        runCatching { file.writeText(updated.toJson().toString()) }
    }

    private fun load(): AiSettings = runCatching {
        if (file.exists()) AiSettings.fromJson(JSONObject(file.readText())) else AiSettings()
    }.getOrElse { AiSettings() }
}
