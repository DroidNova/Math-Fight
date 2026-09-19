package com.droidnova.mathfight.ui.battle

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.clipToBounds
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun MathFightApp(
    displayName: String,
    onProfile: () -> Unit,
    search: MatchSearchState,
    onFindMatch: () -> Unit,
    onCancelMatch: () -> Unit,
    difficulty: Difficulty,
    onDifficulty: (Difficulty) -> Unit,
    state: BattleState,
    isResumed: Boolean,
    impactToken: PhaseKey?,
    consumeImpact: (PhaseKey) -> Boolean,
    settings: FeedbackSettings,
    onSound: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
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
    onRoomCode: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onLeaveRoom: () -> Unit,
    onReady: () -> Unit
) {
    val localName = onlineMatch?.localName ?: displayName
    val opponentName = onlineMatch?.opponentName ?: "Bot"
    val searchingWithoutRoom = search.active && room == null
    BackHandler(enabled = searchingWithoutRoom || state.phase != BattlePhase.HOME || room != null ||
        connectionStatus == BattleViewModel.ConnectionStatus.CONNECTED ||
        connectionStatus == BattleViewModel.ConnectionStatus.CONNECTING,
        onBack = if (searchingWithoutRoom) onCancelMatch else onReturnHome)
    when (state.phase) {
        BattlePhase.HOME -> if (searchingWithoutRoom) SearchScreen(displayName, search, onCancelMatch)
        else HomeScreen(onStart, settings, onSound, onVibration,
            debugConnection, serverUrl, connectionStatus, connectionMessage, onServerUrl, onConnect, onDisconnect,
            roomCodeInput, room, roomError, onRoomCode, onCreateRoom, onJoinRoom, onLeaveRoom, onReady, onProfile, onFindMatch,
            difficulty, onDifficulty)
        BattlePhase.RESULT -> ResultScreen(state.winner, onRestart, onlineMatch != null, onlineSubmissionStatus, localName, opponentName)
        else -> BattleScreen(
            state = state,
            isResumed = isResumed,
            impactToken = impactToken,
            consumeImpact = consumeImpact,
            settings = settings,
            onSound = onSound,
            onVibration = onVibration,
            onDigit = onDigit,
            onBackspace = onBackspace,
            onClear = onClear,
            onSubmit = onSubmit
            ,online = onlineMatch != null, onlineAnswerLocked = onlineAnswerLocked,
            onlineSubmissionStatus = onlineSubmissionStatus,
            difficulty = onlineMatch?.difficulty ?: difficulty,
            canRetry = onlineMatch != null && connectionStatus == BattleViewModel.ConnectionStatus.DISCONNECTED,
            onRetry = onConnect,
            localName = localName,
            opponentName = opponentName
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
                       onFindMatch: () -> Unit, difficulty: Difficulty, onDifficulty: (Difficulty) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Math Fight", style = MaterialTheme.typography.displaySmall)
            TextButton(onClick = onProfile, enabled = room?.matchActive != true) { Text("Profile") }
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
            Button(onClick = onStart, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Start Battle")
            }
            Button(onClick = onFindMatch, enabled = connectionStatus == BattleViewModel.ConnectionStatus.CONNECTED && room == null,
                modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) {
                Text("Find Match")
            }
            FeedbackControls(settings, onSound, onVibration)
        }
    }
}

@Composable
private fun ResultScreen(winner: Fighter?, onRestart: () -> Unit, online: Boolean, message: String,
                         localName: String, opponentName: String) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (winner == Fighter.PLAYER) "You win!" else "You lose!",
                style = MaterialTheme.typography.displaySmall
            )
            Text("$localName vs $opponentName", modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
            if (online && message.isNotBlank()) Text(message, modifier = Modifier.padding(top = 12.dp))
            Button(
                onClick = onRestart,
                modifier = Modifier.padding(top = 28.dp).heightIn(min = 48.dp)
            ) {
                Text(if (online) "Return to Lobby" else "Restart")
            }
        }
    }
}

@Composable
private fun BattleScreen(
    state: BattleState,
    isResumed: Boolean,
    impactToken: PhaseKey?,
    consumeImpact: (PhaseKey) -> Boolean,
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
    difficulty: Difficulty,
    canRetry: Boolean,
    onRetry: () -> Unit,
    localName: String,
    opponentName: String
) {
    val controlsEnabled = isResumed && state.phase == BattlePhase.ANSWERING && (!online || !onlineAnswerLocked)
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HealthRow(state, isResumed, localName, opponentName)
            Text("Mode: ${difficulty.name.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.labelSmall)
            FeedbackControls(settings, onSound, onVibration)
            FighterArena(
                state = state,
                isResumed = isResumed,
                impactToken = impactToken,
                consumeImpact = consumeImpact,
                modifier = Modifier.fillMaxWidth().weight(0.34f)
            )
            Text(
                text = state.question?.display.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
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

@Composable
private fun SearchScreen(displayName: String, search: MatchSearchState, onCancel: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text("Finding an opponent…", style = MaterialTheme.typography.headlineSmall)
            Text(displayName, modifier = Modifier.padding(top = 12.dp))
            Text("Mode: ${search.difficulty.name.lowercase().replaceFirstChar { it.uppercase() }}")
            if (search.error.isNotBlank()) Text(search.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.padding(top = 24.dp)) { Text("Cancel Search") }
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
                if (room.role.equals("Host", true) && !room.matchActive) DifficultySelector(room.difficulty, onDifficulty)
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
private fun FighterArena(state: BattleState, isResumed: Boolean, impactToken: PhaseKey?,
                         consumeImpact: (PhaseKey) -> Boolean,
                         modifier: Modifier = Modifier) {
    val playerMotion = remember { Animatable(0f) }
    val botMotion = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val burst = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    val animationKey = state.key
    LaunchedEffect(animationKey, impactToken, isResumed) {
        flash.snapTo(0f)
        burst.snapTo(1f)
        shake.snapTo(0f)
        if (!isResumed || state.phase != BattlePhase.IMPACT || impactToken != animationKey) {
            return@LaunchedEffect
        }
        if (!consumeImpact(animationKey)) return@LaunchedEffect
        coroutineScope {
            launch { flash.snapTo(1f); flash.animateTo(0f, tween(100)) }
            launch { burst.snapTo(0f); burst.animateTo(1f, tween(140)) }
            launch {
                shake.animateTo(0f, keyframes {
                    durationMillis = 160
                    0f at 0; -1f at 25; 1f at 55; -0.6f at 85; 0.3f at 120; 0f at 160
                })
            }
        }
    }
    LaunchedEffect(animationKey, isResumed) {
        playerMotion.snapTo(
            if (state.phase == BattlePhase.IMPACT && state.attacker == Fighter.PLAYER) 1f else 0f
        )
        botMotion.snapTo(
            if (state.phase == BattlePhase.IMPACT && state.attacker == Fighter.BOT) 1f else 0f
        )
        if (!isResumed) return@LaunchedEffect
        when (state.phase) {
            BattlePhase.WINDUP -> when (state.attacker) {
                Fighter.PLAYER -> playerMotion.animateTo(1f, tween(180))
                Fighter.BOT -> botMotion.animateTo(1f, tween(180))
                null -> Unit
            }
            BattlePhase.IMPACT -> coroutineScope {
                val attackerMotion = if (state.attacker == Fighter.PLAYER) playerMotion else botMotion
                val defenderMotion = if (state.attacker == Fighter.PLAYER) botMotion else playerMotion
                launch { attackerMotion.animateTo(0f, tween(320)) }
                launch {
                    defenderMotion.animateTo(1f, tween(70))
                    defenderMotion.animateTo(0f, tween(250))
                }
            }
            BattlePhase.KO -> when (state.winner) {
                Fighter.PLAYER -> botMotion.animateTo(1f, tween(420))
                Fighter.BOT -> playerMotion.animateTo(1f, tween(420))
                null -> Unit
            }
            else -> Unit
        }
    }

    val playerColor = MaterialTheme.colorScheme.primary
    val opponentColor = MaterialTheme.colorScheme.tertiary
    Canvas(
        modifier = modifier.clipToBounds().semantics {
            contentDescription = "Player fighter facing bot fighter"
        }
    ) {
        val ground = size.height * 0.88f
        // Width bound leaves room for an outward fall, even in a tall/narrow arena.
        val figureHeight = minOf(size.height * 0.64f, size.width * 0.22f)
        val playerAttacking = state.attacker == Fighter.PLAYER
        val botAttacking = state.attacker == Fighter.BOT
        val combatPose = state.phase == BattlePhase.WINDUP || state.phase == BattlePhase.IMPACT
        val reach = (size.width * 0.5f - figureHeight * 0.67f).coerceAtLeast(0f)
        val playerX = size.width * 0.25f + if (combatPose) playerMotion.value *
            (if (playerAttacking) reach else -figureHeight * 0.12f) else 0f
        val botX = size.width * 0.75f + if (combatPose) botMotion.value *
            (if (botAttacking) -reach else figureHeight * 0.12f) else 0f
        val showImpact = isResumed && impactToken == animationKey && state.phase == BattlePhase.IMPACT
        translate(left = if (showImpact) shake.value * 3.dp.toPx() else 0f) {
        drawLine(
            color = Color.Gray.copy(alpha = 0.35f),
            start = Offset(size.width * 0.05f, ground),
            end = Offset(size.width * 0.95f, ground),
            strokeWidth = 3f
        )
        rotate(
            degrees = -72f * playerMotion.value.takeIf {
                state.phase == BattlePhase.KO && state.winner == Fighter.BOT
            }.orZero() + if (combatPose && playerAttacking) 10f * playerMotion.value else 0f,
            pivot = Offset(playerX, ground)
        ) {
            drawFighter(
                centerX = playerX,
                groundY = ground,
                height = figureHeight,
                color = lerp(playerColor, Color.White, if (showImpact && botAttacking) flash.value else 0f),
                facingRight = true,
                striking = if (combatPose && playerAttacking) playerMotion.value else 0f
            )
        }
        rotate(
            degrees = 72f * botMotion.value.takeIf {
                state.phase == BattlePhase.KO && state.winner == Fighter.PLAYER
            }.orZero() - if (combatPose && botAttacking) 10f * botMotion.value else 0f,
            pivot = Offset(botX, ground)
        ) {
            drawFighter(
                centerX = botX,
                groundY = ground,
                height = figureHeight,
                color = lerp(opponentColor, Color.White, if (showImpact && playerAttacking) flash.value else 0f),
                facingRight = false,
                striking = if (combatPose && botAttacking) botMotion.value else 0f
            )
        }
        if (showImpact && burst.value < 1f) {
            val contact = Offset(size.width * (if (playerAttacking) 0.75f else 0.25f),
                ground - figureHeight * 0.66f)
            val radius = figureHeight * (0.06f + 0.18f * burst.value)
            repeat(6) { ray ->
                rotate(ray * 60f, contact) {
                    drawLine(Color.White.copy(alpha = 1f - burst.value),
                        contact + Offset(radius * 0.4f, 0f), contact + Offset(radius, 0f),
                        strokeWidth = 2.dp.toPx())
                }
            }
        }
        }
    }
}

private fun Float?.orZero(): Float = this ?: 0f

private fun DrawScope.drawFighter(
    centerX: Float,
    groundY: Float,
    height: Float,
    color: Color,
    facingRight: Boolean,
    striking: Float
) {
    val direction = if (facingRight) 1f else -1f
    val headRadius = height * 0.12f
    val headY = groundY - height + headRadius
    val shoulderY = headY + headRadius * 1.8f
    val hipY = groundY - height * 0.28f
    val stroke = height * 0.075f
    drawCircle(color, headRadius, Offset(centerX, headY))
    drawLine(color, Offset(centerX, shoulderY), Offset(centerX, hipY), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(color, Offset(centerX, hipY), Offset(centerX - height * 0.14f, groundY), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(color, Offset(centerX, hipY), Offset(centerX + height * 0.14f, groundY), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    val handReach = height * (0.26f + 0.28f * striking)
    drawLine(
        color,
        Offset(centerX, shoulderY),
        Offset(centerX + direction * handReach, shoulderY + height * 0.03f),
        stroke,
        cap = androidx.compose.ui.graphics.StrokeCap.Round
    )
    drawLine(
        color,
        Offset(centerX, shoulderY + height * 0.05f),
        Offset(centerX - direction * height * 0.20f, shoulderY + height * 0.20f),
        stroke,
        cap = androidx.compose.ui.graphics.StrokeCap.Round
    )
    drawCircle(Color.White, headRadius * 0.12f, Offset(centerX + direction * headRadius * 0.38f, headY - headRadius * 0.12f))
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
