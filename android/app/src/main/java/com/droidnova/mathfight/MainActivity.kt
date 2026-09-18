package com.droidnova.mathfight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.droidnova.mathfight.ui.battle.BattleViewModel
import com.droidnova.mathfight.ui.battle.MathFightApp
import com.droidnova.mathfight.ui.theme.MathFightTheme

class MainActivity : ComponentActivity() {
    private val battleViewModel: BattleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by battleViewModel.state.collectAsStateWithLifecycle()
            val isResumed by battleViewModel.isResumed.collectAsStateWithLifecycle()
            MathFightTheme {
                MathFightApp(
                    state = state,
                    isResumed = isResumed,
                    onStart = battleViewModel::startBattle,
                    onRestart = battleViewModel::restartBattle,
                    onDigit = battleViewModel::digit,
                    onBackspace = battleViewModel::backspace,
                    onClear = battleViewModel::clear,
                    onSubmit = battleViewModel::submit,
                    onReturnHome = battleViewModel::returnHome
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
        super.onPause()
    }
}
