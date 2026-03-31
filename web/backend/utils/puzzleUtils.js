/**
 * Puzzle utility functions
 */

/**
 * Normalize difficulty to lowercase standard values
 * @param {string} difficulty - Input difficulty
 * @returns {string} - Normalized difficulty (easy, medium, hard, mixed)
 */
export function normalizeDifficulty(difficulty) {
    if (!difficulty) return 'easy';

    const normalized = difficulty.toLowerCase();
    switch (normalized) {
        case 'easy': return 'easy';
        case 'medium': return 'medium';
        case 'hard': return 'hard';
        case 'mixed': return 'mixed';
        default: return 'easy';
    }
}

/**
 * Extract topic from a prompt string
 * @param {string} prompt - The prompt to extract from
 * @returns {string} - Extracted topic or default
 */
export function extractTopicFromPrompt(prompt) {
    const match = prompt.match(/about\s+(?:the\s+)?(\w+(?:\s+\w+)*)/i);
    return match ? match[1] : 'science';
}

/**
 * Determine category from a topic
 * @param {string} topic - The topic to categorize
 * @returns {string} - The category
 */
export function determineCategory(topic) {
    const categoryMap = {
        'periodic table': 'nature',
        'solar system': 'space',
        'animals': 'wildlife',
        'dinosaurs': 'wildlife',
        'landmarks': 'landmarks',
        'countries': 'landmarks',
        'instruments': 'instruments'
    };

    for (const [key, category] of Object.entries(categoryMap)) {
        if (topic.toLowerCase().includes(key)) {
            return category;
        }
    }

    return 'nature';
}

/**
 * Format time in milliseconds to human-readable string
 * @param {number} timeInMs - Time in milliseconds
 * @returns {string} - Formatted time string
 */
export function formatTime(timeInMs) {
    if (!timeInMs || timeInMs < 0) return "0s";

    const seconds = Math.floor(timeInMs / 1000);
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;

    if (minutes > 0) {
        return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`;
    } else {
        return `${remainingSeconds}s`;
    }
}
