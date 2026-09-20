package com.droidnova.mathfight.ui.battle.arena

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Presentation-side fighter identity. It deliberately has no gameplay meaning. */
enum class ArenaSide {
    LEFT,
    RIGHT;

    val opposite: ArenaSide
        get() = if (this == LEFT) RIGHT else LEFT
}

enum class ArenaEventKind { ATTACK, HIT, KO, VICTORY }

enum class ArenaPresentationCue {
    MELEE_SWING,
    ENERGY_CHARGE,
    PROJECTILE_LAUNCH,
    IMPACT,
    HIT_REACTION,
    KO_POWER_DOWN,
    VICTORY
}

/** Existing battle phase identity translated into a renderer-only value. */
data class ArenaEventId(
    val battleId: Long,
    val questionId: Long,
    val kind: ArenaEventKind
)

data class ArenaPresentationEvent(
    val eventId: ArenaEventId,
    val cue: ArenaPresentationCue
)

data class ArenaSnapshot(
    val battleId: Long,
    val leftHp: Int,
    val rightHp: Int,
    val settled: Boolean,
    val paused: Boolean,
    val questionId: Long,
    val winner: ArenaSide?
)

internal sealed interface ArenaCommand {
    data class Synchronize(val snapshot: ArenaSnapshot) : ArenaCommand
    data class Attack(val side: ArenaSide, val eventId: ArenaEventId) : ArenaCommand
    data class Hit(val side: ArenaSide, val eventId: ArenaEventId) : ArenaCommand
    data class Ko(val side: ArenaSide, val eventId: ArenaEventId) : ArenaCommand
    data class Victory(val side: ArenaSide, val eventId: ArenaEventId) : ArenaCommand
    data class PrepareExit(val token: Long) : ArenaCommand
}

/**
 * Bounded UI-to-render-thread mailbox. Android/Compose threads only enqueue immutable commands;
 * the libGDX render thread is the sole consumer and the sole owner of visual collections.
 */
class ArenaCommandBridge {
    private data class PendingCommand(val session: Long, val command: ArenaCommand)
    private data class SessionEvent(val session: Long, val id: ArenaEventId)
    private data class SessionPresentation(
        val session: Long,
        val eventId: ArenaEventId,
        val cue: ArenaPresentationCue
    )
    private data class RenderAttachment(val session: Long, val owner: Any, val requester: () -> Unit)
    private data class ExitPreparation(val session: Long, val token: Long, val onPrepared: () -> Unit)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = ConcurrentLinkedQueue<PendingCommand>()
    private val enqueuedEvents = ConcurrentHashMap.newKeySet<SessionEvent>()
    private val emittedPresentation = ConcurrentHashMap.newKeySet<SessionPresentation>()
    private val nextSession = AtomicLong(0L)
    private val activeSession = AtomicLong(NO_SESSION)
    private val renderAttachment = AtomicReference<RenderAttachment?>(null)
    private val presentationListener = AtomicReference<((ArenaPresentationEvent) -> Unit)?>(null)
    private val exitPreparation = AtomicReference<ExitPreparation?>(null)
    private val nextExitToken = AtomicLong(0L)
    private val acceptingCommands = AtomicBoolean(false)
    private val disposed = AtomicBoolean(false)

    fun beginSession(): Long {
        if (disposed.get()) return NO_SESSION
        val session = nextSession.incrementAndGet()
        pending.clear()
        enqueuedEvents.clear()
        emittedPresentation.clear()
        renderAttachment.set(null)
        activeSession.set(session)
        acceptingCommands.set(true)
        if (disposed.get()) {
            activeSession.compareAndSet(session, NO_SESSION)
            return NO_SESSION
        }
        return session
    }

    fun endSession(session: Long) {
        if (!activeSession.compareAndSet(session, NO_SESSION)) return
        pending.clear()
        enqueuedEvents.clear()
        emittedPresentation.clear()
        renderAttachment.getAndSet(null)
        exitPreparation.set(null)
        acceptingCommands.set(false)
    }

    fun resumeCommands(session: Long) {
        if (disposed.get() || activeSession.get() != session) return
        enqueuedEvents.clear()
        exitPreparation.set(null)
        acceptingCommands.set(true)
    }

    /** Activity-owned callback. Events are always delivered on Android's main thread. */
    fun setPresentationListener(listener: ((ArenaPresentationEvent) -> Unit)?) {
        presentationListener.set(listener)
    }

    fun canPresentFeedback(session: Long): Boolean =
        !disposed.get() && acceptingCommands.get() && activeSession.get() == session &&
            renderAttachment.get()?.session == session && presentationListener.get() != null

    fun prepareExit(session: Long, onPrepared: () -> Unit) {
        if (disposed.get() || activeSession.get() != session) {
            mainHandler.post { onPrepared() }
            return
        }
        val token = nextExitToken.incrementAndGet()
        val preparation = ExitPreparation(session, token, onPrepared)
        if (!exitPreparation.compareAndSet(null, preparation)) return
        acceptingCommands.set(false)
        pending.clear()
        addBounded(PendingCommand(session, ArenaCommand.PrepareExit(token)))
        val attachment = renderAttachment.get()
        if (attachment?.session == session) {
            attachment.requester.invoke()
        } else {
            acknowledgeExitPrepared(session, token)
        }
    }

    internal fun acknowledgeExitPrepared(session: Long, token: Long) {
        val preparation = exitPreparation.get() ?: return
        if (preparation.session != session || preparation.token != token ||
            !exitPreparation.compareAndSet(preparation, null)) return
        mainHandler.post { preparation.onPrepared() }
    }

    internal fun emitPresentation(
        session: Long,
        eventId: ArenaEventId,
        cue: ArenaPresentationCue
    ) {
        if (disposed.get() || activeSession.get() != session) return
        val key = SessionPresentation(session, eventId, cue)
        if (!emittedPresentation.add(key)) return
        trimPresentationEvents()
        mainHandler.post {
            if (!disposed.get() && activeSession.get() == session) {
                presentationListener.get()?.invoke(ArenaPresentationEvent(eventId, cue))
            }
        }
    }

    fun synchronize(session: Long, snapshot: ArenaSnapshot) {
        enqueue(session, ArenaCommand.Synchronize(snapshot))
    }

    fun playAttack(session: Long, side: ArenaSide, eventId: ArenaEventId) {
        enqueueEvent(session, eventId, ArenaCommand.Attack(side, eventId))
    }

    fun playHit(session: Long, side: ArenaSide, eventId: ArenaEventId) {
        enqueueEvent(session, eventId, ArenaCommand.Hit(side, eventId))
    }

    fun playKo(session: Long, side: ArenaSide, eventId: ArenaEventId) {
        enqueueEvent(session, eventId, ArenaCommand.Ko(side, eventId))
    }

    fun playVictory(session: Long, side: ArenaSide, eventId: ArenaEventId) {
        enqueueEvent(session, eventId, ArenaCommand.Victory(side, eventId))
    }

    internal fun attach(
        session: Long,
        owner: Any,
        requester: () -> Unit
    ) {
        if (disposed.get() || activeSession.get() != session) return
        val next = RenderAttachment(session, owner, requester)
        renderAttachment.set(next)
        if (disposed.get() || activeSession.get() != session) {
            renderAttachment.compareAndSet(next, null)
            return
        }
        if (pending.any { it.session == session }) requester()
    }

    internal fun detach(owner: Any) {
        while (true) {
            val current = renderAttachment.get() ?: return
            if (current.owner !== owner) return
            if (renderAttachment.compareAndSet(current, null)) return
        }
    }

    internal fun drain(session: Long): List<ArenaCommand> {
        if (disposed.get() || activeSession.get() != session) return emptyList()
        if (pending.isEmpty()) return emptyList()
        val commands = ArrayList<ArenaCommand>()
        while (activeSession.get() == session) {
            val next = pending.poll() ?: break
            if (next.session == session) commands.add(next.command)
        }
        return if (activeSession.get() == session) commands else emptyList()
    }

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        activeSession.set(NO_SESSION)
        acceptingCommands.set(false)
        pending.clear()
        enqueuedEvents.clear()
        emittedPresentation.clear()
        renderAttachment.set(null)
        presentationListener.set(null)
        exitPreparation.set(null)
    }

    private fun enqueueEvent(session: Long, id: ArenaEventId, command: ArenaCommand) {
        if (disposed.get() || !acceptingCommands.get() || activeSession.get() != session ||
            !enqueuedEvents.add(SessionEvent(session, id))) return
        trimEventIds()
        enqueue(session, command)
    }

    private fun enqueue(session: Long, command: ArenaCommand) {
        if (disposed.get() || !acceptingCommands.get() || activeSession.get() != session) return
        addBounded(PendingCommand(session, command))
        renderAttachment.get()
            ?.takeIf { it.session == session && activeSession.get() == session }
            ?.requester
            ?.invoke()
    }

    private fun addBounded(command: PendingCommand) {
        pending.add(command)
        while (pending.size > MAX_PENDING_COMMANDS) pending.poll()
    }

    private fun trimEventIds() {
        while (enqueuedEvents.size > MAX_EVENT_IDS) {
            val oldest = enqueuedEvents.firstOrNull() ?: return
            enqueuedEvents.remove(oldest)
        }
    }

    private fun trimPresentationEvents() {
        while (emittedPresentation.size > MAX_PRESENTATION_EVENTS) {
            val oldest = emittedPresentation.firstOrNull() ?: return
            emittedPresentation.remove(oldest)
        }
    }

    companion object {
        const val NO_SESSION = -1L
        private const val MAX_PENDING_COMMANDS = 128
        private const val MAX_EVENT_IDS = 96
        private const val MAX_PRESENTATION_EVENTS = 192
    }
}

interface ArenaBridgeProvider {
    val arenaCommandBridge: ArenaCommandBridge
}
