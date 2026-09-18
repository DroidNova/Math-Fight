package com.droidnova.mathfight.ui.battle

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.droidnova.mathfight.game.BattlePhase
import com.droidnova.mathfight.game.BattleState
import com.droidnova.mathfight.game.Fighter
import com.droidnova.mathfight.game.STARTING_HP
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun MathFightApp(
    state: BattleState,
    isResumed: Boolean,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onReturnHome: () -> Unit
) {
    BackHandler(enabled = state.phase != BattlePhase.HOME, onBack = onReturnHome)
    when (state.phase) {
        BattlePhase.HOME -> HomeScreen(onStart)
        BattlePhase.RESULT -> ResultScreen(state.winner, onRestart)
        else -> BattleScreen(
            state = state,
            isResumed = isResumed,
            onDigit = onDigit,
            onBackspace = onBackspace,
            onClear = onClear,
            onSubmit = onSubmit
        )
    }
}

@Composable
private fun HomeScreen(onStart: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Math Fight", style = MaterialTheme.typography.displaySmall)
            Text(
                "Solve. Strike. Win.",
                modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onStart, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Start Battle")
            }
        }
    }
}

@Composable
private fun ResultScreen(winner: Fighter?, onRestart: () -> Unit) {
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
            Button(
                onClick = onRestart,
                modifier = Modifier.padding(top = 28.dp).heightIn(min = 48.dp)
            ) {
                Text("Restart")
            }
        }
    }
}

@Composable
private fun BattleScreen(
    state: BattleState,
    isResumed: Boolean,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit
) {
    val controlsEnabled = isResumed && state.phase == BattlePhase.ANSWERING
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HealthRow(state, isResumed)
            FighterArena(
                state = state,
                isResumed = isResumed,
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
private fun HealthRow(state: BattleState, isResumed: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HealthBar("Player", state.playerHp, isResumed, Modifier.weight(1f))
        HealthBar("Bot", state.opponentHp, isResumed, Modifier.weight(1f))
    }
}

@Composable
private fun HealthBar(label: String, hp: Int, isResumed: Boolean, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(STARTING_HP.toFloat()) }
    LaunchedEffect(hp, isResumed) {
        if (isResumed) progress.animateTo(hp.toFloat(), tween(260)) else progress.snapTo(hp.toFloat())
    }
    Column(modifier) {
        Text("$label HP: $hp", style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(
            progress = { progress.value / STARTING_HP },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

@Composable
private fun FighterArena(state: BattleState, isResumed: Boolean, modifier: Modifier = Modifier) {
    val playerMotion = remember { Animatable(0f) }
    val botMotion = remember { Animatable(0f) }
    val animationKey = state.key
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
                    defenderMotion.animateTo(1f, tween(120))
                    defenderMotion.animateTo(0f, tween(200))
                }
            }
            BattlePhase.KO -> when (state.winner) {
                Fighter.PLAYER -> botMotion.animateTo(1f, tween(600))
                Fighter.BOT -> playerMotion.animateTo(1f, tween(600))
                null -> Unit
            }
            else -> Unit
        }
    }

    val playerColor = MaterialTheme.colorScheme.primary
    val opponentColor = MaterialTheme.colorScheme.tertiary
    Canvas(
        modifier = modifier.semantics {
            contentDescription = "Player fighter facing bot fighter"
        }
    ) {
        val ground = size.height * 0.88f
        val figureHeight = size.height * 0.64f
        val playerAttacking = state.attacker == Fighter.PLAYER
        val botAttacking = state.attacker == Fighter.BOT
        val playerX = size.width * 0.25f + size.width * playerMotion.value *
            (if (playerAttacking) 0.16f else -0.07f)
        val botX = size.width * 0.75f + size.width * botMotion.value *
            (if (botAttacking) -0.16f else 0.07f)
        drawLine(
            color = Color.Gray.copy(alpha = 0.35f),
            start = Offset(size.width * 0.05f, ground),
            end = Offset(size.width * 0.95f, ground),
            strokeWidth = 3f
        )
        rotate(
            degrees = -72f * playerMotion.value.takeIf {
                state.phase == BattlePhase.KO && state.winner == Fighter.BOT
            }.orZero(),
            pivot = Offset(playerX, ground)
        ) {
            drawFighter(
                centerX = playerX,
                groundY = ground,
                height = figureHeight,
                color = if (state.phase == BattlePhase.IMPACT && botAttacking && playerMotion.value > 0f) Color.White else playerColor,
                facingRight = true,
                striking = if (playerAttacking) playerMotion.value else 0f
            )
        }
        rotate(
            degrees = 72f * botMotion.value.takeIf {
                state.phase == BattlePhase.KO && state.winner == Fighter.PLAYER
            }.orZero(),
            pivot = Offset(botX, ground)
        ) {
            drawFighter(
                centerX = botX,
                groundY = ground,
                height = figureHeight,
                color = if (state.phase == BattlePhase.IMPACT && playerAttacking && botMotion.value > 0f) Color.White else opponentColor,
                facingRight = false,
                striking = if (botAttacking) botMotion.value else 0f
            )
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
