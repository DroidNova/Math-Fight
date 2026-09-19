package com.droidnova.mathfight.ui.battle

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.droidnova.mathfight.R

/** Activity-owned, best-effort offline audio. All calls and load callbacks run on main. */
class CombatAudio(context: Context) {
    private var released = false
    private val ready = mutableSetOf<Int>()
    private val streams = mutableSetOf<Int>()
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val punch: Int
    private val ko: Int

    init {
        pool.setOnLoadCompleteListener { _, sample, status ->
            if (!released && status == 0) ready.add(sample)
        }
        punch = pool.load(context, R.raw.punch, 1)
        ko = pool.load(context, R.raw.ko, 1)
    }

    fun play(kind: FeedbackKind) {
        if (released) return
        val sample = if (kind == FeedbackKind.HIT) punch else ko
        if (sample !in ready) return
        // Hits are separated by seconds; also bound bookkeeping for completed streams.
        stop()
        val stream = pool.play(sample, 0.65f, 0.65f, 1, 0, 1f)
        if (stream != 0) streams.add(stream)
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
}
