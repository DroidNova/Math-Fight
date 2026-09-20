package com.droidnova.mathfight.ui.battle

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import com.droidnova.mathfight.game.STARTING_HP
import com.droidnova.mathfight.game.PhaseKey
import com.droidnova.mathfight.profile.ProfileStats
import com.droidnova.mathfight.profile.XpResult
import com.droidnova.mathfight.profile.XpResultPanel
import com.droidnova.mathfight.ui.components.AnimatedXpBar
import com.droidnova.mathfight.ui.components.CompactStatisticChip
import com.droidnova.mathfight.ui.components.ConnectionStatusIndicator
import com.droidnova.mathfight.ui.components.ConnectionVisualState
import com.droidnova.mathfight.ui.components.GameDifficultySelector
import com.droidnova.mathfight.ui.components.GameLoadingState
import com.droidnova.mathfight.ui.components.GameModeCard
import com.droidnova.mathfight.ui.components.GameModeIcon
import com.droidnova.mathfight.ui.components.GamePrimaryButton
import com.droidnova.mathfight.ui.components.GameSecondaryButton
import com.droidnova.mathfight.ui.components.InlineMessage
import com.droidnova.mathfight.ui.components.PlayerAvatarBadge
import com.droidnova.mathfight.ui.components.SectionHeading
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
    onReturnHome: () -> Unit
    ,debugConnection: Boolean,
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
        BattlePhase.HOME -> if (leaderboardOpen) LeaderboardScreen(leaderboard, onCloseLeaderboard)
        else if (searchingWithoutRoom) MatchmakingScreen(displayName, profileStats, search, onCancelMatch)
        else HomeScreen(
            displayName = displayName,
            profileStats = profileStats,
            onStart = onStart,
            settings = settings,
            onSound = onSound,
            onVibration = onVibration,
            debugConnection = debugConnection,
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
            settings = settings,
            onSound = onSound,
            onVibration = onVibration,
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
    debugConnection: Boolean,
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
    onFindMatch: () -> Unit,
    searchError: String,
    difficulty: Difficulty,
    onDifficulty: (Difficulty) -> Unit,
    onLeaderboard: () -> Unit
) {
    val privateRoomVisible = rememberSaveable { mutableStateOf(room != null) }
    val connectionSettingsVisible = rememberSaveable { mutableStateOf(false) }
    val entered = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered.value = true }
    LaunchedEffect(room?.code) { if (room != null) privateRoomVisible.value = true }

    BackHandler(enabled = room == null && connectionSettingsVisible.value) {
        connectionSettingsVisible.value = false
    }

    val connectionVisualState = when (connectionStatus) {
        BattleViewModel.ConnectionStatus.CONNECTED -> ConnectionVisualState.ONLINE
        BattleViewModel.ConnectionStatus.CONNECTING -> ConnectionVisualState.CONNECTING
        else -> ConnectionVisualState.OFFLINE
    }
    val onlineEnabled = connectionStatus == BattleViewModel.ConnectionStatus.CONNECTED && room == null
    val onlineReason = when {
        room != null -> "Leave the private room before matchmaking"
        connectionStatus == BattleViewModel.ConnectionStatus.CONNECTING -> "Connecting to online play"
        else -> "Connect to play online"
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
        } else AnimatedVisibility(
            visible = entered.value,
            enter = fadeIn(tween(GameDimensions.standardMotionMillis)),
            exit = fadeOut(tween(GameDimensions.quickMotionMillis)),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = GameDimensions.screenPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(GameDimensions.sectionSpacing)
            ) {
                HomeHeader(displayName, connectionVisualState, room?.matchActive != true, onProfile)
                HomeHero()

                Column(verticalArrangement = Arrangement.spacedBy(GameDimensions.itemSpacing)) {
                    SectionHeading("Choose difficulty")
                    GameDifficultySelector(difficulty, onDifficulty)
                }

                Column(verticalArrangement = Arrangement.spacedBy(GameDimensions.itemSpacing)) {
                    SectionHeading("Choose your battle")
                    GameModeCard(
                        title = "Find Match",
                        supportingText = "Battle a random opponent",
                        icon = GameModeIcon.MATCH,
                        accent = GamePrimary,
                        enabled = onlineEnabled,
                        disabledReason = onlineReason,
                        prominent = true,
                        onClick = onFindMatch,
                        modifier = Modifier.fillMaxWidth()
                    )
                    GameModeCard(
                        title = "Play Offline",
                        supportingText = "Train against the bot",
                        icon = GameModeIcon.OFFLINE,
                        accent = GameSuccess,
                        enabled = room == null,
                        disabledReason = "Leave the private room to train offline",
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth()
                    )
                    GameModeCard(
                        title = "Private Room",
                        supportingText = "Create or join with a code",
                        icon = GameModeIcon.ROOM,
                        accent = GameSecondary,
                        enabled = room?.matchActive != true,
                        onClick = { privateRoomVisible.value = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (connectionVisualState == ConnectionVisualState.OFFLINE) {
                        InlineMessage("Online battles are unavailable. Offline battle is ready to play.", isError = false)
                        GameSecondaryButton(
                            label = "Reconnect",
                            onClick = onConnect,
                            icon = GameModeIcon.MATCH,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (connectionVisualState == ConnectionVisualState.CONNECTING) {
                        GameLoadingState("Connecting to online play")
                    }
                    InlineMessage(friendlyMatchmakingMessage(searchError))
                }

                PlayerProgressSection(profileStats, onProfile)

                Column(verticalArrangement = Arrangement.spacedBy(GameDimensions.itemSpacing)) {
                    SectionHeading("Explore")
                    GameSecondaryButton(
                        label = "Leaderboard",
                        onClick = onLeaderboard,
                        enabled = room?.matchActive != true,
                        icon = GameModeIcon.LEADERBOARD,
                        modifier = Modifier.fillMaxWidth()
                    )
                    GameSecondaryButton(
                        label = "Profile",
                        onClick = onProfile,
                        enabled = room?.matchActive != true,
                        icon = GameModeIcon.PROFILE,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(GameDimensions.compactSpacing)) {
                    SectionHeading("Preferences")
                    FeedbackControls(settings, onSound, onVibration)
                }

                if (debugConnection) {
                    GameSecondaryButton(
                        label = if (connectionSettingsVisible.value) "Hide connection settings" else "Connection settings",
                        onClick = { connectionSettingsVisible.value = !connectionSettingsVisible.value },
                        icon = GameModeIcon.SETTINGS,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AnimatedVisibility(connectionSettingsVisible.value) {
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
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun HomeHeader(
    displayName: String,
    connectionState: ConnectionVisualState,
    profileEnabled: Boolean,
    onProfile: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MATH FIGHT", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                Text("Robot maths battles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            ConnectionStatusIndicator(connectionState)
        }
        PlayerAvatarBadge(
            name = displayName,
            onClick = onProfile,
            enabled = profileEnabled,
            modifier = Modifier.widthIn(max = 220.dp)
        )
    }
}

@Composable
private fun HomeHero() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
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
            Column(Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)) {
                Text("Solve Fast. Strike First.", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Challenge opponents with your maths skills.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Image(
                    painter = painterResource(R.drawable.home_robot_duel),
                    contentDescription = "Blue and red robots ready to battle",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(800f / 280f)
                )
            }
        }
    }
}

@Composable
private fun PlayerProgressSection(stats: ProfileStats?, onProfile: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GameDimensions.itemSpacing)) {
        SectionHeading("Player progress", actionLabel = "View profile", onAction = onProfile)
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            if (stats == null) {
                Text(
                    "Progress is available when the server is connected.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompactStatisticChip("Rating", stats.rating.toString(), Modifier.widthIn(min = 100.dp), GamePrimary)
                        CompactStatisticChip("Tier", stats.tier, Modifier.widthIn(min = 100.dp), GameSecondary)
                        CompactStatisticChip("Win rate", "${(stats.winRate * 100).toInt()}%", Modifier.widthIn(min = 100.dp), GameSuccess)
                    }
                    stats.progression?.let { progression ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Level ${progression.level}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Text(
                                "${progression.xpIntoCurrentLevel} / ${progression.xpRequiredForNextLevel} XP",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AnimatedXpBar(progression.fraction, Modifier.fillMaxWidth())
                    }
                }
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
    settings: FeedbackSettings,
    onSound: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
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
    val controlsEnabled = isResumed && state.phase == BattlePhase.ANSWERING && (!online || !onlineAnswerLocked)
    val arenaHostKey = remember { Any() }
    val showLeaveDialog = rememberSaveable(state.battleId) { mutableStateOf(false) }
    val matchCompleted = state.phase == BattlePhase.RESULT

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
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HealthRow(state, isResumed, localName, opponentName)
            Text("Mode: ${difficulty.name.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.labelSmall)
            FeedbackControls(settings, onSound, onVibration)
            key(arenaHostKey) {
                LibGdxBattleArena(
                    state = state,
                    isResumed = isResumed,
                    onlinePaused = onlinePaused,
                    consumeVisualEvent = consumeVisualEvent,
                    modifier = Modifier.fillMaxWidth().weight(0.34f)
                )
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
                    modifier = Modifier.fillMaxWidth().weight(0.66f)
                )
            } else {
                Text(
                    text = onlineQuestionPrompt.ifEmpty { state.question?.display.orEmpty() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (online) {
                    Text(
                        text = when {
                            onlineQuestionTimer.expired -> "Time\u2019s up \u00b7 0"
                            onlineQuestionTimer.visible -> onlineQuestionTimer.seconds.toString()
                            else -> " "
                        },
                        color = if (onlineQuestionTimer.warning) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.heightIn(min = 20.dp)
                    )
                }
                Text(
                    text = state.input.ifEmpty { " " },
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (state.wrongAnswer) "Try again" else " ",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (onlineSubmissionStatus.isNotEmpty()) {
                    Text(onlineSubmissionStatus, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (canRetry) TextButton(onClick = onRetry) { Text("Retry connection") }
                Keypad(
                    enabled = controlsEnabled,
                    onDigit = onDigit,
                    onBackspace = onBackspace,
                    onClear = onClear,
                    onSubmit = onSubmit,
                    modifier = Modifier.fillMaxWidth().weight(0.66f)
                )
            }
        }
    }
}

@Composable
private fun LeaderboardScreen(state: LeaderboardState, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Leaderboard", style = MaterialTheme.typography.headlineSmall)
            Text("Top robot battlers", color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                state.loading -> GameLoadingState("Loading leaderboard", Modifier.padding(top = 8.dp))
                !state.connected -> InlineMessage(
                    friendlyOnlineMessage(state.error).ifBlank { "Connect to view the leaderboard." },
                    isError = false
                )
                state.error.isNotBlank() -> InlineMessage(friendlyOnlineMessage(state.error))
                state.rows.isEmpty() -> InlineMessage("No ranked players yet.", isError = false)
                else -> {
                    if (state.currentPosition > 0) {
                        Text("Your position: #${state.currentPosition}", color = MaterialTheme.colorScheme.primary)
                    }
                    state.rows.forEach { row -> LeaderboardRowCard(row) }
                }
            }
            GameSecondaryButton("Back to Home", onBack, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

@Composable
private fun LeaderboardRowCard(row: LeaderboardRow) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (row.current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (row.current) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("#${row.position}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(row.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${row.tier} • W${row.wins} L${row.losses}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(row.rating.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Development connection", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = serverUrl,
                onValueChange = onServerUrl,
                singleLine = true,
                label = { Text("Server URL") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GamePrimaryButton(
                    "Connect",
                    onConnect,
                    Modifier.weight(1f),
                    enabled = status != BattleViewModel.ConnectionStatus.CONNECTING
                )
                GameSecondaryButton("Disconnect", onDisconnect, Modifier.weight(1f))
            }
            if (status != BattleViewModel.ConnectionStatus.IDLE || message.isNotEmpty()) {
                Text(
                    "${status.name.lowercase().replace('_', ' ')}: $message",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == BattleViewModel.ConnectionStatus.ERROR) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FeedbackControls(settings: FeedbackSettings, onSound: (Boolean) -> Unit,
                             onVibration: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GameSecondaryButton(
            label = "Sound ${if (settings.sound) "On" else "Off"}",
            onClick = { onSound(!settings.sound) },
            modifier = Modifier.fillMaxWidth()
        )
        GameSecondaryButton(
            label = "Vibration ${if (settings.vibration) "On" else "Off"}",
            onClick = { onVibration(!settings.vibration) },
            modifier = Modifier.fillMaxWidth()
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
            value.contains("socket", ignoreCase = true) || value.contains("exception", ignoreCase = true) ->
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
    val progress = remember { Animatable(STARTING_HP.toFloat()) }
    LaunchedEffect(hp, isResumed) {
        if (isResumed) progress.animateTo(hp.toFloat(), tween(260)) else progress.snapTo(hp.toFloat())
    }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("HP: $hp", style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(
            progress = { progress.value / STARTING_HP },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
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
            KeyButton("⌫", "Backspace", enabled, onBackspace, Modifier.weight(1f))
        }
        Button(
            onClick = onSubmit,
            enabled = enabled,
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
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxSize().heightIn(min = 48.dp)
            .semantics { contentDescription = description }
    ) {
        Text(text)
    }
}
