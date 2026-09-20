package com.droidnova.mathfight.ui.battle.arena

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.MathUtils

/** Camera sizing and short impact shake, independent from combat state. */
internal class ArenaCamera {
    val camera = OrthographicCamera()
    var worldWidth: Float = 1_000f
        private set
    val worldHeight: Float = 500f

    private var shakeRemaining = 0f
    private var shakeDuration = 0f
    private var shakeStrength = 0f

    fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        worldWidth = (worldHeight * width.toFloat() / height.toFloat()).coerceAtLeast(720f)
        camera.setToOrtho(false, worldWidth, worldHeight)
        camera.position.set(worldWidth / 2f, worldHeight / 2f, 0f)
        camera.update()
    }

    fun shake(strength: Float = 7f, duration: Float = 0.16f) {
        shakeStrength = maxOf(shakeStrength, strength)
        shakeDuration = maxOf(shakeDuration, duration)
        shakeRemaining = maxOf(shakeRemaining, duration)
    }

    fun update(delta: Float, paused: Boolean) {
        if (!paused) shakeRemaining = (shakeRemaining - delta).coerceAtLeast(0f)
        val amount = if (shakeDuration > 0f) shakeStrength * (shakeRemaining / shakeDuration) else 0f
        val x = if (amount > 0f) MathUtils.random(-amount, amount) else 0f
        val y = if (amount > 0f) MathUtils.random(-amount * 0.45f, amount * 0.45f) else 0f
        camera.position.set(worldWidth / 2f + x, worldHeight / 2f + y, 0f)
        camera.update()
        if (shakeRemaining == 0f) {
            shakeStrength = 0f
            shakeDuration = 0f
        }
    }

    fun reset() {
        shakeRemaining = 0f
        shakeDuration = 0f
        shakeStrength = 0f
        camera.position.set(worldWidth / 2f, worldHeight / 2f, 0f)
        camera.update()
    }
}
