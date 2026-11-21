package com.aistudio.pokiesvault.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aistudio.pokiesvault.data.model.BonusMode
import com.aistudio.pokiesvault.data.model.FallbackCred
import com.aistudio.pokiesvault.data.model.FetchCheckpoint
import com.aistudio.pokiesvault.data.model.FetchSpeedPreset
import com.aistudio.pokiesvault.data.model.SortMode
import com.aistudio.pokiesvault.data.model.VaultCredentials
import com.aistudio.pokiesvault.data.model.VaultPreferences
import com.aistudio.pokiesvault.data.model.VaultThemeId
import com.aistudio.pokiesvault.data.model.ViewMode
import com.aistudio.pokiesvault.util.CredentialCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "pokies_vault_datastore_v2")

class VaultPreferencesStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val THEME_ID = stringPreferencesKey("theme_id")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val SORT_MODE = stringPreferencesKey("sort_mode")
        val BONUS_MODE = stringPreferencesKey("bonus_mode")
        val NOT_VISITED = booleanPreferencesKey("not_visited")
        val WALLETS = booleanPreferencesKey("wallets")
        val MINE_ONLY = booleanPreferencesKey("mine_only")
        val FETCH_SPEED_PRESET = stringPreferencesKey("fetch_speed_preset")
        val FETCH_CHECKPOINT_JSON = stringPreferencesKey("fetch_checkpoint_json_v1")
        val ENCRYPTED_CREDS = stringPreferencesKey("encrypted_credentials_v1")
    }

    val preferencesFlow: Flow<VaultPreferences> = context.vaultDataStore.data
        .catch { ex ->
            if (ex is IOException) emit(emptyPreferences()) else throw ex
        }
        .map { prefs ->
            VaultPreferences(
                themeId = enumOrDefault(prefs[Keys.THEME_ID], VaultThemeId.VAULT),
                viewMode = enumOrDefault(prefs[Keys.VIEW_MODE], ViewMode.GROUPS),
                sortMode = enumOrDefault(prefs[Keys.SORT_MODE], SortMode.DEFAULT),
                bonusMode = enumOrDefault(prefs[Keys.BONUS_MODE], BonusMode.NONE),
                notVisited = prefs[Keys.NOT_VISITED] ?: false,
                wallets = prefs[Keys.WALLETS] ?: true,
                mineOnly = prefs[Keys.MINE_ONLY] ?: false,
                fetchSpeedPreset = enumOrDefault(prefs[Keys.FETCH_SPEED_PRESET], FetchSpeedPreset.SAFE)
            )
        }

    val checkpointFlow: Flow<FetchCheckpoint?> = context.vaultDataStore.data
        .catch { ex ->
            if (ex is IOException) emit(emptyPreferences()) else throw ex
        }
        .map { prefs ->
            val raw = prefs[Keys.FETCH_CHECKPOINT_JSON]
            if (raw.isNullOrBlank()) {
                null
            } else {
                try {
                    json.decodeFromString<FetchCheckpoint>(raw)
                } catch (_: Exception) {
                    null
                }
            }
        }

    val credentialsFlow: Flow<VaultCredentials> = context.vaultDataStore.data
        .catch { ex ->
            if (ex is IOException) emit(emptyPreferences()) else throw ex
        }
        .map { prefs ->
            val raw = prefs[Keys.ENCRYPTED_CREDS]
            if (raw.isNullOrBlank()) {
                VaultCredentials()
            } else {
                try {
                    val decrypted = CredentialCrypto.decrypt(raw)
                    if (decrypted.isBlank()) {
                        VaultCredentials()
                    } else {
                        val decoded = json.decodeFromString<VaultCredentials>(decrypted)
                        normalizeFallbacks(decoded)
                    }
                } catch (_: Exception) {
                    VaultCredentials()
                }
            }
        }

    suspend fun setTheme(themeId: VaultThemeId) {
        context.vaultDataStore.edit { it[Keys.THEME_ID] = themeId.name }
    }

    suspend fun setViewMode(mode: ViewMode) {
        context.vaultDataStore.edit { it[Keys.VIEW_MODE] = mode.name }
    }

    suspend fun setSortMode(mode: SortMode) {
        context.vaultDataStore.edit { it[Keys.SORT_MODE] = mode.name }
    }

    suspend fun setBonusMode(mode: BonusMode) {
        context.vaultDataStore.edit { it[Keys.BONUS_MODE] = mode.name }
    }

    suspend fun setNotVisited(enabled: Boolean) {
        context.vaultDataStore.edit { it[Keys.NOT_VISITED] = enabled }
    }

    suspend fun setWallets(enabled: Boolean) {
        context.vaultDataStore.edit { it[Keys.WALLETS] = enabled }
    }

    suspend fun setMineOnly(enabled: Boolean) {
        context.vaultDataStore.edit { it[Keys.MINE_ONLY] = enabled }
    }

    suspend fun setFetchSpeedPreset(preset: FetchSpeedPreset) {
        context.vaultDataStore.edit { it[Keys.FETCH_SPEED_PRESET] = preset.name }
    }

    suspend fun saveCheckpoint(checkpoint: FetchCheckpoint) {
        val encoded = json.encodeToString(checkpoint)
        context.vaultDataStore.edit { it[Keys.FETCH_CHECKPOINT_JSON] = encoded }
    }

    suspend fun clearCheckpoint() {
        context.vaultDataStore.edit { it.remove(Keys.FETCH_CHECKPOINT_JSON) }
    }

    suspend fun saveCredentials(credentials: VaultCredentials) {
        val normalized = normalizeFallbacks(credentials)
        val encoded = json.encodeToString(normalized)
        val encrypted = CredentialCrypto.encrypt(encoded)
        context.vaultDataStore.edit { it[Keys.ENCRYPTED_CREDS] = encrypted }
    }

    suspend fun clearAll() {
        context.vaultDataStore.edit { it.clear() }
    }

    private fun normalizeFallbacks(creds: VaultCredentials): VaultCredentials {
        val padded = creds.fallbacks.toMutableList()
        while (padded.size < 3) padded.add(FallbackCred())
        return creds.copy(fallbacks = padded.take(3))
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, defaultValue: T): T {
        if (raw.isNullOrBlank()) return defaultValue
        return try {
            enumValueOf<T>(raw)
        } catch (_: Exception) {
            defaultValue
        }
    }
}
