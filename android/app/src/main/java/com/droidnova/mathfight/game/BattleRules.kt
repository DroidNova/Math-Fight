package com.droidnova.mathfight.game

import kotlin.random.Random

const val STARTING_HP = 100
const val HIT_DAMAGE = 20

enum class Operation(val symbol: String) {
    ADD("+"), SUBTRACT("−"), MULTIPLY("×")
}

data class Question(val left: Int, val operation: Operation, val right: Int) {
    val answer: Int
        get() = when (operation) {
            Operation.ADD -> left + right
            Operation.SUBTRACT -> left - right
            Operation.MULTIPLY -> left * right
        }

    val display: String
        get() = "$left ${operation.symbol} $right = ?"
}

fun generateQuestion(previous: Question? = null, random: Random = Random.Default): Question {
    repeat(8) {
        val operation = Operation.entries.random(random)
        val candidate = when (operation) {
            Operation.ADD -> Question(random.nextInt(0, 21), operation, random.nextInt(0, 21))
            Operation.SUBTRACT -> {
                val first = random.nextInt(0, 21)
                val second = random.nextInt(0, 21)
                Question(maxOf(first, second), operation, minOf(first, second))
            }
            Operation.MULTIPLY -> Question(random.nextInt(1, 11), operation, random.nextInt(1, 11))
        }
        if (candidate != previous) return candidate
    }

    val fallback = Question(0, Operation.ADD, 1)
    return if (fallback != previous) fallback else Question(1, Operation.ADD, 1)
}

enum class BattlePhase { HOME, ANSWERING, WINDUP, IMPACT, KO, VICTORY }

data class PhaseKey(val battleId: Long, val questionId: Long, val phase: BattlePhase)

data class BattleState(
    val phase: BattlePhase = BattlePhase.HOME,
    val playerHp: Int = STARTING_HP,
    val opponentHp: Int = STARTING_HP,
    val question: Question? = null,
    val input: String = "",
    val wrongAnswer: Boolean = false,
    val battleId: Long = 0,
    val questionId: Long = 0
) {
    val key: PhaseKey
        get() = PhaseKey(battleId, questionId, phase)
}

fun newBattle(previous: BattleState) = BattleState(
    phase = BattlePhase.ANSWERING,
    question = generateQuestion(previous.question),
    battleId = previous.battleId + 1,
    questionId = 1
)

fun enterDigit(state: BattleState, digit: Int): BattleState {
    if (state.phase != BattlePhase.ANSWERING || digit !in 0..9) return state
    val next = when {
        state.input == "0" -> digit.toString()
        state.input.length < 3 -> state.input + digit
        else -> state.input
    }
    return state.copy(input = next, wrongAnswer = false)
}

fun eraseDigit(state: BattleState) = if (state.phase == BattlePhase.ANSWERING) {
    state.copy(input = state.input.dropLast(1), wrongAnswer = false)
} else state

fun clearInput(state: BattleState) = if (state.phase == BattlePhase.ANSWERING) {
    state.copy(input = "", wrongAnswer = false)
} else state

fun submitAnswer(state: BattleState): BattleState {
    if (state.phase != BattlePhase.ANSWERING || state.input.isEmpty()) return state
    val question = state.question ?: return state
    return if (state.input.toIntOrNull() == question.answer) {
        state.copy(phase = BattlePhase.WINDUP, input = "", wrongAnswer = false)
    } else {
        state.copy(input = "", wrongAnswer = true)
    }
}

fun advancePhase(state: BattleState, expected: PhaseKey): BattleState {
    if (state.key != expected) return state
    return when (state.phase) {
        BattlePhase.WINDUP -> state.copy(
            phase = BattlePhase.IMPACT,
            opponentHp = (state.opponentHp - HIT_DAMAGE).coerceAtLeast(0)
        )
        BattlePhase.IMPACT -> if (state.opponentHp == 0) {
            state.copy(phase = BattlePhase.KO)
        } else {
            state.copy(
                phase = BattlePhase.ANSWERING,
                question = generateQuestion(state.question),
                questionId = state.questionId + 1,
                input = "",
                wrongAnswer = false
            )
        }
        BattlePhase.KO -> state.copy(phase = BattlePhase.VICTORY)
        else -> state
    }
}

fun BattlePhase.durationMillis(): Long? = when (this) {
    BattlePhase.WINDUP -> 180L
    BattlePhase.IMPACT -> 320L
    BattlePhase.KO -> 600L
    else -> null
}
