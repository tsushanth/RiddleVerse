import { createClient } from '@supabase/supabase-js';

const supabaseUrl = process.env.SUPABASE_URL;
// Server-side database access uses the service credential. The public anon key is only used to verify end-user tokens (auth/verifyIdentity.js).
// Fly names the secret SUPABASE_SERVICE_KEY; accept the Supabase-standard name too.
const supabaseKey = process.env.SUPABASE_SERVICE_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY;

// Allow smoke tests to run during Docker build (env vars not yet available)
const IS_SMOKE_TEST = process.env.DOCKER_BUILD === 'true' ||
                       process.argv.some(arg => arg.includes('smoke-tests'));

let supabase = null;

if (!supabaseUrl || !supabaseKey) {
    if (IS_SMOKE_TEST) {
        console.warn('⚠️ SUPABASE credentials not available during smoke test - skipping client initialization');
    } else {
        throw new Error('Missing SUPABASE_URL or SUPABASE_SERVICE_KEY environment variables');
    }
} else {
    supabase = createClient(supabaseUrl, supabaseKey, { auth: { persistSession: false, autoRefreshToken: false } });
}

export { supabase };
