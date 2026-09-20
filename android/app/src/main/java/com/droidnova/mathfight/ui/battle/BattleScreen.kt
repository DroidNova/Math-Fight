package com.droidnova.mathfight.ui.battle

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.droidnova.mathfight.game.BattlePhase
import com.droidnova.mathfight.game.BattleState
import com.droidnova.mathfight.game.Difficulty
import com.droidnova.mathfight.game.Fighter
import com.droidnova.mathfight.game.STARTING_HP
import com.droidnova.mathfight.game.PhaseKey
import com.droidnova.mathfight.profile.ProfileStats
import com.droidnova.mathfight.profile.XpResult
import com.droidnova.mathfight.profile.XpResultPanel
import com.droidnova.mathfight.ui.battle.arena.LibGdxBattleArena

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
        else if (searchingWithoutRoom) SearchScreen(displayName, profileStats, search, onCancelMatch)
        else HomeScreen(onStart, settings, onSound, onVibration,
            debugConnection, serverUrl, connectionStatus, connectionMessage, onServerUrl, onConnect, onDisconnect,
             roomCodeInput, room, roomError, onRoomCode, onCreateRoom, onJoinRoom, onLeaveRoom, onReady, onProfile, onFindMatch,
             search.error, difficulty, onDifficulty, onLeaderboard)
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
private fun HomeScreen(onStart: () -> Unit, settings: FeedbackSettings,
                       onSound: (Boolean) -> Unit, onVibration: (Boolean) -> Unit,
                       debugConnection: Boolean, serverUrl: String,
                       connectionStatus: BattleViewModel.ConnectionStatus, connectionMessage: String,
                       onServerUrl: (String) -> Unit, onConnect: () -> Unit, onDisconnect: () -> Unit,
                       roomCodeInput: String, room: RoomInfo?, roomError: String,
                       onRoomCode: (String) -> Unit, onCreateRoom: () -> Unit,
                        onJoinRoom: () -> Unit, onLeaveRoom: () -> Unit, onReady: () -> Unit, onProfile: () -> Unit,
                        onFindMatch: () -> Unit, searchError: String, difficulty: Difficulty,
                        onDifficulty: (Difficulty) -> Unit, onLeaderboard: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Math Fight", style = MaterialTheme.typography.displaySmall)
            TextButton(onClick = onProfile, enabled = room?.matchActive != true) { Text("Profile") }
            TextButton(onClick = onLeaderboard, enabled = room?.matchActive != true) { Text("Leaderboard") }
            DifficultySelector(difficulty, onDifficulty)
            if (debugConnection) {
            ConnectionCheckPanel(serverUrl, connectionStatus, connectionMessage,
                    onServerUrl, onConnect, onDisconnect, roomCodeInput, room, roomError,
                    onRoomCode, onCreateRoom, onJoinRoom, onLeaveRoom, onReady, difficulty, onDifficulty)
            }
            Text(
                "Solve. Strike. Win.",
                modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onStart, enabled = room == null, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Start Battle")
            }
            Button(onClick = onFindMatch, enabled = connectionStatus == BattleViewModel.ConnectionStatus.CONNECTED && room == null,
                modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) {
                Text("Find Match")
            }
            if (searchError.isNotBlank()) Text(searchError, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            FeedbackControls(settings, onSound, onVibration)
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
private fun SearchScreen(displayName: String, profileStats: ProfileStats?, search: MatchSearchState, onCancel: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text("Finding an opponent…", style = MaterialTheme.typography.headlineSmall)
            Text(displayName, modifier = Modifier.padding(top = 12.dp))
            profileStats?.let { Text("Rating: ${it.rating} • ${it.tier}") }
            Text("Mode: ${search.difficulty.name.lowercase().replaceFirstChar { it.uppercase() }}")
            if (search.error.isNotBlank()) Text(search.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.padding(top = 24.dp)) { Text("Cancel Search") }
        }
    }
}

@Composable
private fun LeaderboardScreen(state: LeaderboardState, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Leaderboard", style = MaterialTheme.typography.headlineSmall)
            when {
                state.loading -> Text("Loading…", Modifier.padding(top = 20.dp))
                !state.connected -> Text(state.error.ifBlank { "Connect to the server" }, Modifier.padding(top = 20.dp))
                state.error.isNotBlank() -> Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 20.dp))
                state.rows.isEmpty() -> Text("No ranked players yet", Modifier.padding(top = 20.dp))
                else -> {
                    if (state.currentPosition > 0) Text("Your position: #${state.currentPosition}", Modifier.padding(top = 12.dp))
                    state.rows.forEach { row -> Text("#${row.position}  ${row.displayName}  ${row.rating} (${row.tier})  W${row.wins} L${row.losses}", fontWeight = if (row.current) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
                }
            }
            OutlinedButton(onClick = onBack, Modifier.padding(top = 24.dp)) { Text("Back") }
        }
    }
}

@Composable
private fun ConnectionCheckPanel(
    serverUrl: String,
    status: BattleViewModel.ConnectionStatus,
    message: String,
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
    difficulty: Difficulty,
    onDifficulty: (Difficulty) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("Connection check", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrl,
            singleLine = true,
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Button(onClick = onConnect, enabled = status != BattleViewModel.ConnectionStatus.CONNECTING) {
                Text("Connect")
            }
            OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
        }
        if (status != BattleViewModel.ConnectionStatus.IDLE || message.isNotEmpty()) {
            Text("${status.name.lowercase().replace('_', ' ')}: $message",
                style = MaterialTheme.typography.bodySmall,
                color = if (status == BattleViewModel.ConnectionStatus.ERROR)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
        }
        if (status == BattleViewModel.ConnectionStatus.CONNECTED) {
            if (room == null) {
                Text("Private room difficulty", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                DifficultySelector(difficulty, onDifficulty)
                Button(onClick = onCreateRoom, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Create Room")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = roomCodeInput,
                        onValueChange = onRoomCode,
                        singleLine = true,
                        label = { Text("Room code") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = onJoinRoom, modifier = Modifier.align(Alignment.CenterVertically)) {
                        Text("Join Room")
                    }
                }
            } else {
                Text("Room: ${room.code}", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp))
                Text("Role: ${room.role}")
                Text("Players: ${room.playerCount}/2")
                Text("Difficulty: ${room.difficulty.name.lowercase().replaceFirstChar { it.uppercase() }}")
                if (room.ranked) Text("Ranked • Host ${room.hostRating} ${room.hostTier} • Guest ${room.guestRating ?: "—"} ${room.guestTier ?: ""}")
                if (!room.ranked && room.role.equals("Host", true) && !room.matchActive) DifficultySelector(room.difficulty, onDifficulty)
                Text("Host: ${room.hostName} • ${if (room.hostReady) "Ready" else "Not ready"}")
                Text("Guest: ${room.guestName.ifBlank { "Waiting…" }} • ${if (room.guestReady) "Ready" else "Not ready"}")
                Text(if (room.playerCount == 2) "Both players connected" else "Waiting for opponent…")
                if (!room.matchActive && room.playerCount == 2) {
                    val ownReady = if (room.role.equals("Host", true)) room.hostReady else room.guestReady
                    Button(onClick = onReady) { Text(if (ownReady) "Cancel Ready" else "Ready") }
                }
                OutlinedButton(onClick = onLeaveRoom) { Text("Leave Room") }
            }
            if (roomError.isNotEmpty()) {
                Text(roomError, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DifficultySelector(selected: Difficulty, onSelected: (Difficulty) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Difficulty.entries.forEach { value ->
            TextButton(onClick = { onSelected(value) }) {
                Text(if (value == selected) "[${value.name.lowercase().replaceFirstChar { it.uppercase() }}]" else value.name.lowercase().replaceFirstChar { it.uppercase() })
            }
        }
    }
}

@Composable
private fun FeedbackControls(settings: FeedbackSettings, onSound: (Boolean) -> Unit,
                             onVibration: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onSound(!settings.sound) }) {
            Text("Sound: ${if (settings.sound) "On" else "Off"}")
        }
        TextButton(onClick = { onVibration(!settings.vibration) }) {
            Text("Vibration: ${if (settings.vibration) "On" else "Off"}")
        }
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
