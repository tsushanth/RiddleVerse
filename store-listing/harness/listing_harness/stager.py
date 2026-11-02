"""Applies a catalog variant (or a snapshot) to the Play listing through Publisher API edits.

Safety model
  * The raw API client is wrapped by GuardedPublisher; only the whitelisted listing/image/edit methods exist on it.
    Releases, tracks, APKs/bundles, pricing, in-app products, testers and details are unreachable by construction.
  * Dry-run is the default: nothing but edits.insert / list / get / edits.delete is ever called (no write method can
    run, enforced in GuardedPublisher, not by convention). Writes need commit=True from the CLI's explicit --commit.
  * Before commit the plan is re-checked to touch only listing fields of ONE locale, the edit is re-read after the
    writes and compared with expectations (other locales must be byte-identical), then validated, then committed.
  * Every call and decision is appended to an audit log (JSONL). A baseline snapshot (texts + image bytes) is required
    before any commit, and `rollback` re-stages that snapshot through the same guarded path.
"""
from __future__ import annotations

import hashlib
import json
import os
import urllib.request
from dataclasses import dataclass, field, replace
from datetime import datetime, timezone

from . import validators as V
from .catalog import Variant, validate_variant

PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher"
ALLOWED = {"edits.insert", "edits.delete", "edits.validate", "edits.commit", "edits.listings.list", "edits.listings.get",
           "edits.listings.update", "edits.images.list", "edits.images.deleteall", "edits.images.upload"}
WRITES = {"edits.listings.update", "edits.images.deleteall", "edits.images.upload", "edits.commit"}
LISTING_FIELDS = {"language", "title", "shortDescription", "fullDescription", "video"}
MIME = {".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg"}


class ScopeViolation(Exception):
    pass


class DryRunViolation(Exception):
    pass


class PlanRefused(Exception):
    pass


def now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def sha(b: bytes | str) -> str:
    return hashlib.sha256(b.encode() if isinstance(b, str) else b).hexdigest()[:16]


class Audit:
    def __init__(self, path: str, actor: str = "listing-harness"):
        self.path, self.actor = path, actor

    def log(self, event: str, **kw) -> None:
        os.makedirs(os.path.dirname(os.path.abspath(self.path)), exist_ok=True)
        with open(self.path, "a", encoding="utf-8") as f:
            f.write(json.dumps({"ts": now(), "actor": self.actor, "event": event, **kw}, ensure_ascii=False, sort_keys=True) + "\n")


class GuardedPublisher:
    def __init__(self, service, package: str, audit: Audit, allow_writes: bool = False):
        self._svc, self.package, self.audit, self.allow_writes = service, package, audit, allow_writes

    def _call(self, name: str, make_request, **log):
        if name not in ALLOWED:
            raise ScopeViolation(f"{name} is not a permitted listing-harness API method")
        if name in WRITES and not self.allow_writes:
            self.audit.log("blocked_write", method=name, **log)
            raise DryRunViolation(f"{name} attempted in dry-run mode")
        self.audit.log("api", method=name, mode="commit" if self.allow_writes else "dry-run", **log)
        return make_request().execute()

    def open_edit(self) -> str:
        return self._call("edits.insert", lambda: self._svc.edits().insert(packageName=self.package, body={}))["id"]

    def delete_edit(self, edit: str) -> None:
        self._call("edits.delete", lambda: self._svc.edits().delete(packageName=self.package, editId=edit), edit=edit)

    def listings(self, edit: str) -> dict:
        r = self._call("edits.listings.list", lambda: self._svc.edits().listings().list(packageName=self.package, editId=edit), edit=edit)
        return {x["language"]: x for x in r.get("listings", [])}

    def images(self, edit: str, lang: str, itype: str) -> list[dict]:
        if itype not in V.IMAGE_TYPES:
            raise ScopeViolation(f"image type {itype}")
        r = self._call("edits.images.list", lambda: self._svc.edits().images().list(
            packageName=self.package, editId=edit, language=lang, imageType=itype), edit=edit, language=lang, imageType=itype)
        return r.get("images", [])

    def listing_update(self, edit: str, lang: str, body: dict) -> dict:
        extra = set(body) - LISTING_FIELDS
        if extra or body.get("language") != lang:
            raise ScopeViolation(f"listing body has non-listing keys {sorted(extra)} or wrong language")
        return self._call("edits.listings.update", lambda: self._svc.edits().listings().update(
            packageName=self.package, editId=edit, language=lang, body=body), edit=edit, language=lang,
            fields={k: (len(v), sha(v)) for k, v in body.items() if isinstance(v, str)})

    def images_deleteall(self, edit: str, lang: str, itype: str) -> dict:
        if itype not in V.IMAGE_TYPES:
            raise ScopeViolation(f"image type {itype}")
        return self._call("edits.images.deleteall", lambda: self._svc.edits().images().deleteall(
            packageName=self.package, editId=edit, language=lang, imageType=itype), edit=edit, language=lang, imageType=itype)

    def images_upload(self, edit: str, lang: str, itype: str, path: str) -> dict:
        if itype not in V.IMAGE_TYPES:
            raise ScopeViolation(f"image type {itype}")
        from googleapiclient.http import MediaFileUpload
        mime = MIME.get(os.path.splitext(path)[1].lower())
        if not mime:
            raise PlanRefused(f"{path}: unsupported extension")
        return self._call("edits.images.upload", lambda: self._svc.edits().images().upload(
            packageName=self.package, editId=edit, language=lang, imageType=itype, media_body=MediaFileUpload(path, mimetype=mime)),
            edit=edit, language=lang, imageType=itype, file=os.path.basename(path), sha=sha(open(path, "rb").read()))

    def validate(self, edit: str) -> dict:
        return self._call("edits.validate", lambda: self._svc.edits().validate(packageName=self.package, editId=edit), edit=edit)

    def commit(self, edit: str) -> dict:
        return self._call("edits.commit", lambda: self._svc.edits().commit(packageName=self.package, editId=edit), edit=edit)


def build_service(sa_json: str):
    from google.oauth2 import service_account
    from googleapiclient.discovery import build
    creds = service_account.Credentials.from_service_account_file(sa_json, scopes=[PUBLISHER_SCOPE])
    return build("androidpublisher", "v3", credentials=creds, cache_discovery=False)


# ---- snapshot --------------------------------------------------------------------------------------------------------

def listing_digest(listings: dict) -> str:
    keep = {lang: {k: l.get(k, "") for k in ("title", "shortDescription", "fullDescription", "video")} for lang, l in listings.items()}
    return sha(json.dumps(keep, sort_keys=True, ensure_ascii=False))


def capture_snapshot(pub: GuardedPublisher, out_dir: str, download=None) -> dict:
    """Read-only: texts for every locale plus the bytes of every image for every locale. Verified by sha256."""
    download = download or (lambda url: urllib.request.urlopen(urllib.request.Request(url + "=s0"), timeout=60).read())
    os.makedirs(out_dir, exist_ok=True)
    edit = pub.open_edit()
    try:
        listings = pub.listings(edit)
        snap = {"captured_at": now(), "package": pub.package, "digest": listing_digest(listings), "listings": listings, "images": {}}
        for lang in listings:
            for t in V.IMAGE_TYPES:
                items = pub.images(edit, lang, t)
                if not items:
                    continue
                entries = []
                for n, it in enumerate(items):
                    data = download(it["url"])
                    ext = ".png" if data[:4] == b"\x89PNG" else ".jpg"
                    rel = os.path.join(lang, t, f"{n:02d}{ext}")
                    os.makedirs(os.path.join(out_dir, lang, t), exist_ok=True)
                    with open(os.path.join(out_dir, rel), "wb") as f:
                        f.write(data)
                    got = hashlib.sha256(data).hexdigest()
                    entries.append({"id": it.get("id"), "file": rel, "sha256": got, "api_sha256": it.get("sha256"),
                                    "byte_exact": (got == it.get("sha256")) if it.get("sha256") else None})
                snap["images"].setdefault(lang, {})[t] = entries
    finally:
        pub.delete_edit(edit)
    with open(os.path.join(out_dir, "snapshot.json"), "w", encoding="utf-8") as f:
        json.dump(snap, f, indent=2, ensure_ascii=False, sort_keys=True)
    pub.audit.log("snapshot", dir=out_dir, digest=snap["digest"], locales=sorted(listings))
    return snap


def load_snapshot(out_dir: str) -> dict:
    with open(os.path.join(out_dir, "snapshot.json"), encoding="utf-8") as f:
        s = json.load(f)
    s["_dir"] = out_dir
    return s


def snapshot_as_variant(snap: dict, locale: str, vid: str = "snapshot") -> Variant:
    l = snap["listings"][locale]
    assets = {}
    for t, entries in snap["images"].get(locale, {}).items():
        files = [os.path.join(snap["_dir"], e["file"]) for e in entries]
        if t == "icon":
            # Play serves the icon re-encoded as JPEG, which cannot be uploaded back (icons must be 32-bit PNG with alpha):
            # restore the icon from the original source asset by hand rather than from the snapshot.
            info = V.read_image_info(files[0])
            if info["format"] != "png" or not info["alpha"]:
                continue
        assets[t] = files if t in V.SCREENSHOT_TYPES else files[0]
    return Variant(id=vid, locale=locale, hypothesis="restore snapshot", created_at=snap["captured_at"], status="ready",
                   title=l.get("title"), short=l.get("shortDescription"), full=l.get("fullDescription"), assets=assets)


# ---- plan --------------------------------------------------------------------------------------------------------------

@dataclass
class Plan:
    variant_id: str
    locale: str
    body: dict | None
    text_diff: dict = field(default_factory=dict)
    image_ops: list = field(default_factory=list)  # {type, files, existing}
    clear_types: list = field(default_factory=list)

    @property
    def empty(self) -> bool:
        return not self.text_diff and not self.image_ops and not self.clear_types

    def summary(self) -> dict:
        return {"variant": self.variant_id, "locale": self.locale,
                "text": {k: {"old_len": len(o or ""), "new_len": len(n), "old_sha": sha(o or ""), "new_sha": sha(n)} for k, (o, n) in self.text_diff.items()},
                "images": [{"type": o["type"], "replace": o["existing"], "with": [os.path.basename(f) for f in o["files"]]} for o in self.image_ops],
                "clear": self.clear_types}


def build_plan(variant: Variant, listings: dict, live_image_counts: dict, clear_missing_images: bool = False,
               protect_types: frozenset = frozenset()) -> Plan:
    if variant.locale not in listings:
        raise PlanRefused(f"locale {variant.locale} has no existing listing; creating locales is out of scope")
    cur = listings[variant.locale]
    body = {"language": variant.locale, "title": cur.get("title", ""), "shortDescription": cur.get("shortDescription", ""),
            "fullDescription": cur.get("fullDescription", "")}
    if cur.get("video"):
        body["video"] = cur["video"]
    diff = {}
    for fld, key in (("title", "title"), ("short", "shortDescription"), ("full", "fullDescription")):
        new = getattr(variant, fld)
        if new is not None and new != body[key]:
            diff[key] = (body[key], new)
            body[key] = new
    ops = []
    for t, val in variant.assets.items():
        ops.append({"type": t, "files": val if isinstance(val, list) else [val], "existing": live_image_counts.get(t, 0)})
    clear = []
    if clear_missing_images:
        clear = [t for t, n in live_image_counts.items() if n and t not in variant.assets and t not in protect_types]
    return Plan(variant.id, variant.locale, body if diff else None, diff, ops, clear)


def assert_listing_only(plan: Plan) -> None:
    if plan.body is not None and (set(plan.body) - LISTING_FIELDS or plan.body.get("language") != plan.locale):
        raise PlanRefused("plan body touches non-listing fields")
    for o in plan.image_ops:
        if o["type"] not in V.IMAGE_TYPES:
            raise PlanRefused(f"image type {o['type']} is not a listing image")
    for t in plan.clear_types:
        if t not in V.IMAGE_TYPES:
            raise PlanRefused(f"image type {t} is not a listing image")


def stage(pub: GuardedPublisher, variant: Variant, snapshot_dir: str | None, base_dir: str = ".", clear_missing_images: bool = False,
          is_rollback: bool = False, protect_types: frozenset = frozenset()) -> dict:
    """Dry-run unless pub.allow_writes. Returns a result dict. Never raises after the edit was opened without deleting it."""
    commit = pub.allow_writes
    skip_snapshot_check = is_rollback  # a rollback restores the snapshot itself, so it cannot require it to match live
    # a rollback restores what was live before: do not hold old assets to today's upload specs
    rep = validate_variant(replace(variant, status="retired") if is_rollback else variant, base_dir)
    if variant.status == "draft" and not is_rollback:
        (rep.errors if commit else rep.warnings).append("draft variants cannot be staged (set status to ready after review)")
    if not rep.ok:
        pub.audit.log("stage_refused", variant=variant.id, errors=rep.errors)
        raise PlanRefused("; ".join(rep.errors))
    snap = None
    if snapshot_dir and os.path.exists(os.path.join(snapshot_dir, "snapshot.json")):
        snap = load_snapshot(snapshot_dir)
    if commit and not snap and not skip_snapshot_check:
        raise PlanRefused("commit requires a baseline snapshot (run: snapshot) so that rollback is possible")
    edit = pub.open_edit()
    committed = False
    try:
        before = pub.listings(edit)
        if commit and snap and snap["digest"] != listing_digest(before) and not skip_snapshot_check:
            raise PlanRefused("live listing differs from the snapshot (someone edited it, or it is stale): take a fresh snapshot")
        counts = {t: len(pub.images(edit, variant.locale, t)) for t in V.IMAGE_TYPES}
        plan = build_plan(variant, before, counts, clear_missing_images, protect_types)
        assert_listing_only(plan)
        pub.audit.log("plan", mode="commit" if commit else "dry-run", **plan.summary())
        result = {"mode": "commit" if commit else "dry-run", "plan": plan.summary(), "empty": plan.empty, "committed": False,
                  "warnings": rep.warnings}
        if plan.empty:
            result["note"] = "listing already matches the variant; nothing to do"
            return result
        if not commit:
            return result
        # ---- writes (only reachable with --commit) ----
        other_before = {l: before[l] for l in before if l != variant.locale}
        if plan.body is not None:
            pub.listing_update(edit, variant.locale, plan.body)
        for t in plan.clear_types:
            pub.images_deleteall(edit, variant.locale, t)
        for o in plan.image_ops:
            pub.images_deleteall(edit, variant.locale, o["type"])
            for fpath in o["files"]:
                pub.images_upload(edit, variant.locale, o["type"], fpath)
        after = pub.listings(edit)
        problems = []
        if plan.body is not None:
            for k in ("title", "shortDescription", "fullDescription"):
                if after[variant.locale].get(k) != plan.body[k]:
                    problems.append(f"{k} not applied as planned")
        for l, b in other_before.items():
            if listing_digest({l: after.get(l, {})}) != listing_digest({l: b}):
                problems.append(f"locale {l} changed unexpectedly")
        for o in plan.image_ops:
            n = len(pub.images(edit, variant.locale, o["type"]))
            if n != len(o["files"]):
                problems.append(f"{o['type']}: expected {len(o['files'])} images, edit has {n}")
        if problems:
            pub.audit.log("verify_failed", problems=problems)
            raise PlanRefused("post-write verification failed, edit discarded: " + "; ".join(problems))
        pub.validate(edit)
        pub.commit(edit)
        committed = True
        result["committed"] = True
        pub.audit.log("committed", variant=variant.id, locale=variant.locale)
        return result
    finally:
        if not committed:
            try:
                pub.delete_edit(edit)
            except Exception as e:  # leave an audit trail rather than hide it
                pub.audit.log("edit_delete_failed", error=str(e)[:200])


def rollback(pub: GuardedPublisher, snapshot_dir: str, locale: str) -> dict:
    snap = load_snapshot(snapshot_dir)
    v = snapshot_as_variant(snap, locale, vid=f"rollback-{os.path.basename(snapshot_dir.rstrip('/'))}")
    pub.audit.log("rollback_requested", snapshot=snapshot_dir, locale=locale, mode="commit" if pub.allow_writes else "dry-run")
    bad = [e for ents in snap["images"].get(locale, {}).values() for e in ents if e.get("byte_exact") is False]
    if bad:
        pub.audit.log("rollback_warning", note="snapshot images not byte-exact vs API sha256", n=len(bad))
    # image types that existed at snapshot time but cannot be restored from it (the JPEG icon) must be left alone, not cleared
    protect = frozenset(snap["images"].get(locale, {})) - frozenset(v.assets)
    return stage(pub, v, snapshot_dir, clear_missing_images=True, is_rollback=True, protect_types=protect)
