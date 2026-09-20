package com.droidnova.mathfight.ui.battle.arena

import android.app.ActivityManager
import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.badlogic.gdx.utils.GdxNativesLoader
import com.droidnova.mathfight.R

/**
 * Activity-owned arena host. The Fragment and SurfaceView are never removed by Compose; Compose
 * only reports the bounds where the already-attached arena should be presented.
 */
class ActivityArenaHost(
    activity: FragmentActivity,
    private val root: FrameLayout,
    private val composeView: ComposeView,
    private val container: FragmentContainerView,
    private val bridge: ArenaCommandBridge
) {
    private val fragmentManager = activity.supportFragmentManager
    private val rootLocation = IntArray(2)
    private var presentedBattleId = Long.MIN_VALUE
    private var exitPreparing = false
    private var destroyed = false

    val available: Boolean = libGdxAvailable(activity.applicationContext)
    val session: Long = if (available) bridge.beginSession() else ArenaCommandBridge.NO_SESSION

    init {
        container.id = R.id.battle_arena_fragment_container
        container.visibility = View.VISIBLE
        if (available) installFragment()
        composeView.bringToFront()
    }

    fun presentBattle(battleId: Long, boundsInWindow: Rect) {
        if (!available || destroyed || boundsInWindow.width() <= 0 || boundsInWindow.height() <= 0) return
        if (presentedBattleId != battleId) {
            presentedBattleId = battleId
            exitPreparing = false
            bridge.resumeCommands(session)
        }
        if (exitPreparing) return

        root.getLocationInWindow(rootLocation)
        val params = container.layoutParams as FrameLayout.LayoutParams
        params.width = boundsInWindow.width()
        params.height = boundsInWindow.height()
        params.leftMargin = boundsInWindow.left - rootLocation[0]
        params.topMargin = boundsInWindow.top - rootLocation[1]
        container.layoutParams = params
        container.bringToFront()
    }

    fun prepareForExit(onPrepared: () -> Unit) {
        if (destroyed) return
        if (!available || session == ArenaCommandBridge.NO_SESSION) {
            coverArena()
            onPrepared()
            return
        }
        if (exitPreparing) return
        exitPreparing = true
        (fragmentManager.findFragmentByTag(FRAGMENT_TAG) as? BattleArenaFragment)
            ?.prepareForBattleExit()
        bridge.prepareExit(session) {
            if (destroyed) return@prepareExit
            coverArena()
            onPrepared()
        }
    }

    fun coverArena() {
        if (!destroyed) composeView.bringToFront()
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        bridge.endSession(session)
    }

    private fun installFragment() {
        if (fragmentManager.findFragmentByTag(FRAGMENT_TAG) != null) return
        fragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .add(container.id, BattleArenaFragment.newInstance(session), FRAGMENT_TAG)
            .commitNow()
    }

    private fun libGdxAvailable(context: Context): Boolean = runCatching {
        GdxNativesLoader.load()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.deviceConfigurationInfo.reqGlEsVersion >= 0x00020000
    }.getOrDefault(false)

    companion object {
        private const val FRAGMENT_TAG = "battle_arena_fragment"
    }
}

interface ArenaHostProvider : ArenaBridgeProvider {
    val arenaHost: ActivityArenaHost
}
