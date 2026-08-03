import rateLimit from 'express-rate-limit';
import cors from 'cors';

// General rate limiting for all endpoints
export const generalLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 200, // limit each IP to 200 requests per 15 minutes
    message: { 
        error: "Too many requests from this IP, please try again later.",
        retryAfter: "15 minutes"
    },
    standardHeaders: true,
    legacyHeaders: false,
});

// Strict rate limiting for expensive puzzle generation
export const generationLimiter = rateLimit({
    windowMs: 60 * 60 * 1000, // 1 hour
    max: 20, // limit each IP to 20 generation requests per hour
    message: { 
        error: "Too many puzzle generation requests. Limit: 20 per hour.",
        retryAfter: "1 hour"
    }
});

// Very strict for admin/dangerous operations
export const adminLimiter = rateLimit({
    windowMs: 60 * 60 * 1000, // 1 hour
    max: 1000,
    message: {
        error: "Too many admin requests. Please try again later.",
        retryAfter: "1 hour"
    }
});

export const notificationLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 50,
    message: {
        error: "Too many notification requests from this IP, please try again later.",
        retryAfter: "15 minutes"
    }
});

// Stricter rate limit for sending notifications
export const sendNotificationLimiter = rateLimit({
    windowMs: 60 * 60 * 1000, // 1 hour
    max: 10,
    message: {
        error: "Too many notification send requests. Limit: 10 per hour.",
        retryAfter: "1 hour"
    }
});

// Rate limiting for RiddleVerse app endpoints
export const riddleVerseRateLimit = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 500,
    skip: (req) => {
        // Skip rate limiting for authenticated users and Telegram bot
        const { userId, email } = req.query;
        const ua = req.get('User-Agent') || '';
        return (userId && email && userId !== "" && email !== "") || ua.includes('TelegramBot');
    },
    keyGenerator: (req) => {
        // Use IP + User-Agent for more granular rate limiting
        const userAgent = req.get('User-Agent') || '';
        return `${req.ip}-${userAgent.substring(0, 50)}`;
    },
    handler: async (req, res) => {
        res.status(429).json({
            success: false,
            message: 'Too many requests. Please wait a moment and try again.'
        });
    }
});

export const batchFetchLimiter = rateLimit({
    windowMs: 60 * 60 * 1000, // 1 hour
    max: 100,
    message: {
        success: false,
        error: {
            code: "RATE_LIMIT_EXCEEDED",
            message: "Too many requests. Please try again later.",
            details: {
                retryAfter: 300,
                maxRequestsPerHour: 100
            }
        }
    },
    skip: (req) => !req.body?.userId
});

// CORS configuration
export const corsOptions = {
    origin: [
        "https://quiz-web-frontend.fly.dev",
        "http://localhost:3000", // for development
        "https://puzzleverseai.com"
    ],
    credentials: true
};

// Security middleware
export const securityMiddleware = (req, res, next) => {
    // Block malicious user agents
    const userAgent = req.headers['user-agent'] || '';
    if (userAgent.includes('ALittle Client')) {
        return res.status(403).json({ error: 'Forbidden' });
    }

    // Block common exploit paths
    const blockedPaths = [
        '/server/php/index.php',
        '/admin/server/php',
        '/sites/all/libraries/elfinder'
    ];
    if (blockedPaths.some(path => req.path.includes(path))) {
        return res.status(403).json({ error: 'Forbidden' });
    }

    // Rate limiting headers
    res.setHeader('X-RateLimit-Limit', '100');
    res.setHeader('X-RateLimit-Remaining', '99');
    
    next();
};