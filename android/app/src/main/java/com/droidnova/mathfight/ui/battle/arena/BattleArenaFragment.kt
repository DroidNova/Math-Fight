package com.droidnova.mathfight.ui.battle.arena

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.backends.android.AndroidFragmentApplication

/** Official libGDX Android fragment backend, hosted only inside the Compose arena bounds. */
class BattleArenaFragment : AndroidFragmentApplication() {
    private var renderer: BattleArenaRenderer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val provider = requireActivity() as ArenaBridgeProvider
        val bridge = provider.arenaCommandBridge
        val session = requireArguments().getLong(ARG_SESSION, ArenaCommandBridge.NO_SESSION)
        require(session != ArenaCommandBridge.NO_SESSION) { "Missing battle arena session" }

        val arenaRenderer = BattleArenaRenderer(bridge, session)
        renderer = arenaRenderer
        val configuration = AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            useRotationVectorSensor = false
            disableAudio = true
            useImmersiveMode = false
            useWakelock = false
            useGL30 = false
        }
        val renderView = initializeForView(arenaRenderer, configuration)
        renderView.isFocusable = false
        renderView.isFocusableInTouchMode = false
        bridge.attach(session, arenaRenderer) { graphics.requestRendering() }
        return renderView
    }

    fun prepareForBattleExit() {
        graphics?.setContinuousRendering(true)
        graphics?.requestRendering()
    }

    override fun onPause() {
        // AndroidGraphics.pause() waits for the GL thread to consume one final lifecycle frame.
        // Keep that thread schedulable before the backend begins its pause synchronization.
        graphics?.setContinuousRendering(true)
        graphics?.requestRendering()
        super.onPause()
    }

    override fun onDestroyView() {
        renderer?.detachBridge()
        renderer = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_SESSION = "arena_session"

        fun newInstance(session: Long) = BattleArenaFragment().apply {
            arguments = Bundle().apply { putLong(ARG_SESSION, session) }
        }
    }
}
