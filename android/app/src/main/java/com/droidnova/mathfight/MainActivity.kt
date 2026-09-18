package com.droidnova.mathfight

import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.droidnova.mathfight.game.PhaseKey
import com.droidnova.mathfight.ui.battle.CombatAudio
import com.droidnova.mathfight.ui.battle.FeedbackKind
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.droidnova.mathfight.ui.battle.BattleViewModel
import com.droidnova.mathfight.ui.battle.MathFightApp
import com.droidnova.mathfight.ui.theme.MathFightTheme

class MainActivity : ComponentActivity() {
    private val battleViewModel: BattleViewModel by viewModels()
    private lateinit var audio: CombatAudio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audio = CombatAudio(this)
        enableEdgeToEdge()
        setContent {
            val state by battleViewModel.state.collectAsStateWithLifecycle()
            val isResumed by battleViewModel.isResumed.collectAsStateWithLifecycle()
            val settings by battleViewModel.settings.collectAsStateWithLifecycle()
            val view = LocalView.current
            var impactToken by remember { mutableStateOf<PhaseKey?>(null) }
            LaunchedEffect(view) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    try {
                        battleViewModel.feedback.collect { event ->
                            if (lifecycle.currentState != Lifecycle.State.RESUMED ||
                                !battleViewModel.consumeFeedback(event)) return@collect
                            val currentSettings = battleViewModel.settings.value
                            if (currentSettings.sound) audio.play(event.kind)
                            if (event.kind == FeedbackKind.HIT) {
                                impactToken = event.token
                                if (currentSettings.vibration) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                }
                            }
                        }
                    } finally {
                        impactToken = null
                        audio.stop()
                    }
                }
            }
            MathFightTheme {
                MathFightApp(
                    state = state,
                    isResumed = isResumed,
                    impactToken = impactToken,
                    consumeImpact = battleViewModel::consumeImpact,
                    settings = settings,
                    onSound = { enabled ->
                        battleViewModel.setSound(enabled)
                        if (!enabled) audio.stop()
                    },
                    onVibration = battleViewModel::setVibration,
                    onStart = battleViewModel::startBattle,
                    onRestart = { audio.stop(); battleViewModel.restartBattle() },
                    onDigit = battleViewModel::digit,
                    onBackspace = battleViewModel::backspace,
                    onClear = battleViewModel::clear,
                    onSubmit = battleViewModel::submit,
                    onReturnHome = { audio.stop(); battleViewModel.returnHome() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        battleViewModel.setResumed(true)
    }

    override fun onPause() {
        battleViewModel.setResumed(false)
        audio.stop()
        super.onPause()
    }

    override fun onDestroy() {
        audio.release()
        super.onDestroy()
    }
}
