package com.droidnova.mathfight.ui.battle.arena

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4

/**
 * Owns the arena's GPU resources and palette. Placeholder shapes can later be replaced by
 * repository-owned textures here without changing gameplay or the Compose bridge.
 */
internal class ArenaAssets {
    lateinit var shapes: ShapeRenderer
        private set
    val transform = Matrix4()
    val identity = Matrix4()
    val mixedColor = Color()

    val blue = Color(0.10f, 0.56f, 1f, 1f)
    val blueDark = Color(0.035f, 0.18f, 0.38f, 1f)
    val blueGlow = Color(0.18f, 0.78f, 1f, 1f)
    val red = Color(1f, 0.20f, 0.24f, 1f)
    val redDark = Color(0.40f, 0.035f, 0.07f, 1f)
    val redGlow = Color(1f, 0.45f, 0.23f, 1f)
    val metal = Color(0.72f, 0.79f, 0.88f, 1f)
    val darkMetal = Color(0.10f, 0.14f, 0.22f, 1f)
    val eye = Color(0.88f, 1f, 1f, 1f)
    val impact = Color(1f, 0.83f, 0.24f, 1f)

    private var loaded = false

    fun load() {
        if (loaded) return
        shapes = ShapeRenderer()
        loaded = true
    }

    fun dispose() {
        if (!loaded) return
        loaded = false
        shapes.dispose()
    }
}
