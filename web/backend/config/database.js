import { createClient } from '@supabase/supabase-js';

const supabaseUrl = process.env.SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_ANON_KEY;

// Allow smoke tests to run during Docker build (env vars not yet available)
const IS_SMOKE_TEST = process.env.DOCKER_BUILD === 'true' ||
                       process.argv.some(arg => arg.includes('smoke-tests'));

let supabase = null;

if (!supabaseUrl || !supabaseKey) {
    if (IS_SMOKE_TEST) {
        console.warn('⚠️ SUPABASE credentials not available during smoke test - skipping client initialization');
    } else {
        throw new Error('Missing SUPABASE_URL or SUPABASE_ANON_KEY environment variables');
    }
} else {
    supabase = createClient(supabaseUrl, supabaseKey);
}

export { supabase };