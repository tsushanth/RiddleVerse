// services/regenerationService.js

import { supabase } from '../config/database.js';
import { generateCustomPuzzles, generateCustomAnagrams, generateCustomCrosswords } from './puzzleService.js';
import { sendMulticastNotification } from './dailyPuzzleService.js';
import crypto from 'crypto';

class RegenerationService {
    async requestRegeneration(puzzleSetId, userEmail, reason = 'User requested regeneration') {
        try {
            // Validate puzzle set exists and user owns it
            const { data: puzzleSet, error: puzzleSetError } = await supabase
                .from('puzzle_sets')
                .select('*')
                .eq('id', puzzleSetId)
                .eq('creator', userEmail)
                .limit(1);

            if (puzzleSetError || !puzzleSet || puzzleSet.length === 0) {
                return {
                    success: false,
                    status: 404,
                    error: 'Puzzle set not found or access denied'
                };
            }

            // Check for existing pending request
            const { data: existingRequest } = await supabase
                .from('puzzle_regeneration_requests')
                .select('id')
                .eq('puzzle_set_id', puzzleSetId)
                .eq('status', 'pending')
                .limit(1);

            if (existingRequest && existingRequest.length > 0) {
                return {
                    success: false,
                    status: 409,
                    error: 'Regeneration already in progress'
                };
            }

            // Create regeneration request
            const requestId = crypto.randomUUID();
            const originalPuzzleSet = puzzleSet[0];

            await supabase
                .from('puzzle_regeneration_requests')
                .insert([{
                    id: requestId,
                    puzzle_set_id: puzzleSetId,
                    user_email: userEmail,
                    original_topic: originalPuzzleSet.name,
                    original_format: originalPuzzleSet.format,
                    original_puzzle_count: originalPuzzleSet.puzzle_count,
                    reason: reason,
                    status: 'pending',
                    requested_at: new Date().toISOString()
                }]);

            // Mark puzzle set as regenerating
            await supabase
                .from('puzzle_sets')
                .update({ status: 'regenerating' })
                .eq('id', puzzleSetId);

            // Start regeneration process (includes notification)
            this.processRegeneration(requestId, originalPuzzleSet);

            return {
                success: true,
                requestId: requestId,
                message: 'Regeneration started - you will be notified when complete',
                estimatedTime: '2-3 minutes'
            };

        } catch (error) {
            console.error('❌ Error in requestRegeneration:', error);
            return {
                success: false,
                error: error.message
            };
        }
    }

    async processRegeneration(requestId, originalPuzzleSet) {
        try {
            console.log(`🔄 Processing regeneration: ${requestId}`);

            // Update status to processing
            await supabase
                .from('puzzle_regeneration_requests')
                .update({ status: 'processing', started_at: new Date().toISOString() })
                .eq('id', requestId);

            const { id: puzzleSetId, creator: userEmail, name: topic, format, puzzle_count: targetCount } = originalPuzzleSet;

            // Delete existing puzzles
            await supabase
                .from('puzzles')
                .delete()
                .eq('parentSetId', puzzleSetId);

            // Generate new puzzles (skip validation for faster regeneration)
            let result;
            if (format?.toLowerCase().includes('anagram')) {
                result = await generateCustomAnagrams(topic, format, Math.max(targetCount, 10), userEmail, puzzleSetId);
            } else if (format?.toLowerCase().includes('crossword')) {
                result = await generateCustomCrosswords(topic, format, targetCount, userEmail, puzzleSetId);
            } else {
                result = await generateCustomPuzzles(topic, format, targetCount, userEmail, puzzleSetId, true); // skipValidation = true
            }

            if (result?.puzzleId) {
                // Success
                await supabase
                    .from('puzzle_regeneration_requests')
                    .update({
                        status: 'completed',
                        completed_at: new Date().toISOString(),
                        new_puzzle_count: targetCount
                    })
                    .eq('id', requestId);

                // Send notification
                await this.sendCompletionNotification(userEmail, topic, targetCount);

                console.log(`✅ Regeneration completed: ${requestId}`);
            } else {
                throw new Error(result?.error || 'Generation failed');
            }

        } catch (error) {
            console.error(`❌ Regeneration failed: ${requestId}`, error);

            await supabase
                .from('puzzle_regeneration_requests')
                .update({
                    status: 'failed',
                    completed_at: new Date().toISOString(),
                    error_message: error.message
                })
                .eq('id', requestId);

            await supabase
                .from('puzzle_sets')
                .update({ status: 'failed' })
                .eq('id', originalPuzzleSet.id);
        }
    }

    async sendCompletionNotification(userEmail, puzzleSetName, puzzleCount) {
        try {
            const { data: tokens } = await supabase
                .from('user_notification_tokens')
                .select('fcm_token')
                .eq('user_email', userEmail)
                .eq('is_active', true);

            if (!tokens || tokens.length === 0) {
                console.log(`⚠️ No FCM tokens for ${userEmail}`);
                return { success: false, error: 'No active tokens' };
            }

            const notificationData = {
                title: '🔄 Puzzle Regeneration Complete!',
                body: `Your "${puzzleSetName}" puzzles have been regenerated with ${puzzleCount} fresh challenges!`,
                data: {
                    type: 'puzzle_regeneration_complete',
                    userEmail: userEmail,
                    puzzleSetName: puzzleSetName,
                    puzzleCount: puzzleCount.toString(),
                    timestamp: new Date().toISOString()
                }
            };

            const result = await sendMulticastNotification(
                tokens.map(t => t.fcm_token), 
                notificationData
            );

            console.log(`📱 Notification sent to ${userEmail}: ${result.success ? 'success' : 'failed'}`);
            return result;

        } catch (error) {
            console.error('❌ Error sending completion notification:', error);
            return { success: false, error: error.message };
        }
    }
}

export const regenerationService = new RegenerationService();