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
import com.aistudio.pokiesvault.data.model.CardColor
import com.aistudio.pokiesvault.data.model.FallbackCred
import com.aistudio.pokiesvault.data.model.FetchCheckpoint
import com.aistudio.pokiesvault.data.model.FetchSpeedPreset
import com.aistudio.pokiesvault.data.model.SavedDomainSession
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
        val CARD_COLOR = stringPreferencesKey("card_color")
        val NOT_VISITED = booleanPreferencesKey("not_visited")
        val SIGNUP_ONLY = booleanPreferencesKey("signup_only")
        val WALLETS = booleanPreferencesKey("wallets")
        val MINE_ONLY = booleanPreferencesKey("mine_only")
        val FETCH_SPEED_PRESET = stringPreferencesKey("fetch_speed_preset")
        val BONUS_SPEED_PRESET = stringPreferencesKey("bonus_speed_preset")
        val ALLOW_BACKGROUND_RUNS = booleanPreferencesKey("allow_background_runs")
        val SKIP_KNOWN_DEAD = booleanPreferencesKey("skip_known_dead")
        val FETCH_CHECKPOINT_JSON = stringPreferencesKey("fetch_checkpoint_json_v1")
        val BONUS_CHECKPOINT_JSON = stringPreferencesKey("bonus_checkpoint_json_v1")
        val ENCRYPTED_CREDS = stringPreferencesKey("encrypted_credentials_v1")
        val ENCRYPTED_DOMAIN_SESSIONS = stringPreferencesKey("encrypted_domain_sessions_v1")
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
                cardColor = enumOrDefault(prefs[Keys.CARD_COLOR], CardColor.CLASSIC),
                notVisited = prefs[Keys.NOT_VISITED] ?: false,
                signupOnly = prefs[Keys.SIGNUP_ONLY] ?: false,
                wallets = prefs[Keys.WALLETS] ?: true,
                mineOnly = prefs[Keys.MINE_ONLY] ?: false,
                fetchSpeedPreset = enumOrDefault(prefs[Keys.FETCH_SPEED_PRESET], FetchSpeedPreset.SAFE),
                bonusSpeedPreset = enumOrDefault(prefs[Keys.BONUS_SPEED_PRESET], FetchSpeedPreset.SAFE),
                allowBackgroundRuns = prefs[Keys.ALLOW_BACKGROUND_RUNS] ?: false,
                skipKnownDead = prefs[Keys.SKIP_KNOWN_DEAD] ?: false
            )
        }

    val checkpointFlow: Flow<FetchCheckpoint?> = context.vaultDataStore.data
        .catch { ex ->
            if (ex is IOException) emit(emptyPreferences()) else throw ex
        }
        .map { prefs ->
            decodeCheckpoint(prefs[Keys.FETCH_CHECKPOINT_JSON])
        }

    val bonusCheckpointFlow: Flow<FetchCheckpoint?> = context.vaultDataStore.data
        .catch { ex ->
            if (ex is IOException) emit(emptyPreferences()) else throw ex
        }
        .map { prefs ->
            decodeCheckpoint(prefs[Keys.BONUS_CHECKPOINT_JSON])
        }

    private fun decodeCheckpoint(raw: String?): FetchCheckpoint? {
        if (raw.isNullOrBlank()) return null
        return try {
            json.decodeFromString<FetchCheckpoint>(raw)
        } catch (_: Exception) {
            null
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

    val domainSessionsFlow: Flow<Map<String, SavedDomainSession>> = context.vaultDataStore.data
        .catch { ex ->
            if (ex is IOException) emit(emptyPreferences()) else throw ex
        }
        .map { prefs ->
            decodeSessionsMap(prefs[Keys.ENCRYPTED_DOMAIN_SESSIONS])
        }

    private fun decodeSessionsMap(raw: String?): Map<String, SavedDomainSession> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val decrypted = CredentialCrypto.decrypt(raw)
            if (decrypted.isBlank()) {
                emptyMap()
            } else {
                json.decodeFromString<Map<String, SavedDomainSession>>(decrypted)
            }
        } catch (_: Exception) {
            emptyMap()
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

    suspend fun setCardColor(color: CardColor) {
        context.vaultDataStore.edit { it[Keys.CARD_COLOR] = color.name }
    }

    suspend fun setNotVisited(enabled: Boolean) {
        context.vaultDataStore.edit { it[Keys.NOT_VISITED] = enabled }
    }

    suspend fun setSignupOnly(enabled: Boolean) {
        context.vaultDataStore.edit { it[Keys.SIGNUP_ONLY] = enabled }
    }

    suspend fun setWallets(enabled: Boolean) {
        context.vaultDataStore.edit { it[Keys.WALLETS] = enabled }
    }

    suspend fun setMineOnly(enabled: Boolean) {
        context.vaultDataStore.edit { it[Keys.MINE_ONLY] = enabled }
    }

    suspend fun applySortFilter(
        sortMode: SortMode,
        bonusMode: BonusMode,
        notVisited: Boolean,
        signupOnly: Boolean,
        mineOnly: Boolean
    ) {
        context.vaultDataStore.edit {
            it[Keys.SORT_MODE] = sortMode.name
            it[Keys.BONUS_MODE] = bonusMode.name
            it[Keys.NOT_VISITED] = notVisited
            it[Keys.SIGNUP_ONLY] = signupOnly
            it[Keys.MINE_ONLY] = mineOnly
        }
    }

    suspend fun resetSortFilter() {
        context.vaultDataStore.edit {
            it[Keys.SORT_MODE] = SortMode.DEFAULT.name
            it[Keys.BONUS_MODE] = BonusMode.NONE.name
            it[Keys.CARD_COLOR] = CardColor.CLASSIC.name
            it[Keys.NOT_VISITED] = false
            it[Keys.SIGNUP_ONLY] = false
            it[Keys.MINE_ONLY] = false
        }
    }

    suspend fun setFetchSpeedPreset(preset: FetchSpeedPreset) {
        context.vaultDataStore.edit { it[Keys.FETCH_SPEED_PRESET] = preset.name }
    }

    suspend fun setBonusSpeedPreset(preset: FetchSpeedPreset) {
        context.vaultDataStore.edit { it[Keys.BONUS_SPEED_PRESET] = preset.name }
    }

    suspend fun setAllowBackgroundRuns(enabled: Boolean) {
        context.vaultDataStore.edit { it[Keys.ALLOW_BACKGROUND_RUNS] = enabled }
    }

    suspend fun setSkipKnownDead(enabled: Boolean) {
        context.vaultDataStore.edit { it[Keys.SKIP_KNOWN_DEAD] = enabled }
    }

    suspend fun saveCheckpoint(checkpoint: FetchCheckpoint) {
        val encoded = json.encodeToString(checkpoint)
        context.vaultDataStore.edit { it[Keys.FETCH_CHECKPOINT_JSON] = encoded }
    }

    suspend fun clearCheckpoint() {
        context.vaultDataStore.edit { it.remove(Keys.FETCH_CHECKPOINT_JSON) }
    }

    suspend fun saveBonusCheckpoint(checkpoint: FetchCheckpoint) {
        val encoded = json.encodeToString(checkpoint)
        context.vaultDataStore.edit { it[Keys.BONUS_CHECKPOINT_JSON] = encoded }
    }

    suspend fun clearBonusCheckpoint() {
        context.vaultDataStore.edit { it.remove(Keys.BONUS_CHECKPOINT_JSON) }
    }

    suspend fun saveCredentials(credentials: VaultCredentials) {
        val normalized = normalizeFallbacks(credentials)
        val encoded = json.encodeToString(normalized)
        val encrypted = CredentialCrypto.encrypt(encoded)
        context.vaultDataStore.edit { it[Keys.ENCRYPTED_CREDS] = encrypted }
    }

    suspend fun saveDomainSession(session: SavedDomainSession) {
        val key = session.domain.trim().lowercase()
        if (key.isBlank()) return
        saveDomainSessionsBatch(mapOf(key to session))
    }

    suspend fun saveDomainSessionsBatch(updates: Map<String, SavedDomainSession>) {
        if (updates.isEmpty()) return
        context.vaultDataStore.edit { prefs ->
            val current = decodeSessionsMap(prefs[Keys.ENCRYPTED_DOMAIN_SESSIONS]).toMutableMap()
            var changed = false
            for ((rawKey, session) in updates) {
                val key = rawKey.trim().lowercase().ifBlank { session.domain.trim().lowercase() }
                if (key.isBlank()) continue
                val prev = current[key]
                val merged = SavedDomainSession(
                    domain = key,
                    host = session.host.ifBlank { prev?.host.orEmpty() },
                    merchantId = session.merchantId.ifBlank { prev?.merchantId.orEmpty() },
                    accessId = session.accessId.ifBlank { prev?.accessId.orEmpty() },
                    accessToken = session.accessToken.ifBlank { prev?.accessToken.orEmpty() },
                    winningPhone = session.winningPhone.ifBlank { prev?.winningPhone.orEmpty() },
                    updatedAt = session.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                )
                current[key] = merged
                changed = true
            }
            if (changed) {
                val encoded = json.encodeToString(current)
                prefs[Keys.ENCRYPTED_DOMAIN_SESSIONS] = CredentialCrypto.encrypt(encoded)
            }
        }
    }

    suspend fun clearDomainToken(domain: String) {
        val key = domain.trim().lowercase()
        if (key.isBlank()) return
        clearDomainTokensBatch(listOf(key))
    }

    suspend fun clearDomainTokensBatch(domains: Collection<String>) {
        if (domains.isEmpty()) return
        val normalizedKeys = domains.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        if (normalizedKeys.isEmpty()) return
        context.vaultDataStore.edit { prefs ->
            val current = decodeSessionsMap(prefs[Keys.ENCRYPTED_DOMAIN_SESSIONS]).toMutableMap()
            var changed = false
            val now = System.currentTimeMillis()
            for (key in normalizedKeys) {
                val prev = current[key] ?: continue
                if (prev.winningPhone.isBlank()) {
                    current.remove(key)
                    changed = true
                } else if (prev.accessId.isNotEmpty() || prev.accessToken.isNotEmpty()) {
                    current[key] = prev.copy(
                        accessId = "",
                        accessToken = "",
                        updatedAt = now
                    )
                    changed = true
                }
            }
            if (!changed) return@edit
            if (current.isEmpty()) {
                prefs.remove(Keys.ENCRYPTED_DOMAIN_SESSIONS)
            } else {
                val encoded = json.encodeToString(current)
                prefs[Keys.ENCRYPTED_DOMAIN_SESSIONS] = CredentialCrypto.encrypt(encoded)
            }
        }
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
