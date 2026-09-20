package com.droidnova.mathfight.ui.components

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.droidnova.mathfight.game.Difficulty
import com.droidnova.mathfight.ui.theme.GameDimensions
import com.droidnova.mathfight.ui.theme.GameDisabled
import com.droidnova.mathfight.ui.theme.GameDisabledText
import com.droidnova.mathfight.ui.theme.GamePrimary
import com.droidnova.mathfight.ui.theme.GameSuccess
import com.droidnova.mathfight.ui.theme.GameSurfacePressed
import com.droidnova.mathfight.ui.theme.GameWarning

enum class GameModeIcon {
    MATCH, OFFLINE, ROOM, PROFILE, LEADERBOARD, SETTINGS, BACK, COPY, SHARE, CHECK, LOCK
}

enum class ConnectionVisualState(val label: String) {
    ONLINE("Online"), CONNECTING("Connecting"), OFFLINE("Offline")
}

@Composable
fun GamePrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: GameModeIcon? = null
) {
    Button(
        onClick = rememberThrottledClick(onClick),
        enabled = enabled,
        modifier = modifier.heightIn(min = GameDimensions.buttonHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = GameDisabled,
            disabledContentColor = GameDisabledText
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        icon?.let {
            GameModeGlyph(it, MaterialTheme.colorScheme.onPrimary, Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GameSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: GameModeIcon? = null
) {
    OutlinedButton(
        onClick = rememberThrottledClick(onClick),
        enabled = enabled,
        modifier = modifier.heightIn(min = GameDimensions.touchTarget),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = GameDisabledText
        ),
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outline else GameDisabled),
        shape = RoundedCornerShape(14.dp)
    ) {
        icon?.let {
            GameModeGlyph(it, MaterialTheme.colorScheme.primary, Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GameIconButton(
    icon: GameModeIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    val description = contentDescription
    Surface(
        modifier = modifier
            .size(GameDimensions.touchTarget)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = rememberThrottledClick(onClick))
            .semantics { this.contentDescription = description },
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) MaterialTheme.colorScheme.surfaceVariant else GameDisabled,
        border = BorderStroke(1.dp, if (enabled) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(contentAlignment = Alignment.Center) {
            GameModeGlyph(icon, if (enabled) accent else GameDisabledText, Modifier.size(22.dp))
        }
    }
}

@Composable
fun GameSegmentedControl(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            val chosen = option == selected
            val color by animateColorAsState(
                targetValue = when {
                    !enabled -> GameDisabled
                    chosen -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                animationSpec = tween(GameDimensions.quickMotionMillis),
                label = "segmented choice"
            )
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = GameDimensions.touchTarget)
                    .clip(RoundedCornerShape(13.dp))
                    .selectable(
                        selected = chosen,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = rememberThrottledClick { onSelected(option) }
                    ),
                shape = RoundedCornerShape(13.dp),
                color = color,
                border = BorderStroke(1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            ) {
                Box(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
                    Text(
                        option,
                        style = MaterialTheme.typography.labelLarge,
                        color = when {
                            !enabled -> GameDisabledText
                            chosen -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GameStatusPill(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    icon: GameModeIcon? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.48f))
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            icon?.let { GameModeGlyph(it, accent, Modifier.size(14.dp)) }
            Text(label, style = MaterialTheme.typography.labelMedium, color = accent, maxLines = 1)
        }
    }
}

@Composable
fun GameModeCard(
    title: String,
    supportingText: String,
    icon: GameModeIcon,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledReason: String? = null,
    prominent: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = tween(GameDimensions.quickMotionMillis),
        label = "mode card press"
    )
    val restingColor = when {
        !enabled -> GameDisabled.copy(alpha = 0.72f)
        prominent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val containerColor by animateColorAsState(
        targetValue = if (pressed && enabled) GameSurfacePressed else restingColor,
        animationSpec = tween(GameDimensions.quickMotionMillis),
        label = "mode card color"
    )
    val action = rememberThrottledClick(onClick)
    Surface(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = action
            )
            .semantics {
                disabledReason?.takeIf { !enabled }?.let { stateDescription = it }
            },
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(1.dp, if (enabled) accent.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (pressed) 1.dp else 5.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = accent.copy(alpha = if (enabled) 0.18f else 0.08f), shape = RoundedCornerShape(14.dp)) {
                GameModeGlyph(
                    icon,
                    if (enabled) accent else GameDisabledText,
                    Modifier.padding(10.dp).size(GameDimensions.iconSize)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = if (prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else GameDisabledText
                )
                Text(
                    disabledReason?.takeIf { !enabled } ?: supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else GameDisabledText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CompactStatisticChip(label: String, value: String, modifier: Modifier = Modifier, accent: Color = GamePrimary) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.38f))
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = rememberThrottledClick(onAction), modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun PlayerAvatarBadge(name: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Surface(
        modifier = modifier
            .heightIn(min = GameDimensions.touchTarget)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = rememberThrottledClick(onClick)),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(initial, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(8.dp))
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ConnectionStatusIndicator(state: ConnectionVisualState, modifier: Modifier = Modifier) {
    val targetColor = when (state) {
        ConnectionVisualState.ONLINE -> GameSuccess
        ConnectionVisualState.CONNECTING -> GameWarning
        ConnectionVisualState.OFFLINE -> MaterialTheme.colorScheme.error
    }
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(GameDimensions.standardMotionMillis),
        label = "connection state"
    )
    Surface(
        modifier = modifier.semantics { contentDescription = "Connection status: ${state.label}" },
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(state.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun GameDifficultySelector(
    selected: Difficulty,
    onSelected: (Difficulty) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    compact: Boolean = false,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(scrollState).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Difficulty.entries.forEach { difficulty ->
            val chosen = difficulty == selected
            val container by animateColorAsState(
                when {
                    !enabled -> GameDisabled
                    chosen -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                tween(GameDimensions.quickMotionMillis),
                label = "difficulty selection"
            )
            val seconds = when (difficulty) {
                Difficulty.EASY -> 10
                Difficulty.STANDARD -> 15
                Difficulty.EXPERT -> 20
            }
            Surface(
                modifier = Modifier
                    .widthIn(min = if (compact) 94.dp else 104.dp)
                    .heightIn(min = if (compact) GameDimensions.touchTarget else 64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .selectable(
                        selected = chosen,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = rememberThrottledClick { onSelected(difficulty) }
                    ),
                color = container,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    Modifier.padding(horizontal = if (compact) 10.dp else 13.dp, vertical = if (compact) 6.dp else 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        difficulty.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge,
                        color = when {
                            !enabled -> GameDisabledText
                            chosen -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        if (compact) "${seconds}s" else "$seconds seconds",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun GameLoadingState(label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InlineMessage(message: String, modifier: Modifier = Modifier, isError: Boolean = true) {
    if (message.isBlank()) return
    val accent = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
    ) {
        Text(message, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = accent)
    }
}

@Composable
fun AnimatedXpBar(progress: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(GameDimensions.standardMotionMillis),
        label = "XP progress"
    )
    Box(
        modifier.height(9.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outlineVariant)
            .semantics { stateDescription = "${(animated * 100).toInt()} percent" }
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(animated).background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun rememberThrottledClick(action: () -> Unit): () -> Unit {
    var lastClick by remember { mutableLongStateOf(0L) }
    return remember(action) {
        {
            val now = SystemClock.elapsedRealtime()
            if (now - lastClick >= 450L) {
                lastClick = now
                action()
            }
        }
    }
}

@Composable
private fun GameModeGlyph(icon: GameModeIcon, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = (size.minDimension * 0.09f).coerceAtLeast(2f)
        when (icon) {
            GameModeIcon.MATCH -> {
                drawCircle(color, size.minDimension * 0.2f, Offset(size.width * 0.25f, size.height * 0.5f), style = Stroke(stroke))
                drawCircle(color, size.minDimension * 0.2f, Offset(size.width * 0.75f, size.height * 0.5f), style = Stroke(stroke))
                drawLine(color, Offset(size.width * 0.43f, size.height * 0.26f), Offset(size.width * 0.52f, size.height * 0.48f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.52f, size.height * 0.48f), Offset(size.width * 0.45f, size.height * 0.74f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.45f, size.height * 0.74f), Offset(size.width * 0.64f, size.height * 0.42f), stroke, StrokeCap.Round)
            }
            GameModeIcon.OFFLINE -> {
                drawRoundRect(color, Offset(size.width * 0.14f, size.height * 0.22f), Size(size.width * 0.72f, size.height * 0.58f), CornerRadius(stroke * 1.5f), style = Stroke(stroke))
                drawLine(color, Offset(size.width * 0.5f, size.height * 0.22f), Offset(size.width * 0.5f, size.height * 0.08f), stroke, StrokeCap.Round)
                drawCircle(color, stroke * 0.62f, Offset(size.width * 0.36f, size.height * 0.48f))
                drawCircle(color, stroke * 0.62f, Offset(size.width * 0.64f, size.height * 0.48f))
                drawLine(color, Offset(size.width * 0.35f, size.height * 0.66f), Offset(size.width * 0.65f, size.height * 0.66f), stroke * 0.75f, StrokeCap.Round)
            }
            GameModeIcon.ROOM -> {
                drawCircle(color, size.minDimension * 0.15f, Offset(size.width * 0.33f, size.height * 0.34f), style = Stroke(stroke))
                drawCircle(color, size.minDimension * 0.15f, Offset(size.width * 0.69f, size.height * 0.4f), style = Stroke(stroke))
                drawArc(color, 195f, 150f, false, Offset(size.width * 0.1f, size.height * 0.45f), Size(size.width * 0.48f, size.height * 0.42f), style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(color, 195f, 150f, false, Offset(size.width * 0.46f, size.height * 0.5f), Size(size.width * 0.43f, size.height * 0.36f), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            GameModeIcon.PROFILE -> {
                drawCircle(color, size.minDimension * 0.2f, Offset(size.width * 0.5f, size.height * 0.3f), style = Stroke(stroke))
                drawArc(color, 195f, 150f, false, Offset(size.width * 0.16f, size.height * 0.47f), Size(size.width * 0.68f, size.height * 0.46f), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            GameModeIcon.LEADERBOARD -> {
                drawRoundRect(color, Offset(size.width * 0.1f, size.height * 0.55f), Size(size.width * 0.22f, size.height * 0.34f), CornerRadius(stroke))
                drawRoundRect(color, Offset(size.width * 0.39f, size.height * 0.2f), Size(size.width * 0.22f, size.height * 0.69f), CornerRadius(stroke))
                drawRoundRect(color, Offset(size.width * 0.68f, size.height * 0.4f), Size(size.width * 0.22f, size.height * 0.49f), CornerRadius(stroke))
            }
            GameModeIcon.SETTINGS -> {
                listOf(0.25f, 0.5f, 0.75f).forEachIndexed { index, y ->
                    drawLine(color, Offset(size.width * 0.12f, size.height * y), Offset(size.width * 0.88f, size.height * y), stroke * 0.7f, StrokeCap.Round)
                    val x = if (index == 1) 0.67f else if (index == 0) 0.36f else 0.48f
                    drawCircle(color, stroke, Offset(size.width * x, size.height * y))
                }
            }
            GameModeIcon.BACK -> {
                drawLine(color, Offset(size.width * 0.78f, size.height * 0.18f), Offset(size.width * 0.3f, size.height * 0.5f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.3f, size.height * 0.5f), Offset(size.width * 0.78f, size.height * 0.82f), stroke, StrokeCap.Round)
            }
            GameModeIcon.COPY -> {
                drawRoundRect(color, Offset(size.width * 0.28f, size.height * 0.12f), Size(size.width * 0.58f, size.height * 0.58f), CornerRadius(stroke), style = Stroke(stroke))
                drawRoundRect(color, Offset(size.width * 0.12f, size.height * 0.3f), Size(size.width * 0.58f, size.height * 0.58f), CornerRadius(stroke), style = Stroke(stroke))
            }
            GameModeIcon.SHARE -> {
                val left = Offset(size.width * 0.22f, size.height * 0.5f)
                val top = Offset(size.width * 0.73f, size.height * 0.22f)
                val bottom = Offset(size.width * 0.73f, size.height * 0.78f)
                drawLine(color, left, top, stroke * 0.7f, StrokeCap.Round)
                drawLine(color, left, bottom, stroke * 0.7f, StrokeCap.Round)
                drawCircle(color, stroke * 1.35f, left)
                drawCircle(color, stroke * 1.35f, top)
                drawCircle(color, stroke * 1.35f, bottom)
            }
            GameModeIcon.CHECK -> {
                drawLine(color, Offset(size.width * 0.16f, size.height * 0.54f), Offset(size.width * 0.42f, size.height * 0.78f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.42f, size.height * 0.78f), Offset(size.width * 0.86f, size.height * 0.24f), stroke, StrokeCap.Round)
            }
            GameModeIcon.LOCK -> {
                drawRoundRect(color, Offset(size.width * 0.2f, size.height * 0.43f), Size(size.width * 0.6f, size.height * 0.45f), CornerRadius(stroke), style = Stroke(stroke))
                drawArc(color, 180f, -180f, false, Offset(size.width * 0.3f, size.height * 0.12f), Size(size.width * 0.4f, size.height * 0.54f), style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
    }
}
