// routes/subscription.routes.js - Subscription management endpoints
import express from 'express';
import {
    handleSubscriptionPurchase,
    getSubscriptionStatus,
    syncSubscriptionStatus,
    cancelSubscription,
    getSubscriptionMetrics
} from '../services/subscription-purchase.js';

const router = express.Router();

/**
 * Handle subscription purchase
 */
router.post('/subscription-purchase', async (req, res) => {
    await handleSubscriptionPurchase(req, res);
});

/**
 * Get current subscription status
 */
router.get('/subscription-status', async (req, res) => {
    await getSubscriptionStatus(req, res);
});

/**
 * Sync subscription status from app
 */
router.post('/sync-subscription-status', async (req, res) => {
    await syncSubscriptionStatus(req, res);
});

/**
 * Cancel subscription
 */
router.post('/subscription-cancel', async (req, res) => {
    await cancelSubscription(req, res);
});

/**
 * Get subscription metrics (admin endpoint)
 */
router.get('/subscription-metrics', async (req, res) => {
    await getSubscriptionMetrics(req, res);
});

export default router;
