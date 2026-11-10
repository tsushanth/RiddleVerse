-- Applied 2026-11-10 (no duplicate or empty ids existed: 128 rows, 125 NULL ids, 3 distinct non-null ids).
-- Forward: one ledger row per store transaction id; NULL ids (spends, legacy rows) are unconstrained.
create unique index if not exists coin_transactions_iap_transaction_id_uniq
  on public.coin_transactions (iap_transaction_id)
  where iap_transaction_id is not null;
-- Rollback:
--   drop index if exists public.coin_transactions_iap_transaction_id_uniq;
