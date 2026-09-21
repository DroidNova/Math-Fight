package com.droidnova.mathfight.ui.battle

import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidnova.mathfight.profile.LocalProfile
import com.droidnova.mathfight.profile.ProfileStore
import com.droidnova.mathfight.profile.ProfileUiState
import com.droidnova.mathfight.profile.XpResult
import com.droidnova.mathfight.profile.parseProgression
import com.droidnova.mathfight.profile.parseXpResult
import com.droidnova.mathfight.profile.NAME_VALIDATION_MESSAGE
import com.droidnova.mathfight.profile.normalizedPlayerName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import java.io.IOException
import com.droidnova.mathfight.game.BattlePhase
import com.droidnova.mathfight.game.BattleState
import com.droidnova.mathfight.game.COMBAT_ANIMATION_DURATION_MS
import com.droidnova.mathfight.game.Fighter
import com.droidnova.mathfight.game.PhaseKey
import com.droidnova.mathfight.game.Question
import com.droidnova.mathfight.game.Operation
import com.droidnova.mathfight.game.Difficulty
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
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.util.UUID

data class FeedbackSettings(val sound: Boolean = true, val vibration: Boolean = true)
enum class FeedbackKind { HIT, KO }
data class CombatFeedback(val token: PhaseKey, val kind: FeedbackKind)
data class RoomInfo(
    val code: String,
    val role: String,
    val playerCount: Int,
    val hostReady: Boolean = false,
    val guestReady: Boolean = false,
    val matchActive: Boolean = false,
    val hostName: String = "",
    val guestName: String = "",
    val difficulty: Difficulty = Difficulty.STANDARD,
    val ranked: Boolean = false,
    val hostRating: Int = 1000,
    val guestRating: Int? = null,
    val hostTier: String = "Silver",
    val guestTier: String? = null
)
data class OnlineMatchInfo(val matchId: String, val questionId: Long, val role: String,
                           val hostName: String, val guestName: String, val difficulty: Difficulty,
                           val ranked: Boolean = false, val hostRating: Int = 1000, val guestRating: Int = 1000,
                           val hostTier: String = "Silver", val guestTier: String = "Silver") {
    val localName: String get() = if (role.equals("host", true)) hostName else guestName
    val opponentName: String get() = if (role.equals("host", true)) guestName else hostName
}
data class MatchSearchState(val active: Boolean = false, val searchId: String = "", val status: String = "idle", val opponentName: String = "", val difficulty: Difficulty = Difficulty.STANDARD, val error: String = "")
data class LeaderboardRow(val position: Int, val displayName: String, val rating: Int, val tier: String, val wins: Int, val losses: Int, val current: Boolean)
data class LeaderboardState(val loading: Boolean = false, val connected: Boolean = false, val error: String = "", val rows: List<LeaderboardRow> = emptyList(), val currentPosition: Int = 0)
data class RankedResult(val ranked: Boolean = false, val available: Boolean = false, val before: Int = 1000, val delta: Int = 0, val after: Int = 1000, val tier: String = "Silver")
data class OnlineQuestionTimerState(
    val visible: Boolean = false,
    val seconds: Int = 0,
    val expired: Boolean = false
) {
    val warning: Boolean get() = visible && seconds <= 3
}
private data class PendingAnswer(val requestId: String, val matchId: String, val questionId: Long)
private data class ClockSample(val roundTripMs: Long, val serverEpochAtMidpointMs: Long, val localMidpointMs: Long)
private data class ServerClockMapping(val serverEpochAtMidpointMs: Long, val localMidpointMs: Long) {
    fun localElapsedFor(serverEpochMs: Long): Long = localMidpointMs + (serverEpochMs - serverEpochAtMidpointMs)
}
private data class ScheduledOnlineQuestion(
    val matchId: String,
    val questionId: Long,
    val revision: Long,
    val opensAt: Long,
    val closesAt: Long,
    val reason: String,
    val question: Question,
    val hostHp: Int,
    val guestHp: Int
)
private data class ActiveQuestionWindow(
    val matchId: String,
    val questionId: Long,
    val opensAt: Long,
    val closesAt: Long
)
private const val DEFAULT_SERVER_URL = "http://192.168.1.5:3000"
private class ProfileSyncFailure(message: String) : Exception(message)
private enum class RetryScope {
    CONNECTION, PROFILE, ACCOUNT_READ, ROOM_ENTRY, ROOM_CONTROL, MATCHMAKING, ANSWER, MATCH_STATE, TIME_SYNC
}

class BattleViewModel(private val profileStore: ProfileStore) : ViewModel() {
    private var localProfile: LocalProfile? = null
    private val mutableProfile = MutableStateFlow(ProfileUiState())
    val profile = mutableProfile.asStateFlow()
    private var profileLoadJob: Job? = null
    private var profileSaveJob: Job? = null
    private val mutableLeaderboard = MutableStateFlow(LeaderboardState())
    val leaderboard = mutableLeaderboard.asStateFlow()
    private val mutableLeaderboardOpen = MutableStateFlow(false)
    val leaderboardOpen = mutableLeaderboardOpen.asStateFlow()
    private var leaderboardRequest = 0L
    private var leaderboardTimeout: Job? = null
    private val mutableRankedResult = MutableStateFlow(RankedResult())
    val rankedResult = mutableRankedResult.asStateFlow()
    private val mutableXpResult = MutableStateFlow<XpResult?>(null)
    val xpResult = mutableXpResult.asStateFlow()
    private var consumedXpMatchId: String? = null
    private var profileStatsRequest = 0L
    private var profileStatsTimeout: Job? = null
    private val mutableDifficulty = MutableStateFlow(Difficulty.STANDARD)
    val difficulty = mutableDifficulty.asStateFlow()
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
    private var arenaEncounter: String? = null
    private var consumedArenaAttack: PhaseKey? = null
    private var consumedArenaHit: PhaseKey? = null
    private var consumedArenaKo: PhaseKey? = null

    enum class ConnectionStatus { IDLE, CONNECTING, CONNECTED, DISCONNECTED, ERROR }
    private val mutableServerUrl = MutableStateFlow(DEFAULT_SERVER_URL)
    val serverUrl = mutableServerUrl.asStateFlow()
    private val mutableConnectionStatus = MutableStateFlow(ConnectionStatus.IDLE)
    val connectionStatus = mutableConnectionStatus.asStateFlow()
    private val mutableConnectionMessage = MutableStateFlow("")
    val connectionMessage = mutableConnectionMessage.asStateFlow()
    private var socket: Socket? = null
    // Temporary, private credentials survive Activity recreation, but never process death.
    private var playerId: String? = null
    private var resumeToken: String? = null
    private var sessionReady = false
    private var sessionOperation = 0L
    private var connectionGeneration = 0L
    private var wantsConnection = false
    private var connectionForeground = false
    private var reconnectJob: Job? = null
    private var acknowledgementJob: Job? = null
    private var reconnectAttempt = 0
    private var disconnectMessageAfterDispose: String? = null
    private val retryUntilElapsedMs = mutableMapOf<RetryScope, Long>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableRoomCodeInput = MutableStateFlow("")
    val roomCodeInput = mutableRoomCodeInput.asStateFlow()
    private val mutableRoom = MutableStateFlow<RoomInfo?>(null)
    val room = mutableRoom.asStateFlow()
    private val mutableRoomError = MutableStateFlow("")
    val roomError = mutableRoomError.asStateFlow()
    private val mutableSearch = MutableStateFlow(MatchSearchState())
    val search = mutableSearch.asStateFlow()
    private var completedSearchId: String? = null
    private var searchOperationGeneration = 0L
    private var cancelSearchWhenIdentified = false
    private var roomOperationGeneration = 0L
    private var roomRequestPending = false
    private var roomRequestTimeout: Job? = null
    private val mutableOnlineMatch = MutableStateFlow<OnlineMatchInfo?>(null)
    val onlineMatch = mutableOnlineMatch.asStateFlow()
    private val mutableOnlineAnswerLocked = MutableStateFlow(false)
    val onlineAnswerLocked = mutableOnlineAnswerLocked.asStateFlow()
    private var onlineResolutionJob: Job? = null
    private var onlineResultJob: Job? = null
    private var onlineResult: String? = null
    private var onlineRevision = 0L
    private var pendingAnswer: PendingAnswer? = null
    private var answerTimeoutJob: Job? = null
    private val mutableOnlineSubmissionStatus = MutableStateFlow("")
    val onlineSubmissionStatus = mutableOnlineSubmissionStatus.asStateFlow()
    private val mutableOnlineQuestionPrompt = MutableStateFlow("")
    val onlineQuestionPrompt = mutableOnlineQuestionPrompt.asStateFlow()
    private val mutableOnlineQuestionTimer = MutableStateFlow(OnlineQuestionTimerState())
    val onlineQuestionTimer = mutableOnlineQuestionTimer.asStateFlow()
    private val mutableOnlinePaused = MutableStateFlow(false)
    val onlinePaused = mutableOnlinePaused.asStateFlow()
    private var pauseCountdownJob: Job? = null
    private var pauseServerDeadline: Long? = null
    private var questionCountdownJob: Job? = null
    private var answerCountdownJob: Job? = null
    private var scheduledOnlineQuestion: ScheduledOnlineQuestion? = null
    private var activeQuestionWindow: ActiveQuestionWindow? = null
    private var serverClock: ServerClockMapping? = null
    private var clockSyncJob: Job? = null
    private var lastAttackQuestion = 0L
    private var returnedMatchId: String? = null

    fun loadProfile() {
        if (profileLoadJob?.isActive == true) return
        mutableProfile.value = profile.value.copy(loading = true, loadFailed = false, error = "")
        profileLoadJob = viewModelScope.launch {
            try {
                val loaded = profileStore.load()
                mutableDifficulty.value = profileStore.loadDifficulty()
                val preferences = profileStore.loadPreferences()
                mutableSettings.value = FeedbackSettings(preferences.sound, preferences.vibration)
                mutableServerUrl.value = preferences.serverUrl.ifBlank { DEFAULT_SERVER_URL }
                localProfile = loaded
                mutableProfile.value = ProfileUiState(loading = false, displayName = loaded.displayName, nameInput = loaded.displayName)
            } catch (_: IOException) {
                // Never replace a stored identity with a default when storage cannot be read.
                mutableProfile.value = profile.value.copy(loading = false, loadFailed = true, error = "Could not load your profile. Try again.")
            }
        }
    }

    fun openProfile() {
        if (state.value.phase != BattlePhase.HOME || room.value?.matchActive == true || profile.value.loading) return
        mutableProfile.value = profile.value.copy(showing = true, historyOnly = false, editing = false,
            nameInput = profile.value.displayName, error = "", statsLoading = true)
        fetchProfileStats()
    }

    fun openMatchHistory() {
        if (state.value.phase != BattlePhase.HOME || room.value?.matchActive == true || profile.value.loading) return
        mutableProfile.value = profile.value.copy(showing = true, historyOnly = true, editing = false,
            nameInput = profile.value.displayName, error = "", statsLoading = true)
        fetchProfileStats()
    }

    fun editProfileName() {
        if (!profile.value.saving) mutableProfile.value = profile.value.copy(editing = true, nameInput = profile.value.displayName, error = "")
    }

    fun openLeaderboard() {
        if (state.value.phase != BattlePhase.HOME || room.value?.matchActive == true) return
        mutableLeaderboardOpen.value = true
        fetchLeaderboard()
    }

    fun closeLeaderboard() { mutableLeaderboardOpen.value = false }

    private fun fetchLeaderboard() {
        val request = ++leaderboardRequest
        val generation = connectionGeneration
        val operation = sessionOperation
        leaderboardTimeout?.cancel()
        if (retryBlocked(RetryScope.ACCOUNT_READ) { mutableLeaderboard.value = LeaderboardState(connected = true, error = it) }) return
        val current = socket
        if (current?.connected() != true || !sessionReady) { mutableLeaderboard.value = LeaderboardState(connected = false, error = "Connect to the server to view the leaderboard"); return }
        mutableLeaderboard.value = LeaderboardState(loading = true, connected = true)
        leaderboardTimeout = viewModelScope.launch {
            delay(5_000)
            if (request == leaderboardRequest) {
                leaderboardRequest++
                mutableLeaderboard.value = LeaderboardState(connected = true, error = "Leaderboard unavailable")
            }
        }
        current.emit("leaderboard:get", JSONObject(), io.socket.client.Ack { args ->
            mainHandler.post {
                if (request != leaderboardRequest || generation != connectionGeneration || operation != sessionOperation ||
                    socket !== current || !sessionReady) return@post
                leaderboardTimeout?.cancel()
                val response = args.firstOrNull() as? JSONObject
                if (response?.optBoolean("ok") != true) {
                    mutableLeaderboard.value = LeaderboardState(connected = true,
                        error = response?.let { serverErrorMessage(it, "Leaderboard unavailable", RetryScope.ACCOUNT_READ) } ?: "Leaderboard unavailable")
                    return@post
                }
                val rows = mutableListOf<LeaderboardRow>(); val values = response.optJSONArray("players")
                for (index in 0 until (values?.length() ?: 0)) { val row = values?.optJSONObject(index) ?: continue; rows += LeaderboardRow(row.optInt("position"), row.optString("displayName"), row.optInt("rating", 1000), row.optString("tier", "Silver"), row.optInt("wins"), row.optInt("losses"), row.optBoolean("current")) }
                mutableLeaderboard.value = LeaderboardState(connected = true, rows = rows, currentPosition = response.optInt("currentPosition"))
            }
        })
    }

    fun closeProfile() {
        if (profile.value.saving) return
        mutableProfile.value = if (profile.value.editing) {
            profile.value.copy(editing = false, nameInput = profile.value.displayName, error = "")
        } else profile.value.copy(showing = false, historyOnly = false, error = "")
    }

    fun setProfileName(value: String) {
        if (!profile.value.saving) mutableProfile.value = profile.value.copy(nameInput = value, error = "")
    }

    fun setDifficulty(value: Difficulty) {
        val currentRoom = room.value
        if (search.value.active || currentRoom?.matchActive == true) return
        if (currentRoom != null) {
            if (currentRoom.role.equals("Host", true)) setRoomDifficulty(value)
            return
        }
        mutableDifficulty.value = value
        viewModelScope.launch { profileStore.saveDifficulty(value) }
    }

    fun saveProfile() {
        val previous = localProfile ?: return
        if (profile.value.saving || state.value.phase != BattlePhase.HOME || room.value?.matchActive == true) return
        val name = normalizedPlayerName(profile.value.nameInput)
        if (name == null) {
            mutableProfile.value = profile.value.copy(error = NAME_VALIDATION_MESSAGE)
            return
        }
        if (socket != null && !sessionReady) {
            mutableProfile.value = profile.value.copy(error = "Wait for the connection, then save again.")
            return
        }
        mutableProfile.value = profile.value.copy(saving = true, error = "")
        profileSaveJob = viewModelScope.launch {
            try {
                val current = socket
                val canonical = if (current?.connected() == true && sessionReady) {
                    synchronizeProfile(current, LocalProfile(previous.id, name)).optString("displayName")
                } else name
                val saved = profileStore.saveName(canonical)
                localProfile = saved
                mutableProfile.value = profile.value.copy(displayName = saved.displayName, nameInput = saved.displayName, editing = false)
            } catch (_: TimeoutCancellationException) {
                mutableProfile.value = profile.value.copy(error = "Profile sync timed out. Try again.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ProfileSyncFailure) {
                mutableProfile.value = profile.value.copy(error = error.message.orEmpty())
            } catch (_: IOException) {
                mutableProfile.value = profile.value.copy(error = "Could not save your profile. Try again.")
            } finally {
                mutableProfile.value = profile.value.copy(saving = false)
            }
        }
    }

    private fun fetchProfileStats() {
        val request = ++profileStatsRequest
        val generation = connectionGeneration
        val operation = sessionOperation
        profileStatsTimeout?.cancel()
        val current = socket
        if (retryBlocked(RetryScope.ACCOUNT_READ) { mutableProfile.value = profile.value.copy(statsLoading = false) }) return
        if (current?.connected() != true || !sessionReady) {
            mutableProfile.value = profile.value.copy(statsLoading = false)
            return
        }
        mutableProfile.value = profile.value.copy(statsLoading = true)
        profileStatsTimeout = viewModelScope.launch {
            delay(5_000)
            if (request == profileStatsRequest) {
                profileStatsRequest++
                mutableProfile.value = profile.value.copy(statsLoading = false)
            }
        }
        current.emit("profile:stats", JSONObject(), io.socket.client.Ack { args ->
            mainHandler.post {
                if (request != profileStatsRequest || generation != connectionGeneration || operation != sessionOperation || socket !== current || !sessionReady) return@post
                profileStatsTimeout?.cancel()
                val response = args.firstOrNull() as? JSONObject
                if (response?.optBoolean("ok") != true) {
                    if (response != null) serverErrorMessage(response, "Statistics unavailable", RetryScope.ACCOUNT_READ)
                    mutableProfile.value = profile.value.copy(statsLoading = false)
                    return@post
                }
                val recent = mutableListOf<com.droidnova.mathfight.profile.ProfileMatchStat>()
                val rows = response.optJSONArray("matches")
                for (index in 0 until (rows?.length() ?: 0)) {
                    val row = rows?.optJSONObject(index) ?: continue
                    val matchId = row.optString("matchId")
                    recent += com.droidnova.mathfight.profile.ProfileMatchStat(
                        matchId = matchId,
                        localName = row.optString("localName").ifBlank { profile.value.displayName },
                        result = row.optString("result"),
                        opponentName = row.optString("opponentName"),
                        difficulty = row.optString("difficulty"),
                        finishReason = row.optString("finishReason"),
                        matchType = row.optString("matchType", "UNRANKED"),
                        ratingChange = if (row.isNull("ratingChange")) null else row.optInt("ratingChange"),
                        progression = row.optJSONObject("progression")?.let { parseXpResult(matchId, it) },
                        completedAt = row.optString("completedAt")
                    )
                }
                mutableProfile.value = profile.value.copy(statsLoading = false, stats = com.droidnova.mathfight.profile.ProfileStats(response.optInt("matchesPlayed"), response.optInt("wins"), response.optInt("losses"), response.optDouble("winRate"), recent, response.optInt("rating", 1000), response.optString("tier", "Silver"), response.optInt("leaderboardPosition"), parseProgression(response)))
            }
        })
    }

    private suspend fun synchronizeProfile(current: Socket, value: LocalProfile): JSONObject {
        val generation = connectionGeneration
        val operation = sessionOperation
        if (socket !== current || !current.connected()) throw ProfileSyncFailure("Connection changed. Try again.")
        if (retryDelayRemaining(RetryScope.PROFILE) > 0L) throw ProfileSyncFailure("Too many attempts. Try again shortly.")
        val accountToken = profileStore.loadAccountToken()
        val response = withTimeout(3_000) {
            suspendCancellableCoroutine<JSONObject> { continuation ->
                val payload = JSONObject().put("profileId", value.id).put("displayName", value.displayName)
                if (accountToken != null) payload.put("accountToken", accountToken)
                current.emit("profile:sync", payload,
                    io.socket.client.Ack { args ->
                        if (continuation.isActive) continuation.resume(args.firstOrNull() as? JSONObject ?: JSONObject())
                    })
            }
        }
        if (socket !== current || generation != connectionGeneration || operation != sessionOperation) throw ProfileSyncFailure("Connection changed. Try again.")
        if (!response.optBoolean("ok")) throw ProfileSyncFailure(serverErrorMessage(response, "Profile was not accepted", RetryScope.PROFILE))
        if (normalizedPlayerName(response.optString("displayName")) == null) throw ProfileSyncFailure("Invalid profile acknowledgement")
        response.optString("accountToken").takeIf { it.isNotBlank() }?.let { profileStore.saveAccountToken(it) }
        return response
    }

    private fun startTimeSynchronization(current: Socket, generation: Long, operation: Long) {
        clockSyncJob?.cancel()
        serverClock = null
        refreshQuestionCountdown()
        refreshPauseCountdown()
        clockSyncJob = viewModelScope.launch {
            var retryDelay = 1_000L
            while (socket === current && generation == connectionGeneration && operation == sessionOperation && sessionReady) {
                val samples = mutableListOf<ClockSample>()
                for (sample in 0 until 3) {
                    if (retryDelayRemaining(RetryScope.TIME_SYNC) > 0L) break
                    takeTimeSample(current, generation, operation)?.let(samples::add)
                }
                val best = samples.minByOrNull { it.roundTripMs }
                if (best != null) {
                    serverClock = ServerClockMapping(best.serverEpochAtMidpointMs, best.localMidpointMs)
                    refreshQuestionCountdown()
                    refreshPauseCountdown()
                    return@launch
                }
                delay(maxOf(retryDelay, retryDelayRemaining(RetryScope.TIME_SYNC)))
                retryDelay = (retryDelay * 2L).coerceAtMost(30_000L)
            }
        }
    }

    private suspend fun takeTimeSample(current: Socket, generation: Long, operation: Long): ClockSample? {
        val started = SystemClock.elapsedRealtime()
        val response = try {
            withTimeout(3_000) {
                suspendCancellableCoroutine<Pair<JSONObject, Long>> { continuation ->
                    current.emit("time:sync", JSONObject(), io.socket.client.Ack { args ->
                        val received = SystemClock.elapsedRealtime()
                        val value = args.firstOrNull() as? JSONObject
                        if (value != null && continuation.isActive) continuation.resume(value to received)
                    })
                }
            }
        } catch (_: TimeoutCancellationException) {
            return null
        }
        if (socket !== current || generation != connectionGeneration || operation != sessionOperation || !sessionReady) return null
        if (!response.first.optBoolean("ok")) {
            serverErrorMessage(response.first, "Time synchronization unavailable", RetryScope.TIME_SYNC)
            return null
        }
        val serverTime = response.first.optLong("serverTime", -1L)
        if (serverTime <= 0L || response.second < started) return null
        val roundTrip = response.second - started
        val midpoint = started + roundTrip / 2L
        return ClockSample(roundTrip, serverTime - roundTrip / 2L, midpoint)
    }

    /**
     * One-shot presentation gate that survives Activity recreation with this ViewModel. The arena
     * cannot replay a phase just because Compose or its libGDX fragment was recreated.
     */
    fun consumeArenaVisualEvent(token: PhaseKey): Boolean {
        if (!resumed.value || onlinePaused.value || state.value.key != token) return false
        val encounter = onlineMatch.value?.matchId ?: "offline:${token.battleId}"
        if (arenaEncounter != encounter) {
            arenaEncounter = encounter
            consumedArenaAttack = null
            consumedArenaHit = null
            consumedArenaKo = null
        }
        return when (token.phase) {
            BattlePhase.WINDUP -> if (consumedArenaAttack == token) false else {
                consumedArenaAttack = token
                true
            }
            BattlePhase.IMPACT -> if (consumedArenaHit == token) false else {
                consumedArenaHit = token
                true
            }
            BattlePhase.KO -> if (consumedArenaKo == token) false else {
                consumedArenaKo = token
                true
            }
            else -> false
        }
    }

    fun setSound(enabled: Boolean) {
        mutableSettings.value = settings.value.copy(sound = enabled)
        viewModelScope.launch { runCatching { profileStore.saveSound(enabled) } }
    }
    fun setVibration(enabled: Boolean) {
        mutableSettings.value = settings.value.copy(vibration = enabled)
        viewModelScope.launch { runCatching { profileStore.saveVibration(enabled) } }
    }

    fun readyToggle() {
        if (retryBlocked(RetryScope.ROOM_CONTROL, ::setRoomError)) return
        val current = socket ?: return setRoomError("Connect to the server first")
        if (!sessionReady || !current.connected()) return
        val currentRoom = room.value ?: return setRoomError("Join a room first")
        val operation = roomOperationGeneration
        val ready = if (currentRoom.role.equals("Host", true)) !currentRoom.hostReady else !currentRoom.guestReady
        current.emit("room:ready", JSONObject().put("ready", ready), io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || !sessionReady || operation != roomOperationGeneration || args.isEmpty() || args[0] !is JSONObject) return@post
                val response = args[0] as JSONObject
                if (!response.optBoolean("ok", false)) setRoomError(serverErrorMessage(response, "Ready request failed", RetryScope.ROOM_CONTROL))
            }
        })
    }

    fun setRoomDifficulty(value: Difficulty) {
        if (retryBlocked(RetryScope.ROOM_CONTROL, ::setRoomError)) return
        val current = socket ?: return
        val currentRoom = room.value ?: return
        if (currentRoom.ranked || !currentRoom.role.equals("Host", true) || currentRoom.matchActive || !sessionReady) return
        current.emit("room:difficulty", JSONObject().put("difficulty", value.name), io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || args.firstOrNull() !is JSONObject) return@post
                val response = args[0] as JSONObject
                if (!response.optBoolean("ok")) setRoomError(serverErrorMessage(response, "Difficulty change failed", RetryScope.ROOM_CONTROL))
            }
        })
    }

    fun setServerUrl(value: String) {
        if (value == mutableServerUrl.value) return
        mutableServerUrl.value = value
        viewModelScope.launch { runCatching { profileStore.saveServerUrl(value) } }
        retryUntilElapsedMs.clear()
        endParticipation()
    }

    fun connect() {
        if (profile.value.loading || profile.value.displayName.isBlank() || profile.value.saving) return
        if (socket != null || connectionStatus.value == ConnectionStatus.CONNECTING) return
        val url = serverUrl.value.trim().trimEnd('/')
        if (url.isEmpty()) {
            mutableConnectionStatus.value = ConnectionStatus.ERROR
            mutableConnectionMessage.value = "Enter a server URL"
            return
        }
        wantsConnection = true
        reconnectAttempt = 0
        reconnectJob?.cancel()
        disposeSocket(ConnectionStatus.IDLE)
        openSocket(url)
    }

    fun disconnect() {
        endParticipation()
    }

    fun setRoomCodeInput(value: String) {
        mutableRoomCodeInput.value = value.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(6)
        mutableRoomError.value = ""
    }

    fun findMatch() {
        if (retryBlocked(RetryScope.MATCHMAKING, ::setSearchError)) return
        val current = socket
        if (!sessionReady || current?.connected() != true) return setSearchError("Connect to the server first")
        if (room.value != null || onlineMatch.value != null) return setSearchError("Leave the current room first")
        if (search.value.active) return
        val operation = ++searchOperationGeneration
        completedSearchId = null
        cancelSearchWhenIdentified = false
        mutableSearch.value = MatchSearchState(active = true, status = "waiting", difficulty = difficulty.value)
        current.emit("matchmaking:join", JSONObject().put("difficulty", difficulty.value.name), io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || operation != searchOperationGeneration || room.value != null ||
                    !search.value.active || args.firstOrNull() !is JSONObject) return@post
                val response = args[0] as JSONObject
                if (!response.optBoolean("ok")) {
                    setSearchError(serverErrorMessage(response, "Matchmaking failed", RetryScope.MATCHMAKING))
                } else {
                    val id = response.optString("searchId")
                    if (id.isNotBlank()) {
                        if (search.value.status == "cancelling" && search.value.searchId == id) return@post
                        mutableSearch.value = search.value.copy(active = true, searchId = id, status = "waiting",
                            difficulty = parseDifficulty(response.optString("difficulty")), error = "")
                        if (cancelSearchWhenIdentified) cancelMatch()
                    }
                }
            }
        })
    }

    fun cancelMatch() {
        if (room.value != null) return clearSearch()
        if (retryBlocked(RetryScope.MATCHMAKING, ::setSearchError)) return
        val current = socket ?: return clearSearch()
        val id = search.value.searchId
        if (id.isBlank()) {
            cancelSearchWhenIdentified = true
            mutableSearch.value = search.value.copy(status = "cancelling")
            return
        }
        if (search.value.status == "cancelling" && !cancelSearchWhenIdentified) return
        cancelSearchWhenIdentified = false
        mutableSearch.value = search.value.copy(status = "cancelling")
        val operation = searchOperationGeneration
        current.emit("matchmaking:cancel", JSONObject().put("searchId", id), io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || operation != searchOperationGeneration || room.value != null || search.value.searchId != id) return@post
                val response = args.firstOrNull() as? JSONObject
                if (response?.optBoolean("ok") == true && response.optString("status") == "matched") {
                    mutableSearch.value = search.value.copy(active = true, status = "matched")
                } else if (response?.optBoolean("ok") == true) clearSearch()
                else mutableSearch.value = search.value.copy(status = "waiting",
                    error = response?.let { serverErrorMessage(it, "Could not cancel search", RetryScope.MATCHMAKING) } ?: "Could not cancel search")
            }
        })
    }

    private fun setSearchError(message: String) {
        searchOperationGeneration++
        cancelSearchWhenIdentified = false
        mutableSearch.value = MatchSearchState(error = message)
    }

    private fun parseDifficulty(value: String): Difficulty = runCatching { Difficulty.valueOf(value) }.getOrDefault(Difficulty.STANDARD)

    private fun clearSearch() {
        searchOperationGeneration++
        cancelSearchWhenIdentified = false
        mutableSearch.value = MatchSearchState()
    }

    private fun handleSearchStatus(value: JSONObject) {
        val id = value.optString("searchId")
        if (id == completedSearchId || room.value != null) return
        if (!search.value.active) return
        if (id.isBlank() || id != search.value.searchId && search.value.searchId.isNotBlank()) return
        when (value.optString("status")) {
            "waiting" -> {
                mutableSearch.value = search.value.copy(active = true, searchId = id, status = "waiting",
                    difficulty = parseDifficulty(value.optString("difficulty")))
                if (cancelSearchWhenIdentified) cancelMatch()
            }
            "cancelled", "idle" -> clearSearch()
            "expired" -> setSearchError("Search expired. Try again.")
        }
    }

    private fun handleMatched(value: JSONObject) {
        val id = value.optString("searchId")
        if (!search.value.active || (search.value.searchId.isNotBlank() && search.value.searchId != id)) return
        val roomValue = value.optJSONObject("room") ?: return
        completedSearchId = id
        updateRoom(roomValue)
    }

    fun createRoom() {
        if (retryBlocked(RetryScope.ROOM_ENTRY, ::setRoomError)) return
        if (search.value.active) return setRoomError("Cancel the current search first")
        if (roomRequestPending) return
        val current = socket
        if (!sessionReady || current?.connected() != true) return setRoomError("Connect to the server first")
        val operation = ++roomOperationGeneration
        beginRoomRequest(operation)
        current.emit("room:create", JSONObject().put("difficulty", difficulty.value.name), io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || operation != roomOperationGeneration || args.isEmpty() || args[0] !is JSONObject) return@post
                handleRoomAck(args[0] as JSONObject, operation)
            }
        })
    }

    fun joinRoom() {
        if (retryBlocked(RetryScope.ROOM_ENTRY, ::setRoomError)) return
        if (search.value.active) return setRoomError("Cancel the current search first")
        if (roomRequestPending) return
        val code = roomCodeInput.value
        if (code.length != 6) return setRoomError("Enter a 6-character room code")
        val current = socket
        if (!sessionReady || current?.connected() != true) return setRoomError("Connect to the server first")
        val operation = ++roomOperationGeneration
        beginRoomRequest(operation)
        current.emit("room:join", JSONObject().put("code", code), io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || operation != roomOperationGeneration || args.isEmpty() || args[0] !is JSONObject) return@post
                handleRoomAck(args[0] as JSONObject, operation)
            }
        })
    }

    fun submitOnlineAnswer() {
        val match = onlineMatch.value ?: return
        if (retryBlocked(RetryScope.ANSWER) { mutableOnlineSubmissionStatus.value = it }) return
        if (mutableState.value.phase != BattlePhase.ANSWERING || mutableOnlineAnswerLocked.value ||
            mutableState.value.input.isEmpty() || !isCurrentQuestionOpen()) return
        val current = socket
        if (!sessionReady || !connectionForeground || current?.connected() != true) {
            mutableOnlineSubmissionStatus.value = "Reconnecting\u2026"
            return
        }
        val answer = mutableState.value.input
        val pending = PendingAnswer(UUID.randomUUID().toString(), match.matchId, match.questionId)
        pendingAnswer = pending
        answerTimeoutJob?.cancel()
        mutableOnlineSubmissionStatus.value = "Checking…"
        mutableOnlineAnswerLocked.value = true
        mutableState.value = mutableState.value.copy(input = "", wrongAnswer = false)
        current.emit("match:answer", JSONObject()
            .put("matchId", match.matchId)
            .put("questionId", match.questionId)
            .put("requestId", pending.requestId)
            .put("answer", answer.toInt()), io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || !sessionReady || pendingAnswer != pending || mutableOnlineMatch.value != match || onlinePaused.value) return@post
                val response = args.firstOrNull() as? JSONObject ?: return@post
                val securityCode = response.optString("code")
                val securityFailure = securityCode == "RATE_LIMITED" || securityCode == "AUTH_REQUIRED" ||
                    securityCode == "SESSION_EXPIRED" || securityCode == "INVALID_PAYLOAD" ||
                    securityCode == "SERVICE_UNAVAILABLE" || securityCode == "SERVER_SHUTDOWN"
                if (!securityFailure && (response.optString("requestId") != pending.requestId ||
                    response.optString("matchId") != pending.matchId || response.optLong("questionId") != pending.questionId)) return@post
                pendingAnswer = null
                answerTimeoutJob?.cancel()
                mutableOnlineSubmissionStatus.value = ""
                if (securityFailure) {
                    mutableOnlineSubmissionStatus.value = serverErrorMessage(response, "Invalid request.", RetryScope.ANSWER)
                    mutableOnlineAnswerLocked.value = true
                    if (securityCode == "RATE_LIMITED") {
                        answerTimeoutJob = viewModelScope.launch {
                            delay(retryDelayRemaining(RetryScope.ANSWER))
                            if (mutableOnlineMatch.value == match && isCurrentQuestionOpen() && !onlinePaused.value) {
                                mutableOnlineSubmissionStatus.value = ""
                                mutableOnlineAnswerLocked.value = false
                            }
                        }
                    } else if (securityCode == "INVALID_PAYLOAD" && isCurrentQuestionOpen()) {
                        mutableOnlineAnswerLocked.value = false
                    }
                    return@post
                }
                if (response.optBoolean("ok", false) && response.optString("result") == "correct") {
                    // Remain locked until the authoritative attack or question update arrives.
                    mutableOnlineAnswerLocked.value = true
                } else if (response.optString("result") == "incorrect") {
                    mutableState.value = mutableState.value.copy(input = "", wrongAnswer = true)
                    if (isCurrentQuestionOpen()) {
                        mutableOnlineAnswerLocked.value = false
                    } else {
                        markQuestionExpiredLocally()
                    }
                } else if (response.optString("result") == "not_open" || response.optString("code") == "NOT_OPEN") {
                    // Keep the authoritative question visible and wait for the server's opening transition.
                    mutableOnlineAnswerLocked.value = true
                    mutableOnlineSubmissionStatus.value = "Question not open yet"
                    mutableState.value = mutableState.value.copy(input = answer, wrongAnswer = false)
                    requestOnlineSnapshot()
                } else if (response.optString("result") == "question_expired" || response.optString("code") == "QUESTION_EXPIRED") {
                    markQuestionExpiredLocally()
                    requestOnlineSnapshot()
                } else if (response.optString("result") == "already_resolved" || response.optString("result") == "invalid") {
                    mutableOnlineAnswerLocked.value = true
                    requestOnlineSnapshot()
                }
            }
        })
        answerTimeoutJob = viewModelScope.launch {
            delay(5_000L)
            if (pendingAnswer == pending) {
                mutableOnlineSubmissionStatus.value = "Connection slow"
                requestOnlineSnapshot()
                delay(3_000L)
                if (pendingAnswer == pending) {
                    pendingAnswer = null
                    if (isCurrentQuestionOpen()) mutableOnlineAnswerLocked.value = false
                    else markQuestionExpiredLocally()
                }
            }
        }
    }

    fun leaveOnlineLobby() {
        returnedMatchId = onlineMatch.value?.matchId
        if (room.value?.ranked == true) {
            endParticipation(keepConnection = true)
            return
        }
        clearOnlineMatch()
        mutableState.value = BattleState(phase = BattlePhase.HOME, battleId = mutableState.value.battleId + 1)
    }

    fun findNewOpponent() {
        val current = socket
        if (current?.connected() != true || !sessionReady) return
        current.emit("room:leave", JSONObject(), io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || args.firstOrNull() !is JSONObject) return@post
                val response = args[0] as JSONObject
                if (!response.optBoolean("ok")) { mutableRoomError.value = serverErrorMessage(response, "Could not leave ranked match"); return@post }
                mutableRoom.value = null
                clearOnlineMatch()
                mutableState.value = BattleState(phase = BattlePhase.HOME, battleId = mutableState.value.battleId + 1)
                findMatch()
            }
        })
    }

    private fun requestOnlineSnapshot() {
        val current = socket ?: return
        if (!sessionReady || !current.connected()) return
        val matchId = onlineMatch.value?.matchId ?: return
        current.emit("match:state", JSONObject(), io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || onlineMatch.value?.matchId != matchId) return@post
                val response = args.firstOrNull() as? JSONObject ?: return@post
                if (response.optBoolean("ok", false)) {
                    response.optJSONObject("snapshot")?.let { snapshot ->
                        val clock = serverClock
                        val opensAt = snapshot.optLong("opensAt")
                        val closesAt = snapshot.optLong("closesAt")
                        if (snapshot.optLong("revision") == onlineRevision && snapshot.optString("phase").uppercase() == "ANSWERING" &&
                            clock != null && opensAt > 0L && SystemClock.elapsedRealtime() >= clock.localElapsedFor(opensAt) &&
                            closesAt > opensAt && SystemClock.elapsedRealtime() < clock.localElapsedFor(closesAt) &&
                            mutableState.value.question != null) {
                            clearPendingAnswer()
                            mutableOnlineAnswerLocked.value = false
                            startAnswerCountdown(matchId, snapshot.optLong("questionId"), opensAt, closesAt)
                        } else if (snapshot.optLong("revision") == onlineRevision && snapshot.optString("phase").uppercase() == "ANSWERING" &&
                            clock != null && closesAt > 0L && SystemClock.elapsedRealtime() >= clock.localElapsedFor(closesAt)) {
                            clearPendingAnswer()
                            markQuestionExpiredLocally()
                        }
                        applyOnlineSnapshot(snapshot)
                    }
                } else if (response.optString("result") == "ended") {
                    clearOnlineMatch()
                    mutableState.value = BattleState(phase = BattlePhase.HOME, battleId = mutableState.value.battleId + 1)
                    mutableRoomError.value = "Match ended"
                } else {
                    clearPendingAnswer()
                    mutableOnlineAnswerLocked.value = true
                    mutableOnlineSubmissionStatus.value = serverErrorMessage(response, "Server temporarily unavailable.", RetryScope.MATCH_STATE)
                    if (response.optString("code") == "RATE_LIMITED") {
                        answerTimeoutJob = viewModelScope.launch {
                            delay(retryDelayRemaining(RetryScope.MATCH_STATE))
                            if (onlineMatch.value?.matchId == matchId && isCurrentQuestionOpen() && !onlinePaused.value) {
                                mutableOnlineSubmissionStatus.value = ""
                                mutableOnlineAnswerLocked.value = false
                            }
                        }
                    }
                }
            }
        })
    }

    private fun clearOnlineMatch() {
        mutableXpResult.value = null
        onlineResolutionJob?.cancel()
        onlineResultJob?.cancel()
        questionCountdownJob?.cancel()
        questionCountdownJob = null
        clearAnswerCountdown()
        scheduledOnlineQuestion = null
        mutableOnlineQuestionPrompt.value = ""
        mutableOnlineMatch.value = null
        mutableOnlineAnswerLocked.value = false
        onlineResult = null
        pendingAnswer = null
        answerTimeoutJob?.cancel()
        answerTimeoutJob = null
        mutableOnlineSubmissionStatus.value = ""
        onlineRevision = 0L
        pauseCountdownJob?.cancel()
        pauseServerDeadline = null
        mutableOnlinePaused.value = false
        lastAttackQuestion = 0L
    }

    private fun clearPendingAnswer() {
        pendingAnswer = null
        answerTimeoutJob?.cancel()
        answerTimeoutJob = null
        mutableOnlineSubmissionStatus.value = ""
    }

    private fun suspendOnlineMatch() {
        roomOperationGeneration++
        roomRequestPending = false
        roomRequestTimeout?.cancel()
        roomRequestTimeout = null
        if (onlineMatch.value == null) return
        clearPendingAnswer()
        onlineResolutionJob?.cancel()
        onlineResultJob?.cancel()
        questionCountdownJob?.cancel()
        questionCountdownJob = null
        clearAnswerCountdown()
        scheduledOnlineQuestion = null
        mutableOnlineQuestionPrompt.value = ""
        pauseCountdownJob?.cancel()
        pauseServerDeadline = null
        mutableOnlineAnswerLocked.value = true
        mutableOnlinePaused.value = true
        if (state.value.phase != BattlePhase.RESULT) {
            mutableState.value = state.value.copy(phase = BattlePhase.ANSWERING, question = null,
                input = "", wrongAnswer = false, attacker = null)
            mutableOnlineSubmissionStatus.value = "Reconnecting\u2026"
        }
    }

    fun leaveRoom() = endParticipation(keepConnection = true)

    private fun endParticipation(keepConnection: Boolean = false) {
        val sessionRequest = ++sessionOperation
        roomOperationGeneration++
        roomRequestPending = false
        roomRequestTimeout?.cancel()
        roomRequestTimeout = null
        clearSearch()
        val current = socket
        val generation = connectionGeneration
        val keep = keepConnection && sessionReady && current?.connected() == true
        playerId = null
        resumeToken = null
        sessionReady = false
        wantsConnection = keep
        reconnectJob?.cancel()
        acknowledgementJob?.cancel()
        mutableRoom.value = null
        mutableRoomCodeInput.value = ""
        mutableRoomError.value = ""
        val wasOnline = onlineMatch.value != null
        returnedMatchId = onlineMatch.value?.matchId
        clearOnlineMatch()
        if (wasOnline) {
            mutableState.value = BattleState(phase = BattlePhase.HOME, battleId = mutableState.value.battleId + 1)
        }
        mutableConnectionStatus.value = if (keep) ConnectionStatus.CONNECTING else ConnectionStatus.DISCONNECTED
        mutableConnectionMessage.value = ""
        if (current?.connected() == true) {
            // Give the intentional leave packet time to reach the server before closing transport.
            acknowledgementJob = viewModelScope.launch {
                delay(3_000)
                if (socket === current && connectionGeneration == generation) {
                    disposeSocket(ConnectionStatus.DISCONNECTED)
                    if (keep && wantsConnection && connectionForeground) openSocket(serverUrl.value.trim().trimEnd('/'))
                }
            }
            current.emit("session:leave", JSONObject(), io.socket.client.Ack {
                mainHandler.post {
                    if (socket !== current || generation != connectionGeneration || sessionRequest != sessionOperation) return@post
                    acknowledgementJob?.cancel()
                    if (keep && wantsConnection && connectionForeground) authenticateSession(current, generation)
                    else disposeSocket(ConnectionStatus.DISCONNECTED)
                }
            })
        } else disposeSocket(ConnectionStatus.DISCONNECTED)
    }

    private fun setRoomError(message: String) { mutableRoomError.value = message }

    private fun serverErrorMessage(response: JSONObject, fallback: String, retryScope: RetryScope? = null): String {
        val code = response.optString("code")
        if (code == "RATE_LIMITED" && retryScope != null) {
            val retryAfter = response.optLong("retryAfterMs", 1_000L).coerceIn(0L, 60L * 60_000L)
            retryUntilElapsedMs[retryScope] = maxOf(retryUntilElapsedMs[retryScope] ?: 0L,
                SystemClock.elapsedRealtime() + retryAfter)
        }
        val message = when (code) {
            "RATE_LIMITED" -> "Too many attempts. Try again shortly."
            "AUTH_REQUIRED", "SESSION_EXPIRED" -> "Session expired. Reconnect."
            "INVALID_PAYLOAD" -> "Invalid request."
            "SERVICE_UNAVAILABLE", "SERVER_SHUTDOWN" -> "Server temporarily unavailable."
            "PROFILE_REQUIRED" -> "Complete your profile first."
            "ACCOUNT_IN_USE" -> "This account is already connected."
            "ALREADY_IN_ROOM" -> "You are already in a room or match."
            "ROOM_NOT_FOUND" -> "Room not found."
            "ROOM_FULL" -> "Room is full."
            "INVALID_ROOM_CODE" -> "Invalid room code."
            "FORBIDDEN" -> "Action is not allowed."
            "INVALID_STATE" -> "Action is not available now."
            "MATCH_UNAVAILABLE" -> "Match unavailable."
            else -> fallback
        }
        if ((code == "AUTH_REQUIRED" || code == "SESSION_EXPIRED") && sessionReady) {
            disconnectMessageAfterDispose = message
            endParticipation()
            mutableConnectionMessage.value = message
            mutableRoomError.value = message
            mutableOnlineSubmissionStatus.value = message
        }
        return message
    }

    private fun retryDelayRemaining(scope: RetryScope): Long =
        ((retryUntilElapsedMs[scope] ?: 0L) - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    private fun retryBlocked(scope: RetryScope, onBlocked: (String) -> Unit): Boolean {
        if (retryDelayRemaining(scope) <= 0L) return false
        onBlocked("Too many attempts. Try again shortly.")
        return true
    }

    private fun beginRoomRequest(operation: Long) {
        roomRequestPending = true
        roomRequestTimeout?.cancel()
        roomRequestTimeout = viewModelScope.launch {
            delay(5_000)
            if (operation == roomOperationGeneration && roomRequestPending) {
                roomRequestPending = false
                setRoomError("Room request timed out. Try again.")
            }
        }
    }

    private fun handleRoomAck(response: JSONObject, operation: Long) {
        if (operation != roomOperationGeneration) return
        roomRequestTimeout?.cancel()
        roomRequestTimeout = null
        if (!response.optBoolean("ok", false)) {
            roomRequestPending = false
            setRoomError(serverErrorMessage(response, "Room request failed", RetryScope.ROOM_ENTRY))
            return
        }
        roomRequestPending = false
        response.optJSONObject("room")?.let(::updateRoom)
    }

    private fun updateRoom(value: JSONObject) {
        roomRequestPending = false
        roomRequestTimeout?.cancel()
        roomRequestTimeout = null
        val roomDifficulty = parseDifficulty(value.optString("difficulty"))
        mutableRoom.value = RoomInfo(
            value.optString("code"),
            value.optString("role").replaceFirstChar { it.uppercase() },
            value.optInt("playerCount", 1),
            value.optBoolean("hostReady"),
            value.optBoolean("guestReady"),
            value.optBoolean("matchActive"),
            value.optString("hostName"),
            value.optString("guestName"),
            roomDifficulty,
            value.optBoolean("ranked"),
            value.optInt("hostRating", 1000),
            if (value.has("guestRating") && !value.isNull("guestRating")) value.optInt("guestRating") else null,
            value.optString("hostTier", "Silver"),
            value.optString("guestTier").takeIf { it.isNotBlank() }
        )
        mutableDifficulty.value = roomDifficulty
        mutableRoomError.value = ""
        if (search.value.active || search.value.status != "idle") clearSearch()
    }

    private fun clearRoomFromServer(message: String? = null) {
        roomOperationGeneration++
        roomRequestPending = false
        roomRequestTimeout?.cancel()
        roomRequestTimeout = null
        mutableRoom.value = null
        mutableRoomCodeInput.value = ""
        if (!message.isNullOrBlank()) mutableRoomError.value = message
        if (mutableSearch.value.active) clearSearch()
        if (mutableOnlineMatch.value != null && onlineResult == null && state.value.phase != BattlePhase.RESULT) {
            clearOnlineMatch()
            mutableState.value = BattleState(phase = BattlePhase.HOME, battleId = mutableState.value.battleId + 1)
        }
    }

    private fun roleForSocket(): String? = room.value?.role?.lowercase()

    private fun onlineQuestion(value: JSONObject): Question {
        val operation = when (value.optString("operation")) {
            "SUBTRACT" -> Operation.SUBTRACT
            "MULTIPLY" -> Operation.MULTIPLY
            "DIVIDE" -> Operation.DIVIDE
            else -> Operation.ADD
        }
        return Question(value.optInt("left"), operation, value.optInt("right"))
    }

    private fun onlineBattleState(question: Question, info: OnlineMatchInfo, hostHp: Int, guestHp: Int, phase: BattlePhase, attacker: Fighter? = null): BattleState {
        val localHp = if (info.role.equals("host", true)) hostHp else guestHp
        val opponentHp = if (info.role.equals("host", true)) guestHp else hostHp
        return BattleState(phase = phase, playerHp = localHp, opponentHp = opponentHp, question = question,
            attacker = attacker, battleId = info.matchId.hashCode().toLong(), questionId = info.questionId)
    }

    private fun stageOnlineQuestion(
        info: OnlineMatchInfo,
        question: Question,
        hostHp: Int,
        guestHp: Int,
        revision: Long,
        opensAt: Long,
        closesAt: Long,
        reason: String
    ) {
        clearPendingAnswer()
        clearAnswerCountdown()
        onlineResolutionJob?.cancel()
        onlineResultJob?.cancel()
        pauseCountdownJob?.cancel()
        pauseServerDeadline = null
        val scheduled = ScheduledOnlineQuestion(
            info.matchId, info.questionId, revision, opensAt, closesAt, reason, question, hostHp, guestHp
        )
        scheduledOnlineQuestion = scheduled
        mutableOnlineAnswerLocked.value = true
        mutableOnlinePaused.value = reason == "RESUME"
        mutableOnlineQuestionPrompt.value = when (reason) {
            "FIRST" -> "Get ready\u2026"
            "RESUME" -> "Resuming\u2026"
            else -> "Next question\u2026"
        }
        mutableState.value = onlineBattleState(question, info, hostHp, guestHp, BattlePhase.ANSWERING)
            .copy(question = null, input = "", wrongAnswer = false, attacker = null)
        refreshQuestionCountdown()
    }

    private fun refreshQuestionCountdown() {
        questionCountdownJob?.cancel()
        questionCountdownJob = null
        val scheduled = scheduledOnlineQuestion ?: return
        val clock = serverClock
        if (clock == null) {
            mutableOnlineQuestionPrompt.value = "Synchronizing…"
            mutableOnlineAnswerLocked.value = true
            return
        }
        val localOpensAt = clock.localElapsedFor(scheduled.opensAt)
        questionCountdownJob = viewModelScope.launch {
            while (scheduledOnlineQuestion === scheduled && mutableOnlineMatch.value?.matchId == scheduled.matchId &&
                mutableOnlineMatch.value?.questionId == scheduled.questionId) {
                val remaining = localOpensAt - SystemClock.elapsedRealtime()
                if (remaining <= 0L) {
                    revealScheduledQuestion(scheduled)
                    return@launch
                }
                mutableOnlineQuestionPrompt.value = when (scheduled.reason) {
                    "FIRST" -> (((remaining + 999L) / 1_000L).coerceIn(1L, 3L)).toString()
                    "RESUME" -> "Resuming…"
                    else -> "Next question…"
                }
                delay(minOf(remaining, 100L))
            }
        }
    }

    private fun revealScheduledQuestion(scheduled: ScheduledOnlineQuestion) {
        val info = mutableOnlineMatch.value ?: return
        if (scheduledOnlineQuestion !== scheduled || info.matchId != scheduled.matchId || info.questionId != scheduled.questionId ||
            !sessionReady || !connectionForeground || socket?.connected() != true) return
        scheduledOnlineQuestion = null
        mutableOnlineQuestionPrompt.value = ""
        mutableOnlinePaused.value = false
        mutableOnlineSubmissionStatus.value = ""
        mutableState.value = onlineBattleState(
            scheduled.question, info, scheduled.hostHp, scheduled.guestHp, BattlePhase.ANSWERING
        )
        startAnswerCountdown(info.matchId, info.questionId, scheduled.opensAt, scheduled.closesAt)
    }

    private fun clearScheduledQuestion() {
        questionCountdownJob?.cancel()
        questionCountdownJob = null
        scheduledOnlineQuestion = null
        mutableOnlineQuestionPrompt.value = ""
        clearAnswerCountdown()
    }

    private fun startAnswerCountdown(matchId: String, questionId: Long, opensAt: Long, closesAt: Long) {
        clearAnswerCountdown()
        val clock = serverClock
        if (clock == null || opensAt <= 0L || closesAt <= opensAt) {
            mutableOnlineAnswerLocked.value = true
            mutableOnlineQuestionPrompt.value = "Synchronizing\u2026"
            return
        }
        val window = ActiveQuestionWindow(matchId, questionId, opensAt, closesAt)
        activeQuestionWindow = window
        val localOpensAt = clock.localElapsedFor(opensAt)
        val localClosesAt = clock.localElapsedFor(closesAt)
        answerCountdownJob = viewModelScope.launch {
            while (activeQuestionWindow === window && mutableOnlineMatch.value?.matchId == matchId &&
                mutableOnlineMatch.value?.questionId == questionId) {
                val now = SystemClock.elapsedRealtime()
                if (now < localOpensAt) {
                    mutableOnlineAnswerLocked.value = true
                    delay(minOf(localOpensAt - now, 100L))
                    continue
                }
                val remaining = localClosesAt - now
                if (remaining <= 0L) {
                    markQuestionExpiredLocally()
                    return@launch
                }
                val seconds = ((remaining + 999L) / 1_000L).toInt()
                mutableOnlineQuestionTimer.value = OnlineQuestionTimerState(visible = true, seconds = seconds)
                mutableOnlineAnswerLocked.value = false
                delay((remaining - (seconds - 1L) * 1_000L).coerceAtLeast(1L))
            }
        }
    }

    private fun clearAnswerCountdown() {
        answerCountdownJob?.cancel()
        answerCountdownJob = null
        activeQuestionWindow = null
        mutableOnlineQuestionTimer.value = OnlineQuestionTimerState()
    }

    private fun markQuestionExpiredLocally() {
        answerCountdownJob?.cancel()
        answerCountdownJob = null
        clearPendingAnswer()
        mutableOnlineAnswerLocked.value = true
        mutableOnlineQuestionTimer.value = OnlineQuestionTimerState(visible = true, seconds = 0, expired = true)
    }

    private fun isCurrentQuestionOpen(): Boolean {
        val info = mutableOnlineMatch.value ?: return false
        val window = activeQuestionWindow ?: return false
        val clock = serverClock ?: return false
        if (window.matchId != info.matchId || window.questionId != info.questionId) return false
        val now = SystemClock.elapsedRealtime()
        return now >= clock.localElapsedFor(window.opensAt) && now < clock.localElapsedFor(window.closesAt)
    }

    private fun refreshPauseCountdown() {
        pauseCountdownJob?.cancel()
        pauseCountdownJob = null
        val deadline = pauseServerDeadline ?: return
        val clock = serverClock
        if (clock == null) {
            if (onlinePaused.value && sessionReady) mutableOnlineSubmissionStatus.value = "Opponent reconnecting…"
            return
        }
        val matchId = onlineMatch.value?.matchId ?: return
        val revision = onlineRevision
        val localDeadline = clock.localElapsedFor(deadline)
        pauseCountdownJob = viewModelScope.launch {
            do {
                val seconds = ((localDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L) + 999L) / 1_000L
                mutableOnlineSubmissionStatus.value = "Opponent reconnecting… ${seconds}s"
                if (seconds == 0L) break
                delay(250L)
            } while (onlineMatch.value?.matchId == matchId && onlineRevision == revision && onlinePaused.value)
        }
    }

    private fun handleMatchStart(value: JSONObject) {
        if (!sessionReady || value.optString("matchId") == returnedMatchId ||
            (onlineMatch.value != null && state.value.phase != BattlePhase.RESULT)) return
        val role = roleForSocket() ?: return
        clearOnlineMatch()
        mutableRankedResult.value = RankedResult(ranked = value.optBoolean("ranked"))
        cancelBot()
        mutableProfile.value = profile.value.copy(editing = false)
        val info = OnlineMatchInfo(value.optString("matchId"), value.optLong("questionId"), role,
            value.optString("hostName"), value.optString("guestName"), parseDifficulty(value.optString("difficulty")),
            value.optBoolean("ranked"), value.optInt("hostRating", 1000), value.optInt("guestRating", 1000), value.optString("hostTier", "Silver"), value.optString("guestTier", "Silver"))
        mutableOnlineMatch.value = info
        onlineRevision = value.optLong("revision", 1L)
        onlineResult = null
        stageOnlineQuestion(info, onlineQuestion(value), value.optInt("playerHp", 100), value.optInt("opponentHp", 100),
            onlineRevision, value.optLong("opensAt"), value.optLong("closesAt"),
            value.optString("scheduleReason", "FIRST").uppercase())
    }

    private fun handleMatchQuestion(value: JSONObject) {
        if (!sessionReady) return
        val old = mutableOnlineMatch.value ?: return
        val revision = value.optLong("revision")
        if (value.optString("matchId") != old.matchId || value.optLong("questionId") <= old.questionId || revision <= onlineRevision) return
        onlineRevision = revision
        val info = old.copy(questionId = value.optLong("questionId"), difficulty = parseDifficulty(value.optString("difficulty")).takeIf { value.has("difficulty") } ?: old.difficulty)
        mutableOnlineMatch.value = info
        stageOnlineQuestion(info, onlineQuestion(value), value.optInt("playerHp"), value.optInt("opponentHp"), revision,
            value.optLong("opensAt"), value.optLong("closesAt"), value.optString("scheduleReason", "NEXT").uppercase())
    }

    private fun handleMatchAttack(value: JSONObject) {
        if (!sessionReady) return
        val info = mutableOnlineMatch.value ?: return
        val revision = value.optLong("revision")
        if (value.optString("matchId") != info.matchId || value.optLong("questionId") != info.questionId || revision <= onlineRevision ||
            mutableState.value.phase != BattlePhase.ANSWERING || info.questionId <= lastAttackQuestion) return
        val question = mutableState.value.question ?: scheduledOnlineQuestion
            ?.takeIf { it.matchId == info.matchId && it.questionId == info.questionId }?.question ?: return
        lastAttackQuestion = info.questionId
        onlineRevision = revision
        clearScheduledQuestion()
        pauseCountdownJob?.cancel()
        pauseServerDeadline = null
        mutableOnlinePaused.value = false
        pendingAnswer = null
        answerTimeoutJob?.cancel()
        mutableOnlineSubmissionStatus.value = ""
        mutableOnlineAnswerLocked.value = true
        val attackerRole = value.optString("attacker")
        val attacker = if (attackerRole.equals(info.role, true)) Fighter.PLAYER else Fighter.BOT
        val hostHp = value.optInt("playerHp", 0)
        val guestHp = value.optInt("opponentHp", 0)
        mutableState.value = onlineBattleState(question, info, hostHp, guestHp, BattlePhase.WINDUP, attacker)
        onlineResolutionJob?.cancel()
        onlineResolutionJob = viewModelScope.launch {
            delay(180)
            if (mutableOnlineMatch.value != info || onlinePaused.value || !sessionReady) return@launch
            mutableState.value = onlineBattleState(question, info, hostHp, guestHp, BattlePhase.IMPACT, attacker)
            mutableFeedback.tryEmit(CombatFeedback(mutableState.value.key, FeedbackKind.HIT))
            delay(COMBAT_ANIMATION_DURATION_MS - 180L)
            if (mutableOnlineMatch.value != info || onlinePaused.value || !sessionReady) return@launch
            if (value.optBoolean("ko")) {
                mutableState.value = onlineBattleState(question, info, hostHp, guestHp, BattlePhase.KO, attacker).copy(winner = attacker)
                mutableFeedback.tryEmit(CombatFeedback(mutableState.value.key, FeedbackKind.KO))
            } else {
                mutableState.value = onlineBattleState(question, info, hostHp, guestHp, BattlePhase.TRANSITION, attacker)
            }
        }
    }

    private fun handleMatchResult(value: JSONObject) {
        if (!sessionReady) return
        val info = mutableOnlineMatch.value ?: return
        val revision = value.optLong("revision")
        if (value.optString("matchId") != info.matchId || revision <= onlineRevision) return
        if (value.optString("message").isNotBlank() || onlinePaused.value) {
            applyOnlineSnapshot(value, recovered = false)
            return
        }
        onlineRevision = revision
        clearScheduledQuestion()
        pauseCountdownJob?.cancel()
        pauseServerDeadline = null
        mutableOnlinePaused.value = false
        applyRankedResult(value)
        onlineResult = value.optString("winner")
        clearPendingAnswer()
        mutableOnlineAnswerLocked.value = true
        onlineResultJob?.cancel()
        onlineResultJob = viewModelScope.launch {
            delay(600)
            if (mutableOnlineMatch.value == info && onlineResult != null) {
                mutableState.value = mutableState.value.copy(
                    phase = BattlePhase.RESULT,
                    winner = if (onlineResult.equals(info.role, true)) Fighter.PLAYER else Fighter.BOT
                )
                fetchProfileStats()
            }
        }
    }

    private fun applyOnlineSnapshot(value: JSONObject, recovered: Boolean = true) {
        if (!sessionReady) return
        val info = mutableOnlineMatch.value ?: return
        if (value.optString("matchId") != info.matchId) return
        val revision = value.optLong("revision")
        if (revision <= onlineRevision) return
        val questionValue = value.optJSONObject("question") ?: return
        val questionId = value.optLong("questionId")
        val updatedInfo = info.copy(
            questionId = questionId,
            hostName = value.optString("hostName", info.hostName),
            guestName = value.optString("guestName", info.guestName),
            difficulty = parseDifficulty(value.optString("difficulty")).takeIf { value.has("difficulty") } ?: info.difficulty
        )
        onlineRevision = revision
        mutableOnlineMatch.value = updatedInfo
        clearPendingAnswer()
        onlineResolutionJob?.cancel()
        onlineResultJob?.cancel()
        pauseCountdownJob?.cancel()
        val question = onlineQuestion(questionValue.put("questionId", questionId).put("matchId", info.matchId))
        val hostHp = value.optInt("playerHp", 100)
        val guestHp = value.optInt("opponentHp", 100)
        when (value.optString("phase").uppercase()) {
            "SCHEDULED" -> {
                val reason = value.optString("scheduleReason", if (questionId == 1L) "FIRST" else "NEXT").uppercase()
                stageOnlineQuestion(updatedInfo, question, hostHp, guestHp, revision, value.optLong("opensAt"),
                    value.optLong("closesAt"), reason)
                if (reason == "RESUME") acknowledgeFreshQuestion(value)
            }
            "ANSWERING" -> {
                pauseServerDeadline = null
                mutableOnlinePaused.value = false
                val opensAt = value.optLong("opensAt")
                val closesAt = value.optLong("closesAt")
                val clock = serverClock
                if (clock == null || opensAt <= 0L || SystemClock.elapsedRealtime() < clock.localElapsedFor(opensAt)) {
                    val reason = value.optString("scheduleReason", if (questionId == 1L) "FIRST" else "NEXT").uppercase()
                    stageOnlineQuestion(updatedInfo, question, hostHp, guestHp, revision, opensAt, closesAt, reason)
                } else {
                    clearScheduledQuestion()
                    mutableOnlineSubmissionStatus.value = ""
                    mutableState.value = onlineBattleState(question, updatedInfo, hostHp, guestHp, BattlePhase.ANSWERING)
                    startAnswerCountdown(updatedInfo.matchId, updatedInfo.questionId, opensAt, closesAt)
                }
            }
            "RESOLVING" -> {
                clearScheduledQuestion()
                pauseServerDeadline = null
                mutableOnlinePaused.value = false
                mutableOnlineAnswerLocked.value = true
                lastAttackQuestion = maxOf(lastAttackQuestion, questionId)
                // Recovery updates HP silently; it never restarts an attack or its effects.
                mutableOnlineQuestionPrompt.value = if (hostHp == 0 || guestHp == 0) {
                    "Finishing battle\u2026"
                } else {
                    "Next question\u2026"
                }
                mutableState.value = onlineBattleState(question, updatedInfo, hostHp, guestHp, BattlePhase.TRANSITION)
                    .copy(question = null, input = "", wrongAnswer = false, attacker = null)
            }
            "PAUSED" -> {
                clearScheduledQuestion()
                mutableOnlinePaused.value = true
                mutableOnlineAnswerLocked.value = true
                lastAttackQuestion = maxOf(lastAttackQuestion, questionId)
                mutableState.value = onlineBattleState(question, updatedInfo, hostHp, guestHp, BattlePhase.ANSWERING)
                    .copy(question = null, input = "", wrongAnswer = false, attacker = null)
                pauseServerDeadline = value.optLong("deadline").takeIf { it > 0L }
                refreshPauseCountdown()
            }
            "FINISHED" -> {
                clearScheduledQuestion()
                pauseServerDeadline = null
                mutableOnlinePaused.value = false
                if (recovered) consumedXpMatchId = info.matchId
                mutableOnlineAnswerLocked.value = true
                val winner = value.optString("winner")
                onlineResult = winner
                applyRankedResult(value)
                mutableOnlineSubmissionStatus.value = value.optString("message", "")
                mutableState.value = onlineBattleState(question, updatedInfo, hostHp, guestHp, BattlePhase.RESULT)
                    .copy(winner = if (winner.equals(info.role, true)) Fighter.PLAYER else Fighter.BOT)
                fetchProfileStats()
            }
        }
    }

    private fun applyRankedResult(value: JSONObject) {
        applyXpResult(value)
        if (value.optString("matchType") != "RANKED") { mutableRankedResult.value = RankedResult(); return }
        val rating = value.optJSONObject("rating")
        if (rating == null) { mutableRankedResult.value = RankedResult(ranked = true); return }
        val role = onlineMatch.value?.role; val host = role.equals("host", true)
        mutableRankedResult.value = RankedResult(true, true, rating.optInt(if (host) "hostRatingBefore" else "guestRatingBefore", 1000), rating.optInt(if (host) "hostRatingDelta" else "guestRatingDelta"), rating.optInt(if (host) "hostRatingAfter" else "guestRatingAfter", 1000), if (host) tierForRating(rating.optInt("hostRatingAfter", 1000)) else tierForRating(rating.optInt("guestRatingAfter", 1000)))
    }

    private fun tierForRating(rating: Int) = when { rating < 900 -> "Bronze"; rating < 1100 -> "Silver"; rating < 1300 -> "Gold"; rating < 1500 -> "Platinum"; else -> "Diamond" }

    private fun applyXpResult(value: JSONObject) {
        val info = onlineMatch.value ?: return
        if (value.optString("matchId") != info.matchId) return
        mutableXpResult.value = value.optJSONObject("progression")?.let { parseXpResult(info.matchId, it) }
    }

    fun consumeXpAnimation(matchId: String): Boolean {
        if (!resumed.value || state.value.phase != BattlePhase.RESULT || onlineMatch.value?.matchId != matchId ||
            mutableXpResult.value?.matchId != matchId || consumedXpMatchId == matchId) return false
        consumedXpMatchId = matchId
        return true
    }

    private fun handleMatchSettled(value: JSONObject) {
        val info = onlineMatch.value ?: return
        if (!sessionReady || value.optString("matchId") != info.matchId || value.optLong("revision") <= onlineRevision) return
        onlineRevision = value.optLong("revision")
        // Settlement changes presentation data only; keep the existing KO/result timeline.
        applyRankedResult(value)
        fetchProfileStats()
    }

    private fun acknowledgeFreshQuestion(value: JSONObject) {
        val current = socket ?: return
        if (!sessionReady || !current.connected()) return
        current.emit("match:received", JSONObject().put("matchId", value.optString("matchId"))
            .put("questionId", value.optLong("questionId")).put("revision", value.optLong("revision")))
    }

    fun connectionPermissionDenied() {
        wantsConnection = false
        disposeSocket(ConnectionStatus.ERROR)
        mutableConnectionMessage.value = "Local network permission denied"
    }

    fun setConnectionForeground(value: Boolean) {
        connectionForeground = value
        if (!value) {
            if (wantsConnection) {
                reconnectJob?.cancel()
                clearSearch()
                suspendOnlineMatch()
                disposeSocket(ConnectionStatus.DISCONNECTED)
            }
        } else if (wantsConnection && socket == null) {
            reconnectAttempt = 0
            openSocket(serverUrl.value.trim().trimEnd('/'))
        }
    }

    private fun openSocket(url: String) {
        if (!connectionForeground || socket != null || !wantsConnection) return
        val generation = ++connectionGeneration
        acknowledgementJob?.cancel()
        mutableConnectionStatus.value = ConnectionStatus.CONNECTING
        mutableConnectionMessage.value = "Connecting…"
        val options = IO.Options().apply {
            timeout = 3_000
            reconnection = false
            forceNew = true
        }
        val created = try { IO.socket(url, options) } catch (error: Exception) {
            mutableConnectionStatus.value = ConnectionStatus.ERROR
            mutableConnectionMessage.value = "Invalid server URL"
            return
        }
        socket = created
        created.on("server:error") { args ->
            mainHandler.post {
                if (generation != connectionGeneration || socket !== created) return@post
                val response = args.firstOrNull() as? JSONObject ?: return@post
                mutableConnectionMessage.value = serverErrorMessage(response, "Server temporarily unavailable.", RetryScope.CONNECTION)
                clearPendingAnswer()
            }
        }
        created.on("server:shutdown") { args ->
            mainHandler.post {
                if (generation != connectionGeneration || socket !== created) return@post
                val response = args.firstOrNull() as? JSONObject ?: JSONObject().put("code", "SERVER_SHUTDOWN")
                val message = serverErrorMessage(response, "Server temporarily unavailable.")
                wantsConnection = false
                clearSearch()
                suspendOnlineMatch()
                disposeSocket(ConnectionStatus.ERROR)
                mutableConnectionMessage.value = message
                mutableOnlineSubmissionStatus.value = message
            }
        }
        created.on("room:state") { args ->
            mainHandler.post {
                if (generation != connectionGeneration || socket !== created || !sessionReady) return@post
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    updateRoom(args[0] as JSONObject)
                    // Room state is authoritative after pairing; clear any late or filtered search event.
                    if (search.value.active) clearSearch()
                }
            }
        }
        created.on("matchmaking:status") { args ->
            mainHandler.post {
                if (generation == connectionGeneration && socket === created && args.firstOrNull() is JSONObject) handleSearchStatus(args[0] as JSONObject)
            }
        }
        created.on("matchmaking:matched") { args ->
            mainHandler.post {
                if (generation == connectionGeneration && socket === created && args.firstOrNull() is JSONObject) handleMatched(args[0] as JSONObject)
            }
        }
        created.on("room:closed") { args ->
            mainHandler.post {
                if (generation != connectionGeneration || socket !== created || !sessionReady) return@post
                val closed = args.firstOrNull() as? JSONObject ?: return@post
                if (closed.optString("code") != room.value?.code) return@post
                val message = if (args.isNotEmpty() && args[0] is JSONObject)
                    (args[0] as JSONObject).optString("message") else "Room closed"
                clearRoomFromServer(message)
            }
        }
        created.on("match:start") { args ->
            mainHandler.post { if (generation == connectionGeneration && socket === created && args.firstOrNull() is JSONObject) handleMatchStart(args[0] as JSONObject) }
        }
        created.on("match:question") { args ->
            mainHandler.post { if (generation == connectionGeneration && socket === created && args.firstOrNull() is JSONObject) handleMatchQuestion(args[0] as JSONObject) }
        }
        created.on("match:attack") { args ->
            mainHandler.post { if (generation == connectionGeneration && socket === created && args.firstOrNull() is JSONObject) handleMatchAttack(args[0] as JSONObject) }
        }
        created.on("match:result") { args ->
            mainHandler.post { if (generation == connectionGeneration && socket === created && args.firstOrNull() is JSONObject) handleMatchResult(args[0] as JSONObject) }
        }
        created.on("match:settled") { args ->
            mainHandler.post { if (generation == connectionGeneration && socket === created && args.firstOrNull() is JSONObject) handleMatchSettled(args[0] as JSONObject) }
        }
        created.on("match:snapshot") { args ->
            mainHandler.post { if (generation == connectionGeneration && socket === created && args.firstOrNull() is JSONObject) applyOnlineSnapshot(args[0] as JSONObject) }
        }
        for (event in listOf("match:paused", "match:resumed")) {
            created.on(event) { args ->
                mainHandler.post {
                    if (generation == connectionGeneration && socket === created && sessionReady && args.firstOrNull() is JSONObject) {
                        applyOnlineSnapshot(args[0] as JSONObject)
                    }
                }
            }
        }
        created.on("match:ended") { args ->
            mainHandler.post {
                if (generation != connectionGeneration || socket !== created) return@post
                val ended = args.firstOrNull() as? JSONObject ?: return@post
                if (ended.optString("matchId") != onlineMatch.value?.matchId || ended.optLong("revision") <= onlineRevision) return@post
                val message = if (args.firstOrNull() is JSONObject) (args[0] as JSONObject).optString("message") else "Opponent disconnected"
                clearOnlineMatch()
                mutableState.value = BattleState(phase = BattlePhase.HOME, battleId = mutableState.value.battleId + 1)
                mutableRoomError.value = message.ifBlank { "Opponent disconnected" }
            }
        }
        created.on(Socket.EVENT_CONNECT) {
            mainHandler.post {
                if (generation != connectionGeneration || socket !== created || !wantsConnection || !connectionForeground) return@post
                authenticateSession(created, generation)
            }
        }
        created.on(Socket.EVENT_CONNECT_ERROR) {
            mainHandler.post {
                if (generation != connectionGeneration || socket !== created) return@post
                handleConnectionLoss(created, generation)
            }
        }
        created.on(Socket.EVENT_DISCONNECT) {
            mainHandler.post {
                if (generation != connectionGeneration || socket !== created) return@post
                handleConnectionLoss(created, generation)
            }
        }
        created.connect()
    }

    private fun authenticateSession(current: Socket, generation: Long) {
        val operation = ++sessionOperation
        sessionReady = false
        acknowledgementJob?.cancel()
        acknowledgementJob = viewModelScope.launch {
            delay(3_000)
            if (socket === current && generation == connectionGeneration && operation == sessionOperation) handleConnectionLoss(current, generation)
        }
        val credentials = JSONObject()
        playerId?.let { credentials.put("playerId", it) }
        resumeToken?.let { credentials.put("resumeToken", it) }
        current.emit("session:open", credentials, io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || generation != connectionGeneration || operation != sessionOperation) return@post
                val response = args.firstOrNull() as? JSONObject ?: return@post
                acknowledgementJob?.cancel()
                if (!response.optBoolean("ok")) {
                    val message = serverErrorMessage(response, "Session expired. Reconnect.", RetryScope.CONNECTION)
                    endParticipation()
                    disposeSocket(ConnectionStatus.DISCONNECTED)
                    mutableRoomError.value = message
                    mutableConnectionMessage.value = message
                    return@post
                }
                playerId = response.optString("playerId")
                response.optString("resumeToken").takeIf { it.isNotBlank() }?.let { resumeToken = it }
                restoreSessionState(response)
                startTimeSynchronization(current, generation, operation)
                acknowledgementJob = viewModelScope.launch {
                    try {
                        profileSaveJob?.join()
                        val saved = localProfile ?: throw ProfileSyncFailure("Set up your player profile first")
                        synchronizeProfile(current, saved)
                        if (socket !== current || generation != connectionGeneration || operation != sessionOperation) return@launch
                        mutableConnectionStatus.value = ConnectionStatus.CONNECTED
                        mutableConnectionMessage.value = "Connected"
                        if (profile.value.showing) fetchProfileStats()
                        if (leaderboardOpen.value) fetchLeaderboard()
                        checkServerConnection(current, generation, operation)
                    } catch (_: TimeoutCancellationException) {
                        handleConnectionLoss(current, generation)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: ProfileSyncFailure) {
                        if (socket === current && generation == connectionGeneration && operation == sessionOperation) {
                            suspendOnlineMatch()
                            disposeSocket(ConnectionStatus.ERROR)
                            mutableConnectionMessage.value = error.message.orEmpty()
                        }
                    }
                }
            }
        })
    }

    private fun restoreSessionState(response: JSONObject) {
        // Restore immediately after token authentication so battle events are not dropped while profile sync is in flight.
        sessionReady = true
        reconnectAttempt = 0
        val restoredRoom = response.optJSONObject("room")
        if (restoredRoom != null) updateRoom(restoredRoom) else clearRoomFromServer()
        val snapshot = response.optJSONObject("snapshot")
        if (snapshot != null && snapshot.optString("matchId") != returnedMatchId) {
            if (onlineMatch.value == null) {
                roleForSocket()?.let { role ->
                    cancelBot()
                            mutableOnlineMatch.value = OnlineMatchInfo(snapshot.optString("matchId"), snapshot.optLong("questionId"), role,
                                snapshot.optString("hostName"), snapshot.optString("guestName"), parseDifficulty(snapshot.optString("difficulty")), snapshot.optBoolean("ranked"), snapshot.optInt("hostRating", 1000), snapshot.optInt("guestRating", 1000), snapshot.optString("hostTier", "Silver"), snapshot.optString("guestTier", "Silver"))
                }
            }
            applyOnlineSnapshot(snapshot)
        } else if (onlineMatch.value != null && state.value.phase != BattlePhase.RESULT) {
            clearOnlineMatch()
            mutableState.value = BattleState(battleId = state.value.battleId + 1)
            mutableRoomError.value = "Match ended"
        }
    }

    private fun checkServerConnection(current: Socket, generation: Long, operation: Long) {
        acknowledgementJob = viewModelScope.launch {
            delay(3_000)
            if (socket === current && generation == connectionGeneration && operation == sessionOperation) {
                mutableConnectionMessage.value = "Server acknowledgement timed out"
            }
        }
        current.emit("connection:check", JSONObject(), io.socket.client.Ack { checkArgs ->
            mainHandler.post {
                if (socket === current && generation == connectionGeneration && sessionReady && operation == sessionOperation) {
                    acknowledgementJob?.cancel()
                    val response = checkArgs.firstOrNull() as? JSONObject
                    mutableConnectionMessage.value = when {
                        response == null -> "Server temporarily unavailable."
                        response.optBoolean("ok") -> response.optString("message", "Connected")
                        else -> serverErrorMessage(response, "Server temporarily unavailable.", RetryScope.CONNECTION)
                    }
                }
            }
        })
    }

    private fun handleConnectionLoss(current: Socket, generation: Long) {
        if (socket !== current || generation != connectionGeneration) return
        clearSearch()
        suspendOnlineMatch()
        disposeSocket(ConnectionStatus.DISCONNECTED)
        mutableConnectionMessage.value = if (retryDelayRemaining(RetryScope.CONNECTION) > 0L) "Too many attempts. Try again shortly." else "Reconnecting\u2026"
        scheduleReconnect(connectionGeneration)
    }

    private fun scheduleReconnect(generation: Long) {
        if (!wantsConnection || !connectionForeground || generation != connectionGeneration) return
        if (reconnectAttempt >= 5) {
            mutableConnectionStatus.value = ConnectionStatus.ERROR
            mutableConnectionMessage.value = "Could not reconnect. Try again."
            if (onlineMatch.value != null && state.value.phase != BattlePhase.RESULT) {
                mutableOnlineSubmissionStatus.value = "Connection lost. Retry connection or go Back."
            }
            return
        }
        reconnectJob?.cancel()
        val delayMs = maxOf(1_000L, retryDelayRemaining(RetryScope.CONNECTION))
        reconnectAttempt++
        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            if (wantsConnection && connectionForeground && generation == connectionGeneration && socket == null) {
                openSocket(serverUrl.value.trim().trimEnd('/'))
            }
        }
    }

    private fun disposeSocket(status: ConnectionStatus) {
        profileStatsRequest++
        profileStatsTimeout?.cancel()
        mutableProfile.value = profile.value.copy(statsLoading = false, stats = null)
        leaderboardRequest++
        leaderboardTimeout?.cancel()
        leaderboardTimeout = null
        mutableLeaderboard.value = LeaderboardState(connected = false,
            error = if (leaderboardOpen.value) "Connect to the server to view the leaderboard" else "")
        roomRequestPending = false
        roomRequestTimeout?.cancel()
        roomRequestTimeout = null
        clockSyncJob?.cancel()
        clockSyncJob = null
        serverClock = null
        connectionGeneration++
        sessionOperation++
        sessionReady = false
        acknowledgementJob?.cancel()
        val old = socket
        socket = null
        old?.off()
        old?.disconnect()
        mutableConnectionStatus.value = status
        if (status != ConnectionStatus.ERROR) mutableConnectionMessage.value = disconnectMessageAfterDispose.orEmpty()
        disconnectMessageAfterDispose = null
    }

    // Survives Activity recreation; skipped or consumed feedback is never retried.
    fun consumeFeedback(event: CombatFeedback): Boolean {
        if (!resumed.value || onlinePaused.value || state.value.key != event.token || consumedToken == event.token) return false
        consumedToken = event.token
        return true
    }

    private var botJob: Job? = null
    private var botKey: PhaseKey? = null
    private var botDeadlineMs: Long? = null

    init {
        loadProfile()
        viewModelScope.launch {
            combine(
                mutableState.map { it.key }.distinctUntilChanged(),
                resumed
            ) { key, active -> key.takeIf { active } }
                .distinctUntilChanged()
                .collectLatest { key ->
                    if (key == null || mutableOnlineMatch.value != null) return@collectLatest
                    val duration = key.phase.durationMillis() ?: return@collectLatest
                    delay(duration)
                    if (resumed.value) updateState(advancePhase(mutableState.value, key, difficulty.value))
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
        if (!resumed.value || mutableOnlineMatch.value != null || current.phase != BattlePhase.ANSWERING) return
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

    fun startBattle() {
        if (room.value != null || search.value.active || onlineMatch.value != null) return
        edit { if (it.phase == BattlePhase.HOME) newBattle(it, difficulty.value) else it }
    }
    fun restartBattle() = edit { if (it.phase == BattlePhase.RESULT) newBattle(it, difficulty.value) else it }
    fun digit(value: Int) {
        if (onlineMatch.value != null && (onlineAnswerLocked.value || !resumed.value || !isCurrentQuestionOpen())) return
        if (onlineMatch.value != null) mutableState.value = enterDigit(mutableState.value, value) else edit { enterDigit(it, value) }
    }
    fun backspace() {
        if (onlineMatch.value != null && (onlineAnswerLocked.value || !resumed.value || !isCurrentQuestionOpen())) return
        if (onlineMatch.value != null) mutableState.value = eraseDigit(mutableState.value) else edit(::eraseDigit)
    }
    fun clear() {
        if (onlineMatch.value != null && (onlineAnswerLocked.value || !resumed.value || !isCurrentQuestionOpen())) return
        if (onlineMatch.value != null) mutableState.value = clearInput(mutableState.value) else edit(::clearInput)
    }
    fun submit() {
        if (onlineMatch.value != null) submitOnlineAnswer() else edit(::submitAnswer)
    }
    fun returnHome() {
        if (onlineMatch.value != null || state.value.phase == BattlePhase.HOME) endParticipation()
        else edit { BattleState(battleId = it.battleId + 1) }
    }

    override fun onCleared() {
        cancelBot()
        wantsConnection = false
        reconnectJob?.cancel()
        endParticipation()
        disposeSocket(ConnectionStatus.IDLE)
        super.onCleared()
    }
}
