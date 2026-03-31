// Daily Puzzles - Supabase Migration Functions (Compatible with existing schema)
// Add these functions to puzzleService.js
import { supabase } from "../config/database.js";
import { callAI } from "../utils/aiClient.js";
import {admin, db} from "../config/firebaseAdmin.js";
import {USAGE_CATEGORIES} from '../utils/usageTracker.js';

/**
 * Store daily puzzles in Supabase using existing schema with historical data preservation
 */
export async function storeDailyPuzzleSet(userEmail, topic, puzzles) {
    console.log(`📅 Storing daily puzzle set: ${userEmail}/${topic} (${puzzles.length} puzzles) with historical preservation`);
    
    try {
        const today = new Date().toISOString().split('T')[0];
        
        // Step 1: Mark any existing "current" sets for today as no longer current
        const { error: markOldError } = await supabase
            .from('daily_puzzles')
            .update({ is_current: false })
            .eq('user_email', userEmail)
            .eq('topic', topic)
            .eq('generation_date', today)
            .eq('is_current', true);

        if (markOldError) {
            console.warn(`⚠️ Failed to mark old sets as non-current: ${markOldError.message}`);
        }

        // Step 2: Create new puzzle set using existing schema
        const { data: newPuzzleSet, error: insertError } = await supabase
            .from('daily_puzzles')
            .insert({
                user_email: userEmail,
                topic: topic,
                puzzle_set: puzzles, // Store puzzles as JSONB array
                generation_date: today,
                generation_timestamp: new Date().toISOString(),
                status: 'completed',
                puzzle_count: puzzles.length,
                is_current: true // This is the new current set
            })
            .select()
            .single();

        if (insertError) {
            throw new Error(`Failed to create puzzle set: ${insertError.message}`);
        }

        console.log(`✅ Successfully stored ${puzzles.length} daily puzzles for ${userEmail}/${topic} (historical data preserved)`);
        return { success: true, puzzleSetId: newPuzzleSet.id };

    } catch (error) {
        console.error(`❌ Error storing daily puzzle set:`, error);
        return { success: false, error: error.message };
    }
}

export async function isDuplicateForUser(userEmail, question, seenQuestions) {
    const newEmbedding = adjustEmbeddingSize(await generateEmbedding(question));
  
    for (const existing of seenQuestions) {
      const existingEmbedding = adjustEmbeddingSize(await generateEmbedding(existing));
      const similarity = cosineSimilarity(newEmbedding, existingEmbedding);
      if (similarity > 0.75) return true;
    }
  
    return false;
  }

/**
 * Get daily puzzle set from Supabase with flexible fallback logic
 * Falls back to yesterday's puzzle if today's isn't available
 */
export async function getDailyPuzzleSet(userEmail, topic, date = null) {
    console.log(`📅 Fetching daily puzzle set: ${userEmail}/${topic}${date ? ` for ${date}` : ' (flexible mode)'}`);
    
    try {
        const targetDate = date || new Date().toISOString().split('T')[0];

        // Strategy: Try to get the most recent puzzle set regardless of date
        let { data: puzzleSet, error: setError } = await supabase
            .from('daily_puzzles')
            .select('*')
            .eq('user_email', userEmail)
            .eq('topic', topic)
            .eq('is_current', true)
            .order('generation_date', { ascending: false })
            .limit(1)
            .single();

        // If no current puzzle found, try ANY puzzle for this user/topic
        if ((setError || !puzzleSet)) {
            console.log(`📅 No current puzzle found for ${userEmail}/${topic}, trying any available puzzle...`);
            
            const { data: anyPuzzle, error: anyError } = await supabase
                .from('daily_puzzles')
                .select('*')
                .eq('user_email', userEmail)
                .eq('topic', topic)
                .order('generation_date', { ascending: false })
                .limit(1)
                .single();
            
            if (anyError || !anyPuzzle) {
                console.log(`📅 No puzzle found at all for ${userEmail}/${topic}`);
                
                // Log the failure to database for monitoring
                await logDailyPuzzleFailure(userEmail, topic, targetDate, 'no_puzzles_found', 
                    `No puzzles available for this user/topic combination`);
                
                return { 
                    success: false, 
                    error: 'No puzzle set found for this topic',
                    suggestedAction: 'generate_new_puzzles',
                    needsGeneration: true
                };
            }
            
            puzzleSet = anyPuzzle;
            console.log(`📅 Using available puzzle from ${anyPuzzle.generation_date}`);
        }

        // Extract puzzles from JSONB - handle different formats
        let puzzles = [];
        try {
            if (Array.isArray(puzzleSet.puzzle_set)) {
                puzzles = puzzleSet.puzzle_set;
            } else if (puzzleSet.puzzle_set && puzzleSet.puzzle_set.puzzles) {
                puzzles = puzzleSet.puzzle_set.puzzles;
            } else if (puzzleSet.puzzle_set && puzzleSet.puzzle_set.puzzleData && puzzleSet.puzzle_set.puzzleData.puzzles) {
                puzzles = puzzleSet.puzzle_set.puzzleData.puzzles;
            }
        } catch (parseError) {
            console.error(`❌ Error parsing puzzle_set for ${userEmail}/${topic}:`, parseError);
            
            // Log the parsing failure
            await logDailyPuzzleFailure(userEmail, topic, targetDate, 'parse_error', 
                `Failed to parse puzzle data: ${parseError.message}`);
            
            return { success: false, error: 'Invalid puzzle data format' };
        }

        const isFromToday = puzzleSet.generation_date === targetDate;
        const daysDifference = isFromToday ? 0 : Math.floor(
            (new Date(targetDate) - new Date(puzzleSet.generation_date)) / (1000 * 60 * 60 * 24)
        );

        // Always return what we have, regardless of age
        console.log(`✅ Retrieved ${puzzles.length} daily puzzles for ${userEmail}/${topic} ${isFromToday ? '(today)' : `(${daysDifference} day${daysDifference !== 1 ? 's' : ''} ago)`}`);
        
        return {
            success: true,
            puzzleSet: {
                topic: puzzleSet.topic,
                generationDate: puzzleSet.generation_date,
                generationTimestamp: puzzleSet.generation_timestamp,
                puzzleCount: puzzleSet.puzzle_count || puzzles.length,
                puzzles: puzzles,
                // Add metadata about fallback
                isFromRequestedDate: isFromToday,
                daysDifference: daysDifference,
                fallbackUsed: !isFromToday,
                age: daysDifference === 0 ? 'today' : 
                     daysDifference === 1 ? 'yesterday' : 
                     daysDifference <= 7 ? `${daysDifference} days ago` :
                     `${Math.floor(daysDifference / 7)} weeks ago`
            }
        };

    } catch (error) {
        console.error(`❌ Error fetching daily puzzle set:`, error);
        
        // Log the system error
        await logDailyPuzzleFailure(userEmail, topic, date || new Date().toISOString().split('T')[0], 
            'system_error', error.message);
        
        return { success: false, error: error.message };
    }
}

async function logDailyPuzzleFailure(userEmail, topic, requestedDate, failureType, errorMessage) {
    try {
        await supabase
            .from('daily_puzzle_failures')
            .insert({
                user_email: userEmail,
                topic: topic,
                requested_date: requestedDate,
                failure_type: failureType,
                error_message: errorMessage,
                timestamp: new Date().toISOString(),
                auto_recovery_attempted: false
            });
            
        console.log(`📝 Logged daily puzzle failure: ${userEmail}/${topic} - ${failureType}`);
    } catch (logError) {
        console.error(`❌ Failed to log daily puzzle failure:`, logError);
    }
}

/**
 * List all topics for a user using existing schema
 */
export async function listUserDailyTopics(userEmail) {
    console.log(`📋 Listing daily topics for: ${userEmail}`);
    
    try {
        // Check both user_topics and daily_puzzles tables
        const [topicsResult, puzzlesResult] = await Promise.all([
            // Get configured topics
            supabase
                .from('user_topics')
                .select('topic')
                .eq('user_email', userEmail),
            
            // Get topics with actual puzzles (current only)
            supabase
                .from('daily_puzzles')
                .select('topic, generation_date, puzzle_count, status, generation_timestamp')
                .eq('user_email', userEmail)
                .eq('is_current', true)
                .order('generation_date', { ascending: false })
        ]);

        if (topicsResult.error) {
            console.warn(`⚠️ Error fetching configured topics: ${topicsResult.error.message}`);
        }

        if (puzzlesResult.error) {
            throw new Error(`Failed to fetch puzzle topics: ${puzzlesResult.error.message}`);
        }

        // Combine configured topics with topics that have puzzles
        const configuredTopics = topicsResult.data?.map(t => t.topic) || [];
        const puzzleTopics = puzzlesResult.data?.map(p => p.topic) || [];
        
        const allTopics = [...new Set([...configuredTopics, ...puzzleTopics])].sort();
        
        console.log(`✅ Found ${allTopics.length} topics for ${userEmail} (${configuredTopics.length} configured, ${puzzleTopics.length} with puzzles)`);
        return { success: true, topics: allTopics };

    } catch (error) {
        console.error(`❌ Error listing daily topics:`, error);
        return { success: false, error: error.message };
    }
}

/**
 * Get existing daily questions for comprehensive deduplication using existing schema
 */
export async function getExistingDailyQuestions(userEmail, topic, dayRange = 365) {
    console.log(`📋 Fetching existing daily questions for ${userEmail}/${topic} (last ${dayRange} days) for deduplication`);
    
    try {
        const cutoffDate = new Date();
        cutoffDate.setDate(cutoffDate.getDate() - dayRange);
        const cutoffDateStr = cutoffDate.toISOString().split('T')[0];

        // Get ALL puzzle sets in date range (both current and historical)
        const { data: puzzleSets, error: setError } = await supabase
            .from('daily_puzzles')
            .select('puzzle_set')
            .eq('user_email', userEmail)
            .eq('topic', topic)
            .gte('generation_date', cutoffDateStr)
            .eq('status', 'completed'); // Only completed sets

        if (setError) {
            console.warn(`⚠️ Error fetching puzzle sets: ${setError.message}`);
            return [];
        }

        if (!puzzleSets || puzzleSets.length === 0) {
            console.log(`📋 No existing puzzle sets found for ${userEmail}/${topic}`);
            return [];
        }

        // Extract questions from all puzzle sets
        const allQuestions = [];
        
        for (const puzzleSet of puzzleSets) {
            try {
                let puzzles = [];
                
                // Handle different puzzle_set formats
                if (Array.isArray(puzzleSet.puzzle_set)) {
                    puzzles = puzzleSet.puzzle_set;
                } else if (puzzleSet.puzzle_set && puzzleSet.puzzle_set.puzzles) {
                    puzzles = puzzleSet.puzzle_set.puzzles;
                } else if (puzzleSet.puzzle_set && puzzleSet.puzzle_set.puzzleData && puzzleSet.puzzle_set.puzzleData.puzzles) {
                    puzzles = puzzleSet.puzzle_set.puzzleData.puzzles;
                }
                
                // Extract questions
                for (const puzzle of puzzles) {
                    if (puzzle.question) {
                        allQuestions.push(puzzle.question);
                    }
                }
            } catch (error) {
                console.warn(`⚠️ Error parsing puzzle set:`, error);
                continue;
            }
        }

        console.log(`📋 Found ${allQuestions.length} historical questions for ${userEmail}/${topic} across ${puzzleSets.length} puzzle sets`);
        return allQuestions;

    } catch (error) {
        console.error(`❌ Error fetching existing daily questions:`, error);
        return [];
    }
}

/**
 * Get comprehensive deduplication data across multiple users and topics using existing schema
 */
export async function getGlobalDailyQuestions(topic, dayRange = 180) {
    console.log(`🌍 Fetching global daily questions for topic: ${topic} (last ${dayRange} days) for cross-user deduplication`);
    
    try {
        const cutoffDate = new Date();
        cutoffDate.setDate(cutoffDate.getDate() - dayRange);
        const cutoffDateStr = cutoffDate.toISOString().split('T')[0];

        // Get puzzle sets for this topic across ALL users using the existing schema
        const { data: puzzleSets, error: setError } = await supabase
            .from('daily_puzzles')
            .select('puzzle_set')
            .eq('topic', topic)
            .gte('generation_date', cutoffDateStr)
            .eq('status', 'completed');

        if (setError) {
            console.warn(`⚠️ Error fetching global puzzle sets: ${setError.message}`);
            return [];
        }

        if (!puzzleSets || puzzleSets.length === 0) {
            console.log(`🌍 No existing puzzle sets found globally for ${topic}`);
            return [];
        }

        // Extract questions from all puzzle sets
        const allQuestions = [];
        
        for (const puzzleSet of puzzleSets) {
            try {
                let puzzles = [];
                
                // Handle different puzzle_set formats
                if (Array.isArray(puzzleSet.puzzle_set)) {
                    puzzles = puzzleSet.puzzle_set;
                } else if (puzzleSet.puzzle_set && puzzleSet.puzzle_set.puzzles) {
                    puzzles = puzzleSet.puzzle_set.puzzles;
                } else if (puzzleSet.puzzle_set && puzzleSet.puzzle_set.puzzleData && puzzleSet.puzzle_set.puzzleData.puzzles) {
                    puzzles = puzzleSet.puzzle_set.puzzleData.puzzles;
                }
                
                // Extract questions
                for (const puzzle of puzzles) {
                    if (puzzle.question) {
                        allQuestions.push(puzzle.question);
                    }
                }
            } catch (error) {
                console.warn(`⚠️ Error parsing puzzle set:`, error);
                continue;
            }
        }

        console.log(`🌍 Found ${allQuestions.length} global questions for ${topic} across ${puzzleSets.length} puzzle sets`);
        return allQuestions;

    } catch (error) {
        console.error(`❌ Error fetching global daily questions:`, error);
        return [];
    }
}

/**
 * Enhanced daily puzzle generation with comprehensive deduplication using historical data
 */
export async function generateUserDailyPuzzles(userEmail, topics) {
    console.log(`🎯 Generating daily puzzles for ${userEmail}: ${topics.join(', ')} with historical deduplication`);
    
    const results = {
        successful: [],
        failed: [],
        summary: {
            totalTopics: topics.length,
            successful: 0,
            failed: 0,
            totalQuestionsGenerated: 0,
            totalAttemptsRequired: 0,
            averageAttemptsPerQuestion: 0
        }
    };

    for (const topic of topics) {
        try {
            console.log(`🧩 Generating puzzles for topic: ${topic}`);
            
            // Get comprehensive existing questions for better deduplication
            const userHistoricalQuestions = await getExistingDailyQuestions(userEmail, topic, 365); // 1 year of user history
            const globalQuestions = await getGlobalDailyQuestions(topic, 90); // 3 months global to avoid common questions
            
            // Combine user historical + global questions for comprehensive deduplication
            const allExistingQuestions = [...new Set([...userHistoricalQuestions, ...globalQuestions])];
            console.log(`📊 Deduplication database: ${userHistoricalQuestions.length} user questions + ${globalQuestions.length} global questions = ${allExistingQuestions.length} unique existing questions`);
            
            const generatedQuestions = [];
            let totalAttempts = 0;
            const maxAttempts = 25; // Increased attempts for better success rate
            const targetCount = 5;
            
            while (generatedQuestions.length < targetCount && totalAttempts < maxAttempts) {
                totalAttempts++;
                const remainingNeeded = targetCount - generatedQuestions.length;
                console.log(`🔄 Generation attempt ${totalAttempts}/${maxAttempts} for ${topic} (need ${remainingNeeded} more)`);
                
                const prompt = `Generate a unique and interesting ${topic} puzzle in JSON format.

CRITICAL REQUIREMENTS:
- Must be different from typical textbook questions
- Should be engaging and thought-provoking
- Must have ONE correct answer that is verifiable
- Difficulty should be appropriate for general knowledge
- Avoid overly technical or specialized knowledge

Topic Focus: ${topic}
Required Format:
{
    "question": "An engaging question about ${topic}",
    "answer": "The correct answer",
    "hint": "A helpful but not revealing clue",
    "options": ["correct_answer", "plausible_wrong1", "plausible_wrong2", "plausible_wrong3"],
    "difficulty": "Easy|Medium|Hard"
}

Make it interesting and unique - avoid common textbook questions. Respond with valid JSON only.`;

                const response = await callAI(prompt, null, 2, {
                    category: USAGE_CATEGORIES.CUSTOM_PUZZLE_GENERATION,
                    puzzleType: topic,
                    difficulty: 'medium'
                });
                if (!response || response.startsWith("Error")) {
                    console.warn(`⚠️ AI generation failed for ${topic}: ${response}`);
                    continue;
                }

                let puzzle;
                try {
                    puzzle = JSON.parse(response);
                    puzzle.question = puzzle.question.trim();
                    
                    // Validate required fields
                    if (!puzzle.question || !puzzle.answer || !puzzle.options || !Array.isArray(puzzle.options)) {
                        console.warn(`⚠️ Invalid puzzle structure for ${topic}`);
                        continue;
                    }
                    
                } catch (e) {
                    console.error(`❌ Invalid JSON response for ${topic}:`, response.substring(0, 200));
                    continue;
                }

                // Enhanced duplicate checking with both user and global history
                const isDuplicate = await isDuplicateForUser(userEmail, puzzle.question, allExistingQuestions);
                if (isDuplicate) {
                    console.log(`⚠️ Duplicate question detected for ${topic} (attempt ${totalAttempts}), retrying...`);
                    continue;
                }

                // Quick validation with AI
                const validationPrompt = `Analyze this puzzle and determine if it's valid. Be specific about any issues.

                PUZZLE TO VALIDATE:
                Topic: ${topic}
                Question: ${puzzle.question}
                Answer: ${puzzle.answer}
                Options: ${puzzle.options.join(', ')}
                Difficulty: ${puzzle.difficulty}

                VALIDATION CRITERIA:
                1. Is the question clear and unambiguous?
                2. Is there exactly one correct answer?
                3. Is the correct answer included in the options?
                4. Are the wrong options plausible but clearly incorrect?
                5. Is the difficulty appropriate for the topic?
                6. Is the content factually accurate?
                7. Is it engaging and not too trivial/obvious?

                Respond ONLY in this JSON format:
                {
                "result": "VALID" or "INVALID",
                "reason": "Brief explanation",
                "confidence": "High" | "Medium" | "Low"
                }
                `;

                const validationResponse = await callAI(validationPrompt, null, 2, {
                    category: USAGE_CATEGORIES.CONTENT_VALIDATION,
                    puzzleType: topic,
                    difficulty: puzzle.difficulty || 'medium'
                });

                let validationResult = {
                    isValid: false,
                    reason: "Unknown validation error",
                    confidence: "Low"
                };
                
                try {
                    const parsed = JSON.parse(validationResponse);
                    validationResult.isValid = parsed.result === 'VALID';
                    validationResult.reason = parsed.reason || validationResult.reason;
                    validationResult.confidence = parsed.confidence || validationResult.confidence;
                } catch (parseError) {
                    console.warn(`⚠️ Failed to parse validation response for ${topic}:`, parseError);
                    validationResult.reason = `Parse error: ${validationResponse.substring(0, 100)}...`;
                }
                
                if (!validationResult.isValid) {
                    console.log(`❌ Puzzle validation failed for ${topic} (attempt ${totalAttempts})`);
                    console.log(`   Reason: ${validationResult.reason}`);
                    console.log(`   Confidence: ${validationResult.confidence}`);
                    console.log(`   Question: "${puzzle.question}"`);
                    console.log(`   Answer: "${puzzle.answer}"`);
                    
                    // Log common validation issues for analysis
                    const commonIssues = [
                        'multiple correct answers',
                        'ambiguous',
                        'not in options',
                        'obviously fake',
                        'too easy',
                        'too trivial',
                        'factually incorrect',
                        'doesn\'t match topic'
                    ];
                    
                    const issueType = commonIssues.find(issue => 
                        validationResult.reason.toLowerCase().includes(issue)
                    ) || 'other';
                    
                    console.log(`   Issue Category: ${issueType}`);
                    
                    // Store failed validation for later analysis
                    await logValidationFailure(userEmail, topic, puzzle, validationResult, totalAttempts);
                    
                    continue;
                }
                
                console.log(`✅ Puzzle validation passed for ${topic} (attempt ${totalAttempts})`);
                console.log(`   Confidence: ${validationResult.confidence}`);
                if (validationResult.reason && validationResult.reason !== "Unknown validation error") {
                    console.log(`   Reason: ${validationResult.reason}`);
                }
                
                // Success! Add to generated questions and update deduplication list
                generatedQuestions.push(puzzle);
                allExistingQuestions.push(puzzle.question); // Prevent internal duplicates in this generation
                console.log(`✅ Generated valid puzzle ${generatedQuestions.length}/${targetCount} for ${topic} (attempt ${totalAttempts})`);
            }

            results.summary.totalAttemptsRequired += totalAttempts;

            if (generatedQuestions.length === 0) {
                throw new Error(`Failed to generate any valid puzzles after ${maxAttempts} attempts`);
            }

            if (generatedQuestions.length < targetCount) {
                console.warn(`⚠️ Only generated ${generatedQuestions.length}/${targetCount} puzzles for ${topic} after ${totalAttempts} attempts`);
            }

            // Store in Supabase with historical preservation
            const storeResult = await storeDailyPuzzleSet(userEmail, topic, generatedQuestions);
            
            if (storeResult.success) {
                results.successful.push({
                    topic,
                    puzzleCount: generatedQuestions.length,
                    attemptsRequired: totalAttempts,
                    puzzleSetId: storeResult.puzzleSetId,
                    historicalQuestionsChecked: allExistingQuestions.length
                });
                results.summary.successful++;
                results.summary.totalQuestionsGenerated += generatedQuestions.length;
                console.log(`✅ Successfully generated and stored ${generatedQuestions.length} puzzles for ${topic} (${totalAttempts} attempts, checked against ${allExistingQuestions.length} historical questions)`);
            } else {
                throw new Error(storeResult.error);
            }

        } catch (error) {
            console.error(`❌ Failed to generate puzzles for ${topic}:`, error);
            results.failed.push({
                topic,
                error: error.message
            });
            results.summary.failed++;
        }
    }

    // Calculate summary statistics
    if (results.summary.totalQuestionsGenerated > 0) {
        results.summary.averageAttemptsPerQuestion = Math.round(
            results.summary.totalAttemptsRequired / results.summary.totalQuestionsGenerated * 10
        ) / 10;
    }

    console.log(`🎉 Daily puzzle generation complete: ${results.summary.successful}/${results.summary.totalTopics} topics successful, ${results.summary.totalQuestionsGenerated} total questions generated, avg ${results.summary.averageAttemptsPerQuestion} attempts per question`);
    return results;
}

/**
 * Get daily puzzle statistics for monitoring and analytics
 */
export async function getDailyPuzzleStatistics(userEmail = null, dayRange = 30) {
    console.log(`📊 Getting daily puzzle statistics${userEmail ? ` for ${userEmail}` : ' globally'} (last ${dayRange} days)`);
    
    try {
        const cutoffDate = new Date();
        cutoffDate.setDate(cutoffDate.getDate() - dayRange);
        const cutoffDateStr = cutoffDate.toISOString().split('T')[0];

        let query = supabase
            .from('daily_puzzles')
            .select('topic, generation_date, puzzle_count, status, is_current')
            .gte('generation_date', cutoffDateStr);

        if (userEmail) {
            query = query.eq('user_email', userEmail);
        }

        const { data: puzzleSets, error } = await query;

        if (error) {
            throw new Error(`Failed to fetch statistics: ${error.message}`);
        }

        const stats = {
            totalSets: puzzleSets.length,
            currentSets: puzzleSets.filter(s => s.is_current).length,
            historicalSets: puzzleSets.filter(s => !s.is_current).length,
            totalPuzzles: puzzleSets.reduce((sum, s) => sum + (s.puzzle_count || 0), 0),
            topicBreakdown: {},
            statusBreakdown: {
                completed: 0,
                generating: 0,
                failed: 0
            },
            dailyActivity: {}
        };

        // Analyze topics
        puzzleSets.forEach(set => {
            if (!stats.topicBreakdown[set.topic]) {
                stats.topicBreakdown[set.topic] = {
                    total: 0,
                    current: 0,
                    historical: 0,
                    puzzles: 0
                };
            }
            
            stats.topicBreakdown[set.topic].total++;
            stats.topicBreakdown[set.topic].puzzles += set.puzzle_count || 0;
            
            if (set.is_current) {
                stats.topicBreakdown[set.topic].current++;
            } else {
                stats.topicBreakdown[set.topic].historical++;
            }
        });

        // Analyze status
        puzzleSets.forEach(set => {
            stats.statusBreakdown[set.status] = (stats.statusBreakdown[set.status] || 0) + 1;
        });

        // Analyze daily activity
        puzzleSets.forEach(set => {
            const date = set.generation_date;
            if (!stats.dailyActivity[date]) {
                stats.dailyActivity[date] = {
                    sets: 0,
                    puzzles: 0,
                    topics: new Set()
                };
            }
            
            stats.dailyActivity[date].sets++;
            stats.dailyActivity[date].puzzles += set.puzzle_count || 0;
            stats.dailyActivity[date].topics.add(set.topic);
        });

        // Convert topic sets to counts
        Object.keys(stats.dailyActivity).forEach(date => {
            stats.dailyActivity[date].uniqueTopics = stats.dailyActivity[date].topics.size;
            delete stats.dailyActivity[date].topics;
        });

        console.log(`📊 Statistics: ${stats.totalSets} sets, ${stats.totalPuzzles} puzzles, ${Object.keys(stats.topicBreakdown).length} topics`);
        return { success: true, stats };

    } catch (error) {
        console.error(`❌ Error getting daily puzzle statistics:`, error);
        return { success: false, error: error.message };
    }
}

/**
 * Clean up old daily puzzle data (removes very old historical data)
 */
export async function cleanupOldDailyPuzzles(dayRange = 730, dryRun = true) {
    console.log(`🧹 ${dryRun ? 'Analyzing' : 'Cleaning up'} daily puzzles older than ${dayRange} days`);
    
    try {
        const cutoffDate = new Date();
        cutoffDate.setDate(cutoffDate.getDate() - dayRange);
        const cutoffDateStr = cutoffDate.toISOString().split('T')[0];

        // Find old puzzle sets
        const { data: oldSets, error: setError } = await supabase
            .from('daily_puzzles')
            .select('id, user_email, topic, generation_date, puzzle_count')
            .lt('generation_date', cutoffDateStr)
            .eq('is_current', false); // Only clean up non-current sets

        if (setError) {
            throw new Error(`Failed to find old sets: ${setError.message}`);
        }

        if (!oldSets || oldSets.length === 0) {
            console.log(`🧹 No old daily puzzle sets found to clean up`);
            return { success: true, cleaned: 0, message: 'No cleanup needed' };
        }

        console.log(`🧹 Found ${oldSets.length} old daily puzzle sets to clean up`);

        if (dryRun) {
            const totalPuzzles = oldSets.reduce((sum, set) => sum + (set.puzzle_count || 0), 0);
            return {
                success: true,
                mode: 'analysis',
                message: `Would clean up ${oldSets.length} sets (${totalPuzzles} puzzles) older than ${dayRange} days`,
                oldSets: oldSets.slice(0, 10), // Sample
                totalSets: oldSets.length,
                totalPuzzles
            };
        }

        // Actually delete old sets
        const setIds = oldSets.map(set => set.id);
        const { error: deleteError } = await supabase
            .from('daily_puzzles')
            .delete()
            .in('id', setIds);

        if (deleteError) {
            throw new Error(`Failed to delete old sets: ${deleteError.message}`);
        }

        const totalPuzzles = oldSets.reduce((sum, set) => sum + (set.puzzle_count || 0), 0);
        console.log(`✅ Cleaned up ${oldSets.length} old daily puzzle sets (${totalPuzzles} puzzles)`);

        return {
            success: true,
            mode: 'cleanup',
            message: `Cleaned up ${oldSets.length} sets (${totalPuzzles} puzzles) older than ${dayRange} days`,
            cleaned: oldSets.length,
            puzzlesRemoved: totalPuzzles
        };

    } catch (error) {
        console.error(`❌ Error cleaning up old daily puzzles:`, error);
        return { success: false, error: error.message };
    }
}

async function logValidationFailure(userEmail, topic, puzzle, validationResult, attempt) {
    try {
        await supabase
            .from('daily_puzzle_validation_failures')
            .insert({
                user_email: userEmail,
                topic: topic,
                question: puzzle.question,
                answer: puzzle.answer,
                options: puzzle.options,
                difficulty: puzzle.difficulty,
                failure_reason: validationResult.reason,
                confidence_level: validationResult.confidence,
                attempt_number: attempt,
                timestamp: new Date().toISOString()
            });
    } catch (error) {
        console.warn(`⚠️ Failed to log validation failure:`, error);
    }
}


export async function storeFCMToken(userEmail, userId, fcmToken, platform) {
    try {
        const { data, error } = await supabase
            .from('user_notification_tokens')
            .upsert({
                user_email: userEmail,
                user_id: userId,
                fcm_token: fcmToken,
                platform: platform,
                is_active: true,
                last_used: new Date().toISOString(),
                created_at: new Date().toISOString()
            }, {
                onConflict: 'user_email,platform,fcm_token',
                ignoreDuplicates: false // This will update existing records
            });

        if (error) {
            throw new Error(`Failed to store FCM token: ${error.message}`);
        }

        return { success: true, data };
    } catch (error) {
        console.error('Error storing FCM token:', error);
        return { success: false, error: error.message };
    }
}

/**
 * Send daily puzzle notifications to users
 */
export async function sendDailyPuzzleNotifications(generationResults) {
    console.log(`📨 Sending daily puzzle notifications to ${generationResults.successful?.length || 0} users`);
    
    if (!generationResults.successful || generationResults.successful.length === 0) {
        console.log(`📭 No successful generations, skipping notifications`);
        return { success: true, sent: 0, message: 'No notifications to send' };
    }
    
    const notificationResults = {
        sent: 0,
        failed: 0,
        errors: []
    };
    
    for (const userResult of generationResults.successful) {
        try {
            const { userEmail, topicsProcessed, totalPuzzlesGenerated } = userResult;
            
            // Get active FCM tokens for this user
            const { data: tokens, error: tokenError } = await supabase
                .from('user_notification_tokens')
                .select('fcm_token, platform')
                .eq('user_email', userEmail)
                .eq('is_active', true);
            
            if (tokenError || !tokens || tokens.length === 0) {
                console.log(`📱 No active tokens found for ${userEmail}`);
                continue;
            }
            
            // Create notification content
            const topicText = topicsProcessed.length === 1 
                ? topicsProcessed[0]
                : `${topicsProcessed.length} topics`;
            
            const notificationData = {
                title: "🧩 New Daily Puzzles Ready!",
                body: `${totalPuzzlesGenerated} fresh puzzles for ${topicText} are waiting for you!`,
                data: {
                    type: 'daily_puzzles',
                    userEmail: userEmail,
                    topics: topicsProcessed,
                    puzzleCount: totalPuzzlesGenerated,
                    generatedAt: new Date().toISOString()
                }
            };
            
            // Send to all user's devices
            const userTokens = tokens.map(t => t.fcm_token);
            const sendResult = await sendMulticastNotification(userTokens, notificationData);
            
            // Log notification
            await logNotification(userEmail, notificationData, sendResult);
            
            if (sendResult.success) {
                notificationResults.sent++;
                console.log(`✅ Notification sent to ${userEmail} (${userTokens.length} devices)`);
            } else {
                notificationResults.failed++;
                notificationResults.errors.push({
                    userEmail,
                    error: sendResult.error
                });
            }
            
        } catch (error) {
            console.error(`❌ Failed to send notification to ${userResult.userEmail}:`, error);
            notificationResults.failed++;
            notificationResults.errors.push({
                userEmail: userResult.userEmail,
                error: error.message
            });
        }
    }
    
    console.log(`📨 Notification summary: ${notificationResults.sent} sent, ${notificationResults.failed} failed`);
    return {
        success: true,
        sent: notificationResults.sent,
        failed: notificationResults.failed,
        errors: notificationResults.errors
    };
}

/**
 * Send multicast notification using Firebase Admin
 */
export async function sendMulticastNotification(tokens, notificationData) {
    try {
        const message = {
            notification: {
                title: notificationData.title,
                body: notificationData.body
            },
            data: {
                // Convert all data to strings (FCM requirement)
                ...Object.fromEntries(
                    Object.entries(notificationData.data).map(([key, value]) => [
                        key, 
                        typeof value === 'string' ? value : JSON.stringify(value)
                    ])
                )
            },
            tokens: tokens
        };
        
        // Use sendEachForMulticast instead of sendMulticast
        const response = await admin.messaging().sendEachForMulticast(message);
        
        return {
            success: response.successCount > 0,
            successCount: response.successCount,
            failureCount: response.failureCount,
            responses: response.responses
        };
        
    } catch (error) {
        console.error(`❌ FCM send error:`, error);
        return {
            success: false,
            error: error.message
        };
    }
}

/**
 * Log notification attempt
 */
async function logNotification(userEmail, notificationData, sendResult) {
    try {
        await supabase
            .from('notification_logs')
            .insert({
                user_email: userEmail,
                notification_type: 'daily_puzzles',
                title: notificationData.title,
                body: notificationData.body,
                data: notificationData.data,
                success: sendResult.success,
                error_message: sendResult.error || null,
                fcm_response: sendResult
            });
    } catch (error) {
        console.error(`❌ Failed to log notification:`, error);
    }
}

/**
 * Enhanced daily puzzle generation with notifications
 */
export async function generateUserDailyPuzzlesWithNotifications(userEmail = null, topics = null) {
    console.log(`🎯 Generating daily puzzles with notifications...`);
    
    let generationResults;
    
    if (userEmail && topics) {
        // Single user generation
        generationResults = await generateUserDailyPuzzles(userEmail, topics);
        // Convert to format expected by notification function
        generationResults = {
            successful: [{
                userEmail,
                topicsProcessed: topics,
                totalPuzzlesGenerated: generationResults.summary.totalQuestionsGenerated
            }]
        };
    } else {
        // Bulk generation for all users
        const allUsers = [];
        const snapshot = await db.collection("user_topics").get();
        
        for (const doc of snapshot.docs) {
            const email = doc.id;
            const userTopics = doc.data().topics || [];
            
            if (userTopics.length === 0) continue;
            
            try {
                const result = await generateUserDailyPuzzles(email, userTopics);
                if (result.summary.successful > 0) {
                    allUsers.push({
                        userEmail: email,
                        topicsProcessed: userTopics,
                        totalPuzzlesGenerated: result.summary.totalQuestionsGenerated
                    });
                }
            } catch (error) {
                console.error(`❌ Generation failed for ${email}:`, error);
            }
        }
        
        generationResults = { successful: allUsers };
    }
    
    // Send notifications
    const notificationResults = await sendDailyPuzzleNotifications(generationResults);
    
    return {
        generation: generationResults,
        notifications: notificationResults,
        summary: {
            usersGenerated: generationResults.successful?.length || 0,
            notificationsSent: notificationResults.sent,
            notificationsFailed: notificationResults.failed
        }
    };
}

// ===============================================
// STEP 4: Automated Daily Generation with Notifications
// ===============================================

// Add this scheduled function (you can use cron or trigger it manually)
export async function dailyPuzzleGenerationJob() {
    console.log(`⏰ Starting automated daily puzzle generation with tracking...`);
    
    let jobId = null;
    const startTime = new Date();
    
    try {
        // 🆕 START: Create job tracking record
        const { data: jobData, error: jobError } = await supabase
            .from('generation_jobs')
            .insert({
                trigger_source: 'cron',
                status: 'running',
                started_at: startTime.toISOString()
            })
            .select()
            .single();

        if (jobError) {
            console.warn('⚠️ Failed to create job tracking:', jobError.message);
        } else {
            jobId = jobData.id;
            console.log(`📊 Created job tracking: ${jobId}`);
        }
        // 🆕 END

        // Your existing generation logic (keep as is)
        const result = await generateUserDailyPuzzlesWithNotifications();
        
        // 🆕 START: Update job with success
        if (jobId) {
            const endTime = new Date();
            const duration = Math.round((endTime - startTime) / 1000);
            
            await supabase
                .from('generation_jobs')
                .update({
                    status: 'completed',
                    completed_at: endTime.toISOString(),
                    duration_seconds: duration,
                    total_users: result.summary?.usersGenerated || 0,
                    successful_users: result.summary?.usersGenerated || 0,
                    failed_users: 0,
                    total_puzzles_generated: result.generation?.successful?.reduce((sum, user) => 
                        sum + (user.totalPuzzlesGenerated || 0), 0) || 0
                })
                .eq('id', jobId);
                
            console.log(`📊 Job completed: ${jobId} (${duration}s)`);
        }
        // 🆕 END
        
        return result;
        
    } catch (error) {
        console.error(`❌ Daily generation job failed:`, error);
        
        // 🆕 START: Update job with failure
        if (jobId) {
            const endTime = new Date();
            const duration = Math.round((endTime - startTime) / 1000);
            
            await supabase
                .from('generation_jobs')
                .update({
                    status: 'failed',
                    completed_at: endTime.toISOString(),
                    duration_seconds: duration,
                    error_message: error.message
                })
                .eq('id', jobId);
                
            console.log(`📊 Job failed: ${jobId} (${duration}s)`);
        }
        // 🆕 END
        
        throw error;
    }
}

export async function dailyPuzzleGenerationJobWithManualTrigger() {
    // Same as dailyPuzzleGenerationJob() but with trigger_source: 'manual'
    console.log(`⏰ Starting automated daily puzzle generation with tracking...`);
    
    let jobId = null;
    const startTime = new Date();
    
    try {
        // 🆕 START: Create job tracking record
        const { data: jobData, error: jobError } = await supabase
            .from('generation_jobs')
            .insert({
                trigger_source: 'manual',
                status: 'running',
                started_at: startTime.toISOString()
            })
            .select()
            .single();

        if (jobError) {
            console.warn('⚠️ Failed to create job tracking:', jobError.message);
        } else {
            jobId = jobData.id;
            console.log(`📊 Created job tracking: ${jobId}`);
        }
        // 🆕 END

        // Your existing generation logic (keep as is)
        const result = await generateUserDailyPuzzlesWithNotifications();
        
        // 🆕 START: Update job with success
        if (jobId) {
            const endTime = new Date();
            const duration = Math.round((endTime - startTime) / 1000);
            
            await supabase
                .from('generation_jobs')
                .update({
                    status: 'completed',
                    completed_at: endTime.toISOString(),
                    duration_seconds: duration,
                    total_users: result.summary?.usersGenerated || 0,
                    successful_users: result.summary?.usersGenerated || 0,
                    failed_users: 0,
                    total_puzzles_generated: result.generation?.successful?.reduce((sum, user) => 
                        sum + (user.totalPuzzlesGenerated || 0), 0) || 0
                })
                .eq('id', jobId);
                
            console.log(`📊 Job completed: ${jobId} (${duration}s)`);
        }
        // 🆕 END
        
        return result;
        
    } catch (error) {
        console.error(`❌ Daily generation job failed:`, error);
        
        // 🆕 START: Update job with failure
        if (jobId) {
            const endTime = new Date();
            const duration = Math.round((endTime - startTime) / 1000);
            
            await supabase
                .from('generation_jobs')
                .update({
                    status: 'failed',
                    completed_at: endTime.toISOString(),
                    duration_seconds: duration,
                    error_message: error.message
                })
                .eq('id', jobId);
                
            console.log(`📊 Job failed: ${jobId} (${duration}s)`);
        }
        // 🆕 END
        
        throw error;
    }
}