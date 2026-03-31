import express from 'express';
import { supabase } from '../config/database.js';

const router = express.Router();

/**
 * GET /api/user-config
 * Simple endpoint that returns user config JSON
 * 
 * Query params: userId, email
 * Returns: JSON config object
 */
router.get('/user-config', async (req, res) => {
  try {
    const { userId, email } = req.query;

    // Validate inputs
    if (!userId || !email) {
      return res.status(400).json({
        error: 'Missing required parameters: userId and email'
      });
    }

    console.log(`📥 Fetching config for: ${email}`);

    // Fetch user from database
    const { data: user, error } = await supabase
      .from('users')
      .select('*')
      .eq('user_id', userId)
      .single();

    // If user doesn't exist, return default config
    if (error || !user) {
      console.log(`ℹ️  User not found, returning default config`);
      return res.json(getDefaultConfig(userId, email));
    }

    // Build config response
    const config = {
      userId: user.user_id,
      email: user.email,
      subscriptionTier: user.subscription_tier || 'free',
      shouldShowTrialPrompt: shouldShowTrial(user),
      trialPromptReason: getTrialReason(user),
      usageStats: {
        dailyGenerations: user.daily_count || 0,
        monthlyGenerations: user.monthly_count || 0,
        totalGenerations: user.total_count || 0
      },
      features: {
        showAds: user.subscription_tier === 'free',
        unlimitedPuzzles: user.subscription_tier !== 'free',
        priorityGeneration: user.subscription_tier !== 'free'
      },
      lastUpdated: Date.now()
    };

    console.log(`✅ Config sent: showTrial=${config.shouldShowTrialPrompt}`);
    res.json(config);

  } catch (error) {
    console.error('❌ Error:', error);
    res.status(500).json({
      error: 'Internal server error',
      message: error.message
    });
  }
});

// ═══════════════════════════════════════════════════════════════════════
// Helper Functions
// ═══════════════════════════════════════════════════════════════════════

/**
 * Check if trial prompt should be shown
 */
function shouldShowTrial(user) {
  // Don't show if premium
  if (user.subscription_tier !== 'free') {
    return false;
  }

  // Don't show if dismissed recently (< 7 days)
  const daysSinceDismiss = (Date.now() - (user.trial_prompt_last_dismissed || 0)) / (1000 * 60 * 60 * 24);
  if (daysSinceDismiss < 7) {
    return false;
  }

  // Don't show if dismissed too many times
  if (user.trial_prompt_dismiss_count >= 3) {
    return false;
  }

  // Show if high usage
  if (user.daily_count > 8 || user.monthly_count > 80) {
    return true;
  }

  // Show if milestone
  if ([50, 100, 200].includes(user.total_count)) {
    return true;
  }

  return false;
}

/**
 * Get trial prompt reason
 */
function getTrialReason(user) {
  if (!shouldShowTrial(user)) {
    return 'none';
  }

  if (user.daily_count > 8 || user.monthly_count > 80) {
    return 'high_usage';
  }

  if ([50, 100, 200].includes(user.total_count)) {
    return 'milestone_reached';
  }

  return 'none';
}

/**
 * Default config for new users
 */
function getDefaultConfig(userId, email) {
  return {
    userId,
    email,
    subscriptionTier: 'free',
    shouldShowTrialPrompt: true,
    trialPromptReason: 'onboarding',
    usageStats: {
      dailyGenerations: 0,
      monthlyGenerations: 0,
      totalGenerations: 0
    },
    features: {
      showAds: true,
      unlimitedPuzzles: false,
      priorityGeneration: false
    },
    lastUpdated: Date.now()
  };
}

/**
 * POST /api/trial-prompt-shown
 * Records that the trial prompt was shown to the user
 * Updates user record to prevent showing again too soon
 *
 * Body: { userId, email, timestamp }
 */
router.post('/trial-prompt-shown', async (req, res) => {
  try {
    const { userId, email, timestamp } = req.body;

    // Validate inputs
    if (!userId || !email) {
      return res.status(400).json({
        success: false,
        error: 'Missing required parameters: userId and email'
      });
    }

    console.log(`📝 Recording trial prompt shown for: ${email}`);

    // Update user record in database
    const { data: existingUser, error: fetchError } = await supabase
      .from('users')
      .select('trial_prompt_dismiss_count, trial_prompt_last_dismissed')
      .eq('user_id', userId)
      .single();

    if (fetchError && fetchError.code !== 'PGRST116') {
      // PGRST116 = no rows found, which is okay for new users
      console.error('Error fetching user:', fetchError);
    }

    const currentDismissCount = existingUser?.trial_prompt_dismiss_count || 0;
    const newDismissCount = currentDismissCount + 1;
    const dismissTime = timestamp || Date.now();

    // Upsert user record with trial prompt info
    const { error: upsertError } = await supabase
      .from('users')
      .upsert({
        user_id: userId,
        email: email,
        trial_prompt_dismiss_count: newDismissCount,
        trial_prompt_last_dismissed: dismissTime,
        updated_at: new Date().toISOString()
      }, {
        onConflict: 'user_id'
      });

    if (upsertError) {
      console.error('Error updating user:', upsertError);
      return res.status(500).json({
        success: false,
        error: 'Failed to update user record'
      });
    }

    console.log(`✅ Trial prompt recorded. Dismiss count: ${newDismissCount}`);

    res.json({
      success: true,
      message: 'Trial prompt shown recorded',
      dismissCount: newDismissCount,
      nextShowAfter: new Date(dismissTime + 7 * 24 * 60 * 60 * 1000).toISOString()
    });

  } catch (error) {
    console.error('❌ Error recording trial prompt:', error);
    res.status(500).json({
      success: false,
      error: 'Internal server error',
      message: error.message
    });
  }
});

export default router;