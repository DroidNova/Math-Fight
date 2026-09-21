package com.droidnova.mathfight.ui.battle

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.droidnova.mathfight.R
import com.droidnova.mathfight.profile.ProfileStats
import com.droidnova.mathfight.ui.components.GameIconButton
import com.droidnova.mathfight.ui.components.GameLoadingState
import com.droidnova.mathfight.ui.components.GameModeIcon
import com.droidnova.mathfight.ui.components.GameSecondaryButton
import com.droidnova.mathfight.ui.components.GameStatusPill
import com.droidnova.mathfight.ui.theme.GameBronze
import com.droidnova.mathfight.ui.theme.GameDiamond
import com.droidnova.mathfight.ui.theme.GameDimensions
import com.droidnova.mathfight.ui.theme.GameGold
import com.droidnova.mathfight.ui.theme.GamePlatinum
import com.droidnova.mathfight.ui.theme.GamePrimary
import com.droidnova.mathfight.ui.theme.GameSilver

@Composable
internal fun LeaderboardScreen(
    state: LeaderboardState,
    currentName: String,
    currentStats: ProfileStats?,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    BackHandler(onBack = onBack)
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
                LeaderboardTopBar(onBack)
                when {
                    state.loading -> LeaderboardLoading()
                    !state.connected -> LeaderboardMessage("Connect to view the leaderboard.", onRetry)
                    state.error.isNotBlank() -> LeaderboardMessage(friendlyLeaderboardMessage(state.error), onRetry)
                    state.rows.isEmpty() -> EmptyLeaderboard()
                    else -> LeaderboardContent(state, currentName, currentStats)
                }
            }
        }
    }
}

@Composable
private fun LeaderboardTopBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GameIconButton(GameModeIcon.BACK, "Back", onBack)
        Text(
            "LEADERBOARD",
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.size(GameDimensions.touchTarget))
    }
}

@Composable
private fun LeaderboardLoading() {
    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(painterResource(R.drawable.lobby_robot_red), contentDescription = null, modifier = Modifier.size(108.dp))
        GameLoadingState("Loading rankings")
    }
}

@Composable
private fun LeaderboardMessage(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        GameSecondaryButton("Retry", onRetry, icon = GameModeIcon.REFRESH)
    }
}

@Composable
private fun EmptyLeaderboard() {
    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(painterResource(R.drawable.lobby_robot_blue), contentDescription = null, modifier = Modifier.size(112.dp))
        Text(
            "No ranked players yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text("Complete a ranked battle to enter the arena rankings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LeaderboardContent(state: LeaderboardState, currentName: String, currentStats: ProfileStats?) {
    val hasPodium = state.rows.size >= 3
    val rankedRows = if (hasPodium) state.rows.drop(3) else state.rows
    val currentVisible = state.rows.any { it.current }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (hasPodium) item(key = "podium") { LeaderboardPodium(state.rows.take(3)) }
            item(key = "rankings-heading") {
                Text(
                    if (hasPodium) "ARENA RANKINGS" else "RANKINGS",
                    Modifier.padding(top = 4.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(rankedRows, key = { "${it.position}:${it.displayName}" }) { row ->
                LeaderboardRowCard(row)
            }
        }
        if (!currentVisible && state.currentPosition > 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
                contentColor = MaterialTheme.colorScheme.onBackground,
                shadowElevation = 10.dp
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("YOUR RANK", style = MaterialTheme.typography.labelSmall, color = GamePrimary, fontWeight = FontWeight.Black)
                    PinnedRankRow(state.currentPosition, currentName, currentStats)
                }
            }
        }
    }
}

@Composable
private fun LeaderboardPodium(top: List<LeaderboardRow>) {
    var shown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(GameDimensions.standardMotionMillis)) + slideInVertically(tween(GameDimensions.standardMotionMillis)) { it / 5 }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, GamePrimary.copy(alpha = 0.3f))
        ) {
            Row(
                Modifier.fillMaxWidth().height(176.dp).padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                PodiumPlayer(top[1], 2, Modifier.weight(1f))
                PodiumPlayer(top[0], 1, Modifier.weight(1f))
                PodiumPlayer(top[2], 3, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PodiumPlayer(row: LeaderboardRow, place: Int, modifier: Modifier = Modifier) {
    val accent = tierAccent(row.tier)
    val avatarSize = if (place == 1) 68.dp else 54.dp
    val pedestalHeight = when (place) { 1 -> 44.dp; 2 -> 34.dp; else -> 28.dp }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(avatarSize),
            shape = CircleShape,
            color = accent.copy(alpha = 0.15f),
            border = BorderStroke(if (place == 1) 2.dp else 1.dp, accent)
        ) {
            Image(
                painterResource(if (row.current) R.drawable.lobby_robot_blue else R.drawable.lobby_robot_red),
                contentDescription = "Rank $place robot for ${row.displayName}",
                modifier = Modifier.padding(2.dp)
            )
        }
        Text(
            row.displayName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text("${row.rating}", style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Black)
        Surface(
            modifier = Modifier.fillMaxWidth().height(pedestalHeight),
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            color = accent.copy(alpha = 0.2f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.6f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("#$place", style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun LeaderboardRowCard(row: LeaderboardRow) {
    val base = if (row.current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.74f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    val container by animateColorAsState(base, tween(GameDimensions.quickMotionMillis), label = "current player highlight")
    val accent = tierAccent(row.tier)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = container,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (row.current) GamePrimary else accent.copy(alpha = 0.26f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("#${row.position}", Modifier.width(42.dp), style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Black)
            Surface(Modifier.size(36.dp), CircleShape, accent.copy(alpha = 0.14f)) {
                Image(
                    painterResource(if (row.current) R.drawable.lobby_robot_blue else R.drawable.lobby_robot_red),
                    contentDescription = null,
                    modifier = Modifier.padding(2.dp)
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        row.displayName,
                        Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (row.current) GameStatusPill("You", GamePrimary)
                }
                Text(row.tier, style = MaterialTheme.typography.bodySmall, color = accent)
            }
            Text(
                row.rating.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PinnedRankRow(position: Int, currentName: String, stats: ProfileStats?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, GamePrimary)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("#$position", Modifier.width(46.dp), style = MaterialTheme.typography.titleMedium, color = GamePrimary, fontWeight = FontWeight.Black)
            Column(Modifier.weight(1f)) {
                Text(
                    currentName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(stats?.tier ?: "Your Rank", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GameStatusPill("You", GamePrimary)
            stats?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    it.rating.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun tierAccent(tier: String): Color = when (tier.lowercase()) {
    "bronze" -> GameBronze
    "gold" -> GameGold
    "platinum" -> GamePlatinum
    "diamond" -> GameDiamond
    else -> GameSilver
}

private fun friendlyLeaderboardMessage(message: String): String = when {
    message.contains("too many", ignoreCase = true) -> "Too many attempts. Try again shortly."
    message.contains("session", ignoreCase = true) -> "Session expired. Reconnect."
    message.contains("connect", ignoreCase = true) -> "Connection lost."
    else -> "Couldn’t load the leaderboard."
}
