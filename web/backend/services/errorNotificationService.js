// services/errorNotificationService.js
// Sends email notifications for unexpected server errors with silencing capability

import nodemailer from 'nodemailer';
import { supabase } from '../config/database.js';

// In-memory cache for silenced errors and rate limiting
const errorCache = {
    silenced: new Map(),        // Error patterns to silence
    recentErrors: new Map(),    // Recent errors for deduplication
    sentToday: 0,               // Count of emails sent today
    lastResetDate: new Date().toDateString()
};

// Configuration
const CONFIG = {
    MAX_EMAILS_PER_DAY: 50,
    ERROR_DEDUP_WINDOW_MS: 5 * 60 * 1000, // 5 minutes
    MAX_ERRORS_BEFORE_BATCH: 10,
    BATCH_WINDOW_MS: 60 * 1000, // 1 minute batch window
};

// Pending errors for batching
let pendingErrors = [];
let batchTimeout = null;

// Email transporter
const getTransporter = () => {
    return nodemailer.createTransport({
        service: 'gmail',
        auth: {
            user: process.env.EMAIL_USER || 'puzzleverseai@gmail.com',
            pass: process.env.EMAIL_PASSWORD || 'duoo ukes bjkx nanj'
        }
    });
};

/**
 * Generate error fingerprint for deduplication
 */
function getErrorFingerprint(error, context) {
    const message = error.message || String(error);
    const endpoint = context?.endpoint || 'unknown';
    // Use first line of stack trace for fingerprinting
    const stackLine = error.stack?.split('\n')[1]?.trim() || '';
    return `${endpoint}:${message.substring(0, 100)}:${stackLine.substring(0, 50)}`;
}

/**
 * Check if error should be silenced
 */
function isErrorSilenced(fingerprint, error, context) {
    // Check exact fingerprint match
    if (errorCache.silenced.has(fingerprint)) {
        const silenceInfo = errorCache.silenced.get(fingerprint);
        if (silenceInfo.until > Date.now()) {
            return true;
        }
        // Silence expired, remove it
        errorCache.silenced.delete(fingerprint);
    }

    // Check pattern matches
    for (const [pattern, info] of errorCache.silenced.entries()) {
        if (info.isPattern && info.until > Date.now()) {
            try {
                const regex = new RegExp(pattern, 'i');
                if (regex.test(error.message) || regex.test(context?.endpoint || '')) {
                    return true;
                }
            } catch (e) {
                // Invalid regex, skip
            }
        }
    }

    return false;
}

/**
 * Check if error was recently sent (deduplication)
 */
function wasRecentlySent(fingerprint) {
    const lastSent = errorCache.recentErrors.get(fingerprint);
    if (lastSent && Date.now() - lastSent < CONFIG.ERROR_DEDUP_WINDOW_MS) {
        return true;
    }
    return false;
}

/**
 * Reset daily counter if needed
 */
function checkDailyReset() {
    const today = new Date().toDateString();
    if (errorCache.lastResetDate !== today) {
        errorCache.sentToday = 0;
        errorCache.lastResetDate = today;
    }
}

/**
 * Silence an error pattern
 * @param {string} pattern - Error message pattern or fingerprint
 * @param {number} durationMs - How long to silence (default 24 hours)
 * @param {boolean} isPattern - Whether pattern is a regex
 */
export function silenceError(pattern, durationMs = 24 * 60 * 60 * 1000, isPattern = false) {
    errorCache.silenced.set(pattern, {
        until: Date.now() + durationMs,
        isPattern,
        silencedAt: new Date().toISOString()
    });
    console.log(`🔇 Silenced error pattern: ${pattern} for ${durationMs / 1000 / 60} minutes`);
}

/**
 * Unsilence an error pattern
 */
export function unsilenceError(pattern) {
    if (errorCache.silenced.delete(pattern)) {
        console.log(`🔊 Unsilenced error pattern: ${pattern}`);
        return true;
    }
    return false;
}

/**
 * Get list of silenced errors
 */
export function getSilencedErrors() {
    const result = [];
    for (const [pattern, info] of errorCache.silenced.entries()) {
        if (info.until > Date.now()) {
            result.push({
                pattern,
                ...info,
                remainingMs: info.until - Date.now()
            });
        }
    }
    return result;
}

/**
 * Clear all silenced errors
 */
export function clearAllSilences() {
    errorCache.silenced.clear();
    console.log('🔊 Cleared all error silences');
}

/**
 * Send batched error notification
 */
async function sendBatchedErrors() {
    if (pendingErrors.length === 0) return;

    checkDailyReset();
    if (errorCache.sentToday >= CONFIG.MAX_EMAILS_PER_DAY) {
        console.warn('📧 Daily email limit reached, skipping error notification');
        pendingErrors = [];
        return;
    }

    const errors = [...pendingErrors];
    pendingErrors = [];
    batchTimeout = null;

    try {
        const transporter = getTransporter();
        const alertEmail = process.env.ALERT_EMAIL || 'puzzleverseai@gmail.com';

        const subject = `🚨 RiddleVerse: ${errors.length} Server Error${errors.length > 1 ? 's' : ''}`;

        const errorsHtml = errors.map((err, idx) => `
            <div style="background: #f8f9fa; border-left: 4px solid #dc3545; padding: 15px; margin: 10px 0; border-radius: 4px;">
                <h3 style="margin: 0 0 10px 0; color: #dc3545;">Error ${idx + 1}: ${err.context?.endpoint || 'Unknown'}</h3>
                <p><strong>Message:</strong> ${err.error.message || String(err.error)}</p>
                <p><strong>Time:</strong> ${err.timestamp}</p>
                ${err.context?.method ? `<p><strong>Method:</strong> ${err.context.method}</p>` : ''}
                ${err.context?.userAgent ? `<p><strong>User-Agent:</strong> ${err.context.userAgent.substring(0, 100)}</p>` : ''}
                ${err.context?.ip ? `<p><strong>IP:</strong> ${err.context.ip}</p>` : ''}
                <details style="margin-top: 10px;">
                    <summary style="cursor: pointer; color: #6c757d;">Stack Trace</summary>
                    <pre style="background: #212529; color: #f8f9fa; padding: 10px; border-radius: 4px; overflow-x: auto; font-size: 11px;">${err.error.stack || 'No stack trace'}</pre>
                </details>
                <p style="margin-top: 10px;">
                    <strong>Silence this error:</strong><br>
                    <code style="background: #e9ecef; padding: 2px 6px; border-radius: 3px; font-size: 11px;">
                        POST /api/admin/silence-error { "fingerprint": "${err.fingerprint}" }
                    </code>
                </p>
            </div>
        `).join('');

        const html = `
            <html>
            <body style="font-family: Arial, sans-serif; padding: 20px; max-width: 800px; margin: 0 auto;">
                <h1 style="color: #dc3545; border-bottom: 2px solid #dc3545; padding-bottom: 10px;">
                    🚨 Server Error Alert
                </h1>
                <p style="color: #6c757d;">
                    ${errors.length} unexpected error${errors.length > 1 ? 's' : ''} occurred on the RiddleVerse backend.
                </p>

                ${errorsHtml}

                <hr style="margin: 30px 0;">
                <h2>Quick Actions</h2>
                <ul>
                    <li><a href="https://console.cloud.google.com/run/detail/us-central1/quiz-web-frontend/logs">View Cloud Run Logs</a></li>
                    <li><a href="https://puzzleverseai.com/api/health/detailed">Check Health Status</a></li>
                </ul>

                <p style="color: #6c757d; font-size: 12px; margin-top: 30px;">
                    Emails sent today: ${errorCache.sentToday + 1}/${CONFIG.MAX_EMAILS_PER_DAY}<br>
                    To silence errors, use the admin API or reply with the fingerprint.
                </p>
            </body>
            </html>
        `;

        await transporter.sendMail({
            from: '"RiddleVerse Errors" <puzzleverseai@gmail.com>',
            to: alertEmail,
            subject,
            html
        });

        errorCache.sentToday++;
        errors.forEach(err => {
            errorCache.recentErrors.set(err.fingerprint, Date.now());
        });

        console.log(`📧 Error notification sent (${errors.length} errors)`);

    } catch (err) {
        console.error('Failed to send error notification:', err.message);
    }
}

/**
 * Queue error for notification
 */
function queueError(error, context, fingerprint) {
    pendingErrors.push({
        error: {
            message: error.message,
            stack: error.stack,
            name: error.name
        },
        context,
        fingerprint,
        timestamp: new Date().toISOString()
    });

    // Send immediately if we have many errors
    if (pendingErrors.length >= CONFIG.MAX_ERRORS_BEFORE_BATCH) {
        if (batchTimeout) {
            clearTimeout(batchTimeout);
            batchTimeout = null;
        }
        sendBatchedErrors();
    } else if (!batchTimeout) {
        // Otherwise batch for a minute
        batchTimeout = setTimeout(sendBatchedErrors, CONFIG.BATCH_WINDOW_MS);
    }
}

/**
 * Report an unexpected error
 * @param {Error} error - The error object
 * @param {Object} context - Context about where the error occurred
 */
export async function reportError(error, context = {}) {
    const fingerprint = getErrorFingerprint(error, context);

    // Check if silenced
    if (isErrorSilenced(fingerprint, error, context)) {
        console.log(`🔇 Silenced error: ${error.message?.substring(0, 50)}`);
        return { notified: false, reason: 'silenced' };
    }

    // Check if recently sent
    if (wasRecentlySent(fingerprint)) {
        console.log(`⏭️ Skipped duplicate error: ${error.message?.substring(0, 50)}`);
        return { notified: false, reason: 'duplicate' };
    }

    // Queue for notification
    queueError(error, context, fingerprint);
    return { notified: true, fingerprint };
}

/**
 * Express middleware for automatic error reporting
 */
export function errorReportingMiddleware(err, req, res, next) {
    // Only report 500 errors
    const statusCode = err.statusCode || 500;
    if (statusCode >= 500) {
        reportError(err, {
            endpoint: req.path,
            method: req.method,
            userAgent: req.get('User-Agent'),
            ip: req.ip,
            query: req.query,
            body: req.body ? JSON.stringify(req.body).substring(0, 200) : undefined
        });
    }
    next(err);
}

/**
 * Log error to Supabase for tracking
 */
export async function logErrorToDatabase(error, context = {}) {
    try {
        if (!supabase) return;

        await supabase.from('error_logs').insert([{
            error_message: error.message?.substring(0, 500),
            error_stack: error.stack?.substring(0, 2000),
            endpoint: context.endpoint,
            method: context.method,
            user_agent: context.userAgent?.substring(0, 200),
            ip_address: context.ip,
            created_at: new Date().toISOString()
        }]);
    } catch (err) {
        // Don't fail on logging errors
        console.error('Failed to log error to database:', err.message);
    }
}

export default {
    reportError,
    silenceError,
    unsilenceError,
    getSilencedErrors,
    clearAllSilences,
    errorReportingMiddleware,
    logErrorToDatabase
};
