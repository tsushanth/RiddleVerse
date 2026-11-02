"""Scheduler / state machine. Pure functions over a JSON-able state dict; nothing here touches the network.

Window lifecycle:  staged (listing change submitted, in Play review) -> running (owner confirmed it is live; measurement
starts after a ramp) -> complete (analysed) | aborted.  Order of windows is ABAB: baseline, candidate, baseline, candidate...
A candidate that is adopted becomes the new baseline and is not reverted.

Hard rules enforced here:
  * at most one open window, and no listing change while one is open (stage_allowed)
  * a minimum gap (cooldown_days) after the previous window's measurement ends
  * measurement starts only after the owner confirms the change is live (Play review takes hours to ~7 days) plus ramp_days
  * results are read only once the window's pre-set length has elapsed AND the metrics lag has passed (no peeking)
  * if the traffic cannot support the pre-set effect size within max_days the machine says so and refuses to schedule
"""
from __future__ import annotations

import json
import math
import os
from dataclasses import dataclass, asdict
from datetime import date, timedelta

from . import stats

STATE_VERSION = 1


@dataclass
class Policy:
    experiment_locale: str = "en-US"
    target_relative_lift: float = 0.20   # smallest lift worth detecting (pre-registered)
    alpha: float = 0.05
    power: float = 0.80
    min_days: int = 14
    max_days: int = 56
    review_lag_days: int = 7             # Google: allow up to a week for review; owner confirms real go-live date
    ramp_days: int = 1                   # drop the first day(s) after go-live (cached listings, rollout)
    cooldown_days: int = 3
    metrics_lag_days: int = 7            # GCS exports post within 3-7 days; use 2 for Console-UI CSVs
    max_attempts_per_variant: int = 2
    decision_hi: float = 0.95
    decision_lo: float = 0.05
    min_data_coverage: float = 0.8       # share of window days that must have metrics rows


def d(s) -> date:
    return s if isinstance(s, date) else date.fromisoformat(s)


def new_state(policy: Policy | None = None) -> dict:
    p = policy or Policy()
    return {"version": STATE_VERSION, "policy": asdict(p), "windows": [], "baseline_variant": None, "live_variant": None,
            "groups": {"treated": [], "control": []}}


def policy_of(state: dict) -> Policy:
    return Policy(**state["policy"])


def load_state(path: str) -> dict:
    if not os.path.exists(path):
        return new_state()
    with open(path, encoding="utf-8") as f:
        s = json.load(f)
    if s.get("version") != STATE_VERSION:
        raise ValueError("unsupported state version")
    return s


def save_state(state: dict, path: str) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(state, f, indent=2, sort_keys=True)
        f.write("\n")
    os.replace(tmp, path)


def window_plan(policy: Policy, visitors_per_day: float | None, baseline_cvr: float | None) -> dict:
    """Pre-registered window length from the power calculation (sequential: each window is one 'arm')."""
    if not visitors_per_day or visitors_per_day <= 0 or not baseline_cvr or not 0 < baseline_cvr < 1:
        return {"feasible": False, "reason": "no traffic estimate (need visitors/day and baseline conversion)"}
    need = stats.required_n_per_arm(baseline_cvr, policy.target_relative_lift, policy.alpha, policy.power)
    days = max(policy.min_days, math.ceil(need / visitors_per_day))
    days = int(math.ceil(days / 7) * 7)  # whole weeks so each window sees every weekday equally
    mde_max = stats.mde_relative(baseline_cvr, visitors_per_day * policy.max_days, policy.alpha, policy.power)
    plan = {"feasible": days <= policy.max_days, "min_days": days, "required_visitors": need,
            "mde_rel_at_max_days": mde_max, "target_relative_lift": policy.target_relative_lift,
            "visitors_per_day": visitors_per_day, "baseline_cvr": baseline_cvr}
    if not plan["feasible"]:
        m = f"{mde_max:.0%}" if mde_max else "more than any possible"
        plan["reason"] = (f"insufficient traffic: detecting a {policy.target_relative_lift:.0%} lift needs {need} visitors per "
                          f"window = {days} days at {visitors_per_day:g}/day (cap {policy.max_days}); the smallest lift "
                          f"detectable within the cap is {m}. Fix distribution first.")
    return plan


def open_window(state: dict) -> dict | None:
    for w in state["windows"]:
        if w["status"] in ("staged", "running"):
            return w
    return None


def last_closed(state: dict) -> dict | None:
    for w in reversed(state["windows"]):
        if w["status"] in ("complete", "aborted"):
            return w
    return None


def _attempts(state: dict, variant_id: str) -> int:
    return sum(1 for w in state["windows"] if w["variant_id"] == variant_id and w["status"] == "complete")


def measure_range(w: dict, policy: Policy) -> tuple[date, date]:
    start = d(w["live_on"]) + timedelta(days=policy.ramp_days)
    return start, start + timedelta(days=w["min_days"] - 1)


def data_ready_on(w: dict, policy: Policy) -> date:
    return measure_range(w, policy)[1] + timedelta(days=policy.metrics_lag_days + 1)


def stage_allowed(state: dict, today: date) -> tuple[bool, str]:
    p = policy_of(state)
    w = open_window(state)
    if w:
        return False, f"window {w['id']} ({w['variant_id']}) is {w['status']}: never two listing changes in one window"
    last = last_closed(state)
    if last and last.get("live_on") and last["status"] == "complete":
        end = measure_range(last, p)[1]
        if today < end + timedelta(days=1 + p.cooldown_days):
            return False, f"cool-down until {end + timedelta(days=1 + p.cooldown_days)}"
    return True, "ok"


def _pick_candidate(state: dict, cat) -> object | None:
    p = policy_of(state)
    ready = [v for v in cat.ready() if v.locale == p.experiment_locale and _attempts(state, v.id) < p.max_attempts_per_variant]
    ready.sort(key=lambda v: (_attempts(state, v.id), v.created_at, v.id))
    return ready[0] if ready else None


def next_action(state: dict, cat, today: date, visitors_per_day: float | None, baseline_cvr: float | None) -> dict:
    p = policy_of(state)
    plan = window_plan(p, visitors_per_day, baseline_cvr)
    base = {"plan": plan}
    w = open_window(state)
    if w:
        if w["status"] == "staged":
            return {**base, "action": "confirm_live", "window": w["id"], "variant_id": w["variant_id"],
                    "reason": (f"waiting for Play review since {w['staged_on']} (up to {p.review_lag_days} days). Once the new listing is "
                               f"visible on Google Play run: mark-live {w['id']} --date <first day it was live>"),
                    "check_after": w["staged_on"],
                    "overdue": today > d(w["staged_on"]) + timedelta(days=p.review_lag_days)}
        due = data_ready_on(w, p)
        if today < due:
            return {**base, "action": "wait", "window": w["id"], "on_or_after": str(due),
                    "reason": f"window {w['id']} measures {measure_range(w, p)[0]}..{measure_range(w, p)[1]}; results readable {due} "
                              f"(metrics lag {p.metrics_lag_days}d). Do not look early."}
        return {**base, "action": "analyze", "window": w["id"], "reason": "window length and metrics lag have elapsed"}
    if not plan["feasible"]:
        return {**base, "action": "blocked", "reason": plan.get("reason", "traffic too low")}
    ok, why = stage_allowed(state, today)
    if not ok:
        return {**base, "action": "wait", "reason": why}
    if not state["windows"]:
        return {**base, "action": "start_baseline", "variant_id": state.get("baseline_variant"),
                "reason": "no baseline window yet: record the unchanged live listing as the first window (no listing change needed)"}
    last = last_closed(state)
    base_id = state.get("baseline_variant")
    if last and last["kind"] == "candidate" and (last.get("result") or {}).get("verdict") != "adopt" and state.get("live_variant") != base_id:
        return {**base, "action": "stage", "kind": "baseline", "variant_id": base_id,
                "reason": f"revert to baseline after non-adopted candidate {last['variant_id']}; this is also the post-control window"}
    cand = _pick_candidate(state, cat)
    if not cand:
        return {**base, "action": "idle", "reason": "no ready variants with attempts left; add variants to the catalog"}
    return {**base, "action": "stage", "kind": "candidate", "variant_id": cand.id,
            "reason": f"next ready variant (attempt {_attempts(state, cand.id) + 1}): {cand.hypothesis}"}


def _new_id(state: dict) -> str:
    return f"w{len(state['windows']) + 1:03d}"


def open_baseline_window(state: dict, today: date, plan: dict, variant_id: str) -> dict:
    ok, why = stage_allowed(state, today)
    if not ok:
        raise ValueError(why)
    w = {"id": _new_id(state), "variant_id": variant_id, "kind": "baseline", "status": "running", "staged_on": str(today),
         "live_on": str(today), "min_days": plan["min_days"], "required_visitors": plan["required_visitors"], "result": None}
    state["windows"].append(w)
    state["baseline_variant"] = state["live_variant"] = variant_id
    return w


def record_staged(state: dict, today: date, plan: dict, variant_id: str, kind: str) -> dict:
    """Call only after the Publisher API commit succeeded. Enforces the one-change-per-window rule."""
    ok, why = stage_allowed(state, today)
    if not ok:
        raise ValueError(why)
    if not plan.get("feasible"):
        raise ValueError(plan.get("reason", "plan infeasible"))
    w = {"id": _new_id(state), "variant_id": variant_id, "kind": kind, "status": "staged", "staged_on": str(today),
         "live_on": None, "min_days": plan["min_days"], "required_visitors": plan["required_visitors"], "result": None}
    state["windows"].append(w)
    state["live_variant"] = variant_id
    return w


def mark_live(state: dict, window_id: str, live_on: date, today: date) -> dict:
    w = next((x for x in state["windows"] if x["id"] == window_id), None)
    if not w or w["status"] != "staged":
        raise ValueError("window is not awaiting go-live")
    if live_on < d(w["staged_on"]):
        raise ValueError("go-live cannot precede the staging date")
    if live_on > today:
        raise ValueError("go-live date is in the future")
    w["live_on"], w["status"] = str(live_on), "running"
    return w


def abort_window(state: dict, window_id: str, why: str) -> None:
    w = next(x for x in state["windows"] if x["id"] == window_id)
    w["status"], w["abort_reason"] = "aborted", why


def apply_result(state: dict, window_id: str, result: dict, cat=None) -> None:
    """Store an analysis result. Verdicts are immutable once set. 'extend' lengthens the window by a week (sample-size
    rule only, never outcome-based)."""
    w = next(x for x in state["windows"] if x["id"] == window_id)
    p = policy_of(state)
    if w["status"] != "running":
        raise ValueError("only running windows take results")
    if result["verdict"] == "extend":
        if w["min_days"] + 7 > p.max_days:
            result = {**result, "verdict": "inconclusive", "note": "hit max_days without enough visitors"}
        else:
            w["min_days"] += 7
            w.setdefault("extensions", []).append(result.get("note", "underpowered"))
            return
    w["result"], w["status"] = result, "complete"
    if w["kind"] == "baseline" or result["verdict"] != "adopt":
        return
    state["baseline_variant"] = w["variant_id"]
    if cat is not None:
        from .catalog import CatalogError
        old = cat.baseline()
        try:
            new = cat.get(w["variant_id"])
        except CatalogError:
            return
        if old and old.id != new.id:
            old.status = "retired"
        new.status = "baseline"
