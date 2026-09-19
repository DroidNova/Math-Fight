package com.droidnova.mathfight.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProfileScreen(state: ProfileUiState, onName: (String) -> Unit, onSave: () -> Unit,
                  onBack: () -> Unit, onRetry: () -> Unit) {
    BackHandler(enabled = state.editing || state.saving) { if (!state.saving) onBack() }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                state.loading -> Text("Loading profile…")
                state.loadFailed -> {
                    Text(state.error)
                    Button(onClick = onRetry) { Text("Retry") }
                }
                else -> {
                    Text(if (state.editing) "Profile" else "Choose your player name",
                        style = MaterialTheme.typography.headlineSmall)
                    if (state.editing) Text("Current name: ${state.displayName}", Modifier.padding(top = 12.dp))
                    when {
                        state.statsLoading -> Text("Loading statistics…", Modifier.padding(top = 16.dp))
                        state.stats != null -> {
                            val stats = state.stats
                            Text("Matches played: ${stats.matchesPlayed}   Wins: ${stats.wins}   Losses: ${stats.losses}", Modifier.padding(top = 16.dp))
                            Text("Win rate: ${(stats.winRate * 100).toInt()}%", Modifier.padding(top = 4.dp))
                            if (stats.matches.isNotEmpty()) {
                                Text("Recent matches", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                                stats.matches.forEach { match ->
                                    Text("${match.result} vs ${match.opponentName} • ${match.difficulty.lowercase().replaceFirstChar { it.uppercase() }}${if (match.finishReason == "forfeit") " • forfeit" else ""}")
                                }
                            }
                        }
                        state.editing -> Text("Statistics unavailable while offline", Modifier.padding(top = 16.dp))
                    }
                    OutlinedTextField(
                        value = state.nameInput, onValueChange = onName, label = { Text("Player name") },
                        singleLine = true, enabled = !state.saving, isError = state.error.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    )
                    if (state.error.isNotEmpty()) Text(state.error, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.padding(top = 16.dp)) {
                        Text(if (state.saving) "Saving…" else if (state.editing) "Save" else "Continue")
                    }
                    if (state.editing) TextButton(onClick = onBack, enabled = !state.saving) { Text("Cancel") }
                }
            }
        }
    }
}
