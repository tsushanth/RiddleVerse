// services/puzzleLeaderboard.js - Puzzle leaderboard management
import { supabase } from "../config/database.js";
import { admin } from "../config/firebaseAdmin.js";

/**
 * Mask email address for privacy
 * Converts "john.doe@gmail.com" to "john***"
 * Or generates a friendly anonymous name if email parsing fails
 */
export function maskEmail(email) {
    if (!email) return 'Anonymous';

    // Check if it's an email address
    if (email.includes('@')) {
        const [localPart] = email.split('@');
        if (localPart.length <= 3) {
            // Very short local part - show first char + stars
            return localPart.charAt(0) + '***';
        }
        // Show first 4 characters + stars
        return localPart.substring(0, Math.min(4, localPart.length)) + '***';
    }

    // If not an email (already a username), return as-is but truncated
    if (email.length > 15) {
        return email.substring(0, 12) + '...';
    }
    return email;
}

/**
 * Format time in seconds to human readable format
 */
export function formatTime(seconds) {
    if (seconds < 60) {
        return `${Math.round(seconds)}s`;
    } else if (seconds < 3600) {
        const mins = Math.floor(seconds / 60);
        const secs = Math.round(seconds % 60);
        return `${mins}m ${secs}s`;
    } else {
        const hours = Math.floor(seconds / 3600);
        const mins = Math.floor((seconds % 3600) / 60);
        return `${hours}h ${mins}m`;
    }
}

/**
 * Get puzzle leaderboard
 */
export async function getPuzzleLeaderboard(puzzleId, userId = null) {
    try {
        // Get puzzle set info
        const { data: puzzleSet, error: puzzleError } = await supabase
            .from('puzzle_sets')
            .select('name, creator')
            .eq('id', puzzleId)
            .single();

        if (puzzleError && puzzleError.code !== 'PGRST116') {
            console.error('Error fetching puzzle set:', puzzleError);
        }

        // Get leaderboard entries
        const { data: leaderboard, error: leaderboardError } = await supabase
            .from('custom_leaderboard')
            .select('*')
            .eq('parentsetid', puzzleId)
            .order('score', { ascending: false })
            .order('timetaken', { ascending: true })
            .limit(100);

        if (leaderboardError) {
            return { success: false, error: leaderboardError.message };
        }

        if (!leaderboard || leaderboard.length === 0) {
            return {
                success: true,
                puzzleId,
                puzzleName: puzzleSet?.name || 'Unknown Puzzle',
                puzzleCreator: puzzleSet?.creator || 'Unknown',
                topPlayers: [],
                totalPlayers: 0,
                isEmpty: true
            };
        }

        // Resolve display names from Firebase Auth
        const userIds = leaderboard.map(e => e.userid).filter(Boolean);
        const displayNameMap = {};
        if (userIds.length > 0 && admin) {
            try {
                const result = await admin.auth().getUsers(userIds.map(uid => ({ uid })));
                result.users.forEach(user => {
                    displayNameMap[user.uid] = user.displayName || maskEmail(user.email || user.uid);
                });
            } catch (e) {
                console.warn('Could not resolve Firebase display names:', e.message);
            }
        }

        // Format leaderboard entries with resolved display names
        const topPlayers = leaderboard.map((entry, index) => ({
            rank: index + 1,
            odisplay_name: displayNameMap[entry.userid] || maskEmail(entry.userid),
            playerName: displayNameMap[entry.userid] || maskEmail(entry.userid),
            userId: entry.userid, // Keep original for matching current user
            score: entry.score,
            timeTaken: entry.timetaken,
            formattedTime: formatTime(entry.timetaken),
            completedAt: entry.createdat
        }));

        // Calculate statistics
        const scores = leaderboard.map(e => e.score);
        const times = leaderboard.map(e => e.timetaken);
        const statistics = {
            averageScore: Math.round(scores.reduce((a, b) => a + b, 0) / scores.length),
            highestScore: Math.max(...scores),
            lowestScore: Math.min(...scores),
            averageTime: Math.round(times.reduce((a, b) => a + b, 0) / times.length),
            fastestTime: Math.min(...times),
            slowestTime: Math.max(...times)
        };

        // Get user's rank if userId provided
        let userRank = null;
        let userScore = null;
        let userTime = null;
        let userPercentile = null;

        if (userId) {
            const userEntry = leaderboard.find(e => e.userid === userId);
            if (userEntry) {
                userRank = topPlayers.find(p => p.userId === userId)?.rank;
                userScore = userEntry.score;
                userTime = userEntry.timetaken;
                userPercentile = Math.round((1 - (userRank - 1) / leaderboard.length) * 100);
            }
        }

        return {
            success: true,
            puzzleId,
            puzzleName: puzzleSet?.name || 'Unknown Puzzle',
            puzzleCreator: puzzleSet?.creator || 'Unknown',
            topPlayers: topPlayers.slice(0, 10),
            userRank,
            userScore,
            userTime,
            userPercentile,
            totalPlayers: leaderboard.length,
            statistics,
            isEmpty: false
        };

    } catch (error) {
        console.error('Error in getPuzzleLeaderboard:', error);
        return { success: false, error: error.message };
    }
}

/**
 * Get featured puzzles (most played, highest rated)
 */
export async function getFeaturedPuzzles(limit = 5) {
    try {
        // Get puzzle sets with the most leaderboard entries
        const { data: popularPuzzles, error } = await supabase
            .from('custom_leaderboard')
            .select('parentsetid')
            .limit(1000);

        if (error) {
            return { success: false, error: error.message };
        }

        // Count entries per puzzle
        const puzzleCounts = {};
        popularPuzzles.forEach(entry => {
            puzzleCounts[entry.parentsetid] = (puzzleCounts[entry.parentsetid] || 0) + 1;
        });

        // Sort by count and get top puzzles
        const topPuzzleIds = Object.entries(puzzleCounts)
            .sort(([, a], [, b]) => b - a)
            .slice(0, limit)
            .map(([id]) => id);

        if (topPuzzleIds.length === 0) {
            return { success: true, featuredPuzzles: [], count: 0 };
        }

        // Get puzzle details
        const { data: puzzleSets, error: puzzleError } = await supabase
            .from('puzzle_sets')
            .select('*')
            .in('id', topPuzzleIds);

        if (puzzleError) {
            return { success: false, error: puzzleError.message };
        }

        const featuredPuzzles = puzzleSets.map(ps => ({
            ...ps,
            playerCount: puzzleCounts[ps.id] || 0
        })).sort((a, b) => b.playerCount - a.playerCount);

        return {
            success: true,
            featuredPuzzles,
            count: featuredPuzzles.length
        };

    } catch (error) {
        console.error('Error in getFeaturedPuzzles:', error);
        return { success: false, error: error.message };
    }
}

/**
 * Update puzzle leaderboard entry
 */
export async function updatePuzzleLeaderboard(puzzleId, odisplay_name, timeTaken, score) {
    try {
        // Check if entry exists
        const { data: existing, error: checkError } = await supabase
            .from('custom_leaderboard')
            .select('*')
            .eq('parentsetid', puzzleId)
            .eq('userid', odisplay_name)
            .limit(1);

        if (checkError) {
            return { success: false, error: checkError.message };
        }

        if (existing && existing.length > 0) {
            // Update only if new score is better
            const currentEntry = existing[0];
            const shouldUpdate = score > currentEntry.score ||
                (score === currentEntry.score && timeTaken < currentEntry.timetaken);

            if (shouldUpdate) {
                const { error: updateError } = await supabase
                    .from('custom_leaderboard')
                    .update({
                        score,
                        timetaken: timeTaken,
                        updatedat: new Date().toISOString()
                    })
                    .eq('parentsetid', puzzleId)
                    .eq('userid', odisplay_name);

                if (updateError) {
                    return { success: false, error: updateError.message };
                }
            }
        } else {
            // Insert new entry
            const { error: insertError } = await supabase
                .from('custom_leaderboard')
                .insert({
                    parentsetid: puzzleId,
                    userid: odisplay_name,
                    score,
                    timetaken: timeTaken,
                    createdat: new Date().toISOString()
                });

            if (insertError) {
                return { success: false, error: insertError.message };
            }
        }

        // Get updated leaderboard
        const leaderboard = await getPuzzleLeaderboard(puzzleId, odisplay_name);

        return {
            success: true,
            leaderboard: leaderboard.topPlayers,
            userStats: {
                rank: leaderboard.userRank,
                score: leaderboard.userScore,
                time: leaderboard.userTime,
                percentile: leaderboard.userPercentile
            },
            leaderboardStats: leaderboard.statistics,
            achievements: detectAchievements(leaderboard.userRank, score, timeTaken)
        };

    } catch (error) {
        console.error('Error in updatePuzzleLeaderboard:', error);
        return { success: false, error: error.message };
    }
}

/**
 * Detect achievements based on performance
 */
function detectAchievements(rank, score, timeTaken) {
    const achievements = [];

    if (rank === 1) {
        achievements.push({ type: 'first_place', message: 'You are #1!' });
    } else if (rank <= 3) {
        achievements.push({ type: 'top_three', message: 'Top 3 finish!' });
    } else if (rank <= 10) {
        achievements.push({ type: 'top_ten', message: 'Top 10 finish!' });
    }

    if (score >= 100) {
        achievements.push({ type: 'perfect_score', message: 'Perfect score!' });
    }

    if (timeTaken < 60) {
        achievements.push({ type: 'speed_demon', message: 'Completed in under a minute!' });
    }

    return achievements;
}

/**
 * Delete puzzle leaderboard entry
 */
export async function deletePuzzleLeaderboardEntry(puzzleId, odisplay_name) {
    try {
        const { error } = await supabase
            .from('custom_leaderboard')
            .delete()
            .eq('parentsetid', puzzleId)
            .eq('userid', odisplay_name);

        if (error) {
            return { success: false, error: error.message };
        }

        return { success: true };

    } catch (error) {
        console.error('Error in deletePuzzleLeaderboardEntry:', error);
        return { success: false, error: error.message };
    }
}
