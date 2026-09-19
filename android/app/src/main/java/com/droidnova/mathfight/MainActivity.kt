package com.droidnova.mathfight

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
            val serverUrl by battleViewModel.serverUrl.collectAsStateWithLifecycle()
            val connectionStatus by battleViewModel.connectionStatus.collectAsStateWithLifecycle()
            val connectionMessage by battleViewModel.connectionMessage.collectAsStateWithLifecycle()
            val roomCodeInput by battleViewModel.roomCodeInput.collectAsStateWithLifecycle()
            val room by battleViewModel.room.collectAsStateWithLifecycle()
            val roomError by battleViewModel.roomError.collectAsStateWithLifecycle()
            val onlineMatch by battleViewModel.onlineMatch.collectAsStateWithLifecycle()
            val onlineAnswerLocked by battleViewModel.onlineAnswerLocked.collectAsStateWithLifecycle()
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
                    debugConnection = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                    serverUrl = serverUrl,
                    connectionStatus = connectionStatus,
                    connectionMessage = connectionMessage,
                    onServerUrl = battleViewModel::setServerUrl,
                    onConnect = ::requestConnection,
                    onDisconnect = battleViewModel::disconnect,
                    roomCodeInput = roomCodeInput,
                    room = room,
                    roomError = roomError,
                    onlineMatch = onlineMatch,
                    onlineAnswerLocked = onlineAnswerLocked,
                    onRoomCode = battleViewModel::setRoomCodeInput,
                    onCreateRoom = battleViewModel::createRoom,
                    onJoinRoom = battleViewModel::joinRoom,
                    onLeaveRoom = battleViewModel::leaveRoom,
                    onReady = battleViewModel::readyToggle,
                    onSound = { enabled ->
                        battleViewModel.setSound(enabled)
                        if (!enabled) audio.stop()
                    },
                    onVibration = battleViewModel::setVibration,
                    onStart = battleViewModel::startBattle,
                    onRestart = {
                        audio.stop()
                        if (onlineMatch != null) battleViewModel.leaveOnlineLobby() else battleViewModel.restartBattle()
                    },
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
        battleViewModel.setConnectionForeground(true)
    }

    override fun onPause() {
        battleViewModel.setResumed(false)
        battleViewModel.setConnectionForeground(false)
        audio.stop()
        super.onPause()
    }

    override fun onDestroy() {
        audio.release()
        super.onDestroy()
    }

    private fun requestConnection() {
        if (Build.VERSION.SDK_INT >= 37 &&
            checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK), LOCAL_NETWORK_REQUEST)
        } else {
            battleViewModel.connect()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCAL_NETWORK_REQUEST) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) battleViewModel.connect()
            else battleViewModel.connectionPermissionDenied()
        }
    }

    companion object { private const val LOCAL_NETWORK_REQUEST = 7301 }
}
