package com.droidnova.mathfight.ui.battle

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.droidnova.mathfight.R

enum class CombatSound {
    MELEE_SWING,
    ENERGY_CHARGE,
    PROJECTILE_LAUNCH,
    IMPACT,
    HIT_REACTION,
    KO_POWER_DOWN,
    VICTORY
}

/** Activity-owned, best-effort offline audio. All calls and load callbacks run on main. */
class CombatAudio(context: Context) {
    private var released = false
    private val ready = mutableSetOf<Int>()
    private val streams = mutableSetOf<Int>()
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val samples: Map<CombatSound, Int>

    init {
        pool.setOnLoadCompleteListener { _, sample, status ->
            if (!released && status == 0) ready.add(sample)
        }
        samples = mapOf(
            CombatSound.MELEE_SWING to pool.load(context, R.raw.melee_swing, 1),
            CombatSound.ENERGY_CHARGE to pool.load(context, R.raw.energy_charge, 1),
            CombatSound.PROJECTILE_LAUNCH to pool.load(context, R.raw.projectile_launch, 1),
            CombatSound.IMPACT to pool.load(context, R.raw.impact, 1),
            CombatSound.HIT_REACTION to pool.load(context, R.raw.hit_reaction, 1),
            CombatSound.KO_POWER_DOWN to pool.load(context, R.raw.ko_power_down, 1),
            CombatSound.VICTORY to pool.load(context, R.raw.victory_stinger, 1)
        )
    }

    fun play(kind: CombatSound) {
        if (released) return
        val sample = samples[kind] ?: return
        if (sample !in ready) return
        val volume = when (kind) {
            CombatSound.IMPACT -> 0.72f
            CombatSound.HIT_REACTION -> 0.48f
            CombatSound.VICTORY -> 0.68f
            else -> 0.60f
        }
        val stream = pool.play(sample, volume, volume, 1, 0, 1f)
        if (stream != 0) {
            streams.add(stream)
            while (streams.size > MAX_TRACKED_STREAMS) {
                val oldest = streams.first()
                streams.remove(oldest)
                pool.stop(oldest)
            }
        }
    }

    /** Used only when the libGDX presentation renderer is unavailable. */
    fun playImmediate(kind: FeedbackKind) {
        if (kind == FeedbackKind.HIT) {
            play(CombatSound.IMPACT)
            play(CombatSound.HIT_REACTION)
        } else {
            play(CombatSound.KO_POWER_DOWN)
            play(CombatSound.VICTORY)
        }
    }

    fun stop() {
        if (released) return
        streams.forEach(pool::stop)
        streams.clear()
    }

    fun release() {
        if (released) return
        stop()
        released = true
        pool.setOnLoadCompleteListener(null)
        ready.clear()
        pool.release()
    }

    companion object {
        private const val MAX_TRACKED_STREAMS = 16
    }
}
