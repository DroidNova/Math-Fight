package com.droidnova.mathfight.ui.battle

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.droidnova.mathfight.R
import com.droidnova.mathfight.game.Difficulty
import com.droidnova.mathfight.profile.ProfileStats
import com.droidnova.mathfight.ui.components.GameDifficultySelector
import com.droidnova.mathfight.ui.components.GameIconButton
import com.droidnova.mathfight.ui.components.GameModeIcon
import com.droidnova.mathfight.ui.components.GamePrimaryButton
import com.droidnova.mathfight.ui.components.GameSecondaryButton
import com.droidnova.mathfight.ui.components.GameSegmentedControl
import com.droidnova.mathfight.ui.components.GameStatusPill
import com.droidnova.mathfight.ui.components.InlineMessage
import com.droidnova.mathfight.ui.theme.GameDimensions
import com.droidnova.mathfight.ui.theme.GameDisabledText
import com.droidnova.mathfight.ui.theme.GamePrimary
import com.droidnova.mathfight.ui.theme.GameSecondary
import com.droidnova.mathfight.ui.theme.GameSuccess
import com.droidnova.mathfight.ui.theme.GameWarning
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val CREATE_MODE = "Create Room"
private const val JOIN_MODE = "Join Room"

@Composable
internal fun MatchmakingScreen(
    displayName: String,
    profileStats: ProfileStats?,
    search: MatchSearchState,
    onCancel: () -> Unit
) {
    val cancelling = search.status == "cancelling"
    val matched = search.status == "matched"
    val actionLocked = cancelling || matched
    BackHandler { if (!actionLocked) onCancel() }
    ArenaLobbyBackdrop {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val compact = maxHeight < 590.dp || LocalDensity.current.fontScale > 1.2f
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
            ) {
                CompactScreenHeader("Finding Match", onCancel, backEnabled = !actionLocked)
                RadarVisual(if (compact) 188.dp else 236.dp)
                Text(
                    when {
                        matched -> "Match found…"
                        cancelling -> "Cancelling search…"
                        else -> "Finding an opponent…"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    profileStats?.let {
                        GameStatusPill("${it.tier} • ${it.rating}", GamePrimary)
                    }
                    GameStatusPill(difficultyLabel(search.difficulty), GameSecondary)
                }
                InlineMessage(friendlyMatchmakingMessage(search.error))
                Spacer(Modifier.height(if (compact) 2.dp else 8.dp))
                GamePrimaryButton(
                    label = when {
                        matched -> "Joining lobby…"
                        cancelling -> "Cancelling…"
                        else -> "Cancel Search"
                    },
                    onClick = onCancel,
                    enabled = !actionLocked,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)
                )
            }
        }
    }
}

@Composable
internal fun PrivateRoomFlow(
    displayName: String,
    status: BattleViewModel.ConnectionStatus,
    difficulty: Difficulty,
    roomCodeInput: String,
    room: RoomInfo?,
    roomError: String,
    onRoomCode: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onReady: () -> Unit,
    onConnect: () -> Unit,
    onDifficulty: (Difficulty) -> Unit,
    onBack: () -> Unit
) {
    var pendingRequest by rememberSaveable { mutableStateOf("") }
    val connected = status == BattleViewModel.ConnectionStatus.CONNECTED
    LaunchedEffect(room?.code, roomError, status) {
        if (room != null || roomError.isNotBlank() || !connected) pendingRequest = ""
    }
    LaunchedEffect(pendingRequest) {
        if (pendingRequest.isNotBlank()) {
            delay(5_500)
            pendingRequest = ""
        }
    }
    val guardedBack = {
        if (room != null || pendingRequest.isBlank()) onBack()
    }
    BackHandler { guardedBack() }
    ArenaLobbyBackdrop {
        if (room == null) {
            RoomEntryScreen(
                displayName = displayName,
                status = status,
                difficulty = difficulty,
                code = roomCodeInput,
                error = roomError,
                onCode = onRoomCode,
                onCreate = onCreateRoom,
                onJoin = onJoinRoom,
                onConnect = onConnect,
                onDifficulty = onDifficulty,
                pendingRequest = pendingRequest,
                onPendingRequest = { pendingRequest = it },
                onBack = guardedBack
            )
        } else {
            LobbyScreen(
                room = room,
                status = status,
                error = roomError,
                onDifficulty = onDifficulty,
                onReady = onReady,
                onBack = guardedBack
            )
        }
    }
}

@Composable
private fun RoomEntryScreen(
    displayName: String,
    status: BattleViewModel.ConnectionStatus,
    difficulty: Difficulty,
    code: String,
    error: String,
    onCode: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onConnect: () -> Unit,
    onDifficulty: (Difficulty) -> Unit,
    pendingRequest: String,
    onPendingRequest: (String) -> Unit,
    onBack: () -> Unit
) {
    var selectedMode by rememberSaveable { mutableStateOf(if (code.isBlank()) CREATE_MODE else JOIN_MODE) }
    val connected = status == BattleViewModel.ConnectionStatus.CONNECTED
    val validCode = code.length == 6

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        val compact = maxHeight < 590.dp || LocalDensity.current.fontScale > 1.2f
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
        ) {
            CompactScreenHeader("Private Room", onBack, backEnabled = pendingRequest.isBlank())
            GameSegmentedControl(
                options = listOf(CREATE_MODE, JOIN_MODE),
                selected = selectedMode,
                onSelected = {
                    selectedMode = it
                    onPendingRequest("")
                    onCode(code)
                },
                enabled = pendingRequest.isBlank()
            )

            if (!connected) {
                InlineMessage(
                    if (status == BattleViewModel.ConnectionStatus.CONNECTING) "Connecting…" else "Connection lost",
                    isError = status != BattleViewModel.ConnectionStatus.CONNECTING
                )
            }

            AnimatedVisibility(visible = selectedMode == CREATE_MODE) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.lobby_robot_blue),
                        contentDescription = "Blue host robot",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(if (compact) 112.dp else 142.dp)
                    )
                    Text(
                        "$displayName hosts",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    GameStatusPill(difficultyLabel(difficulty), GamePrimary)
                    GameDifficultySelector(
                        difficulty,
                        onDifficulty,
                        compact = true,
                        enabled = pendingRequest.isBlank()
                    )
                    if (pendingRequest == CREATE_MODE) {
                        GameStatusPill("Creating…", GameWarning)
                    }
                    GamePrimaryButton(
                        label = if (pendingRequest == CREATE_MODE) "Creating…" else "Create Room",
                        onClick = {
                            if (pendingRequest.isBlank()) {
                                onCode(code)
                                onPendingRequest(CREATE_MODE)
                                onCreate()
                            }
                        },
                        enabled = connected && pendingRequest.isBlank(),
                        icon = GameModeIcon.ROOM,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            AnimatedVisibility(visible = selectedMode == JOIN_MODE) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 12.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.home_robot_duel),
                        contentDescription = "Two robots ready for a private battle",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).aspectRatio(800f / 280f)
                    )
                    Text("Enter room code", style = MaterialTheme.typography.titleMedium)
                    RoomCodeInput(
                        value = code,
                        onValueChange = onCode,
                        enabled = connected && pendingRequest.isBlank(),
                        onSubmit = {
                            if (connected && validCode && pendingRequest.isBlank()) {
                                onCode(code)
                                onPendingRequest(JOIN_MODE)
                                onJoin()
                            }
                        }
                    )
                    GamePrimaryButton(
                        label = if (pendingRequest == JOIN_MODE) "Joining…" else "Join Room",
                        onClick = {
                            if (validCode && pendingRequest.isBlank()) {
                                onCode(code)
                                onPendingRequest(JOIN_MODE)
                                onJoin()
                            }
                        },
                        enabled = connected && validCode && pendingRequest.isBlank(),
                        icon = GameModeIcon.ROOM,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            InlineMessage(friendlyRoomMessage(error))
            if (!connected && status != BattleViewModel.ConnectionStatus.CONNECTING) {
                GameSecondaryButton("Reconnect", onConnect, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun LobbyScreen(
    room: RoomInfo,
    status: BattleViewModel.ConnectionStatus,
    error: String,
    onDifficulty: (Difficulty) -> Unit,
    onReady: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val localIsHost = room.role.equals("Host", true)
    val localName = if (localIsHost) room.hostName else room.guestName
    val opponentName = if (localIsHost) room.guestName else room.hostName
    val localReady = if (localIsHost) room.hostReady else room.guestReady
    val opponentReady = if (localIsHost) room.guestReady else room.hostReady
    val localRating = if (localIsHost) room.hostRating else room.guestRating
    val opponentRating = if (localIsHost) room.guestRating else room.hostRating
    val localTier = if (localIsHost) room.hostTier else room.guestTier.orEmpty()
    val opponentTier = if (localIsHost) room.guestTier.orEmpty() else room.hostTier
    val connected = status == BattleViewModel.ConnectionStatus.CONNECTED
    val hasOpponent = room.playerCount == 2 && opponentName.isNotBlank()
    val bothReady = hasOpponent && room.hostReady && room.guestReady
    var readyRequestPending by rememberSaveable(room.code) { mutableStateOf(false) }
    var copied by rememberSaveable(room.code) { mutableStateOf(false) }
    var shareFailed by rememberSaveable(room.code) { mutableStateOf(false) }
    var matchStartFlashed by rememberSaveable(room.code) { mutableStateOf(false) }
    val matchStartFlash = remember { Animatable(0f) }

    LaunchedEffect(localReady, error, status) { readyRequestPending = false }
    LaunchedEffect(readyRequestPending) {
        if (readyRequestPending) {
            delay(3_000)
            readyRequestPending = false
        }
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_500)
            copied = false
        }
    }
    LaunchedEffect(bothReady) {
        if (bothReady && !matchStartFlashed) {
            matchStartFlashed = true
            matchStartFlash.snapTo(0.28f)
            matchStartFlash.animateTo(0f, tween(GameDimensions.standardMotionMillis))
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val stackedPlayers = maxWidth < 330.dp || LocalDensity.current.fontScale > 1.35f
        val compact = maxHeight < 590.dp || LocalDensity.current.fontScale > 1.2f
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
        ) {
            CompactScreenHeader("Battle Lobby", onBack)
            RoomCodeHeader(
                code = room.code,
                copied = copied,
                onCopy = {
                    copyRoomCode(context, room.code)
                    copied = true
                    shareFailed = false
                },
                onShare = {
                    shareFailed = !shareRoomCode(context, room.code)
                }
            )

            if (!connected) InlineMessage("Connection lost")
            if (shareFailed) InlineMessage("Unable to open sharing.")

            PlayerVersusLayout(
                stacked = stackedPlayers,
                compact = compact,
                hasOpponent = hasOpponent,
                localName = localName,
                opponentName = opponentName,
                localReady = localReady,
                opponentReady = opponentReady,
                localHost = localIsHost,
                opponentHost = !localIsHost,
                localConnected = connected,
                localRating = localRating,
                opponentRating = opponentRating,
                localTier = localTier,
                opponentTier = opponentTier
            )

            LobbyConfiguration(room, localIsHost, onDifficulty)
            InlineMessage(friendlyRoomMessage(error))

            val readyLabel = when {
                !connected -> "Connection lost"
                room.matchActive || bothReady -> "Starting…"
                !hasOpponent -> "Waiting for opponent"
                readyRequestPending -> "Starting…"
                localReady -> "Ready ✓"
                else -> "Ready"
            }
            GamePrimaryButton(
                label = readyLabel,
                onClick = {
                    if (!readyRequestPending) {
                        readyRequestPending = true
                        onReady()
                    }
                },
                enabled = connected && hasOpponent && !bothReady && !readyRequestPending && !room.matchActive,
                icon = if (localReady) GameModeIcon.CHECK else null,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(2.dp))
        }
        Box(Modifier.fillMaxSize().background(GamePrimary.copy(alpha = matchStartFlash.value)))
    }
}

@Composable
private fun CompactScreenHeader(title: String, onBack: () -> Unit, backEnabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GameIconButton(GameModeIcon.BACK, "Back", onBack, enabled = backEnabled)
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.size(GameDimensions.touchTarget))
    }
}

@Composable
private fun RadarVisual(size: Dp) {
    val transition = rememberInfiniteTransition(label = "matchmaking radar")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar pulse"
    )
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Searching radar" }) {
            val centre = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxRadius = this.size.minDimension * 0.48f
            repeat(3) { index ->
                drawCircle(
                    color = GamePrimary.copy(alpha = 0.18f - index * 0.035f),
                    radius = maxRadius * (0.38f + index * 0.27f),
                    center = centre,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            val firstPulse = phase
            val secondPulse = (phase + 0.5f) % 1f
            drawCircle(
                color = GamePrimary.copy(alpha = (1f - firstPulse) * 0.55f),
                radius = maxRadius * (0.35f + firstPulse * 0.65f),
                center = centre,
                style = Stroke(width = 3.dp.toPx())
            )
            drawCircle(
                color = GamePrimary.copy(alpha = (1f - secondPulse) * 0.55f),
                radius = maxRadius * (0.35f + secondPulse * 0.65f),
                center = centre,
                style = Stroke(width = 3.dp.toPx())
            )
            val angle = phase * 2f * PI.toFloat()
            drawLine(
                color = GamePrimary.copy(alpha = 0.62f),
                start = centre,
                end = Offset(
                    centre.x + cos(angle.toDouble()).toFloat() * maxRadius,
                    centre.y + sin(angle.toDouble()).toFloat() * maxRadius
                ),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        Image(
            painter = painterResource(R.drawable.lobby_robot_blue),
            contentDescription = "Local blue robot",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size * 0.68f)
        )
    }
}

@Composable
private fun RoomCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSubmit: () -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = { input ->
            onValueChange(input.uppercase(Locale.ROOT).filter { it in 'A'..'Z' || it in '0'..'9' }.take(6))
        },
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { if (value.length == 6) onSubmit() }),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Six-character room code" },
        decorationBox = { innerTextField ->
            Box(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(6) { index ->
                        val active = index == value.length && enabled
                        Surface(
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(11.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                if (active) 2.dp else 1.dp,
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    value.getOrNull(index)?.toString() ?: "•",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (value.getOrNull(index) == null) GameDisabledText else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxSize().alpha(0.01f)) { innerTextField() }
            }
        }
    )
}

@Composable
private fun RoomCodeHeader(code: String, copied: Boolean, onCopy: () -> Unit, onShare: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (copied) "COPIED" else "ROOM",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (copied) GameSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    code,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
            GameIconButton(GameModeIcon.COPY, "Copy room code", onCopy)
            Spacer(Modifier.width(6.dp))
            GameIconButton(GameModeIcon.SHARE, "Share room code", onShare, accent = GameSecondary)
        }
    }
}

@Composable
private fun PlayerVersusLayout(
    stacked: Boolean,
    compact: Boolean,
    hasOpponent: Boolean,
    localName: String,
    opponentName: String,
    localReady: Boolean,
    opponentReady: Boolean,
    localHost: Boolean,
    opponentHost: Boolean,
    localConnected: Boolean,
    localRating: Int?,
    opponentRating: Int?,
    localTier: String,
    opponentTier: String
) {
    val opponentState = remember { MutableTransitionState(hasOpponent).apply { targetState = hasOpponent } }
    LaunchedEffect(hasOpponent) { opponentState.targetState = hasOpponent }

    if (stacked) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LobbyPlayerCard(
                local = true,
                name = localName,
                ready = localReady,
                host = localHost,
                connected = localConnected,
                occupied = true,
                rating = localRating,
                tier = localTier,
                compact = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("VS", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
            LobbyPlayerCard(
                local = false,
                name = opponentName,
                ready = opponentReady,
                host = opponentHost,
                connected = true,
                occupied = hasOpponent,
                rating = opponentRating,
                tier = opponentTier,
                compact = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LobbyPlayerCard(
                local = true,
                name = localName,
                ready = localReady,
                host = localHost,
                connected = localConnected,
                occupied = true,
                rating = localRating,
                tier = localTier,
                compact = compact,
                modifier = Modifier.weight(1f)
            )
            AnimatedVisibility(
                visibleState = opponentState,
                enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.7f)
            ) {
                Text(
                    "VS",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 7.dp)
                )
            }
            if (!opponentState.currentState && !opponentState.targetState) {
                Text(
                    "VS",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 7.dp)
                )
            }
            LobbyPlayerCard(
                local = false,
                name = opponentName,
                ready = opponentReady,
                host = opponentHost,
                connected = true,
                occupied = hasOpponent,
                rating = opponentRating,
                tier = opponentTier,
                compact = compact,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LobbyPlayerCard(
    local: Boolean,
    name: String,
    ready: Boolean,
    host: Boolean,
    connected: Boolean,
    occupied: Boolean,
    rating: Int?,
    tier: String,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = if (local) GamePrimary else GameSecondary
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            !connected -> GameWarning
            ready -> GameSuccess
            else -> accent.copy(alpha = 0.62f)
        },
        animationSpec = tween(GameDimensions.quickMotionMillis),
        label = "ready glow"
    )
    val imageAlpha by animateFloatAsState(
        targetValue = if (occupied) 1f else 0.22f,
        animationSpec = tween(GameDimensions.standardMotionMillis),
        label = "opponent arrival"
    )
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(if (ready) 2.dp else 1.dp, borderColor),
        shadowElevation = if (ready) 6.dp else 1.dp
    ) {
        Column(
            Modifier.padding(horizontal = 7.dp, vertical = if (compact) 6.dp else 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                if (local) "YOU" else "OPPONENT",
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
            Image(
                painter = painterResource(if (local) R.drawable.lobby_robot_blue else R.drawable.lobby_robot_red),
                contentDescription = if (local) "Your blue robot" else if (occupied) "Opponent red robot" else "Empty opponent slot",
                contentScale = ContentScale.Fit,
                colorFilter = if (!occupied) ColorFilter.tint(GameDisabledText) else null,
                modifier = Modifier.size(if (compact) 76.dp else 96.dp).alpha(imageAlpha)
            )
            Text(
                if (occupied) name else "Waiting for opponent",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (host && occupied) GameStatusPill("Host", accent)
            if (occupied && rating != null && tier.isNotBlank()) {
                Text("$tier • $rating", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val statusLabel = when {
                !connected -> "Connection lost"
                !occupied -> "Open slot"
                ready -> "Ready"
                else -> "Not ready"
            }
            val statusAccent = when {
                !connected -> GameWarning
                ready -> GameSuccess
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            GameStatusPill(statusLabel, statusAccent, icon = if (ready) GameModeIcon.CHECK else null)
        }
    }
}

@Composable
private fun LobbyConfiguration(room: RoomInfo, localIsHost: Boolean, onDifficulty: (Difficulty) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (localIsHost && !room.ranked) "Host settings" else "Match settings",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                GameStatusPill(if (room.ranked) "Ranked" else "Unranked", if (room.ranked) GameSecondary else GamePrimary)
            }
            if (localIsHost && !room.ranked && !room.matchActive) {
                GameDifficultySelector(room.difficulty, onDifficulty, compact = true)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GameStatusPill(difficultyLabel(room.difficulty), GamePrimary)
                    if (!localIsHost) GameStatusPill("Host controls settings", GameDisabledText, icon = GameModeIcon.LOCK)
                }
            }
        }
    }
}

@Composable
private fun ArenaLobbyBackdrop(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f), MaterialTheme.colorScheme.background),
                radius = 1_100f
            )
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val grid = GamePrimary.copy(alpha = 0.055f)
            val step = 36.dp.toPx()
            var x = 0f
            while (x <= size.width) {
                drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                y += step
            }
        }
        content()
    }
}

internal fun friendlyMatchmakingMessage(message: String): String {
    val value = message.trim()
    if (value.isBlank()) return ""
    return when {
        value.contains("too many", true) -> "Too many attempts. Try again shortly."
        value.contains("session", true) && value.contains("expired", true) -> "Session expired. Reconnect."
        value.contains("cancelled", true) && !value.contains("could not", true) -> "Search cancelled"
        value.contains("connect", true) || value.contains("server", true) -> "Connection lost"
        value.contains("expired", true) || value.contains("matchmaking", true) || value.contains("search", true) -> "Unable to find a match"
        else -> "Unable to find a match"
    }
}

private fun friendlyRoomMessage(message: String): String {
    val value = message.trim()
    if (value.isBlank()) return ""
    return when {
        value.contains("too many", true) -> "Too many attempts. Try again shortly."
        value.contains("session", true) && value.contains("expired", true) -> "Session expired. Reconnect."
        value.contains("not found", true) -> "Room not found"
        value.contains("full", true) -> "Room is full"
        value.contains("expired", true) || value.contains("room closed", true) || value.contains("host left", true) ||
            value.contains("opponent left", true) -> "Room expired"
        value.contains("already", true) -> "You are already in a room"
        value.contains("connect", true) || value.contains("timed out", true) || value.contains("server", true) -> "Connection lost"
        value.contains("invalid room code", true) || value.contains("6-character", true) -> "Enter a valid six-character room code"
        else -> "Unable to update the room"
    }
}

private fun difficultyLabel(difficulty: Difficulty): String {
    val seconds = when (difficulty) {
        Difficulty.EASY -> 10
        Difficulty.STANDARD -> 15
        Difficulty.EXPERT -> 20
    }
    return "${difficulty.name.lowercase().replaceFirstChar { it.uppercase() }} • ${seconds}s"
}

private fun copyRoomCode(context: Context, code: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("Math Fight room code", code))
}

private fun shareRoomCode(context: Context, code: String): Boolean = runCatching {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Join my Math Fight room!\nRoom code: $code")
    }
    val chooser = Intent.createChooser(sendIntent, "Share room code")
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}.isSuccess
