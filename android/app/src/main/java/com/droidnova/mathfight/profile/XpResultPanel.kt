package com.droidnova.mathfight.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.droidnova.mathfight.ui.theme.GameSecondary
import com.droidnova.mathfight.ui.theme.GameSuccess

@Composable
fun XpResultPanel(result: XpResult, isResumed: Boolean, consumeAnimation: (String) -> Boolean) {
    val progress = remember(result.matchId) { Animatable(result.progression.fraction) }
    val levelUp = remember(result.matchId) { Animatable(0f) }
    LaunchedEffect(result.matchId, isResumed) {
        levelUp.snapTo(0f)
        progress.snapTo(result.progression.fraction)
        if (!isResumed || !consumeAnimation(result.matchId)) return@LaunchedEffect
        progress.snapTo(result.previousFraction)
        if (result.newLevel > result.previousLevel) {
            progress.animateTo(1f, tween(350))
            progress.snapTo(0f)
        }
        progress.animateTo(result.progression.fraction, tween(450))
        if (result.newLevel > result.previousLevel) {
            levelUp.animateTo(1f, tween(220))
            levelUp.animateTo(0f, tween(700))
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            "+${result.xpAwarded} XP",
            color = GameSuccess,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Level ${result.newLevel} - ${result.newTotalXp} total XP",
            color = MaterialTheme.colorScheme.onSurface
        )
        LinearProgressIndicator(progress = { progress.value }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Text(
            "${result.progression.xpIntoCurrentLevel} / ${result.progression.xpRequiredForNextLevel} XP",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Level Up! Level ${result.newLevel}",
            color = GameSecondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer {
                alpha = levelUp.value
                scaleX = 0.95f + 0.05f * levelUp.value
                scaleY = scaleX
            }
        )
    }
}
