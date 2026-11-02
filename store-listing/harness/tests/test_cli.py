import contextlib
import io
import json
import os
import tempfile
import unittest
from datetime import date, timedelta
from pathlib import Path

from listing_harness import cli, catalog as C

BASE = date(2026, 11, 2)


def run(*args, env=None):
    out, err = io.StringIO(), io.StringIO()
    old = dict(os.environ)
    os.environ.update(env or {})
    try:
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            rc = cli.main(list(args))
    finally:
        os.environ.clear()
        os.environ.update(old)
    return rc, out.getvalue(), err.getvalue()


def write_catalog(d):
    cat = C.Catalog("pkg", [
        C.Variant(id="base", locale="en-US", hypothesis="control", created_at="2026-11-01", status="baseline", title="RiddleVerse"),
        C.Variant(id="v1", locale="en-US", hypothesis="riddle-first title", created_at="2026-11-01", status="ready", title="Riddles & Brain Puzzles")])
    p = str(Path(d) / "catalog.json")
    C.save(cat, p)
    return p


def write_metrics(d, start, days, vpd, cvr):
    p = Path(d) / "m.csv"
    p.write_text("date,visitors,installs\n" + "".join(
        f"{(start + timedelta(days=i)).isoformat()},{vpd},{round(vpd * cvr)}\n" for i in range(days)))
    return str(p)


class CliTests(unittest.TestCase):
    def setUp(self):
        self.d = tempfile.mkdtemp()
        self.cat = write_catalog(self.d)
        self.base = ["--home", os.path.join(self.d, "h"), "--catalog", self.cat]

    def r(self, *a, **kw):
        return run(*self.base, *a, **kw)

    def test_power_table(self):
        rc, out, _ = run("power", "--cvr", "0.25")
        self.assertEqual(rc, 0)
        self.assertIn("| 10.0 | 14 |", out)

    def test_catalog_validate(self):
        rc, out, _ = self.r("catalog")
        self.assertEqual(rc, 0)
        self.assertIn("OK", out)

    def test_low_traffic_blocks_with_honest_message(self):
        m = write_metrics(self.d, BASE - timedelta(days=60), 40, 10, 0.25)
        self.r("ingest", "--csv", m)
        rc, out, _ = self.r("--today", str(BASE), "next")
        a = json.loads(out)
        self.assertEqual(a["action"], "blocked")
        self.assertIn("Fix distribution", a["reason"])

    def test_gcs_ingest_unconfigured_is_a_clear_noop(self):
        rc, out, _ = self.r("ingest", env={"PLAY_DEVELOPER_ID": "", "PLAY_SA_JSON": ""})
        self.assertEqual(rc, 3)
        self.assertIn("not configured", out)

    def test_full_offline_cycle_baseline_to_report(self):
        start = BASE - timedelta(days=100)
        m = write_metrics(self.d, start, 100, 300, 0.25)
        self.assertEqual(self.r("ingest", "--csv", m)[0], 0)
        rc, out, _ = self.r("--today", str(BASE), "start-baseline")
        self.assertEqual(rc, 0, out)
        state = json.load(open(os.path.join(self.d, "h", "state.json")))
        w = state["windows"][0]
        late = BASE + timedelta(days=w["min_days"] + 20)
        # window data is only there from the metrics file; extend it so the window is covered
        m2 = write_metrics(self.d, BASE - timedelta(days=100), 160, 300, 0.25)
        self.r("ingest", "--csv", m2)
        rc, out, err = self.r("--today", str(BASE + timedelta(days=3)), "analyze")
        self.assertEqual(rc, 2)
        self.assertIn("peeking", out)
        rc, out, _ = self.r("--today", str(late), "analyze")
        self.assertEqual(rc, 0, out)
        self.assertEqual(json.loads(out)["verdict"], "reference")
        rc, out, _ = self.r("--today", str(late + timedelta(days=5)), "report")
        self.assertIn("## Next recommendation", out)
        self.assertIn("stage", out)

    def test_commit_needs_env_gate_and_never_touches_network_without_it(self):
        for args in (("stage", "--variant", "v1", "--commit"), ("rollback", "--locale", "en-US", "--commit")):
            with self.assertRaises(SystemExit) as cm:
                self.r(*args, env={"LISTING_HARNESS_ALLOW_COMMIT": ""})
            self.assertIn("LISTING_HARNESS_ALLOW_COMMIT", str(cm.exception))

    def test_commit_refused_when_scheduler_disagrees(self):
        with self.assertRaises(SystemExit) as cm:
            self.r("stage", "--variant", "v1", "--commit", env={"LISTING_HARNESS_ALLOW_COMMIT": "1"})
        self.assertIn("scheduler does not allow", str(cm.exception))

    def test_stage_dry_run_needs_credentials(self):
        with self.assertRaises(SystemExit) as cm:
            self.r("stage", "--variant", "v1", env={"PLAY_SA_JSON": ""})
        self.assertIn("PLAY_SA_JSON", str(cm.exception))


if __name__ == "__main__":
    unittest.main()
