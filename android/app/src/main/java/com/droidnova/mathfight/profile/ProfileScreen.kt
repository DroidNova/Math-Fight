package com.droidnova.mathfight.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onName: (String) -> Unit,
    onSave: () -> Unit,
    onEditName: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    val setup = !state.loading && state.displayName.isBlank()
    BackHandler(enabled = !setup) { if (!state.saving) onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    if (!setup) IconButton(onClick = onBack, enabled = !state.saving,
                        modifier = Modifier.semantics { contentDescription = "Back" }) {
                        Text("←", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            Modifier.fillMaxSize()
                .padding(contentPadding)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                state.loading -> StatusCard("Loading profile…")
                state.loadFailed -> {
                    StatusCard(state.error.ifBlank { "Could not load your profile." })
                    Button(onClick = onRetry, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Retry") }
                }
                setup -> NameSetupCard(state, onName, onSave)
                else -> ProfileContent(state, onName, onSave, onEditName, onBack)
            }
        }
    }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onName: (String) -> Unit,
    onSave: () -> Unit,
    onEditName: () -> Unit,
    onCancelEdit: () -> Unit
) {
    val stats = state.stats
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(state.displayName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                stats?.progression?.let { LevelBadge(it.level) }
            }
            if (stats != null) {
                Text("${stats.rating} Rating · ${stats.tier} · ${if (stats.leaderboardPosition > 0) "Rank #${stats.leaderboardPosition}" else "Rank —"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                stats.progression?.let {
                    XpProgressBar(it.fraction)
                    Text("${it.xpIntoCurrentLevel} / ${it.xpRequiredForNextLevel} XP", style = MaterialTheme.typography.labelLarge)
                } ?: Text("XP unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.editing) NameEditor(state, onName, onSave, onCancelEdit)
            else OutlinedButton(onClick = onEditName) { Text("Edit name") }
        }
    }

    when {
        state.statsLoading -> StatusCard("Loading statistics…")
        stats == null -> StatusCard("Statistics unavailable while disconnected.")
        else -> {
            Text("Statistics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Matches", stats.matchesPlayed, Modifier.weight(1f))
                StatCard("Wins", stats.wins, Modifier.weight(1f))
                StatCard("Losses", stats.losses, Modifier.weight(1f))
            }
            Text("Win rate ${(stats.winRate * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Recent matches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (stats.matches.isEmpty()) StatusCard("No completed matches yet.")
            else stats.matches.forEach { MatchHistoryCard(it) }
        }
    }
}

@Composable
private fun NameSetupCard(state: ProfileUiState, onName: (String) -> Unit, onSave: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Choose your player name", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            NameField(state, onName)
            Button(onClick = onSave, enabled = normalizedPlayerName(state.nameInput) != null && !state.saving,
                modifier = Modifier.fillMaxWidth()) { Text(if (state.saving) "Saving…" else "Continue") }
        }
    }
}

@Composable
private fun NameEditor(state: ProfileUiState, onName: (String) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    NameField(state, onName)
    val normalized = normalizedPlayerName(state.nameInput)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSave, enabled = normalized != null && normalized != state.displayName && !state.saving) {
            Text(if (state.saving) "Saving…" else "Save")
        }
        TextButton(onClick = onCancel, enabled = !state.saving) { Text("Cancel") }
    }
}

@Composable
private fun NameField(state: ProfileUiState, onName: (String) -> Unit) {
    val changed = state.nameInput != state.displayName
    val localError = changed && normalizedPlayerName(state.nameInput) == null
    OutlinedTextField(value = state.nameInput, onValueChange = onName, label = { Text("Player name") }, singleLine = true,
        enabled = !state.saving, isError = state.error.isNotBlank() || localError, modifier = Modifier.fillMaxWidth())
    val message = state.error.ifBlank { if (localError) NAME_VALIDATION_MESSAGE else "" }
    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun LevelBadge(level: Int) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) {
        Text("Level $level", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun XpProgressBar(value: Float) {
    val progress = value.coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50))
        .background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (progress > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun StatCard(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MatchHistoryCard(match: ProfileMatchStat) {
    val won = match.result.equals("WIN", ignoreCase = true)
    val ranked = match.matchType.equals("RANKED", ignoreCase = true)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${match.localName} vs ${match.opponentName}", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                ResultBadge(if (won) "WIN" else "LOSS", won)
            }
            val details = buildList {
                add(match.difficulty.lowercase().replaceFirstChar { it.uppercase() })
                add(if (ranked) "Ranked" else "Unranked")
                if (match.finishReason.equals("forfeit", ignoreCase = true)) add("Forfeit")
            }.joinToString(" · ")
            Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            if (ranked) {
                val rating = match.ratingChange?.let { "Rating ${signed(it)}" } ?: "Rating unavailable"
                val xp = match.progression?.let { "XP +${it.xpAwarded}" } ?: "XP unavailable"
                Text("$rating · $xp", style = MaterialTheme.typography.bodyMedium)
            } else Text("No rating change · No XP", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ResultBadge(text: String, won: Boolean) {
    Surface(
        color = if (won) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (won) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusCard(message: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun signed(value: Int): String = when {
    value > 0 -> "+$value"
    value < 0 -> "−${abs(value)}"
    else -> "+0"
}
