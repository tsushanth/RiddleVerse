import unittest
from datetime import date, timedelta

from listing_harness import analysis, scheduler as S

D0 = date(2026, 11, 1)


def rows_for(start, days, vpd, cvr, country="", extra=None):
    out = []
    for i in range(days):
        d = start + timedelta(days=i)
        out.append({"date": d.isoformat(), "country": country, "source": "", "visitors": vpd, "installs": round(vpd * cvr)})
    return out


def setup(vpd=300, base_cvr=0.25, cand_cvr=0.25, post_cvr=None):
    st = S.new_state()
    plan = S.window_plan(S.Policy(), vpd, base_cvr)
    w1 = S.open_baseline_window(st, D0, plan, "base")
    p = S.policy_of(st)
    s1, e1 = S.measure_range(w1, p)
    rows = rows_for(s1, w1["min_days"], vpd, base_cvr)
    S.apply_result(st, "w001", {"verdict": "reference"})
    t = e1 + timedelta(days=10)
    w2 = S.record_staged(st, t, plan, "v1", "candidate")
    S.mark_live(st, "w002", t + timedelta(days=2), t + timedelta(days=2))
    s2, e2 = S.measure_range(w2, p)
    rows += rows_for(s2, w2["min_days"], vpd, cand_cvr)
    return st, rows, w2, e2


class AnalysisTests(unittest.TestCase):
    def test_peeking_guard(self):
        st, rows, w2, e2 = setup()
        with self.assertRaises(analysis.TooEarly):
            analysis.analyze_window(st, "w002", rows, e2 + timedelta(days=3))

    def test_interim_is_labelled_and_not_a_verdict(self):
        st, rows, w2, e2 = setup(cand_cvr=0.40)
        r = analysis.analyze_window(st, "w002", rows, e2 - timedelta(days=3), interim=True)
        self.assertEqual(r["verdict"], "interim")
        self.assertIn("INTERIM", r["note"])

    def test_adopts_a_real_lift(self):
        st, rows, w2, e2 = setup(vpd=400, cand_cvr=0.33)
        r = analysis.analyze_window(st, "w002", rows, e2 + timedelta(days=10))
        self.assertEqual(r["verdict"], "adopt")
        self.assertGreater(r["rel_lift_ci"][0], 0)

    def test_no_effect_is_not_adopted(self):
        st, rows, w2, e2 = setup(vpd=400, cand_cvr=0.25)
        r = analysis.analyze_window(st, "w002", rows, e2 + timedelta(days=10))
        self.assertNotEqual(r["verdict"], "adopt")

    def test_missing_days_blocks(self):
        st, rows, w2, e2 = setup()
        rows = [r for r in rows if not (S.measure_range(w2, S.policy_of(st))[0] <= date.fromisoformat(r["date"]) <= e2)]
        r = analysis.analyze_window(st, "w002", rows, e2 + timedelta(days=10))
        self.assertEqual(r["verdict"], "blocked")

    def test_underpowered_extends_instead_of_deciding(self):
        st, rows, w2, e2 = setup(vpd=300)
        w2["required_visitors"] = 10 ** 9
        r = analysis.analyze_window(st, "w002", rows, e2 + timedelta(days=10))
        self.assertEqual(r["verdict"], "extend")

    def test_did_present_with_groups_and_flags_drift_disclaimer(self):
        st, rows, w2, e2 = setup(vpd=300)
        st["groups"] = {"treated": ["US"], "control": ["IN"]}
        p = S.policy_of(st)
        rows = []
        for w in st["windows"]:
            s, e = S.measure_range(w, p)
            for c in ("US", "IN"):
                rows += rows_for(s, w["min_days"], 150, 0.25 if w["kind"] == "baseline" or c == "IN" else 0.35, country=c)
        r = analysis.analyze_window(st, "w002", rows, e2 + timedelta(days=10))
        self.assertIn("did", r)
        self.assertGreater(r["did"]["p_positive"], 0.9)
        self.assertIn("approximate", r["did_note"])

    def test_post_control_drift_flag(self):
        st, rows, w2, e2 = setup(vpd=400, cand_cvr=0.25)
        S.apply_result(st, "w002", {"verdict": "reject"})
        p = S.policy_of(st)
        t = e2 + timedelta(days=12)
        plan = S.window_plan(p, 400, 0.25)
        w3 = S.record_staged(st, t, plan, "base", "baseline")
        S.mark_live(st, "w003", t + timedelta(days=2), t + timedelta(days=2))
        s3, e3 = S.measure_range(w3, p)
        rows += rows_for(s3, w3["min_days"], 400, 0.34)  # the baseline itself moved: time drift
        r = analysis.analyze_window(st, "w003", rows, e3 + timedelta(days=10))
        self.assertTrue(r["drift_flag"])
        analysis.attach_post_control(st, r)
        self.assertTrue(st["windows"][1]["result"]["post_control"]["drift_flag"])

    def test_candidate_needs_reference(self):
        st = S.new_state()
        plan = S.window_plan(S.Policy(), 300, 0.25)
        S.open_baseline_window(st, D0, plan, "base")
        S.apply_result(st, "w001", {"verdict": "extend"})  # still running
        st["windows"][0]["kind"] = "candidate"
        p = S.policy_of(st)
        e = S.measure_range(st["windows"][0], p)[1]
        s0 = S.measure_range(st["windows"][0], p)[0]
        rows = rows_for(s0, st["windows"][0]["min_days"], 300, 0.25)
        r = analysis.analyze_window(st, "w001", rows, e + timedelta(days=20))
        self.assertEqual(r["verdict"], "blocked")
        self.assertIn("reference", r["note"])


if __name__ == "__main__":
    unittest.main()
