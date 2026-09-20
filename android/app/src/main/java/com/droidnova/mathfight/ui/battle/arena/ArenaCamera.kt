package com.droidnova.mathfight.ui.battle.arena

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.viewport.FitViewport

/** Fixed composition with letterboxing, never stretched or cropped on narrow screens. */
internal class ArenaCamera {
    val camera = OrthographicCamera()
    private val viewport = FitViewport(1000f, 500f, camera)
    private var remaining = 0f
    fun resize(width: Int, height: Int) { if (width > 0 && height > 0) viewport.update(width, height, true) }
    fun apply() = viewport.apply()
    fun shake() { remaining = 0.18f }
    fun update(delta: Float) {
        if (delta <= 0f) return
        remaining = (remaining - delta).coerceAtLeast(0f)
        val strength = remaining / 0.18f * 5f
        camera.position.set(500f + MathUtils.sin(remaining * 170f) * strength,
            250f + MathUtils.cos(remaining * 140f) * strength * 0.4f, 0f)
        camera.update()
    }
    fun reset() { remaining = 0f; camera.position.set(500f, 250f, 0f); camera.update() }
}
