import json
import os
import tempfile
import unittest
from pathlib import Path

from listing_harness import stager as G
from listing_harness.catalog import Variant
from tests.fake_play import FakePlay
from tests.helpers import png, write

LISTINGS = {
    "en-US": {"language": "en-US", "title": "RiddleVerse", "shortDescription": "short", "fullDescription": "full text"},
    "fr-FR": {"language": "fr-FR", "title": "RiddleVerse", "shortDescription": "court", "fullDescription": "texte"},
}
IMAGES = {"en-US": {"phoneScreenshots": [{"id": "a", "url": "u1", "sha256": "s"}, {"id": "b", "url": "u2", "sha256": "s"}],
                    "icon": [{"id": "i", "url": "ui", "sha256": "s"}]}}
WRITE_CALLS = {"edits.listings.update", "edits.images.deleteall", "edits.images.upload", "edits.commit"}


def variant(**kw):
    base = dict(id="t1", locale="en-US", hypothesis="h", created_at="2026-11-01", status="ready", title="Riddles & Brain Puzzles")
    base.update(kw)
    return Variant(**base)


class Base(unittest.TestCase):
    def setUp(self):
        self.d = Path(tempfile.mkdtemp())
        self.audit = G.Audit(str(self.d / "audit.jsonl"))
        self.fake = FakePlay(LISTINGS, IMAGES)

    def pub(self, writes=False, fake=None):
        return G.GuardedPublisher(fake or self.fake, "pkg", self.audit, allow_writes=writes)

    def snapshot(self):
        out = str(self.d / "snap")
        G.capture_snapshot(self.pub(), out, download=lambda url: png(1080, 1920))
        self.fake.calls.clear()
        return out

    def audit_events(self):
        return [json.loads(l) for l in open(self.audit.path)]


class DryRunTests(Base):
    def test_dry_run_never_writes_and_deletes_its_edit(self):
        res = G.stage(self.pub(), variant(), None)
        self.assertFalse(res["committed"])
        self.assertEqual(res["mode"], "dry-run")
        self.assertIn("title", res["plan"]["text"])
        self.assertFalse(set(self.fake.calls) & WRITE_CALLS)
        self.assertEqual(self.fake.calls[0], "edits.insert")
        self.assertEqual(self.fake.calls[-1], "edits.delete")
        self.assertEqual(self.fake.edits_, {})
        self.assertEqual(self.fake.live["listings"]["en-US"]["title"], "RiddleVerse")

    def test_dry_run_with_images_still_no_writes(self):
        f = write(self.d / "s.png", png(1080, 1920))
        res = G.stage(self.pub(), variant(title=None, assets={"phoneScreenshots": [f, f]}), None, str(self.d))
        self.assertEqual(res["plan"]["images"][0]["replace"], 2)
        self.assertFalse(set(self.fake.calls) & WRITE_CALLS)

    def test_write_methods_blocked_in_dry_run_and_audited(self):
        p = self.pub()
        e = p.open_edit()
        with self.assertRaises(G.DryRunViolation):
            p.listing_update(e, "en-US", {"language": "en-US", "title": "x"})
        with self.assertRaises(G.DryRunViolation):
            p.commit(e)
        self.assertEqual([x["event"] for x in self.audit_events()].count("blocked_write"), 2)

    def test_noop_when_listing_already_matches(self):
        res = G.stage(self.pub(), variant(title="RiddleVerse"), None)
        self.assertTrue(res["empty"])


class ScopeTests(Base):
    def test_only_listing_methods_exist(self):
        p = self.pub(True)
        with self.assertRaises(G.ScopeViolation):
            p._call("edits.tracks.update", lambda: None)
        with self.assertRaises(G.ScopeViolation):
            p._call("edits.bundles.upload", lambda: None)
        with self.assertRaises(G.ScopeViolation):
            p._call("monetization.subscriptions.patch", lambda: None)
        self.assertFalse(any(("track" in m or "bundle" in m or "apk" in m or "monetization" in m) for m in G.ALLOWED))

    def test_listing_body_must_be_listing_fields_for_that_locale(self):
        p = self.pub(True)
        e = p.open_edit()
        with self.assertRaises(G.ScopeViolation):
            p.listing_update(e, "en-US", {"language": "en-US", "title": "x", "price": 1})
        with self.assertRaises(G.ScopeViolation):
            p.listing_update(e, "en-US", {"language": "fr-FR", "title": "x"})
        with self.assertRaises(G.ScopeViolation):
            p.images(e, "en-US", "notAnImage")

    def test_plan_guard(self):
        with self.assertRaises(G.PlanRefused):
            G.assert_listing_only(G.Plan("x", "en-US", {"language": "en-US", "releases": []}))
        with self.assertRaises(G.PlanRefused):
            G.assert_listing_only(G.Plan("x", "en-US", None, image_ops=[{"type": "apk", "files": [], "existing": 0}]))

    def test_new_locale_refused(self):
        with self.assertRaises(G.PlanRefused):
            G.stage(self.pub(), variant(locale="de-DE"), None)


class CommitTests(Base):
    def test_commit_requires_snapshot(self):
        with self.assertRaises(G.PlanRefused):
            G.stage(self.pub(True), variant(), None)
        self.assertEqual(self.fake.calls, [])

    def test_commit_flow_order_and_result(self):
        snap = self.snapshot()
        f = write(self.d / "s.png", png(1080, 1920))
        v = variant(assets={"phoneScreenshots": [f, f, f]})
        res = G.stage(self.pub(True), v, snap, str(self.d))
        self.assertTrue(res["committed"])
        c = self.fake.calls
        self.assertLess(c.index("edits.listings.update"), c.index("edits.validate"))
        self.assertLess(c.index("edits.validate"), c.index("edits.commit"))
        self.assertEqual(c.count("edits.images.upload"), 3)
        self.assertNotIn("edits.delete", c)  # committed edits are consumed, not deleted
        self.assertEqual(self.fake.live["listings"]["en-US"]["title"], "Riddles & Brain Puzzles")
        self.assertEqual(self.fake.live["listings"]["fr-FR"], LISTINGS["fr-FR"])
        self.assertEqual(len(self.fake.live["images"]["en-US"]["phoneScreenshots"]), 3)
        self.assertEqual(len(self.fake.live["images"]["en-US"]["icon"]), 1)  # untouched
        self.assertIn("committed", [e["event"] for e in self.audit_events()])

    def test_draft_cannot_be_committed(self):
        snap = self.snapshot()
        with self.assertRaises(G.PlanRefused):
            G.stage(self.pub(True), variant(status="draft"), snap)
        self.assertEqual(self.fake.calls, [])

    def test_stale_snapshot_refused_and_edit_discarded(self):
        snap = self.snapshot()
        self.fake.live["listings"]["en-US"]["title"] = "Edited in the Console"
        with self.assertRaises(G.PlanRefused):
            G.stage(self.pub(True), variant(), snap)
        self.assertEqual(self.fake.committed, 0)
        self.assertEqual(self.fake.edits_, {})

    def test_post_write_verification_failure_discards_edit(self):
        fake = FakePlay(LISTINGS, IMAGES, corrupt_other_locale=True)
        snap = str(self.d / "snap2")
        G.capture_snapshot(self.pub(fake=fake), snap, download=lambda u: png(1080, 1920))
        with self.assertRaises(G.PlanRefused) as cm:
            G.stage(self.pub(True, fake), variant(), snap)
        self.assertIn("fr-FR", str(cm.exception))
        self.assertEqual(fake.committed, 0)
        self.assertEqual(fake.edits_, {})
        self.assertNotIn("edits.commit", fake.calls)

    def test_invalid_variant_refused_before_any_call(self):
        snap = self.snapshot()
        with self.assertRaises(G.PlanRefused):
            G.stage(self.pub(True), variant(title="x" * 31), snap)
        self.assertEqual(self.fake.calls, [])


class SnapshotRollbackTests(Base):
    def test_snapshot_captures_everything_and_is_byte_verified(self):
        out = self.snapshot()
        s = G.load_snapshot(out)
        self.assertEqual(set(s["listings"]), {"en-US", "fr-FR"})
        self.assertEqual(len(s["images"]["en-US"]["phoneScreenshots"]), 2)
        self.assertTrue(os.path.exists(os.path.join(out, s["images"]["en-US"]["phoneScreenshots"][0]["file"])))
        self.assertEqual(s["digest"], G.listing_digest(LISTINGS))

    def test_rollback_restores_text_and_screens_but_skips_non_png_icon(self):
        out = str(self.d / "snap3")
        # icon served as JPEG (what Play really does) -> not restorable from snapshot
        G.capture_snapshot(self.pub(), out, download=lambda url: b"\xff\xd8\xff\xc0\x00\x11\x08\x02\x00\x02\x00\x03\x01\x22\x00\x02\x11\x01\x03\x11\x01\xff\xd9" if url == "ui" else png(1080, 1920))
        self.fake.calls.clear()
        f = write(self.d / "s.png", png(1080, 1920))
        G.stage(self.pub(True), variant(assets={"phoneScreenshots": [f, f]}), out, str(self.d))  # live now differs
        res = G.rollback(self.pub(True), out, "en-US")
        self.assertTrue(res["committed"])
        live = self.fake.live
        self.assertEqual(live["listings"]["en-US"]["title"], "RiddleVerse")
        self.assertEqual(len(live["images"]["en-US"]["phoneScreenshots"]), 2)
        self.assertEqual(len(live["images"]["en-US"]["icon"]), 1)  # icon left alone, not deleted
        self.assertIn("rollback_requested", [e["event"] for e in self.audit_events()])

    def test_rollback_dry_run_default(self):
        out = self.snapshot()
        res = G.rollback(self.pub(), out, "en-US")
        self.assertFalse(res["committed"])
        self.assertFalse(set(self.fake.calls) & WRITE_CALLS)


if __name__ == "__main__":
    unittest.main()
