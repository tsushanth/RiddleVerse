// routes/coins.routes.js - Virtual currency endpoints
import express from 'express';
import {
    getCoinBalance,
    purchaseCoins,
    spendCoins,
    getCreatorEarnings,
    getTransactionHistory,
    getCoinStore,
    awardCoins
} from '../services/coinService.js';

const router = express.Router();

// GET /api/coins/balance?userId=xxx
router.get('/balance', async (req, res) => {
    await getCoinBalance(req, res);
});

// GET /api/coins/store - Get available coin packs
router.get('/store', async (req, res) => {
    await getCoinStore(req, res);
});

// POST /api/coins/purchase - Record IAP coin purchase
router.post('/purchase', async (req, res) => {
    await purchaseCoins(req, res);
});

// POST /api/coins/spend - Spend coins (continue play, etc.)
router.post('/spend', async (req, res) => {
    await spendCoins(req, res);
});

// GET /api/coins/earnings?creatorId=xxx - Creator earnings
router.get('/earnings', async (req, res) => {
    await getCreatorEarnings(req, res);
});

// GET /api/coins/transactions?userId=xxx - Transaction history
router.get('/transactions', async (req, res) => {
    await getTransactionHistory(req, res);
});

// POST /api/coins/award - Award coins (rating reward, etc.)
router.post('/award', async (req, res) => {
    await awardCoins(req, res);
});

export default router;
