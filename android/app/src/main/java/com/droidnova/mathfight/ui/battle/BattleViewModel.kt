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
private data class PendingAnswer(val requestId: String, val matchId: String, val questionId: Long)
private class ProfileSyncFailure(message: String) : Exception(message)

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
    private var consumedImpact: PhaseKey? = null

    enum class ConnectionStatus { IDLE, CONNECTING, CONNECTED, DISCONNECTED, ERROR }
    private val mutableServerUrl = MutableStateFlow("http://192.168.1.4:3000")
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
    private var roomOperationGeneration = 0L
    private var roomRequestPending = false
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
    private val mutableOnlinePaused = MutableStateFlow(false)
    val onlinePaused = mutableOnlinePaused.asStateFlow()
    private var pauseCountdownJob: Job? = null
    private var lastAttackQuestion = 0L
    private var returnedMatchId: String? = null

    fun loadProfile() {
        if (profileLoadJob?.isActive == true) return
        mutableProfile.value = profile.value.copy(loading = true, loadFailed = false, error = "")
        profileLoadJob = viewModelScope.launch {
            try {
                val loaded = profileStore.load()
                mutableDifficulty.value = profileStore.loadDifficulty()
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
        mutableProfile.value = profile.value.copy(showing = true, editing = false, nameInput = profile.value.displayName, error = "", statsLoading = true)
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
        val current = socket
        if (current?.connected() != true || !sessionReady) { mutableLeaderboard.value = LeaderboardState(connected = false, error = "Connect to the server to view the leaderboard"); return }
        mutableLeaderboard.value = LeaderboardState(loading = true, connected = true)
        current.emit("leaderboard:get", io.socket.client.Ack { args ->
            mainHandler.post {
                val response = args.firstOrNull() as? JSONObject
                if (socket !== current || response?.optBoolean("ok") != true) { mutableLeaderboard.value = LeaderboardState(connected = true, error = response?.optString("error", "Leaderboard unavailable") ?: "Leaderboard unavailable"); return@post }
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
        } else profile.value.copy(showing = false, error = "")
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
        if (current?.connected() != true || !sessionReady) {
            mutableProfile.value = profile.value.copy(statsLoading = false, stats = null)
            return
        }
        mutableProfile.value = profile.value.copy(statsLoading = true)
        profileStatsTimeout = viewModelScope.launch {
            delay(5_000)
            if (request == profileStatsRequest) {
                profileStatsRequest++
                mutableProfile.value = profile.value.copy(statsLoading = false, stats = null)
            }
        }
        current.emit("profile:stats", io.socket.client.Ack { args ->
            mainHandler.post {
                if (request != profileStatsRequest || generation != connectionGeneration || operation != sessionOperation || socket !== current || !sessionReady) return@post
                profileStatsTimeout?.cancel()
                val response = args.firstOrNull() as? JSONObject
                if (response?.optBoolean("ok") != true) {
                    mutableProfile.value = profile.value.copy(statsLoading = false, stats = null)
                    return@post
                }
                val recent = mutableListOf<com.droidnova.mathfight.profile.ProfileMatchStat>()
                val rows = response.optJSONArray("matches")
                for (index in 0 until (rows?.length() ?: 0)) {
                    val row = rows?.optJSONObject(index) ?: continue
                    recent += com.droidnova.mathfight.profile.ProfileMatchStat(row.optString("localName", profile.value.displayName), row.optString("result"), row.optString("opponentName"), row.optString("difficulty"), row.optString("finishReason"), row.optString("matchType", "UNRANKED"), if (row.isNull("ratingChange")) null else row.optInt("ratingChange"), row.optJSONObject("progression")?.let { parseXpResult(row.optString("matchId"), it) })
                }
                mutableProfile.value = profile.value.copy(statsLoading = false, stats = com.droidnova.mathfight.profile.ProfileStats(response.optInt("matchesPlayed"), response.optInt("wins"), response.optInt("losses"), response.optDouble("winRate"), recent, response.optInt("rating", 1000), response.optString("tier", "Silver"), response.optInt("leaderboardPosition"), parseProgression(response)))
            }
        })
    }

    private suspend fun synchronizeProfile(current: Socket, value: LocalProfile): JSONObject {
        val generation = connectionGeneration
        val operation = sessionOperation
        if (socket !== current || !current.connected()) throw ProfileSyncFailure("Connection changed. Try again.")
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
        if (!response.optBoolean("ok")) throw ProfileSyncFailure(response.optString("error", "Profile was not accepted"))
        if (normalizedPlayerName(response.optString("displayName")) == null) throw ProfileSyncFailure("Invalid profile acknowledgement")
        response.optString("accountToken").takeIf { it.isNotBlank() }?.let { profileStore.saveAccountToken(it) }
        return response
    }

    fun consumeImpact(token: PhaseKey): Boolean {
        if (!resumed.value || state.value.key != token || token.phase != BattlePhase.IMPACT ||
            consumedToken != token || consumedImpact == token) return false
        consumedImpact = token
        return true
    }

    fun setSound(enabled: Boolean) { mutableSettings.value = settings.value.copy(sound = enabled) }
    fun setVibration(enabled: Boolean) { mutableSettings.value = settings.value.copy(vibration = enabled) }

    fun readyToggle() {
        val current = socket ?: return setRoomError("Connect to the server first")
        if (!sessionReady || !current.connected()) return
        val currentRoom = room.value ?: return setRoomError("Join a room first")
        val operation = roomOperationGeneration
        val ready = if (currentRoom.role.equals("Host", true)) !currentRoom.hostReady else !currentRoom.guestReady
        current.emit("room:ready", JSONObject().put("ready", ready), io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || !sessionReady || operation != roomOperationGeneration || args.isEmpty() || args[0] !is JSONObject) return@post
                val response = args[0] as JSONObject
                if (!response.optBoolean("ok", false)) setRoomError(response.optString("error", "Ready request failed"))
            }
        })
    }

    fun setRoomDifficulty(value: Difficulty) {
        val current = socket ?: return
        val currentRoom = room.value ?: return
        if (!currentRoom.role.equals("Host", true) || currentRoom.matchActive || !sessionReady) return
        current.emit("room:difficulty", JSONObject().put("difficulty", value.name), io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || args.firstOrNull() !is JSONObject) return@post
                val response = args[0] as JSONObject
                if (!response.optBoolean("ok")) setRoomError(response.optString("error", "Difficulty change failed"))
            }
        })
    }

    fun setServerUrl(value: String) {
        if (value == mutableServerUrl.value) return
        mutableServerUrl.value = value
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
        mutableRoomCodeInput.value = value.uppercase().filter { it.isLetterOrDigit() }.take(6)
        mutableRoomError.value = ""
    }

    fun findMatch() {
        val current = socket
        if (!sessionReady || current?.connected() != true) return setSearchError("Connect to the server first")
        if (room.value != null || onlineMatch.value != null) return setSearchError("Leave the current room first")
        if (search.value.active) return
        completedSearchId = null
        mutableSearch.value = MatchSearchState(active = true, status = "waiting", difficulty = difficulty.value)
        current.emit("matchmaking:join", JSONObject().put("difficulty", difficulty.value.name), io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || args.firstOrNull() !is JSONObject) return@post
                val response = args[0] as JSONObject
                if (!response.optBoolean("ok")) {
                    mutableSearch.value = MatchSearchState(error = response.optString("error", "Matchmaking failed"))
                } else {
                    val id = response.optString("searchId")
                    if (id.isNotBlank()) mutableSearch.value = search.value.copy(active = true, searchId = id, status = "waiting", difficulty = parseDifficulty(response.optString("difficulty")), error = "")
                }
            }
        })
    }

    fun cancelMatch() {
        val current = socket ?: return clearSearch()
        val id = search.value.searchId
        if (id.isBlank()) return clearSearch()
        current.emit("matchmaking:cancel", JSONObject().put("searchId", id), io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || search.value.searchId != id) return@post
                clearSearch()
            }
        })
    }

    private fun setSearchError(message: String) {
        mutableSearch.value = MatchSearchState(error = message)
    }

    private fun parseDifficulty(value: String): Difficulty = runCatching { Difficulty.valueOf(value) }.getOrDefault(Difficulty.STANDARD)

    private fun clearSearch() { mutableSearch.value = MatchSearchState() }

    private fun handleSearchStatus(value: JSONObject) {
        val id = value.optString("searchId")
        if (id == completedSearchId || room.value != null) return
        if (id.isBlank() || id != search.value.searchId && search.value.searchId.isNotBlank()) return
        when (value.optString("status")) {
            "waiting" -> mutableSearch.value = search.value.copy(active = true, searchId = id, status = "waiting",
                difficulty = parseDifficulty(value.optString("difficulty")))
            "cancelled", "idle" -> clearSearch()
        }
    }

    private fun handleMatched(value: JSONObject) {
        val id = value.optString("searchId")
        if (!search.value.active || (search.value.searchId.isNotBlank() && search.value.searchId != id)) return
        val roomValue = value.optJSONObject("room") ?: return
        completedSearchId = id
        mutableSearch.value = search.value.copy(active = false, status = "matched", opponentName = value.optString("opponentName"))
        updateRoom(roomValue)
        clearSearch()
    }

    fun createRoom() {
        if (search.value.active) return setRoomError("Cancel the current search first")
        val current = socket
        if (!sessionReady || current?.connected() != true) return setRoomError("Connect to the server first")
        val operation = ++roomOperationGeneration
        roomRequestPending = true
        current.emit("room:create", JSONObject().put("difficulty", difficulty.value.name), io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || operation != roomOperationGeneration || args.isEmpty() || args[0] !is JSONObject) return@post
                handleRoomAck(args[0] as JSONObject, operation)
            }
        })
    }

    fun joinRoom() {
        if (search.value.active) return setRoomError("Cancel the current search first")
        val code = roomCodeInput.value
        if (code.length != 6) return setRoomError("Enter a 6-character room code")
        val current = socket
        if (!sessionReady || current?.connected() != true) return setRoomError("Connect to the server first")
        val operation = ++roomOperationGeneration
        roomRequestPending = true
        current.emit("room:join", JSONObject().put("code", code), io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || operation != roomOperationGeneration || args.isEmpty() || args[0] !is JSONObject) return@post
                handleRoomAck(args[0] as JSONObject, operation)
            }
        })
    }

    fun submitOnlineAnswer() {
        val match = onlineMatch.value ?: return
        if (mutableState.value.phase != BattlePhase.ANSWERING || mutableOnlineAnswerLocked.value || mutableState.value.input.isEmpty()) return
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
            .put("answer", answer), io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || !sessionReady || pendingAnswer != pending || mutableOnlineMatch.value != match || onlinePaused.value) return@post
                val response = args.firstOrNull() as? JSONObject ?: return@post
                if (response.optString("requestId") != pending.requestId || response.optString("matchId") != pending.matchId || response.optLong("questionId") != pending.questionId) return@post
                pendingAnswer = null
                answerTimeoutJob?.cancel()
                mutableOnlineSubmissionStatus.value = ""
                if (response.optBoolean("ok", false) && response.optString("result") == "correct") {
                    // Remain locked until the authoritative attack or question update arrives.
                    mutableOnlineAnswerLocked.value = true
                } else if (response.optString("result") == "incorrect") {
                    mutableOnlineAnswerLocked.value = false
                    mutableState.value = mutableState.value.copy(input = "", wrongAnswer = true)
                } else if (response.optString("result") == "already_resolved" || response.optString("result") == "invalid") {
                    mutableOnlineAnswerLocked.value = false
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
                    mutableOnlineAnswerLocked.value = false
                }
            }
        }
    }

    fun leaveOnlineLobby() {
        returnedMatchId = onlineMatch.value?.matchId
        if (room.value?.ranked == true) {
            socket?.emit("room:leave")
            mutableRoom.value = null
        }
        clearOnlineMatch()
        mutableState.value = BattleState(phase = BattlePhase.HOME, battleId = mutableState.value.battleId + 1)
    }

    fun findNewOpponent() {
        val current = socket
        if (current?.connected() != true || !sessionReady) return
        current.emit("room:leave", io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || args.firstOrNull() !is JSONObject) return@post
                val response = args[0] as JSONObject
                if (!response.optBoolean("ok")) { mutableRoomError.value = response.optString("error", "Could not leave ranked match"); return@post }
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
        current.emit("match:state", io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || onlineMatch.value?.matchId != matchId) return@post
                val response = args.firstOrNull() as? JSONObject ?: return@post
                if (response.optBoolean("ok", false)) {
                    response.optJSONObject("snapshot")?.let { snapshot ->
                        if (snapshot.optLong("revision") == onlineRevision && snapshot.optString("phase") == "answering") {
                            clearPendingAnswer()
                            mutableOnlineAnswerLocked.value = false
                        }
                        applyOnlineSnapshot(snapshot)
                    }
                } else if (response.optString("result") == "ended") {
                    clearOnlineMatch()
                    mutableState.value = BattleState(phase = BattlePhase.HOME, battleId = mutableState.value.battleId + 1)
                    mutableRoomError.value = "Match ended"
                }
            }
        })
    }

    private fun clearOnlineMatch() {
        mutableXpResult.value = null
        onlineResolutionJob?.cancel()
        onlineResultJob?.cancel()
        mutableOnlineMatch.value = null
        mutableOnlineAnswerLocked.value = false
        onlineResult = null
        pendingAnswer = null
        answerTimeoutJob?.cancel()
        answerTimeoutJob = null
        mutableOnlineSubmissionStatus.value = ""
        onlineRevision = 0L
        pauseCountdownJob?.cancel()
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
        if (onlineMatch.value == null) return
        clearPendingAnswer()
        onlineResolutionJob?.cancel()
        onlineResultJob?.cancel()
        pauseCountdownJob?.cancel()
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
                if (socket === current && connectionGeneration == generation) disposeSocket(ConnectionStatus.DISCONNECTED)
            }
            current.emit("session:leave", io.socket.client.Ack {
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

    private fun handleRoomAck(response: JSONObject, operation: Long) {
        if (operation != roomOperationGeneration) return
        if (!response.optBoolean("ok", false)) {
            roomRequestPending = false
            setRoomError(response.optString("error", "Room request failed"))
            return
        }
        roomRequestPending = false
        response.optJSONObject("room")?.let(::updateRoom)
    }

    private fun updateRoom(value: JSONObject) {
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
    }

    private fun clearRoomFromServer(message: String? = null) {
        roomOperationGeneration++
        roomRequestPending = false
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
        mutableOnlineAnswerLocked.value = false
        pendingAnswer = null
        mutableOnlineSubmissionStatus.value = ""
        onlineResult = null
        mutableState.value = onlineBattleState(onlineQuestion(value), info, value.optInt("playerHp", 100), value.optInt("opponentHp", 100), BattlePhase.ANSWERING)
    }

    private fun handleMatchQuestion(value: JSONObject) {
        if (!sessionReady) return
        val old = mutableOnlineMatch.value ?: return
        val revision = value.optLong("revision")
        if (value.optString("matchId") != old.matchId || value.optLong("questionId") <= old.questionId || revision <= onlineRevision) return
        onlineRevision = revision
        val info = old.copy(questionId = value.optLong("questionId"), difficulty = parseDifficulty(value.optString("difficulty")).takeIf { value.has("difficulty") } ?: old.difficulty)
        mutableOnlineMatch.value = info
        mutableOnlineAnswerLocked.value = false
        pendingAnswer = null
        answerTimeoutJob?.cancel()
        mutableOnlineSubmissionStatus.value = ""
        onlineResolutionJob?.cancel()
        mutableState.value = onlineBattleState(onlineQuestion(value), info, value.optInt("playerHp"), value.optInt("opponentHp"), BattlePhase.ANSWERING)
    }

    private fun handleMatchAttack(value: JSONObject) {
        if (!sessionReady) return
        val info = mutableOnlineMatch.value ?: return
        val revision = value.optLong("revision")
        if (value.optString("matchId") != info.matchId || value.optLong("questionId") != info.questionId || revision <= onlineRevision ||
            mutableState.value.phase != BattlePhase.ANSWERING || onlinePaused.value || info.questionId <= lastAttackQuestion) return
        lastAttackQuestion = info.questionId
        onlineRevision = revision
        pendingAnswer = null
        answerTimeoutJob?.cancel()
        mutableOnlineSubmissionStatus.value = ""
        mutableOnlineAnswerLocked.value = true
        val attackerRole = value.optString("attacker")
        val attacker = if (attackerRole.equals(info.role, true)) Fighter.PLAYER else Fighter.BOT
        val question = mutableState.value.question ?: return
        val hostHp = value.optInt("playerHp", 0)
        val guestHp = value.optInt("opponentHp", 0)
        mutableState.value = onlineBattleState(question, info, hostHp, guestHp, BattlePhase.WINDUP, attacker)
        onlineResolutionJob?.cancel()
        onlineResolutionJob = viewModelScope.launch {
            delay(180)
            if (mutableOnlineMatch.value != info || onlinePaused.value || !sessionReady) return@launch
            mutableState.value = onlineBattleState(question, info, hostHp, guestHp, BattlePhase.IMPACT, attacker)
            mutableFeedback.tryEmit(CombatFeedback(mutableState.value.key, FeedbackKind.HIT))
            delay(320)
            if (mutableOnlineMatch.value != info || onlinePaused.value || !sessionReady) return@launch
            if (value.optBoolean("ko")) {
                mutableState.value = onlineBattleState(question, info, hostHp, guestHp, BattlePhase.KO, attacker).copy(winner = attacker)
                mutableFeedback.tryEmit(CombatFeedback(mutableState.value.key, FeedbackKind.KO))
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
        val updatedInfo = info.copy(questionId = questionId,
            hostName = value.optString("hostName", info.hostName), guestName = value.optString("guestName", info.guestName))
        onlineRevision = revision
        mutableOnlineMatch.value = updatedInfo
        clearPendingAnswer()
        onlineResolutionJob?.cancel()
        onlineResultJob?.cancel()
        pauseCountdownJob?.cancel()
        mutableOnlinePaused.value = value.optString("phase") in listOf("paused", "resuming")
        val question = onlineQuestion(questionValue.put("questionId", questionId).put("matchId", info.matchId))
        val hostHp = value.optInt("playerHp", 100)
        val guestHp = value.optInt("opponentHp", 100)
        when (value.optString("phase")) {
            "answering" -> {
                mutableOnlineAnswerLocked.value = false
                mutableState.value = onlineBattleState(question, updatedInfo, hostHp, guestHp, BattlePhase.ANSWERING)
            }
            "resolving" -> {
                mutableOnlineAnswerLocked.value = true
                lastAttackQuestion = maxOf(lastAttackQuestion, questionId)
                // Recovery updates HP silently; it never restarts an attack or its effects.
                mutableState.value = onlineBattleState(question, updatedInfo, hostHp, guestHp, BattlePhase.ANSWERING)
            }
            "paused", "resuming" -> {
                mutableOnlineAnswerLocked.value = true
                lastAttackQuestion = maxOf(lastAttackQuestion, if (value.optString("phase") == "paused") questionId else questionId - 1)
                mutableState.value = onlineBattleState(question, updatedInfo, hostHp, guestHp, BattlePhase.ANSWERING)
                    .copy(question = if (value.optString("phase") == "paused") null else question)
                if (value.optString("phase") == "resuming") {
                    mutableOnlineSubmissionStatus.value = "Waiting for both players\u2026"
                    acknowledgeFreshQuestion(value)
                } else {
                    val remaining = (value.optLong("deadline") - value.optLong("serverNow")).coerceAtLeast(0L)
                    val localDeadline = SystemClock.elapsedRealtime() + remaining
                    pauseCountdownJob = viewModelScope.launch {
                        do {
                            val seconds = ((localDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L) + 999) / 1000
                            mutableOnlineSubmissionStatus.value = "Opponent reconnecting\u2026 ${seconds}s"
                            if (seconds == 0L) break
                            delay(250)
                        } while (onlineMatch.value == updatedInfo && onlineRevision == revision)
                    }
                }
            }
            "result" -> {
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
            mutableConnectionMessage.value = error.message ?: "Invalid server URL"
            return
        }
        socket = created
        created.on("room:state") { args ->
            mainHandler.post {
                if (generation != connectionGeneration || socket !== created || !sessionReady ||
                    (!roomRequestPending && mutableRoom.value == null && !search.value.active)) return@post
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
                    val message = response.optString("error", "Session expired. Connect again.")
                    endParticipation()
                    disposeSocket(ConnectionStatus.DISCONNECTED)
                    mutableRoomError.value = message
                    mutableConnectionMessage.value = message
                    return@post
                }
                playerId = response.optString("playerId")
                resumeToken = response.optString("resumeToken")
                restoreSessionState(response)
                acknowledgementJob = viewModelScope.launch {
                    try {
                        profileSaveJob?.join()
                        val saved = localProfile ?: throw ProfileSyncFailure("Set up your player profile first")
                        synchronizeProfile(current, saved)
                        if (socket !== current || generation != connectionGeneration || operation != sessionOperation) return@launch
                        mutableConnectionStatus.value = ConnectionStatus.CONNECTED
                        mutableConnectionMessage.value = "Connected"
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
            // Receipt may be repeated if the snapshot was already applied; the server deduplicates it.
            if (snapshot.optString("phase") == "resuming") acknowledgeFreshQuestion(snapshot)
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
        current.emit("connection:check", io.socket.client.Ack { checkArgs ->
            mainHandler.post {
                if (socket === current && generation == connectionGeneration && sessionReady && operation == sessionOperation) {
                    acknowledgementJob?.cancel()
                    mutableConnectionMessage.value = (checkArgs.firstOrNull() as? JSONObject)
                        ?.optString("message", "Connected") ?: "Connected"
                }
            }
        })
    }

    private fun handleConnectionLoss(current: Socket, generation: Long) {
        if (socket !== current || generation != connectionGeneration) return
        clearSearch()
        suspendOnlineMatch()
        disposeSocket(ConnectionStatus.DISCONNECTED)
        mutableConnectionMessage.value = "Reconnecting\u2026"
        scheduleReconnect(connectionGeneration)
    }

    private fun scheduleReconnect(generation: Long) {
        if (!wantsConnection || !connectionForeground || generation != connectionGeneration) return
        if (reconnectAttempt >= 5) {
            if (onlineMatch.value != null && state.value.phase != BattlePhase.RESULT) {
                mutableOnlineSubmissionStatus.value = "Connection lost. Retry connection or go Back."
            }
            return
        }
        reconnectJob?.cancel()
        val delayMs = 1_000L
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
        connectionGeneration++
        sessionOperation++
        sessionReady = false
        acknowledgementJob?.cancel()
        val old = socket
        socket = null
        old?.off()
        old?.disconnect()
        mutableConnectionStatus.value = status
        if (status != ConnectionStatus.ERROR) mutableConnectionMessage.value = ""
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

    fun startBattle() = edit { if (it.phase == BattlePhase.HOME) newBattle(it, difficulty.value) else it }
    fun restartBattle() = edit { if (it.phase == BattlePhase.RESULT) newBattle(it, difficulty.value) else it }
    fun digit(value: Int) {
        if (onlineMatch.value != null && (onlineAnswerLocked.value || !resumed.value)) return
        if (onlineMatch.value != null) mutableState.value = enterDigit(mutableState.value, value) else edit { enterDigit(it, value) }
    }
    fun backspace() {
        if (onlineMatch.value != null && (onlineAnswerLocked.value || !resumed.value)) return
        if (onlineMatch.value != null) mutableState.value = eraseDigit(mutableState.value) else edit(::eraseDigit)
    }
    fun clear() {
        if (onlineMatch.value != null && (onlineAnswerLocked.value || !resumed.value)) return
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
