import json
import os
import tempfile
import unittest
from pathlib import Path

from listing_harness import catalog as C
from tests.helpers import png, write


def v(**kw):
    base = dict(id="v1", locale="en-US", hypothesis="shorter title lifts CTR", created_at="2026-11-01", status="draft", title="Riddle Brain Games")
    base.update(kw)
    return C.Variant(**base)


class VariantTests(unittest.TestCase):
    def test_valid_text_only(self):
        self.assertTrue(C.validate_variant(v()).ok)

    def test_requires_hypothesis_and_a_change(self):
        self.assertFalse(C.validate_variant(v(hypothesis=" ")).ok)
        self.assertFalse(C.validate_variant(v(title=None)).ok)

    def test_bad_id_locale_status(self):
        for kw in ({"id": "Bad Id"}, {"locale": "english"}, {"status": "live"}, {"created_at": "yesterday"}):
            self.assertFalse(C.validate_variant(v(**kw)).ok, kw)

    def test_limits_flow_through(self):
        self.assertFalse(C.validate_variant(v(title="x" * 31)).ok)

    def test_multi_factor_warning(self):
        d = Path(tempfile.mkdtemp())
        write(d / "i.png", png(512, 512, rgba=True))
        r = C.validate_variant(v(status="ready", assets={"icon": "i.png"}), str(d))
        self.assertTrue(r.ok, r.errors)
        self.assertTrue(any("text AND graphics" in w for w in r.warnings))

    def test_ready_requires_real_assets(self):
        self.assertFalse(C.validate_variant(v(status="ready", assets={"icon": "missing.png"})).ok)
        self.assertTrue(C.validate_variant(v(status="draft", assets={"icon": "missing.png"})).ok)  # drafts may be unfinished


class FileTests(unittest.TestCase):
    def test_roundtrip_and_strict_keys(self):
        d = tempfile.mkdtemp()
        p = os.path.join(d, "c.json")
        cat = C.Catalog("pkg", [v(), v(id="v2", status="baseline")])
        C.save(cat, p)
        back = C.load(p)
        self.assertEqual([x.id for x in back.variants], ["v1", "v2"])
        self.assertEqual(back.baseline().id, "v2")
        raw = json.load(open(p))
        raw["variants"][0]["surprise"] = 1
        json.dump(raw, open(p, "w"))
        with self.assertRaises(C.CatalogError):
            C.load(p)

    def test_duplicates_and_two_baselines(self):
        cat = C.Catalog("pkg", [v(status="baseline"), v(status="baseline")])
        errs = cat.validate().errors
        self.assertTrue(any("duplicate" in e for e in errs))
        self.assertTrue(any("more than one" in e for e in errs))

    def test_example_catalog_loads(self):
        ex = Path(__file__).resolve().parent.parent / "catalog.example.json"
        self.assertTrue(C.load(str(ex)).validate().ok)


if __name__ == "__main__":
    unittest.main()
