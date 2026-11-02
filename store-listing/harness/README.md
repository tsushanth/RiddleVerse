# RiddleVerse Play listing harness

Python 3 (stdlib; `google-api-python-client` + `google-auth` only for the Publisher API and the Cloud Storage read).
Run from this directory with `/opt/homebrew/bin/python3.14 -m listing_harness <command>`. Tests: `python3.14 -m pytest -q tests`.
Nothing here runs on a schedule; every command is run by hand. Dry-run is the default for anything that talks to Play.

## What Google Play lets us automate (checked 2026-11-01)

| Thing | API? |
|---|---|
| Store listing experiments (create, read, results) | **No.** Not in androidpublisher v3 (`edits.listings`/`edits.images` are the only listing resources), not in the Play Developer Reporting API (v1beta1: vitals/anomalies/errors only). Console UI only. |
| Custom store listings (up to 50) | **No API found.** Console UI only (docs describe only the Console). |
| Update default/localized listing text and images | **Yes** (`edits.listings.update`, `edits.images.deleteall/upload`, then `edits.commit`). This is what `stage` uses. |
| Store listing visitors / acquisitions / conversion | Only via the **Cloud Storage bulk export** `gs://pubsite_prod_rev_<DEVELOPER_ID>/stats/store_performance/store_performance_<package>_<yyyymm>_<country|traffic_source>.csv`, posted 3-7 days late, or manual Console CSV download. Needs the numeric Play developer id and the permission "View app information and download bulk reports". |

Docs: [experiments](https://support.google.com/googleplay/android-developer/answer/6227309),
[bulk reports / GCS export](https://support.google.com/googleplay/android-developer/answer/6135870),
[custom listings](https://support.google.com/googleplay/android-developer/answer/9867158),
[asset specs](https://support.google.com/googleplay/android-developer/answer/9866151),
[publisher REST](https://developers.google.com/android-publisher/api-ref/rest),
[reporting API](https://developers.google.com/play/developer/reporting/reference/rest).

Console experiments: one default-graphics experiment OR up to five localized experiments at a time; up to 2 variants against the
current listing; default-graphics tests cannot test text; localized tests can test text and graphics; 6 month cap; the Console picks
and shows the required sample.

## Commands

```
power [--cvr 0.25] [--visitors 10,50,200] [--days 14,28,56]   minimum detectable effect table
catalog                          validate catalog.json (copy catalog.example.json first)
ingest --csv FILE | (no flag = GCS: needs PLAY_DEVELOPER_ID + PLAY_SA_JSON)
next [--visitors-per-day N --cvr P]   what the scheduler would do now (read-only)
snapshot                         capture ALL locales + image bytes into runtime/snapshots/ (read-only API calls)
stage --variant ID               DRY-RUN: shows the diff, writes nothing
stage --variant ID --commit      writes (also needs env LISTING_HARNESS_ALLOW_COMMIT=1)
rollback --locale en-US [--commit]  restore the latest snapshot through the same guarded path
start-baseline | mark-live W --date D | analyze [--interim] | set-groups --treated US --control IN,BR
report [--out FILE]              markdown run report
```
Env: `PLAY_SA_JSON` (service-account key file, never commit it), `PLAY_DEVELOPER_ID`, `LISTING_HARNESS_HOME` (default `runtime/`, git-ignored).

## Manual metrics CSV (works today)

`date,visitors,installs[,country][,traffic_source]` with ISO dates (`2026-11-01`), integer counts, `installs <= visitors`. One kind of row per
store (totals, or per country, or per source; mixing double counts and `ingest` warns). Rows are upserted by (date, country, source), so
re-dropping a revised export overwrites. Take the numbers from Console > Statistics (or Grow users > Store performance), metrics
"Store listing visitors" and "Store listing acquisitions" (first-time installers). Use organic/explore+search traffic only if you can filter it.
The GCS file parser matches columns loosely (`Date`, `Store Listing Visitors`, `Store Listing Acquisitions`, `Country`/`Traffic Source`);
the real header names are unverified until the first real file is read. Console data is Pacific Time.

## Design

* `catalog.py` variants (null field = unchanged, so a variant can change one factor); `validators.py` limits (title 30, short 80, full 4000, icon 512x512 32-bit PNG, feature 1024x500, screenshots 2-8, 320-3840 px, max 2:1).
* `scheduler.py` ABAB sequential windows (baseline, candidate, baseline, candidate; an adopted candidate becomes the baseline). Window length comes from the power calculation (whole weeks, 14-56 days). One open window, 3-day cool-down, owner confirms the go-live date (review takes hours to ~7 days), 1 ramp day dropped, results unreadable until window + 7-day metrics lag have passed (peeking guard). If traffic cannot reach the pre-set effect size in 56 days it returns `blocked` and says to fix distribution.
* `analysis.py`/`stats.py` Beta-Binomial P(variant beats reference), 95% credible interval on relative lift, adopt at P>=0.95 / reject at <=0.05, drift check between the two baseline windows around a candidate, difference-in-differences against a control-country group (approximate: the export has countries, listings are by language).
* `stager.py` `GuardedPublisher` exposes only listing/image/edit methods (no tracks, releases, bundles, pricing). Dry-run cannot call a write method at all. Commit path: snapshot must exist and match live, plan must touch one existing locale's listing fields/images only, edit is re-read and other locales must be unchanged, then validate, then commit. Audit log: `runtime/audit.jsonl`.
* Known limit: Play serves the icon back as JPEG, so a snapshot cannot restore the icon; restore it from the source PNG by hand (rollback leaves the icon alone).
