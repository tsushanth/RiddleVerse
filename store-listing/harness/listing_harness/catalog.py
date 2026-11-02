"""Variant catalog: JSON file of listing variants.

Schema (schema_version 1):
{ "schema_version": 1, "app_package": "com.kreativekoala.riddleverse",
  "variants": [ { "id": "baseline-2026-11", "locale": "en-US", "title": "...", "short": "...", "full": "...",
                  "assets": {"icon": "assets/icon.png", "featureGraphic": "...", "phoneScreenshots": ["..", ".."]},
                  "hypothesis": "...", "created_at": "2026-11-01T00:00:00Z",
                  "status": "draft|ready|baseline|running|adopted|rejected|retired" } ] }
A field set to null is left unchanged on the live listing, which lets a variant change exactly one factor
(text OR graphics) so that a result can be attributed.
"""
from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass, field, asdict

from . import validators as V

STATUSES = ("draft", "ready", "baseline", "running", "adopted", "rejected", "retired")
TEXT_FIELDS = ("title", "short", "full")
_TS_RE = re.compile(r"^\d{4}-\d{2}-\d{2}(T\d{2}:\d{2}:\d{2}Z)?$")


@dataclass
class Variant:
    id: str
    locale: str
    hypothesis: str
    created_at: str
    status: str = "draft"
    title: str | None = None
    short: str | None = None
    full: str | None = None
    assets: dict = field(default_factory=dict)

    @property
    def text_changes(self) -> list[str]:
        return [f for f in TEXT_FIELDS if getattr(self, f) is not None]

    @property
    def factors(self) -> list[str]:
        """Distinct things this variant changes: each text field and each asset role."""
        return self.text_changes + sorted(self.assets)


def validate_variant(v: Variant, base_dir: str = ".") -> V.Report:
    r = V.Report()
    if not V.ID_RE.match(v.id or ""):
        r.errors.append(f"bad id {v.id!r}")
    if not V.LOCALE_RE.match(v.locale or ""):
        r.errors.append(f"bad locale {v.locale!r}")
    if v.status not in STATUSES:
        r.errors.append(f"bad status {v.status!r}")
    if not _TS_RE.match(v.created_at or ""):
        r.errors.append(f"bad created_at {v.created_at!r}")
    if not (v.hypothesis or "").strip():
        r.errors.append("hypothesis is required (what you expect to change and why)")
    if not v.factors:
        r.errors.append("variant changes nothing")
    r.merge(V.check_text(v.title, v.short, v.full))
    if v.status in ("ready", "running", "baseline"):
        r.merge(V.check_assets(v.assets, base_dir))
    else:
        for role in v.assets:
            if role not in V.IMAGE_TYPES:
                r.errors.append(f"unknown asset role {role!r}")
    texts = bool(v.text_changes)
    if texts and v.assets:
        r.warnings.append("variant changes text AND graphics: a win cannot be attributed to either")
    if len(v.text_changes) > 1:
        r.warnings.append("variant changes several text fields at once")
    return r


class CatalogError(Exception):
    pass


@dataclass
class Catalog:
    app_package: str
    variants: list[Variant]
    base_dir: str = "."

    def get(self, vid: str) -> Variant:
        for v in self.variants:
            if v.id == vid:
                return v
        raise CatalogError(f"unknown variant {vid!r}")

    def validate(self) -> V.Report:
        r = V.Report()
        seen = set()
        for v in self.variants:
            if v.id in seen:
                r.errors.append(f"duplicate id {v.id}")
            seen.add(v.id)
            r.merge(validate_variant(v, self.base_dir), prefix=f"[{v.id}] ")
        if sum(1 for v in self.variants if v.status == "baseline") > 1:
            r.errors.append("more than one variant has status baseline")
        return r

    def ready(self) -> list[Variant]:
        return sorted((v for v in self.variants if v.status == "ready"), key=lambda v: (v.created_at, v.id))

    def baseline(self) -> Variant | None:
        for v in self.variants:
            if v.status == "baseline":
                return v
        return None


def load(path: str) -> Catalog:
    with open(path, encoding="utf-8") as f:
        d = json.load(f)
    if d.get("schema_version") != 1:
        raise CatalogError("unsupported schema_version")
    vs = []
    for raw in d.get("variants", []):
        extra = set(raw) - {"id", "locale", "title", "short", "full", "assets", "hypothesis", "created_at", "status"}
        if extra:
            raise CatalogError(f"variant {raw.get('id')}: unknown keys {sorted(extra)}")
        vs.append(Variant(id=raw.get("id", ""), locale=raw.get("locale", ""), hypothesis=raw.get("hypothesis", ""),
                          created_at=raw.get("created_at", ""), status=raw.get("status", "draft"),
                          title=raw.get("title"), short=raw.get("short"), full=raw.get("full"), assets=raw.get("assets") or {}))
    return Catalog(app_package=d.get("app_package", ""), variants=vs, base_dir=os.path.dirname(os.path.abspath(path)))


def save(cat: Catalog, path: str) -> None:
    d = {"schema_version": 1, "app_package": cat.app_package, "variants": [asdict(v) for v in cat.variants]}
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(d, f, indent=2, ensure_ascii=False)
        f.write("\n")
    os.replace(tmp, path)
