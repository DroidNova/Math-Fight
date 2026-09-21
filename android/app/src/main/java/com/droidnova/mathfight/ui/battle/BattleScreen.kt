package com.droidnova.mathfight.ui.battle

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.droidnova.mathfight.R
import com.droidnova.mathfight.game.BattlePhase
import com.droidnova.mathfight.game.BattleState
import com.droidnova.mathfight.game.Difficulty
import com.droidnova.mathfight.game.Fighter
import com.droidnova.mathfight.game.HIT_DAMAGE
import com.droidnova.mathfight.game.STARTING_HP
import com.droidnova.mathfight.game.PhaseKey
import com.droidnova.mathfight.profile.ProfileStats
import com.droidnova.mathfight.profile.XpResult
import com.droidnova.mathfight.profile.XpResultPanel
import com.droidnova.mathfight.ui.components.ConnectionStatusIndicator
import com.droidnova.mathfight.ui.components.ConnectionVisualState
import com.droidnova.mathfight.ui.components.GameDifficultySelector
import com.droidnova.mathfight.ui.components.GameGlyph
import com.droidnova.mathfight.ui.components.GameModeIcon
import com.droidnova.mathfight.ui.components.GamePrimaryButton
import com.droidnova.mathfight.ui.components.GameSecondaryButton
import com.droidnova.mathfight.ui.components.GameStatusPill
import com.droidnova.mathfight.ui.components.InlineMessage
import com.droidnova.mathfight.ui.battle.arena.LibGdxBattleArena
import com.droidnova.mathfight.ui.theme.GameDimensions
import com.droidnova.mathfight.ui.theme.GamePrimary
import com.droidnova.mathfight.ui.theme.GameSecondary
import com.droidnova.mathfight.ui.theme.GameSuccess

@Composable
fun MathFightApp(
    displayName: String,
    profileStats: ProfileStats?,
    onProfile: () -> Unit,
    onMatchHistory: () -> Unit,
    leaderboard: LeaderboardState,
    leaderboardOpen: Boolean,
    onLeaderboard: () -> Unit,
    onCloseLeaderboard: () -> Unit,
    rankedResult: RankedResult,
    xpResult: XpResult?,
    consumeXpAnimation: (String) -> Boolean,
    search: MatchSearchState,
    onFindMatch: () -> Unit,
    onCancelMatch: () -> Unit,
    difficulty: Difficulty,
    onDifficulty: (Difficulty) -> Unit,
    state: BattleState,
    exitInProgress: Boolean,
    isResumed: Boolean,
    onlinePaused: Boolean,
    consumeVisualEvent: (PhaseKey) -> Boolean,
    settings: FeedbackSettings,
    onSound: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onBattleExit: () -> Unit,
    onReturnHome: () -> Unit,
    serverUrl: String,
    connectionStatus: BattleViewModel.ConnectionStatus,
    connectionMessage: String,
    onServerUrl: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    roomCodeInput: String,
    room: RoomInfo?,
    roomError: String,
    onlineMatch: OnlineMatchInfo?,
    onlineAnswerLocked: Boolean,
    onlineSubmissionStatus: String,
    onlineQuestionPrompt: String,
    onlineQuestionTimer: OnlineQuestionTimerState,
    onRoomCode: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onLeaveRoom: () -> Unit,
    onReady: () -> Unit
) {
    val localName = onlineMatch?.localName ?: displayName
    val opponentName = onlineMatch?.opponentName ?: "Bot"
    val searchingWithoutRoom = search.active && room == null
    BackHandler(enabled = state.phase == BattlePhase.HOME && (leaderboardOpen || searchingWithoutRoom || room != null ||
        connectionStatus == BattleViewModel.ConnectionStatus.CONNECTED ||
        connectionStatus == BattleViewModel.ConnectionStatus.CONNECTING),
        onBack = when { leaderboardOpen -> onCloseLeaderboard; searchingWithoutRoom -> onCancelMatch; else -> onReturnHome })
    when (state.phase) {
        BattlePhase.HOME -> if (leaderboardOpen) LeaderboardScreen(
            state = leaderboard,
            currentName = displayName,
            currentStats = profileStats,
            onBack = onCloseLeaderboard,
            onRetry = onLeaderboard
        )
        else if (searchingWithoutRoom) MatchmakingScreen(displayName, profileStats, search, onCancelMatch)
        else HomeScreen(
            displayName = displayName,
            profileStats = profileStats,
            onStart = onStart,
            settings = settings,
            onSound = onSound,
            onVibration = onVibration,
            serverUrl = serverUrl,
            connectionStatus = connectionStatus,
            connectionMessage = connectionMessage,
            onServerUrl = onServerUrl,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            roomCodeInput = roomCodeInput,
            room = room,
            roomError = roomError,
            onRoomCode = onRoomCode,
            onCreateRoom = onCreateRoom,
            onJoinRoom = onJoinRoom,
            onLeaveRoom = onLeaveRoom,
            onReady = onReady,
            onProfile = onProfile,
            onMatchHistory = onMatchHistory,
            onFindMatch = onFindMatch,
            searchError = search.error,
            difficulty = difficulty,
            onDifficulty = onDifficulty,
            onLeaderboard = onLeaderboard
        )
        else -> BattleScreen(
            state = state,
            exitInProgress = exitInProgress,
            isResumed = isResumed,
            onlinePaused = onlinePaused,
            consumeVisualEvent = consumeVisualEvent,
            onDigit = onDigit,
            onBackspace = onBackspace,
            onClear = onClear,
            onSubmit = onSubmit
            ,online = onlineMatch != null, onlineAnswerLocked = onlineAnswerLocked,
            onlineSubmissionStatus = onlineSubmissionStatus,
            onlineQuestionPrompt = onlineQuestionPrompt,
            onlineQuestionTimer = onlineQuestionTimer,
            difficulty = onlineMatch?.difficulty ?: difficulty,
            canRetry = onlineMatch != null && connectionStatus == BattleViewModel.ConnectionStatus.DISCONNECTED,
            onRetry = onConnect,
            localName = localName,
            opponentName = opponentName,
            onRestart = onRestart,
            onBattleExit = onBattleExit,
            rankedResult = rankedResult,
            xpResult = xpResult,
            consumeXpAnimation = consumeXpAnimation
        )
    }
}

@Composable
private fun HomeScreen(
    displayName: String,
    profileStats: ProfileStats?,
    onStart: () -> Unit,
    settings: FeedbackSettings,
    onSound: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
    serverUrl: String,
    connectionStatus: BattleViewModel.ConnectionStatus,
    connectionMessage: String,
    onServerUrl: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    roomCodeInput: String,
    room: RoomInfo?,
    roomError: String,
    onRoomCode: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onLeaveRoom: () -> Unit,
    onReady: () -> Unit,
    onProfile: () -> Unit,
    onMatchHistory: () -> Unit,
    onFindMatch: () -> Unit,
    searchError: String,
    difficulty: Difficulty,
    onDifficulty: (Difficulty) -> Unit,
    onLeaderboard: () -> Unit
) {
    val privateRoomVisible = rememberSaveable { mutableStateOf(room != null) }
    val settingsVisible = rememberSaveable { mutableStateOf(false) }
    val entered = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered.value = true }
    LaunchedEffect(room?.code) { if (room != null) privateRoomVisible.value = true }

    BackHandler(enabled = room == null && settingsVisible.value) {
        settingsVisible.value = false
    }

    val connectionVisualState = when (connectionStatus) {
        BattleViewModel.ConnectionStatus.CONNECTED -> ConnectionVisualState.ONLINE
        BattleViewModel.ConnectionStatus.CONNECTING -> ConnectionVisualState.CONNECTING
        else -> if (connectionMessage.contains("reconnecting", ignoreCase = true)) {
            ConnectionVisualState.CONNECTING
        } else ConnectionVisualState.OFFLINE
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (privateRoomVisible.value || room != null) {
            PrivateRoomFlow(
                displayName = displayName,
                status = connectionStatus,
                difficulty = difficulty,
                roomCodeInput = roomCodeInput,
                room = room,
                roomError = roomError,
                onRoomCode = onRoomCode,
                onCreateRoom = onCreateRoom,
                onJoinRoom = onJoinRoom,
                onReady = onReady,
                onConnect = onConnect,
                onDifficulty = onDifficulty,
                onBack = {
                    privateRoomVisible.value = false
                    if (room != null) onLeaveRoom()
                }
            )
        } else if (settingsVisible.value) {
            HomeSettingsScreen(
                settings = settings,
                onSound = onSound,
                onVibration = onVibration,
                serverUrl = serverUrl,
                connectionStatus = connectionStatus,
                connectionMessage = connectionMessage,
                onServerUrl = onServerUrl,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onBack = { settingsVisible.value = false }
            )
        } else AnimatedVisibility(
            visible = entered.value,
            enter = fadeIn(tween(GameDimensions.standardMotionMillis)),
            exit = fadeOut(tween(GameDimensions.quickMotionMillis)),
            modifier = Modifier.fillMaxSize()
        ) {
            HomeDashboard(
                displayName = displayName,
                profileStats = profileStats,
                connectionState = connectionVisualState,
                connectionStatus = connectionStatus,
                connectionMessage = connectionMessage,
                searchError = searchError,
                difficulty = difficulty,
                onDifficulty = onDifficulty,
                onQuickMatch = if (connectionStatus == BattleViewModel.ConnectionStatus.CONNECTED) onFindMatch else onConnect,
                onOfflineBattle = onStart,
                onPrivateBattle = { privateRoomVisible.value = true },
                onProfile = onProfile,
                onLeaderboard = onLeaderboard,
                onMatchHistory = onMatchHistory,
                onSettings = { settingsVisible.value = true }
            )
        }
    }
}

@Composable
private fun HomeDashboard(
    displayName: String,
    profileStats: ProfileStats?,
    connectionState: ConnectionVisualState,
    connectionStatus: BattleViewModel.ConnectionStatus,
    connectionMessage: String,
    searchError: String,
    difficulty: Difficulty,
    onDifficulty: (Difficulty) -> Unit,
    onQuickMatch: () -> Unit,
    onOfflineBattle: () -> Unit,
    onPrivateBattle: () -> Unit,
    onProfile: () -> Unit,
    onLeaderboard: () -> Unit,
    onMatchHistory: () -> Unit,
    onSettings: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        val compact = maxHeight < 700.dp || fontScale > 1.1f
        val roomy = maxHeight >= 760.dp && fontScale <= 1.1f
        val emergencyScroll = maxHeight < 600.dp || fontScale > 1.3f
        val horizontalPadding = 12.dp
        val contentWidth = minOf(maxWidth - horizontalPadding * 2, 560.dp)
        val gap = if (compact) 7.dp else 10.dp
        val quickMatchLoading = connectionState == ConnectionVisualState.CONNECTING
        val online = connectionStatus == BattleViewModel.ConnectionStatus.CONNECTED
        val statusMessage = friendlyMatchmakingMessage(searchError).ifBlank {
            if (connectionStatus == BattleViewModel.ConnectionStatus.ERROR) friendlyOnlineMessage(connectionMessage) else ""
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.width(contentWidth).fillMaxHeight()
                    .then(if (emergencyScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(vertical = if (compact) 8.dp else 14.dp),
                verticalArrangement = if (emergencyScroll) Arrangement.spacedBy(gap) else Arrangement.SpaceBetween
            ) {
                HomeHeader(
                    displayName = displayName,
                    profileStats = profileStats,
                    connectionState = connectionState,
                    onProfile = onProfile,
                    onSettings = onSettings
                )
                HomeHero(
                    modifier = Modifier.fillMaxWidth().height(
                        when {
                            roomy -> 112.dp
                            compact -> 82.dp
                            else -> 98.dp
                        }
                    )
                )
                GameDifficultySelector(
                    selected = difficulty,
                    onSelected = onDifficulty,
                    compact = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "BATTLE MODES",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    com.droidnova.mathfight.ui.components.GameModeTile(
                        title = "Quick Match",
                        supportingText = when {
                            online -> "Find an online opponent"
                            quickMatchLoading -> "Connecting to online play"
                            else -> "Tap to reconnect"
                        },
                        icon = GameModeIcon.MATCH,
                        accent = GamePrimary,
                        onClick = onQuickMatch,
                        enabled = !quickMatchLoading,
                        disabledReason = "Connecting to online play",
                        loading = quickMatchLoading,
                        prominent = true,
                        modifier = Modifier.fillMaxWidth().height(
                            when {
                                roomy -> 100.dp
                                compact -> 80.dp
                                else -> 90.dp
                            }
                        )
                    )
                    Row(
                        Modifier.fillMaxWidth().height(
                            when {
                                roomy -> 116.dp
                                compact -> 94.dp
                                else -> 104.dp
                            }
                        ),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        com.droidnova.mathfight.ui.components.GameModeTile(
                            title = "Solo Battle",
                            supportingText = "Fight the bot offline",
                            icon = GameModeIcon.OFFLINE,
                            accent = GameSuccess,
                            onClick = onOfflineBattle,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        com.droidnova.mathfight.ui.components.GameModeTile(
                            title = "Private Battle",
                            supportingText = "Create or join",
                            icon = GameModeIcon.ROOM,
                            accent = GameSecondary,
                            onClick = onPrivateBattle,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "PLAYER HUB",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.droidnova.mathfight.ui.components.GameShortcutButton(
                            "Profile", GameModeIcon.PROFILE, GamePrimary, onProfile,
                            Modifier.weight(1f).height(if (roomy) 70.dp else 60.dp)
                        )
                        com.droidnova.mathfight.ui.components.GameShortcutButton(
                            "Leaderboard", GameModeIcon.LEADERBOARD, GameSecondary, onLeaderboard,
                            Modifier.weight(1f).height(if (roomy) 70.dp else 60.dp)
                        )
                        com.droidnova.mathfight.ui.components.GameShortcutButton(
                            "History", GameModeIcon.HISTORY, GameSuccess, onMatchHistory,
                            Modifier.weight(1f).height(if (roomy) 70.dp else 60.dp)
                        )
                    }
                    if (statusMessage.isNotBlank()) {
                        InlineMessage(statusMessage, isError = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    displayName: String,
    profileStats: ProfileStats?,
    connectionState: ConnectionVisualState,
    onProfile: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        com.droidnova.mathfight.ui.components.GameIconButton(
            icon = GameModeIcon.PROFILE,
            contentDescription = "Open profile",
            onClick = onProfile,
            accent = GamePrimary
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val progressLabel = profileStats?.progression?.let { "LEVEL ${it.level}" }
                ?: "RATING ${profileStats?.rating ?: 1000}"
            GameStatusPill(
                progressLabel,
                GamePrimary,
                icon = if (profileStats?.progression != null) GameModeIcon.LEVEL else GameModeIcon.RATING
            )
        }
        ConnectionStatusIndicator(connectionState)
        com.droidnova.mathfight.ui.components.GameIconButton(
            icon = GameModeIcon.SETTINGS,
            contentDescription = "Open settings",
            onClick = onSettings,
            accent = GamePrimary
        )
    }
}

@Composable
private fun HomeHero(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f)
                    )
                )
            )
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("MATH FIGHT", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    Text("Solve fast. Strike first.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Image(
                    painter = painterResource(R.drawable.home_robot_duel),
                    contentDescription = "Blue and red robots ready to battle",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.weight(1.1f).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun ResultPanel(winner: Fighter?, onRestart: () -> Unit, onHome: () -> Unit,
                        actionInProgress: Boolean, online: Boolean, message: String,
                        localName: String, opponentName: String, rankedResult: RankedResult,
                        xpResult: XpResult?, isResumed: Boolean, consumeXpAnimation: (String) -> Boolean,
                        modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (winner == Fighter.PLAYER) "You win!" else "You lose!",
            style = MaterialTheme.typography.displaySmall
        )
        Text("$localName vs $opponentName", modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
        if (online && message.isNotBlank()) Text(message, modifier = Modifier.padding(top = 12.dp))
        if (online && rankedResult.ranked) {
            if (rankedResult.available) Text("Rating ${rankedResult.before} ${if (rankedResult.delta >= 0) "+${rankedResult.delta}" else rankedResult.delta} → ${rankedResult.after}\n${rankedResult.tier}", modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
            else Text("Rating unavailable", modifier = Modifier.padding(top = 12.dp))
        }
        if (online && rankedResult.ranked) {
            if (xpResult != null) XpResultPanel(xpResult, isResumed, consumeXpAnimation)
            else Text("XP temporarily unavailable", modifier = Modifier.padding(top = 12.dp))
        } else Text("Unranked — no XP", modifier = Modifier.padding(top = 12.dp))
        Button(
            onClick = onRestart,
            enabled = !actionInProgress,
            modifier = Modifier.padding(top = 28.dp).heightIn(min = 48.dp)
        ) { Text("Restart") }
        OutlinedButton(
            onClick = onHome,
            enabled = !actionInProgress,
            modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)
        ) { Text("Home") }
    }
}

@Composable
private fun BattleScreen(
    state: BattleState,
    exitInProgress: Boolean,
    isResumed: Boolean,
    onlinePaused: Boolean,
    consumeVisualEvent: (PhaseKey) -> Boolean,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    online: Boolean,
    onlineAnswerLocked: Boolean,
    onlineSubmissionStatus: String,
    onlineQuestionPrompt: String,
    onlineQuestionTimer: OnlineQuestionTimerState,
    difficulty: Difficulty,
    canRetry: Boolean,
    onRetry: () -> Unit,
    localName: String,
    opponentName: String,
    onRestart: () -> Unit,
    onBattleExit: () -> Unit,
    rankedResult: RankedResult,
    xpResult: XpResult?,
    consumeXpAnimation: (String) -> Boolean
) {
    val arenaHostKey = remember { Any() }
    val showLeaveDialog = rememberSaveable(state.battleId) { mutableStateOf(false) }
    val matchCompleted = state.phase == BattlePhase.RESULT
    val questionReady = state.phase == BattlePhase.ANSWERING && state.question != null &&
        (!online || onlineQuestionPrompt.isEmpty())
    val controlsEnabled = isResumed && questionReady && (!online || !onlineAnswerLocked)
    val finishingBattle = state.phase == BattlePhase.KO || state.playerHp == 0 || state.opponentHp == 0 ||
        (!online && state.phase == BattlePhase.WINDUP && when (state.attacker) {
            Fighter.PLAYER -> state.opponentHp <= HIT_DAMAGE
            Fighter.BOT -> state.playerHp <= HIT_DAMAGE
            null -> false
        })
    val transitionMessage = when {
        onlinePaused -> "Battle paused"
        finishingBattle -> "Finishing battle\u2026"
        onlineQuestionPrompt.isNotBlank() && onlineQuestionPrompt.all { it.isDigit() } ->
            "Question starts in $onlineQuestionPrompt"
        onlineQuestionPrompt.isNotBlank() -> onlineQuestionPrompt
        state.phase == BattlePhase.ANSWERING -> "Get ready\u2026"
        else -> "Next question\u2026"
    }

    BackHandler {
        when {
            exitInProgress -> Unit
            matchCompleted -> onBattleExit()
            else -> showLeaveDialog.value = true
        }
    }
    if (showLeaveDialog.value) {
        AlertDialog(
            onDismissRequest = {
                if (!exitInProgress) showLeaveDialog.value = false
            },
            title = { Text("Leave battle?") },
            text = {
                Text(
                    if (online) "Leaving an active online battle will count as a forfeit."
                    else "Your current battle will end."
                )
            },
            dismissButton = {
                TextButton(
                    enabled = !exitInProgress,
                    onClick = { showLeaveDialog.value = false }
                ) { Text("Continue Playing") }
            },
            confirmButton = {
                TextButton(
                    enabled = !exitInProgress,
                    onClick = {
                        showLeaveDialog.value = false
                        onBattleExit()
                    }
                ) { Text("Leave") }
            }
        )
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val questionAreaHeight = 112.dp
            val keypadHeight = 260.dp
            val sectionSpacing = 6.dp
            val lowerAreaHeight = questionAreaHeight + sectionSpacing + keypadHeight
            val verticallyConstrained = maxHeight < 600.dp
            val availableWidth = maxWidth
            Column(
                modifier = Modifier.fillMaxSize()
                    .then(if (verticallyConstrained) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(sectionSpacing)
            ) {
                HealthRow(state, isResumed, localName, opponentName)
                Text(
                    "Mode: ${difficulty.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().then(
                        if (verticallyConstrained) Modifier.height(minOf(availableWidth * 0.5f, 220.dp))
                        else Modifier.weight(1f)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    val arenaWidth = minOf(maxWidth, maxHeight * 2f, 440.dp)
                    key(arenaHostKey) {
                        LibGdxBattleArena(
                            state = state,
                            isResumed = isResumed,
                            onlinePaused = onlinePaused,
                            consumeVisualEvent = consumeVisualEvent,
                            modifier = Modifier.width(arenaWidth).aspectRatio(2f)
                        )
                    }
                }
                if (state.phase == BattlePhase.RESULT) {
                    ResultPanel(
                        winner = state.winner,
                        onRestart = onRestart,
                        onHome = onBattleExit,
                        actionInProgress = exitInProgress,
                        online = online,
                        message = onlineSubmissionStatus,
                        localName = localName,
                        opponentName = opponentName,
                        rankedResult = rankedResult,
                        xpResult = xpResult,
                        isResumed = isResumed,
                        consumeXpAnimation = consumeXpAnimation,
                        modifier = Modifier.fillMaxWidth().height(lowerAreaHeight)
                    )
                } else {
                    BattleQuestionArea(
                        question = state.question?.display.orEmpty(),
                        input = state.input,
                        wrongAnswer = state.wrongAnswer,
                        loading = !questionReady,
                        message = transitionMessage,
                        supportingText = onlineSubmissionStatus,
                        online = online,
                        timer = onlineQuestionTimer,
                        canRetry = canRetry,
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxWidth().height(questionAreaHeight)
                    )
                    Keypad(
                        enabled = controlsEnabled,
                        onDigit = onDigit,
                        onBackspace = onBackspace,
                        onClear = onClear,
                        onSubmit = onSubmit,
                        modifier = Modifier.fillMaxWidth().height(keypadHeight)
                    )
                }
            }
        }
    }
}

@Composable
private fun BattleQuestionArea(
    question: String,
    input: String,
    wrongAnswer: Boolean,
    loading: Boolean,
    message: String,
    supportingText: String,
    online: Boolean,
    timer: OnlineQuestionTimerState,
    canRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
        )
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            if (loading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        )
                        Text(message, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    if (supportingText.isNotBlank()) {
                        Text(
                            supportingText,
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (canRetry) TextButton(onClick = onRetry) { Text("Retry connection") }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = question,
                            modifier = if (online) Modifier.padding(horizontal = 84.dp) else Modifier,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        if (online) {
                            Text(
                                text = when {
                                    timer.expired -> "Time\u2019s up \u00b7 0"
                                    timer.visible -> timer.seconds.toString()
                                    else -> " "
                                },
                                modifier = Modifier.align(Alignment.CenterEnd).width(80.dp),
                                color = if (timer.warning) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.End,
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        text = input.ifEmpty { " " },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    val feedbackText = supportingText.ifBlank { if (wrongAnswer) "Try again" else " " }
                    Text(
                        text = feedbackText,
                        color = if (wrongAnswer && supportingText.isBlank()) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSettingsScreen(
    settings: FeedbackSettings,
    onSound: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
    serverUrl: String,
    connectionStatus: BattleViewModel.ConnectionStatus,
    connectionMessage: String,
    onServerUrl: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val visualState = when (connectionStatus) {
        BattleViewModel.ConnectionStatus.CONNECTED -> ConnectionVisualState.ONLINE
        BattleViewModel.ConnectionStatus.CONNECTING -> ConnectionVisualState.CONNECTING
        else -> if (connectionMessage.contains("reconnecting", ignoreCase = true)) {
            ConnectionVisualState.CONNECTING
        } else ConnectionVisualState.OFFLINE
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val contentWidth = minOf(maxWidth - 24.dp, 560.dp)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.width(contentWidth).fillMaxHeight().verticalScroll(rememberScrollState())
                        .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.droidnova.mathfight.ui.components.GameIconButton(
                            GameModeIcon.BACK,
                            "Back to Home",
                            onBack
                        )
                        Text(
                            "SETTINGS",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        ConnectionStatusIndicator(visualState)
                    }

                    Text("FEEDBACK", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    FeedbackControls(settings, onSound, onVibration)

                    Text("ONLINE CONNECTION", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    ConnectionSettingsPanel(
                        serverUrl = serverUrl,
                        status = connectionStatus,
                        message = connectionMessage,
                        onServerUrl = onServerUrl,
                        onConnect = onConnect,
                        onDisconnect = onDisconnect
                    )
                }
            }
        }
    }
}

private data class ServerAddress(val host: String, val port: String, val scheme: String)

private fun serverAddress(url: String): ServerAddress {
    val candidate = url.trim().ifBlank { "http://192.168.1.5:3000" }
    val normalized = if (candidate.contains("://")) candidate else "http://$candidate"
    return runCatching {
        val uri = java.net.URI(normalized)
        val scheme = uri.scheme?.lowercase().takeIf { it == "https" } ?: "http"
        val port = uri.port.takeIf { it in 1..65535 } ?: if (scheme == "https") 443 else 3000
        ServerAddress(uri.host.orEmpty(), port.toString(), scheme)
    }.getOrElse { ServerAddress("", "3000", "http") }
}

@Composable
private fun ConnectionSettingsPanel(
    serverUrl: String,
    status: BattleViewModel.ConnectionStatus,
    message: String,
    onServerUrl: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val initial = remember(serverUrl) { serverAddress(serverUrl) }
    val host = rememberSaveable(serverUrl) { mutableStateOf(initial.host) }
    val port = rememberSaveable(serverUrl) { mutableStateOf(initial.port) }
    val validationError = rememberSaveable(serverUrl) { mutableStateOf("") }

    fun connectToEnteredServer() {
        val normalizedHost = host.value.trim()
        val normalizedPort = port.value.trim().toIntOrNull()
        validationError.value = when {
            normalizedHost.isBlank() -> "Enter a server IP address or host name."
            !Regex("[A-Za-z0-9._-]+").matches(normalizedHost) ->
                "Enter only the host here, for example 192.168.1.5."
            normalizedPort == null || normalizedPort !in 1..65535 -> "Enter a port from 1 to 65535."
            else -> ""
        }
        if (validationError.value.isNotBlank()) return
        onServerUrl("${initial.scheme}://$normalizedHost:$normalizedPort")
        onConnect()
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Manual server", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Use this only when automatic connection is unavailable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth < 340.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.25f
                if (stacked) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ServerHostField(host.value, { host.value = it; validationError.value = "" }, Modifier.fillMaxWidth())
                        ServerPortField(port.value, { port.value = it; validationError.value = "" }, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ServerHostField(host.value, { host.value = it; validationError.value = "" }, Modifier.weight(2f))
                        ServerPortField(port.value, { port.value = it; validationError.value = "" }, Modifier.weight(1f))
                    }
                }
            }
            if (validationError.value.isNotBlank()) {
                Text(validationError.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GamePrimaryButton(
                    if (status == BattleViewModel.ConnectionStatus.CONNECTING) "Connecting…" else "Connect",
                    ::connectToEnteredServer,
                    Modifier.weight(1f),
                    enabled = status != BattleViewModel.ConnectionStatus.CONNECTING &&
                        status != BattleViewModel.ConnectionStatus.CONNECTED
                )
                GameSecondaryButton(
                    "Disconnect",
                    onDisconnect,
                    Modifier.weight(1f),
                    enabled = status != BattleViewModel.ConnectionStatus.IDLE &&
                        status != BattleViewModel.ConnectionStatus.DISCONNECTED
                )
            }
            val friendlyMessage = friendlyOnlineMessage(message)
            if (status != BattleViewModel.ConnectionStatus.IDLE || friendlyMessage.isNotEmpty()) {
                Text(
                    "${status.name.lowercase().replace('_', ' ')}${friendlyMessage.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == BattleViewModel.ConnectionStatus.ERROR) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ServerHostField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(253)) },
        singleLine = true,
        label = { Text("Server IP or host") },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
            imeAction = androidx.compose.ui.text.input.ImeAction.Next
        ),
        modifier = modifier
    )
}

@Composable
private fun ServerPortField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(5)) },
        singleLine = true,
        label = { Text("Port") },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done
        ),
        modifier = modifier
    )
}

@Composable
private fun FeedbackControls(settings: FeedbackSettings, onSound: (Boolean) -> Unit,
                             onVibration: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GameSecondaryButton(
            label = "Sound ${if (settings.sound) "On" else "Off"}",
            onClick = { onSound(!settings.sound) },
            modifier = Modifier.weight(1f)
        )
        GameSecondaryButton(
            label = "Vibration ${if (settings.vibration) "On" else "Off"}",
            onClick = { onVibration(!settings.vibration) },
            modifier = Modifier.weight(1f)
        )
    }
}

private fun friendlyOnlineMessage(message: String): String {
    val value = message.trim()
    if (value.isBlank()) return ""
    return when {
        value.contains("too many", ignoreCase = true) -> "Too many attempts. Try again shortly."
        value.contains("session", ignoreCase = true) && value.contains("expired", ignoreCase = true) -> "Session expired. Reconnect."
        value.contains("http://", ignoreCase = true) || value.contains("https://", ignoreCase = true) ||
            value.contains("socket", ignoreCase = true) || value.contains("exception", ignoreCase = true) ||
            value.contains("transport", ignoreCase = true) || value.contains("poll", ignoreCase = true) ||
            value.contains("timeout", ignoreCase = true) || value.contains("failed", ignoreCase = true) ->
            "Server temporarily unavailable."
        else -> value
    }
}

@Composable
private fun HealthRow(state: BattleState, isResumed: Boolean, localName: String, opponentName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HealthBar(localName, state.playerHp, isResumed, Modifier.weight(1f))
        HealthBar(opponentName, state.opponentHp, isResumed, Modifier.weight(1f))
    }
}

@Composable
private fun HealthBar(label: String, hp: Int, isResumed: Boolean, modifier: Modifier = Modifier) {
    val safeHp = hp.coerceIn(0, STARTING_HP)
    val progress = remember { Animatable(STARTING_HP.toFloat()) }
    LaunchedEffect(safeHp, isResumed) {
        if (isResumed) progress.animateTo(safeHp.toFloat(), tween(260)) else progress.snapTo(safeHp.toFloat())
    }
    Column(modifier) {
        Text(
            label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF17232D)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.fillMaxHeight()
                    .fillMaxWidth((progress.value / STARTING_HP).coerceIn(0f, 1f))
                    .align(Alignment.CenterStart)
                    .background(GameSuccess)
            )
            Text(
                "HP: $safeHp",
                modifier = Modifier.clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.48f))
                    .padding(horizontal = 5.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun Keypad(
    enabled: Boolean,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { digits ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                digits.forEach { digit ->
                    KeyButton(digit.toString(), "Digit $digit", enabled, { onDigit(digit) }, Modifier.weight(1f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KeyButton("Clear", "Clear answer", enabled, onClear, Modifier.weight(1f))
            KeyButton("0", "Digit 0", enabled, { onDigit(0) }, Modifier.weight(1f))
            KeyButton("", "Backspace", enabled, onBackspace, Modifier.weight(1f), GameModeIcon.BACKSPACE)
        }
        Button(
            onClick = onSubmit,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
            ),
            modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 48.dp)
                .semantics { contentDescription = "Submit answer" }
        ) {
            Text("Submit")
        }
    }
}

@Composable
private fun KeyButton(
    text: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: GameModeIcon? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 1f else 0.72f)
        ),
        modifier = modifier.fillMaxSize().heightIn(min = 48.dp)
            .semantics { contentDescription = description }
    ) {
        if (icon != null) {
            GameGlyph(
                icon,
                if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                Modifier.size(24.dp)
            )
        } else {
            Text(text)
        }
    }
}
