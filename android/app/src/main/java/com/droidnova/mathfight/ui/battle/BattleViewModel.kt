package com.droidnova.mathfight.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidnova.mathfight.game.BattlePhase
import com.droidnova.mathfight.game.BattleState
import com.droidnova.mathfight.game.advancePhase
import com.droidnova.mathfight.game.clearInput
import com.droidnova.mathfight.game.durationMillis
import com.droidnova.mathfight.game.enterDigit
import com.droidnova.mathfight.game.eraseDigit
import com.droidnova.mathfight.game.newBattle
import com.droidnova.mathfight.game.submitAnswer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class BattleViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(BattleState())
    val state = mutableState.asStateFlow()

    private val resumed = MutableStateFlow(false)
    val isResumed = resumed.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                mutableState.map { it.key }.distinctUntilChanged(),
                resumed
            ) { key, active -> key.takeIf { active } }
                .distinctUntilChanged()
                .collectLatest { key ->
                    if (key == null) return@collectLatest
                    val duration = key.phase.durationMillis() ?: return@collectLatest
                    delay(duration)
                    if (resumed.value) {
                        mutableState.value = advancePhase(mutableState.value, key)
                    }
                }
        }
    }

    fun setResumed(value: Boolean) {
        resumed.value = value
    }

    private fun edit(transform: (BattleState) -> BattleState) {
        if (resumed.value) mutableState.value = transform(mutableState.value)
    }

    fun startBattle() = edit { if (it.phase == BattlePhase.HOME) newBattle(it) else it }
    fun restartBattle() = edit { if (it.phase == BattlePhase.VICTORY) newBattle(it) else it }
    fun digit(value: Int) = edit { enterDigit(it, value) }
    fun backspace() = edit(::eraseDigit)
    fun clear() = edit(::clearInput)
    fun submit() = edit(::submitAnswer)
    fun returnHome() = edit { BattleState(battleId = it.battleId + 1) }
}
