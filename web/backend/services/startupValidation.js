// services/startupValidation.js
// Validates required environment variables and dependencies at startup

import nodemailer from 'nodemailer';

const REQUIRED_ENV_VARS = [
    { name: 'SUPABASE_URL', description: 'Supabase REST API URL' },
    { name: 'SUPABASE_ANON_KEY', description: 'Supabase anonymous key' },
    { name: 'FIREBASE_SERVICE_ACCOUNT_KEY', description: 'Firebase service account JSON' },
];

const OPTIONAL_BUT_RECOMMENDED = [
    { name: 'OPENAI_API_KEY', description: 'OpenAI API key for puzzle generation' },
    { name: 'ANTHROPIC_API_KEY', description: 'Anthropic API key for AI features' },
    { name: 'DEEPSEEK_API_KEY', description: 'DeepSeek API key' },
    { name: 'EMAIL_USER', description: 'Email for sending notifications' },
    { name: 'EMAIL_PASSWORD', description: 'Email password/app password' },
    { name: 'IOS_API_KEY', description: 'iOS app API key' },
    { name: 'ANDROID_API_KEY', description: 'Android app API key' },
];

// Email configuration for alerts
const getTransporter = () => {
    const user = process.env.EMAIL_USER || 'puzzleverseai@gmail.com';
    const pass = process.env.EMAIL_PASSWORD || 'duoo ukes bjkx nanj';

    return nodemailer.createTransport({
        service: 'gmail',
        auth: { user, pass }
    });
};

/**
 * Validate all required environment variables
 * @returns {{ valid: boolean, missing: string[], warnings: string[] }}
 */
export function validateEnvironment() {
    const missing = [];
    const warnings = [];

    // Check required vars
    for (const envVar of REQUIRED_ENV_VARS) {
        const value = process.env[envVar.name];
        if (!value || value.trim() === '') {
            missing.push(`${envVar.name} - ${envVar.description}`);
        }
    }

    // Check optional but recommended vars
    for (const envVar of OPTIONAL_BUT_RECOMMENDED) {
        const value = process.env[envVar.name];
        if (!value || value.trim() === '') {
            warnings.push(`${envVar.name} - ${envVar.description}`);
        }
    }

    return {
        valid: missing.length === 0,
        missing,
        warnings
    };
}

/**
 * Validate specific service connections
 * @returns {Promise<{ supabase: boolean, firebase: boolean, errors: string[] }>}
 */
export async function validateConnections() {
    const errors = [];
    let supabaseOk = false;
    let firebaseOk = false;

    // Check Supabase
    try {
        const { supabase } = await import('../config/database.js');
        if (supabase) {
            const { error } = await supabase.from('puzzles').select('count').limit(1);
            supabaseOk = !error;
            if (error) errors.push(`Supabase: ${error.message}`);
        } else {
            errors.push('Supabase: Client is null - missing credentials');
        }
    } catch (err) {
        errors.push(`Supabase: ${err.message}`);
    }

    // Check Firebase
    try {
        const { db } = await import('../config/firebaseAdmin.js');
        if (db) {
            await db.collection('users').limit(1).get();
            firebaseOk = true;
        } else {
            errors.push('Firebase: Client is null - missing credentials');
        }
    } catch (err) {
        errors.push(`Firebase: ${err.message}`);
    }

    return { supabase: supabaseOk, firebase: firebaseOk, errors };
}

/**
 * Send deployment alert email
 */
export async function sendDeploymentAlert(status, details) {
    try {
        const transporter = getTransporter();
        const alertEmail = process.env.ALERT_EMAIL || 'puzzleverseai@gmail.com';

        const statusEmoji = status === 'success' ? '✅' : '❌';
        const subject = `${statusEmoji} RiddleVerse Deployment ${status.toUpperCase()}`;

        const html = `
            <html>
            <body style="font-family: Arial, sans-serif; padding: 20px;">
                <h1 style="color: ${status === 'success' ? '#28a745' : '#dc3545'};">
                    ${statusEmoji} Deployment ${status.toUpperCase()}
                </h1>
                <p><strong>Time:</strong> ${new Date().toISOString()}</p>
                <p><strong>Environment:</strong> ${process.env.NODE_ENV || 'unknown'}</p>

                ${details.missing?.length > 0 ? `
                    <h2 style="color: #dc3545;">❌ Missing Required Variables</h2>
                    <ul>
                        ${details.missing.map(v => `<li>${v}</li>`).join('')}
                    </ul>
                ` : ''}

                ${details.warnings?.length > 0 ? `
                    <h2 style="color: #ffc107;">⚠️ Missing Optional Variables</h2>
                    <ul>
                        ${details.warnings.map(v => `<li>${v}</li>`).join('')}
                    </ul>
                ` : ''}

                ${details.connectionErrors?.length > 0 ? `
                    <h2 style="color: #dc3545;">🔌 Connection Errors</h2>
                    <ul>
                        ${details.connectionErrors.map(e => `<li>${e}</li>`).join('')}
                    </ul>
                ` : ''}

                ${status === 'success' ? `
                    <h2 style="color: #28a745;">✅ All Systems Operational</h2>
                    <ul>
                        <li>Supabase: ${details.connections?.supabase ? '✅ Connected' : '❌ Failed'}</li>
                        <li>Firebase: ${details.connections?.firebase ? '✅ Connected' : '❌ Failed'}</li>
                    </ul>
                ` : ''}

                <hr>
                <p style="color: #6c757d; font-size: 12px;">
                    Automated deployment notification from RiddleVerse Backend
                </p>
            </body>
            </html>
        `;

        await transporter.sendMail({
            from: '"RiddleVerse Deployment" <puzzleverseai@gmail.com>',
            to: alertEmail,
            subject,
            html
        });

        console.log(`📧 Deployment ${status} alert sent`);
    } catch (err) {
        console.error('Failed to send deployment alert:', err.message);
    }
}

/**
 * Run full startup validation
 * @param {boolean} exitOnFailure - Whether to exit process on validation failure
 * @returns {Promise<boolean>}
 */
export async function runStartupValidation(exitOnFailure = true) {
    console.log('\n🔍 Running startup validation...\n');

    // Skip validation during Docker build/smoke tests
    if (process.env.DOCKER_BUILD === 'true') {
        console.log('⏭️ Skipping validation during Docker build');
        return true;
    }

    // 1. Validate environment variables
    const envResult = validateEnvironment();

    if (envResult.missing.length > 0) {
        console.error('❌ MISSING REQUIRED ENVIRONMENT VARIABLES:');
        envResult.missing.forEach(v => console.error(`   - ${v}`));
    }

    if (envResult.warnings.length > 0) {
        console.warn('⚠️ MISSING OPTIONAL ENVIRONMENT VARIABLES:');
        envResult.warnings.forEach(v => console.warn(`   - ${v}`));
    }

    // 2. Validate connections (only if env vars are present)
    let connectionResult = { supabase: false, firebase: false, errors: [] };
    if (envResult.valid) {
        connectionResult = await validateConnections();

        if (connectionResult.errors.length > 0) {
            console.error('❌ CONNECTION ERRORS:');
            connectionResult.errors.forEach(e => console.error(`   - ${e}`));
        }
    }

    // 3. Determine overall status
    const isValid = envResult.valid && connectionResult.errors.length === 0;

    // 4. Send email notification
    await sendDeploymentAlert(isValid ? 'success' : 'failure', {
        missing: envResult.missing,
        warnings: envResult.warnings,
        connectionErrors: connectionResult.errors,
        connections: connectionResult
    });

    // 5. Handle failure
    if (!isValid) {
        console.error('\n❌ STARTUP VALIDATION FAILED\n');

        if (exitOnFailure && !envResult.valid) {
            console.error('🛑 Exiting due to missing required environment variables');
            process.exit(1);
        }
    } else {
        console.log('\n✅ STARTUP VALIDATION PASSED\n');
        console.log(`   Supabase: ${connectionResult.supabase ? '✅' : '❌'}`);
        console.log(`   Firebase: ${connectionResult.firebase ? '✅' : '❌'}`);
    }

    return isValid;
}

export default {
    validateEnvironment,
    validateConnections,
    sendDeploymentAlert,
    runStartupValidation
};
