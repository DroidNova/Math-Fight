package com.droidnova.mathfight.ui.battle

import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidnova.mathfight.game.BattlePhase
import com.droidnova.mathfight.game.BattleState
import com.droidnova.mathfight.game.Fighter
import com.droidnova.mathfight.game.PhaseKey
import com.droidnova.mathfight.game.Question
import com.droidnova.mathfight.game.Operation
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

data class FeedbackSettings(val sound: Boolean = true, val vibration: Boolean = true)
enum class FeedbackKind { HIT, KO }
data class CombatFeedback(val token: PhaseKey, val kind: FeedbackKind)
data class RoomInfo(
    val code: String,
    val role: String,
    val playerCount: Int,
    val hostReady: Boolean = false,
    val guestReady: Boolean = false,
    val matchActive: Boolean = false
)
data class OnlineMatchInfo(val matchId: String, val questionId: Long, val role: String)

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

    enum class ConnectionStatus { IDLE, CONNECTING, CONNECTED, DISCONNECTED, ERROR }
    private val mutableServerUrl = MutableStateFlow("http://192.168.1.9:3000")
    val serverUrl = mutableServerUrl.asStateFlow()
    private val mutableConnectionStatus = MutableStateFlow(ConnectionStatus.IDLE)
    val connectionStatus = mutableConnectionStatus.asStateFlow()
    private val mutableConnectionMessage = MutableStateFlow("")
    val connectionMessage = mutableConnectionMessage.asStateFlow()
    private var socket: Socket? = null
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
    private var roomOperationGeneration = 0L
    private var roomRequestPending = false
    private val mutableOnlineMatch = MutableStateFlow<OnlineMatchInfo?>(null)
    val onlineMatch = mutableOnlineMatch.asStateFlow()
    private val mutableOnlineAnswerLocked = MutableStateFlow(false)
    val onlineAnswerLocked = mutableOnlineAnswerLocked.asStateFlow()
    private var onlineResolutionJob: Job? = null
    private var onlineResultJob: Job? = null
    private var onlineResult: String? = null

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
        val currentRoom = room.value ?: return setRoomError("Join a room first")
        val ready = if (currentRoom.role.equals("Host", true)) !currentRoom.hostReady else !currentRoom.guestReady
        current.emit("room:ready", JSONObject().put("ready", ready), io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || args.isEmpty() || args[0] !is JSONObject) return@post
                val response = args[0] as JSONObject
                if (!response.optBoolean("ok", false)) setRoomError(response.optString("error", "Ready request failed"))
            }
        })
    }

    fun setServerUrl(value: String) {
        if (value == mutableServerUrl.value) return
        mutableServerUrl.value = value
        leaveRoom()
        wantsConnection = false
        disposeSocket(ConnectionStatus.IDLE)
    }

    fun connect() {
        val url = serverUrl.value.trim().trimEnd('/')
        if (url.isEmpty()) {
            mutableConnectionStatus.value = ConnectionStatus.ERROR
            mutableConnectionMessage.value = "Enter a server URL"
            return
        }
        wantsConnection = true
        reconnectAttempt = 0
        reconnectJob?.cancel()
        leaveRoom()
        disposeSocket(ConnectionStatus.IDLE)
        openSocket(url)
    }

    fun disconnect() {
        wantsConnection = false
        reconnectJob?.cancel()
        reconnectJob = null
        leaveRoom()
        disposeSocket(ConnectionStatus.DISCONNECTED)
    }

    fun setRoomCodeInput(value: String) {
        mutableRoomCodeInput.value = value.uppercase().filter { it.isLetterOrDigit() }.take(6)
        mutableRoomError.value = ""
    }

    fun createRoom() {
        val current = socket
        if (current?.connected() != true) return setRoomError("Connect to the server first")
        val operation = ++roomOperationGeneration
        roomRequestPending = true
        current.emit("room:create", io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || operation != roomOperationGeneration || args.isEmpty() || args[0] !is JSONObject) return@post
                handleRoomAck(args[0] as JSONObject, operation)
            }
        })
    }

    fun joinRoom() {
        val code = roomCodeInput.value
        if (code.length != 6) return setRoomError("Enter a 6-character room code")
        val current = socket
        if (current?.connected() != true) return setRoomError("Connect to the server first")
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
        val current = socket ?: return
        val answer = mutableState.value.input
        mutableOnlineAnswerLocked.value = true
        mutableState.value = mutableState.value.copy(input = "", wrongAnswer = false)
        current.emit("match:answer", JSONObject()
            .put("matchId", match.matchId)
            .put("questionId", match.questionId)
            .put("answer", answer), io.socket.client.Ack { args ->
            mainHandler.post {
                if (socket !== current || mutableOnlineMatch.value?.matchId != match.matchId) return@post
                val response = args.firstOrNull() as? JSONObject ?: return@post
                if (!response.optBoolean("ok", false) && response.optBoolean("wrong", false)) {
                    mutableOnlineAnswerLocked.value = false
                    mutableState.value = mutableState.value.copy(input = "", wrongAnswer = true)
                } else if (!response.optBoolean("ok", false)) {
                    mutableOnlineAnswerLocked.value = false
                    mutableState.value = mutableState.value.copy(input = "", wrongAnswer = true)
                }
            }
        })
    }

    fun leaveOnlineLobby() {
        clearOnlineMatch()
        mutableState.value = BattleState(phase = BattlePhase.HOME, battleId = mutableState.value.battleId + 1)
    }

    private fun clearOnlineMatch() {
        onlineResolutionJob?.cancel()
        onlineResultJob?.cancel()
        mutableOnlineMatch.value = null
        mutableOnlineAnswerLocked.value = false
        onlineResult = null
    }

    fun leaveRoom() {
        roomOperationGeneration++
        roomRequestPending = false
        val current = socket
        if (current?.connected() == true && mutableRoom.value != null) {
            current.emit("room:leave", io.socket.client.Ack { })
        }
        mutableRoom.value = null
        mutableRoomCodeInput.value = ""
        mutableRoomError.value = ""
        clearOnlineMatch()
        if (mutableState.value.phase != BattlePhase.HOME) {
            mutableState.value = BattleState(phase = BattlePhase.HOME, battleId = mutableState.value.battleId + 1)
        }
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
        mutableRoom.value = RoomInfo(
            value.optString("code"),
            value.optString("role").replaceFirstChar { it.uppercase() },
            value.optInt("playerCount", 1),
            value.optBoolean("hostReady"),
            value.optBoolean("guestReady"),
            value.optBoolean("matchActive")
        )
        mutableRoomError.value = ""
    }

    private fun clearRoomFromServer(message: String? = null) {
        roomOperationGeneration++
        roomRequestPending = false
        mutableRoom.value = null
        mutableRoomCodeInput.value = ""
        if (!message.isNullOrBlank()) mutableRoomError.value = message
        if (mutableOnlineMatch.value != null) {
            clearOnlineMatch()
            mutableState.value = BattleState(phase = BattlePhase.HOME, battleId = mutableState.value.battleId + 1)
        }
    }

    private fun roleForSocket(): String? = room.value?.role?.lowercase()

    private fun onlineQuestion(value: JSONObject): Question {
        val operation = when (value.optString("operation")) {
            "SUBTRACT" -> Operation.SUBTRACT
            "MULTIPLY" -> Operation.MULTIPLY
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
        val role = roleForSocket() ?: return
        val info = OnlineMatchInfo(value.optString("matchId"), value.optLong("questionId"), role)
        mutableOnlineMatch.value = info
        mutableOnlineAnswerLocked.value = false
        onlineResult = null
        mutableState.value = onlineBattleState(onlineQuestion(value), info, value.optInt("playerHp", 100), value.optInt("opponentHp", 100), BattlePhase.ANSWERING)
    }

    private fun handleMatchQuestion(value: JSONObject) {
        val old = mutableOnlineMatch.value ?: return
        if (value.optString("matchId") != old.matchId || value.optLong("questionId") <= old.questionId) return
        val info = old.copy(questionId = value.optLong("questionId"))
        mutableOnlineMatch.value = info
        mutableOnlineAnswerLocked.value = false
        mutableState.value = onlineBattleState(onlineQuestion(value), info, mutableState.value.playerHp, mutableState.value.opponentHp, BattlePhase.ANSWERING)
    }

    private fun handleMatchAttack(value: JSONObject) {
        val info = mutableOnlineMatch.value ?: return
        if (value.optString("matchId") != info.matchId || value.optLong("questionId") != info.questionId || mutableState.value.phase != BattlePhase.ANSWERING) return
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
            if (mutableOnlineMatch.value != info) return@launch
            mutableState.value = onlineBattleState(question, info, hostHp, guestHp, BattlePhase.IMPACT, attacker)
            mutableFeedback.tryEmit(CombatFeedback(mutableState.value.key, FeedbackKind.HIT))
            delay(320)
            if (mutableOnlineMatch.value != info) return@launch
            if (value.optBoolean("ko")) {
                mutableState.value = onlineBattleState(question, info, hostHp, guestHp, BattlePhase.KO, attacker)
                mutableFeedback.tryEmit(CombatFeedback(mutableState.value.key, FeedbackKind.KO))
            }
        }
    }

    private fun handleMatchResult(value: JSONObject) {
        val info = mutableOnlineMatch.value ?: return
        if (value.optString("matchId") != info.matchId) return
        onlineResult = value.optString("winner")
        onlineResultJob?.cancel()
        onlineResultJob = viewModelScope.launch {
            delay(600)
            if (mutableOnlineMatch.value == info && onlineResult != null) {
                mutableState.value = mutableState.value.copy(
                    phase = BattlePhase.RESULT,
                    winner = if (onlineResult.equals(info.role, true)) Fighter.PLAYER else Fighter.BOT
                )
            }
        }
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
                leaveRoom()
                disposeSocket(ConnectionStatus.DISCONNECTED)
            }
        } else if (wantsConnection && socket == null) {
            reconnectAttempt = 0
            openSocket(serverUrl.value.trim().trimEnd('/'))
        }
    }

    private fun openSocket(url: String) {
        if (!connectionForeground) return
        val generation = ++connectionGeneration
        acknowledgementJob?.cancel()
        mutableConnectionStatus.value = ConnectionStatus.CONNECTING
        mutableConnectionMessage.value = "Connecting…"
        val options = IO.Options().apply {
            timeout = 5_000
            reconnection = false
        }
        val created = try { IO.socket(url, options) } catch (error: Exception) {
            mutableConnectionStatus.value = ConnectionStatus.ERROR
            mutableConnectionMessage.value = error.message ?: "Invalid server URL"
            return
        }
        socket = created
        created.on("room:state") { args ->
            mainHandler.post {
                if (generation != connectionGeneration || socket !== created ||
                    (!roomRequestPending && mutableRoom.value == null)) return@post
                if (args.isNotEmpty() && args[0] is JSONObject) updateRoom(args[0] as JSONObject)
            }
        }
        created.on("room:closed") { args ->
            mainHandler.post {
                if (generation != connectionGeneration || socket !== created) return@post
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
        created.on("match:ended") { args ->
            mainHandler.post {
                if (generation != connectionGeneration || socket !== created) return@post
                val message = if (args.firstOrNull() is JSONObject) (args[0] as JSONObject).optString("message") else "Opponent disconnected"
                onlineResolutionJob?.cancel()
                onlineResultJob?.cancel()
                mutableOnlineMatch.value = null
                mutableOnlineAnswerLocked.value = false
                mutableState.value = BattleState(phase = BattlePhase.HOME, battleId = mutableState.value.battleId + 1)
                mutableRoomError.value = message.ifBlank { "Opponent disconnected" }
            }
        }
        created.on(Socket.EVENT_CONNECT) {
            mainHandler.post {
                if (generation != connectionGeneration || socket !== created) return@post
                mutableConnectionStatus.value = ConnectionStatus.CONNECTED
                mutableConnectionMessage.value = "Connected"
                acknowledgementJob?.cancel()
                acknowledgementJob = viewModelScope.launch {
                    delay(3_000L)
                    if (generation == connectionGeneration && socket === created &&
                        mutableConnectionStatus.value == ConnectionStatus.CONNECTED) {
                        mutableConnectionStatus.value = ConnectionStatus.ERROR
                        mutableConnectionMessage.value = "Server acknowledgement timed out"
                    }
                }
                created.emit("connection:check", io.socket.client.Ack { args ->
                    mainHandler.post {
                        if (generation != connectionGeneration || socket !== created) return@post
                        acknowledgementJob?.cancel()
                        if (args.isNotEmpty() && args[0] is JSONObject) {
                            mutableConnectionMessage.value = (args[0] as JSONObject).optString("message", "Connected")
                        } else {
                            mutableConnectionMessage.value = "Math Fight server ready"
                        }
                    }
                })
            }
        }
        created.on(Socket.EVENT_CONNECT_ERROR) {
            mainHandler.post {
                if (generation != connectionGeneration || socket !== created) return@post
                socket = null
                clearRoomFromServer()
                created.off()
                created.disconnect()
                mutableConnectionStatus.value = ConnectionStatus.ERROR
                mutableConnectionMessage.value = "Unable to connect"
                scheduleReconnect(generation)
            }
        }
        created.on(Socket.EVENT_DISCONNECT) {
            mainHandler.post {
                if (generation != connectionGeneration || socket !== created) return@post
                socket = null
                clearRoomFromServer()
                mutableConnectionStatus.value = ConnectionStatus.DISCONNECTED
                mutableConnectionMessage.value = "Disconnected"
                scheduleReconnect(generation)
            }
        }
        created.connect()
    }

    private fun scheduleReconnect(generation: Long) {
        if (!wantsConnection || !connectionForeground || generation != connectionGeneration || reconnectAttempt >= 3) return
        reconnectJob?.cancel()
        val delayMs = 1_000L shl reconnectAttempt++
        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            if (wantsConnection && connectionForeground && generation == connectionGeneration && socket == null) {
                openSocket(serverUrl.value.trim().trimEnd('/'))
            }
        }
    }

    private fun disposeSocket(status: ConnectionStatus) {
        connectionGeneration++
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
                    if (key == null || mutableOnlineMatch.value != null) return@collectLatest
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
    fun digit(value: Int) {
        if (onlineMatch.value != null) mutableState.value = enterDigit(mutableState.value, value) else edit { enterDigit(it, value) }
    }
    fun backspace() {
        if (onlineMatch.value != null) mutableState.value = eraseDigit(mutableState.value) else edit(::eraseDigit)
    }
    fun clear() {
        if (onlineMatch.value != null) mutableState.value = clearInput(mutableState.value) else edit(::clearInput)
    }
    fun submit() {
        if (onlineMatch.value != null) submitOnlineAnswer() else edit(::submitAnswer)
    }
    fun returnHome() {
        if (onlineMatch.value != null) {
            if (state.value.phase != BattlePhase.RESULT) leaveRoom() else leaveOnlineLobby()
        } else edit { BattleState(battleId = it.battleId + 1) }
    }

    override fun onCleared() {
        cancelBot()
        wantsConnection = false
        reconnectJob?.cancel()
        leaveRoom()
        disposeSocket(ConnectionStatus.IDLE)
        super.onCleared()
    }
}
