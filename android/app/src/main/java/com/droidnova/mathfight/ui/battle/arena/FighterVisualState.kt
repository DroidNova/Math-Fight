package com.droidnova.mathfight.ui.battle.arena

import com.badlogic.gdx.math.MathUtils

internal enum class FighterAction { IDLE, ATTACK, HIT, KO, VICTORY }

/** Render-thread-only animation state for one robot. */
internal class FighterVisualState(val side: ArenaSide) {
    var action: FighterAction = FighterAction.IDLE
        private set
    var hp: Int = 100
        private set

    private var actionTime = 0f
    private var idleTime = if (side == ArenaSide.LEFT) 0f else 0.7f

    val inwardDirection: Float
        get() = if (side == ArenaSide.LEFT) 1f else -1f
    private val outwardDirection: Float
        get() = -inwardDirection

    val normalizedActionTime: Float
        get() = when (action) {
            FighterAction.ATTACK -> (actionTime / ATTACK_DURATION).coerceIn(0f, 1f)
            FighterAction.HIT -> (actionTime / HIT_DURATION).coerceIn(0f, 1f)
            FighterAction.KO -> (actionTime / KO_DURATION).coerceIn(0f, 1f)
            FighterAction.VICTORY -> (actionTime / VICTORY_DURATION).coerceIn(0f, 1f)
            FighterAction.IDLE -> 0f
        }

    val offsetX: Float
        get() = when (action) {
            FighterAction.ATTACK -> attackOffset(normalizedActionTime) * inwardDirection
            FighterAction.HIT -> MathUtils.sin(normalizedActionTime * MathUtils.PI) * 48f * outwardDirection
            FighterAction.KO -> easeOutCubic(normalizedActionTime) * 34f * outwardDirection
            else -> 0f
        }

    val offsetY: Float
        get() = when (action) {
            FighterAction.IDLE -> MathUtils.sin(idleTime * 2.1f) * 3.5f
            FighterAction.ATTACK -> MathUtils.sin(normalizedActionTime * MathUtils.PI) * 5f
            FighterAction.HIT -> MathUtils.sin(normalizedActionTime * MathUtils.PI) * 4f
            FighterAction.KO -> -easeOutCubic(normalizedActionTime) * 9f
            FighterAction.VICTORY -> {
                val decay = 1f - normalizedActionTime
                kotlin.math.abs(MathUtils.sin(normalizedActionTime * MathUtils.PI2 * 1.5f)) * 24f * decay
            }
        }

    val rotation: Float
        get() = when (action) {
            FighterAction.ATTACK -> inwardDirection * MathUtils.sin(normalizedActionTime * MathUtils.PI) * -7f
            FighterAction.HIT -> outwardDirection * MathUtils.sin(normalizedActionTime * MathUtils.PI) * -11f
            FighterAction.KO -> if (side == ArenaSide.LEFT) {
                easeOutCubic(normalizedActionTime) * 78f
            } else {
                easeOutCubic(normalizedActionTime) * -78f
            }
            else -> 0f
        }

    val attackReach: Float
        get() = if (action == FighterAction.ATTACK) {
            smoothPulse(normalizedActionTime, 0.12f, 0.46f, 0.82f)
        } else 0f

    val victoryPose: Float
        get() = if (action == FighterAction.VICTORY) smoothStep((normalizedActionTime / 0.22f).coerceIn(0f, 1f)) else 0f

    val flash: Float
        get() = if (action == FighterAction.HIT) (1f - normalizedActionTime * 3.8f).coerceAtLeast(0f) else 0f

    val idleBreath: Float
        get() = if (action == FighterAction.IDLE) 1f + MathUtils.sin(idleTime * 2.1f) * 0.025f else 1f

    val charge: Float
        get() = if (action == FighterAction.ATTACK) {
            val time = normalizedActionTime
            when {
                time < 0.22f -> smoothStep(time / 0.22f)
                time < 0.52f -> 1f - smoothStep((time - 0.22f) / 0.30f)
                else -> 0f
            }
        } else 0f

    fun update(delta: Float) {
        idleTime += delta
        if (idleTime > 1_000f) idleTime -= 1_000f
        if (action == FighterAction.IDLE) return

        actionTime += delta
        when (action) {
            FighterAction.ATTACK -> if (actionTime >= ATTACK_DURATION) returnToIdle()
            FighterAction.HIT -> if (actionTime >= HIT_DURATION) returnToIdle()
            FighterAction.KO -> actionTime = actionTime.coerceAtMost(KO_DURATION)
            FighterAction.VICTORY -> actionTime = actionTime.coerceAtMost(VICTORY_DURATION)
            FighterAction.IDLE -> Unit
        }
    }

    fun updateHealth(value: Int) {
        hp = value.coerceIn(0, 100)
    }

    fun playAttack() {
        if (action == FighterAction.KO) return
        action = FighterAction.ATTACK
        actionTime = 0f
    }

    fun playHit() {
        if (action == FighterAction.KO) return
        action = FighterAction.HIT
        actionTime = 0f
    }

    fun playKo() {
        action = FighterAction.KO
        actionTime = 0f
    }

    fun playVictory() {
        if (action == FighterAction.KO) return
        action = FighterAction.VICTORY
        actionTime = 0f
    }

    fun forceKo() {
        action = FighterAction.KO
        actionTime = KO_DURATION
    }

    fun forceVictory() {
        if (action == FighterAction.KO) return
        action = FighterAction.VICTORY
        actionTime = VICTORY_DURATION
    }

    fun settle() {
        if (action == FighterAction.ATTACK || action == FighterAction.HIT) returnToIdle()
    }

    fun reset() {
        action = FighterAction.IDLE
        actionTime = 0f
        hp = 100
    }

    private fun returnToIdle() {
        action = FighterAction.IDLE
        actionTime = 0f
    }

    private fun attackOffset(time: Float): Float = when {
        time < 0.18f -> -8f * smoothStep(time / 0.18f)
        time < 0.46f -> -8f + 174f * smoothStep((time - 0.18f) / 0.28f)
        else -> 166f * (1f - smoothStep((time - 0.46f) / 0.54f))
    }

    private fun smoothPulse(value: Float, riseStart: Float, peak: Float, fallEnd: Float): Float = when {
        value <= riseStart -> 0f
        value < peak -> smoothStep((value - riseStart) / (peak - riseStart))
        value < fallEnd -> 1f - smoothStep((value - peak) / (fallEnd - peak))
        else -> 0f
    }

    private fun smoothStep(value: Float): Float = value * value * (3f - 2f * value)

    private fun easeOutCubic(value: Float): Float {
        val inverse = 1f - value
        return 1f - inverse * inverse * inverse
    }

    companion object {
        private const val ATTACK_DURATION = 0.48f
        private const val HIT_DURATION = 0.30f
        private const val KO_DURATION = 0.48f
        private const val VICTORY_DURATION = 0.58f
    }
}
