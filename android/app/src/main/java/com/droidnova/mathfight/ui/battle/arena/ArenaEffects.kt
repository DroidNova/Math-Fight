package com.droidnova.mathfight.ui.battle.arena

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils

/** Fixed storage: no particle/projectile allocation during playback. */
internal class ArenaEffects {
    private class Particle {
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var life = 0f
        var side = ArenaSide.LEFT
        var spark = false
    }
    private val particles = Array(48) { Particle() }
    private var cursor = 0
    private val flashes = Array(4) { Particle() }
    private var flashCursor = 0
    fun spawnImpact(x: Float, y: Float, side: ArenaSide) {
        val flash = flashes[flashCursor]
        flashCursor = (flashCursor + 1) % flashes.size
        flash.x = x; flash.y = y; flash.life = 0.2f; flash.side = side
        repeat(12) { index ->
            val p = particles[cursor]
            cursor = (cursor + 1) % particles.size
            val angle = index * MathUtils.PI2 / 12f
            p.x = x; p.y = y; p.life = 0.32f
            p.vx = MathUtils.cos(angle) * (100f + index * 9f)
            p.vy = MathUtils.sin(angle) * (100f + index * 9f)
            p.side = side; p.spark = false
        }
    }
    fun spawnKoSparks(x: Float, y: Float, side: ArenaSide) {
        repeat(10) { index ->
            val p = particles[cursor]
            cursor = (cursor + 1) % particles.size
            val angle = MathUtils.PI * (0.12f + index / 12f)
            p.x = x + MathUtils.sin(index * 2.1f) * 30f
            p.y = y + 15f; p.life = 0.42f
            p.vx = MathUtils.cos(angle) * (75f + index * 6f)
            p.vy = MathUtils.sin(angle) * (105f + index * 7f)
            p.side = side; p.spark = true
        }
    }
    fun update(delta: Float) {
        for (f in flashes) f.life = (f.life - delta).coerceAtLeast(0f)
        for (p in particles) if (p.life > 0f) {
            p.life = (p.life - delta).coerceAtLeast(0f)
            p.x += p.vx * delta; p.y += p.vy * delta
        }
    }
    fun draw(
        batch: SpriteBatch,
        impactRegion: TextureRegion?,
        particleRegion: TextureRegion?,
        sparkRegion: TextureRegion?
    ) {
        for (p in particles) if (p.life > 0f) {
            val region = if (p.spark) sparkRegion else particleRegion
            if (region != null) {
                tint(batch, p.side, (p.life / if (p.spark) 0.42f else 0.32f).coerceIn(0f, 1f))
                val size = if (p.spark) 18f else 13f
                batch.draw(region, p.x - size / 2f, p.y - size / 2f, size, size)
            }
        }
        for (f in flashes) if (f.life > 0f && impactRegion != null) {
            val size = 50f + (1f - f.life / 0.2f) * 70f
            tint(batch, f.side, f.life / 0.2f)
            batch.draw(impactRegion, f.x - size / 2f, f.y - size / 2f, size, size)
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }
    fun clear() {
        for (p in particles) p.life = 0f
        for (f in flashes) f.life = 0f
        cursor = 0; flashCursor = 0
    }

    private fun tint(batch: SpriteBatch, side: ArenaSide, alpha: Float) {
        if (side == ArenaSide.LEFT) batch.setColor(0.45f, 0.92f, 1f, alpha)
        else batch.setColor(1f, 0.57f, 0.22f, alpha)
    }
}
