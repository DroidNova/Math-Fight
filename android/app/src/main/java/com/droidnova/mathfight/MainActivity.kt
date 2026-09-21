package com.droidnova.mathfight

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.droidnova.mathfight.profile.ProfileStore
import com.droidnova.mathfight.profile.ProfileScreen
import com.droidnova.mathfight.ui.battle.CombatAudio
import com.droidnova.mathfight.ui.battle.CombatSound
import com.droidnova.mathfight.ui.battle.FeedbackKind
import com.droidnova.mathfight.ui.battle.arena.ActivityArenaHost
import com.droidnova.mathfight.ui.battle.arena.ArenaHostProvider
import com.droidnova.mathfight.ui.battle.arena.ArenaCommandBridge
import com.droidnova.mathfight.ui.battle.arena.ArenaPresentationCue
import com.droidnova.mathfight.ui.battle.arena.BattleExitCoordinator
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.droidnova.mathfight.ui.battle.BattleViewModel
import com.droidnova.mathfight.ui.battle.MathFightApp
import com.droidnova.mathfight.ui.theme.MathFightTheme

class MainActivity : FragmentActivity(), AndroidFragmentApplication.Callbacks, ArenaHostProvider {
    override val arenaCommandBridge = ArenaCommandBridge()
    override lateinit var arenaHost: ActivityArenaHost
    private lateinit var battleExitCoordinator: BattleExitCoordinator
    private val battleViewModel: BattleViewModel by viewModels {
        viewModelFactory { initializer { BattleViewModel(ProfileStore(applicationContext)) } }
    }
    private lateinit var audio: CombatAudio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audio = CombatAudio(this)
        enableEdgeToEdge()
        val root = FrameLayout(this)
        val arenaContainer = FragmentContainerView(this).apply {
            id = R.id.battle_arena_fragment_container
        }
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        root.addView(arenaContainer, FrameLayout.LayoutParams(1, 1))
        root.addView(composeView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)
        arenaHost = ActivityArenaHost(this, root, composeView, arenaContainer, arenaCommandBridge)
        battleExitCoordinator = BattleExitCoordinator(arenaHost)
        arenaCommandBridge.setPresentationListener { event ->
            if (lifecycle.currentState != Lifecycle.State.RESUMED ||
                battleViewModel.onlinePaused.value) return@setPresentationListener
            val currentSettings = battleViewModel.settings.value
            if (currentSettings.sound) audio.play(event.cue.toCombatSound())
            if (event.cue == ArenaPresentationCue.IMPACT && currentSettings.vibration) {
                root.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }
        composeView.setContent {
            val state by battleViewModel.state.collectAsStateWithLifecycle()
            val profile by battleViewModel.profile.collectAsStateWithLifecycle()
            val isResumed by battleViewModel.isResumed.collectAsStateWithLifecycle()
            val settings by battleViewModel.settings.collectAsStateWithLifecycle()
            val serverUrl by battleViewModel.serverUrl.collectAsStateWithLifecycle()
            val connectionStatus by battleViewModel.connectionStatus.collectAsStateWithLifecycle()
            val connectionMessage by battleViewModel.connectionMessage.collectAsStateWithLifecycle()
            val roomCodeInput by battleViewModel.roomCodeInput.collectAsStateWithLifecycle()
            val room by battleViewModel.room.collectAsStateWithLifecycle()
            val roomError by battleViewModel.roomError.collectAsStateWithLifecycle()
            val search by battleViewModel.search.collectAsStateWithLifecycle()
            val difficulty by battleViewModel.difficulty.collectAsStateWithLifecycle()
            val onlineMatch by battleViewModel.onlineMatch.collectAsStateWithLifecycle()
            val onlineAnswerLocked by battleViewModel.onlineAnswerLocked.collectAsStateWithLifecycle()
            val onlineSubmissionStatus by battleViewModel.onlineSubmissionStatus.collectAsStateWithLifecycle()
            val onlineQuestionPrompt by battleViewModel.onlineQuestionPrompt.collectAsStateWithLifecycle()
            val onlineQuestionTimer by battleViewModel.onlineQuestionTimer.collectAsStateWithLifecycle()
            val onlinePaused by battleViewModel.onlinePaused.collectAsStateWithLifecycle()
            val leaderboard by battleViewModel.leaderboard.collectAsStateWithLifecycle()
            val leaderboardOpen by battleViewModel.leaderboardOpen.collectAsStateWithLifecycle()
            val rankedResult by battleViewModel.rankedResult.collectAsStateWithLifecycle()
            val xpResult by battleViewModel.xpResult.collectAsStateWithLifecycle()
            val exitInProgress by battleExitCoordinator.exitInProgress.collectAsStateWithLifecycle()
            val view = LocalView.current
            LaunchedEffect(state.battleId, state.phase) {
                if (state.phase != com.droidnova.mathfight.game.BattlePhase.HOME) {
                    battleExitCoordinator.beginBattle(state.battleId)
                }
            }
            LaunchedEffect(onlinePaused) {
                if (onlinePaused) {
                    audio.stop()
                }
            }
            LaunchedEffect(view) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    try {
                        battleViewModel.feedback.collect { event ->
                            if (lifecycle.currentState != Lifecycle.State.RESUMED ||
                                !battleViewModel.consumeFeedback(event)) return@collect
                            if (arenaCommandBridge.canPresentFeedback(arenaHost.session)) return@collect
                            val currentSettings = battleViewModel.settings.value
                            if (currentSettings.sound) audio.playImmediate(event.kind)
                            if (event.kind == FeedbackKind.HIT) {
                                if (currentSettings.vibration) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                }
                            }
                        }
                    } finally {
                        audio.stop()
                    }
                }
            }
            MathFightTheme {
                if (profile.loading || profile.loadFailed || profile.displayName.isBlank() || profile.showing) {
                    ProfileScreen(
                        state = profile,
                        onName = battleViewModel::setProfileName,
                        onSave = battleViewModel::saveProfile,
                        onEditName = battleViewModel::editProfileName,
                        onBack = battleViewModel::closeProfile,
                        onRetry = battleViewModel::loadProfile,
                        onRefresh = battleViewModel::openProfile,
                        onFindMatch = {
                            battleViewModel.closeProfile()
                            battleViewModel.findMatch()
                        }
                    )
                } else {
                MathFightApp(
                    displayName = profile.displayName,
                    profileStats = profile.stats,
                    onProfile = battleViewModel::openProfile,
                    onMatchHistory = battleViewModel::openMatchHistory,
                    leaderboard = leaderboard,
                    leaderboardOpen = leaderboardOpen,
                    onLeaderboard = battleViewModel::openLeaderboard,
                    onCloseLeaderboard = battleViewModel::closeLeaderboard,
                    rankedResult = rankedResult,
                    xpResult = xpResult,
                    consumeXpAnimation = battleViewModel::consumeXpAnimation,
                    search = search,
                    onFindMatch = battleViewModel::findMatch,
                    onCancelMatch = battleViewModel::cancelMatch,
                    difficulty = difficulty,
                    onDifficulty = battleViewModel::setDifficulty,
                    state = state,
                    exitInProgress = exitInProgress,
                    isResumed = isResumed,
                    onlinePaused = onlinePaused,
                    consumeVisualEvent = battleViewModel::consumeArenaVisualEvent,
                    settings = settings,
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
                    onlineSubmissionStatus = onlineSubmissionStatus,
                    onlineQuestionPrompt = onlineQuestionPrompt,
                    onlineQuestionTimer = onlineQuestionTimer,
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
                        if (onlineMatch != null) {
                            battleExitCoordinator.exit {
                                if (rankedResult.ranked) battleViewModel.findNewOpponent()
                                else battleViewModel.leaveOnlineLobby()
                            }
                        } else {
                            battleExitCoordinator.restart(battleViewModel::restartBattle)
                        }
                    },
                    onDigit = battleViewModel::digit,
                    onBackspace = battleViewModel::backspace,
                    onClear = battleViewModel::clear,
                    onSubmit = battleViewModel::submit,
                    onBattleExit = {
                        audio.stop()
                        battleExitCoordinator.exit(battleViewModel::returnHome)
                    },
                    onReturnHome = { audio.stop(); battleViewModel.returnHome() }
                )
                }
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

    override fun onStart() {
        super.onStart()
        battleViewModel.setConnectionForeground(true)
    }

    override fun onStop() {
        if (!isChangingConfigurations) battleViewModel.setConnectionForeground(false)
        super.onStop()
    }

    override fun onDestroy() {
        arenaCommandBridge.setPresentationListener(null)
        audio.release()
        super.onDestroy()
        arenaHost.destroy()
        arenaCommandBridge.dispose()
    }

    override fun exit() = Unit

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

private fun ArenaPresentationCue.toCombatSound(): CombatSound = when (this) {
    ArenaPresentationCue.MELEE_SWING -> CombatSound.MELEE_SWING
    ArenaPresentationCue.ENERGY_CHARGE -> CombatSound.ENERGY_CHARGE
    ArenaPresentationCue.PROJECTILE_LAUNCH -> CombatSound.PROJECTILE_LAUNCH
    ArenaPresentationCue.IMPACT -> CombatSound.IMPACT
    ArenaPresentationCue.HIT_REACTION -> CombatSound.HIT_REACTION
    ArenaPresentationCue.KO_POWER_DOWN -> CombatSound.KO_POWER_DOWN
    ArenaPresentationCue.VICTORY -> CombatSound.VICTORY
}
