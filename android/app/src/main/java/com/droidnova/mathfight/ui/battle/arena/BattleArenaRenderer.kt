package com.droidnova.mathfight.ui.battle.arena

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.MathUtils

/** Presentation only. Authoritative HP/questions/results never wait for this timeline. */
internal class BattleArenaRenderer(
    private val bridge: ArenaCommandBridge,
    private val session: Long
) : ApplicationAdapter() {
    private val assets = ArenaAssets()
    private val camera = ArenaCamera()
    private val effects = ArenaEffects()
    private val fallback = ArenaFallbackRenderer()
    private val fighters = Array(2) { FighterVisualState(ArenaSide.entries[it]) }
    // One reusable flight/impact slot per side. A hit is armed only by authoritative damage.
    private class Strike {
        var question = Long.MIN_VALUE
        var pendingHit = false
        var launchPlayed = false
        var attackEvent: ArenaEventId? = null
        var contactEvent: ArenaEventId? = null
        fun reset() {
            question = Long.MIN_VALUE
            pendingHit = false
            launchPlayed = false
            attackEvent = null
            contactEvent = null
        }
    }
    private val strikes = Array(2) { Strike() }
    private val highWater = LongArray(ArenaEventKind.entries.size) { Long.MIN_VALUE }
    private var battleId = Long.MIN_VALUE
    private var winner: ArenaSide? = null
    private var pendingKoEvent: ArenaEventId? = null
    private var pendingVictoryEvent: ArenaEventId? = null
    private var paused = false
    private var initialized = false
    private var disposed = false
    private var elapsed = 0f

    override fun create() {
        try {
            assets.load()
            initialized = true
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        }
        catch (exception: Exception) {
            Gdx.app.debug("BattleArenaRenderer", "Arena GPU resources unavailable", exception)
            assets.dispose()
            bridge.detach(this)
        }
        Gdx.graphics.setContinuousRendering(true)
    }
    override fun resize(width: Int, height: Int) = camera.resize(width, height)
    override fun resume() {
        Gdx.graphics.setContinuousRendering(true)
        Gdx.graphics.requestRendering()
    }
    override fun render() {
        if (disposed) return
        for (command in bridge.drain(session)) applyCommand(command)
        Gdx.gl.glClearColor(0.025f, 0.045f, 0.09f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        if (!initialized) {
            bridge.detach(this)
            return
        }
        val delta = if (paused) 0f else Gdx.graphics.deltaTime.coerceIn(0f, 0.05f)
        try {
            update(delta)
            draw()
        } catch (exception: Exception) {
            Gdx.app.error("BattleArenaRenderer", "Arena render unavailable", exception)
            initialized = false
            bridge.detach(this)
            assets.dispose()
        }
    }
    override fun dispose() {
        if (disposed) return
        disposed = true
        bridge.detach(this)
        effects.clear()
        assets.dispose()
    }
    fun detachBridge() = bridge.detach(this)

    private fun consume(id: ArenaEventId): Boolean {
        if (id.battleId != battleId || id.questionId <= highWater[id.kind.ordinal]) return false
        highWater[id.kind.ordinal] = id.questionId
        return true
    }
    private fun applyCommand(command: ArenaCommand) {
        when (command) {
            is ArenaCommand.Synchronize -> synchronize(command.snapshot)
            is ArenaCommand.Attack -> if (consume(command.eventId) && winner == null) {
                val f = fighters[command.side.ordinal]
                val action = if (command.eventId.questionId % 2L == 0L)
                    FighterAction.ATTACK_PROJECTILE else FighterAction.ATTACK_MELEE
                if (f.play(action)) {
                    strikes[command.side.ordinal].reset()
                    strikes[command.side.ordinal].question = command.eventId.questionId
                    strikes[command.side.ordinal].attackEvent = command.eventId
                    bridge.emitPresentation(
                        session,
                        command.eventId,
                        if (action == FighterAction.ATTACK_MELEE) ArenaPresentationCue.MELEE_SWING
                        else ArenaPresentationCue.ENERGY_CHARGE
                    )
                }
            }
            is ArenaCommand.Hit -> if (consume(command.eventId)) {
                val attacker = command.side.opposite.ordinal
                val strike = strikes[attacker]
                if (strike.question == command.eventId.questionId && fighters[attacker].attacking &&
                    fighters[attacker].time < fighters[attacker].contactTime) {
                    strike.pendingHit = true
                    strike.contactEvent = command.eventId
                } else impact(command.side, command.eventId)
            }
            is ArenaCommand.Ko -> if (consume(command.eventId)) {
                pendingKoEvent = command.eventId
                finishWhenReady(command.side.opposite)
            }
            is ArenaCommand.Victory -> if (consume(command.eventId)) {
                pendingVictoryEvent = command.eventId
                finishWhenReady(command.side)
            }
            is ArenaCommand.PrepareExit -> {
                paused = true
                bridge.acknowledgeExitPrepared(session, command.token)
            }
        }
    }
    private fun synchronize(snapshot: ArenaSnapshot) {
        val fresh = snapshot.battleId != battleId
        if (fresh) {
            battleId = snapshot.battleId
            for (f in fighters) f.reset()
            for (s in strikes) s.reset()
            highWater.fill(Long.MIN_VALUE)
            effects.clear(); camera.reset(); elapsed = 0f; winner = null
            pendingKoEvent = null; pendingVictoryEvent = null
        }
        // A resumed authoritative snapshot may have advanced while this renderer was waiting.
        // Drop unfinished strikes for earlier questions; never replay them after reconnect.
        val resuming = paused && !snapshot.paused
        if (resuming && (snapshot.settled || snapshot.winner != null)) {
            for (index in fighters.indices) {
                if (strikes[index].question < snapshot.questionId || snapshot.winner != null) {
                    fighters[index].reset()
                    strikes[index].reset()
                }
            }
            effects.clear(); camera.reset()
        }
        paused = snapshot.paused
        for (f in fighters) f.paused = paused
        snapshot.winner?.let {
            winner = it
            if (fresh || resuming) {
                fighters[it.ordinal].restore(FighterAction.VICTORY)
                fighters[it.opposite.ordinal].restore(FighterAction.KO)
            }
        }
        // ANSWERING must not truncate the independent 650/800 ms visual sequence.
    }
    private fun finishWhenReady(side: ArenaSide) {
        winner = side
        if (strikes.any { it.pendingHit }) return
        val loser = fighters[side.opposite.ordinal]
        val koEvent = pendingKoEvent
        if (koEvent != null && loser.play(FighterAction.KO)) {
            effects.spawnKoSparks(baseX(loser.side), 170f, loser.side)
            bridge.emitPresentation(session, koEvent, ArenaPresentationCue.KO_POWER_DOWN)
        }
        val victoryEvent = pendingVictoryEvent
        if (victoryEvent != null && fighters[side.ordinal].play(FighterAction.VICTORY)) {
            bridge.emitPresentation(session, victoryEvent, ArenaPresentationCue.VICTORY)
        }
    }
    private fun impact(side: ArenaSide, eventId: ArenaEventId) {
        if (fighters[side.ordinal].terminal) return
        fighters[side.ordinal].play(FighterAction.HIT)
        effects.spawnImpact(
            baseX(side) + fighters[side.ordinal].direction * 45f,
            225f,
            side.opposite
        )
        camera.shake()
        bridge.emitPresentation(session, eventId, ArenaPresentationCue.IMPACT)
        bridge.emitPresentation(session, eventId, ArenaPresentationCue.HIT_REACTION)
    }
    private fun update(delta: Float) {
        if (delta <= 0f) return
        elapsed += delta
        effects.update(delta)
        for (index in fighters.indices) {
            val f = fighters[index]
            val strike = strikes[index]
            f.update(delta)
            if (f.pose == FighterAction.ATTACK_PROJECTILE && f.time >= 0.20f && !strike.launchPlayed) {
                strike.launchPlayed = true
                strike.attackEvent?.let {
                    bridge.emitPresentation(session, it, ArenaPresentationCue.PROJECTILE_LAUNCH)
                }
            }
            if (strike.pendingHit && (!f.attacking || f.time >= f.contactTime)) {
                strike.pendingHit = false
                strike.contactEvent?.let { impact(f.side.opposite, it) }
            }
        }
        winner?.let { finishWhenReady(it) }
        camera.update(delta)
    }
    private fun draw() {
        if (!assets.artworkReady) {
            fallback.draw(assets.shapes, camera, fighters, paused, elapsed)
            return
        }
        camera.apply()
        val batch = assets.batch
        batch.projectionMatrix = camera.camera.combined
        batch.begin()
        batch.setColor(1f, 1f, 1f, 1f)
        batch.draw(assets.background, 0f, 0f, 1000f, 500f)
        batch.draw(assets.platform, 0f, 0f, 1000f, 120f)
        for (f in fighters) {
            val x = baseX(f.side) + f.offsetX
            assets.shadow?.let {
                batch.setColor(1f, 1f, 1f, 0.6f)
                batch.draw(it, x - 75f, 75f, 150f + f.offsetY, 25f)
            }
            batch.setColor(1f, 1f - f.flash * 0.55f, 1f - f.flash * 0.55f, 1f)
            batch.draw(assets.frame(f), x - 150f, 50f + f.offsetY, 150f, 0f,
                300f, 300f, 1f, 1f, f.rotation)
            val glow = assets.glow
            if (glow != null) {
                tint(f.side, f.charge)
                batch.draw(glow, x + f.direction * 80f - 35f, 190f, 70f, 70f)
                tint(f.side, 0.10f + MathUtils.sin(elapsed * 2f) * 0.025f)
                batch.draw(glow, baseX(f.side) - 95f, 60f, 190f, 40f)
            }
            if (f.pose == FighterAction.VICTORY && f.progress < 0.72f) {
                assets.victoryPulse?.let { pulse ->
                    val size = 95f + f.progress * 310f
                    tint(f.side, (1f - f.progress / 0.72f).coerceIn(0f, 1f) * 0.72f)
                    batch.draw(pulse, x - size / 2f, 210f - size / 2f, size, size)
                }
            }
            drawProjectile(f)
        }
        effects.draw(batch, assets.impact, assets.burst, assets.spark)
        batch.setColor(1f, 1f, 1f, 1f)
        if (paused) assets.pauseIcon?.let { batch.draw(it, 468f, 218f, 64f, 64f) }
        batch.end()
    }
    private fun drawProjectile(f: FighterVisualState) {
        val region = assets.projectile ?: return
        if (f.pose != FighterAction.ATTACK_PROJECTILE || f.time < 0.20f || f.time >= 0.56f) return
        val progress = (f.time - 0.20f) / 0.36f
        val start = baseX(f.side) + f.direction * 90f
        val end = baseX(f.side.opposite) - f.direction * 45f
        val x = start + (end - start) * progress
        repeat(4) { trail ->
            val size = 42f - trail * 7f
            tint(f.side, 1f - trail * 0.24f)
            assets.batch.draw(
                region,
                x - f.direction * trail * 16f - size / 2f,
                225f - size / 2f,
                size / 2f,
                size / 2f,
                size,
                size,
                f.direction,
                1f,
                0f
            )
        }
    }
    private fun tint(side: ArenaSide, alpha: Float) {
        if (side == ArenaSide.LEFT) assets.batch.setColor(0.3f, 0.85f, 1f, alpha)
        else assets.batch.setColor(1f, 0.45f, 0.2f, alpha)
    }
    private fun baseX(side: ArenaSide) = if (side == ArenaSide.LEFT) 260f else 740f
}
