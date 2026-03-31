// difficultyService.js - Progressive difficulty pricing

export const MAX_DIFFICULTY_LEVEL = 5;

export const DIFFICULTY_COSTS = {
    2: 15,
    3: 20,
    4: 25,
    5: 30,
};

export function getDifficultyCost(level) {
    return DIFFICULTY_COSTS[level] || null;
}
