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
    private val fighters = Array(2) { FighterVisualState(ArenaSide.entries[it]) }
    // One reusable flight/impact slot per side. A hit is armed only by authoritative damage.
    private class Strike {
        var question = Long.MIN_VALUE
        var pendingHit = false
        fun reset() { question = Long.MIN_VALUE; pendingHit = false }
    }
    private val strikes = Array(2) { Strike() }
    private val highWater = LongArray(ArenaEventKind.entries.size) { Long.MIN_VALUE }
    private var battleId = Long.MIN_VALUE
    private var winner: ArenaSide? = null
    private var paused = false
    private var initialized = false
    private var disposed = false
    private var elapsed = 0f

    override fun create() {
        try { assets.load(); initialized = true }
        catch (exception: Exception) {
            Gdx.app.error("BattleArenaRenderer", "Arena assets unavailable", exception)
            assets.dispose()
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
        if (!initialized) return
        val delta = if (paused) 0f else Gdx.graphics.deltaTime.coerceIn(0f, 0.05f)
        try {
            update(delta)
            draw()
        } catch (exception: Exception) {
            Gdx.app.error("BattleArenaRenderer", "Arena render unavailable", exception)
            initialized = false
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
                }
            }
            is ArenaCommand.Hit -> if (consume(command.eventId)) {
                val attacker = command.side.opposite.ordinal
                val strike = strikes[attacker]
                if (strike.question == command.eventId.questionId && fighters[attacker].attacking &&
                    fighters[attacker].time < fighters[attacker].contactTime) strike.pendingHit = true
                else impact(command.side)
            }
            is ArenaCommand.Ko -> if (consume(command.eventId)) finishWhenReady(command.side.opposite)
            is ArenaCommand.Victory -> if (consume(command.eventId)) finishWhenReady(command.side)
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
            } else finishWhenReady(it)
        }
        // ANSWERING must not truncate the independent 650/800 ms visual sequence.
    }
    private fun finishWhenReady(side: ArenaSide) {
        winner = side
        if (strikes.any { it.pendingHit }) return
        val loser = fighters[side.opposite.ordinal]
        if (loser.play(FighterAction.KO)) effects.burst(baseX(loser.side), 170f)
        fighters[side.ordinal].play(FighterAction.VICTORY)
    }
    private fun impact(side: ArenaSide) {
        if (fighters[side.ordinal].terminal) return
        fighters[side.ordinal].play(FighterAction.HIT)
        effects.burst(baseX(side) + fighters[side.ordinal].direction * 45f, 225f)
        camera.shake()
    }
    private fun update(delta: Float) {
        if (delta <= 0f) return
        elapsed += delta
        effects.update(delta)
        for (index in fighters.indices) {
            val f = fighters[index]
            val strike = strikes[index]
            f.update(delta)
            if (strike.pendingHit && (!f.attacking || f.time >= f.contactTime)) {
                strike.pendingHit = false
                impact(f.side.opposite)
            }
        }
        winner?.let { finishWhenReady(it) }
        camera.update(delta)
    }
    private fun draw() {
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
            batch.draw(assets.frame(f), x - 150f, 82f + f.offsetY, 150f, 0f,
                300f, 300f, 1f, 1f, f.rotation)
            val glow = assets.glow
            if (glow != null) {
                val victory = if (f.pose == FighterAction.VICTORY) 1f - f.progress else 0f
                val size = if (victory > 0f) 100f + f.progress * 280f else 70f
                val alpha = maxOf(f.charge, victory * 0.6f)
                tint(f.side, alpha)
                batch.draw(glow, x + f.direction * 80f - size / 2f, 225f - size / 2f, size, size)
                tint(f.side, 0.10f + MathUtils.sin(elapsed * 2f) * 0.025f)
                batch.draw(glow, baseX(f.side) - 95f, 60f, 190f, 40f)
            }
            drawProjectile(f)
        }
        effects.draw(batch, assets.impact)
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
            assets.batch.draw(region, x - f.direction * trail * 16f - size / 2f, 225f - size / 2f, size, size)
        }
    }
    private fun tint(side: ArenaSide, alpha: Float) {
        if (side == ArenaSide.LEFT) assets.batch.setColor(0.3f, 0.85f, 1f, alpha)
        else assets.batch.setColor(1f, 0.45f, 0.2f, alpha)
    }
    private fun baseX(side: ArenaSide) = if (side == ArenaSide.LEFT) 260f else 740f
}
