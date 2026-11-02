import unittest
from datetime import date, timedelta

from listing_harness import scheduler as S
from listing_harness.catalog import Catalog, Variant

D0 = date(2026, 11, 1)


def cat_with(*ids, extra=()):
    vs = [Variant(id="base", locale="en-US", hypothesis="h", created_at="2026-10-01", status="baseline", title="T")]
    for n, i in enumerate(ids):
        vs.append(Variant(id=i, locale="en-US", hypothesis=f"hyp {i}", created_at=f"2026-10-{10 + n:02d}", status="ready", title="X" + i))
    vs += list(extra)
    return Catalog("pkg", vs)


def run_window(state, cat, today, vpd, cvr, verdict="reject", live_delta=3):
    """stage per scheduler, mark live, advance to readable day, apply verdict. Returns the day after."""
    act = S.next_action(state, cat, today, vpd, cvr)
    if act["action"] == "start_baseline":
        w = S.open_baseline_window(state, today, act["plan"], "base")
    else:
        assert act["action"] == "stage", act
        w = S.record_staged(state, today, act["plan"], act["variant_id"], act["kind"])
        S.mark_live(state, w["id"], today + timedelta(days=live_delta), today + timedelta(days=live_delta))
    done = S.data_ready_on(w, S.policy_of(state))
    S.apply_result(state, w["id"], {"verdict": "reference" if w["kind"] == "baseline" else verdict}, cat)
    return done + timedelta(days=0), w


class PlanTests(unittest.TestCase):
    def test_low_traffic_is_infeasible_and_says_fix_distribution(self):
        p = S.window_plan(S.Policy(), 10, 0.25)
        self.assertFalse(p["feasible"])
        self.assertIn("distribution", p["reason"])

    def test_good_traffic_whole_weeks(self):
        p = S.window_plan(S.Policy(), 200, 0.25)
        self.assertTrue(p["feasible"])
        self.assertEqual(p["min_days"] % 7, 0)
        self.assertGreaterEqual(p["min_days"], 14)

    def test_length_scales_with_traffic(self):
        a = S.window_plan(S.Policy(), 60, 0.25)["min_days"]
        b = S.window_plan(S.Policy(), 120, 0.25)["min_days"]
        self.assertGreaterEqual(a, b)

    def test_unknown_traffic(self):
        self.assertFalse(S.window_plan(S.Policy(), None, None)["feasible"])
        self.assertFalse(S.window_plan(S.Policy(), 100, 0)["feasible"])


class MachineTests(unittest.TestCase):
    def setUp(self):
        self.state = S.new_state()
        self.state["baseline_variant"] = "base"
        self.cat = cat_with("v1", "v2")

    def test_blocked_without_traffic_or_when_too_low(self):
        self.assertEqual(S.next_action(self.state, self.cat, D0, None, None)["action"], "blocked")
        a = S.next_action(self.state, self.cat, D0, 10, 0.25)
        self.assertEqual(a["action"], "blocked")
        self.assertIn("Fix distribution", a["reason"])

    def test_starts_with_baseline_window(self):
        self.assertEqual(S.next_action(self.state, self.cat, D0, 300, 0.25)["action"], "start_baseline")

    def test_abab_order(self):
        today = D0
        seq = []
        for verdict in ["reject", "reference", "reject", "reference"]:
            today, w = run_window(self.state, self.cat, today + timedelta(days=0), 300, 0.25, verdict)
            today += timedelta(days=S.policy_of(self.state).cooldown_days + 20)
            seq.append((w["kind"], w["variant_id"]))
        self.assertEqual(seq, [("baseline", "base"), ("candidate", "v1"), ("baseline", "base"), ("candidate", "v2")])

    def test_candidate_attempt_limit_then_idle(self):
        cat = cat_with("v1")
        today = D0
        kinds = []
        for _ in range(8):
            act = S.next_action(self.state, cat, today, 300, 0.25)
            if act["action"] in ("idle", "wait", "blocked"):
                kinds.append(act["action"])
                break
            today, w = run_window(self.state, cat, today, 300, 0.25, "inconclusive")
            kinds.append(w["variant_id"])
            today += timedelta(days=30)
        self.assertEqual(kinds.count("v1"), 2)
        self.assertEqual(kinds[-1], "idle")

    def test_never_two_changes_in_a_window(self):
        plan = S.window_plan(S.Policy(), 300, 0.25)
        S.open_baseline_window(self.state, D0, plan, "base")
        with self.assertRaises(ValueError):
            S.record_staged(self.state, D0 + timedelta(days=2), plan, "v1", "candidate")
        a = S.next_action(self.state, self.cat, D0 + timedelta(days=2), 300, 0.25)
        self.assertEqual(a["action"], "wait")

    def test_staged_waits_for_owner_confirmation(self):
        plan = S.window_plan(S.Policy(), 300, 0.25)
        S.open_baseline_window(self.state, D0, plan, "base")
        S.apply_result(self.state, "w001", {"verdict": "reference"})
        t = D0 + timedelta(days=60)
        S.record_staged(self.state, t, plan, "v1", "candidate")
        a = S.next_action(self.state, self.cat, t + timedelta(days=1), 300, 0.25)
        self.assertEqual(a["action"], "confirm_live")
        self.assertFalse(a["overdue"])
        self.assertTrue(S.next_action(self.state, self.cat, t + timedelta(days=9), 300, 0.25)["overdue"])

    def test_no_peeking_before_window_and_lag(self):
        plan = S.window_plan(S.Policy(), 300, 0.25)
        w = S.open_baseline_window(self.state, D0, plan, "base")
        due = S.data_ready_on(w, S.policy_of(self.state))
        self.assertEqual(S.next_action(self.state, self.cat, due - timedelta(days=1), 300, 0.25)["action"], "wait")
        self.assertEqual(S.next_action(self.state, self.cat, due, 300, 0.25)["action"], "analyze")

    def test_mark_live_validation(self):
        plan = S.window_plan(S.Policy(), 300, 0.25)
        S.open_baseline_window(self.state, D0, plan, "base")
        S.apply_result(self.state, "w001", {"verdict": "reference"})
        t = D0 + timedelta(days=60)
        w = S.record_staged(self.state, t, plan, "v1", "candidate")
        with self.assertRaises(ValueError):
            S.mark_live(self.state, w["id"], t - timedelta(days=1), t)
        with self.assertRaises(ValueError):
            S.mark_live(self.state, w["id"], t + timedelta(days=5), t)
        S.mark_live(self.state, w["id"], t + timedelta(days=4), t + timedelta(days=4))
        with self.assertRaises(ValueError):
            S.mark_live(self.state, w["id"], t + timedelta(days=4), t + timedelta(days=4))

    def test_revert_after_reject_adopt_promotes(self):
        today, _ = run_window(self.state, self.cat, D0, 300, 0.25, "reference")
        today += timedelta(days=30)
        today, w = run_window(self.state, self.cat, today, 300, 0.25, "adopt")
        self.assertEqual(self.state["baseline_variant"], "v1")
        self.assertEqual(self.cat.get("v1").status, "baseline")
        self.assertEqual(self.cat.get("base").status, "retired")
        today += timedelta(days=30)
        act = S.next_action(self.state, self.cat, today, 300, 0.25)
        self.assertEqual((act["action"], act["variant_id"]), ("stage", "v2"))  # no revert after adoption

    def test_revert_staged_after_reject(self):
        today, _ = run_window(self.state, self.cat, D0, 300, 0.25, "reference")
        today += timedelta(days=30)
        today, _ = run_window(self.state, self.cat, today, 300, 0.25, "reject")
        today += timedelta(days=30)
        act = S.next_action(self.state, self.cat, today, 300, 0.25)
        self.assertEqual((act["action"], act["kind"], act["variant_id"]), ("stage", "baseline", "base"))

    def test_cooldown(self):
        plan = S.window_plan(S.Policy(), 300, 0.25)
        w = S.open_baseline_window(self.state, D0, plan, "base")
        S.apply_result(self.state, "w001", {"verdict": "reference"})
        end = S.measure_range(w, S.policy_of(self.state))[1]
        ok, why = S.stage_allowed(self.state, end + timedelta(days=2))
        self.assertFalse(ok)
        self.assertIn("cool-down", why)
        self.assertTrue(S.stage_allowed(self.state, end + timedelta(days=5))[0])

    def test_extend_is_sample_size_only_and_capped(self):
        plan = S.window_plan(S.Policy(), 300, 0.25)
        w = S.open_baseline_window(self.state, D0, plan, "base")
        n0 = w["min_days"]
        S.apply_result(self.state, "w001", {"verdict": "extend"})
        self.assertEqual(w["min_days"], n0 + 7)
        self.assertEqual(w["status"], "running")
        w["min_days"] = S.policy_of(self.state).max_days
        S.apply_result(self.state, "w001", {"verdict": "extend"})
        self.assertEqual(w["status"], "complete")

    def test_other_locale_variants_not_scheduled(self):
        cat = cat_with(extra=[Variant(id="fr1", locale="fr-FR", hypothesis="h", created_at="2026-10-01", status="ready", title="Z")])
        today, _ = run_window(self.state, cat, D0, 300, 0.25, "reference")
        self.assertEqual(S.next_action(self.state, cat, today + timedelta(days=30), 300, 0.25)["action"], "idle")

    def test_state_roundtrip(self):
        import os, tempfile
        p = os.path.join(tempfile.mkdtemp(), "s.json")
        S.save_state(self.state, p)
        self.assertEqual(S.load_state(p), self.state)


if __name__ == "__main__":
    unittest.main()
