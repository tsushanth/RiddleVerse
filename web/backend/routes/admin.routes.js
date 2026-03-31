import express from 'express';
import { adminLimiter } from '../config/middleware.js';
import { 
    generateCustomPuzzles,
    generateCustomAnagrams,
    generateCustomCrosswords
} from '../services/puzzleService.js';
import { regenerationService } from '../services/regenerationService.js';
import { PuzzleGenerator } from '../services/puzzleGeneration.js';
import { supabase } from '../config/database.js';

const router = express.Router();

// All admin routes use strict rate limiting
router.use(adminLimiter);

// Regenerate stuck puzzle sets
router.post('/api/regenerate-stuck-puzzle-sets', async (req, res) => {
    try {
        const { dryRun = true } = req.body;
        const defaultPuzzleCount = 10;

        console.log(`${dryRun ? 'Analyzing' : 'Regenerating'} stuck puzzle sets...`);

        // Find puzzle sets stuck in "generating" status
        const { data: stuckSets, error: queryError } = await supabase
            .from('puzzle_sets')
            .select('*')
            .eq('status', 'generating')
            .order('created_at', { ascending: true });

        if (queryError) {
            throw new Error(`Failed to query stuck puzzle sets: ${queryError.message}`);
        }

        if (!stuckSets || stuckSets.length === 0) {
            return res.json({
                success: true,
                message: 'No stuck puzzle sets found',
                stuckCount: 0,
                processed: 0
            });
        }

        console.log(`Found ${stuckSets.length} stuck puzzle sets`);

        const results = {
            stuckCount: stuckSets.length,
            processed: 0,
            successful: 0,
            failed: 0,
            details: []
        };

        if (dryRun) {
            // Just report what would be processed
            stuckSets.forEach(set => {
                const ageHours = Math.round((Date.now() - new Date(set.created_at).getTime()) / (60 * 60 * 1000));
                results.details.push({
                    puzzleSetId: set.id,
                    name: set.name,
                    format: set.format,
                    creator: set.creator,
                    ageHours: ageHours,
                    action: 'would_regenerate'
                });
            });

            return res.json({
                success: true,
                message: `Analysis complete: ${stuckSets.length} stuck puzzle sets found`,
                mode: 'analysis',
                results
            });
        }

        // Process each stuck set
        for (const stuckSet of stuckSets) {
            results.processed++;
            
            try {
                console.log(`Processing stuck set: ${stuckSet.id} (${stuckSet.name})`);

                // Delete any existing incomplete puzzles
                await supabase
                    .from('puzzles')
                    .delete()
                    .eq('parentSetId', stuckSet.id);

                // Kick off regeneration based on format with notifications
                let generationPromise;
                
                if (stuckSet.format?.toLowerCase().includes('anagram')) {
                    generationPromise = generateCustomAnagrams(
                        stuckSet.name, 
                        stuckSet.format, 
                        defaultPuzzleCount, 
                        stuckSet.creator, 
                        stuckSet.id,
                        true // skipValidation
                    );
                } else if (stuckSet.format?.toLowerCase().includes('crossword')) {
                    generationPromise = generateCustomCrosswords(
                        stuckSet.name, 
                        stuckSet.format, 
                        defaultPuzzleCount, 
                        stuckSet.creator, 
                        stuckSet.id,
                        true // skipValidation
                    );
                } else {
                    generationPromise = generateCustomPuzzles(
                        stuckSet.name, 
                        stuckSet.format, 
                        defaultPuzzleCount, 
                        stuckSet.creator, 
                        stuckSet.id,
                        true // skipValidation
                    );
                }

                // Fire off regeneration with comprehensive notification handling
                generationPromise
                    .then(async (generationResult) => {
                        if (generationResult?.puzzleId) {
                            console.log(`Successfully regenerated stuck set: ${stuckSet.id}`);
                            
                            try {
                                // Send success notification
                                await regenerationService.sendCompletionNotification(
                                    stuckSet.creator, 
                                    stuckSet.name, 
                                    defaultPuzzleCount
                                );
                                console.log(`Success notification sent for ${stuckSet.id}`);
                            } catch (notificationError) {
                                console.warn(`Failed to send success notification for ${stuckSet.id}:`, notificationError.message);
                            }
                        } else {
                            console.error(`Regeneration failed for ${stuckSet.id}: ${generationResult?.error || 'Unknown error'}`);
                            
                            // Update to failed status
                            await supabase
                                .from('puzzle_sets')
                                .update({ status: 'failed' })
                                .eq('id', stuckSet.id);

                            // Send failure notification
                            try {
                                await regenerationService.sendFailureNotification(
                                    stuckSet.creator,
                                    stuckSet.name,
                                    'Regeneration failed after being stuck in generating status'
                                );
                                console.log(`Failure notification sent for ${stuckSet.id}`);
                            } catch (notificationError) {
                                console.warn(`Failed to send failure notification for ${stuckSet.id}:`, notificationError.message);
                            }
                        }
                    })
                    .catch(async (error) => {
                        console.error(`Regeneration exception for ${stuckSet.id}:`, error);
                        
                        // Update to failed status
                        await supabase
                            .from('puzzle_sets')
                            .update({ status: 'failed' })
                            .eq('id', stuckSet.id);

                        // Send exception notification
                        try {
                            await regenerationService.sendFailureNotification(
                                stuckSet.creator,
                                stuckSet.name,
                                `Regeneration failed with error: ${error.message}`
                            );
                            console.log(`Exception notification sent for ${stuckSet.id}`);
                        } catch (notificationError) {
                            console.warn(`Failed to send exception notification for ${stuckSet.id}:`, notificationError.message);
                        }
                    });

                // Count as successful since we kicked off the regeneration
                results.successful++;
                results.details.push({
                    puzzleSetId: stuckSet.id,
                    name: stuckSet.name,
                    creator: stuckSet.creator,
                    status: 'regeneration_started',
                    puzzleCount: defaultPuzzleCount
                });

                console.log(`Kicked off regeneration for stuck set: ${stuckSet.id}`);

            } catch (error) {
                results.failed++;
                
                console.error(`Failed to start regeneration for ${stuckSet.id}:`, error);

                results.details.push({
                    puzzleSetId: stuckSet.id,
                    name: stuckSet.name,
                    creator: stuckSet.creator,
                    status: 'failed_to_start',
                    error: error.message
                });

                // Update puzzle set status to failed
                try {
                    await supabase
                        .from('puzzle_sets')
                        .update({ status: 'failed' })
                        .eq('id', stuckSet.id);
                } catch (updateError) {
                    console.error(`Failed to update failed status for ${stuckSet.id}:`, updateError);
                }
            }
        }

        const message = `Regeneration kickoff complete: Started regeneration for ${results.successful}/${results.processed} puzzle sets`;

        console.log(message);

        res.json({
            success: true,
            message,
            mode: 'regeneration',
            results,
            notifications: {
                enabled: true,
                successNotifications: `Will be sent to ${results.successful} users upon completion`,
                failureNotifications: 'Will be sent if any regenerations fail'
            },
            note: 'Regenerations are running in the background. Users will be notified of completion status.'
        });

    } catch (error) {
        console.error('Error in regenerate stuck puzzle sets:', error);
        res.status(500).json({
            success: false,
            error: error.message,
            message: 'Failed to process stuck puzzle sets'
        });
    }
});

// Cleanup duplicates
router.post('/api/cleanup-duplicates', async (req, res) => {
    try {
        const { 
            dryRun = true,
            puzzleType = null,
            maxDuplicatesToRemove = 1000
        } = req.body;
        
        console.log(`${dryRun ? 'Analyzing' : 'Cleaning up'} duplicate puzzles...`);
        
        const results = {
            totalProcessed: 0,
            duplicatesFound: 0,
            duplicatesRemoved: 0,
            errors: [],
            details: []
        };
        
        // Get all puzzle types to process
        const puzzleTypes = puzzleType ? [puzzleType] : [
            'mathestimation', 'mathtipping', 'subtraction', 'purchasing',
            'percentages', 'division', 'discounts', 'conversion', 'average',
            'antonyms', 'connotationwords', 'wordassociation', 'anagram', 'trivia'
        ];
        
        for (const type of puzzleTypes) {
            console.log(`Processing ${type} puzzles...`);

            try {
                // Get all puzzles of this type (case-insensitive, higher limit)
                const { data: puzzles, error } = await supabase
                    .from('puzzles')
                    .select('*')
                    .ilike('type', type)
                    .order('timestamp', { ascending: true })
                    .limit(1000);
                
                if (error) {
                    results.errors.push(`Failed to fetch ${type} puzzles: ${error.message}`);
                    continue;
                }
                
                if (!puzzles || puzzles.length === 0) {
                    console.log(`No ${type} puzzles found`);
                    continue;
                }
                
                results.totalProcessed += puzzles.length;
                
                // Simple duplicate detection
                const seenQuestions = new Map();
                const duplicatesToRemove = [];
                
                for (const puzzle of puzzles) {
                    const normalizedQuestion = puzzle.question
                        .trim()
                        .toLowerCase()
                        .replace(/[^\w\s]/g, '')
                        .replace(/\s+/g, ' ');
                    
                    if (seenQuestions.has(normalizedQuestion)) {
                        duplicatesToRemove.push(puzzle);
                        results.duplicatesFound++;
                    } else {
                        seenQuestions.set(normalizedQuestion, puzzle);
                    }
                }
                
                console.log(`Found ${duplicatesToRemove.length} duplicates in ${type}`);
                
                // Remove duplicates if not dry run
                if (!dryRun && duplicatesToRemove.length > 0) {
                    const toRemove = duplicatesToRemove.slice(0, maxDuplicatesToRemove);

                    for (const duplicate of toRemove) {
                        try {
                            const puzzleId = duplicate.puzzleid || duplicate.id;

                            // Step 1: Get the puzzle_path entry for this puzzle to find its nextpuzzleid
                            const { data: pathEntry } = await supabase
                                .from('puzzle_path')
                                .select('nextpuzzleid')
                                .eq('puzzleid', puzzleId)
                                .single();

                            const nextId = pathEntry?.nextpuzzleid || null;

                            // Step 2: Update any puzzle_path entries that point TO this puzzle
                            // to instead point to this puzzle's next (skip over the deleted one)
                            if (nextId) {
                                const { error: updatePathError } = await supabase
                                    .from('puzzle_path')
                                    .update({ nextpuzzleid: nextId })
                                    .eq('nextpuzzleid', puzzleId);

                                if (updatePathError) {
                                    console.warn(`Failed to update puzzle_path for ${puzzleId}: ${updatePathError.message}`);
                                }
                            }

                            // Step 3: Delete the puzzle_path entry for the deleted puzzle
                            await supabase
                                .from('puzzle_path')
                                .delete()
                                .eq('puzzleid', puzzleId);

                            // Step 4: Delete the puzzle itself
                            const { error: deleteError } = await supabase
                                .from('puzzles')
                                .delete()
                                .eq('puzzleid', puzzleId);

                            if (deleteError) {
                                results.errors.push(`Failed to delete puzzle ${puzzleId}: ${deleteError.message}`);
                                continue;
                            }

                            results.duplicatesRemoved++;
                            console.log(`Removed duplicate puzzle and updated path: ${puzzleId}`);

                        } catch (error) {
                            results.errors.push(`Error removing puzzle ${duplicate.puzzleid || duplicate.id}: ${error.message}`);
                        }
                    }
                }
                
                results.details.push({
                    puzzleType: type,
                    totalPuzzles: puzzles.length,
                    duplicatesFound: duplicatesToRemove.length,
                    duplicatesRemoved: dryRun ? 0 : Math.min(duplicatesToRemove.length, maxDuplicatesToRemove)
                });
                
            } catch (error) {
                console.error(`Error processing ${type}:`, error);
                results.errors.push(`Error processing ${type}: ${error.message}`);
            }
        }
        
        const message = dryRun 
            ? `Analysis complete: Found ${results.duplicatesFound} duplicates across ${puzzleTypes.length} puzzle types`
            : `Cleanup complete: Removed ${results.duplicatesRemoved} duplicates`;
        
        console.log(message);
        
        res.json({
            success: true,
            mode: dryRun ? 'analysis' : 'cleanup',
            message,
            summary: {
                totalProcessed: results.totalProcessed,
                duplicatesFound: results.duplicatesFound,
                duplicatesRemoved: results.duplicatesRemoved,
                errorCount: results.errors.length
            },
            details: results.details,
            errors: results.errors
        });
        
    } catch (error) {
        console.error('Error in duplicate cleanup:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Analyze puzzle duplicates by type - comprehensive analysis
router.get('/api/analyze-duplicates', async (req, res) => {
    try {
        const { limit = 500 } = req.query;
        const table = 'puzzles'; // Fixed to puzzles table

        console.log(`🔍 Starting comprehensive duplicate analysis on ${table} table...`);

        // Get all puzzle types from the puzzles table
        const { data: typeData, error: typeError } = await supabase
            .from(table)
            .select('type')
            .not('type', 'is', null);

        if (typeError) {
            throw new Error(`Failed to fetch puzzle types: ${typeError.message}`);
        }

        console.log(`Raw type data count: ${typeData?.length || 0}`);

        // Count unique types (case-insensitive grouping)
        const typeCounts = {};
        typeData.forEach(p => {
            if (p.type) {
                const normalizedType = p.type.toLowerCase().trim();
                typeCounts[normalizedType] = (typeCounts[normalizedType] || 0) + 1;
            }
        });

        const puzzleTypes = Object.keys(typeCounts);
        console.log(`Found ${puzzleTypes.length} unique puzzle types (case-insensitive)`);

        const results = {
            totalPuzzleTypes: puzzleTypes.length,
            analysisPerType: [],
            highDuplicateTypes: [],
            summary: {
                totalPuzzles: 0,
                totalDuplicates: 0,
                duplicateRate: 0
            }
        };

        // Analyze each puzzle type
        for (const type of puzzleTypes) {
            try {
                // Get puzzles for this type (case-insensitive)
                const { data: puzzles, error } = await supabase
                    .from(table)
                    .select('puzzleid, question, type')
                    .ilike('type', type)
                    .limit(parseInt(limit));

                if (error || !puzzles) {
                    console.warn(`Failed to fetch ${type} puzzles:`, error?.message);
                    continue;
                }

                if (puzzles.length === 0) continue;

                results.summary.totalPuzzles += puzzles.length;

                // Detect duplicates based on question field
                const questionMap = new Map();
                let duplicateCount = 0;
                const duplicateExamples = [];

                for (const puzzle of puzzles) {
                    const question = puzzle.question || '';
                    // Normalize: lowercase, remove extra spaces, remove punctuation for comparison
                    const normalized = question.toString()
                        .toLowerCase()
                        .trim()
                        .replace(/\s+/g, ' ')
                        .replace(/[^\w\s]/g, '');

                    if (questionMap.has(normalized)) {
                        duplicateCount++;
                        if (duplicateExamples.length < 3) {
                            duplicateExamples.push({
                                original: questionMap.get(normalized).substring(0, 100),
                                duplicate: question.toString().substring(0, 100)
                            });
                        }
                    } else {
                        questionMap.set(normalized, question.toString());
                    }
                }

                results.summary.totalDuplicates += duplicateCount;

                const duplicateRate = puzzles.length > 0
                    ? Math.round((duplicateCount / puzzles.length) * 100)
                    : 0;

                const typeAnalysis = {
                    puzzleType: type,
                    totalPuzzles: puzzles.length,
                    uniquePuzzles: puzzles.length - duplicateCount,
                    duplicates: duplicateCount,
                    duplicateRate: `${duplicateRate}%`,
                    examples: duplicateExamples
                };

                results.analysisPerType.push(typeAnalysis);

                // Flag high duplicate types (>10% duplicates)
                if (duplicateRate > 10) {
                    results.highDuplicateTypes.push({
                        type,
                        duplicateRate: `${duplicateRate}%`,
                        duplicates: duplicateCount,
                        total: puzzles.length
                    });
                }

            } catch (error) {
                console.error(`Error analyzing ${type}:`, error.message);
            }
        }

        // Sort by duplicate rate descending
        results.analysisPerType.sort((a, b) =>
            parseInt(b.duplicateRate) - parseInt(a.duplicateRate)
        );
        results.highDuplicateTypes.sort((a, b) =>
            parseInt(b.duplicateRate) - parseInt(a.duplicateRate)
        );

        // Calculate overall duplicate rate
        results.summary.duplicateRate = results.summary.totalPuzzles > 0
            ? `${Math.round((results.summary.totalDuplicates / results.summary.totalPuzzles) * 100)}%`
            : '0%';

        console.log(`✅ Analysis complete: ${results.summary.totalDuplicates} duplicates found (${results.summary.duplicateRate})`);

        res.json({
            success: true,
            message: `Analyzed ${results.totalPuzzleTypes} puzzle types`,
            ...results,
            recommendations: results.highDuplicateTypes.map(t =>
                `${t.type}: ${t.duplicateRate} duplicate rate - needs variety improvement`
            )
        });

    } catch (error) {
        console.error('Error in duplicate analysis:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

export default router;