package com.droidnova.mathfight.ui.battle.arena

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer

/** One manager owns the atlas textures; the batch owns no artwork. No Activity references. */
internal class ArenaAssets {
    private val manager = AssetManager()
    private var disposed = false
    lateinit var batch: SpriteBatch
        private set
    lateinit var shapes: ShapeRenderer
        private set
    var artworkReady = false
        private set
    private lateinit var atlas: TextureAtlas
    private lateinit var frames: Array<Array<Array<TextureRegion>>>
    lateinit var background: TextureRegion
    lateinit var platform: TextureRegion
    var shadow: TextureRegion? = null
    var glow: TextureRegion? = null
    var projectile: TextureRegion? = null
    var impact: TextureRegion? = null
    var burst: TextureRegion? = null
    var spark: TextureRegion? = null
    var victoryPulse: TextureRegion? = null
    var pauseIcon: TextureRegion? = null

    fun load() {
        batch = SpriteBatch()
        shapes = ShapeRenderer()
        artworkReady = try {
            manager.load("arena/combat.atlas", TextureAtlas::class.java)
            manager.finishLoading()
            atlas = manager.get("arena/combat.atlas", TextureAtlas::class.java)
            background = requireNotNull(atlas.findRegion("arena/background"))
            platform = requireNotNull(atlas.findRegion("arena/platform"))
            frames = Array(2) { side ->
                val prefix = if (side == 0) "blue" else "red"
                val idle = requireNotNull(atlas.findRegion("$prefix/idle", 0))
                Array(FighterAction.entries.size) { index ->
                    val regions = atlas.findRegions("$prefix/${FighterAction.entries[index].region}")
                    if (regions.size == 0) arrayOf<TextureRegion>(idle)
                    else Array<TextureRegion>(regions.size) { regions[it] }
                }
            }
            pauseIcon = atlas.findRegion("fx/paused")
            shadow = atlas.findRegion("fx/shadow")
            glow = atlas.findRegion("fx/glow")
            projectile = atlas.findRegion("fx/projectile")
            impact = atlas.findRegion("fx/impact")
            burst = atlas.findRegion("fx/burst")
            spark = atlas.findRegion("fx/spark")
            victoryPulse = atlas.findRegion("fx/victory")
            true
        } catch (exception: Exception) {
            Gdx.app?.debug(TAG, "Combat atlas unavailable; using procedural fallback", exception)
            false
        }
    }
    fun frame(fighter: FighterVisualState): TextureRegion {
        val animation = frames[fighter.side.ordinal][fighter.pose.ordinal]
        return animation[(fighter.progress * animation.size).toInt().coerceAtMost(animation.lastIndex)]
    }
    fun dispose() {
        if (disposed) return
        disposed = true
        if (::batch.isInitialized) batch.dispose()
        if (::shapes.isInitialized) shapes.dispose()
        manager.dispose()
    }

    companion object {
        private const val TAG = "ArenaAssets"
    }
}
