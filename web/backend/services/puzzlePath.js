// services/puzzlePath.js - Puzzle path management and diagnostics
import { supabase } from "../config/database.js";

// Monitor path insertion failures
let pathFailureCount = 0;
let pathFailureLog = [];

/**
 * Log path failure for monitoring
 */
export function logPathFailure(puzzleId, puzzleType, difficulty, error) {
    pathFailureCount++;
    pathFailureLog.push({
        puzzleId,
        puzzleType,
        difficulty,
        error,
        timestamp: new Date().toISOString()
    });

    if (pathFailureLog.length > 100) {
        pathFailureLog = pathFailureLog.slice(-100);
    }

    if (pathFailureCount % 10 === 0) {
        console.warn(`⚠️ Path failure alert: ${pathFailureCount} total failures`);
        console.log(`Recent failures:`, pathFailureLog.slice(-5));
    }
}

/**
 * Get path failure statistics
 */
export function getPathFailureStats() {
    return {
        totalFailures: pathFailureCount,
        recentFailures: pathFailureLog.slice(-10)
    };
}

/**
 * Diagnose puzzle path state for a type/difficulty
 */
export async function diagnosePuzzlePathState(puzzleType, difficulty) {
    console.log(`🔍 Diagnosing puzzle path state for ${puzzleType}/${difficulty}`);

    try {
        const { data: allPuzzles, error: puzzleError } = await supabase
            .from('puzzles')
            .select('puzzleid, timestamp')
            .eq('type', puzzleType.toLowerCase())
            .eq('difficulty', difficulty.toLowerCase())
            .order('timestamp', { ascending: true });

        if (puzzleError) {
            return { error: `Failed to query puzzles: ${puzzleError.message}` };
        }

        const { data: pathEntries, error: pathError } = await supabase
            .from('puzzle_path')
            .select('*')
            .eq('type', puzzleType)
            .eq('difficulty', difficulty.toLowerCase());

        if (pathError) {
            return { error: `Failed to query path: ${pathError.message}` };
        }

        const totalPuzzles = allPuzzles?.length || 0;
        const totalPathEntries = pathEntries?.length || 0;

        const issues = [];
        let needsRepair = false;

        const puzzleIds = new Set(allPuzzles?.map(p => p.puzzleid) || []);
        const pathPuzzleIds = new Set(pathEntries?.map(p => p.puzzleid) || []);
        const missingFromPath = [...puzzleIds].filter(id => !pathPuzzleIds.has(id));

        if (missingFromPath.length > 0) {
            issues.push(`${missingFromPath.length} puzzles missing from path`);
            needsRepair = true;
        }

        const orphanedPaths = [...pathPuzzleIds].filter(id => !puzzleIds.has(id));
        if (orphanedPaths.length > 0) {
            issues.push(`${orphanedPaths.length} orphaned path entries`);
            needsRepair = true;
        }

        const endNodes = pathEntries?.filter(p => p.nextpuzzleid === null) || [];
        if (endNodes.length !== 1 && totalPathEntries > 0) {
            issues.push(`${endNodes.length} end nodes (should be 1)`);
            needsRepair = true;
        }

        const brokenChains = [];
        if (pathEntries) {
            for (const entry of pathEntries) {
                if (entry.nextpuzzleid && !pathPuzzleIds.has(entry.nextpuzzleid)) {
                    brokenChains.push(entry.puzzleid);
                }
            }
        }
        if (brokenChains.length > 0) {
            issues.push(`${brokenChains.length} broken chain links`);
            needsRepair = true;
        }

        return {
            totalPuzzles,
            totalPathEntries,
            missingFromPath: missingFromPath.length,
            orphanedPaths: orphanedPaths.length,
            endNodes: endNodes.length,
            brokenChains: brokenChains.length,
            issues,
            needsRepair,
            isHealthy: !needsRepair && totalPuzzles === totalPathEntries && totalPuzzles > 0
        };

    } catch (error) {
        return { error: `Diagnosis failed: ${error.message}` };
    }
}

/**
 * Find the end of the puzzle path for a type/difficulty
 */
export async function findPathEnd(puzzleType, difficulty) {
    const { data: endNode, error } = await supabase
        .from('puzzle_path')
        .select('puzzleid')
        .eq('type', puzzleType)
        .eq('difficulty', difficulty.toLowerCase())
        .is('nextpuzzleid', null)
        .limit(1);

    if (error) {
        console.error('Error finding path end:', error);
        return null;
    }

    return endNode?.[0]?.puzzleid || null;
}

/**
 * Insert a puzzle into the path
 */
export async function insertIntoPuzzlePath(puzzleId, puzzleType, difficulty) {
    try {
        const currentEnd = await findPathEnd(puzzleType, difficulty);

        if (currentEnd) {
            // Update the current end to point to the new puzzle
            const { error: updateError } = await supabase
                .from('puzzle_path')
                .update({ nextpuzzleid: puzzleId })
                .eq('puzzleid', currentEnd)
                .eq('type', puzzleType)
                .eq('difficulty', difficulty.toLowerCase());

            if (updateError) {
                logPathFailure(puzzleId, puzzleType, difficulty, updateError.message);
                return { success: false, error: updateError.message };
            }
        }

        // Insert the new puzzle as the new end
        const { error: insertError } = await supabase
            .from('puzzle_path')
            .insert({
                puzzleid: puzzleId,
                type: puzzleType,
                difficulty: difficulty.toLowerCase(),
                nextpuzzleid: null
            });

        if (insertError) {
            logPathFailure(puzzleId, puzzleType, difficulty, insertError.message);
            return { success: false, error: insertError.message };
        }

        return { success: true };

    } catch (error) {
        logPathFailure(puzzleId, puzzleType, difficulty, error.message);
        return { success: false, error: error.message };
    }
}
