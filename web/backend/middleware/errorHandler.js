/**
 * Centralized error handling middleware
 */

/**
 * Custom error class for API errors
 */
export class ApiError extends Error {
    constructor(statusCode, message, details = null) {
        super(message);
        this.statusCode = statusCode;
        this.details = details;
        this.isOperational = true;
        Error.captureStackTrace(this, this.constructor);
    }

    static badRequest(message, details = null) {
        return new ApiError(400, message, details);
    }

    static unauthorized(message = 'Unauthorized') {
        return new ApiError(401, message);
    }

    static forbidden(message = 'Forbidden') {
        return new ApiError(403, message);
    }

    static notFound(message = 'Resource not found') {
        return new ApiError(404, message);
    }

    static tooManyRequests(message = 'Too many requests') {
        return new ApiError(429, message);
    }

    static internal(message = 'Internal server error') {
        return new ApiError(500, message);
    }
}

/**
 * Async handler wrapper to catch errors in async route handlers
 * @param {Function} fn - Async route handler function
 * @returns {Function} - Wrapped function that catches errors
 */
export const asyncHandler = (fn) => (req, res, next) => {
    Promise.resolve(fn(req, res, next)).catch(next);
};

/**
 * Not found handler - catches 404 errors
 */
export const notFoundHandler = (req, res, next) => {
    // Skip for static files and well-known paths
    if (req.path.startsWith('/.well-known') ||
        req.path.endsWith('.js') ||
        req.path.endsWith('.css') ||
        req.path.endsWith('.html')) {
        return next();
    }

    const error = new ApiError(404, `Route not found: ${req.method} ${req.path}`);
    next(error);
};

/**
 * Global error handler middleware
 * This should be the last middleware in the chain
 */
export const errorHandler = (err, req, res, next) => {
    // If response already sent, delegate to default Express error handler
    if (res.headersSent) {
        return next(err);
    }

    // Default error values
    let statusCode = err.statusCode || 500;
    let message = err.message || 'Internal server error';
    let details = err.details || null;

    // Handle specific error types
    if (err.name === 'ValidationError') {
        statusCode = 400;
        message = 'Validation error';
        details = err.errors;
    } else if (err.name === 'JsonWebTokenError') {
        statusCode = 401;
        message = 'Invalid token';
    } else if (err.name === 'TokenExpiredError') {
        statusCode = 401;
        message = 'Token expired';
    } else if (err.code === 'ECONNREFUSED') {
        statusCode = 503;
        message = 'Service unavailable';
    } else if (err.code === 'ETIMEDOUT') {
        statusCode = 504;
        message = 'Request timeout';
    }

    // Log error for debugging (only log 5xx errors or unexpected errors)
    if (statusCode >= 500 || !err.isOperational) {
        console.error(`[ERROR] ${req.method} ${req.path}:`, {
            statusCode,
            message: err.message,
            stack: err.stack,
            timestamp: new Date().toISOString()
        });
    }

    // Send error response
    const response = {
        success: false,
        error: {
            message,
            ...(details && { details }),
            ...(process.env.NODE_ENV === 'development' && { stack: err.stack })
        },
        timestamp: new Date().toISOString()
    };

    res.status(statusCode).json(response);
};

/**
 * Request logging middleware (optional - for debugging)
 */
export const requestLogger = (req, res, next) => {
    const start = Date.now();

    res.on('finish', () => {
        const duration = Date.now() - start;
        const logLevel = res.statusCode >= 400 ? 'warn' : 'info';

        // Only log slow requests or errors in production
        if (process.env.NODE_ENV === 'production') {
            if (duration > 1000 || res.statusCode >= 400) {
                console[logLevel](`${req.method} ${req.path} - ${res.statusCode} (${duration}ms)`);
            }
        } else {
            console.log(`${req.method} ${req.path} - ${res.statusCode} (${duration}ms)`);
        }
    });

    next();
};

export default {
    ApiError,
    asyncHandler,
    notFoundHandler,
    errorHandler,
    requestLogger
};
