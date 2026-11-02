import unittest

from listing_harness import stats


class PowerTests(unittest.TestCase):
    def test_required_n_matches_textbook(self):
        # 25% -> 30% (20% lift), alpha .05 two-sided, power .8: about 1250 per arm
        n = stats.required_n_per_arm(0.25, 0.20)
        self.assertTrue(1200 <= n <= 1300, n)

    def test_n_grows_when_effect_shrinks(self):
        self.assertGreater(stats.required_n_per_arm(0.25, 0.05), stats.required_n_per_arm(0.25, 0.20))

    def test_mde_inverts_required_n(self):
        m = stats.mde_relative(0.25, 1248)
        self.assertAlmostEqual(m, 0.20, delta=0.01)

    def test_mde_shrinks_with_traffic(self):
        a, b, c = (stats.mde_relative(0.25, v * 28) for v in (10, 50, 200))
        self.assertGreater(a, b)
        self.assertGreater(b, c)

    def test_ten_visitors_a_day_cannot_detect_realistic_lifts(self):
        self.assertGreater(stats.mde_relative(0.25, 10 * 28 / 2), 0.5)

    def test_mde_none_without_data(self):
        self.assertIsNone(stats.mde_relative(0.25, 0))

    def test_bad_inputs(self):
        with self.assertRaises(ValueError):
            stats.required_n_per_arm(0, 0.1)
        with self.assertRaises(ValueError):
            stats.required_n_per_arm(0.9, 0.5)


class BayesTests(unittest.TestCase):
    def test_clear_win(self):
        r = stats.compare(stats.Counts(2000, 400), stats.Counts(2000, 520))
        self.assertGreater(r["p_beat"], 0.99)
        self.assertGreater(r["diff_ci"][0], 0)
        self.assertEqual(stats.verdict(r["p_beat"]), "adopt")

    def test_clear_loss(self):
        r = stats.compare(stats.Counts(2000, 520), stats.Counts(2000, 400))
        self.assertLess(r["p_beat"], 0.01)
        self.assertEqual(stats.verdict(r["p_beat"]), "reject")

    def test_identical_is_inconclusive_and_ci_contains_zero(self):
        r = stats.compare(stats.Counts(500, 125), stats.Counts(500, 125))
        self.assertEqual(stats.verdict(r["p_beat"]), "inconclusive")
        self.assertLess(r["diff_ci"][0], 0)
        self.assertGreater(r["diff_ci"][1], 0)

    def test_deterministic(self):
        a = stats.compare(stats.Counts(300, 70), stats.Counts(310, 80))
        b = stats.compare(stats.Counts(300, 70), stats.Counts(310, 80))
        self.assertEqual(a, b)

    def test_tiny_sample_wide_interval(self):
        r = stats.compare(stats.Counts(20, 5), stats.Counts(20, 7))
        self.assertGreater(r["rel_lift_ci"][1] - r["rel_lift_ci"][0], 1.0)
        self.assertLess(r["p_beat"], 0.95)  # +40% on 20 visitors is not evidence

    def test_counts_validation(self):
        with self.assertRaises(ValueError):
            stats.Counts(10, 11)
        with self.assertRaises(ValueError):
            stats.Counts(-1, 0)

    def test_did_cancels_common_trend(self):
        # both groups rise 25% -> 30%: no treatment effect
        r = stats.did(stats.Counts(2000, 500), stats.Counts(2000, 600), stats.Counts(2000, 500), stats.Counts(2000, 600))
        self.assertLess(abs(r["did_median"]), 0.02)
        self.assertTrue(r["did_ci"][0] < 0 < r["did_ci"][1])

    def test_did_detects_treatment_only_lift(self):
        r = stats.did(stats.Counts(3000, 750), stats.Counts(3000, 900), stats.Counts(3000, 750), stats.Counts(3000, 750))
        self.assertGreater(r["p_positive"], 0.99)

    def test_verdict_thresholds(self):
        self.assertEqual(stats.verdict(0.95), "adopt")
        self.assertEqual(stats.verdict(0.949), "inconclusive")
        self.assertEqual(stats.verdict(0.05), "reject")

    def test_mde_table_shape(self):
        t = stats.mde_table(0.25, [10, 50], [14, 28])
        self.assertEqual(len(t), 4)
        self.assertGreater(t[0]["mde_rel_concurrent"], t[0]["mde_rel_sequential"])


if __name__ == "__main__":
    unittest.main()
