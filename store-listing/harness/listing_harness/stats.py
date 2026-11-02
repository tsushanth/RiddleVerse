"""Power calculation and Bayesian analysis (stdlib only).

Two-proportion power uses the usual normal approximation. The Bayesian part is a Beta-Binomial model
(independent Beta posteriors per window) sampled with a seeded Monte Carlo so results are reproducible.
"""
from __future__ import annotations

import math
import random
from dataclasses import dataclass
from statistics import NormalDist

_N = NormalDist()


def required_n_per_arm(p0: float, rel_lift: float, alpha: float = 0.05, power: float = 0.8) -> int:
    """Visitors needed in EACH of two groups to detect p0 -> p0*(1+rel_lift) (two-sided)."""
    if not 0 < p0 < 1:
        raise ValueError("baseline conversion must be in (0,1)")
    p1 = p0 * (1 + rel_lift)
    if not 0 < p1 < 1:
        raise ValueError("lifted conversion out of range")
    za = _N.inv_cdf(1 - alpha / 2)
    zb = _N.inv_cdf(power)
    var = p0 * (1 - p0) + p1 * (1 - p1)
    return math.ceil((za + zb) ** 2 * var / (p1 - p0) ** 2)


def mde_relative(p0: float, n_per_arm: float, alpha: float = 0.05, power: float = 0.8) -> float | None:
    """Smallest relative lift detectable with n visitors per arm. None if even p1 -> 1 is not enough."""
    if n_per_arm <= 0:
        return None
    lo, hi = 0.0, (1 - p0) / p0 - 1e-9  # relative lift up to conversion 100%
    if hi <= 0 or required_n_per_arm(p0, hi - 1e-9 if hi > 1e-9 else hi, alpha, power) > n_per_arm:
        return None
    for _ in range(80):
        mid = (lo + hi) / 2
        if mid <= 0:
            break
        if required_n_per_arm(p0, mid, alpha, power) <= n_per_arm:
            hi = mid
        else:
            lo = mid
    return hi


def mde_table(p0: float, visitors_per_day: list[float], days: list[int], alpha=0.05, power=0.8):
    """Rows of (visitors/day, days, n_per_arm, mde_relative) for a concurrent 50/50 split.

    Sequential before/after windows (each window run alone) have n_per_arm = visitors_per_day * days as well, but
    the real error is larger because time confounds the comparison; the MDE here is therefore a lower bound.
    """
    out = []
    for v in visitors_per_day:
        for d in days:
            # concurrent: total visitors v*d are split between arms; sequential: each arm gets v*d.
            out.append({"visitors_per_day": v, "days": d, "n_per_arm_concurrent": v * d / 2, "n_per_arm_sequential": v * d,
                        "mde_rel_concurrent": mde_relative(p0, v * d / 2, alpha, power),
                        "mde_rel_sequential": mde_relative(p0, v * d, alpha, power)})
    return out


@dataclass
class Counts:
    visitors: int
    installs: int

    def __post_init__(self):
        if self.visitors < 0 or self.installs < 0 or self.installs > self.visitors:
            raise ValueError(f"invalid counts visitors={self.visitors} installs={self.installs}")

    @property
    def rate(self) -> float:
        return self.installs / self.visitors if self.visitors else float("nan")


def _draws(c: Counts, rng: random.Random, n: int, prior=(1.0, 1.0)):
    a = prior[0] + c.installs
    b = prior[1] + c.visitors - c.installs
    return [rng.betavariate(a, b) for _ in range(n)]


def _pct(xs: list[float], q: float) -> float:
    s = sorted(xs)
    k = min(len(s) - 1, max(0, int(round(q * (len(s) - 1)))))
    return s[k]


def compare(baseline: Counts, variant: Counts, draws: int = 40000, seed: int = 7, prior=(1.0, 1.0), cred: float = 0.95) -> dict:
    """Beta-Binomial posterior of the variant against the baseline."""
    rng = random.Random(seed)
    a = _draws(baseline, rng, draws, prior)
    b = _draws(variant, rng, draws, prior)
    diff = [y - x for x, y in zip(a, b)]
    rel = [(y / x) - 1 for x, y in zip(a, b)]
    lo, hi = (1 - cred) / 2, 1 - (1 - cred) / 2
    return {
        "baseline_rate": baseline.rate, "variant_rate": variant.rate,
        "p_beat": sum(1 for d in diff if d > 0) / draws,
        "diff_ci": [_pct(diff, lo), _pct(diff, hi)],
        "rel_lift_ci": [_pct(rel, lo), _pct(rel, hi)],
        "rel_lift_median": _pct(rel, 0.5),
    }


def did(treated_pre: Counts, treated_post: Counts, control_pre: Counts, control_post: Counts,
        draws: int = 40000, seed: int = 11, prior=(1.0, 1.0), cred: float = 0.95) -> dict:
    """Difference-in-differences of conversion: (T_post - T_pre) - (C_post - C_pre), posterior by sampling."""
    rng = random.Random(seed)
    tp, tq, cp, cq = (_draws(c, rng, draws, prior) for c in (treated_pre, treated_post, control_pre, control_post))
    d = [(b - a) - (d_ - c) for a, b, c, d_ in zip(tp, tq, cp, cq)]
    lo, hi = (1 - cred) / 2, 1 - (1 - cred) / 2
    return {"did_median": _pct(d, 0.5), "p_positive": sum(1 for x in d if x > 0) / draws, "did_ci": [_pct(d, lo), _pct(d, hi)]}


def verdict(p_beat: float, hi: float = 0.95, lo: float = 0.05) -> str:
    if p_beat >= hi:
        return "adopt"
    if p_beat <= lo:
        return "reject"
    return "inconclusive"
