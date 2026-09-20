package com.droidnova.mathfight.ui.battle.arena

import com.badlogic.gdx.math.MathUtils

internal enum class FighterAction(val region: String, val duration: Float, val priority: Int) {
    IDLE("idle", 1.2f, 0), ATTACK_MELEE("melee", 0.65f, 1),
    ATTACK_PROJECTILE("projectile", 0.8f, 1), HIT("hit", 0.3f, 2),
    KO("ko", 0.55f, 3), VICTORY("victory", 0.7f, 3), PAUSED("idle", 1f, 4)
}

/** Poses are derived from time, never accumulated into the base transform. */
internal class FighterVisualState(val side: ArenaSide) {
    var pose = FighterAction.IDLE
        private set
    var paused = false
    val action get() = if (paused) FighterAction.PAUSED else pose
    var time = 0f
        private set
    val progress get() = (time / pose.duration).coerceIn(0f, 1f)
    val direction get() = if (side == ArenaSide.LEFT) 1f else -1f
    val terminal get() = pose == FighterAction.KO || pose == FighterAction.VICTORY
    val attacking get() = pose == FighterAction.ATTACK_MELEE || pose == FighterAction.ATTACK_PROJECTILE
    val contactTime get() = if (pose == FighterAction.ATTACK_PROJECTILE) 0.56f else 0.30f
    val offsetX: Float get() = when (pose) {
        FighterAction.ATTACK_MELEE -> direction * when {
            time < 0.12f -> -10f * time / 0.12f
            time < 0.30f -> -10f + 315f * smooth((time - 0.12f) / 0.18f)
            else -> 305f * (1f - smooth((time - 0.30f) / 0.35f))
        }
        FighterAction.HIT -> -direction * MathUtils.sin(progress * MathUtils.PI) * 32f
        FighterAction.KO -> -direction * 16f * progress
        else -> 0f
    }
    val offsetY get() = if (pose == FighterAction.IDLE) MathUtils.sin(time * 5f) * 3f else 0f
    val rotation get() = if (pose == FighterAction.HIT) direction * 9f * MathUtils.sin(progress * MathUtils.PI) else 0f
    val flash get() = if (pose == FighterAction.HIT) (1f - progress * 3f).coerceAtLeast(0f) else 0f
    val charge get() = if (attacking && time < 0.22f) MathUtils.sin(time / 0.22f * MathUtils.PI) else 0f

    fun play(next: FighterAction): Boolean {
        if (terminal || next.priority < pose.priority || next == pose) return false
        pose = next
        time = 0f
        return true
    }
    fun restore(next: FighterAction) { pose = next; time = next.duration }
    fun update(delta: Float) {
        if (paused) return
        time += delta
        if (pose == FighterAction.IDLE) time %= pose.duration
        else if (time >= pose.duration) {
            if (terminal) time = pose.duration else { pose = FighterAction.IDLE; time = 0f }
        }
    }
    fun reset() { pose = FighterAction.IDLE; time = 0f; paused = false }
    private fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
}
