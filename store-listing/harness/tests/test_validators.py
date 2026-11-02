import tempfile
import unittest
from pathlib import Path

from listing_harness import validators as V
from tests.helpers import png, jpeg, write


class TextTests(unittest.TestCase):
    def test_limits_exact(self):
        self.assertTrue(V.check_text("a" * 30, "b" * 80, "c" * 4000).ok)
        self.assertFalse(V.check_text("a" * 31).ok)
        self.assertFalse(V.check_text(None, "b" * 81).ok)
        self.assertFalse(V.check_text(None, None, "c" * 4001).ok)

    def test_empty_rejected_none_ignored(self):
        self.assertFalse(V.check_text("  ").ok)
        self.assertTrue(V.check_text(None, None, None).ok)

    def test_policy_warnings(self):
        r = V.check_text("RIDDLE MASTER")
        self.assertTrue(any("CAPS" in w for w in r.warnings))
        self.assertTrue(any("promotional" in w for w in V.check_text("Best Riddles Free").warnings))
        self.assertTrue(any("emoji" in w for w in V.check_text("Riddles \U0001F600").warnings))


class ImageTests(unittest.TestCase):
    def setUp(self):
        self.d = Path(tempfile.mkdtemp())

    def f(self, name, data):
        return write(self.d / name, data)

    def test_icon_ok(self):
        self.assertTrue(V.check_image("icon", self.f("i.png", png(512, 512, rgba=True))).ok)

    def test_icon_wrong_size_rgb_or_jpeg(self):
        self.assertFalse(V.check_image("icon", self.f("a.png", png(500, 500, rgba=True))).ok)
        self.assertFalse(V.check_image("icon", self.f("b.png", png(512, 512, rgba=False))).ok)
        self.assertFalse(V.check_image("icon", self.f("c.jpg", jpeg(512, 512))).ok)

    def test_feature_graphic(self):
        self.assertTrue(V.check_image("featureGraphic", self.f("f.png", png(1024, 500))).ok)
        self.assertTrue(V.check_image("featureGraphic", self.f("f.jpg", jpeg(1024, 500))).ok)
        self.assertFalse(V.check_image("featureGraphic", self.f("g.png", png(1024, 500, rgba=True))).ok)
        self.assertFalse(V.check_image("featureGraphic", self.f("h.png", png(1000, 500))).ok)

    def test_screenshot_rules(self):
        self.assertTrue(V.check_image("phoneScreenshots", self.f("s.png", png(1080, 1920))).ok)
        self.assertFalse(V.check_image("phoneScreenshots", self.f("t.png", png(1080, 2400))).ok)   # > 2:1
        self.assertFalse(V.check_image("phoneScreenshots", self.f("u.png", png(300, 500))).ok)     # < 320
        self.assertFalse(V.check_image("phoneScreenshots", self.f("v.png", png(1080, 1920, rgba=True))).ok)
        self.assertTrue(V.check_image("phoneScreenshots", self.f("w.png", png(720, 1280))).warnings)  # below 1080

    def test_garbage_and_missing(self):
        self.assertFalse(V.check_image("icon", self.f("x.png", b"not an image")).ok)
        self.assertFalse(V.check_image("icon", str(self.d / "nope.png")).ok)
        self.assertFalse(V.check_image("banner", self.f("y.png", png(10, 10))).ok)

    def test_asset_counts(self):
        shots = [self.f(f"p{i}.png", png(1080, 1920)) for i in range(9)]
        self.assertFalse(V.check_assets({"phoneScreenshots": shots[:1]}).ok)
        self.assertTrue(V.check_assets({"phoneScreenshots": shots[:2]}).ok)
        self.assertTrue(V.check_assets({"phoneScreenshots": shots[:8]}).ok)
        self.assertFalse(V.check_assets({"phoneScreenshots": shots[:9]}).ok)
        self.assertFalse(V.check_assets({"icon": [shots[0], shots[1]]}).ok)

    def test_relative_paths_resolve_against_base(self):
        self.f("i.png", png(512, 512, rgba=True))
        self.assertTrue(V.check_assets({"icon": "i.png"}, str(self.d)).ok)


if __name__ == "__main__":
    unittest.main()
