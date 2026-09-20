package com.droidnova.mathfight.profile

import org.json.JSONObject

// All progression values come from the server; Android only renders them.
data class PlayerProgression(
    val totalXp: Long, val level: Int, val currentLevelStartXp: Long, val nextLevelXp: Long,
    val xpIntoCurrentLevel: Long, val xpRequiredForNextLevel: Long
) {
    val fraction: Float get() = (xpIntoCurrentLevel.toDouble() / xpRequiredForNextLevel).toFloat().coerceIn(0f, 1f)
}

data class XpResult(
    val matchId: String, val xpAwarded: Long, val previousTotalXp: Long, val newTotalXp: Long,
    val previousLevel: Int, val newLevel: Int, val previousFraction: Float,
    val progression: PlayerProgression
)

fun parseProgression(value: JSONObject): PlayerProgression? {
    val total = value.optLong("totalXp", -1)
    val level = value.optInt("level", 0)
    val start = value.optLong("currentLevelStartXp", -1)
    val next = value.optLong("nextLevelXp", -1)
    val into = value.optLong("xpIntoCurrentLevel", -1)
    val required = value.optLong("xpRequiredForNextLevel", -1)
    if (total < 0 || level < 1 || start < 0 || next <= start || into < 0 || required <= 0) return null
    return PlayerProgression(total, level, start, next, into, required)
}

fun parseXpResult(matchId: String, value: JSONObject): XpResult? {
    val progression = parseProgression(value) ?: return null
    val previous = value.optLong("previousTotalXp", -1)
    val awarded = value.optLong("xpAwarded", -1)
    val newTotal = value.optLong("newTotalXp", -1)
    val previousLevel = value.optInt("previousLevel", 0)
    val newLevel = value.optInt("newLevel", 0)
    val required = value.optLong("previousXpRequiredForNextLevel", 0)
    val into = value.optLong("previousXpIntoCurrentLevel", -1)
    if (previous < 0 || awarded < 0 || newTotal < previous || previousLevel < 1 || newLevel < previousLevel || required <= 0 || into < 0) return null
    return XpResult(matchId, awarded, previous, newTotal, previousLevel, newLevel,
        (into.toDouble() / required).toFloat().coerceIn(0f, 1f), progression)
}
