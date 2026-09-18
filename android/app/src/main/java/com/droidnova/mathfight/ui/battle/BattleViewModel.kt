package com.droidnova.mathfight.ui.battle

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidnova.mathfight.game.BattlePhase
import com.droidnova.mathfight.game.BattleState
import com.droidnova.mathfight.game.Fighter
import com.droidnova.mathfight.game.PhaseKey
import com.droidnova.mathfight.game.advancePhase
import com.droidnova.mathfight.game.claimQuestion
import com.droidnova.mathfight.game.clearInput
import com.droidnova.mathfight.game.durationMillis
import com.droidnova.mathfight.game.enterDigit
import com.droidnova.mathfight.game.eraseDigit
import com.droidnova.mathfight.game.newBattle
import com.droidnova.mathfight.game.submitAnswer
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class FeedbackSettings(val sound: Boolean = true, val vibration: Boolean = true)
enum class FeedbackKind { HIT, KO }
data class CombatFeedback(val token: PhaseKey, val kind: FeedbackKind)

class BattleViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(BattleState())
    val state = mutableState.asStateFlow()

    private val resumed = MutableStateFlow(false)
    val isResumed = resumed.asStateFlow()

    private val mutableSettings = MutableStateFlow(FeedbackSettings())
    val settings = mutableSettings.asStateFlow()
    private val mutableFeedback = MutableSharedFlow<CombatFeedback>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val feedback = mutableFeedback.asSharedFlow()
    private var emittedToken: PhaseKey? = null
    private var consumedToken: PhaseKey? = null
    private var consumedImpact: PhaseKey? = null

    fun consumeImpact(token: PhaseKey): Boolean {
        if (!resumed.value || state.value.key != token || token.phase != BattlePhase.IMPACT ||
            consumedToken != token || consumedImpact == token) return false
        consumedImpact = token
        return true
    }

    fun setSound(enabled: Boolean) { mutableSettings.value = settings.value.copy(sound = enabled) }
    fun setVibration(enabled: Boolean) { mutableSettings.value = settings.value.copy(vibration = enabled) }

    // Survives Activity recreation; skipped or consumed feedback is never retried.
    fun consumeFeedback(event: CombatFeedback): Boolean {
        if (!resumed.value || state.value.key != event.token || consumedToken == event.token) return false
        consumedToken = event.token
        return true
    }

    private var botJob: Job? = null
    private var botKey: PhaseKey? = null
    private var botDeadlineMs: Long? = null

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
                    if (resumed.value) updateState(advancePhase(mutableState.value, key))
                }
        }
    }

    fun setResumed(value: Boolean) {
        if (value == resumed.value) return
        if (!value) {
            resumed.value = false
            saveAndCancelBotDeadline()
        } else {
            resumed.value = true
            scheduleBotIfNeeded()
        }
    }

    private fun saveAndCancelBotDeadline() {
        val current = mutableState.value
        val deadline = botDeadlineMs
        if (deadline != null && botKey == current.key && current.phase == BattlePhase.ANSWERING) {
            mutableState.value = current.copy(
                botRemainingMs = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            )
        }
        cancelBot()
    }

    private fun scheduleBotIfNeeded() {
        val current = mutableState.value
        if (!resumed.value || current.phase != BattlePhase.ANSWERING) return
        if (botJob?.isActive == true && botKey == current.key) return

        val capturedKey = current.key
        val remaining = current.botRemainingMs
        botKey = capturedKey
        botDeadlineMs = SystemClock.elapsedRealtime() + remaining
        botJob = viewModelScope.launch {
            delay(remaining)
            botJob = null
            botKey = null
            botDeadlineMs = null
            if (resumed.value) {
                updateState(claimQuestion(mutableState.value, capturedKey, Fighter.BOT))
            }
        }
    }

    private fun cancelBot() {
        botJob?.cancel()
        botJob = null
        botKey = null
        botDeadlineMs = null
    }

    private fun updateState(next: BattleState) {
        val previous = mutableState.value
        mutableState.value = next
        if (resumed.value && next.phase != previous.phase && emittedToken != next.key) {
            val kind = when (next.phase) {
                BattlePhase.IMPACT -> FeedbackKind.HIT
                BattlePhase.KO -> FeedbackKind.KO
                else -> null
            }
            if (kind != null) {
                emittedToken = next.key
                mutableFeedback.tryEmit(CombatFeedback(next.key, kind))
            }
        }
        if (next.phase == BattlePhase.ANSWERING) scheduleBotIfNeeded() else cancelBot()
    }

    private fun edit(transform: (BattleState) -> BattleState) {
        if (resumed.value) updateState(transform(mutableState.value))
    }

    fun startBattle() = edit { if (it.phase == BattlePhase.HOME) newBattle(it) else it }
    fun restartBattle() = edit { if (it.phase == BattlePhase.RESULT) newBattle(it) else it }
    fun digit(value: Int) = edit { enterDigit(it, value) }
    fun backspace() = edit(::eraseDigit)
    fun clear() = edit(::clearInput)
    fun submit() = edit(::submitAnswer)
    fun returnHome() = edit { BattleState(battleId = it.battleId + 1) }

    override fun onCleared() {
        cancelBot()
        super.onCleared()
    }
}
