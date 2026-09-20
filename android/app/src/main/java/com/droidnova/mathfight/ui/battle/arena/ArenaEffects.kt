package com.droidnova.mathfight.ui.battle.arena

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils

/** Small, bounded particle collection owned exclusively by the render thread. */
internal class ArenaEffects {
    private data class Particle(
        var x: Float,
        var y: Float,
        val velocityX: Float,
        val velocityY: Float,
        val duration: Float,
        var life: Float,
        val size: Float,
        val red: Float,
        val green: Float,
        val blue: Float
    )

    private data class Burst(var x: Float, var y: Float, var life: Float = 0f)

    private val particles = ArrayList<Particle>(MAX_PARTICLES)
    private val bursts = ArrayList<Burst>(MAX_BURSTS)

    fun spawnImpact(x: Float, y: Float, color: Color) {
        if (bursts.size == MAX_BURSTS) bursts.removeAt(0)
        bursts.add(Burst(x, y))
        repeat(16) { index ->
            val angle = index * (MathUtils.PI2 / 16f) + MathUtils.random(-0.12f, 0.12f)
            val speed = MathUtils.random(105f, 245f)
            addParticle(
                x = x,
                y = y,
                velocityX = MathUtils.cos(angle) * speed,
                velocityY = MathUtils.sin(angle) * speed,
                duration = MathUtils.random(0.18f, 0.34f),
                size = MathUtils.random(3.5f, 8f),
                color = if (index % 3 == 0) Color.WHITE else color
            )
        }
    }

    fun spawnLowHealthSpark(x: Float, y: Float, color: Color) {
        addParticle(
            x = x + MathUtils.random(-34f, 34f),
            y = y + MathUtils.random(52f, 154f),
            velocityX = MathUtils.random(-24f, 24f),
            velocityY = MathUtils.random(32f, 76f),
            duration = MathUtils.random(0.2f, 0.42f),
            size = MathUtils.random(2f, 4.5f),
            color = color
        )
    }

    fun update(delta: Float) {
        for (index in particles.lastIndex downTo 0) {
            val particle = particles[index]
            particle.life += delta
            if (particle.life >= particle.duration) {
                particles.removeAt(index)
            } else {
                particle.x += particle.velocityX * delta
                particle.y += particle.velocityY * delta
            }
        }
        for (index in bursts.lastIndex downTo 0) {
            val burst = bursts[index]
            burst.life += delta
            if (burst.life >= BURST_DURATION) bursts.removeAt(index)
        }
    }

    fun draw(shapes: ShapeRenderer) {
        bursts.forEach { burst ->
            val progress = burst.life / BURST_DURATION
            val alpha = (1f - progress).coerceAtLeast(0f)
            shapes.color.set(0.88f, 0.98f, 1f, alpha * 0.22f)
            shapes.circle(burst.x, burst.y, 18f + progress * 58f, 24)
            shapes.color.set(1f, 0.93f, 0.52f, alpha * 0.9f)
            repeat(8) { ray ->
                val angle = ray * MathUtils.PI2 / 8f
                val inner = 22f + progress * 18f
                val outer = 52f + progress * 72f
                shapes.rectLine(
                    burst.x + MathUtils.cos(angle) * inner,
                    burst.y + MathUtils.sin(angle) * inner,
                    burst.x + MathUtils.cos(angle) * outer,
                    burst.y + MathUtils.sin(angle) * outer,
                    3.5f * alpha.coerceAtLeast(0.2f)
                )
            }
        }
        particles.forEach { particle ->
            val progress = particle.life / particle.duration
            val alpha = (1f - progress).coerceAtLeast(0f)
            shapes.color.set(particle.red, particle.green, particle.blue, alpha)
            shapes.circle(particle.x, particle.y, particle.size * (1f - progress * 0.45f), 8)
        }
    }

    fun clear() {
        particles.clear()
        bursts.clear()
    }

    private fun addParticle(
        x: Float,
        y: Float,
        velocityX: Float,
        velocityY: Float,
        duration: Float,
        size: Float,
        color: Color
    ) {
        if (particles.size == MAX_PARTICLES) particles.removeAt(0)
        particles.add(
            Particle(x, y, velocityX, velocityY, duration, 0f, size, color.r, color.g, color.b)
        )
    }

    companion object {
        private const val MAX_PARTICLES = 72
        private const val MAX_BURSTS = 4
        private const val BURST_DURATION = 0.24f
    }
}
