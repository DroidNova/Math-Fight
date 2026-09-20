package com.droidnova.mathfight.ui.battle.arena

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils

/** Fixed storage: no particle/projectile allocation during playback. */
internal class ArenaEffects {
    private class Particle {
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var life = 0f
    }
    private val particles = Array(48) { Particle() }
    private var cursor = 0
    private val flashes = Array(4) { Particle() }
    private var flashCursor = 0
    fun burst(x: Float, y: Float) {
        val flash = flashes[flashCursor]
        flashCursor = (flashCursor + 1) % flashes.size
        flash.x = x; flash.y = y; flash.life = 0.2f
        repeat(12) { index ->
            val p = particles[cursor]
            cursor = (cursor + 1) % particles.size
            val angle = index * MathUtils.PI2 / 12f
            p.x = x; p.y = y; p.life = 0.32f
            p.vx = MathUtils.cos(angle) * (100f + index * 9f)
            p.vy = MathUtils.sin(angle) * (100f + index * 9f)
        }
    }
    fun update(delta: Float) {
        for (f in flashes) f.life = (f.life - delta).coerceAtLeast(0f)
        for (p in particles) if (p.life > 0f) {
            p.life = (p.life - delta).coerceAtLeast(0f)
            p.x += p.vx * delta; p.y += p.vy * delta
        }
    }
    fun draw(batch: SpriteBatch, region: TextureRegion?) {
        if (region == null) return
        for (p in particles) if (p.life > 0f) {
            batch.setColor(1f, 0.9f, 0.45f, p.life / 0.32f)
            batch.draw(region, p.x - 7f, p.y - 7f, 14f, 14f)
        }
        for (f in flashes) if (f.life > 0f) {
            val size = 50f + (1f - f.life / 0.2f) * 70f
            batch.setColor(1f, 1f, 0.8f, f.life / 0.2f)
            batch.draw(region, f.x - size / 2f, f.y - size / 2f, size, size)
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }
    fun clear() {
        for (p in particles) p.life = 0f
        for (f in flashes) f.life = 0f
        cursor = 0; flashCursor = 0
    }
}
