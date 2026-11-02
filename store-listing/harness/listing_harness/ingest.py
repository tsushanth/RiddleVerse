"""Metrics ingestion. Normalised row: {date, country, source, visitors, installs}.

Two inputs:
  1. Manual CSV (the Console 'Store listing performance' / Statistics page, Download CSV, or a hand-typed file):
        date,visitors,installs[,country][,traffic_source]
     ISO dates, integer counts, installs <= visitors. country is an ISO-3166 alpha-2 code, traffic_source free text.
  2. Play Console bulk-report export in Cloud Storage: gs://pubsite_prod_rev_<DEVELOPER_ID>/stats/store_performance/
        store_performance_<package>_<yyyymm>_<country|traffic_source>.csv  (UTF-16LE with BOM, posted 3-7 days late).
     Column names are matched loosely (case/space/underscore-insensitive); they were NOT verified against a real file
     because the developer id is not available to us yet. The first real run must be eyeballed.
"""
from __future__ import annotations

import csv
import io
import json
import os
import re
import urllib.parse
import urllib.request
from datetime import date

GCS_SCOPE = "https://www.googleapis.com/auth/devstorage.read_only"


class NotConfigured(Exception):
    pass


def _norm(h: str) -> str:
    return re.sub(r"[^a-z0-9]", "", h.lower())


def decode(buf: bytes) -> str:
    if buf[:2] in (b"\xff\xfe", b"\xfe\xff"):
        return buf.decode("utf-16")
    return buf.decode("utf-8-sig")


def _int(v: str) -> int:
    v = (v or "").strip().replace(",", "")
    if v in ("", "-"):
        return 0
    return int(float(v))


def _find(heads: dict, *names):
    for n in names:
        if n in heads:
            return heads[n]
    return None


def parse_rows(text: str, expect_file_kind: str | None = None) -> list[dict]:
    reader = csv.reader(io.StringIO(text))
    rows = [r for r in reader if any(c.strip() for c in r)]
    if not rows:
        raise ValueError("empty CSV")
    heads = {_norm(h): i for i, h in enumerate(rows[0])}
    i_date = _find(heads, "date", "day")
    i_vis = _find(heads, "visitors", "storelistingvisitors", "listingvisitors")
    i_ins = _find(heads, "installs", "storelistingacquisitions", "acquisitions", "installers", "storelistinginstalls")
    i_cty = _find(heads, "country", "countryregion")
    i_src = _find(heads, "trafficsource", "source")
    missing = [n for n, i in (("date", i_date), ("visitors", i_vis), ("installs/acquisitions", i_ins)) if i is None]
    if missing:
        raise ValueError(f"CSV is missing columns {missing}; headers seen: {rows[0]}")
    out = []
    for ln, r in enumerate(rows[1:], start=2):
        try:
            dt = date.fromisoformat(r[i_date].strip()[:10])
            vis, ins = _int(r[i_vis]), _int(r[i_ins])
        except (ValueError, IndexError) as e:
            raise ValueError(f"line {ln}: {e}") from e
        if vis < 0 or ins < 0 or ins > vis:
            raise ValueError(f"line {ln}: installs {ins} > visitors {vis} or negative")
        out.append({"date": dt.isoformat(), "country": (r[i_cty].strip().upper() if i_cty is not None else ""),
                    "source": (r[i_src].strip() if i_src is not None else ""), "visitors": vis, "installs": ins})
    return out


def parse_file(path: str) -> list[dict]:
    with open(path, "rb") as f:
        return parse_rows(decode(f.read()))


class Store:
    """runtime/metrics.csv, upserted by (date, country, source): later exports overwrite revised numbers."""
    FIELDS = ["date", "country", "source", "visitors", "installs"]

    def __init__(self, path: str):
        self.path = path
        self.rows: dict[tuple, dict] = {}
        if os.path.exists(path):
            with open(path, newline="", encoding="utf-8") as f:
                for r in csv.DictReader(f):
                    r["visitors"], r["installs"] = int(r["visitors"]), int(r["installs"])
                    self.rows[(r["date"], r["country"], r["source"])] = r

    def upsert(self, rows: list[dict]) -> int:
        n = 0
        for r in rows:
            k = (r["date"], r["country"], r["source"])
            if self.rows.get(k) != r:
                n += 1
            self.rows[k] = r
        return n

    def save(self) -> None:
        os.makedirs(os.path.dirname(os.path.abspath(self.path)), exist_ok=True)
        with open(self.path, "w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=self.FIELDS)
            w.writeheader()
            for k in sorted(self.rows):
                w.writerow(self.rows[k])

    def all(self) -> list[dict]:
        return [self.rows[k] for k in sorted(self.rows)]


def sum_range(rows, start: date, end: date, countries=None, sources_exclude=()) -> tuple[int, int, int]:
    """(visitors, installs, days_with_data) over [start, end] inclusive. Rows with several countries/sources per day add up."""
    v = i = 0
    days = set()
    for r in rows:
        dt = date.fromisoformat(r["date"])
        if not (start <= dt <= end):
            continue
        if countries is not None and r["country"] not in countries:
            continue
        if r["source"] in sources_exclude:
            continue
        v += r["visitors"]
        i += r["installs"]
        days.add(r["date"])
    return v, i, len(days)


def double_count_guard(rows: list[dict]) -> list[str]:
    """Country-breakdown and source-breakdown files each add up to the total: mixing both in one store double counts."""
    has_country = any(r["country"] and not r["source"] for r in rows)
    has_source = any(r["source"] and not r["country"] for r in rows)
    has_total = any(not r["country"] and not r["source"] for r in rows)
    kinds = sum([has_country, has_source, has_total])
    return ["metrics mix totals/country/source rows; analysis would double count. Keep ONE kind per metrics store."] if kinds > 1 else []


# ---- Cloud Storage (read only) -------------------------------------------------------------------------------------

def bucket_name(developer_id: str) -> str:
    i = developer_id.strip().removeprefix("gs://").rstrip("/").removeprefix("pubsite_prod_rev_")
    if not re.fullmatch(r"[0-9]{6,30}", i):
        raise NotConfigured("PLAY_DEVELOPER_ID must be the numeric developer id from the Play Console URL")
    return f"pubsite_prod_rev_{i}"


def gcs_object_name(package: str, yyyymm: str, breakdown: str) -> str:
    if breakdown not in ("country", "traffic_source"):
        raise ValueError("breakdown must be country or traffic_source")
    return f"stats/store_performance/store_performance_{package}_{yyyymm}_{breakdown}.csv"


def fetch_gcs_object(bucket: str, name: str, sa_json: str, opener=None) -> bytes | None:
    """GET one object with a read-only token. Returns None on 404. This is the only network call in this module."""
    from google.oauth2 import service_account
    from google.auth.transport.requests import Request
    creds = service_account.Credentials.from_service_account_file(sa_json, scopes=[GCS_SCOPE])
    creds.refresh(Request())
    url = f"https://storage.googleapis.com/storage/v1/b/{bucket}/o/{urllib.parse.quote(name, safe='')}?alt=media"
    req = urllib.request.Request(url, method="GET", headers={"Authorization": f"Bearer {creds.token}"})
    try:
        with (opener or urllib.request.urlopen)(req, timeout=60) as r:
            return r.read()
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None
        raise
