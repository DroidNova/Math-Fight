package com.droidnova.mathfight.ui.battle.arena

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Serializes restart and exit actions without ever blocking the Compose main thread. */
class BattleExitCoordinator(private val arenaHost: ActivityArenaHost) {
    private val mutableExitInProgress = MutableStateFlow(false)
    private var currentBattleId = Long.MIN_VALUE

    val exitInProgress = mutableExitInProgress.asStateFlow()

    fun beginBattle(battleId: Long) {
        if (currentBattleId == battleId) return
        currentBattleId = battleId
        mutableExitInProgress.value = false
    }

    fun restart(action: () -> Unit) {
        if (!claimAction()) return
        action()
    }

    fun exit(action: () -> Unit) {
        if (!claimAction()) return
        arenaHost.prepareForExit(action)
    }

    private fun claimAction(): Boolean {
        if (mutableExitInProgress.value) return false
        mutableExitInProgress.value = true
        return true
    }
}
