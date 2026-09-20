package com.droidnova.mathfight.ui.battle.arena

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import com.droidnova.mathfight.game.BattlePhase
import com.droidnova.mathfight.game.BattleState
import com.droidnova.mathfight.game.Fighter
import kotlin.math.roundToInt

/** Compose adapter: authoritative state in, renderer-only commands out. */
@Composable
fun LibGdxBattleArena(
    state: BattleState,
    isResumed: Boolean,
    onlinePaused: Boolean,
    consumeVisualEvent: (com.droidnova.mathfight.game.PhaseKey) -> Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val provider = remember(context) { context.findArenaHostProvider() }
    val host = provider?.arenaHost
    if (host == null || !host.available || host.session == ArenaCommandBridge.NO_SESSION) {
        FallbackArena(state, onlinePaused || !isResumed, modifier)
        return
    }

    val bridge = provider.arenaCommandBridge
    val session = host.session
    val currentConsumer by rememberUpdatedState(consumeVisualEvent)

    DisposableEffect(host) {
        onDispose { host.coverArena() }
    }

    LaunchedEffect(
        session,
        state.key,
        state.playerHp,
        state.opponentHp,
        state.winner,
        isResumed,
        onlinePaused
    ) {
        val paused = !isResumed || onlinePaused
        bridge.synchronize(
            session,
            ArenaSnapshot(
                battleId = state.battleId,
                questionId = state.questionId,
                leftHp = state.playerHp,
                rightHp = state.opponentHp,
                settled = state.phase == BattlePhase.ANSWERING,
                paused = paused,
                winner = state.winner?.toArenaSide()
            )
        )
        if (paused || !currentConsumer(state.key)) return@LaunchedEffect

        when (state.phase) {
            BattlePhase.WINDUP -> state.attacker?.toArenaSide()?.let { attacker ->
                bridge.playAttack(
                    session,
                    attacker,
                    ArenaEventId(state.battleId, state.questionId, ArenaEventKind.ATTACK)
                )
            }
            BattlePhase.IMPACT -> state.attacker?.toArenaSide()?.opposite?.let { defender ->
                bridge.playHit(
                    session,
                    defender,
                    ArenaEventId(state.battleId, state.questionId, ArenaEventKind.HIT)
                )
            }
            BattlePhase.KO -> state.winner?.toArenaSide()?.let { winner ->
                bridge.playKo(
                    session,
                    winner.opposite,
                    ArenaEventId(state.battleId, state.questionId, ArenaEventKind.KO)
                )
                bridge.playVictory(
                    session,
                    winner,
                    ArenaEventId(state.battleId, state.questionId, ArenaEventKind.VICTORY)
                )
            }
            else -> Unit
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color(0xFF071126))
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                host.presentBattle(
                    state.battleId,
                    Rect(
                        bounds.left.roundToInt(),
                        bounds.top.roundToInt(),
                        bounds.right.roundToInt(),
                        bounds.bottom.roundToInt()
                    )
                )
            }
            .semantics {
            contentDescription = "Blue player robot facing red opponent robot in battle arena"
        }
    )
}

private fun Fighter.toArenaSide(): ArenaSide =
    if (this == Fighter.PLAYER) ArenaSide.LEFT else ArenaSide.RIGHT

private tailrec fun Context.findArenaHostProvider(): ArenaHostProvider? = when (this) {
    is ArenaHostProvider -> this
    is ContextWrapper -> baseContext.findArenaHostProvider()
    else -> null
}

@Composable
private fun FallbackArena(state: BattleState, paused: Boolean, modifier: Modifier) {
    Box(
        modifier = modifier.clipToBounds().background(Color(0xFF071126)).semantics {
            contentDescription = "Battle arena fallback with player and opponent robots"
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val ground = size.height * 0.86f
            drawRect(Color(0xFF0B1D38), size = size)
            drawRect(
                color = Color(0xFF17314B),
                topLeft = Offset(0f, ground),
                size = Size(size.width, size.height - ground)
            )
            drawLine(
                color = Color(0xFF54C7F2),
                start = Offset(size.width * 0.08f, ground),
                end = Offset(size.width * 0.92f, ground),
                strokeWidth = 4f
            )
            val robotHeight = minOf(size.height * 0.62f, size.width * 0.27f)
            val winner = state.winner
            drawFallbackRobot(
                centerX = size.width * 0.26f,
                groundY = ground,
                height = robotHeight,
                color = Color(0xFF168CFF),
                facingRight = true,
                defeated = state.phase == BattlePhase.KO && winner == Fighter.BOT
            )
            drawFallbackRobot(
                centerX = size.width * 0.74f,
                groundY = ground,
                height = robotHeight,
                color = Color(0xFFFF3340),
                facingRight = false,
                defeated = state.phase == BattlePhase.KO && winner == Fighter.PLAYER
            )
            if (paused) drawRect(Color.Black.copy(alpha = 0.52f), size = size)
        }
        Text(
            text = if (paused) "Arena paused" else "Arena unavailable",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun DrawScope.drawFallbackRobot(
    centerX: Float,
    groundY: Float,
    height: Float,
    color: Color,
    facingRight: Boolean,
    defeated: Boolean
) {
    val direction = if (facingRight) 1f else -1f
    rotate(if (defeated) -direction * 76f else 0f, Offset(centerX, groundY)) {
        val bodyWidth = height * 0.34f
        drawRoundRect(
            color = color,
            topLeft = Offset(centerX - bodyWidth / 2f, groundY - height * 0.65f),
            size = Size(bodyWidth, height * 0.38f)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(centerX - bodyWidth * 0.44f, groundY - height * 0.92f),
            size = Size(bodyWidth * 0.88f, height * 0.20f)
        )
        drawLine(color, Offset(centerX - bodyWidth * 0.22f, groundY - height * 0.27f),
            Offset(centerX - bodyWidth * 0.34f, groundY), height * 0.075f)
        drawLine(color, Offset(centerX + bodyWidth * 0.22f, groundY - height * 0.27f),
            Offset(centerX + bodyWidth * 0.34f, groundY), height * 0.075f)
        drawLine(color, Offset(centerX + direction * bodyWidth * 0.42f, groundY - height * 0.56f),
            Offset(centerX + direction * bodyWidth, groundY - height * 0.52f), height * 0.075f)
        drawCircle(Color.White, height * 0.025f,
            Offset(centerX + direction * bodyWidth * 0.21f, groundY - height * 0.82f))
    }
}
