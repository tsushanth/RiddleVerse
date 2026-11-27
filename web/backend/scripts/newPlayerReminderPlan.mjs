// Read-only preview of today's new-player reminder: prints how many people would get it. Sends and writes nothing,
// prints no address or token. Run on the server: node scripts/newPlayerReminderPlan.mjs
import { supabase } from '../config/database.js';
import { admin } from '../config/firebaseAdmin.js';
import { planReminder, reminderConfig, REMINDER_TEXT } from '../services/newPlayerReminder.js';

const config = reminderConfig();
const { counts } = await planReminder({ db: supabase, auth: { getUsers: (ids) => admin.auth().getUsers(ids) }, config });
console.log(JSON.stringify({ config, counts, draftText: REMINDER_TEXT.en }, null, 2));
process.exit(0);
