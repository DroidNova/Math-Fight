package com.droidnova.mathfight.profile

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import com.droidnova.mathfight.game.Difficulty
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.profileDataStore by preferencesDataStore(name = "player_profile")

// Identity is deliberately absent from UI state and from generated toString output.
class LocalProfile(val id: String, val displayName: String)

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
}

data class ProfileUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val displayName: String = "",
    val editing: Boolean = false,
    val nameInput: String = "",
    val saving: Boolean = false,
    val error: String = ""
)
