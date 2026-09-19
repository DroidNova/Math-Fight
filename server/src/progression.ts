// PostgreSQL integer XP stays exact in JavaScript and Kotlin Long.
export function progression(totalXp: number) {
  const level = Math.floor((1 + Math.sqrt(1 + totalXp / 25)) / 2);
  const currentLevelStartXp = 100 * level * (level - 1);
  const nextLevelXp = 100 * level * (level + 1);
  return { totalXp, level, currentLevelStartXp, nextLevelXp,
    xpIntoCurrentLevel: totalXp - currentLevelStartXp,
    xpRequiredForNextLevel: 200 * level };
}

export function progressionResult(previousTotalXp: number, newTotalXp: number, xpAwarded: number) {
  const previous = progression(previousTotalXp);
  const next = progression(newTotalXp);
  return { ...next, xpAwarded, previousTotalXp, newTotalXp,
    previousLevel: previous.level, newLevel: next.level,
    previousXpIntoCurrentLevel: previous.xpIntoCurrentLevel,
    previousXpRequiredForNextLevel: previous.xpRequiredForNextLevel };
}
