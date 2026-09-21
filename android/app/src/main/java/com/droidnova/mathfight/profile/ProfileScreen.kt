package com.droidnova.mathfight.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.droidnova.mathfight.R
import com.droidnova.mathfight.ui.components.AnimatedXpBar
import com.droidnova.mathfight.ui.components.GameGlyph
import com.droidnova.mathfight.ui.components.GameIconButton
import com.droidnova.mathfight.ui.components.GameLoadingState
import com.droidnova.mathfight.ui.components.GameModeIcon
import com.droidnova.mathfight.ui.components.GamePrimaryButton
import com.droidnova.mathfight.ui.components.GameSecondaryButton
import com.droidnova.mathfight.ui.components.GameStatusPill
import com.droidnova.mathfight.ui.theme.GameDimensions
import com.droidnova.mathfight.ui.theme.GameError
import com.droidnova.mathfight.ui.theme.GamePrimary
import com.droidnova.mathfight.ui.theme.GameSecondary
import com.droidnova.mathfight.ui.theme.GameSuccess
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onName: (String) -> Unit,
    onSave: () -> Unit,
    onEditName: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onFindMatch: () -> Unit
) {
    val setup = !state.loading && state.displayName.isBlank()
    BackHandler(enabled = !setup) { if (!state.saving) onBack() }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.background)
                )
            )
        ) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                ProfileTopBar(
                    title = if (state.historyOnly) "MATCH HISTORY" else "PROFILE",
                    showBack = !setup,
                    backEnabled = !state.saving,
                    onBack = onBack
                )
                when {
                    state.loading -> ProfileLoading()
                    state.loadFailed -> ProfileLoadError(onRetry)
                    setup -> NameSetup(state, onName, onSave)
                    else -> ProfileContent(state, onName, onSave, onEditName, onBack, onRefresh, onFindMatch)
                }
            }
        }
    }
}

@Composable
private fun ProfileTopBar(title: String, showBack: Boolean, backEnabled: Boolean, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) GameIconButton(GameModeIcon.BACK, "Back", onBack, enabled = backEnabled)
        else Spacer(Modifier.size(GameDimensions.touchTarget))
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.size(GameDimensions.touchTarget))
    }
}

@Composable
private fun ProfileLoading() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(painterResource(R.drawable.lobby_robot_blue), contentDescription = null, modifier = Modifier.size(112.dp))
        GameLoadingState("Loading profile")
    }
}

@Composable
private fun ProfileLoadError(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Couldn’t load your profile.", color = MaterialTheme.colorScheme.error)
        GameSecondaryButton("Retry", onRetry, Modifier.widthIn(min = 128.dp), icon = GameModeIcon.REFRESH)
    }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onName: (String) -> Unit,
    onSave: () -> Unit,
    onEditName: () -> Unit,
    onCancelEdit: () -> Unit,
    onRefresh: () -> Unit,
    onFindMatch: () -> Unit
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(GameDimensions.standardMotionMillis))) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!state.historyOnly) {
                    item(key = "profile-summary") { ProfileSummary(state.displayName, state.stats, onEditName) }
                    state.stats?.progression?.let { progression ->
                        item(key = "xp") { XpSummary(progression) }
                    }
                    state.stats?.let { stats ->
                        item(key = "statistics") { StatisticsSummary(stats) }
                    }
                }
                item(key = "history-heading") {
                    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        GameGlyph(GameModeIcon.HISTORY, GamePrimary, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "RECENT BATTLES",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        GameIconButton(
                            GameModeIcon.REFRESH,
                            "Refresh profile and battle history",
                            onRefresh,
                            enabled = !state.statsLoading
                        )
                    }
                }
                when {
                    state.statsLoading && state.stats == null -> items(3) { HistorySkeletonRow() }
                    state.stats == null -> item(key = "history-error") { HistoryError(onRefresh) }
                    state.stats.matches.isEmpty() -> item(key = "history-empty") { EmptyHistory(onFindMatch) }
                    else -> {
                        if (state.statsLoading) item(key = "history-refreshing") {
                            GameLoadingState("Refreshing battles", Modifier.padding(vertical = 4.dp))
                        }
                        itemsIndexed(
                            state.stats.matches,
                            key = { index, match -> match.matchId.ifBlank { "history-$index-${match.completedAt}" } }
                        ) { _, match -> MatchHistoryRow(match, state.displayName) }
                    }
                }
            }
        }
        if (state.editing) NameEditDialog(state, onName, onSave, onCancelEdit)
    }
}

@Composable
private fun ProfileSummary(displayName: String, stats: ProfileStats?, onEditName: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, GamePrimary.copy(alpha = 0.4f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(92.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, GamePrimary.copy(alpha = 0.65f))
            ) {
                Image(
                    painterResource(R.drawable.lobby_robot_blue),
                    contentDescription = "Blue robot avatar for $displayName",
                    modifier = Modifier.padding(2.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        displayName,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    GameIconButton(GameModeIcon.EDIT, "Edit player name", onEditName)
                }
                if (stats != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GameStatusPill("${stats.rating} Rating", GamePrimary, icon = GameModeIcon.RATING)
                        GameStatusPill(stats.tier, GameSecondary, icon = GameModeIcon.TROPHY)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GameStatusPill(
                            if (stats.leaderboardPosition > 0) "Rank #${stats.leaderboardPosition}" else "Unranked",
                            if (stats.leaderboardPosition > 0) GameSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                            icon = GameModeIcon.RANK
                        )
                        stats.progression?.let {
                            GameStatusPill("Level ${it.level}", GamePrimary, icon = GameModeIcon.LEVEL)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XpSummary(progression: PlayerProgression) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Level ${progression.level}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${progression.xpIntoCurrentLevel} / ${progression.xpRequiredForNextLevel} XP",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedXpBar(
                progression.fraction,
                Modifier.fillMaxWidth(),
                "Level ${progression.level} XP: ${progression.xpIntoCurrentLevel} of ${progression.xpRequiredForNextLevel}"
            )
        }
    }
}

@Composable
private fun StatisticsSummary(stats: ProfileStats) {
    val compactRow = LocalConfiguration.current.screenWidthDp >= 350 && LocalDensity.current.fontScale <= 1.25f
    val values = listOf(
        StatValue("Matches", stats.matchesPlayed.toString(), GameModeIcon.HISTORY, GamePrimary),
        StatValue("Wins", stats.wins.toString(), GameModeIcon.TROPHY, GameSuccess),
        StatValue("Losses", stats.losses.toString(), GameModeIcon.LOSS, GameError),
        StatValue("Win rate", "${(stats.winRate.coerceIn(0.0, 1.0) * 100).toInt()}%", GameModeIcon.WIN_RATE, GameSecondary)
    )
    if (compactRow) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            values.forEach { ProfileStat(it, Modifier.weight(1f)) }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            values.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { ProfileStat(it, Modifier.weight(1f)) }
                }
            }
        }
    }
}

private data class StatValue(val label: String, val value: String, val icon: GameModeIcon, val accent: Color)

@Composable
private fun ProfileStat(value: StatValue, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 82.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, value.accent.copy(alpha = 0.3f))
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            GameGlyph(value.icon, value.accent, Modifier.size(18.dp))
            Text(
                value.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(value.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = value.accent)
        }
    }
}

@Composable
private fun MatchHistoryRow(match: ProfileMatchStat, authenticatedName: String) {
    val won = match.result.equals("WIN", ignoreCase = true)
    val ranked = match.matchType.equals("RANKED", ignoreCase = true)
    val localName = match.localName.ifBlank { authenticatedName }
    val opponentName = match.opponentName.ifBlank { "Unknown rival" }
    val modeDetails = buildList {
        add(match.difficulty.lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) })
        add(if (ranked) "Ranked" else "Unranked")
        match.finishReason.takeIf { it.isNotBlank() && !it.equals("normal", true) }?.let {
            add(it.lowercase().replaceFirstChar { value -> value.titlecase(Locale.getDefault()) })
        }
    }.joinToString(" \u2022 ")
    val resultDetails = buildList {
        match.progression?.xpAwarded?.takeIf { it > 0L }?.let { add("+$it XP") }
        compactTimestamp(match.completedAt).takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(" \u2022 ")
    val accent = if (won) GameSuccess else GameError
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ResultBadge(if (won) "WIN" else "LOSS", won, match.matchId.ifBlank { match.completedAt })
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "$localName vs $opponentName",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    modeDetails,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (resultDetails.isNotBlank()) {
                    Text(
                        resultDetails,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (ranked && match.ratingChange != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    signed(match.ratingChange),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = when {
                        match.ratingChange > 0 -> GameSuccess
                        match.ratingChange < 0 -> GameError
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun ResultBadge(text: String, won: Boolean, eventKey: String) {
    var shown by rememberSaveable(eventKey) { mutableStateOf(false) }
    LaunchedEffect(eventKey) { shown = true }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(GameDimensions.quickMotionMillis)) + scaleIn(tween(GameDimensions.quickMotionMillis), initialScale = 0.86f)
    ) {
        val accent = if (won) GameSuccess else GameError
        Surface(
            color = accent.copy(alpha = 0.17f),
            contentColor = accent,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.65f))
        ) {
            Text(
                text,
                Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun HistorySkeletonRow() {
    Surface(
        Modifier.fillMaxWidth().height(66.dp),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {}
}

@Composable
private fun HistoryError(onRetry: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Couldn’t load battle history.",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            GameSecondaryButton("Retry", onRetry, icon = GameModeIcon.REFRESH)
        }
    }
}

@Composable
private fun EmptyHistory(onFindMatch: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(painterResource(R.drawable.lobby_robot_blue), contentDescription = null, modifier = Modifier.size(72.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "No battles yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text("Play your first match to build your record.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GamePrimaryButton("Find Match", onFindMatch)
        }
    }
}

@Composable
private fun NameEditDialog(state: ProfileUiState, onName: (String) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    val normalized = normalizedPlayerName(state.nameInput)
    val changed = state.nameInput != state.displayName
    val localError = changed && normalized == null
    val canSave = normalized != null && normalized != state.displayName && !state.saving
    val characterCount = state.nameInput.codePointCount(0, state.nameInput.length)
    AlertDialog(
        onDismissRequest = { if (!state.saving) onCancel() },
        title = { Text("Edit player name", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = state.nameInput,
                onValueChange = onName,
                label = { Text("Player name") },
                singleLine = true,
                enabled = !state.saving,
                isError = localError || state.error.isNotBlank(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSave) onSave() }),
                supportingText = {
                    Text(
                        when {
                            state.error.isNotBlank() -> friendlyProfileError(state.error)
                            localError -> NAME_VALIDATION_MESSAGE
                            else -> "$characterCount / 16"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { GamePrimaryButton(if (state.saving) "Saving…" else "Save", onSave, enabled = canSave) },
        dismissButton = { GameSecondaryButton("Cancel", onCancel, enabled = !state.saving) }
    )
}

@Composable
private fun NameSetup(state: ProfileUiState, onName: (String) -> Unit, onSave: () -> Unit) {
    val normalized = normalizedPlayerName(state.nameInput)
    val localError = state.nameInput.isNotBlank() && normalized == null
    val count = state.nameInput.codePointCount(0, state.nameInput.length)
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(painterResource(R.drawable.lobby_robot_blue), contentDescription = null, modifier = Modifier.size(124.dp))
        Text(
            "Choose your fighter name",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        OutlinedTextField(
            value = state.nameInput,
            onValueChange = onName,
            label = { Text("Player name") },
            singleLine = true,
            enabled = !state.saving,
            isError = localError || state.error.isNotBlank(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (normalized != null && !state.saving) onSave() }),
            supportingText = {
                Text(
                    when {
                        state.error.isNotBlank() -> friendlyProfileError(state.error)
                        localError -> NAME_VALIDATION_MESSAGE
                        else -> "$count / 16"
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        GamePrimaryButton(
            if (state.saving) "Saving…" else "Continue",
            onSave,
            Modifier.fillMaxWidth(),
            enabled = normalized != null && !state.saving
        )
    }
}

private fun signed(value: Int): String = when {
    value > 0 -> "+$value"
    value < 0 -> "\u2212${abs(value)}"
    else -> "0"
}

private fun compactTimestamp(value: String): String = runCatching {
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.parse(value).atZone(zone)
    val today = LocalDate.now(zone)
    val dateLabel = when (dateTime.toLocalDate()) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> dateTime.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
    if (dateTime.toLocalDate() == today || dateTime.toLocalDate() == today.minusDays(1)) {
        "$dateLabel ${dateTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))}"
    } else dateLabel
}.getOrDefault("")

private fun friendlyProfileError(message: String): String = when {
    message.contains("too many", ignoreCase = true) -> "Too many attempts. Try again shortly."
    message.contains("session", ignoreCase = true) -> "Session expired. Reconnect."
    message.contains("connect", ignoreCase = true) -> "Connection lost. Try again."
    message.contains("3", ignoreCase = true) && message.contains("16", ignoreCase = true) -> NAME_VALIDATION_MESSAGE
    message.isBlank() -> "Couldn’t load your profile."
    else -> "Couldn’t save your player name. Try again."
}
