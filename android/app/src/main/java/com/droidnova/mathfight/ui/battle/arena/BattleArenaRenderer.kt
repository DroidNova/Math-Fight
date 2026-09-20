package com.droidnova.mathfight.ui.battle.arena

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils

/**
 * Presentation-only libGDX renderer. It knows about robot poses and effects, but never questions,
 * damage rules, timers, sockets, winners, or persistence.
 */
internal class BattleArenaRenderer(
    private val bridge: ArenaCommandBridge,
    private val session: Long
) : ApplicationAdapter() {
    private val assets = ArenaAssets()
    private val arenaCamera = ArenaCamera()
    private val effects = ArenaEffects()
    private val leftFighter = FighterVisualState(ArenaSide.LEFT)
    private val rightFighter = FighterVisualState(ArenaSide.RIGHT)
    private val consumedEvents = LinkedHashSet<ArenaEventId>()

    private var battleId = Long.MIN_VALUE
    private var paused = false
    private var initialized = false
    private var failed = false
    private var disposed = false
    private var sparkTimer = 0f
    private var elapsed = 0f

    override fun create() {
        try {
            assets.load()
            initialized = true
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            Gdx.graphics.setContinuousRendering(true)
        } catch (exception: Exception) {
            Gdx.app?.error(TAG, "Arena initialization failed", exception)
            failed = true
            runCatching { assets.dispose() }
        }
    }

    override fun resize(width: Int, height: Int) {
        arenaCamera.resize(width, height)
    }

    override fun render() {
        if (disposed) return
        bridge.drain(session).forEach(::applyCommand)

        if (failed || !initialized) {
            drawSafePlaceholder()
            return
        }

        try {
            val delta = if (paused) 0f else Gdx.graphics.deltaTime.coerceIn(0f, MAX_DELTA)
            update(delta)
            draw()
        } catch (exception: Exception) {
            Gdx.app?.error(TAG, "Arena render failed", exception)
            failed = true
            runCatching { assets.dispose() }
            initialized = false
            drawSafePlaceholder()
        }
    }

    override fun resume() {
        Gdx.graphics.setContinuousRendering(true)
        Gdx.graphics.requestRendering()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        bridge.detach(this)
        effects.clear()
        runCatching { assets.dispose() }
        initialized = false
    }

    fun detachBridge() {
        bridge.detach(this)
    }

    private fun applyCommand(command: ArenaCommand) {
        when (command) {
            is ArenaCommand.Synchronize -> synchronize(command.snapshot)
            is ArenaCommand.Attack -> if (consume(command.eventId)) fighter(command.side).playAttack()
            is ArenaCommand.Hit -> if (consume(command.eventId)) playHit(command.side)
            is ArenaCommand.Ko -> if (consume(command.eventId)) fighter(command.side).playKo()
            is ArenaCommand.Victory -> if (consume(command.eventId)) fighter(command.side).playVictory()
            is ArenaCommand.PrepareExit -> {
                paused = true
                bridge.acknowledgeExitPrepared(session, command.token)
            }
        }
    }

    private fun synchronize(snapshot: ArenaSnapshot) {
        if (snapshot.battleId != battleId) resetArena(snapshot.battleId)
        leftFighter.updateHealth(snapshot.leftHp)
        rightFighter.updateHealth(snapshot.rightHp)
        paused = snapshot.paused

        val winner = snapshot.winner
        if (winner != null) {
            fighter(winner.opposite).forceKo()
            fighter(winner).forceVictory()
        } else if (snapshot.settled) {
            leftFighter.settle()
            rightFighter.settle()
            effects.clear()
            arenaCamera.reset()
        }
    }

    private fun resetArena(nextBattleId: Long) {
        battleId = nextBattleId
        leftFighter.reset()
        rightFighter.reset()
        effects.clear()
        consumedEvents.clear()
        arenaCamera.reset()
        sparkTimer = 0f
        elapsed = 0f
    }

    private fun playHit(side: ArenaSide) {
        val target = fighter(side)
        target.playHit()
        val x = fighterX(side) + sideDirection(side) * 56f
        effects.spawnImpact(x, GROUND_Y + 150f, assets.impact)
        arenaCamera.shake()
    }

    private fun consume(eventId: ArenaEventId): Boolean {
        if (!consumedEvents.add(eventId)) return false
        while (consumedEvents.size > MAX_EVENT_IDS) {
            val iterator = consumedEvents.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
        return true
    }

    private fun update(delta: Float) {
        elapsed += delta
        leftFighter.update(delta)
        rightFighter.update(delta)
        effects.update(delta)
        arenaCamera.update(delta, paused)

        sparkTimer -= delta
        if (sparkTimer <= 0f) {
            if (leftFighter.hp in 1..40) {
                effects.spawnLowHealthSpark(fighterX(ArenaSide.LEFT), GROUND_Y, assets.blueGlow)
            }
            if (rightFighter.hp in 1..40) {
                effects.spawnLowHealthSpark(fighterX(ArenaSide.RIGHT), GROUND_Y, assets.redGlow)
            }
            sparkTimer = 0.11f
        }
    }

    private fun draw() {
        Gdx.gl.glClearColor(0.018f, 0.028f, 0.065f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        val shapes = assets.shapes
        shapes.setProjectionMatrix(arenaCamera.camera.combined)
        shapes.setTransformMatrix(assets.identity.idt())
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        drawBackground(shapes)
        drawEnergyField(shapes, leftFighter, fighterX(ArenaSide.LEFT), assets.blueGlow)
        drawEnergyField(shapes, rightFighter, fighterX(ArenaSide.RIGHT), assets.redGlow)
        drawRobot(shapes, leftFighter, fighterX(ArenaSide.LEFT), assets.blue, assets.blueDark, assets.blueGlow)
        drawRobot(shapes, rightFighter, fighterX(ArenaSide.RIGHT), assets.red, assets.redDark, assets.redGlow)
        effects.draw(shapes)
        if (paused) drawPausedOverlay(shapes)
        shapes.end()
    }

    private fun drawBackground(shapes: ShapeRenderer) {
        val width = arenaCamera.worldWidth
        val bandHeight = arenaCamera.worldHeight / 10f
        repeat(10) { band ->
            val blend = band / 9f
            shapes.color.set(
                0.025f + blend * 0.035f,
                0.045f + blend * 0.025f,
                0.11f + blend * 0.08f,
                1f
            )
            shapes.rect(0f, arenaCamera.worldHeight - (band + 1) * bandHeight, width, bandHeight + 1f)
        }

        shapes.color.set(0.16f, 0.55f, 0.86f, 0.08f)
        var x = 0f
        while (x <= width) {
            shapes.rect(x, GROUND_Y, 1.5f, arenaCamera.worldHeight - GROUND_Y)
            x += 86f
        }
        var y = GROUND_Y + 42f
        while (y < arenaCamera.worldHeight) {
            shapes.rect(0f, y, width, 1.5f)
            y += 58f
        }

        val pulse = 0.45f + MathUtils.sin(elapsed * 1.7f) * 0.08f
        shapes.color.set(0.25f, 0.75f, 1f, 0.08f * pulse)
        shapes.circle(width * 0.18f, arenaCamera.worldHeight * 0.68f, 132f, 40)
        shapes.color.set(1f, 0.20f, 0.25f, 0.07f * pulse)
        shapes.circle(width * 0.82f, arenaCamera.worldHeight * 0.68f, 132f, 40)

        shapes.color.set(0.035f, 0.055f, 0.09f, 1f)
        shapes.rect(0f, 0f, width, GROUND_Y + 5f)
        shapes.color.set(0.11f, 0.18f, 0.27f, 1f)
        shapes.triangle(0f, GROUND_Y + 5f, width, GROUND_Y + 5f, width * 0.88f, GROUND_Y + 44f)
        shapes.triangle(0f, GROUND_Y + 5f, width * 0.12f, GROUND_Y + 44f, width * 0.88f, GROUND_Y + 44f)
        shapes.color.set(0.22f, 0.72f, 0.92f, 0.55f)
        shapes.rect(width * 0.08f, GROUND_Y + 42f, width * 0.84f, 3f)
        shapes.color.set(0.04f, 0.09f, 0.15f, 1f)
        repeat(9) { panel ->
            val panelWidth = width / 9f
            shapes.rect(panel * panelWidth + 3f, 8f, panelWidth - 6f, GROUND_Y - 8f)
        }
    }

    private fun drawEnergyField(
        shapes: ShapeRenderer,
        fighter: FighterVisualState,
        centerX: Float,
        glow: Color
    ) {
        val x = centerX + fighter.offsetX
        shapes.color.set(0f, 0f, 0f, 0.34f)
        shapes.ellipse(x - 72f, GROUND_Y - 7f, 144f, 27f, 30)

        val charge = fighter.charge
        if (charge <= 0f) return
        val direction = fighter.inwardDirection
        val handX = x + direction * (87f + fighter.attackReach * 67f)
        val handY = GROUND_Y + fighter.offsetY + 148f
        repeat(4) { ring ->
            val radius = 13f + ring * 8f + MathUtils.sin(elapsed * 15f + ring) * 2f
            shapes.color.set(glow.r, glow.g, glow.b, charge * (0.15f - ring * 0.025f))
            shapes.circle(handX, handY, radius, 18)
        }
        shapes.color.set(glow.r, glow.g, glow.b, charge * 0.42f)
        shapes.triangle(
            x - direction * 24f,
            handY - 23f,
            handX,
            handY,
            x - direction * 24f,
            handY + 23f
        )
    }

    private fun drawRobot(
        shapes: ShapeRenderer,
        fighter: FighterVisualState,
        centerX: Float,
        baseColor: Color,
        darkColor: Color,
        glowColor: Color
    ) {
        val center = centerX + fighter.offsetX
        assets.transform.idt()
            .translate(center, GROUND_Y + fighter.offsetY, 0f)
            .rotate(0f, 0f, 1f, fighter.rotation)
        shapes.setTransformMatrix(assets.transform)

        val bodyColor = assets.mixedColor.set(baseColor).lerp(Color.WHITE, fighter.flash)
        val direction = fighter.inwardDirection
        val breath = fighter.idleBreath

        shapes.setColor(darkColor)
        shapes.rectLine(-27f, 47f, -38f, 4f, 28f)
        shapes.rectLine(27f, 47f, 38f, 4f, 28f)
        shapes.rect(-58f, 34f, 116f, 137f * breath)
        shapes.circle(-50f, 151f, 22f, 18)
        shapes.circle(50f, 151f, 22f, 18)

        shapes.setColor(assets.darkMetal)
        shapes.rect(-57f, 178f, 114f, 70f)
        shapes.rect(-10f, 247f, 20f, 18f)
        shapes.circle(0f, 269f, 7f, 12)

        shapes.setColor(bodyColor)
        shapes.rect(-48f, 43f, 96f, 119f * breath)
        shapes.rect(-48f, 186f, 96f, 54f)
        shapes.circle(-50f, 151f, 15f, 16)
        shapes.circle(50f, 151f, 15f, 16)
        shapes.rect(-30f, -1f, 28f, 15f)
        shapes.rect(2f, -1f, 28f, 15f)

        drawArms(shapes, fighter, bodyColor, darkColor)

        shapes.setColor(assets.darkMetal)
        shapes.rect(-37f, 199f, 74f, 23f)
        shapes.setColor(glowColor)
        val visorOffset = direction * 9f
        shapes.rect(-27f + visorOffset, 205f, 54f, 10f)
        shapes.setColor(assets.eye)
        shapes.circle(direction * 25f, 210f, 4.5f, 10)

        val coreColor = when {
            fighter.action == FighterAction.KO -> assets.darkMetal
            fighter.hp <= 40 -> assets.impact
            else -> glowColor
        }
        shapes.setColor(assets.darkMetal)
        shapes.circle(0f, 105f, 30f, 24)
        shapes.setColor(coreColor)
        shapes.circle(0f, 105f, 19f + MathUtils.sin(elapsed * 4f) * 1.5f, 24)
        shapes.color.set(1f, 1f, 1f, if (fighter.action == FighterAction.KO) 0.08f else 0.55f)
        shapes.circle(direction * 5f, 111f, 5f, 12)

        shapes.setTransformMatrix(assets.identity.idt())
    }

    private fun drawArms(
        shapes: ShapeRenderer,
        fighter: FighterVisualState,
        bodyColor: Color,
        darkColor: Color
    ) {
        val direction = fighter.inwardDirection
        val victory = fighter.victoryPose
        val attack = fighter.attackReach
        val frontShoulderX = direction * 49f
        val rearShoulderX = -direction * 49f

        val frontX = if (victory > 0f) {
            direction * (80f - victory * 14f)
        } else {
            direction * (88f + attack * 76f)
        }
        val frontY = if (victory > 0f) 147f + victory * 95f else 143f + attack * 9f
        val rearX = -direction * (75f - victory * 8f)
        val rearY = 130f + victory * 108f

        shapes.setColor(darkColor)
        shapes.rectLine(frontShoulderX, 151f, frontX, frontY, 31f)
        shapes.rectLine(rearShoulderX, 145f, rearX, rearY, 29f)
        shapes.setColor(bodyColor)
        shapes.rectLine(frontShoulderX, 151f, frontX, frontY, 20f)
        shapes.rectLine(rearShoulderX, 145f, rearX, rearY, 18f)
        shapes.circle(frontX, frontY, 14f, 14)
        shapes.circle(rearX, rearY, 13f, 14)
    }

    private fun drawPausedOverlay(shapes: ShapeRenderer) {
        val width = arenaCamera.worldWidth
        shapes.setTransformMatrix(assets.identity.idt())
        shapes.color.set(0.01f, 0.02f, 0.05f, 0.62f)
        shapes.rect(0f, 0f, width, arenaCamera.worldHeight)
        shapes.color.set(0.45f, 0.88f, 1f, 0.8f)
        shapes.circle(width / 2f, arenaCamera.worldHeight / 2f, 54f, 32)
        shapes.color.set(0.03f, 0.08f, 0.14f, 1f)
        shapes.rect(width / 2f - 22f, arenaCamera.worldHeight / 2f - 25f, 13f, 50f)
        shapes.rect(width / 2f + 9f, arenaCamera.worldHeight / 2f - 25f, 13f, 50f)
        shapes.color.set(0.45f, 0.88f, 1f, 0.55f)
        shapes.rect(width * 0.20f, arenaCamera.worldHeight * 0.18f, width * 0.60f, 3f)
    }

    private fun drawSafePlaceholder() {
        Gdx.gl.glClearColor(0.035f, 0.05f, 0.09f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    }

    private fun fighter(side: ArenaSide): FighterVisualState =
        if (side == ArenaSide.LEFT) leftFighter else rightFighter

    private fun fighterX(side: ArenaSide): Float = arenaCamera.worldWidth *
        if (side == ArenaSide.LEFT) 0.25f else 0.75f

    private fun sideDirection(side: ArenaSide): Float = if (side == ArenaSide.LEFT) 1f else -1f

    companion object {
        private const val GROUND_Y = 82f
        private const val MAX_DELTA = 0.05f
        private const val MAX_EVENT_IDS = 96
        private const val TAG = "BattleArenaRenderer"
    }
}
