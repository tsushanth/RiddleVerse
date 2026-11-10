// The shared server-side database client must authenticate with the service credential, never the public anon key.
import { test } from 'node:test';
import assert from 'node:assert/strict';

process.env.SUPABASE_URL = 'http://supabase.test';
process.env.SUPABASE_ANON_KEY = 'anon-public-key';
process.env.SUPABASE_SERVICE_KEY = 'service-secret-key';
delete process.env.SUPABASE_SERVICE_ROLE_KEY;

const { supabase } = await import('../config/database.js');

test('shared client uses the service credential, not the anon key', () => {
  assert.equal(supabase.supabaseKey, 'service-secret-key');
  assert.notEqual(supabase.supabaseKey, process.env.SUPABASE_ANON_KEY);
});
