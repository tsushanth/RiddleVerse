import express from 'express';
import { performComprehensiveHealthCheck } from '../services/startupHealthCheck.js';
import { wordFrequencyManager } from '../services/wordFrequencyManager.js';
import { supabase } from '../config/database.js';

const router = express.Router();

// Health check cache
let lastHealthCheck = null;
let healthCheckInProgress = false;
const HEALTH_CHECK_CACHE_DURATION = 5 * 60 * 1000; // 5 minutes

// Comprehensive health check for system startup verification
router.get('/comprehensive', async (req, res) => {
    try {
        const forceCheck = req.query.force === 'true';
        
        // Return cached result if recent and not forced
        if (!forceCheck && lastHealthCheck && 
            (Date.now() - lastHealthCheck.timestamp) < HEALTH_CHECK_CACHE_DURATION) {
            console.log('Returning cached health check result');
            return res.status(lastHealthCheck.healthy ? 200 : 503).json(lastHealthCheck);
        }

        // Prevent concurrent health checks
        if (healthCheckInProgress) {
            return res.status(429).json({
                healthy: false,
                status: 'health_check_in_progress',
                message: 'Health check already in progress',
                timestamp: new Date().toISOString()
            });
        }

        healthCheckInProgress = true;
        console.log('Starting comprehensive health check...');

        const healthResult = await performComprehensiveHealthCheck();
        
        // Cache the result
        lastHealthCheck = {
            ...healthResult,
            timestamp: Date.now()
        };

        const statusCode = healthResult.healthy ? 200 : 503;
        res.status(statusCode).json(healthResult);

    } catch (error) {
        console.error('Health check failed with error:', error);
        
        const errorResult = {
            healthy: false,
            status: 'error',
            message: 'Health check failed with exception',
            error: error.message,
            timestamp: new Date().toISOString()
        };

        res.status(503).json(errorResult);
    } finally {
        healthCheckInProgress = false;
    }
});

// Quick health check for Docker
router.get('/', (req, res) => {
    // Quick health check for Docker
    if (lastHealthCheck && (Date.now() - lastHealthCheck.timestamp) < HEALTH_CHECK_CACHE_DURATION) {
        const statusCode = lastHealthCheck.healthy ? 200 : 503;
        return res.status(statusCode).json({
            healthy: lastHealthCheck.healthy,
            status: lastHealthCheck.status,
            message: lastHealthCheck.message,
            lastFullCheck: new Date(lastHealthCheck.timestamp).toISOString(),
            uptime: process.uptime()
        });
    }

    // Fallback basic check
    res.status(200).json({
        healthy: true,
        status: 'basic_healthy',
        message: 'Server is running (full check not completed)',
        uptime: process.uptime(),
        timestamp: new Date().toISOString()
    });
});

// Detailed health information
router.get('/detailed', async (req, res) => {
    try {
        const detailed = {
            server: {
                uptime: process.uptime(),
                memory: process.memoryUsage(),
                nodeVersion: process.version,
                platform: process.platform
            },
            lastHealthCheck: lastHealthCheck ? {
                timestamp: new Date(lastHealthCheck.timestamp).toISOString(),
                healthy: lastHealthCheck.healthy,
                status: lastHealthCheck.status,
                summary: lastHealthCheck.summary
            } : null,
            cache: {
                healthCheckInProgress,
                cacheAge: lastHealthCheck ? Date.now() - lastHealthCheck.timestamp : null
            }
        };

        res.json(detailed);

    } catch (error) {
        res.status(500).json({
            error: 'Failed to get detailed health status',
            message: error.message
        });
    }
});

// Word manager status
router.get('/word-manager', (req, res) => {
    const stats = wordFrequencyManager.getStats();
    res.json({
        success: true,
        wordManager: stats,
        timestamp: new Date().toISOString()
    });
});

// Get current alerts
router.get('/alerts', async (req, res) => {
    try {
        const { severity, resolved = 'false', limit = 50 } = req.query;
        
        let query = supabase
            .from('system_alerts')
            .select('*')
            .order('timestamp', { ascending: false })
            .limit(parseInt(limit));
            
        if (severity) {
            query = query.eq('severity', severity);
        }
        
        if (resolved !== 'all') {
            query = query.eq('resolved', resolved === 'true');
        }
        
        const { data: alerts, error } = await query;
        
        if (error) throw error;
        
        res.json({
            success: true,
            alerts,
            count: alerts.length
        });
        
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Resolve alert
router.post('/alerts/:alertId/resolve', async (req, res) => {
    try {
        const { alertId } = req.params;
        const { resolvedBy = 'system' } = req.body;
        
        const { error } = await supabase
            .from('system_alerts')
            .update({
                resolved: true,
                resolved_at: new Date().toISOString(),
                resolved_by: resolvedBy
            })
            .eq('id', alertId);
            
        if (error) throw error;
        
        res.json({ success: true, message: 'Alert resolved' });
        
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

export default router;