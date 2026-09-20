package com.droidnova.mathfight.ui.battle.arena

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.MathUtils

/** Lightweight development fallback when the replaceable atlas cannot be decoded. */
internal class ArenaFallbackRenderer {
    private val identity = Matrix4()
    private val blueShell = Color(0.08f, 0.58f, 1f, 1f)
    private val blueEnergy = Color(0.35f, 0.95f, 1f, 1f)
    private val redShell = Color(1f, 0.20f, 0.12f, 1f)
    private val redEnergy = Color(1f, 0.62f, 0.18f, 1f)

    fun draw(
        shapes: ShapeRenderer,
        camera: ArenaCamera,
        fighters: Array<FighterVisualState>,
        paused: Boolean,
        elapsed: Float
    ) {
        camera.apply()
        shapes.projectionMatrix = camera.camera.combined
        shapes.transformMatrix = identity.idt()
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color.set(0.025f, 0.055f, 0.12f, 1f)
        shapes.rect(0f, 0f, 1000f, 500f)
        shapes.color.set(0.08f, 0.18f, 0.30f, 1f)
        repeat(9) { panel -> shapes.rect(panel * 125f, 115f, 112f, 250f) }
        shapes.color.set(0.25f, 0.72f, 0.94f, 0.16f)
        shapes.circle(215f, 305f, 120f + MathUtils.sin(elapsed * 1.5f) * 5f, 36)
        shapes.color.set(1f, 0.36f, 0.16f, 0.14f)
        shapes.circle(785f, 305f, 120f + MathUtils.cos(elapsed * 1.5f) * 5f, 36)
        shapes.color.set(0.06f, 0.13f, 0.22f, 1f)
        shapes.rect(0f, 0f, 1000f, 112f)
        shapes.color.set(0.27f, 0.80f, 0.92f, 1f)
        shapes.rect(0f, 105f, 500f, 5f)
        shapes.color.set(1f, 0.38f, 0.18f, 1f)
        shapes.rect(500f, 105f, 500f, 5f)
        drawRobot(shapes, fighters[ArenaSide.LEFT.ordinal], 260f, blueShell, blueEnergy)
        drawRobot(shapes, fighters[ArenaSide.RIGHT.ordinal], 740f, redShell, redEnergy)
        if (paused) {
            shapes.color.set(0f, 0f, 0f, 0.55f)
            shapes.rect(0f, 0f, 1000f, 500f)
            shapes.color.set(0.5f, 0.95f, 1f, 1f)
            shapes.rect(478f, 220f, 13f, 60f)
            shapes.rect(509f, 220f, 13f, 60f)
        }
        shapes.end()
    }

    private fun drawRobot(
        shapes: ShapeRenderer,
        fighter: FighterVisualState,
        baseX: Float,
        shell: Color,
        energy: Color
    ) {
        val x = baseX + fighter.offsetX
        val y = 96f + fighter.offsetY
        shapes.color.set(0f, 0f, 0f, 0.33f)
        shapes.ellipse(x - 66f, 78f, 132f, 22f, 24)
        shapes.color.set(0.035f, 0.10f, 0.18f, 1f)
        shapes.rect(x - 50f, y + 25f, 100f, 130f)
        shapes.circle(x, y + 184f, 57f, 28)
        shapes.rectLine(x - 28f, y + 35f, x - 38f, y, 25f)
        shapes.rectLine(x + 28f, y + 35f, x + 38f, y, 25f)
        shapes.color.set(shell).lerp(Color.WHITE, fighter.flash)
        shapes.rect(x - 43f, y + 31f, 86f, 117f)
        shapes.circle(x, y + 184f, 49f, 28)
        val reach = if (fighter.pose == FighterAction.ATTACK_MELEE) 65f else 0f
        val front = x + fighter.direction * (72f + reach)
        shapes.rectLine(x + fighter.direction * 39f, y + 118f, front, y + 120f, 23f)
        shapes.rectLine(x - fighter.direction * 39f, y + 110f,
            x - fighter.direction * 72f, y + 105f, 21f)
        shapes.color.set(0.02f, 0.07f, 0.13f, 1f)
        shapes.rect(x - 35f, y + 174f, 70f, 23f)
        shapes.color.set(energy)
        shapes.circle(x, y + 91f, 20f, 22)
        shapes.rect(x + fighter.direction * 7f - 20f, y + 181f, 40f, 9f)
    }
}
