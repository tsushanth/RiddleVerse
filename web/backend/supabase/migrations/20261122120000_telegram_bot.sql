-- Telegram bot, account linking and sharing events (ADDITIVE ONLY). NOT applied by anyone yet.
-- The Supabase project is shared with other apps: every new table here carries the rv_ prefix. Service role only:
-- RLS enabled, NO policies. Rollback: 20261122120000_telegram_bot.rollback.sql
-- Apply BEFORE setting the Telegram webhook (the bot dedupes updates and stores language and link state here).

create table if not exists public.rv_telegram_users (
  tg_user_id      bigint primary key,
  chat_id         bigint,                       -- private chat id (equals tg_user_id); null until the user messages the bot
  first_name      text,
  username        text,
  language        text not null default 'en',   -- resolved from Telegram language_code, changed with /language
  firebase_uid    text unique,                  -- the RiddleVerse (Firebase) account this Telegram user is linked to
  linked_at       timestamptz,
  remind_daily    boolean not null default false,
  last_daily_sent date,
  blocked         boolean not null default false,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create index if not exists rv_telegram_users_remind_idx on public.rv_telegram_users (remind_daily, blocked) where remind_daily;

-- One-time login/link codes. Only the SHA-256 of the code is stored; used_at is set by one conditional update (replay-safe).
create table if not exists public.rv_telegram_link_codes (
  code_hash   text primary key,
  tg_user_id  bigint not null references public.rv_telegram_users (tg_user_id) on delete cascade,
  expires_at  timestamptz not null,
  used_at     timestamptz,
  created_at  timestamptz not null default now()
);
create index if not exists rv_telegram_link_codes_user_idx on public.rv_telegram_link_codes (tg_user_id) where used_at is null;

-- Telegram update ids already handled (webhook retries). Rows older than 3 days are pruned by the daily push.
create table if not exists public.rv_telegram_updates (
  update_id    bigint primary key,
  received_at  timestamptz not null default now()
);
create index if not exists rv_telegram_updates_received_idx on public.rv_telegram_updates (received_at);

alter table public.rv_telegram_users      enable row level security;
alter table public.rv_telegram_link_codes enable row level security;
alter table public.rv_telegram_updates    enable row level security;
revoke all on table public.rv_telegram_users, public.rv_telegram_link_codes, public.rv_telegram_updates from anon, authenticated;

-- Sharing / channel analytics: share_sent, share_open, deeplink_open, telegram_cta carry a short channel label.
alter table public.puzzle_events add column if not exists channel text;
create index if not exists puzzle_events_channel_idx on public.puzzle_events (event_type, channel, ts desc)
  where event_type in ('share_sent', 'share_open', 'deeplink_open', 'telegram_cta', 'web_play_hit');
