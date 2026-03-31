// payout.routes.js - Creator payout endpoints
import express from 'express';
import { verifyCreator } from '../middleware/payoutAuth.js';
import {
    getPayoutEligibility,
    connectStripe,
    getStripeStatus,
    requestPayout,
    getPayoutHistory,
    connectReturn,
    connectRefresh
} from '../services/payoutService.js';

const router = express.Router();

// GET /api/payouts/eligibility?creatorId=X
router.get('/eligibility', async (req, res) => {
    await getPayoutEligibility(req, res);
});

// POST /api/payouts/connect-stripe (auth required)
router.post('/connect-stripe', verifyCreator, async (req, res) => {
    await connectStripe(req, res);
});

// GET /api/payouts/stripe-status?creatorId=X
router.get('/stripe-status', async (req, res) => {
    await getStripeStatus(req, res);
});

// POST /api/payouts/request (auth required)
router.post('/request', verifyCreator, async (req, res) => {
    await requestPayout(req, res);
});

// GET /api/payouts/history?creatorId=X
router.get('/history', async (req, res) => {
    await getPayoutHistory(req, res);
});

// Stripe redirect pages (user lands here after onboarding)
router.get('/connect-return', (req, res) => {
    connectReturn(req, res);
});

router.get('/connect-refresh', (req, res) => {
    connectRefresh(req, res);
});

export default router;
