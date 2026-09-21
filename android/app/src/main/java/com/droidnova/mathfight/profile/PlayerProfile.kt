package com.droidnova.mathfight.profile

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import com.droidnova.mathfight.game.Difficulty
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.profileDataStore by preferencesDataStore(name = "player_profile")

// Identity is deliberately absent from UI state and from generated toString output.
class LocalProfile(val id: String, val displayName: String)

data class AppPreferences(
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val serverUrl: String = ""
)

fun normalizedPlayerName(input: String): String? {
    val name = input.trim(' ').replace(Regex(" +"), " ")
    return name.takeIf {
        it.codePointCount(0, it.length) in 3..16 && Regex("[\\p{L}\\p{N}_ ]+").matches(it)
    }
}

const val NAME_VALIDATION_MESSAGE = "Use 3–16 letters or numbers, spaces, or underscore."

class ProfileStore(context: Context) {
    private val store = context.applicationContext.profileDataStore
    private val idKey = stringPreferencesKey("profile_id")
    private val nameKey = stringPreferencesKey("display_name")
    private val difficultyKey = stringPreferencesKey("difficulty")
    private val accountTokenKey = stringPreferencesKey("account_token")
    private val soundKey = booleanPreferencesKey("sound_enabled")
    private val vibrationKey = booleanPreferencesKey("vibration_enabled")
    private val serverUrlKey = stringPreferencesKey("server_url")

    suspend fun load(): LocalProfile {
        val saved = store.edit { values ->
            // The same atomic transaction reads and initializes the ID, once per installation.
            if (values[idKey] == null) values[idKey] = UUID.randomUUID().toString()
        }
        return LocalProfile(checkNotNull(saved[idKey]), normalizedPlayerName(saved[nameKey].orEmpty()).orEmpty())
    }

    suspend fun saveName(name: String): LocalProfile {
        val normalized = requireNotNull(normalizedPlayerName(name))
        val saved = store.edit { values ->
            checkNotNull(values[idKey])
            values[nameKey] = normalized
        }
        return LocalProfile(checkNotNull(saved[idKey]), normalized)
    }

    suspend fun loadDifficulty(): Difficulty = store.data.map { values ->
        runCatching { Difficulty.valueOf(values[difficultyKey] ?: Difficulty.STANDARD.name) }.getOrDefault(Difficulty.STANDARD)
    }.first()

    suspend fun saveDifficulty(difficulty: Difficulty) {
        store.edit { it[difficultyKey] = difficulty.name }
    }

    suspend fun loadPreferences(): AppPreferences = store.data.map { values ->
        AppPreferences(
            sound = values[soundKey] ?: true,
            vibration = values[vibrationKey] ?: true,
            serverUrl = values[serverUrlKey].orEmpty()
        )
    }.first()

    suspend fun saveSound(enabled: Boolean) { store.edit { it[soundKey] = enabled } }
    suspend fun saveVibration(enabled: Boolean) { store.edit { it[vibrationKey] = enabled } }
    suspend fun saveServerUrl(value: String) { store.edit { it[serverUrlKey] = value } }

    suspend fun loadAccountToken(): String? = store.data.map { it[accountTokenKey] }.first()
    suspend fun saveAccountToken(token: String) { store.edit { it[accountTokenKey] = token } }
}

data class ProfileMatchStat(
    val matchId: String,
    val localName: String,
    val result: String,
    val opponentName: String,
    val difficulty: String,
    val finishReason: String,
    val matchType: String = "UNRANKED",
    val ratingChange: Int? = null,
    val progression: XpResult? = null,
    val completedAt: String = ""
)
data class ProfileStats(val matchesPlayed: Int, val wins: Int, val losses: Int, val winRate: Double, val matches: List<ProfileMatchStat>, val rating: Int = 1000, val tier: String = "Silver", val leaderboardPosition: Int = 0, val progression: PlayerProgression? = null)

data class ProfileUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val displayName: String = "",
    val showing: Boolean = false,
    val historyOnly: Boolean = false,
    val editing: Boolean = false,
    val nameInput: String = "",
    val saving: Boolean = false,
    val error: String = "",
    val statsLoading: Boolean = false,
    val stats: ProfileStats? = null
)
